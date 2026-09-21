package io.github.qlivedev.runtime.merge;

/// The two tables the merge owns, by the names the framework's schema gives them.
///
/// Constants rather than configuration, for the reason `MergeMeta.VERSION` is one: Automaton let these be
/// renamed and nobody ever renamed them. They are reached through plain JOOQ names rather than through
/// generated classes, because the classes are generated into the application and the framework cannot see
/// them.
public final class MergeTables
{
    private MergeTables()
    {
        // no instances
    }

    /// One row per recorded change to a versioned row: which fields it touched, under which field layout,
    /// and the version it was made against.
    public final static String APP_VERSION = "app_version";

    /// One row per field layout any stored mask was written against.
    public final static String APP_FIELD_LAYOUT = "app_field_layout";
}
