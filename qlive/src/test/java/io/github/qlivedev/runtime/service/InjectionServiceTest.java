package io.github.qlivedev.runtime.service;

import io.github.qlivedev.model.bootstrap.Injection;
import io.github.qlivedev.model.ts.TrackUsageData;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.domain.TestDomainConfig;
import io.github.qlivedev.runtime.domain.TestLogic;
import io.github.qlivedev.runtime.meta.QueryConfigMetadataProvider;
import io.github.qlivedev.testdomain.tables.pojos.TestFoo;
import de.quinscape.domainql.DomainQL;
import io.github.qlivedev.runtime.util.JSONUtil;
import graphql.GraphQL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the way from a useInjection() call recorded by the frontend build to the data the page is served
 * with: which query the identifier resolves to, which id it goes out under, and what actually running it
 * produces.
 */
class InjectionServiceTest
{
    /**
     * A query on the test domain, as the track-usage data holds it: one string, JSON-escaped.
     */
    private final static String Q_TEST =
        "query Q_Test($config: QueryConfig!) { queryTestFooDocument(config: $config) { type config } }";

    /**
     * A query taking a list of the same type, which the test logic answers with the page size of each.
     */
    private final static String Q_SIZES =
        "query Q_Sizes($configs: [QueryConfig!]!) { queryPageSizes(configs: $configs) }";

    /**
     * A component doing what only a view may do.
     */
    private final static TrackUsageData COMPONENT_INJECTS = analysis("""
        {
            "./app/Home": {
                "requires": [ "./component/Widget" ],
                "calls": {}
            },
            "./component/Widget": {
                "requires": [ "./app/Q_Test" ],
                "calls": {
                    "useInjection": [ [ { "__identifier": "Q_Test" }, { "config": { "pageSize": 7 } } ] ]
                }
            },
            "./app/Q_Test": {
                "requires": [],
                "calls": { "GraphQLQuery": [ [ "%s" ] ] }
            }
        }
        """.formatted(Q_TEST));

    /**
     * The same injection where it belongs.
     */
    private final static TrackUsageData VIEW_INJECTS = analysis("""
        {
            "./app/Home": {
                "requires": [ "./app/Q_Test" ],
                "calls": {
                    "useInjection": [ [ { "__identifier": "Q_Test" }, { "config": { "pageSize": 7 } } ] ]
                }
            },
            "./app/Q_Test": {
                "requires": [],
                "calls": { "GraphQLQuery": [ [ "%s" ] ] }
            }
        }
        """.formatted(Q_TEST));

    private InjectionService injectionService;


    @BeforeEach
    void createInjectionService()
    {
        final DomainQL domainQL = TestDomainConfig.domainQL(new TestLogic());

        injectionService = new InjectionService(
            GraphQL.newGraphQL(domainQL.getGraphQLSchema()).build(),
            domainQL
        );
    }


    @Test
    void runsTheQueryTheViewInjects()
    {
        final Map<String, Injection> injections = injectionService.provideInjections(
            analysis("""
                {
                    "./app/Home": {
                        "requires": [ "@quinscape/qlive-ts", "./app/Q_Test" ],
                        "calls": {
                            "useInjection": [
                                [ { "__identifier": "Q_Test" }, { "config": { "pageSize": 5 } } ]
                            ]
                        }
                    },
                    "./app/Q_Test": {
                        "requires": [ "@quinscape/qlive-ts" ],
                        "calls": { "GraphQLQuery": [ [ "%s" ] ] }
                    }
                }
                """.formatted(Q_TEST)),
            "./app/Home"
        );

        assertThat(injections.keySet(), contains("Q_Test"));

        final Injection injection = injections.get("Q_Test");

        // the GraphQL type of the injected value, for the client to log next to it
        assertThat(injection.getType(), is("TestFooDocument"));

        // The data is the GraphQL result keyed by result key, which is what the client's inject() takes the
        // first value of -- not the document itself.
        assertThat(
            JSONUtil.DEFAULT_GENERATOR.forValue(injection.getData()),
            containsString("\"queryTestFooDocument\"")
        );

        // The parameters of the call reached the query as its variables. They come out of JSON, so 5 arrives
        // as a Long where QueryConfig wants an int.
        assertThat(pageSizeOf(injection), is(5));
    }


