package com.sentinel.data.repo

import com.sentinel.data.Clock
import com.sentinel.data.db.*
import kotlinx.coroutines.flow.*

class JumpExceptionRepository(private val dao: JumpExceptionDao, private val clock: Clock) {
    suspend fun isExcepted(src: String, tgt: String): Boolean = dao.isExcepted(src, tgt)
    suspend fun add(src: String, tgt: String) = dao.upsert(JumpExceptionEntity(src, tgt))
}
