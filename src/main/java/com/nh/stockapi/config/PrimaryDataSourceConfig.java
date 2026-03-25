package com.nh.stockapi.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * 로컬/운영 PostgreSQL — JPA 기본(Primary) DataSource
 *
 * SupabaseDataSourceConfig 가 DataSource 빈을 등록하면
 * Spring Boot DataSourceAutoConfiguration 이 @ConditionalOnMissingBean 조건으로
 * 자동 구성을 건너뛰는 문제를 막기 위해 명시적으로 @Primary 빈을 선언한다.
 *
 * HikariCP 는 'url' 프로퍼티 대신 'jdbcUrl' 을 요구하므로
 * @ConfigurationProperties 바인딩 대신 @Value 로 직접 주입한다.
 */
@Configuration
@Profile("!test")
public class PrimaryDataSourceConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minIdle;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setPoolName("primary-pool");
        return new HikariDataSource(config);
    }
}
