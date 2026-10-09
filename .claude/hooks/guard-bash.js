// PreToolUse guard for Bash/PowerShell: steers raw Gradle calls to the
// filtered runner, since plain Gradle output burns thousands of tokens.
let raw = "";
process.stdin.on("data", (c) => (raw += c));
process.stdin.on("end", () => {
  let cmd = "";
  try {
    cmd = JSON.parse(raw).tool_input?.command ?? "";
  } catch {
    process.exit(0);
  }

  // Only gradlew in command position (start, or after ; & | newline), so
  // `grep gradlew` or a quoted prompt mentioning it is not blocked.
  const rawGradle = /(^|[;&|\n]|\bcmd\s+\/c)\s*(&\s*)?["']?([\w.:~-]*[\\/])*gradlew(\.bat)?["']?(\s|$)/i.test(cmd);
  const harmless = /--stop|--version|scripts[\\/]ai[\\/]gradle\.py/.test(cmd);
  if (rawGradle && !harmless) {
    deny(
      "Run Gradle through `python scripts/ai/gradle.py <tasks>` — it keeps the full log in .ai-logs/ and prints only errors, test totals and a summary. Add --full for the log tail."
    );
  }
  process.exit(0);
});

function deny(reason) {
  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "PreToolUse",
        permissionDecision: "deny",
        permissionDecisionReason: reason,
      },
    })
  );
  process.exit(0);
}
