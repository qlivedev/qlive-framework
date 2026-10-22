package io.github.qlivedev.qlive.runtime.i18n;

import io.github.qlivedev.qlive.graphql.util.JSONHolder;

import java.util.List;
import java.util.Locale;

/// The texts the frontend's `i18n()` translates its tags into, in each of the locales the application offers.
///
/// A tag is the static text of an `i18n()` call, `"Filter {0}"`, or the name of a domain type or of one of its
/// fields, `"Foo"` and `"Foo.name"`. Its translation holds the same `{0}`, `{1}`... placeholders, which the
/// frontend fills with the call's other arguments. Which tags there are is known from the frontend's static
/// analysis and the domain; what they translate to is the application's to store.
public interface TranslationService
{
    /// The locales the application offers, the first being the one a request gets that asks for none of them.
    List<Locale> getLocales();


    /// The offered locale that serves the given `Accept-Language` header best.
    ///
    /// @param acceptLanguage   the header, or `null` where the request has none
    ///
    /// @return the best match by the header's own priorities, or the first offered locale where the header names
    ///         none of them, or cannot be read
    Locale resolveLocale(String acceptLanguage);


    /// The translations into the given locale, as JSON map of tag to translation. A tag without one is left out,
    /// and the frontend renders it as itself.
    ///
    /// @param locale   one of {@link #getLocales()}
    JSONHolder getTranslations(Locale locale);
}
