# CarGenome Wireless ADB Auto-Connect Script
[CmdletBinding()]
param(
    [string]$DeviceIp = "172.16.0.159",
    [int]$Port = 5555,
    [switch]$KeepAlive
)

function Write-Info($msg) {
    Write-Host "[ADB-WIFI] $msg" -ForegroundColor Cyan
}

function Write-Success($msg) {
    Write-Host "[ADB-WIFI] $msg" -ForegroundColor Green
}

function Write-Warn($msg) {
    Write-Host "[ADB-WIFI] $msg" -ForegroundColor Yellow
}

function Test-AdbConnection($target) {
    $devices = adb devices 2>&1
    foreach ($line in $devices) {
        if ($line -match "^$([regex]::Escape($target))\s+device") {
            return $true
        }
    }
    return $false
}

function Connect-Target($target) {
    Write-Info "Connecting to $target..."
    $result = adb connect $target 2>&1
    Write-Host "  $result"
    Start-Sleep -Milliseconds 500
    return (Test-AdbConnection $target)
}

function Try-Connect {
    $target = "${DeviceIp}:${Port}"

    # 1. Check if already connected
    if (Test-AdbConnection $target) {
        Write-Success "Device already connected at $target"
        return $true
    }

    # 2. Try direct connect to standard port
    if (Connect-Target $target) {
        Write-Success "Successfully connected to $target"
        return $true
    }

    # 3. Check if USB device is attached to enable tcpip 5555
    Write-Info "Checking for USB connected device..."
    $usbDevices = adb devices 2>&1 | Where-Object { $_ -match "^\w+\s+device" -and $_ -notmatch ":\d+" -and $_ -notmatch "_adb" }
    if ($usbDevices) {
        Write-Info "Found USB device. Enabling TCP/IP mode on port $Port..."
        adb -d tcpip $Port 2>&1 | Out-Null
        Start-Sleep -Seconds 1

        # Query device Wi-Fi IP
        $ipOutput = adb -d shell ip -f inet addr show wlan0 2>&1 | Out-String
        if ($ipOutput -match 'inet\s+(\d+\.\d+\.\d+\.\d+)') {
            $detectedIp = $matches[1]
            Write-Info "Detected device Wi-Fi IP: $detectedIp"
            $target = "${detectedIp}:${Port}"
        }

        if (Connect-Target $target) {
            Write-Success "Successfully enabled and connected to $target via Wi-Fi!"
            return $true
        }
    }

    # 4. Check mDNS services for Android 11+ Wireless Debugging
    Write-Info "Scanning for Wireless Debugging mDNS services..."
    $mdns = adb mdns services 2>&1
    foreach ($line in $mdns) {
        if ($line -match '(\d+\.\d+\.\d+\.\d+):(\d+)') {
            $mdnsTarget = "$($matches[1]):$($matches[2])"
            Write-Info "Discovered mDNS wireless service at $mdnsTarget"
            if (Connect-Target $mdnsTarget) {
                # Once connected, switch to persistent port 5555
                Write-Info "Switching device to permanent TCP/IP port $Port..."
                adb -s $mdnsTarget tcpip $Port 2>&1 | Out-Null
                Start-Sleep -Seconds 1
                if (Connect-Target "${DeviceIp}:${Port}") {
                    Write-Success "Connected to permanent port ${DeviceIp}:${Port}"
                    return $true
                }
                Write-Success "Connected via mDNS port $mdnsTarget"
                return $true
            }
        }
    }

    Write-Warn "Could not connect to $target."
    Write-Warn "Ensure either:"
    Write-Warn "  1. The phone and PC are on the same Wi-Fi network (172.16.0.x)."
    Write-Warn "  2. If first time or rebooted: plug USB once or enable Wireless Debugging in Developer Options."
    return $false
}

# Main execution
Write-Host "========================================" -ForegroundColor Blue
Write-Host "  CarGenome Wi-Fi ADB Connection Tool" -ForegroundColor Blue
Write-Host "========================================" -ForegroundColor Blue

$connected = Try-Connect

if ($KeepAlive) {
    Write-Info "Running in KeepAlive mode. Press Ctrl+C to stop."
    $target = "${DeviceIp}:${Port}"
    while ($true) {
        Start-Sleep -Seconds 10
        if (-not (Test-AdbConnection $target)) {
            Write-Warn "Connection lost to $target. Reconnecting..."
            Try-Connect | Out-Null
        }
    }
}
