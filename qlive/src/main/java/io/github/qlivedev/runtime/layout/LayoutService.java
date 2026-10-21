package io.github.qlivedev.runtime.layout;

import io.github.qlivedev.graphql.util.JSONHolder;

import java.util.Locale;

/// The named layouts every page gets with its bootstrap, the forms `<Form layout="name"/>` renders.
///
/// The JSON is a map of layout name to layout, in the frontend's format (`Layout` in qlive-ts): a domain type and
/// nodes, a node being a field, a section of nodes or a component, a widget or component given by the name it is
/// registered under. The name is the map's key; a layout's own `name` is ignored.
///
/// Every layout goes to every page rather than those a view asks for: which layouts a page refers to can't be
/// told from its code as its injections can, and an application changing a form while it is open needs any of
/// them at hand. A page that gets no schema gets no layouts, having no form to render them in.
///
/// Layouts share one namespace with those the application adds in startup()'s `init`, which may also change or
/// replace one of these. Where they come from -- files, a table, an editor, a generator over the domain -- and
/// what is cached is the implementation's to decide. An application without one declares no bean, and its pages
/// get no layouts.
public interface LayoutService
{
    /// What the bootstrap carries where the application declares no LayoutService.
    LayoutService NONE = new LayoutService()
    {
        private final JSONHolder empty = new JSONHolder("{}");

        @Override
        public JSONHolder getLayouts(Locale locale)
        {
            return empty;
        }
    };


    /// The layouts for a page served in the given locale, as a JSON map of name to layout. Section names are text
    /// as the page shows it, so a layout with sections is translated into the locale here; fields are labeled by
    /// the frontend.
    ///
    /// Called for every page; an implementation that builds its JSON once per locale keeps it.
    ///
    /// @param locale   the locale the page is served in, one of `TranslationService.getLocales()`
    JSONHolder getLayouts(Locale locale);
}
