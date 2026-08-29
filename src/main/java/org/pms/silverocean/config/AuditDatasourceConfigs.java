package org.pms.silverocean.config;

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

@Configuration @EnableTransactionManagement
@EnableJpaRepositories(basePackages = "org.pms.silverocean.database.audit",
        entityManagerFactoryRef = "auditDBEntityManagerFactory",
        transactionManagerRef= "auditDBTransactionManager")
public class AuditDatasourceConfigs {
    @Bean
    @ConfigurationProperties("audit.datasource.mysql")
    public DataSourceProperties auditDBDataSourceProperties() {
        return new DataSourceProperties();
    }


    @Bean
    public DataSource auditDBDataSource() {
       return auditDBDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(CustomHikariDatasource.class)
                .build();

    }

    @Bean
    public LocalContainerEntityManagerFactoryBean auditDBEntityManagerFactory(EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(auditDBDataSource())
                .packages("org.pms.silverocean.database.audit.entities")
                .build();
    }

    @Bean
    public PlatformTransactionManager auditDBTransactionManager(
            final @Qualifier("auditDBEntityManagerFactory") LocalContainerEntityManagerFactoryBean mysqlEntityManagerFactory) {
        assert mysqlEntityManagerFactory.getObject() != null;
        return new JpaTransactionManager(mysqlEntityManagerFactory.getObject());
    }
}
