package com.sentinel.system.fakes

import com.sentinel.system.shell.ShizukuApi

class FakeShizukuApi(
    var installed: Boolean = true,
    var running: Boolean = true,
    var permitted: Boolean = true,
) : ShizukuApi {
    override fun isInstalled() = installed
    override fun pingBinder() = running
    override fun checkSelfPermission() = permitted
}

