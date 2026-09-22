package moe.antimony.hoshi.features.reader

import android.view.KeyEvent

internal enum class PopupTermNavigationDirection {
    Previous,
    Next,
}

internal sealed interface ReaderHardwareKeyAction {
    data class ReaderNavigation(val direction: ReaderNavigationDirection) : ReaderHardwareKeyAction
    data class PopupTermNavigation(val direction: PopupTermNavigationDirection) : ReaderHardwareKeyAction
    data object SasayakiSeekForward : ReaderHardwareKeyAction
    data object SasayakiSeekBackward : ReaderHardwareKeyAction
    data class ReadAloudSentenceNavigation(val direction: ReadAloudSentenceNavigationDirection) : ReaderHardwareKeyAction
}

internal enum class ReadAloudSentenceNavigationDirection {
    Previous,
    Next,
}

internal data class ReaderHardwareKeyEventResult(
    val consumed: Boolean,
    val action: ReaderHardwareKeyAction? = null,
)

internal fun readerNavigationDirectionForKeyEvent(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    settings: ReaderSettings,
): ReaderNavigationDirection? =
    (readerHardwareKeyEventForKeyEvent(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        settings = settings,
        sasayakiEnabled = false,
        hasSasayakiAudio = false,
        readAloudActive = false,
    ).action as? ReaderHardwareKeyAction.ReaderNavigation)?.direction

internal fun readerHardwareKeyActionForKeyEvent(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    settings: ReaderSettings,
    sasayakiEnabled: Boolean,
    hasSasayakiAudio: Boolean,
    hasLookupPopup: Boolean = false,
    readAloudActive: Boolean = false,
): ReaderHardwareKeyAction? =
    readerHardwareKeyEventForKeyEvent(
        keyCode = keyCode,
        action = action,
        repeatCount = repeatCount,
        settings = settings,
        sasayakiEnabled = sasayakiEnabled,
        hasSasayakiAudio = hasSasayakiAudio,
        hasLookupPopup = hasLookupPopup,
        readAloudActive = readAloudActive,
    ).action

internal fun readerHardwareKeyEventForKeyEvent(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    settings: ReaderSettings,
    sasayakiEnabled: Boolean,
    hasSasayakiAudio: Boolean,
    hasLookupPopup: Boolean = false,
    readAloudActive: Boolean = false,
): ReaderHardwareKeyEventResult {
    return when (keyCode) {
        KeyEvent.KEYCODE_PAGE_DOWN -> pageKeyResult(
            action = action,
            repeatCount = repeatCount,
            direction = ReaderNavigationDirection.Forward,
        )
        KeyEvent.KEYCODE_PAGE_UP -> pageKeyResult(
            action = action,
            repeatCount = repeatCount,
            direction = ReaderNavigationDirection.Backward,
        )
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_UP,
        -> volumeKeyResult(
            keyCode = keyCode,
            action = action,
            settings = settings,
            sasayakiEnabled = sasayakiEnabled,
            hasSasayakiAudio = hasSasayakiAudio,
            hasLookupPopup = hasLookupPopup,
            readAloudActive = readAloudActive,
        )
        else -> ReaderHardwareKeyEventResult(consumed = false)
    }
}

private fun pageKeyResult(
    action: Int,
    repeatCount: Int,
    direction: ReaderNavigationDirection,
): ReaderHardwareKeyEventResult {
    if (action != KeyEvent.ACTION_DOWN || repeatCount != 0) {
        return ReaderHardwareKeyEventResult(consumed = false)
    }
    return ReaderHardwareKeyEventResult(
        consumed = true,
        action = ReaderHardwareKeyAction.ReaderNavigation(direction),
    )
}

private fun volumeKeyResult(
    keyCode: Int,
    action: Int,
    settings: ReaderSettings,
    sasayakiEnabled: Boolean,
    hasSasayakiAudio: Boolean,
    hasLookupPopup: Boolean,
    readAloudActive: Boolean,
): ReaderHardwareKeyEventResult {
    val keyAction = readerVolumeKeyAction(
        keyCode = keyCode,
        settings = settings,
        sasayakiEnabled = sasayakiEnabled,
        hasSasayakiAudio = hasSasayakiAudio,
        hasLookupPopup = hasLookupPopup,
        readAloudActive = readAloudActive,
    )
    keyAction ?: return ReaderHardwareKeyEventResult(consumed = false)
    return ReaderHardwareKeyEventResult(
        consumed = true,
        action = keyAction.takeIf { action == KeyEvent.ACTION_DOWN },
    )
}

