package moe.antimony.hoshi.features.readaloud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.session.MediaSession
import moe.antimony.hoshi.R

internal const val ReadAloudNotificationId = 1002
internal const val ReadAloudNotificationChannelId = "hoshi_read_aloud"

internal data class ReadAloudActionIntents(
    val prev: PendingIntent,
    val play: PendingIntent,
    val pause: PendingIntent,
    val stop: PendingIntent,
    val next: PendingIntent,
    val timer: PendingIntent,
    val mediaSessionToken: MediaSession.Token,
)

internal fun buildReadAloudNotification(
    context: Context,
    state: ReadAloudState,
    contentIntent: PendingIntent,
    intents: ReadAloudActionIntents,
): Notification {
    ensureReadAloudChannel(context)
    val playing = state.isPlaying
    val builder = Notification.Builder(context, ReadAloudNotificationChannelId)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setCategory(Notification.CATEGORY_TRANSPORT)
        .setSmallIcon(R.drawable.ic_stat_hoshi)
        .setContentTitle(state.title ?: context.getString(R.string.read_aloud_menu))
        .setContentText(
            state.subtitle?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.read_aloud_playing),
        )
        .setContentIntent(contentIntent)
        .setOngoing(playing)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .addAction(
            R.drawable.ic_read_aloud_prev,
            context.getString(R.string.read_aloud_previous_sentence),
            intents.prev,
        )
        .addAction(
            if (playing) R.drawable.ic_read_aloud_pause else R.drawable.ic_read_aloud_play,
            context.getString(if (playing) R.string.read_aloud_pause else R.string.read_aloud_play),
            if (playing) intents.pause else intents.play,
        )
        .addAction(
            R.drawable.ic_read_aloud_stop,
            context.getString(R.string.read_aloud_stop),
            intents.stop,
        )
        .addAction(
            R.drawable.ic_read_aloud_next,
            context.getString(R.string.read_aloud_next_sentence),
            intents.next,
        )
        .addAction(
            R.drawable.ic_read_aloud_timer,
            timerLabel(context, state.sleepTimerMinutes),
            intents.timer,
        )
        .setStyle(
            Notification.MediaStyle()
                .setShowActionsInCompactView(0, 1, 3)
                .setMediaSession(intents.mediaSessionToken),
        )
    return builder.build()
}

private fun timerLabel(context: Context, minutes: Int): String {
    return if (minutes > 0) {
        context.getString(R.string.read_aloud_timer_minutes, minutes)
    } else {
        context.getString(R.string.read_aloud_timer)
    }
}

private fun ensureReadAloudChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(ReadAloudNotificationChannelId) != null) return
    val channel = NotificationChannel(
        ReadAloudNotificationChannelId,
        context.getString(R.string.read_aloud),
        NotificationManager.IMPORTANCE_LOW,
    ).apply {
        setSound(null, null)
        enableVibration(false)
        setShowBadge(false)
    }
    manager.createNotificationChannel(channel)
}
