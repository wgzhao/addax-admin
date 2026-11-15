package com.wgzhao.addax.admin.config;

import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the entity manager factory depend on {@link SchemaInitializer}.
 *
 * <p>On an empty database the initializer creates the tables, but Hibernate-backed beans seed dict
 * rows and bootstrap cached configuration from their own {@code @PostConstruct}. Bean ordering alone
 * does not guarantee the initializer runs first (JPA only depends on the DataSource), and the first
 * Docker start without a pre-created schema logged "relation sys_item does not exist" and silently
 * skipped the job templates. This is the same hook Spring Boot uses for Flyway and Liquibase.</p>
 */
@Configuration(proxyBeanMethods = false)
public class SchemaInitializerJpaDependencyConfiguration extends EntityManagerFactoryDependsOnPostProcessor
{
    public SchemaInitializerJpaDependencyConfiguration()
    {
        super(SchemaInitializer.class);
    }
}
