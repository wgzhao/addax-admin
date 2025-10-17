package com.wgzhao.addax.admin.config;

import com.wgzhao.addax.admin.service.SystemConfigService;
import lombok.AllArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ConfigInitializer implements ApplicationRunner
{
    private final SystemConfigService systemConfigService;

    @Override
    public void run(ApplicationArguments args)
    {
        systemConfigService.loadConfig();
        System.out.println("系统配置已加载完成");
    }
}
