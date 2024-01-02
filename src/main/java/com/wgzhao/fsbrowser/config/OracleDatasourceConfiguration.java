package com.wgzhao.fsbrowser.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        basePackages = {"com.wgzhao.fsbrowser.repository.oracle"},
        entityManagerFactoryRef = "oracleEntityManagerFactory",
        transactionManagerRef = "oracleTransactionManager",
        repositoryBaseClass = com.wgzhao.fsbrowser.repository.BaseRepositoryImpl.class
)
public class OracleDatasourceConfiguration {

    @Primary
    @Bean(name = "oracleProperties")
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties oracleDatasourceProperties()
    {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "oracleDatasource")
    public DataSource datasource(@Qualifier("oracleProperties") DataSourceProperties oracleProperties)
    {
        return oracleProperties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean(name = "oracleEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("oracleDatasource") DataSource oracleDatasource,
            EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(oracleDatasource)
                .packages("com.wgzhao.fsbrowser.model.oracle")
                .build();
    }



    @Primary
    @Bean(name = "oracleTransactionManager")
    public PlatformTransactionManager transactionManager(
            @Qualifier("oracleEntityManagerFactory") LocalContainerEntityManagerFactoryBean oracleEntityManagerFactory) {
        return new JpaTransactionManager(Objects.requireNonNull(oracleEntityManagerFactory.getObject()));
    }

}
