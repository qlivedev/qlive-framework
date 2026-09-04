package com.dataciders.qlive.runtime.domain;

import de.quinscape.domainql.DomainQL;
import com.dataciders.qlive.model.ts.ModuleFunctionReferences;
import com.dataciders.qlive.model.ts.TrackUsageData;
import de.quinscape.spring.jsview.util.JSONUtil;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.dataciders.qlive.runtime.QLiveException;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;


class GraphQLQueryTypingServiceTest
{
    private final static Logger log = LoggerFactory.getLogger(GraphQLQueryTypingServiceTest.class);

    private GraphQLQueryTypingService service;

    private final static File TS_SOURCE_ROOT = new File(
        GraphQLQueryTypingServiceTest.class.getClassLoader()
            .getResource("test-ts-root/")
            .getFile()
    );

    static File moduleFile;

    @BeforeAll
    public static void setupModule() throws IOException
    {
        moduleFile = new File(TS_SOURCE_ROOT, "sub/Q_Test.ts");
        FileUtils.copyFile(
            new File(TS_SOURCE_ROOT, "sub/Q_Test.template.ts"),
            moduleFile
        );
        moduleFile.deleteOnExit();
    }
    
    @BeforeEach
    public void createTypingService()
    {
        final DomainQL domainQL = TestDomainConfig.domainQL(
            new TestLogic()
        );
        service = new GraphQLQueryTypingService(
            domainQL,
            TS_SOURCE_ROOT
        );


        //log.info(new SchemaPrinter().print(domainQL.getGraphQLSchema()));

    }


    @Test
    public void testFullUpdate() throws IOException
    {
        final TrackUsageData data = readTrackUsageData();

        service.updateGraphQLQueryTypes(data);

        // reread source modified by service
        String modified = readModule(moduleFile);
        log.info("source: {}", modified);

        final String expected = readModule(
            new File(TS_SOURCE_ROOT, "sub/Q_Test.expected.ts")
        );
        assertThat(modified, is(expected));
    }


    @Test
    void testOperationAlias() throws IOException
    {
        // language=GraphQL
        String typeDef = queryTransform("""
            query Q_Test($config: QueryConfig!) {
                    xxx: queryTestFooDocument(config: $config) {
                        type
                            config
                        rows {
                            name
                            owner {
                                login
                            }
                        }
                    }
                }
            """);

        // language=TypeScript
        assertThat(typeDef, is("""
            Pick<TestFooDocument,"type" | "config"> & {
                rows : Array<Pick<TestFoo,"name"> & {
                    owner : Pick<TestUser,"login">
                }>
            }"""
        ));
    }

    @Test
    void testCompleteSubObject() throws IOException
    {
        // language=GraphQL
        String typeDef = queryTransform("""
            query Q_Test($config: QueryConfig!) {
                    queryTestFooDocument(config: $config) {
                        type
                            config
                        rows {
                            name
                            fooType {
                                name
                                ordinal
                            }
                        }
                    }
            }
            """);


        // language=TypeScript
        assertThat(typeDef, is("""
            Pick<TestFooDocument,"type" | "config"> & {
                rows : Array<Pick<TestFoo,"name" | "fooType">>
            }"""
        ));
    }

    @Test
    void testTypeAlias() throws IOException
    {
        // language=GraphQL
        String typeDef = queryTransform("""
            query Q_Test($config: QueryConfig!) {
                    queryTestFooDocument(config: $config) {
                        type
                            config
                        rows {
                            name
                            fooType {
                                name
                                id: ordinal
                            }
                        }
                    }
            }
            """);

        // language=TypeScript
        assertThat(typeDef, is("""
            Pick<TestFooDocument,"type" | "config"> & {
                rows : Array<Pick<TestFoo,"name"> & {
                    fooType : Pick<TestFooType,"name"> & {
                        id : Int
                    }
                }>
            }"""
        ));
    }

    @Test
    void testNonNullFields() throws IOException
    {
        // language=GraphQL
        String typeDef = queryTransform("""
            query Q_Test($config: QueryConfig!) {
                    queryTestFooDocument(config: $config) {
                        type
                            config
                        rows {
                            name
                            desc: description
                        }
                    }
            }
            """);

        log.info(typeDef);
        
        // language=TypeScript
        assertThat(typeDef, is("""
            Pick<TestFooDocument,"type" | "config"> & {
                rows : Array<Pick<TestFoo,"name"> & {
                    desc? : String
                }>
            }"""
        ));
    }


    @Test
    void testFragmentsAreRefused()
    {
        // language=GraphQL
        final String withSpread = """
            query Q_Test($config: QueryConfig!) {
                    queryTestFooDocument(config: $config) {
                        type
                        config
                        rows {
                            ...RowFields
                        }
                    }
            }
            fragment RowFields on TestFoo {
                name
            }
            """;

        final QLiveException spread = assertThrows(QLiveException.class, () -> queryTransform(withSpread));
        assertThat(spread.getMessage(), containsString("fragments are not supported"));
        // says where, so the message is actionable without hunting for the query
        assertThat(spread.getMessage(), containsString("./sub/Q_Test"));
        assertThat(spread.getMessage(), containsString("query Q_Test"));
        assertThat(spread.getMessage(), containsString("a fragment spread in TestFoo"));

        // language=GraphQL
        final String withInline = """
            query Q_Test($config: QueryConfig!) {
                    queryTestFooDocument(config: $config) {
                        type
                        config
                        rows {
                            ... on TestFoo { name }
                        }
                    }
            }
            """;

        assertThat(
            assertThrows(QLiveException.class, () -> queryTransform(withInline)).getMessage(),
            containsString("an inline fragment in TestFoo")
        );
    }


    private static TrackUsageData readTrackUsageData() throws IOException
    {
        TrackUsageData data = JSONUtil.DEFAULT_PARSER.parse(TrackUsageData.class, FileUtils.readFileToString(
            new File(TS_SOURCE_ROOT, "track-usage.json"),
            "UTF-8")
        );
        return data;
    }


    private static String readModule(File moduleFile) throws IOException
    {
        return FileUtils.readFileToString(moduleFile, "UTF-8");
    }

    public String queryTransform(String graphQLQuery) throws IOException
    {
        final String modulePath = "./sub/Q_Test";

        final TrackUsageData data = readTrackUsageData();

        final ModuleFunctionReferences modFnRef = data.getModuleFunctionReferences(modulePath);

        final List<List<?>> calls = modFnRef.getCalls(ModuleFunctionReferences.GRAPHQL_QUERY_CONSTRUCTOR_NAME);
        final List objects = calls.get(0);
        objects.set(0, graphQLQuery);

        final GraphQLQueryTypingService.SelectionInfo selectionInfo = service.analyzeGraphQLQuery(modFnRef, modulePath);
        final List<GraphQLQueryTypingService.SelectionTypeNode> selectedOperations = selectionInfo.selectedOperations();

        if (!selectedOperations.isEmpty())
        {
            final GraphQLQueryTypingService.QueryInfo result = selectionInfo.result();

            String rootTypeName = result.rootTypeName();
            boolean allComplete = result.allComplete();
            log.trace("{}: {}, complete = {}", rootTypeName, selectedOperations, allComplete);

            return service.renderResultType(selectedOperations);
        }
        return null;
    }
}
