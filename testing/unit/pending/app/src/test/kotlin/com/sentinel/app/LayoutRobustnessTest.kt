package com.sentinel.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import com.sentinel.app.fakes.*
import com.sentinel.app.ui.nav.Routes
import com.sentinel.data.db.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [35])
class LayoutRobustnessTest : ComposeAppTest() {
    private val pkg = "com.example.reader"
    private fun fixtures() {
        data.appsRows.value = listOf(data.app(pkg, "这是一个用于验证大字号换行的较长应用名称",
            observationEndsAt = data.clock.now() + 259_200_000))
        data.statusRows.value = listOf(EngineStatusEntity(EngineId.SYSTEM, EngineState.DEGRADED,
            "Shizuku 未激活（不影响已生效项）", data.clock.now()))
        data.overrideRows.value = listOf(RuleOverrideEntity(pkg, "long-rule",
            OverrideState.DISABLED, "应用疑似受影响，已自动降级", data.clock.now()))
    }

    @Test fun MT_AP_02_360dp_large_font_has_no_overflow_or_ellipsis() {
        fixtures(); render(largeFont = true)
        checkHomeDetailCleanup(::assertTextFits)
    }

    @Test fun MT_AP_02_dark_mode_renders_home_detail_and_cleanup() {
        fixtures(); render(dark = true, largeFont = true)
        checkHomeDetailCleanup(::assertTextFits)
    }

    @Test fun MT_AP_05_every_click_target_is_at_least_48dp() {
        fixtures(); render(largeFont = true)
        val pages = listOf(Routes.HOME, Routes.APPS,
            Routes.APP_DETAIL.replace("{pkg}", pkg), Routes.RULES, Routes.SYSTEM_CLEANUP,
            Routes.ENGINE_LOG.replace("{engine}", "VPN"), Routes.OP_LOG, Routes.ONBOARDING)
        pages.forEach { route -> navigate(route); scanPage(route, ::assertTouchTargets) }
        navigate(Routes.APP_DETAIL.replace("{pkg}", pkg))
        compose.onNodeWithTag("app-detail:advanced").performClick()
        scanPage("expanded app detail", ::assertTouchTargets)
        navigate(Routes.SYSTEM_CLEANUP)
        compose.onNodeWithTag("cleanup:uninstall").performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        assertTouchTargets("uninstall confirmation")
    }

    private fun checkHomeDetailCleanup(check: (String) -> Unit) {
        compose.onNodeWithTag("screen:home").assertIsDisplayed()
        scanPage("home", check)
        openDetail(pkg)
        scanPage("detail", check)
        compose.onNodeWithTag("app-detail:advanced").performClick()
        scanPage("expanded detail", check)
        navigate(Routes.HOME); openSystem()
        scanPage("cleanup", check)
    }

    /** Visit scroll content as well as the first viewport; a 50-page ceiling is a failure. */
    private fun scanPage(label: String, check: (String) -> Unit) {
        check(label)
        val scrolls = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
            useUnmergedTree = true).fetchSemanticsNodes().map { it.id }
        scrolls.forEach { id ->
            val interaction = compose.onNode(SemanticsMatcher("node $id") { it.id == id }, useUnmergedTree = true)
            // A new navigation destination normally starts at 0; reset explicitly after prior scans.
            interaction.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
            compose.waitForIdle()
            var ended = false
            repeat(50) {
                if (!ended) {
                    check(label)
                    val before = interaction.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                    val height = interaction.fetchSemanticsNode().boundsInRoot.height
                    interaction.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, height * 0.75f) }
                    compose.waitForIdle()
                    val after = interaction.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                    ended = after == before
                }
            }
            assertTrue(ended, "$label: scroll did not reach its end in 50 viewports")
            interaction.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
            compose.waitForIdle()
        }
    }

    private fun assertTextFits(label: String) {
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
            useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(texts.isNotEmpty(), "$label must contain actual rendered text")
        texts.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
            assertNotNull(action, "$label: no layout result for ${node.config}")
            compose.runOnIdle { assertTrue(action(layouts)) }
            assertTrue(layouts.isNotEmpty(), "$label: text measurement missing")
            layouts.forEach { result ->
                assertFalse(result.hasVisualOverflow, "$label: clipped text: ${result.layoutInput.text}")
                if (node.layoutInfo.isPlaced && node.boundsInRoot.width > 0 && node.boundsInRoot.height > 0) {
                    assertTrue(result.size.width <= node.boundsInRoot.width + 1f,
                        "$label: text clipped horizontally by its parent: ${result.layoutInput.text}")
                }
                for (line in 0 until result.lineCount) {
                    assertFalse(result.isLineEllipsized(line), "$label: ellipsis: ${result.layoutInput.text}")
                }
            }
        }
    }

    private fun assertTouchTargets(label: String) {
        val clickable = compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(clickable.isNotEmpty(), "$label must contain actual clickable controls")
        // Scroll viewports can expose a fragment of a correctly sized button.
        // Scan overlapping viewports and measure each control when its layout is fully visible.
        val visible = clickable.filter { node ->
            node.layoutInfo.isPlaced && node.boundsInRoot.width > 0 && node.boundsInRoot.height > 0 &&
                node.boundsInRoot.width + 1f >= node.layoutInfo.width &&
                node.boundsInRoot.height + 1f >= node.layoutInfo.height
        }
        assertTrue(visible.isNotEmpty(), "$label must expose a complete clickable control")
        visible.forEach { node ->
            val bounds = node.touchBoundsInRoot
            val density = node.layoutInfo.density.density
            val widthDp = bounds.width / density
            val heightDp = bounds.height / density
            assertTrue(widthDp >= 48f - 0.01f && heightDp >= 48f - 0.01f,
                "$label: node ${node.id} touch target is ${widthDp}x${heightDp}dp; ${node.config}")
        }
    }
}
