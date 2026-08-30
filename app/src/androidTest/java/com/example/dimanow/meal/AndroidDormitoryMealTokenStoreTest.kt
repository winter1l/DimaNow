package com.example.dimanow.meal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidDormitoryMealTokenStoreTest {
    @Test
    fun githubUserTokenRoundTripsThroughAndroidKeystoreEncryption() {
        val store = AndroidDormitoryMealTokenStore(ApplicationProvider.getApplicationContext<Context>())
        store.clear()

        store.write("ghu_test_token")
        assertEquals("ghu_test_token", store.read())

        store.clear()
        assertNull(store.read())
    }
}
