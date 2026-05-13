package moe.antimony.hoshi.features.sync

internal object GoogleCloudOAuthConfiguration {
    const val ttuSetupUrl: String = "https://github.com/ttu-ttu/ebook-reader?tab=readme-ov-file#storage-sources"

    const val introduction: String =
        "Google Drive sync uses Device Code flow so this Android app can use the same user-owned Google Cloud project as iOS/ッツ."

    val instructions: List<String> = listOf(
        "Open the same Google Cloud project used by iOS/ッツ sync and make sure the Google Drive API is enabled.",
        "Create an OAuth client with application type TVs and Limited Input devices.",
        "Paste that client ID and client secret here. Do not create an Android OAuth client for this flow.",
        "Press Connect Google Drive, open the verification URL, and enter the displayed device code.",
        "Authorize the same Google Account whose Drive contains the ッツ sync folder.",
    )
}
