package io.github.qlivedev.graphql;

import com.github.javaparser.utils.SourceRoot;
import io.github.qlivedev.graphql.docs.DocsExtractor;
import io.github.qlivedev.graphql.docs.FieldDoc;
import io.github.qlivedev.graphql.docs.TypeDoc;
import io.github.qlivedev.graphql.docs.TypeDocs;
import io.github.qlivedev.graphql.generic.DomainObject;
import io.github.qlivedev.graphql.logicimpl.DocumentedLogic;
import io.github.qlivedev.graphql.testdomain.Public;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class DomainQLTypeDocTest
{
    private final static Logger log = LoggerFactory.getLogger(DomainQLTypeDocTest.class);

    @Test
    public void testTypeDocSchema() throws IOException
    {
        final QLiveDomain domainQL = DomainQL.newDomainQL(null)
            .objectTypes(Public.PUBLIC)
            .logicBeans(Collections.singleton(new DocumentedLogic()))
            .withTypeDocs(
                generateDocs()
            )
            .build();



        final GraphQLSchema graphQLSchema = domainQL.getGraphQLSchema();

        //log.info(new SchemaPrinter().print(graphQLSchema));

        final GraphQLObjectType queryType = (GraphQLObjectType) graphQLSchema.getType(TypeDoc.QUERY_TYPE);
        final GraphQLFieldDefinition queryField = queryType.getFieldDefinition("query");
        assertThat(queryField.getDescription(), is("A minimal GraphQL query"));
        final GraphQLFieldDefinition queryField2 = queryType.getFieldDefinition("anotherQuery");
        assertThat(queryField2.getDescription(), is("Another query"));
        final GraphQLFieldDefinition queryField3 = queryType.getFieldDefinition("genericPagedFoo");
        assertThat(queryField3.getDescription(), is("Paginated result of type Foo"));

        final GraphQLObjectType mutationType = (GraphQLObjectType) graphQLSchema.getType(TypeDoc.MUTATION_TYPE);

        final GraphQLFieldDefinition mutationField = mutationType.getFieldDefinition("mutation");
        assertThat(mutationField.getDescription(), is("A GraphQL mutation"));


        final GraphQLObjectType type = (GraphQLObjectType) graphQLSchema.getType("DocumentedBean");

        assertThat(type.getDescription(), is("Target for DocsExtractorTest"));

        final GraphQLFieldDefinition nameField = type.getFieldDefinition("name");
        assertThat(nameField.getDescription(), is("Name desc from getter"));

        final GraphQLFieldDefinition numField = type.getFieldDefinition("num");
        assertThat(numField.getDescription(), is("Num desc from setter"));

        final GraphQLEnumType enumType = (GraphQLEnumType) graphQLSchema.getType("DocumentedEnum");

        assertThat(enumType.getValue("AAA").getDescription(), is("Option A"));
        assertThat(enumType.getValue("BBB").getDescription(), is("Option B"));
        // {@link ... } are stripped from enum value descriptions, too
        assertThat(enumType.getValue("CCC").getDescription(), is("Option C, see DocumentedBean"));

        // test optional JOOQ documentation by JSON data
        final GraphQLObjectType jooqType = (GraphQLObjectType) graphQLSchema.getType("SourceFour");
        assertThat(jooqType.getDescription(), is("SourceFour desc"));

        final GraphQLFieldDefinition idField = jooqType.getFieldDefinition(DomainObject.ID);
        assertThat(idField.getDescription(), is("Custom desc for SourceFour.id"));


        final GraphQLObjectType pagedFooType = (GraphQLObjectType) graphQLSchema.getType("PagedFoo");

        final GraphQLFieldDefinition rowCountField = pagedFooType.getFieldDefinition("rowCount");
        assertThat(rowCountField.getDescription(), is("Row count available."));
        final GraphQLFieldDefinition rowsField = pagedFooType.getFieldDefinition("rows");
        assertThat(rowsField.getDescription(), is("List of Foo rows."));
    }

    private List<TypeDoc> generateDocs() throws IOException
    {
        SourceRoot mainSourceRoot = new SourceRoot(Paths.get("./src/main/java/"));
        SourceRoot testSourceRoot = new SourceRoot(Paths.get("./src/test/java/"));

        final DocsExtractor docsExtractor = new DocsExtractor();

        final List<TypeDoc> docs = new ArrayList<>();

        docs.addAll(
            docsExtractor.extract(testSourceRoot,"", "io/github/qlivedev/graphql/logicimpl/DocumentedLogic.java")
        );
        docs.addAll(
            docsExtractor.extract(testSourceRoot,"", "io/github/qlivedev/graphql/beans/DocumentedBean.java")
        );
        docs.addAll(
            docsExtractor.extract(testSourceRoot,"", "io/github/qlivedev/graphql/beans/DocumentedEnum.java")
        );
        docs.addAll(
            docsExtractor.extract(mainSourceRoot,"", "io/github/qlivedev/graphql/util/Paged.java")
        );

        final List<TypeDoc> normalized = TypeDocs.normalize(docs);

        //
        // JOOQ POJOs can be documented if the corresponding data is included in the JSON docs
        //
        final TypeDoc jooqExampleDoc = new TypeDoc("SourceFour");
        jooqExampleDoc.setDescription("SourceFour desc");

        jooqExampleDoc.setFieldDocs(
            Collections.singletonList(
                new FieldDoc(DomainObject.ID, "Custom desc for SourceFour.id")
            )
        );

        normalized.add(
            jooqExampleDoc
        );


        return normalized;
    }
}
