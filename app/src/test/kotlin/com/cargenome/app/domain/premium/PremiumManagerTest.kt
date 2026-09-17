package com.cargenome.app.domain.premium

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cargenome.app.BuildConfig
import com.cargenome.app.data.settings.AppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
