package com.sentinel.app

import androidx.compose.ui.test.*
import com.sentinel.app.fakes.*
import com.sentinel.app.ui.nav.Routes
import com.sentinel.data.db.SignalKind
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApplication::class, sdk = [35])
class AppFlowsTest : ComposeAppTest() {
    @Test fun MT_AP_01_temp_allow_takes_two_clicks_without_confirmation() {
        val app = data.app()
        data.appsRows.value = listOf(app)
        render(); openApps()
        // The two counted clicks start at the application list.
        compose.onNodeWithTag("app:${app.pkg}").performClick()
        compose.onNodeWithTag("screen:app-detail").assertIsDisplayed()
        compose.onNode(hasText("一键临时放行", substring = true)).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(data.clock.now() + 86_400_000L, data.appsRows.value.single().tempAllowUntil)
        assertEquals(listOf(app.pkg), data.signalRows.value.filter { it.kind == SignalKind.TEMP_ALLOW }.map { it.pkg })
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithTag("temp-allow:confirmation").assertDoesNotExist()
    }

    @Test fun MT_AP_03_unfinished_setup_starts_onboarding() {
        render(onboardingDone = false, startup = true)
        compose.onNodeWithTag("screen:onboarding").assertIsDisplayed()
        compose.onNodeWithTag("screen:home").assertDoesNotExist()
    }

    @Test fun MT_AP_03_finished_setup_starts_home() {
        render(onboardingDone = true, startup = true)
        compose.onNodeWithTag("screen:home").assertIsDisplayed()
        compose.onNodeWithTag("screen:onboarding").assertDoesNotExist()
    }

    @Test fun MT_AP_03_log_pages_offer_a_back_action() {
        render()
        for (route in listOf(Routes.ENGINE_LOG.replace("{engine}", "VPN"), Routes.OP_LOG)) {
            navigate(route)
            compose.onNodeWithTag("nav:back").assertIsDisplayed().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("screen:home").assertIsDisplayed()
        }
    }

    @Test fun MT_AP_04_uninstall_requires_confirmation_and_cancel_has_no_effect() {
        render(); openSystem()
        compose.onNodeWithTag("cleanup:uninstall").performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        assertFalse("apply uninstall" in system.shell.commands)
        compose.onNodeWithTag("cleanup:cancel").performClick()
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        assertFalse("apply uninstall" in system.shell.commands)
        compose.onNodeWithTag("cleanup:uninstall").performClick()
        compose.onNodeWithTag("cleanup:confirm").performClick()
        compose.waitForIdle()
        assertEquals(1, system.shell.commands.count { it == "apply uninstall" })
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test fun MT_AP_04_other_operations_execute_without_confirmation() {
        render(); openSystem()
        compose.onNodeWithTag("cleanup:normal").performClick()
        compose.waitForIdle()
        assertEquals(1, system.shell.commands.count { it == "apply normal" })
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }
}
