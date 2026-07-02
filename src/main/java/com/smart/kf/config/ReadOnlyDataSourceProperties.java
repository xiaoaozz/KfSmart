package com.smart.kf.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 工作流 SQL 节点「只读 DataSource」配置（深度防御）。
 *
 * <p>对应 {@code workflow.sql-node.read-only.*}：
 * <ul>
 *   <li>配置 {@code url} 时，{@code SqlNodeExecutor} 在构造期自建独立连接池，
 *       推荐使用只读权限的 DB 账号，作为 SELECT 白名单 + {@code setReadOnly} 之外的深度防御；</li>
 *   <li>未配置 {@code url} 时回退主业务 {@code DataSource}，行为与改造前一致。</li>
 * </ul>
 *
 * <p>注意：该配置不注册独立的 {@code DataSource} Bean，避免触发 Spring Boot
 * {@code DataSourceAutoConfiguration} 的 {@code @ConditionalOnMissingBean} 抑制，
 * 从而不影响主库自动装配与 JPA。
 */
@Component
@ConfigurationProperties(prefix = "workflow.sql-node.read-only")
@Data
public class ReadOnlyDataSourceProperties {

    /** 只读 DataSource 的 JDBC URL；留空则回退主 DataSource。 */
    private String url;

    private String username;

    private String password;

    /** JDBC 驱动类名，默认 MySQL 8 驱动。 */
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
}