package com.utilitybox.domain.datasource

import com.utilitybox.domain.model.AppError

/**
 * 数据源调用的统一结果。
 *
 * 与 `UiState` 的区别：[DataResult] 是**数据层**的结果（"这次去取数据成不成"），
 * `UiState` 是**界面层**的状态（含"加载中"这类界面概念）。两层分开，避免概念混淆。
 *
 * ★ [Unavailable] 是刻意存在的第三态，用来表达
 *   「**这个数据源根本无法提供该能力**」，与「这次请求失败了」严格区分。
 *   例如好友状态：不是请求失败，而是**没有数据源**。
 *   设计稿要求不得用推测填充，这个类型就是把该要求落进类型系统。
 */
sealed interface DataResult<out T> {
    data class Ok<T>(val value: T) : DataResult<T>

    /** 这次调用失败了（可重试） */
    data class Err(val error: AppError) : DataResult<Nothing>

    /** 该能力在当前数据源下**不可用**（重试也没用） */
    data class Unavailable(val reason: String) : DataResult<Nothing>
}

/** 便捷变换：只在 [DataResult.Ok] 上做映射，其余状态原样透传 */
inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Ok -> DataResult.Ok(transform(value))
    is DataResult.Err -> this
    is DataResult.Unavailable -> this
}

/** 取成功值；失败/不可用时返回 null（调用方需自行处理降级） */
fun <T> DataResult<T>.getOrNull(): T? = (this as? DataResult.Ok)?.value
