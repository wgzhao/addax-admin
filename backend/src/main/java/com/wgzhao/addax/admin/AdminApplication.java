package com.wgzhao.addax.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AdminApplication
{
    public static void main(String[] args)
    {
        // 柜台交易日历的服务器只下发叶子证书、不带中间 CA，JVM 默认不会按 AIA 扩展去补（curl 会），
        // 握手就会失败在 PKIX path building。这个开关让 JDK 自动补齐中间证书，不放松任何校验；
        // 必须尽早设置：证书链构建相关的静态状态一旦初始化就不会再读它。
        System.setProperty("com.sun.security.enableAIAcaIssuers", "true");
        SpringApplication.run(AdminApplication.class, args);
    }
}
