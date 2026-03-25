package com.nh.stockapi.infrastructure.supabase;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Supabase PostgreSQL 직접 연결 (PostgREST 우회)
 * service_role 수준 접근
 *
 * @Profile("!test") — 테스트 환경에서는 Supabase 연결 불필요
 * initializationFailTimeout = -1 — 서버 기동 시 Supabase 연결 실패해도 앱 뜸
 *   (실제 쿼리 시점에 재시도)
 */
@Configuration
@Profile("!test")
public class SupabaseDataSourceConfig {

    @Value("${supabase.db.url:jdbc:postgresql://db.tylxnldgwrqyvnyykflj.supabase.co:5432/postgres}")
    private String url;

    @Value("${supabase.db.username:postgres}")
    private String username;

    @Value("${supabase.db.password:}")
    private String password;

    @Bean(name = "supabaseDataSource")
    public DataSource supabaseDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        // 기동 시 Supabase 연결 실패를 무시 (-1 = 무한 대기하지 않고 나중에 재시도)
        config.setInitializationFailTimeout(-1);
        config.setPoolName("supabase-pool");
        return new HikariDataSource(config);
    }

    @Bean(name = "supabaseJdbc")
    public JdbcTemplate supabaseJdbcTemplate() {
        return new JdbcTemplate(supabaseDataSource());
    }
}
