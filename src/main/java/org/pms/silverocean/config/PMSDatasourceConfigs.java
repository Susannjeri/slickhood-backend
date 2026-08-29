package org.pms.silverocean.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableJpaAuditing
@EnableTransactionManagement
@Primary
@EnableJpaRepositories(basePackages = "org.pms.silverocean.database.pms",
        entityManagerFactoryRef = "pmsDBEntityManagerFactory",
        transactionManagerRef= "pmsDBTransactionManager")
public class PMSDatasourceConfigs {
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties pmsDBDataSourceProperties() {
        return new DataSourceProperties();
    }


    @Bean @Primary
    public DataSource pmsDBDataSource() {
       return pmsDBDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(CustomHikariDatasource.class)
                .build();

    }

    @Bean @Primary
    public LocalContainerEntityManagerFactoryBean pmsDBEntityManagerFactory(
            EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(pmsDBDataSource())
                .packages("org.pms.silverocean.database.pms.entities")
                .build();
    }

    @Bean @Primary
    public PlatformTransactionManager pmsDBTransactionManager(
            final @Qualifier("pmsDBEntityManagerFactory") LocalContainerEntityManagerFactoryBean mysqlEntityManagerFactory) {
        assert mysqlEntityManagerFactory.getObject() != null;
        return new JpaTransactionManager(mysqlEntityManagerFactory.getObject());
    }
}
