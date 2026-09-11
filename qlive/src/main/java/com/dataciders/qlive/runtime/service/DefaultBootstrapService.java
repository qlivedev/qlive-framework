package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.bootstrap.ClientCsrfToken;
import com.dataciders.qlive.model.bootstrap.Injection;
import com.dataciders.qlive.model.bootstrap.QLiveBoostrap;
import com.dataciders.qlive.runtime.auth.AppAuthentication;
import com.dataciders.qlive.model.bootstrap.QLiveConfig;
import com.dataciders.qlive.model.ts.ModuleFunctionReferences;
import com.dataciders.qlive.model.ts.TrackUsageData;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.QLivePaths;
import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.meta.DomainQLMeta;
import de.quinscape.domainql.util.IntrospectionUtil;
import de.quinscape.domainql.util.JSONHolder;
import de.quinscape.spring.jsview.util.JSONUtil;
import graphql.GraphQL;
import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.web.csrf.CsrfToken;
import org.svenson.util.JSONPathUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private final InjectionService injectionService;

    private final JSONPathUtil pathUtil = new JSONPathUtil(JSONUtil.OBJECT_SUPPORT);

    public DefaultBootstrapService(
        ServletContext servletContext,
        @Lazy DomainQL domainQL,
        @Lazy GraphQL graphQL,
        StaticAnalysisProvider staticAnalysisProvider
    )
    {
        this(
            servletContext,
            domainQL,
            graphQL,
            staticAnalysisProvider,
            List.of(new QueryConfigArgumentProcessor(domainQL))
        );
    }


    /// @param argumentProcessors  what turns the static parameters of an application's `useInjection()` calls
    ///                            into GraphQL variables, see {@link InjectionArgumentProcessor}
    public DefaultBootstrapService(
        ServletContext servletContext,
        @Lazy DomainQL domainQL,
        @Lazy GraphQL graphQL,
        StaticAnalysisProvider staticAnalysisProvider,
        List<InjectionArgumentProcessor> argumentProcessors
    )
    {
        this.servletContext = servletContext;
        this.domainQL = domainQL;
        this.staticAnalysisProvider = staticAnalysisProvider;
        this.injectionService = new InjectionService(graphQL, domainQL, argumentProcessors);

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

        // Resolved once and handed to both: which module serves the path is what decides the size of the
        // config as well as which queries run for it.
        final String module = moduleForPath(staticAnalysis, servletContext.getContextPath(), path);

        final QLiveBoostrap qLiveBoostrap = new QLiveBoostrap();

        qLiveBoostrap.setConfig(needsSchema(staticAnalysis, module) ? qlConfigJSON : reducedConfigJSON);
        qLiveBoostrap.setData(injectionService.provideInjections(staticAnalysis, module));
        qLiveBoostrap.setCsrfToken(new ClientCsrfToken(csrfToken));
        qLiveBoostrap.setAuthentication(AppAuthentication.current());

        return qLiveBoostrap;
    }


    /// Provides just the injection data subset for dynamic path updates
    @Override
    public Map<String, Injection> provideInjectionData(String path)
    {
        final TrackUsageData staticAnalysis = staticAnalysisProvider.getTrackUsageData();
        if (staticAnalysis == null)
        {
            log.debug("No static analysis data (yet) -- reporting not ready for path {}", path);
            return null;
        }

        return injectionService.provideInjections(
            staticAnalysis,
            moduleForPath(
                staticAnalysis,
                servletContext.getContextPath(),
                path
            )
        );
    }


    /// Whether the module serving the given path needs the domain schema, i.e. whether it does *not* declare
    /// noSchema().
    ///
    /// The declaration is a call in the module's own source, which only the frontend build's static analysis
    /// can see -- so this reads the same data the injections are resolved from, rather than letting the caller
    /// pass a flag it would have to know from somewhere else.
    ///
    /// Answers `true` for a path that resolves to no module. A page that gets the schema it did not need is
    /// slower than necessary; a page that does not get the schema it needed is broken, so the case this
    /// cannot resolve -- see {@link #moduleForPath(TrackUsageData, String, String)} -- has to fall on this
    /// side.
    private static boolean needsSchema(TrackUsageData staticAnalysis, String module)
    {
        if (module == null)
        {
            return true;
        }

        final ModuleFunctionReferences refs = staticAnalysis.getModuleFunctionReferences(module);

        return refs == null || refs.getCalls(NO_SCHEMA_CALL).isEmpty();
    }


    /// Maps a path to the track-usage module name of whatever renders it.
    ///
    /// Module names are relative to the frontend's track-usage `sourceRoot` and carry a leading "./". Two
    /// kinds of path resolve through this, because an application serves two kinds of page:
    ///
    ///  * an entry point of its own, whose route is its module name: `/login` is served by "./login". These
    ///    are the modules that call startup() and can therefore declare noSchema().
    ///  * a view below the Vite base, which the frontend loads through the route table it builds in views.ts.
    ///    That table is built from the view glob, which only the frontend has, so the same correspondence is
    ///    reconstructed here from the analysis -- see {@link #viewModule(TrackUsageData, String)}.
    ///
    /// @return the module name, or `null` where the path belongs to no module of the application
    static String moduleForPath(TrackUsageData staticAnalysis, String contextPath, String path)
    {
        final String normalized = normalizePath(contextPath, path);

        // Below the Vite base everything is a view, whatever the analysis happens to hold: those routes are
        // served by the index page, and the frontend resolves them through its route table alone.
        if (normalized.startsWith(QLivePaths.APP_BASE))
        {
            final String route = normalized.substring(QLivePaths.APP_BASE.length())
                .toLowerCase(Locale.ROOT);

            // "/app/" itself addresses no view -- the frontend renders its own landing page there.
            return route.isEmpty() ? null : viewModule(staticAnalysis, route);
        }

        // Outside it, an entry point is named by its route directly, so this is a plain lookup.
        final String entryPoint = "." + normalized;

        return staticAnalysis.getModuleFunctionReferences(entryPoint) != null ? entryPoint : null;
    }


    /// Resolves a route below the Vite base to the module the frontend would load for it.
    ///
    /// views.ts derives a view's route by dropping the directory of the view glob from its module path and
    /// lower-casing what is left, so "./app/sub/View" is reached at "/app/sub/view". This is that in
    /// reverse, with {@link QLivePaths#VIEW_ROOT} standing in for the glob's directory.
    ///
    /// @return the module name, or `null` where no module matches
    private static String viewModule(TrackUsageData staticAnalysis, String route)
    {
        final String wanted = QLivePaths.VIEW_ROOT + route;

        String found = null;
        for (String module : staticAnalysis.getModuleFunctionReferences().keySet())
        {
            if (!module.toLowerCase(Locale.ROOT).equals(wanted))
            {
                continue;
            }

            if (found != null)
            {
                // Two modules the frontend's own route table could not hold at the same time either: it
                // refuses view names that differ only in case. Reported rather than picked from, because
                // picking wrong means a page that renders with another view's data.
                throw new QLiveException(
                    "Route '" + route + "' matches both module '" + found + "' and module '" + module +
                        "'. View names have to differ by more than their case."
                );
            }
            found = module;
        }

        return found;
    }


    /// The request URI reduced to what module names are expressed in: no context path, no trailing slash.
    ///
    /// The paths that reach here are request URIs, so they carry the context path an application happens to
    /// be deployed under. Module names never do -- they are relative to the frontend's source root, and the
    /// frontend is the same build wherever it ends up mounted.
    private static String normalizePath(String contextPath, String path)
    {
        if (path == null || path.isEmpty())
        {
            return "/";
        }

        String normalized = contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)
            ? path.substring(contextPath.length())
            : path;

        if (normalized.length() > 1 && normalized.endsWith("/"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}
