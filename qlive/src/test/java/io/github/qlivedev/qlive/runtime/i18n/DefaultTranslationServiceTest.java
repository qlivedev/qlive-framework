package io.github.qlivedev.qlive.runtime.i18n;

import io.github.qlivedev.qlive.model.ts.TrackUsageData;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.runtime.domain.TestDomainConfig;
import io.github.qlivedev.qlive.api.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Covers which tags there are, which stored translations a page gets for them, and which of the offered locales
/// a browser is served in.
class DefaultTranslationServiceTest
{
    private final static TrackUsageData ANALYSIS = JSONUtil.DEFAULT_PARSER.parse(
        TrackUsageData.class,
        """
        {
            "usages": {
                "./app/Home": {
                    "requires": [],
                    "calls": { "i18n": [ ["Welcome {0}"], ["Home"] ], "useActionDraft": [ ["approveFoo"] ] }
                },
                "./app/Bulk": { "requires": [], "calls": { "issueAction": [ ["approveFoos"] ] } },
                "./component/Other": { "requires": [], "calls": { "useInjection": [ ["Q_Foo"] ] } },
                "@qlivedev/qlive-ts": {
                    "requires": [],
                    "calls": { "i18n": [ ["Not a number"], ["Home"], ["Stale rows"] ] },
                    "translations": { "en": { "Not a number": "Not a number", "Stale rows": "Rows changed elsewhere" } }
                }
            }
        }
        """
    );

    /// What the executor would say: each action's rule keys and input labels.
    private final static Map<String, Set<String>> ACTION_TAGS = Map.of(
        "approveFoo", Set.of("Rule.key", "approveFoo.comment"),
        "approveFoos", Set.of("Rule.bulk"),
        "deleteFoo", Set.of("Rule.unused")
    );

    private final DefaultTranslationService service = new DefaultTranslationService(
        null,
        null,
        () -> null,
        DefaultTranslationService.parseLocales(List.of("en", "de", "de-CH")),
        false,
        Map.of()
    );


    @Test
    void collectsTheTagsOfTheCallsAndOfTheDomainTypes()
    {
        final Set<String> tags = DefaultTranslationService.tags(ANALYSIS, TestDomainConfig.domainNoMeta(), ACTION_TAGS);

        // the application's calls and QLive's own, the latter filed under a module of their own
        assertThat(tags, hasItems("Welcome {0}", "Home", "Not a number"));
        // every table-backed type, by itself and with each of its fields
        assertThat(tags, hasItems("TestFoo", "TestFoo.name", "TestFoo.num", "TestFooType", "TestFooType.ordinal"));
        // the query type is no domain type of a table
        assertThat(tags, not(hasItem("QueryType")));
        // the tags of the actions the frontend uses, by useActionDraft() or issueAction(), and of no other
        assertThat(tags, hasItems("Rule.key", "approveFoo.comment", "Rule.bulk"));
        assertThat(tags, not(hasItem("Rule.unused")));
    }


    @Test
    void collectsTheDomainTagsWithoutAnalysis()
    {
        final Set<String> tags = DefaultTranslationService.tags(null, TestDomainConfig.domainNoMeta(), ACTION_TAGS);

        assertThat(tags, hasItem("TestFoo.name"));
        // without an analysis, nothing says which actions are used, so every action's are offered
        assertThat(tags, hasItems("Rule.key", "Rule.bulk", "Rule.unused"));
    }


    @Test
    void offersTheStoredTranslationsOfKnownTagsOnly()
    {
        final Map<String, String> translations = DefaultTranslationService.translations(
            Set.of("Foo.name", "Welcome {0}", "Home"),
            Map.of("Foo.name", "Name", "Welcome {0}", "Willkommen {0}", "Gone", "Weg")
        );

        assertThat(translations, is(Map.of("Foo.name", "Name", "Welcome {0}", "Willkommen {0}")));
    }


    @Test
    void shipsQLivesEnglishToEveryEnglishLocale()
    {
        assertThat(
            DefaultTranslationService.shipped(ANALYSIS, Locale.ENGLISH),
            is(Map.of("Not a number", "Not a number", "Stale rows", "Rows changed elsewhere"))
        );
        assertThat(DefaultTranslationService.shipped(ANALYSIS, Locale.UK).get("Stale rows"), is("Rows changed elsewhere"));
        assertThat(DefaultTranslationService.shipped(ANALYSIS, Locale.GERMAN), is(Map.of()));
        assertThat(DefaultTranslationService.shipped(null, Locale.ENGLISH), is(Map.of()));
    }


    @Test
    void readsThePlaceholders()
    {
        assertThat(DefaultTranslationService.placeholders("{1} von {0}"), is(Set.of("0", "1")));
        assertThat(DefaultTranslationService.placeholders("Foo.name"), is(Set.of()));
    }


    @Test
    void servesTheBestOfTheOfferedLocales()
    {
        assertThat(service.resolveLocale("de-DE,de;q=0.9,en;q=0.8"), is(Locale.forLanguageTag("de")));
        assertThat(service.resolveLocale("de-CH"), is(Locale.forLanguageTag("de-CH")));
        assertThat(service.resolveLocale("fr-FR,en;q=0.5"), is(Locale.ENGLISH));
    }


    @Test
    void servesTheFirstOfferedLocaleWhereNoneIsAskedFor()
    {
        assertThat(service.resolveLocale(null), is(Locale.ENGLISH));
        assertThat(service.resolveLocale(""), is(Locale.ENGLISH));
        assertThat(service.resolveLocale("fr"), is(Locale.ENGLISH));
        assertThat(service.resolveLocale("*"), is(Locale.ENGLISH));
        assertThat(service.resolveLocale("not a header;;q=x"), is(Locale.ENGLISH));
    }


    @Test
    void refusesAnOfferedLocaleThatNamesNoLanguage()
    {
        assertThrows(QLiveException.class, () -> DefaultTranslationService.parseLocales(List.of("en", "de_DE")));
        assertThrows(QLiveException.class, () -> new DefaultTranslationService(null, null, () -> null, List.of(), false, Map.of()));
    }
}
