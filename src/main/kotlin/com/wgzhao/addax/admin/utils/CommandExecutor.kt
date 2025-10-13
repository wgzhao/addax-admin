package com.wgzhao.addax.admin.utils

import com.wgzhao.addax.admin.dto.TaskResultDto
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.failure
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.success
import lombok.extern.slf4j.Slf4j
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * 命令执行工具类。
 * 提供执行 shell 命令并返回标准化结果的方法。
 */
@Slf4j
object CommandExecutor {
    /**
     * 执行指定的 shell 命令，并返回执行结果。
     *
     *
     * 标准输出内容会打印到控制台，错误输出内容会作为失败信息返回。
     * 返回的 TaskResultDto 包含执行状态和耗时（秒）。
     *
     *
     * @param command 要执行的 shell 命令字符串
     * @return TaskResultDto 执行结果对象，包含成功/失败信息及耗时
     */
    fun executeWithResult(command: String?): TaskResultDto {
        val startAt = System.currentTimeMillis()
        try {
            // 使用 bash 执行命令，支持管道等 shell 特性
            val process = Runtime.getRuntime().exec(arrayOf<String?>("bash", "-c", command))
            BufferedReader(InputStreamReader(process.getInputStream())).use { reader ->
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    println(line)
                }
            }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                // 读取错误输出
                val errLine = StringBuilder()
                BufferedReader(InputStreamReader(process.getErrorStream())).use { errorReader ->
                    var line: String?
                    while ((errorReader.readLine().also { line = it }) != null) {
                        errLine.append(line).append("\n")
                    }
                }
                return failure(errLine.toString(), (System.currentTimeMillis() - startAt) / 1000)
            } else {
                return success("SUCCESS", (System.currentTimeMillis() - startAt) / 1000)
            }
        } catch (e: IOException) {
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            CommandExecutor.log.error("execute command failed: {}", command, e)
            return TaskResultDto.failure(e.message!!, (System.currentTimeMillis() - startAt) / 1000)
        } catch (e: InterruptedException) {
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            CommandExecutor.log.error("execute command failed: {}", command, e)
            return TaskResultDto.failure(e.message!!, (System.currentTimeMillis() - startAt) / 1000)
        }
    }
}