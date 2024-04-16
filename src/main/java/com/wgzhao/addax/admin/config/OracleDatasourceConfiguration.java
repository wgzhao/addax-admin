package com.wgzhao.addax.admin.config;

import com.wgzhao.addax.admin.repository.BaseRepositoryImpl;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {"com.wgzhao.addax.admin.repository.oracle"},
        entityManagerFactoryRef = "oracleEntityManagerFactory",
        transactionManagerRef = "oracleTransactionManager",
        repositoryBaseClass = BaseRepositoryImpl.class
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
        Map<String, String> props = new HashMap<>();
        props.put("hibernate.dialect", "org.hibernate.dialect.OracleDialect");
        return builder
                .dataSource(oracleDatasource)
                .properties(props)
                .packages("com.wgzhao.addax.admin.model.oracle")
                .build();
    }



    @Primary
    @Bean(name = "oracleTransactionManager")
    public PlatformTransactionManager transactionManager(
            @Qualifier("oracleEntityManagerFactory") LocalContainerEntityManagerFactoryBean oracleEntityManagerFactory) {
        return new JpaTransactionManager(Objects.requireNonNull(oracleEntityManagerFactory.getObject()));
    }

}
