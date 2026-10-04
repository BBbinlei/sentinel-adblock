package com.sentinel.app.launch

import android.os.Parcel
import com.sentinel.app.fakes.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [35])
class OriginalEntryServiceTest {
    private fun call(code: Int, enabled: Boolean = false, token: String = OriginalEntryUserService.TOKEN): String? {
        val service = OriginalEntryUserService()
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(token)
            if (code == 1) data.writeInt(if (enabled) 1 else 0)
            data.setDataPosition(0)
            service.transact(code, data, reply, 0)
            reply.setDataPosition(0); reply.readException()
            return reply.readString()
        } finally { data.recycle(); reply.recycle() }
    }
    @Test fun shizukuConfigurationCanActuallyBeSerialized() {
        val args = originalEntryServiceArgs(org.robolectric.RuntimeEnvironment.getApplication())
        val method = args.javaClass.getDeclaredMethod("forAdd").apply { isAccessible = true }
        assertNotNull(method.invoke(args)) // Reproduces the missing processNameSuffix failure before binding.
    }
    @Test fun amErrorCannotBeReportedAsSuccessfulLaunchEvenWithZeroExitCode() {
        assertFalse(OriginalEntryUserService.commandSucceeded(0, "Starting: Intent\nError type 3\nError: Activity class does not exist"))
        assertFalse(OriginalEntryUserService.commandSucceeded(1, ""))
        assertTrue(OriginalEntryUserService.commandSucceeded(0, "Starting: Intent"))
    }
    @Test fun newServiceIsOffAndDisableIsIdempotent() {
        assertEquals("已关闭原图标自动转接", call(2))
        assertEquals("已关闭原图标自动转接", call(1, false))
    }
    @Test fun unsupportedSystemReturnsFailureRatherThanSuccess() {
        assertFailsWith<IllegalStateException> { call(1, true) }
    }
    @Test fun rejectsOtherBinderInterface() {
        assertFailsWith<SecurityException> { call(1, true, "external") }
    }
}
