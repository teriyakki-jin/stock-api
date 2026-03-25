package com.nh.stockapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
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
 * spring.datasource.* 및 spring.datasource.hikari.* 프로퍼티가 자동 바인딩된다.
 */
@Configuration
@Profile("!test")   // test 프로파일은 H2 자동 구성 사용
public class PrimaryDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }
}
