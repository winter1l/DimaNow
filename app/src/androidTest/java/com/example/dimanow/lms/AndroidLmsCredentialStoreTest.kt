package com.example.dimanow.lms

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidLmsCredentialStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store by lazy { AndroidLmsCredentialStore(context) }

    @After
    fun clear() = runTest { store.delete() }

    @Test
    fun credentialsRoundTripThroughEncryptedNoBackupFileAndCanBeDeleted() = runTest {
        val credentials = SavedLmsCredentials("202614125", "not-a-real-password", true)

        store.save(credentials)

        assertEquals(credentials, store.load())
        assertEquals(CredentialState.SAVED, store.state.first())
        val blob = File(context.noBackupFilesDir, AndroidLmsCredentialStore.FILE_NAME)
        assertTrue(blob.exists())
        assertFalse(blob.readText(Charsets.ISO_8859_1).contains(credentials.password))

        store.delete()

        assertNull(store.load())
        assertEquals(CredentialState.EMPTY, store.state.first())
    }
}
