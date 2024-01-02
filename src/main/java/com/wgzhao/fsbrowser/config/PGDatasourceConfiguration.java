package com.wgzhao.fsbrowser.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Primary;
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
        basePackages = {"com.wgzhao.fsbrowser.repository.pg"},
        entityManagerFactoryRef = "pgEntityManagerFactory",
        transactionManagerRef = "pgTransactionManager",
        repositoryBaseClass = com.wgzhao.fsbrowser.repository.BaseRepositoryImpl.class
)
public class PGDatasourceConfiguration {

    @Bean(name = "pgProperties")
    @ConfigurationProperties("spring.datasource.pg")
    public DataSourceProperties pgDatasourceProperties()
    {
        return new DataSourceProperties();
    }

    @Bean(name = "pgDatasource")
    public DataSource datasource(@Qualifier("pgProperties") DataSourceProperties pgProperties)
    {
        return pgProperties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "pgEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("pgDatasource") DataSource pgDatasource,
            EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(pgDatasource)
                .packages("com.wgzhao.fsbrowser.model.pg")
                .persistenceUnit("pg")
                .build();
    }

    @Bean(name = "pgTransactionManager")
    @ConfigurationProperties("spring.jpa")
    public PlatformTransactionManager transactionManager(
            @Qualifier("pgEntityManagerFactory") LocalContainerEntityManagerFactoryBean pgEntityManagerFactory) {
        return new JpaTransactionManager(Objects.requireNonNull(pgEntityManagerFactory.getObject()));
    }
}
