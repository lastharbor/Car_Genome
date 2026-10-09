package com.cargenome.app.domain.premium

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cargenome.app.BuildConfig
import com.cargenome.app.data.settings.AppSettingsRepository
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PremiumManagerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settingsRepo by lazy { AppSettingsRepository(context) }
    private val manager by lazy { PremiumManager(settingsRepo) }

    @org.junit.Before
    fun setUp() = runTest {
        settingsRepo.setPremiumPurchased(false)
    }

    @Test
    fun testPremiumFeaturesAvailabilityReflectsBuildConfig() {
        assertEquals(BuildConfig.IS_PREMIUM, manager.isPremium)
        for (feature in PremiumFeature.entries) {
            assertEquals(BuildConfig.IS_PREMIUM, manager.isFeatureAvailable(feature))
        }
    }

    @Test
    fun testCryptographicPromoCodeRedemption() = runTest {
        // Valid signature generated with CARGENOME master private key
        val validCode = "CG1-QwEBAAAAAGqrrjgAAAAAAAAAAB1UqBVDNqCyMEUCIHgTDGsZ08S9TKdKClsR-UNavWyKtktqCpf3k4FOgFTHAiEA9K3yaUvFb4MuajiCpbze6DXpUemBY6cPxWeq9tFXa_g"
        val result = manager.redeemCode(validCode)
        assertTrue(result is RedeemResult.Success)
        val success = result as RedeemResult.Success
        assertEquals("Lifetime Unlimited", success.tier)
        assertEquals(0L, success.expiresAtSeconds)

        // Invalid / forged code
        val invalidResult = manager.redeemCode("CG1-QwEBAAAAAGqrrjgAAAAAAAAAAB1UqBVDNqCyMEUCIHgTDGsZ08S9TKdKClsR-FORGED")
        assertEquals(RedeemResult.InvalidCode, invalidResult)

        val randomGarbage = manager.redeemCode("DEV-PREMIUM")
        assertEquals(RedeemResult.InvalidCode, randomGarbage)
    }

    @Test
    fun testPremiumActivationLifecycle() = runTest {
        // Step 1: Default state is not premium
        assertFalse(BuildConfig.IS_PREMIUM)
        assertFalse(settingsRepo.settings.first().isPremiumActive)

        // Step 2: Redeem valid code
        val validCode = "CG1-QwEBAAAAAGqrrjgAAAAAAAAAAB1UqBVDNqCyMEUCIHgTDGsZ08S9TKdKClsR-UNavWyKtktqCpf3k4FOgFTHAiEA9K3yaUvFb4MuajiCpbze6DXpUemBY6cPxWeq9tFXa_g"
        val result = manager.redeemCode(validCode)
        assertTrue(result is RedeemResult.Success)

        // Step 3: Verify isPremiumActive is now true
        assertTrue(manager.isPremium())
        assertTrue(settingsRepo.settings.first().isPremiumActive)
    }

    @Test
    fun aTimeLimitedCodeKeepsItsExpiryAndLapsesAfterIt() = runTest {
        val keys = testKeyPair()
        val expiresAt = System.currentTimeMillis() / 1000 + 30L * 24 * 3600
        val code = signedCode(keys.private, tier = 3, expiresAtSeconds = expiresAt)

        val result = PremiumManager(settingsRepo, keys.public).redeemCode(code)

        assertEquals(RedeemResult.Success("Monthly (1 Month)", expiresAt), result)
        val settings = settingsRepo.settings.first()
        assertEquals(expiresAt, settings.premiumExpiresAtSeconds)
        assertTrue(settings.isPremiumActiveAt(expiresAt))
        assertFalse(settings.isPremiumActiveAt(expiresAt + 1))
    }

    @Test
    fun aLifetimeCodeNeverLapses() = runTest {
        val keys = testKeyPair()
        val code = signedCode(keys.private, tier = 1, expiresAtSeconds = 0L)

        PremiumManager(settingsRepo, keys.public).redeemCode(code)

        val settings = settingsRepo.settings.first()
        assertNull(settings.premiumExpiresAtSeconds)
        assertTrue(settings.isPremiumActiveAt(Long.MAX_VALUE))
    }

    @Test
    fun aCodeSignedWithAnotherKeyIsRejected() = runTest {
        val code = signedCode(testKeyPair().private, tier = 1, expiresAtSeconds = 0L)

        assertEquals(RedeemResult.InvalidCode, PremiumManager(settingsRepo, testKeyPair().public).redeemCode(code))
        assertEquals(RedeemResult.InvalidCode, manager.redeemCode(code))
        assertFalse(settingsRepo.settings.first().isPremiumActive)
    }

    private fun testKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()

    /** Same layout as the offline generator: payload, then a DER ECDSA signature. */
    private fun signedCode(key: PrivateKey, tier: Int, expiresAtSeconds: Long): String {
        val payload = ByteBuffer.allocate(PremiumManager.PAYLOAD_SIZE).order(ByteOrder.BIG_ENDIAN)
            .put(0x43.toByte())
            .put(0x01.toByte())
            .put(tier.toByte())
            .putLong(System.currentTimeMillis() / 1000)
            .putLong(expiresAtSeconds)
            .putLong(42L)
            .array()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(payload)
            sign()
        }
        return "CG1-" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload + signature)
    }
}
