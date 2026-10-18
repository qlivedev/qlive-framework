package io.github.qlivedev.runtime.merge;

import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.domain.TestDomainConfig;
import io.github.qlivedev.runtime.domain.TestLogic;
import io.github.qlivedev.graphql.QLiveDomain;
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
class FieldSetTest
{
    private static QLiveDomain domain;


    @BeforeAll
    static void buildDomain()
    {
        domain = TestDomainConfig.domain(new TestLogic());
    }


    /// The bit index is the position in the alphabetical field list, and the list is every field of the
    /// GraphQL type -- the relations and the computed properties along with the columns, since all of them
    /// shift the positions of the ones after them.
    @Test
    void numbersEveryFieldOfTheTypeAlphabetically()
    {
        assertThat(
            FieldSet.of(domain, "TestFoo").getFields(),
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
        final FieldSet fieldSet = FieldSet.of(domain, "TestFoo");

        final BigInteger mask = fieldSet.mask(List.of("name", "num"));

        // 'name' is the sixth field and 'num' the seventh
        assertThat(mask, is(BigInteger.ZERO.setBit(5).setBit(6)));
        assertThat(List.copyOf(fieldSet.fields(mask)), contains("name", "num"));
    }


    /// A field the field set does not have is left out rather than refused, at both ends: a change may name a
    /// field this deployment added, and a mask written by one that had more fields sets bits past the end
    /// of the list. Neither is an error and a field that is gone cannot be in conflict.
    @Test
    void ignoresTheFieldsItDoesNotHave()
    {
        final FieldSet fieldSet = FieldSet.of("Small", List.of("a", "b"));

        assertThat(fieldSet.mask(List.of("a", "somethingElse")), is(BigInteger.ONE));
        assertThat(List.copyOf(fieldSet.fields(BigInteger.ZERO.setBit(1).setBit(40))), contains("b"));
    }


    /// The separator is what keeps the hash honest. Without one the same characters in a different
    /// arrangement are the same input, and two field sets that assign different bits would share an id.
    @Test
    void doesNotLetTheNamesRunTogether()
    {
        assertThat(
            FieldSet.of("T", List.of("ab", "c")).getId(),
            is(not(FieldSet.of("T", List.of("a", "bc")).getId()))
        );
    }


    /// The type name is hashed in even though the bit semantics do not need it. Two types whose field lists
    /// happen to be identical -- which the qlive-test domain has -- would otherwise share one row, and
    /// "what did this type look like then" would have no answer.
    @Test
    void tellsTwoTypesWithTheSameFieldsApart()
    {
        assertThat(
            FieldSet.of("Bar", List.of("id", "name")).getId(),
            is(not(FieldSet.of("Baz", List.of("id", "name")).getId()))
        );
    }


    /// The same list is the same field set wherever it is computed, which is what lets a version record name
    /// its field set in one column and what makes storing one an upsert.
    @Test
    void isTheSameFieldSetWhereverItIsComputed()
    {
        assertThat(
            FieldSet.of(domain, "TestFoo").getId(),
            is(FieldSet.of("TestFoo", FieldSet.of(domain, "TestFoo").getFields()).getId())
        );
    }


    /// A mask holds 128 bits and a type with more fields than that would have fields no mask can name --
    /// silently absent from every mask, which reads as "nobody changed it". Refused where it can still be
    /// fixed.
    @Test
    void refusesATypeWithMoreFieldsThanAMaskHasBits()
    {
        final List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= FieldSet.MAX_FIELDS; i++)
        {
            tooMany.add("field" + i);
        }

        assertThat(
            assertThrows(QLiveException.class, () -> FieldSet.of("Wide", tooMany)).getMessage(),
            containsString("128")
        );
    }
}