    @Test
    void injectsNothingForComponentsTheViewImports()
    {
        // Only the view's own calls run. What a view imports is not what it renders, and a component that
        // could inject would cost a query in every view importing it.
        final Map<String, Injection> injections = injectionService.provideInjections(
            COMPONENT_INJECTS,
            "./app/Home"
        );

        assertThat(injections, is(Map.of()));
    }


    @Test
    void namesTheModulesThatInjectWithoutBeingAView()
    {
        assertThat(
            InjectionService.injectionsOutsideViews(COMPONENT_INJECTS),
            contains("./component/Widget")
        );

        assertThat(
            InjectionService.describeInjectionsOutsideViews(
                InjectionService.injectionsOutsideViews(COMPONENT_INJECTS)
            ),
            containsString("./component/Widget")
        );
    }


    @Test
    void findsNothingToComplainAboutWhenEveryInjectionSitsInAView()
    {
        assertThat(InjectionService.injectionsOutsideViews(VIEW_INJECTS), is(List.of()));
    }


    @Test
    void resolvesQueriesCollectedInASharedModule()
    {
        // No module named after the query here, so the operation name is what the identifier is matched
        // against.
        final Map<String, Injection> injections = injectionService.provideInjections(
            analysis("""
                {
                    "./app/Home": {
                        "requires": [ "./app/queries" ],
                        "calls": {
                            "useInjection": [ [ { "__identifier": "Q_Test" }, { "config": {} } ] ]
                        }
                    },
                    "./app/queries": {
                        "requires": [],
                        "calls": { "GraphQLQuery": [ [ "%s" ] ] }
                    }
                }
                """.formatted(Q_TEST)),
            "./app/Home"
        );

        assertThat(injections.keySet(), contains("Q_Test"));

        // nothing said about the page size, so the config arrives at its meta config defaults
        assertThat(pageSizeOf(injections.get("Q_Test")), is(5));
    }


    @Test
    void completesAQueryConfigTheCallOnlyPartlyNames()
    {
        // The call names an offset and nothing else, the way QueryConfigDelta does on the client -- the
        // rest of the config comes from its own defaults.
        final Map<String, Injection> injections = injectionService.provideInjections(
            analysis("""
                {
                    "./app/Home": {
                        "requires": [ "./app/Q_Test" ],
                        "calls": {
                            "useInjection": [ [ { "__identifier": "Q_Test" }, { "config": { "offset": 2 } } ] ]
                        }
                    },
                    "./app/Q_Test": {
                        "requires": [],
                        "calls": { "GraphQLQuery": [ [ "%s" ] ] }
                    }
                }
                """.formatted(Q_TEST)),
            "./app/Home"
        );

        assertThat(configOf(injections.get("Q_Test")).get("offset"), is(2));
        assertThat(configOf(injections.get("Q_Test")).get("pageSize"), is(5));
    }


    @Test
    void reportsAQueryConfigFieldThatIsNoNumber()
    {
        final TrackUsageData analysis = analysis("""
            {
                "./app/Home": {
                    "requires": [ "./app/Q_Test" ],
                    "calls": {
                        "useInjection": [
                            [ { "__identifier": "Q_Test" }, { "config": { "pageSize": "five" } } ]
                        ]
                    }
                },
                "./app/Q_Test": {
                    "requires": [],
                    "calls": { "GraphQLQuery": [ [ "%s" ] ] }
                }
            }
            """.formatted(Q_TEST));

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> injectionService.provideInjections(analysis, "./app/Home")
        );

