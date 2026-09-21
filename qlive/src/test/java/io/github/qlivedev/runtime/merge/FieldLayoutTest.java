package io.github.qlivedev.runtime.merge;

import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.domain.TestDomainConfig;
import io.github.qlivedev.runtime.domain.TestLogic;
import io.github.qlivedev.graphql.DomainQL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// What a field mask means, which is a question about a list of names and needs no database to answer.
class FieldLayoutTest
{
    private static DomainQL domainQL;


    @BeforeAll
    static void buildDomain()
    {
        domainQL = TestDomainConfig.domainQL(new TestLogic());
    }


    /// The bit index is the position in the alphabetical field list, and the list is every field of the
    /// GraphQL type -- the relations and the computed properties along with the columns, since all of them
    /// shift the positions of the ones after them.
    @Test
    void numbersEveryFieldOfTheTypeAlphabetically()
    {
        assertThat(
            FieldLayout.of(domainQL, "TestFoo").getFields(),
            contains(
                "created", "description", "flag", "fooType", "id", "name", "num", "owner", "ownerId",
                "type", "version"
            )
        );
    }


    /// Names in, names out. The bits in between are storage, and nothing outside this class has to think
    /// about which one a field owns.
    @Test
    void turnsNamesIntoBitsAndBack()
    {
        final FieldLayout layout = FieldLayout.of(domainQL, "TestFoo");

        final BigInteger mask = layout.mask(List.of("name", "num"));

        // 'name' is the sixth field and 'num' the seventh
        assertThat(mask, is(BigInteger.ZERO.setBit(5).setBit(6)));
        assertThat(List.copyOf(layout.fields(mask)), contains("name", "num"));
    }


    /// A field the layout does not have is left out rather than refused, at both ends: a change may name a
    /// field this deployment added, and a mask written by one that had more fields sets bits past the end
    /// of the list. Neither is an error and a field that is gone cannot be in conflict.
    @Test
    void ignoresTheFieldsItDoesNotHave()
    {
        final FieldLayout layout = FieldLayout.of("Small", List.of("a", "b"));

        assertThat(layout.mask(List.of("a", "somethingElse")), is(BigInteger.ONE));
        assertThat(List.copyOf(layout.fields(BigInteger.ZERO.setBit(1).setBit(40))), contains("b"));
    }


    /// The separator is what keeps the hash honest. Without one the same characters in a different
    /// arrangement are the same input, and two layouts that assign different bits would share an id.
    @Test
    void doesNotLetTheNamesRunTogether()
    {
        assertThat(
            FieldLayout.of("T", List.of("ab", "c")).getId(),
            is(not(FieldLayout.of("T", List.of("a", "bc")).getId()))
        );
    }


    /// The type name is hashed in even though the bit semantics do not need it. Two types whose field lists
    /// happen to be identical -- which the qlive-test domain has -- would otherwise share one row, and
    /// "what did this type look like then" would have no answer.
    @Test
    void tellsTwoTypesWithTheSameFieldsApart()
    {
        assertThat(
            FieldLayout.of("Bar", List.of("id", "name")).getId(),
            is(not(FieldLayout.of("Baz", List.of("id", "name")).getId()))
        );
    }


    /// The same list is the same layout wherever it is computed, which is what lets a version record name
    /// its layout in one column and what makes storing one an upsert.
    @Test
    void isTheSameLayoutWhereverItIsComputed()
    {
        assertThat(
            FieldLayout.of(domainQL, "TestFoo").getId(),
            is(FieldLayout.of("TestFoo", FieldLayout.of(domainQL, "TestFoo").getFields()).getId())
        );
    }


    /// A mask holds 128 bits and a type with more fields than that would have fields no mask can name --
    /// silently absent from every mask, which reads as "nobody changed it". Refused where it can still be
    /// fixed.
    @Test
    void refusesATypeWithMoreFieldsThanAMaskHasBits()
    {
        final List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= FieldLayout.MAX_FIELDS; i++)
        {
            tooMany.add("field" + i);
        }

        assertThat(
            assertThrows(QLiveException.class, () -> FieldLayout.of("Wide", tooMany)).getMessage(),
            containsString("128")
        );
    }
}
