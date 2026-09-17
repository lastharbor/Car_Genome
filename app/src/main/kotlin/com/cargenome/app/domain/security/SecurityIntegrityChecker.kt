package com.cargenome.app.domain.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import java.io.File
import java.security.MessageDigest

/**
 * Multi-layered runtime application self-protection (RASP) helper.
 * Detects tampering, repackaging, debugger attachment, Frida instrumentation, and root environments.
 */
object SecurityIntegrityChecker {

    /**
     * Common paths where root binaries (su, magisk, daemonsu) are placed.
     */
    private val KNOWN_ROOT_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
    )

    /**
     * Signatures or keywords commonly present in memory maps when Frida, Xposed or Substrate are hooked.
     */
    private val SUSPICIOUS_MAP_KEYWORDS = arrayOf(
        "frida",
        "gadget",
        "xposed",
        "substrate",
        "edxposed",
        "lsposed",
    )

    /**
     * Verifies that the APK certificate signature matches the expected SHA-256 hash.
     * If an attacker repacks the APK with MT Manager / Lucky Patcher, the signature changes.
     */
    fun isSignatureValid(context: Context, expectedSha256: String): Boolean {
        return try {
            val currentSha256 = getSigningCertificateSha256(context) ?: return false
            currentSha256.equals(expectedSha256, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extracts SHA-256 certificate fingerprint of current running application.
     */
    @Suppress("DEPRECATION")
    fun getSigningCertificateSha256(context: Context): String? {
        return try {
            val pm = context.packageManager
            val packageName = context.packageName

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo ?: return null
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                packageInfo.signatures
            }

            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(certBytes)
            hash.joinToString("") { "%02X".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if the device appears to have root access or test-keys build tags.
     */
    fun isDeviceRooted(): Boolean {
        // 1. Build tags check
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        // 2. su binary checks in filesystem
        for (path in KNOWN_ROOT_PATHS) {
            try {
                if (File(path).exists()) return true
            } catch (_: Exception) {
                // Access restricted or file does not exist
            }
        }

        // 3. which su execution check
        try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            if (process.waitFor() == 0) return true
        } catch (_: Exception) {
            // Ignored
        }

        return false
    }

    /**
     * Checks if the process is currently being debugged or traced by GDB/LLDB/IDA Pro/Frida.
     */
    fun isDebuggerOrTracerAttached(): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            return true
        }

        return try {
            val statusFile = File("/proc/self/status")
            if (statusFile.exists()) {
                statusFile.useLines { lines ->
                    lines.any { line ->
                        if (line.startsWith("TracerPid:")) {
                            val pid = line.substringAfter(":").trim().toIntOrNull() ?: 0
                            pid > 0 && pid != Process.myPid()
                        } else false
                    }
                }
            } else false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether dynamic hooking frameworks like Frida or Xposed are injected into process memory.
     */
    fun isHookFrameworkDetected(): Boolean {
        return try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                mapsFile.useLines { lines ->
                    lines.any { line ->
                        val lower = line.lowercase()
                        SUSPICIOUS_MAP_KEYWORDS.any { keyword -> lower.contains(keyword) }
                    }
                }
            } else false
        } catch (_: Exception) {
            false
        }
    }
}
