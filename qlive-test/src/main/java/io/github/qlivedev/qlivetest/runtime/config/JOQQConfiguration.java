package io.github.qlivedev.qlivetest.runtime.config;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;

@Configuration
public class JOQQConfiguration
{
    private final static Logger log = LoggerFactory.getLogger(JOQQConfiguration.class);

    @Bean
    public DSLContext dslContext(DataSourceConnectionProvider connectionProvider)
    {
        DefaultDSLContext defaultDSLContext = new DefaultDSLContext(
            new DefaultConfiguration()
                .derive(connectionProvider)
                .derive(SQLDialect.POSTGRES)
        );

        log.info("Created DSLContext");
        log.debug("DSLContext: {}", defaultDSLContext);

        return defaultDSLContext;
    }

    /**
     * <p>
     *     Hands JOOQ the connection Spring's transaction management is holding, rather than a fresh one out
     *     of the pool. Without the proxy every statement runs in a connection and a transaction of its own,
     *     and a {@code @Transactional} boundary around them -- the framework's merge is one -- would neither
     *     commit them together nor be able to roll them back.
     * </p>
     */
    @Bean
    public DataSourceConnectionProvider connectionProvider(
        DataSource dataSource
    )
    {
        return new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(dataSource));
    }

}
