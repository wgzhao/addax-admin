package com.wgzhao.addax.admin

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableAsync
@EnableScheduling
object AdminApplication {
    @JvmStatic
    fun main(args: Array<String>) {
        SpringApplication.run(AdminApplication::class.java, *args)
    }
}
