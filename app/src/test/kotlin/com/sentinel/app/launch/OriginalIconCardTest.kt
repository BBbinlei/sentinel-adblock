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
class OriginalIconCardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unsupportedVersionCannotEnableButCanStopOldService() {
        var disabled = 0
        compose.setContent { OriginalIconContent("当前版本不支持", false, {}, { disabled++ }) }
        compose.onNodeWithTag("original:enable").assertDoesNotExist()
        compose.onNodeWithTag("original:disable").performClick()
        assertEquals(1, disabled)
    }
    @Test fun permissionAndActualRunningStatusAreDistinct() {
        var enabled = 0
        compose.setContent { OriginalIconContent("需要 Shizuku 授权", true, { enabled++ }, {}) }
        compose.onNodeWithTag("original:state").assertTextEquals("需要 Shizuku 授权")
        compose.onNodeWithTag("original:enable").performClick()
        assertEquals(1, enabled)
        compose.onNodeWithText("已启用原图标自动转接").assertDoesNotExist()
    }
}
