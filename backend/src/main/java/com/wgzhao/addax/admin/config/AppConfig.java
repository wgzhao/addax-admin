package com.wgzhao.addax.admin.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig
{
    @Bean
    public RestTemplate restTemplate()
    {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // sensible defaults: 2s connect, 5s read
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(5000);
        return new RestTemplate(requestFactory);
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer customizer()
    {
        return builder -> {
//            builder.propertyNamingStrategy(PropertyNamingStrategies.LOWER_CASE);
            builder.propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        };
    }
}
