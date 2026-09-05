package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.QueryConfig;
import com.dataciders.qlive.model.QueryDocument;
import com.dataciders.qlive.model.bootstrap.ClientCsrfToken;
import com.dataciders.qlive.model.bootstrap.Injection;
import com.dataciders.qlive.model.bootstrap.QLiveBoostrap;
import com.dataciders.qlive.model.bootstrap.QLiveConfig;
import com.dataciders.qlive.runtime.scalar.FilterDSL;
import com.google.errorprone.annotations.ForOverride;
import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.util.IntrospectionUtil;
import de.quinscape.domainql.util.JSONHolder;
import de.quinscape.spring.jsview.util.JSONUtil;
import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.svenson.util.JSONBeanUtil;
import org.svenson.util.JSONPathUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DefaultBootstrapService
    implements BootstrapService
{
    private final static Logger log = LoggerFactory.getLogger(DefaultBootstrapService.class);

    private final ServletContext servletContext;

    private final DomainQL domainQL;

    private final JSONHolder qlConfigJSON;

    private final JSONPathUtil pathUtil = new JSONPathUtil(JSONUtil.OBJECT_SUPPORT);


    public DefaultBootstrapService(
        ServletContext servletContext, @Lazy DomainQL domainQL
    )
    {
        this.servletContext = servletContext;
        this.domainQL = domainQL;

        final Map<String, Object> raw = IntrospectionUtil.introspect(domainQL.getGraphQLSchema());

        final Map<String, Object> schema = (Map<String, Object>) pathUtil.getPropertyPath(raw, "data.__schema");
        final Map<String, Object> cleaned = new HashMap<>(schema);

        cleaned.put("types", schema.get("types"));

        if (log.isDebugEnabled())
        {
            log.debug("Raw schema is {}",  JSONUtil.formatJSON(JSONUtil.DEFAULT_GENERATOR.forValue(cleaned)));
        }

        QLiveConfig qlConfig = new QLiveConfig();
        qlConfig.setContextPath(servletContext.getContextPath());
        qlConfig.setMeta(domainQL.getMetaData());
        qlConfig.setSchema(cleaned);

        this.qlConfigJSON = new JSONHolder(qlConfig);

        log.info("QLiveConfig size: {}", this.qlConfigJSON.toJSON().length());
    }


    @Override
    public QLiveBoostrap provideConfig(CsrfToken csrfToken, String path)
    {
        final QLiveBoostrap qLiveBoostrap = new QLiveBoostrap();
        final Map<String, Injection> data = provideInjectionData(path);

        qLiveBoostrap.setConfig(qlConfigJSON);
        qLiveBoostrap.setData(data);
        qLiveBoostrap.setCsrfToken(new ClientCsrfToken(csrfToken));

        return qLiveBoostrap;
    }

    /// Provides just the injection data subset for dynamic path updates
    @Override
    public Map<String, Injection> provideInjectionData(String path)
    {
        final HashMap<String, Injection> data = new HashMap<>();

        // TODO: implement injection
        final HashMap<String, Object> r = new HashMap<>();
        try
        {
            final Class<?> fooClass = Class.forName(
                "com.dataciders.qlivetest.domain.tables.pojos.Foo");
            final QueryDocument<?> doc = new QueryDocument<>(fooClass);

            final QueryConfig config = new QueryConfig();
            config.setPageSize(1);
            doc.setConfig(config);
            final ArrayList rows = new ArrayList<>();
            try
            {
                final Object foo = fooClass.getConstructor().newInstance();

                int rnd = (int) Math.round(Math.random() * 100);

                pathUtil.setPropertyPath(foo, "name", "Foo #" + rnd);
                pathUtil.setPropertyPath(foo, "num", rnd);
                pathUtil.setPropertyPath(foo, "description", "Desc for Foo #" + rnd);

                rows.add(foo);
            }
            catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e)
            {
                throw new RuntimeException(e);
            }

            doc.setRows(rows);
            doc.setRowCount(1);
            r.put("xxx", doc);
        }
        catch (ClassNotFoundException e)
        {
            throw new RuntimeException(e);
        }
        data.put("Q_Foo", new Injection(r, "Int"));


        return data;
    }
}
