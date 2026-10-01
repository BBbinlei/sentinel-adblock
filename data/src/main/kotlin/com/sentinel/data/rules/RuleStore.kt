package com.sentinel.data.rules

import com.sentinel.data.repo.GlobalStateRepository
import com.sentinel.rules.domain.DomainMatcher
import com.sentinel.rules.model.*
import com.sentinel.rules.parse.JsonRuleParser
import com.sentinel.rules.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*

data class BuiltRules(val domainBytes: ByteArray, val uiJson: String, val notifyJson: String, val stats: Map<String, Int>)
class RuleInstallException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

class RuleStore(private val dir: File, private val global: GlobalStateRepository) {
    companion object {
        // ponytail: installs serialize in each process; use per-directory mutexes if multiple stores are needed.
        private val installs = Mutex()
    }
    private val names = listOf("domains.bin", "ui.json", "notify.json")
    private fun previous(name: String): String = name.substringBeforeLast('.') + ".prev." + name.substringAfterLast('.')
    private fun move(from: File, to: File) { Files.move(from.toPath(), to.toPath(), ATOMIC_MOVE, REPLACE_EXISTING) }
    private fun write(file: File, bytes: ByteArray) = FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    private fun validate(name: String, bytes: ByteArray) {
        when (name) {
            "domains.bin" -> require(DomainMatcher.verify(bytes)) { "域名二进制损坏" }
            "ui.json" -> {
                val rules = JsonRuleParser.parse(bytes.toString(Charsets.UTF_8))
                require(rules.all { it is UiRule }) { "UI 文件含其他类型规则" }
                require(UiRuleIndex.build(rules.filterIsInstance<UiRule>()).invalidCount == 0) { "UI 选择器无效" }
            }
            "notify.json" -> require(JsonRuleParser.parse(bytes.toString(Charsets.UTF_8)).all { it is NotifyRule }) { "通知文件含其他类型规则" }
        }
    }
    suspend fun install(built: BuiltRules): Long = withContext(Dispatchers.IO) {
        installs.withLock {
            try {
                check(dir.isDirectory || dir.mkdirs()) { "无法创建规则目录" }
                FileChannel.open(File(dir, ".install.lock").toPath(), CREATE, WRITE).use { channel ->
                    channel.lock().use {
                        val files = names.flatMap { listOf(it, previous(it)) }
                        val before = files.associateWith { name -> File(dir, name).takeIf { it.isFile }?.readBytes() }
                        var replacing = false
                        try {
                            val bytes = listOf(built.domainBytes, built.uiJson.toByteArray(), built.notifyJson.toByteArray())
                            names.zip(bytes).forEach { (name, content) -> write(File(dir, "$name.tmp"), content) }
                            names.forEach { name -> validate(name, File(dir, "$name.tmp").readBytes()) }
                            replacing = true
                            for (name in names) {
                                val current = File(dir, name)
                                if (current.isFile && runCatching { validate(name, current.readBytes()) }.isSuccess) move(current, File(dir, previous(name)))
                                move(File(dir, "$name.tmp"), current)
                            }
                            global.bumpRuleVersion()
                        } catch (e: Exception) {
                            if (replacing) files.forEach { name ->
                                try {
                                    val content = before[name]
                                    if (content == null) Files.deleteIfExists(File(dir, name).toPath())
                                    else { write(File(dir, "$name.restore.tmp"), content); move(File(dir, "$name.restore.tmp"), File(dir, name)) }
                                } catch (restore: Exception) { e.addSuppressed(restore) }
                            }
                            throw e
                        } finally { dir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() } }
                    }
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                throw RuleInstallException("规则安装失败: ${e.message}", e)
            }
        }
    }
    // Readers roll back to the previous valid file without writing from the VPN process.
    private fun <T> load(name: String, parse: (ByteArray) -> T): T? {
        for (candidate in listOf(name, previous(name))) {
            try {
                val bytes = File(dir, candidate).readBytes()
                validate(name, bytes)
                return parse(bytes)
            } catch (_: Exception) { /* Try the previous version, then the safe default. */ }
        }
        return null
    }
    fun loadDomainMatcher(): DomainMatcher? = load("domains.bin") { DomainMatcher(java.nio.ByteBuffer.wrap(it)) }
    fun loadUiIndex(): UiRuleIndex = load("ui.json") { UiRuleIndex.build(JsonRuleParser.parse(it.toString(Charsets.UTF_8)).filterIsInstance<UiRule>()) }
        ?: UiRuleIndex.build(BuiltInUiRules.all)
    fun loadNotifyRules(): List<NotifyRule> = load("notify.json") { JsonRuleParser.parse(it.toString(Charsets.UTF_8)).filterIsInstance<NotifyRule>() } ?: emptyList()
}
