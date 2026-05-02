package moe.antimony.hoshi.features.reader

import android.view.KeyEvent

internal fun readerNavigationDirectionForKeyEvent(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    settings: ReaderSettings,
): ReaderNavigationDirection? {
    if (action != KeyEvent.ACTION_DOWN || repeatCount != 0) return null
    return when (keyCode) {
        KeyEvent.KEYCODE_PAGE_DOWN -> ReaderNavigationDirection.Forward
        KeyEvent.KEYCODE_PAGE_UP -> ReaderNavigationDirection.Backward
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (settings.volumeKeysTurnPages) {
            if (settings.reverseVolumeKeyDirection) {
                ReaderNavigationDirection.Backward
            } else {
                ReaderNavigationDirection.Forward
            }
        } else {
            null
        }
        KeyEvent.KEYCODE_VOLUME_UP -> if (settings.volumeKeysTurnPages) {
            if (settings.reverseVolumeKeyDirection) {
                ReaderNavigationDirection.Forward
            } else {
                ReaderNavigationDirection.Backward
            }
        } else {
            null
        }
        else -> null
    }
}
