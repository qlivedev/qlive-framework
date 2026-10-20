package io.github.qlivedev.qlivetest.runtime;

import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.controller.RelatedRowsController;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static io.github.qlivedev.qlivetest.domain.Tables.APP_USER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Asks for the rows a picker offers, against this application's own database.
///
/// The user it looks for is whichever the table holds first, so the test depends on there being one user
/// and on nothing else about the rows (see docs/design/test-isolation.md).
@SpringBootTest
class RelatedRowsControllerTest
{
    @Autowired
    private RelatedRowsController controller;

    @Autowired
    private DSLContext dslContext;


    @Test
    @SuppressWarnings("unchecked")
    void servesKeyAndNameFieldsOfTheRowsTheConfigFinds()
    {
        final Record2<String, String> user = dslContext.select(APP_USER.ID, APP_USER.LOGIN)
            .from(APP_USER)
            .orderBy(APP_USER.LOGIN)
            .limit(1)
            .fetchOne();

        final Map<String, Object> answer = controller.relatedRows(Map.of(
            "type", "AppUser",
            "config", Map.of(
                "condition", Map.of(
                    "type", "Condition",
                    "name", "eq",
                    "operands", List.of(
                        Map.of("type", "FieldReference", "name", "login"),
                        Map.of("type", "Value", "scalarType", "String", "value", user.value2())
                    )
                )
            )
        ));

        assertThat(answer.get("type"), is("AppUser"));
        assertThat(answer.get("rowCount"), is(1));

        final List<Map<String, Object>> rows = (List<Map<String, Object>>) answer.get("rows");
        assertThat(rows, contains(Map.of("id", user.value1(), "login", user.value2())));
        // the key and the name, never all of a user
        assertThat(List.copyOf(rows.getFirst().keySet()), contains("id", "login"));
    }


    @Test
    void refusesATypeNoForeignKeyReferences()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> controller.relatedRows(Map.of("type", "Qux"))
        );
        assertThat(e.getMessage(), containsString("No foreign key references 'Qux'"));
    }


    @Test
    void refusesAFilterOnAFieldItDoesNotServe()
    {
        assertThrows(
            QLiveException.class,
            () -> controller.relatedRows(Map.of(
                "type", "AppUser",
                "config", Map.of(
                    "condition", Map.of(
                        "type", "Condition",
                        "name", "eq",
                        "operands", List.of(
                            Map.of("type", "FieldReference", "name", "password"),
                            Map.of("type", "Value", "scalarType", "String", "value", "x")
                        )
                    )
                )
            ))
        );
    }
}
