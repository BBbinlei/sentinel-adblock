package com.sentinel.vpn.service

import com.sentinel.vpn.tun.TunSpec
import java.io.*
interface TunFactory { fun establish(spec: TunSpec): TunHandle? }
interface TunHandle : Closeable { val input: InputStream; val output: OutputStream }
