package com.kanban.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * TiDB Serverless rejects plaintext MySQL connections. Connector/J also treats
 * {@code useSSL=true} as deprecated; without {@code sslMode} the handshake can
 * still go out as insecure. Rewrite the JDBC URL before Hikari opens the pool.
 */
@Configuration
@Profile("prod")
public class MysqlTlsUrlEnforcer {

    private static final String TLS_PARAMS = "sslMode=VERIFY_IDENTITY&enabledTLSProtocols=TLSv1.2,TLSv1.3";

    @Bean
    public static BeanPostProcessor mysqlTlsUrlBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof HikariDataSource hikari) {
                    String url = hikari.getJdbcUrl();
                    if (url != null && url.startsWith("jdbc:mysql:")) {
                        String rewritten = ensureTls(ensureAppDatabase(url));
                        hikari.setJdbcUrl(rewritten);
                        applyCatalog(hikari, rewritten);
                    }
                    rejectBareTidbUsername(hikari);
                }
                return bean;
            }
        };
    }

    static String ensureTls(String url) {
        String cleaned = url
                .replace("useSSL=false", "useSSL=true")
                .replace("requireSSL=false", "requireSSL=true")
                .replace("sslMode=DISABLED", "sslMode=VERIFY_IDENTITY")
                .replace("sslMode=PREFERRED", "sslMode=VERIFY_IDENTITY");
        if (!cleaned.toLowerCase().contains("sslmode=")) {
            cleaned += (cleaned.contains("?") ? "&" : "?") + TLS_PARAMS;
        }
        if (!cleaned.contains("enabledTLSProtocols=")) {
            cleaned += (cleaned.contains("?") ? "&" : "?") + "enabledTLSProtocols=TLSv1.2,TLSv1.3";
        }
        return cleaned;
    }

    static String ensureAppDatabase(String url) {
        int pathStart = url.indexOf("://");
        if (pathStart < 0) {
            return url;
        }
        int dbStart = url.indexOf('/', pathStart + 3);
        if (dbStart < 0) {
            return url;
        }
        int queryStart = url.indexOf('?', dbStart);
        String db = queryStart < 0 ? url.substring(dbStart + 1) : url.substring(dbStart + 1, queryStart);
        String lower = db.toLowerCase();
        if (db.isBlank() || "sys".equals(lower) || "mysql".equals(lower)
                || "information_schema".equals(lower) || "performance_schema".equals(lower)) {
            String rest = queryStart < 0 ? "" : url.substring(queryStart);
            url = url.substring(0, dbStart + 1) + "kanbandb" + rest;
        }
        if (!url.contains("createDatabaseIfNotExist=")) {
            url += (url.contains("?") ? "&" : "?") + "createDatabaseIfNotExist=true";
        }
        return url;
    }

    static void applyCatalog(HikariDataSource hikari, String url) {
        int pathStart = url.indexOf("://");
        int dbStart = url.indexOf('/', pathStart + 3);
        int queryStart = url.indexOf('?', dbStart);
        String db = queryStart < 0 ? url.substring(dbStart + 1) : url.substring(dbStart + 1, queryStart);
        if (!db.isBlank()) {
            hikari.setCatalog(db);
            hikari.setConnectionInitSql("USE `" + db.replace("`", "") + "`");
        }
    }

    static void rejectBareTidbUsername(HikariDataSource hikari) {
        String url = hikari.getJdbcUrl();
        if (url == null || !url.contains("tidbcloud.com")) {
            return;
        }
        String user = hikari.getUsername();
        if (user == null || !user.contains(".")) {
            throw new IllegalStateException(
                    "TiDB username must include the cluster prefix, e.g. 3pTAoNNegb47Uc8.root. "
                            + "Copy the User field from TiDB → Connect. Current DB_USERNAME='"
                            + user + "'.");
        }
    }
}
