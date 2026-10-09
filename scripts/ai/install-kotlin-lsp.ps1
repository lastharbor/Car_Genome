# Installs JetBrains kotlin-lsp for the Claude Code `kotlin-lsp` plugin.
# download.jetbrains.com is unreachable from some networks: run this with a VPN on.
$ErrorActionPreference = 'Stop'

$version = '263.6379.0'
$sha256  = '72148fa0832c2a45a4b26c55f56197d8d8886bcdb732b910607efc5e2693b9df'
$url     = "https://download.jetbrains.com/language-server/kotlin-server/$version/kotlin-server-$version.win.zip"
$target  = Join-Path $env:USERPROFILE '.local\share\kotlin-lsp'
$zip     = Join-Path $env:TEMP "kotlin-server-$version.zip"

Invoke-WebRequest -Uri $url -OutFile $zip
$actual = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
if ($actual -ne $sha256) { throw "SHA-256 mismatch: $actual" }

if (Test-Path $target) { Remove-Item $target -Recurse -Force -Confirm:$false }
Expand-Archive $zip -DestinationPath $target
Remove-Item $zip -Confirm:$false

$launcher = Get-ChildItem $target -Recurse -Include 'kotlin-lsp.cmd', 'kotlin-lsp.bat', 'kotlin-lsp.exe' | Select-Object -First 1
if (-not $launcher) { throw "kotlin-lsp launcher not found under $target" }

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if ($userPath -notlike "*$($launcher.DirectoryName)*") {
    [Environment]::SetEnvironmentVariable('Path', "$userPath;$($launcher.DirectoryName)", 'User')
}
Write-Host "kotlin-lsp installed: $($launcher.FullName). Restart VS Code / Claude Code to pick up PATH."
Write-Host 'Then enable the plugin: set "kotlin-lsp@claude-plugins-official": true in .claude/settings.json'
