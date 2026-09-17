package com.cargenome.app.domain.premium

import androidx.compose.runtime.compositionLocalOf
import com.cargenome.app.BuildConfig
import com.cargenome.app.R
import com.cargenome.app.data.settings.AppSettingsRepository
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val LocalIsPremium = compositionLocalOf { BuildConfig.IS_PREMIUM }

enum class PremiumFeature(
    val titleRes: Int,
    val descriptionRes: Int,
) {
    UNLIMITED_VEHICLES(
        R.string.premium_feat_unlimited_cars,
        R.string.premium_limit_vehicle_desc,
    ),
    RECEIPT_OCR(
        R.string.premium_feat_receipt_ocr,
        R.string.receipt_ocr_detected,
    ),
    VIN_CAMERA_SCANNER(
        R.string.premium_feat_vin_camera,
        R.string.garage_vin_tool,
    ),
    DATA_BACKUP(
        R.string.premium_feat_backup,
        R.string.settings_backup_export_desc,
    ),
}

sealed interface RedeemResult {
    data class Success(val tier: String, val expiresAtSeconds: Long) : RedeemResult
    data object InvalidCode : RedeemResult
    data object Expired : RedeemResult
}

@Singleton
class PremiumManager @Inject constructor(
    private val settingsRepo: AppSettingsRepository,
) {
    companion object {
        // CarGenome Master ECC Public Key (NIST P-256 / SECP256R1, X.509 DER in Base64)
        // Verified cryptographically via ECDSA SHA-256. Private key is strictly offline with the developer.
        const val CARGENOME_PUBLIC_KEY_BASE64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE7HwphxEMuqaUQLTpUS++xIuK1OpHCGanlkP6zNZ2am1gdt0O2/AcE1xo4EP7T3+szlBKtb/LforTuUq2o9wEDQ=="

        private const val MAGIC_HEADER: Byte = 0x43 // 'C'
        private const val CURRENT_VERSION: Byte = 0x01
        const val PAYLOAD_SIZE = 27 // 1 (magic) + 1 (ver) + 1 (tier) + 8 (issuedAt) + 8 (expiresAt) + 8 (nonce)
    }

    private val publicKey: PublicKey by lazy {
        val keyBytes = java.util.Base64.getDecoder().decode(CARGENOME_PUBLIC_KEY_BASE64)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("EC")
        kf.generatePublic(keySpec)
    }

    val isPremium: Boolean
        get() = BuildConfig.IS_PREMIUM

    val isPremiumFlow: Flow<Boolean> = settingsRepo.settings.map { it.isPremiumActive }

    suspend fun isPremium(): Boolean {
        if (BuildConfig.IS_PREMIUM) return true
        val s = settingsRepo.settings.first()
        return s.isPremiumActive
    }

    fun isFeatureAvailable(feature: PremiumFeature, isPremium: Boolean = BuildConfig.IS_PREMIUM): Boolean {
        return isPremium
    }

    fun verifyCode(rawCode: String): RedeemResult {
        try {
            var clean = rawCode.trim()
                .replace("\r", "")
                .replace("\n", "")
                .replace(" ", "")
                .replace("\t", "")

            if (clean.startsWith("CG1-", ignoreCase = true)) {
                clean = clean.substring(4)
            } else if (clean.startsWith("CG1", ignoreCase = true)) {
                clean = clean.substring(3)
            }

            // Restore padding if needed for URL-safe base64
            val remainder = clean.length % 4
            if (remainder > 0) {
                clean += "=".repeat(4 - remainder)
            }

            val bytes = try {
                java.util.Base64.getUrlDecoder().decode(clean)
            } catch (e: Exception) {
                java.util.Base64.getDecoder().decode(clean)
            }

            if (bytes.size < PAYLOAD_SIZE + 64) {
                return RedeemResult.InvalidCode
            }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.get()
            val ver = buffer.get()
            val tierByte = buffer.get()
            val issuedAt = buffer.getLong()
            val expiresAt = buffer.getLong()
            val nonce = ByteArray(8)
            buffer.get(nonce)

            if (magic != MAGIC_HEADER || ver != CURRENT_VERSION) {
                return RedeemResult.InvalidCode
            }

            val payloadBytes = ByteArray(PAYLOAD_SIZE)
            System.arraycopy(bytes, 0, payloadBytes, 0, PAYLOAD_SIZE)

            val signatureBytes = ByteArray(bytes.size - PAYLOAD_SIZE)
            System.arraycopy(bytes, PAYLOAD_SIZE, signatureBytes, 0, signatureBytes.size)

            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(payloadBytes)
            val isVerified = sig.verify(signatureBytes)

            if (!isVerified) {
                return RedeemResult.InvalidCode
            }

            // Check expiration
            if (expiresAt > 0) {
                val nowSeconds = System.currentTimeMillis() / 1000
                if (nowSeconds > expiresAt) {
                    return RedeemResult.Expired
                }
            }

            val tierName = when (tierByte.toInt()) {
                1 -> "Lifetime Unlimited"
                2 -> "Annual (1 Year)"
                3 -> "Monthly (1 Month)"
                4 -> "VIP / Developer"
                else -> "Premium"
            }

            return RedeemResult.Success(tierName, expiresAt)
        } catch (e: Exception) {
            return RedeemResult.InvalidCode
        }
    }

    suspend fun redeemCode(code: String): RedeemResult {
        val result = verifyCode(code)
        if (result is RedeemResult.Success) {
            settingsRepo.setPremiumPurchased(true)
        }
        return result
    }
}

