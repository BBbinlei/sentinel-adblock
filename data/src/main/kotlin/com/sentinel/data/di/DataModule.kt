package com.sentinel.data.di

import com.sentinel.data.Clock
import com.sentinel.data.apps.AppRegistry
import com.sentinel.data.db.SentinelDatabase
import com.sentinel.data.repo.*
import com.sentinel.data.rules.*
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

val dataModule = module {
    single<Clock> { Clock(System::currentTimeMillis) }
    single { SentinelDatabase.build(androidContext()) }
    single { get<SentinelDatabase>().appConfigDao() }
    single { get<SentinelDatabase>().globalStateDao() }
    single { get<SentinelDatabase>().subscriptionDao() }
    single { get<SentinelDatabase>().userRuleDao() }
    single { get<SentinelDatabase>().eventDao() }
    single { get<SentinelDatabase>().signalDao() }
    single { get<SentinelDatabase>().overrideDao() }
    single { get<SentinelDatabase>().opLogDao() }
    single { get<SentinelDatabase>().jumpExceptionDao() }
    single { get<SentinelDatabase>().rewardWindowDao() }
    single { get<SentinelDatabase>().engineStatusDao() }
    single { GlobalStateRepository(get(), get()) }
    single { EventRepository(get(), get()) }
    single { SignalRepository(get(), get()) }
    single { OverrideRepository(get(), get()) }
    single { OpLogRepository(get(), get()) }
    single { JumpExceptionRepository(get(), get()) }
    single { RewardWindowRepository(get(), get()) }
    single { EngineStatusRepository(get(), get()) }
    single { UserRuleRepository(get(), get()) }
    single { AppConfigRepository(get(), get(), get(), get()) }
    single { AppRegistry(get(), get()) }
    single { RuleStore(File(androidContext().filesDir, "rules"), get()) }
    single { OkHttpClient() }
    single { SubscriptionUpdater(get(), get(), get(), File(androidContext().filesDir, "subs"), get(), get()) }
}
