package com.sentinel.guard.di

import com.sentinel.guard.runtime.AndroidGuardNotifier
import com.sentinel.guard.runtime.GuardNotifier
import com.sentinel.guard.runtime.GuardRunner
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val guardModule = module {
    single<GuardNotifier> { AndroidGuardNotifier(androidContext()) }
    single { GuardRunner(get(), get(), get(), get(), get()) }
}
