package com.sentinel.notify.service

import android.app.Notification
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import android.text.SpannedString
import com.sentinel.notify.core.PostedNotification
import com.sentinel.notify.fakes.MARKET_PKG
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ListenerMappingTest {
    // UT-NT-4-01
    @Test fun UT_NT_4_01_to_posted_maps_extras_flags_channel_package_and_key() {
        val context = RuntimeEnvironment.getApplication()
        val notification = Notification.Builder(context, "marketing")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(SpannedString("限时福利"))
            .setContentText(SpannedString("领取优惠"))
            .setOngoing(true)
            .build()
        assertEquals("限时福利", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("领取优惠", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        val sbn = posted(notification, 7)
        assertEquals(PostedNotification(MARKET_PKG, "marketing", "限时福利", "领取优惠", true, sbn.key),
            sbn.toPosted())

        // 其他 flag 不等于 ongoing；缺失 extra/channel 必须保留 null，不能变成字符串 "null"。
        @Suppress("DEPRECATION")
        val empty = Notification()
        empty.flags = Notification.FLAG_AUTO_CANCEL
        val emptySbn = posted(empty, 8)
        assertEquals(PostedNotification(MARKET_PKG, null, null, null, false, emptySbn.key),
            emptySbn.toPosted())
    }

    private fun posted(notification: Notification, id: Int) = StatusBarNotification(
        MARKET_PKG, MARKET_PKG, id, "tag", 10_001, 123, notification, UserHandle.of(0), null, 1_000L,
    )
}
