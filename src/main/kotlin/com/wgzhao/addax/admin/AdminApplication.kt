package com.wgzhao.addax.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableAsync
@EnableScheduling
object AdminApplication {
    fun main(args: Array<String>) {
        runApplication<AdminApplication>(*args)
    }
}
