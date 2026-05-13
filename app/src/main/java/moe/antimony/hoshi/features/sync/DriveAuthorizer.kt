package moe.antimony.hoshi.features.sync

interface DriveAccessTokenProvider {
    suspend fun accessToken(): String

    suspend fun clearAccessToken(token: String) = Unit
}

interface DriveAuthorizer : DriveAccessTokenProvider {
    suspend fun revokeAccess()

    suspend fun status(): DriveAuthStatus
}
