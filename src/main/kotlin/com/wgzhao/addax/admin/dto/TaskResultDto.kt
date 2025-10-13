package com.wgzhao.addax.admin.dto

data class TaskResultDto(
    val success: Boolean = false,
    val message: String? = null,
    val durationSeconds: Long = 0
) {
    companion object {
        @JvmStatic
        fun success(message: String, durationSeconds: Long): TaskResultDto {
            return TaskResultDto(true, message, durationSeconds)
        }

        @JvmStatic
        fun failure(message: String, durationSeconds: Long): TaskResultDto {
            return TaskResultDto(false, message, durationSeconds)
        }
    }
}
