"""Token-lean Gradle runner for AI agents.

Runs gradlew with the given tasks, stores the full log under .ai-logs/ and
prints only what matters: compiler errors, failed tests, the "What went wrong"
block, per-task test totals and a one-line summary. Exit code is Gradle's.

Usage: python scripts/ai/gradle.py :app:testDebugUnitTest [:app:assembleDebug ...]
       python scripts/ai/gradle.py --full <tasks>   # also dump the log tail
"""

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAX_LINES = 80
STUDIO_JBR = Path(r"C:\Program Files\Android\Android Studio\jbr")

sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def java_env() -> dict:
    env = dict(os.environ)
    # AGP rejects the system Oracle JDK; fall back to Android Studio's JBR.
    if not env.get("JAVA_HOME") and STUDIO_JBR.exists():
        env["JAVA_HOME"] = str(STUDIO_JBR)
    return env


def interesting(lines: list[str]) -> list[str]:
    out: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.startswith("e: ") or re.search(r"\berror:", line):
            out.append(line)
        elif line.endswith(" FAILED"):
            out.append(line)
            # Assertion message and the first stack frames are indented below.
            j = i + 1
            while j < len(lines) and j <= i + 4 and lines[j].startswith((" ", "\t")):
                out.append(lines[j])
                j += 1
        elif line.startswith("* What went wrong:"):
            j = i
            while j < len(lines) and not lines[j].startswith("* Try:"):
                out.append(lines[j])
                j += 1
            i = j
            continue
        elif re.match(r"^> Task \S*:(test|test\w+UnitTest) (UP-TO-DATE|FROM-CACHE|NO-SOURCE)$", line):
            # Without this a skipped test task looks the same as a passing one.
            out.append(line + " (tests not re-run)")
        elif re.match(r"^\d+ tests? completed", line) or "Lint found" in line:
            out.append(line)
        i += 1
    seen, unique = set(), []
    for line in out:
        if line not in seen:
            seen.add(line)
            unique.append(line)
    return unique


def test_totals(since: float) -> list[str]:
    """Totals from the JUnit XML each test task wrote during this run.

    Gradle prints nothing about tests that pass, so this is the proof they ran.
    """
    lines = []
    # Modules sit one or two levels deep (app, core/vin); a ** glob would crawl .gradle.
    task_dirs = [*ROOT.glob("*/build/test-results/*"), *ROOT.glob("*/*/build/test-results/*")]
    for results in sorted(task_dirs):
        files = [f for f in results.glob("TEST-*.xml") if f.stat().st_mtime >= since]
        if not files:
            continue
        run = failed = skipped = 0
        reasons = []
        for f in files:
            suite = ET.parse(f).getroot()
            run += int(suite.get("tests", 0))
            failed += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
            skipped += int(suite.get("skipped", 0))
            for case in suite.iter("testcase"):
                problem = case.find("failure")
                if problem is None:
                    problem = case.find("error")
                if problem is not None:
                    # Gradle's console shows only the exception type; the message is the useful part.
                    message = (problem.get("message") or problem.text or "").strip().splitlines()
                    reasons.append(f"  {case.get('name')}: {message[0][:200] if message else problem.get('type')}")
        module = results.parents[2].relative_to(ROOT).as_posix()
        lines.append(f"tests {module}:{results.name}: {run} run, {failed} failed, {skipped} skipped ({len(files)} classes)")
        lines.extend(reasons)
    return lines


def lint_totals() -> list[str]:
    """Last line of each lint text report, e.g. "0 errors, 60 warnings".

    An up-to-date lint task keeps its report from a run with the same inputs,
    so the totals are current either way.
    """
    lines = []
    reports = sorted(ROOT.glob("*/build/reports/lint-results-*.txt"), key=lambda r: r.stat().st_mtime)
    # Older reports can linger from variants that no longer exist; the newest is the one just run.
    for report in reports[-1:]:
        text = [l for l in report.read_text(encoding="utf-8", errors="replace").splitlines() if l.strip()]
        if text:
            lines.append(f"lint {report.parents[2].name}/{report.name}: {text[-1].strip()}")
    return lines


def main() -> int:
    args = sys.argv[1:]
    full = "--full" in args
    args = [a for a in args if a != "--full"]
    if not args:
        print(__doc__)
        return 2

    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    log_dir = ROOT / ".ai-logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / f"{time.strftime('%Y%m%d-%H%M%S')}.log"

    started = time.time()
    proc = subprocess.run(
        [str(gradlew), "--console=plain", *args],
        cwd=ROOT,
        env=java_env(),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    log_path.write_text(proc.stdout, encoding="utf-8")
    lines = proc.stdout.splitlines()

    warnings = sum(1 for l in lines if l.startswith("w: "))
    picked = interesting(lines)
    if len(picked) > MAX_LINES:
        picked = picked[:MAX_LINES] + [f"... {len(picked) - MAX_LINES} more lines in log"]
    for line in picked:
        print(line)
    for line in test_totals(started):
        print(line)
    if any("lint" in a.lower() for a in args):
        for line in lint_totals():
            print(line)
    if full:
        print("--- log tail ---")
        print("\n".join(lines[-40:]))

    status = "BUILD SUCCESSFUL" if proc.returncode == 0 else "BUILD FAILED"
    print(f"{status} in {time.time() - started:.0f}s | kotlin warnings: {warnings} | log: {log_path.relative_to(ROOT)}")
    return proc.returncode


if __name__ == "__main__":
    sys.exit(main())
