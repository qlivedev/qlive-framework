package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.bootstrap.Injection;
import com.dataciders.qlive.model.bootstrap.QLiveBoostrap;
import com.dataciders.qlive.model.bootstrap.QLiveConfig;
import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.util.IntrospectionUtil;
import de.quinscape.domainql.util.JSONHolder;
import de.quinscape.spring.jsview.util.JSONUtil;
import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.svenson.util.JSONPathUtil;

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
    public QLiveBoostrap provideConfig(String path)
    {
        final QLiveBoostrap qLiveBoostrap = new QLiveBoostrap();
        final Map<String, Injection> data = provideInjectionData(path);

        qLiveBoostrap.setConfig(qlConfigJSON);
        qLiveBoostrap.setData(data);

        return qLiveBoostrap;
    }

    /// Provides just the injection data subset for dynamic path updates
    @Override
    public Map<String, Injection> provideInjectionData(String path)
    {
        final HashMap<String, Injection> data = new HashMap<>();

        // TODO: implement injection
        final HashMap<String, Object> r = new HashMap<>();
        r.put("xxx", 1234);
        data.put("Q_Foo", new Injection(r, "Int"));


        return data;
    }
}
