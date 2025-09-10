package com.wgzhao.addax.admin.config;

import com.wgzhao.addax.admin.AdminApplication;
import com.wgzhao.addax.admin.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ConfigInitializer implements ApplicationRunner
{
    @Autowired
    private SystemConfigService systemConfigService;

    @Override
    public void run(ApplicationArguments args)
            throws Exception
    {
        systemConfigService.loadConfig();
        System.out.println("系统配置已加载完成");
    }
}
