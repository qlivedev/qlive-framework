package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.QueryConfig;
import com.dataciders.qlive.model.QueryDocument;
import com.dataciders.qlive.model.bootstrap.ClientCsrfToken;
import com.dataciders.qlive.model.bootstrap.Injection;
import com.dataciders.qlive.model.bootstrap.QLiveBoostrap;
import com.dataciders.qlive.model.bootstrap.QLiveConfig;
import com.dataciders.qlive.model.ts.ModuleFunctionReferences;
import com.dataciders.qlive.model.ts.TrackUsageData;
import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.fetcher.FetcherContext;
import de.quinscape.domainql.jooq.GeneratedDomainObject;
import de.quinscape.domainql.meta.DomainQLMeta;
import de.quinscape.domainql.util.IntrospectionUtil;
import de.quinscape.domainql.util.JSONHolder;
import de.quinscape.spring.jsview.util.JSONUtil;
import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.web.csrf.CsrfToken;
import org.svenson.JSON;
import org.svenson.util.JSONPathUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DefaultBootstrapService
    implements BootstrapService
{
    private final static Logger log = LoggerFactory.getLogger(DefaultBootstrapService.class);

    /// Symbolic call name the frontend's track-usage config registers noSchema() under. Has to agree with the
    /// key in the application's `trackedFunctions`, which is conventionally the function's own name.
    private final static String NO_SCHEMA_CALL = "noSchema";

    private final ServletContext servletContext;

    private final DomainQL domainQL;

    private final JSONHolder qlConfigJSON;

    private final JSONHolder reducedConfigJSON;

    private final StaticAnalysisProvider staticAnalysisProvider;

    private final JSONPathUtil pathUtil = new JSONPathUtil(JSONUtil.OBJECT_SUPPORT);

    public DefaultBootstrapService(
        ServletContext servletContext, @Lazy DomainQL domainQL, StaticAnalysisProvider staticAnalysisProvider
    )
    {
        this.servletContext = servletContext;
        this.domainQL = domainQL;
        this.staticAnalysisProvider = staticAnalysisProvider;

        // the whole model just exists to be sent to the client. We only need it in JSON string form,
        // over and over for every full page load forever. The JSONHolder allows us to only generate it once and then
        // embed it as JSON subgraph when the rest of the JSON is generated.
        //
        // Both variants are built here rather than on first use: the reduced one costs nothing to produce, and
        // building both up front keeps this constructor the only place that touches the schema.
        qlConfigJSON = new JSONHolder(
            createSystemConfig(servletContext, domainQL, false)
        );
        reducedConfigJSON = new JSONHolder(
            createSystemConfig(servletContext, domainQL, true)
        );

        log.info(
            "Cached QLiveConfig JSON size: {} (reduced: {})",
            this.qlConfigJSON.toJSON().length(),
            this.reducedConfigJSON.toJSON().length()
        );
        if (log.isDebugEnabled())
        {
            log.debug("QLiveConfig JSON: {}", this.qlConfigJSON.toJSON());
        }
    }

    /// Creates a {@link QLiveConfig} bean hierarchy.
    ///
    /// @param reduced  if true, produce the reduced variant: the same config with an empty domain in place of
    ///                 the introspected schema and the DomainQL meta data. See {@link #emptyMeta()} for what
    ///                 "empty" has to mean here.
    private QLiveConfig createSystemConfig(ServletContext servletContext, DomainQL domainQL, boolean reduced)
    {
        QLiveConfig qlConfig = new QLiveConfig();
        qlConfig.setContextPath(servletContext.getContextPath());

        if (reduced)
        {
            qlConfig.setSchema(Map.of("types", List.of()));
            qlConfig.setMeta(emptyMeta());

            return qlConfig;
        }

        final Map<String, Object> raw = IntrospectionUtil.introspect(domainQL.getGraphQLSchema());

        final Map<String, Object> schema = (Map<String, Object>) pathUtil.getPropertyPath(raw, "data.__schema");
        final Map<String, Object> cleaned = new HashMap<>(schema);

        cleaned.put("types", schema.get("types"));

        if (log.isDebugEnabled())
        {
            log.debug("Raw schema is {}",  JSONUtil.formatJSON(JSONUtil.DEFAULT_GENERATOR.forValue(cleaned)));
        }

        qlConfig.setMeta(domainQL.getMetaData());
        qlConfig.setSchema(cleaned);

        return qlConfig;
    }


    /// DomainQL meta data with all three addenda present and empty.
    ///
    /// Empty, not absent: the client dereferences `meta.types`, `meta.genericTypes` and `meta.relations`
    /// unconditionally while it initializes the derived config, so leaving any of them off the wire turns a
    /// reduced page into a startup crash rather than a smaller payload.
    private static DomainQLMeta emptyMeta()
    {
        final DomainQLMeta meta = new DomainQLMeta(Map.of());
        meta.addAddendum(DomainQLMeta.RELATIONS, List.of());
        meta.addAddendum(DomainQLMeta.GENERIC_TYPES, List.of());

        return meta;
    }


    @Override
    public QLiveBoostrap provideConfig(CsrfToken csrfToken, String path)
    {
        final TrackUsageData staticAnalysis = staticAnalysisProvider.getTrackUsageData();
        if (staticAnalysis == null)
        {
            // Not an error and not a reason to guess: in dev the Vite dev server has not pushed its first
            // snapshot yet, so what this path needs is simply not knowable at this moment. Answering null
            // makes the caller report "not ready", which the frontend's bootstrap fetch retries until it is.
            // Serving a config assembled without the analysis would instead hand out, and cache in a rendered
            // page, an answer we would have contradicted a second later.
            log.debug("No static analysis data (yet) -- reporting not ready for path {}", path);
            return null;
        }

        final QLiveBoostrap qLiveBoostrap = new QLiveBoostrap();
        final Map<String, Injection> data = provideInjectionData(path);

        qLiveBoostrap.setConfig(needsSchema(staticAnalysis, path) ? qlConfigJSON : reducedConfigJSON);
        qLiveBoostrap.setData(data);
        qLiveBoostrap.setCsrfToken(new ClientCsrfToken(csrfToken));

        return qLiveBoostrap;
    }


    /// Whether the module serving the given path needs the domain schema, i.e. whether it does *not* declare
    /// noSchema().
    ///
    /// The declaration is a call in the module's own source, which only the frontend build's static analysis
    /// can see -- so this reads the same data the injections are resolved from, rather than letting the caller
    /// pass a flag it would have to know from somewhere else.
    ///
    /// Answers `true` for a path with no entry of its own. A page that gets the schema it did not need is
    /// slower than necessary; a page that does not get the schema it needed is broken, so the case this
    /// cannot resolve -- see {@link #moduleForPath(String)} -- has to fall on this side.
    private boolean needsSchema(TrackUsageData staticAnalysis, String path)
    {
        final ModuleFunctionReferences refs = staticAnalysis.getModuleFunctionReferences(moduleForPath(path));

        return refs == null || refs.getCalls(NO_SCHEMA_CALL).isEmpty();
    }


    /// Maps a path to the track-usage module name of whatever renders it.
    ///
    /// Module names are relative to the frontend's track-usage `sourceRoot` and carry a leading "./", so the
    /// entry module of `/login` is "./login". That direct correspondence is all that is resolved here, which
    /// covers an application's own entry points -- the ones that can declare noSchema() in the first place,
    /// because they are the ones that call startup().
    ///
    /// Views below the Vite base do not resolve through this yet: their route is lower-cased and their module
    /// lives under the view glob's directory, so "/app/home" reaches "./app/Home" only through the same
    /// route table the client builds in views.ts. Nothing needs that mapping until injections are resolved
    /// per view, and getting it wrong here would be invisible -- an unresolved path is a path that gets the
    /// full config, which is what a view wants anyway.
    private String moduleForPath(String path)
    {
        if (path == null || path.isEmpty())
        {
            return "";
        }

        // The paths that reach here are request URIs, so they carry the context path an application happens
        // to be deployed under. Module names never do -- they are relative to the frontend's source root, and
        // the frontend is the same build wherever it ends up mounted.
        final String contextPath = servletContext.getContextPath();
        String normalized = !contextPath.isEmpty() && path.startsWith(contextPath)
            ? path.substring(contextPath.length())
            : path;

        if (normalized.endsWith("/"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized.startsWith("/") ? "." + normalized : "./" + normalized;
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
                final GeneratedDomainObject foo = (GeneratedDomainObject) fooClass.getConstructor().newInstance();

                int rnd = (int) Math.round(Math.random() * 100);

                foo.setProperty("id", UUID.randomUUID().toString());
                foo.setProperty("name", "Foo #" + rnd);
                foo.setProperty("num", rnd);
                foo.setProperty("description", "Desc for Foo #" + rnd);
                foo.setProperty("ownerId", "d7df0f2c-9aa8-4845-b2bf-1d02abd3666e");
                final FetcherContext fetcherContext = new FetcherContext();
                fetcherContext.setProperty("id", "d7df0f2c-9aa8-4845-b2bf-1d02abd3666e");
                fetcherContext.setProperty("login", "admin");
                foo.provideFetcherContext(fetcherContext);

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
