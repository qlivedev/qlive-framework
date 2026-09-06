package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.bootstrap.Injection;
import com.dataciders.qlive.model.ts.TrackUsageData;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.domain.TestDomainConfig;
import com.dataciders.qlive.runtime.domain.TestLogic;
import de.quinscape.domainql.DomainQL;
import de.quinscape.spring.jsview.util.JSONUtil;
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
            domainQL.getGraphQLSchema()
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

        // nothing said about the page size, so the config arrives at its default
        assertThat(pageSizeOf(injections.get("Q_Test")), is(0));
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
        assertThat(configOf(injections.get("Q_Test")).get("pageSize"), is(0));
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
    void reportsErrorsOfTheInjectedQuery()
    {
        // The query needs a config, and this call names none -- the kind of mismatch that would otherwise
        // surface as missing data in the browser.
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

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> injectionService.provideInjections(analysis, "./app/Home")
        );

        assertThat(e.getMessage(), containsString("Q_Test"));
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
