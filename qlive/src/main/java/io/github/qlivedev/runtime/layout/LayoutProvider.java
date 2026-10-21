package io.github.qlivedev.runtime.layout;

import io.github.qlivedev.model.layout.Layouts;

import java.util.Locale;

/// A source of the named layouts every page gets with its bootstrap, the forms `<Form layout="name"/>` renders.
///
/// Every provider bean contributes, in their order (`@Order`), to one namespace: a layout a later provider gives
/// under a name an earlier one used replaces it. The frontend's init hook may change or replace any of them again.
/// Where the layouts come from -- files ({@link Layouts#parse(String)}), a table, an editor, a generator over the
/// domain -- is the provider's business. An application without layouts declares none.
///
/// The bootstrap service asks the providers once per locale, checks the result against the domain ({@link
/// LayoutCheck}) and keeps it as the JSON it sends, until its `flushCache()`: a provider whose layouts change calls
/// that, and the next page gets them. The layouts of every offered locale are asked for at startup, so a wrong
/// reference fails startup rather than a page.
///
/// Every layout goes to every page rather than those a view asks for: which layouts a page refers to can't be
/// told from its code as its injections can, and an application changing a form while it is open needs any of
/// them at hand. A page that gets no schema gets no layouts, having no form to render them in.
public interface LayoutProvider
{
    /// The layouts for pages served in the given locale. Section names are text as the page shows it, so a layout
    /// with sections is translated into the locale here; fields are labeled by the frontend.
    ///
    /// @param locale   the locale the page is served in, one of `TranslationService.getLocales()`
    Layouts getLayouts(Locale locale);
}
