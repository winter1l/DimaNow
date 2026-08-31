package com.example.dimanow.lms

import kotlinx.coroutines.flow.StateFlow

interface LmsCredentialStore {
    val state: StateFlow<CredentialState>
    suspend fun save(credentials: SavedLmsCredentials)
    suspend fun load(): SavedLmsCredentials?
    suspend fun delete()
}
