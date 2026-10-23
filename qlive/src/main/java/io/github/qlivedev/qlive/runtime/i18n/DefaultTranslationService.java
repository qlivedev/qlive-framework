package io.github.qlivedev.qlive.runtime.i18n;

import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLTypeUtil;
import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.util.JSONHolder;
import io.github.qlivedev.qlive.model.ts.ModuleFunctionReferences;
import io.github.qlivedev.qlive.model.ts.TrackUsageData;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.runtime.service.StaticAnalysisProvider;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Default {@link TranslationService}, keeping the translations in `app_translation`.
///
/// The tags are the static first arguments of every `i18n()` call the frontend's analysis found, QLive's own
/// among them, the name of every type the domain has a table for and of each of its fields, every value of an
/// enum one of those fields holds, `"Size.MEDIUM"`, and the message key of every action rule, which reaches the
/// frontend with a refusal rather than through a call.
///
/// QLive's module in the analysis brings the English of QLive's own tags. It is the translation of those tags into
/// every English locale, and a row in the table for the same tag and locale takes its place.
///
/// In production the translations into a locale are read once and kept: neither the analysis nor the domain
/// changes while the application runs, and a page load costs no query. In dev they are read for every page, so
/// that a translation written into the database and a call added to a view show with the next reload.
public class DefaultTranslationService
    implements TranslationService
{
    private final static Logger log = LoggerFactory.getLogger(DefaultTranslationService.class);

    /// The table the translations are kept in. A constant for the reason `MergeTables` holds them: the table is
    /// the framework's, reached through a plain JOOQ name because its generated class is the application's.
    public final static String APP_TRANSLATION = "app_translation";

    /// Symbolic call name the frontend's track-usage config registers i18n() under.
    public final static String I18N_CALL = "i18n";

    private final static Table<?> TABLE = DSL.table(DSL.name(APP_TRANSLATION));

    private final static Field<String> TAG = DSL.field(DSL.name("tag"), String.class);

    private final static Field<String> LOCALE = DSL.field(DSL.name("locale"), String.class);

    private final static Field<String> VALUE = DSL.field(DSL.name("value"), String.class);

    private final static Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    private final DSLContext dslContext;

    private final QLiveDomain domain;

    private final StaticAnalysisProvider staticAnalysisProvider;

    private final List<Locale> locales;

    private final boolean prod;

    private final Map<String, Set<String>> actionTags;

    private final Map<Locale, JSONHolder> kept = new ConcurrentHashMap<>();

    /// The translations already warned about, so that dev, which reads them for every page, says so once.
    private final Set<String> reported = ConcurrentHashMap.newKeySet();


    /// @param locales  the locales the application offers, the first being the one a request gets that asks for
    ///                 none of them
    /// @param prod     true when running in production, where the translations are read once
    /// @param actionTags the tags the actions bring, see `DefaultActionExecutor.getActionTags()`
    public DefaultTranslationService(
        DSLContext dslContext,
        QLiveDomain domain,
        StaticAnalysisProvider staticAnalysisProvider,
        List<Locale> locales,
        boolean prod,
        Map<String, Set<String>> actionTags
    )
    {
        if (locales.isEmpty())
        {
            throw new QLiveException("An application has to offer at least one locale");
        }

        this.dslContext = dslContext;
        this.domain = domain;
        this.staticAnalysisProvider = staticAnalysisProvider;
        this.locales = List.copyOf(locales);
        this.prod = prod;
        this.actionTags = Map.copyOf(actionTags);
    }


    /// The locales by the language tags they are configured with, `"en"`, `"de-CH"`.
    ///
    /// @throws QLiveException for a tag that names no language, which would otherwise be offered as `und`
    public static List<Locale> parseLocales(List<String> languageTags)
    {
        return languageTags.stream()
            .map(String::trim)
            .map(tag -> {
                final Locale locale = Locale.forLanguageTag(tag);
                if (locale.getLanguage().isEmpty())
                {
                    throw new QLiveException(
                        "'" + tag + "' is not a language tag. Offered locales are written like 'en' or 'de-CH'."
                    );
                }
                return locale;
            })
            .toList();
    }


    @Override
    public List<Locale> getLocales()
    {
        return locales;
    }


    @Override
    public Locale resolveLocale(String acceptLanguage)
    {
        if (acceptLanguage == null || acceptLanguage.isBlank())
        {
            return locales.getFirst();
        }

        try
        {
            final Locale match = Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), locales);
            return match != null ? match : locales.getFirst();
        }
        catch (IllegalArgumentException e)
        {
            // A browser does not send what LanguageRange cannot parse, and whoever does gets a page all the same.
            log.debug("Unreadable Accept-Language header '{}'", acceptLanguage, e);
            return locales.getFirst();
        }
    }


    @Override
    public JSONHolder getTranslations(Locale locale)
    {
        if (prod)
        {
            return kept.computeIfAbsent(locale, this::read);
        }
        return read(locale);
    }


    private JSONHolder read(Locale locale)
    {
        final String languageTag = locale.toLanguageTag();

        final TrackUsageData analysis = staticAnalysisProvider.getTrackUsageData();

        final Map<String, String> stored = shipped(analysis, locale);
        dslContext.select(TAG, VALUE)
            .from(TABLE)
            .where(LOCALE.eq(languageTag))
            .forEach(r -> stored.put(r.get(TAG), r.get(VALUE)));

        final Map<String, String> translations = translations(tags(analysis, domain, actionTags), stored);

        translations.forEach((tag, translation) -> {
            final Set<String> expected = placeholders(tag);
            final Set<String> found = placeholders(translation);
            if (!expected.equals(found) && reported.add(languageTag + "\0" + tag + "\0" + translation))
            {
                log.warn(
                    "The {} translation of '{}' has the placeholders {} where the tag has {}: '{}'",
                    languageTag, tag, found, expected, translation
                );
            }
        });

        return new JSONHolder(translations);
    }


    /// Every tag there is: the static first argument of every recorded i18n() call, every type the domain has a
    /// table for, by itself and with each of its fields, each value of an enum such a field holds, and the tags of
    /// the actions the frontend uses: those it names in a `useActionDraft()` or `issueAction()` call, or all of them
    /// where there is no analysis yet.
    ///
    /// @param analysis     the frontend's static analysis, or `null` where there is none yet
    /// @param actionTags   the tags the actions bring, see `DefaultActionExecutor.getActionTags()`
    static Set<String> tags(TrackUsageData analysis, QLiveDomain domain, Map<String, Set<String>> actionTags)
    {
        final Set<String> tags = new TreeSet<>();

        if (analysis == null)
        {
            actionTags.values().forEach(tags::addAll);
        }
        else
        {
            for (String action : usedActions(analysis))
            {
                tags.addAll(actionTags.getOrDefault(action, Set.of()));
            }

            for (ModuleFunctionReferences refs : analysis.getModuleFunctionReferences().values())
            {
                for (List<?> args : refs.getCalls(I18N_CALL))
                {
                    if (!args.isEmpty() && args.getFirst() instanceof String tag)
                    {
                        tags.add(tag);
                    }
                }
            }
        }

        for (String typeName : domain.getTypeRegistry().getJooqTables().keySet())
        {
            tags.add(typeName);
            if (domain.getGraphQLSchema().getType(typeName) instanceof GraphQLObjectType type)
            {
                for (GraphQLFieldDefinition field : type.getFieldDefinitions())
                {
                    tags.add(typeName + "." + field.getName());

                    if (GraphQLTypeUtil.unwrapAll(field.getType()) instanceof GraphQLEnumType enumType)
                    {
                        for (GraphQLEnumValueDefinition value : enumType.getValues())
                        {
                            tags.add(enumType.getName() + "." + value.getName());
                        }
                    }
                }
            }
        }

        return tags;
    }


    /// The translations the analysis' modules bring into the given locale: those filed under its language, `en`
    /// for `en-GB`, and over them those filed under the locale itself.
    ///
    /// @param analysis     the frontend's static analysis, or `null` where there is none yet
    static Map<String, String> shipped(TrackUsageData analysis, Locale locale)
    {
        final Map<String, String> shipped = new HashMap<>();
        if (analysis != null)
        {
            for (ModuleFunctionReferences refs : analysis.getModuleFunctionReferences().values())
            {
                shipped.putAll(refs.getTranslations(locale.getLanguage()));
            }
            if (!locale.toLanguageTag().equals(locale.getLanguage()))
            {
                for (ModuleFunctionReferences refs : analysis.getModuleFunctionReferences().values())
                {
                    shipped.putAll(refs.getTranslations(locale.toLanguageTag()));
                }
            }
        }
        return shipped;
    }


    /// The names of the actions the frontend uses, by the calls naming one: `useActionDraft()` and `issueAction()`.
    ///
    /// @param analysis     the frontend's static analysis
    static Set<String> usedActions(TrackUsageData analysis)
    {
        final Set<String> actions = new TreeSet<>();
        for (ModuleFunctionReferences refs : analysis.getModuleFunctionReferences().values())
        {
            for (String call : List.of(
                ModuleFunctionReferences.USE_ACTION_DRAFT_CALL_NAME,
                ModuleFunctionReferences.ISSUE_ACTION_CALL_NAME
            ))
            {
                for (List<?> args : refs.getCalls(call))
                {
                    if (!args.isEmpty() && args.getFirst() instanceof String action)
                    {
                        actions.add(action);
                    }
                }
            }
        }
        return actions;
    }


    /// The stored translations of the given tags, sorted. A stored translation of a tag no code uses any more
    /// is left out.
    static Map<String, String> translations(Set<String> tags, Map<String, String> stored)
    {
        final Map<String, String> translations = new TreeMap<>();
        for (String tag : tags)
        {
            final String translation = stored.get(tag);
            if (translation != null)
            {
                translations.put(tag, translation);
            }
        }
        return translations;
    }


    /// The placeholders in the given text, `{0}`, `{1}`..., as their numbers.
    static Set<String> placeholders(String text)
    {
        final Set<String> found = new TreeSet<>();
        final Matcher m = PLACEHOLDER.matcher(text);
        while (m.find())
        {
            found.add(m.group(1));
        }
        return found;
    }
}
