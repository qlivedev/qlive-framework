package io.github.qlivedev.qlive.runtime.service;

import io.github.qlivedev.qlive.model.bootstrap.ClientCsrfToken;
import io.github.qlivedev.qlive.model.bootstrap.Injection;
import io.github.qlivedev.qlive.model.bootstrap.QLiveBoostrap;
import io.github.qlivedev.qlive.runtime.auth.AppAuthentication;
import io.github.qlivedev.qlive.runtime.i18n.TranslationService;
import io.github.qlivedev.qlive.model.layout.Layouts;
import io.github.qlivedev.qlive.runtime.layout.LayoutCheck;
import io.github.qlivedev.qlive.runtime.layout.LayoutProvider;
import io.github.qlivedev.qlive.model.bootstrap.QLiveConfig;
import io.github.qlivedev.qlive.model.ts.ModuleFunctionReferences;
import io.github.qlivedev.qlive.model.ts.TrackUsageData;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.runtime.QLivePaths;
import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.meta.DomainMeta;
import io.github.qlivedev.qlive.graphql.util.IntrospectionUtil;
import io.github.qlivedev.qlive.graphql.util.JSONHolder;
import io.github.qlivedev.qlive.api.json.JSONUtil;
import graphql.GraphQL;
import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.csrf.CsrfToken;
import org.svenson.util.JSONPathUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultBootstrapService
    implements BootstrapService
{
    private final static Logger log = LoggerFactory.getLogger(DefaultBootstrapService.class);

    /// Symbolic call name the frontend's track-usage config registers noSchema() under. Has to agree with the
    /// key in the application's `trackedFunctions`, which is conventionally the function's own name.
    private final static String NO_SCHEMA_CALL = "noSchema";

    private final ServletContext servletContext;

    private final QLiveDomain domain;

    private final JSONHolder qlConfigJSON;

    private final JSONHolder reducedConfigJSON;

    private final StaticAnalysisProvider staticAnalysisProvider;

    private final InjectionService injectionService;

    private final TranslationService translationService;

    private final List<LayoutProvider> layoutProviders;

    private final LayoutCheck layoutCheck;

    /// The named layouts as the JSON every page with the schema gets, per locale, until flushCache().
    private final Map<Locale, JSONHolder> layoutJSON = new ConcurrentHashMap<>();

    private final boolean prod;

    private final JSONPathUtil pathUtil = new JSONPathUtil(JSONUtil.OBJECT_SUPPORT);

    public DefaultBootstrapService(
        ServletContext servletContext,
        QLiveDomain domain,
        GraphQL graphQL,
        StaticAnalysisProvider staticAnalysisProvider,
        TranslationService translationService,
        boolean prod
    )
    {
        this(
            servletContext,
            domain,
            graphQL,
            staticAnalysisProvider,
            List.of(new QueryConfigArgumentProcessor(domain)),
            Map.of(),
            translationService,
            List.of(),
            prod
        );
    }


    /// @param argumentProcessors  what turns the static parameters of an application's `useInjection()` calls
    ///                            into GraphQL variables, see {@link InjectionArgumentProcessor}
    /// @param catalogs            every row of every catalog type, by type name, see {@link Catalogs}. They go
    ///                            into the config of every page that gets the schema.
    /// @param translationService  what every page gets its translations from, in the locale it is served in
    /// @param layoutProviders     what every page that gets the schema gets its named layouts from, a later
    ///                            provider's layout replacing an earlier one's of the same name. Their layouts in
    ///                            every offered locale are read and checked against the domain here, see
    ///                            {@link LayoutCheck}, so that a wrong reference fails startup rather than a page.
    /// @param prod                true when running in production, where the schema the client gets carries no
    ///                            descriptions. They document the domain for the developer -- DomainTables shows
    ///                            them -- and are a quarter of the schema every page would ship otherwise.
    public DefaultBootstrapService(
        ServletContext servletContext,
        QLiveDomain domain,
        GraphQL graphQL,
        StaticAnalysisProvider staticAnalysisProvider,
        List<InjectionArgumentProcessor> argumentProcessors,
        Map<String, List<Map<String, Object>>> catalogs,
        TranslationService translationService,
        List<LayoutProvider> layoutProviders,
        boolean prod
    )
    {
        this.servletContext = servletContext;
        this.translationService = translationService;
        this.layoutProviders = List.copyOf(layoutProviders);
        this.layoutCheck = new LayoutCheck(domain);
        this.domain = domain;
        this.staticAnalysisProvider = staticAnalysisProvider;
        this.prod = prod;
        this.injectionService = new InjectionService(graphQL, domain, argumentProcessors);

        translationService.getLocales().forEach(this::layouts);

        // the whole model just exists to be sent to the client. We only need it in JSON string form,
        // over and over for every full page load forever. The JSONHolder allows us to only generate it once and then
        // embed it as JSON subgraph when the rest of the JSON is generated.
        //
        // Both variants are built here rather than on first use: the reduced one costs nothing to produce, and
        // building both up front keeps this constructor the only place that touches the schema.
        qlConfigJSON = new JSONHolder(
            createSystemConfig(servletContext, domain, catalogs, false)
        );
        reducedConfigJSON = new JSONHolder(
            createSystemConfig(servletContext, domain, catalogs, true)
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
    ///                 the introspected schema and the domain meta data. See {@link #emptyMeta()} for what
    ///                 "empty" has to mean here. It has no catalogs either.
    private QLiveConfig createSystemConfig(
        ServletContext servletContext,
        QLiveDomain domain,
        Map<String, List<Map<String, Object>>> catalogs,
        boolean reduced
    )
    {
        QLiveConfig qlConfig = new QLiveConfig();
        qlConfig.setContextPath(servletContext.getContextPath());

        if (reduced)
        {
            qlConfig.setSchema(Map.of("types", List.of()));
            qlConfig.setMeta(emptyMeta());
            qlConfig.setCatalogs(Map.of());

            return qlConfig;
        }

        final Map<String, Object> raw = IntrospectionUtil.introspect(domain.getGraphQLSchema());

        final Map<String, Object> introspected = (Map<String, Object>) pathUtil.getPropertyPath(raw, "data.__schema");
        final Map<String, Object> schema = prod ? (Map<String, Object>) withoutDescriptions(introspected) : introspected;
        final Map<String, Object> cleaned = new HashMap<>(schema);

        cleaned.put("types", schema.get("types"));

        if (log.isDebugEnabled())
        {
            log.debug("Raw schema is {}",  JSONUtil.formatJSON(JSONUtil.DEFAULT_GENERATOR.forValue(cleaned)));
        }

        qlConfig.setMeta(domain.getMetaData());
        qlConfig.setSchema(cleaned);
        qlConfig.setCatalogs(catalogs);

        return qlConfig;
    }


    /// Returns a copy of the given part of an introspection result with every description set to null, which is
    /// what introspection answers where there is none, so the client reads the result the same either way.
    ///
    /// @param value    introspection result, or a part of it
    static Object withoutDescriptions(Object value)
    {
        if (value instanceof Map<?, ?> map)
        {
            final Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet())
            {
                copy.put(e.getKey(), "description".equals(e.getKey()) ? null : withoutDescriptions(e.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list)
        {
            final List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list)
            {
                copy.add(withoutDescriptions(element));
            }
            return copy;
        }
        return value;
    }


    /// domain meta data with every builtin addendum present and empty.
    ///
    /// Empty, not absent: the client dereferences `meta.types`, `meta.genericTypes`, `meta.relations` and
    /// `meta.manyToMany` unconditionally, so leaving any of them off the wire turns a reduced page into a
    /// startup crash rather than a smaller payload.
    private static DomainMeta emptyMeta()
    {
        final DomainMeta meta = new DomainMeta(Map.of());
        meta.addAddendum(DomainMeta.RELATIONS, List.of());
        meta.addAddendum(DomainMeta.MANY_TO_MANY, List.of());
        meta.addAddendum(DomainMeta.GENERIC_TYPES, List.of());

        return meta;
    }



    @Override
    public QLiveBoostrap provideConfig(CsrfToken csrfToken, String path, String acceptLanguage)
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

        final boolean withSchema = needsSchema(staticAnalysis, module);
        qLiveBoostrap.setConfig(withSchema ? qlConfigJSON : reducedConfigJSON);
        qLiveBoostrap.setData(injectionService.provideInjections(staticAnalysis, module));
        qLiveBoostrap.setCsrfToken(new ClientCsrfToken(csrfToken));
        qLiveBoostrap.setAuthentication(AppAuthentication.current());

        final Locale locale = translationService.resolveLocale(acceptLanguage);
        qLiveBoostrap.setLocale(locale.toLanguageTag());
        qLiveBoostrap.setTranslations(translationService.getTranslations(locale));
        if (withSchema)
        {
            qLiveBoostrap.setLayouts(layouts(locale));
        }

        return qLiveBoostrap;
    }


    /// Forgets the named layouts. The next page in each locale asks the layout providers again, and gets their
    /// layouts checked: what a provider whose layouts changed calls.
    @Override
    public void flushCache()
    {
        layoutJSON.clear();
    }


    /// The named layouts of all providers for pages in the locale, as JSON, built and checked on first use.
    ///
    /// @throws io.github.qlivedev.qlive.runtime.QLiveException for layouts that refer to what the domain doesn't have
    private JSONHolder layouts(Locale locale)
    {
        return layoutJSON.computeIfAbsent(locale, l -> {
            final Layouts layouts = new Layouts();
            for (LayoutProvider provider : layoutProviders)
            {
                layouts.putAll(provider.getLayouts(l));
            }
            layoutCheck.check(layouts);
            return new JSONHolder(layouts.asMap());
        });
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
