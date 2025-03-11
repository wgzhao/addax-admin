package com.wgzhao.addax.admin.config;

import com.wgzhao.addax.admin.repository.BaseRepositoryImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

import java.util.Objects;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {"com.wgzhao.addax.admin.repository.hive"},
        entityManagerFactoryRef = "hiveEntityManagerFactory",
        transactionManagerRef = "hiveTransactionManager",
        repositoryBaseClass = BaseRepositoryImpl.class
)
public class HiveDatasourceConfiguration
{

    @Bean(name = "hiveProperties")
    @ConfigurationProperties("spring.datasource.hive")
    public DataSourceProperties hiveDatasourceProperties()
    {
        return new DataSourceProperties();
    }

    @Bean(name = "hiveDatasource")
    public DataSource datasource(@Qualifier("hiveProperties") DataSourceProperties hiveProperties)
    {
        return hiveProperties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "hiveEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("hiveDatasource") DataSource hiveDatasource,
            EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(hiveDatasource)
                .packages("com.wgzhao.addax.admin.model.hive")
                .persistenceUnit("hive")
                .build();
    }

    @Bean(name = "hiveTransactionManager")
    @ConfigurationProperties("spring.jpa")
    public PlatformTransactionManager transactionManager(
            @Qualifier("hiveEntityManagerFactory") LocalContainerEntityManagerFactoryBean hiveEntityManagerFactory) {
        return new JpaTransactionManager(Objects.requireNonNull(hiveEntityManagerFactory.getObject()));
    }
}