private fun readerVolumeKeyAction(
    keyCode: Int,
    settings: ReaderSettings,
    sasayakiEnabled: Boolean,
    hasSasayakiAudio: Boolean,
    hasLookupPopup: Boolean,
    readAloudActive: Boolean,
): ReaderHardwareKeyAction? {
    if (settings.volumeKeysControlReadAloud && readAloudActive) {
        return ReaderHardwareKeyAction.ReadAloudSentenceNavigation(
            readAloudSentenceDirectionForVolumeKey(
                keyCode = keyCode,
                reverseDirection = settings.reverseVolumeKeyDirection,
            ),
        )
    }
    if (settings.volumeKeysNavigatePopupTerms && hasLookupPopup) {
        return ReaderHardwareKeyAction.PopupTermNavigation(
            popupTermNavigationDirectionForVolumeKey(
                keyCode = keyCode,
                reverseDirection = settings.reverseVolumeKeyDirection,
            ),
        )
    }
    if (settings.volumeKeysSeekSasayaki && sasayakiEnabled && hasSasayakiAudio) {
        return sasayakiSeekActionForVolumeKey(
            keyCode = keyCode,
            reverseDirection = settings.reverseVolumeKeyDirection,
        )
    }
    if (!settings.volumeKeysTurnPages) return null
    return ReaderHardwareKeyAction.ReaderNavigation(
        volumePageTurnDirectionForKey(
            keyCode = keyCode,
            reverseDirection = settings.reverseVolumeKeyDirection,
        ),
    )
}

private fun readAloudSentenceDirectionForVolumeKey(
    keyCode: Int,
    reverseDirection: Boolean,
): ReadAloudSentenceNavigationDirection =
    when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> if (reverseDirection) {
            ReadAloudSentenceNavigationDirection.Previous
        } else {
            ReadAloudSentenceNavigationDirection.Next
        }
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (reverseDirection) {
            ReadAloudSentenceNavigationDirection.Next
        } else {
            ReadAloudSentenceNavigationDirection.Previous
        }
        else -> error("Unsupported volume key: $keyCode")
    }

private fun popupTermNavigationDirectionForVolumeKey(
    keyCode: Int,
    reverseDirection: Boolean,
): PopupTermNavigationDirection =
    when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> if (reverseDirection) {
            PopupTermNavigationDirection.Next
        } else {
            PopupTermNavigationDirection.Previous
        }
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (reverseDirection) {
            PopupTermNavigationDirection.Previous
        } else {
            PopupTermNavigationDirection.Next
        }
        else -> error("Unsupported volume key: $keyCode")
    }

private fun sasayakiSeekActionForVolumeKey(
    keyCode: Int,
    reverseDirection: Boolean,
): ReaderHardwareKeyAction =
    when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> if (reverseDirection) {
            ReaderHardwareKeyAction.SasayakiSeekForward
        } else {
            ReaderHardwareKeyAction.SasayakiSeekBackward
        }
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (reverseDirection) {
            ReaderHardwareKeyAction.SasayakiSeekBackward
        } else {
            ReaderHardwareKeyAction.SasayakiSeekForward
        }
        else -> error("Unsupported volume key: $keyCode")
    }

private fun volumePageTurnDirectionForKey(
    keyCode: Int,
    reverseDirection: Boolean,
): ReaderNavigationDirection =
    when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (reverseDirection) {
            ReaderNavigationDirection.Backward
        } else {
            ReaderNavigationDirection.Forward
        }
        KeyEvent.KEYCODE_VOLUME_UP -> if (reverseDirection) {
            ReaderNavigationDirection.Forward
        } else {
            ReaderNavigationDirection.Backward
        }
        else -> error("Unsupported volume key: $keyCode")
    }
