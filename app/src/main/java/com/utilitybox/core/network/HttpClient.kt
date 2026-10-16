package com.utilitybox.core.network

import com.utilitybox.domain.model.AppError

/**
 * 网络层的最小契约。
 *
 * 依据设计稿「统一网络层，**业务模块不直接依赖具体 HTTP 实现**」：
 * 业务模块只依赖本接口，未来要把 `HttpURLConnection` 换成别的实现，
 * 只需新增一个 [HttpClient] 实现，业务代码 0 改动。
 *
 * 也因为它是接口，单元测试可以注入假实现 —— 不需要真实网络，也不需要 Android 框架。
 */
interface HttpClient {
    suspend fun get(
        path: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): HttpResult
}

/** 一次 HTTP 调用的结果。刻意区分「网络层失败」与「业务层失败」，便于各自归因。 */
sealed interface HttpResult {
    data class Success(val body: String) : HttpResult

    /** 网络层失败（无连接、超时、5xx、坏 JSON 由调用方判定） */
    data class Failure(val error: AppError) : HttpResult
}

/**
 * 配置。`baseUrl` 与鉴权头**全配置化**，不写死在业务代码里 ——
 * 这是「数据源可整体替换」要求的前提（见 DATA_SOURCES.md：社区服务可能随时停服）。
 */
data class HttpConfig(
    val baseUrl: String,
    /** 每次请求都会附带的头（例如 API Key） */
    val defaultHeaders: Map<String, String> = emptyMap(),
    val connectTimeoutMs: Int = 10_000,
    val readTimeoutMs: Int = 20_000,
)