        assertThat(e.getMessage(), containsString("pageSize"));
    }


    @Test
    void keysTheInjectionByItsIdParameter()
    {
        final Map<String, Injection> injections = injectionService.provideInjections(
            analysis("""
                {
                    "./app/Home": {
                        "requires": [ "./app/Q_Test" ],
                        "calls": {
                            "useInjection": [
                                [
                                    { "__identifier": "Q_Test" },
                                    { "__id": "second", "config": { "pageSize": 3 } }
                                ]
                            ]
                        }
                    },
                    "./app/Q_Test": {
                        "requires": [],
                        "calls": { "GraphQLQuery": [ [ "%s" ] ] }
                    }
                }
                """.formatted(Q_TEST)),
            "./app/Home"
        );

        // read back under the same id the client's inject() looks it up with
        assertThat(injections.keySet(), contains("second"));

        // __id disambiguates the injection, it is not a variable of the query -- one it does not declare
        // would have failed the execution
        assertThat(pageSizeOf(injections.get("second")), is(3));
    }


    @Test
    void injectsNothingForAPathWithoutAModule()
    {
        assertThat(injectionService.provideInjections(analysis("{}"), null), is(Map.of()));
    }


    @Test
    void reportsAnIdentifierThatResolvesToNoQuery()
    {
        final TrackUsageData analysis = analysis("""
            {
                "./app/Home": {
                    "requires": [ "@quinscape/qlive-ts" ],
                    "calls": {
                        "useInjection": [ [ { "__identifier": "Q_Missing" }, { "config": {} } ] ]
                    }
                }
            }
            """);

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> injectionService.provideInjections(analysis, "./app/Home")
        );

        assertThat(e.getMessage(), containsString("Q_Missing"));
    }


    @Test
    void reportsTwoInjectionsFightingOverOneId()
    {
        // The very case __id exists for: one view reading the same query twice, with different parameters.
        final TrackUsageData analysis = analysis("""
            {
                "./app/Home": {
                    "requires": [ "./app/Q_Test" ],
                    "calls": {
                        "useInjection": [
                            [ { "__identifier": "Q_Test" }, { "config": { "pageSize": 5 } } ],
                            [ { "__identifier": "Q_Test" }, { "config": { "pageSize": 9 } } ]
                        ]
                    }
                },
                "./app/Q_Test": {
                    "requires": [],
                    "calls": { "GraphQLQuery": [ [ "%s" ] ] }
                }
            }
            """.formatted(Q_TEST));

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> injectionService.provideInjections(analysis, "./app/Home")
        );

        assertThat(e.getMessage(), containsString("__id"));
    }


    @Test
    void handlesMissingQueryConfig()
    {
        // The query takes a config and this call names none, which is the normal case: what the type
        // declares is what the injection runs with.
        final TrackUsageData analysis = analysis("""
            {
                "./app/Home": {
                    "requires": [ "./app/Q_Test" ],
                    "calls": { "useInjection": [ [ { "__identifier": "Q_Test" } ] ] }
                },
                "./app/Q_Test": {
                    "requires": [],
                    "calls": { "GraphQLQuery": [ [ "%s" ] ] }
                }
            }
            """.formatted(Q_TEST));

        final Map<String, Injection> injections = injectionService.provideInjections(analysis, "./app/Home");

        assertThat(injections.keySet(), contains("Q_Test"));

        // we get the pageSize from the meta config
        assertThat(pageSizeOf(injections.get("Q_Test")), is(5));
    }


    @Test
    void injectsNothingForAModuleWithoutInjections()
    {
        final Map<String, Injection> injections = injectionService.provideInjections(
            analysis("""
                {
                    "./login": {
                        "requires": [ "@quinscape/qlive-ts" ],
                        "calls": { "noSchema": [ [] ] }
                    }
                }
                """),
            "./login"
        );

        assertThat(injections, is(Map.of()));
    }


    /**
     * The dev server pushes the modules one save changed, so a module that was not saved keeps the exact
     * references it was pushed with. The plans read from a changed module have to be built again anyway --
     * here the query the view injects is renamed, which the injection is keyed by.
     */
    @Test
    void rebuildsThePlansOfAViewWhoseQueryChanged()
    {
        final DevStaticAnalysisProvider provider = new DevStaticAnalysisProvider();
        provider.replace(VIEW_INJECTS);

        assertThat(
            injectionService.provideInjections(provider.getTrackUsageData(), "./app/Home").keySet(),
            contains("Q_Test")
        );

        // ./app/Home is not in this push and keeps its references: what changed is the module it imports
        provider.merge(analysis("""
            {
                "./app/Q_Test": {
                    "requires": [],
                    "calls": { "GraphQLQuery": [ [ "%s" ] ] }
                }
            }
            """.formatted(Q_TEST.replace("Q_Test", "Q_Renamed"))));

        assertThat(
            injectionService.provideInjections(provider.getTrackUsageData(), "./app/Home").keySet(),
            contains("Q_Renamed")
        );
    }


    /**
     * The counterpart: a push that touches neither the view nor anything it imports leaves its injections
     * exactly as they were.
     */
    @Test
    void keepsTheInjectionsOfAViewAnUnrelatedPushDoesNotTouch()
    {
        final DevStaticAnalysisProvider provider = new DevStaticAnalysisProvider();
        provider.replace(VIEW_INJECTS);

        final Map<String, Injection> before =
            injectionService.provideInjections(provider.getTrackUsageData(), "./app/Home");

        provider.merge(analysis("""
            {
                "./app/Other": {
                    "requires": [],
                    "calls": {}
                }
            }
            """));

        final Map<String, Injection> after =
            injectionService.provideInjections(provider.getTrackUsageData(), "./app/Home");

        assertThat(after.keySet(), is(before.keySet()));
        assertThat(pageSizeOf(after.get("Q_Test")), is(pageSizeOf(before.get("Q_Test"))));
    }


    @Test
    void completesEveryQueryConfigOfAListArgument()
    {
        // A list is not the one place an argument goes through unprocessed: each element is the processor's
        // business, so each arrives as the complete config its delta stands for.
        final Map<String, Injection> injections = injectionService.provideInjections(
            analysis("""
                {
                    "./app/Home": {
                        "requires": [ "./app/Q_Sizes" ],
                        "calls": {
                            "useInjection": [
                                [
                                    { "__identifier": "Q_Sizes" },
                                    { "configs": [ { "pageSize": 7 }, { "offset": 1 } ] }
                                ]
                            ]
                        }
                    },
                    "./app/Q_Sizes": {
                        "requires": [],
                        "calls": { "GraphQLQuery": [ [ "%s" ] ] }
                    }
                }
                """.formatted(Q_SIZES)),
            "./app/Home"
        );

        assertThat(pageSizesOf(injections.get("Q_Sizes")), contains(7, 0));
    }


    @Test
    void letsTheApplicationProcessAnArgumentTypeOfItsOwn()
    {
        // What an application contributes as a bean, here handed over directly: a processor claiming a type
        // decides what the variables of that type are executed with, the framework's own understanding of
        // QueryConfig included.
        final DomainQL domainQL = TestDomainConfig.domainQL(new TestLogic());

        final InjectionService service = new InjectionService(
            GraphQL.newGraphQL(domainQL.getGraphQLSchema()).build(),
            domainQL,
            List.of(new FixedPageSize(42))
        );

        final Map<String, Injection> injections = service.provideInjections(VIEW_INJECTS, "./app/Home");

        // the call said 7, the processor says otherwise
        assertThat(pageSizeOf(injections.get("Q_Test")), is(42));
    }


    @Test
    void reportsWhatTheApplicationsProcessorRejects()
    {
        final DomainQL domainQL = TestDomainConfig.domainQL(new TestLogic());

        final InjectionService service = new InjectionService(
            GraphQL.newGraphQL(domainQL.getGraphQLSchema()).build(),
            domainQL,
            List.of(new InjectionArgumentProcessor()
            {
                @Override
                public boolean handles(String typeName)
                {
                    return true;
                }

                @Override
                public Object process(InjectionArgument argument)
                {
                    throw argument.reject("no reason at all");
                }
            })
        );

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> service.provideInjections(VIEW_INJECTS, "./app/Home")
        );

        // where the offending argument sits, which is the only place the mistake is still visible
        assertThat(e.getMessage(), containsString("./app/Home"));
        assertThat(e.getMessage(), containsString("QueryConfig"));
        assertThat(e.getMessage(), containsString("config"));
        assertThat(e.getMessage(), containsString("no reason at all"));
    }


    @Test
    void startsAQueryConfigAtWhatTheQueriedTypeDeclares()
    {
        // The call says where to start reading and nothing else. What a page of Foos is, and in which order,
        // is the type's to say -- and the query is what says that Foos are what is being queried here.
        final Map<String, Injection> injections = injectionsWithDeclaredDefaults("""
            { "offset": 2 }
            """);

        assertThat(configOf(injections.get("Q_Test")).get("offset"), is(2));
        assertThat(configOf(injections.get("Q_Test")).get("pageSize"), is(20));
        assertThat(sortFieldsOf(injections.get("Q_Test")), contains("name"));
    }


    @Test
    void letsTheCallOverrideWhatTheQueriedTypeDeclares()
    {
        // The type says what a page of Foos usually is, this one view says otherwise. The nearer word wins,
        // the same way it does over the defaults of a config itself.
        final Map<String, Injection> injections = injectionsWithDeclaredDefaults("""
            { "pageSize": 7 }
            """);

        assertThat(configOf(injections.get("Q_Test")).get("pageSize"), is(7));

        // and what the call left out still comes from the type
        assertThat(sortFieldsOf(injections.get("Q_Test")), contains("name"));
    }


    /**
     * Injects Q_Test with the given call parameters, against a domain where TestFoo declares a query config
     * delta of its own.
     */
    private static Map<String, Injection> injectionsWithDeclaredDefaults(String config)
    {
        final DomainQL domainQL = TestDomainConfig.domainQL(
            List.of(
                QueryConfigMetadataProvider.newProvider()
                    .forType(TestFoo.class)
                            .pageSize(20)
                            .sortFields("name")
                            .build()
            ),
            new TestLogic()
        );

        final InjectionService service = new InjectionService(
            GraphQL.newGraphQL(domainQL.getGraphQLSchema()).build(),
            domainQL
        );

        return service.provideInjections(
            analysis("""
                {
                    "./app/Home": {
                        "requires": [ "./app/Q_Test" ],
                        "calls": {
                            "useInjection": [ [ { "__identifier": "Q_Test" }, { "config": %s } ] ]
                        }
                    },
                    "./app/Q_Test": {
                        "requires": [],
                        "calls": { "GraphQLQuery": [ [ "%s" ] ] }
                    }
                }
                """.formatted(config.strip(), Q_TEST)),
            "./app/Home"
        );
    }


    /**
     * An application's processor for a type the framework already handles.
     */
    private record FixedPageSize(int pageSize)
        implements InjectionArgumentProcessor
    {
        @Override
        public boolean handles(String typeName)
        {
            return "QueryConfig".equals(typeName);
        }


        @Override
        public Object process(InjectionArgument argument)
        {
            return Map.of("offset", 0, "pageSize", pageSize);
        }
    }


    @SuppressWarnings("unchecked")
    private static List<String> sortFieldsOf(Injection injection)
    {
        return (List<String>) configOf(injection).get("sortFields");
    }


    @SuppressWarnings("unchecked")
    private static List<Integer> pageSizesOf(Injection injection)
    {
        return (List<Integer>) ((Map<String, Object>) injection.getData()).get("queryPageSizes");
    }


    private static Integer pageSizeOf(Injection injection)
    {
        return (Integer) configOf(injection).get("pageSize");
    }


    /**
     * The config of an injected query document, as it comes back out of the GraphQL result. The test logic
     * echoes the one it was called with, so this is what the injection actually ran with.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> configOf(Injection injection)
    {
        final Map<String, Object> data = (Map<String, Object>) injection.getData();
        final Map<String, Object> document = (Map<String, Object>) data.get("queryTestFooDocument");

        return (Map<String, Object>) document.get("config");
    }


    /**
     * Track-usage data from the usages of a snapshot, i.e. what the frontend build pushes or writes minus
     * the "usages" wrapper.
     */
    private static TrackUsageData analysis(String usages)
    {
        return JSONUtil.DEFAULT_PARSER.parse(
            TrackUsageData.class,
            "{ \"usages\": " + usages + " }"
        );
    }
}
