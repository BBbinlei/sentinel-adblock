package com.sentinel.app.fakes

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.core.context.stopKoin

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    val dispatcher = UnconfinedTestDispatcher()
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}

@OptIn(ExperimentalCoroutinesApi::class)
abstract class AppTest {
    @get:Rule val main = MainDispatcherRule()
    val data = FakeData()
    private val viewModels = ViewModelStore()
    fun <T : ViewModel> keep(vm: T): T = vm.also {
        viewModels.put("${vm.javaClass.name}:${System.identityHashCode(vm)}", vm)
    }
    fun TestScope.watch(state: StateFlow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
        runCurrent()
    }
    @After fun cleanUp() { viewModels.clear(); stopKoin(); data.close() }
}

/** Prevent production Application/module startup in tests other than startup-isolation. */
class TestApplication : Application()
