package com.sentinel.app.launch

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sentinel.app.fakes.TestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApplication::class, sdk = [35])
class LaunchShortcutCardTest {
    @get:Rule val compose = createComposeRule()
    private val profile = LaunchProfile("test", "com.test", "测试", 1, "1", "Home", null, true, "冷启动", "证据")
    @Test fun verifiedShowsWorkingCreateButton() {
        var clicks = 0
        compose.setContent { LaunchShortcutContent(profile, LaunchStatus.VERIFIED) { clicks++ } }
        compose.onNodeWithText("已验证").assertIsDisplayed()
        compose.onNodeWithTag("launch:create").assertIsEnabled().performClick()
        assertEquals(1, clicks)
    }
    @Test fun pendingHasNoExperimentalButton() {
        compose.setContent { LaunchShortcutContent(profile.copy(verified = false), LaunchStatus.PENDING) }
        compose.onNodeWithText("待验证").assertIsDisplayed()
        compose.onNodeWithTag("launch:create").assertDoesNotExist()
    }
    @Test fun unsupportedHasNoCreateButton() {
        compose.setContent { LaunchShortcutContent(profile, LaunchStatus.UNSUPPORTED) }
        compose.onNodeWithText("当前版本不支持").assertIsDisplayed()
        compose.onNodeWithTag("launch:create").assertDoesNotExist()
    }
    @Test fun yangshipinExplainsSplashAndPopupSeparately() {
        val p = LaunchCatalog.policy.byId("yangshipin")!!
        compose.setContent { LaunchShortcutContent(p, LaunchStatus.VERIFIED) }
        compose.onNodeWithTag("launch:create").assertIsEnabled()
        compose.onNodeWithTag("launch:notice").assertTextContains("首页弹窗仍可能短暂出现", substring = true)
        compose.onNodeWithTag("launch:notice").assertTextContains("Shizuku 自动转接", substring = true)
    }
    @Test fun waitingRequestDoesNotShowAddedSuccess() {
        compose.setContent { LaunchShortcutContent(profile, LaunchStatus.VERIFIED, PinFeedback.REQUESTED, true) }
        compose.onNodeWithTag("launch:create").assertIsNotEnabled()
        compose.onNodeWithTag("launch:feedback").assertTextContains("尚未确认添加成功。", substring = true)
        compose.onNodeWithText("已确认添加到桌面").assertDoesNotExist()
    }
}
