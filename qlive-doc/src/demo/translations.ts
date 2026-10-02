import type {QLiveConfig} from "@qlivedev/qlive-ts";

/*
 * The English the demos show. qlive-ts and qlive-test write their tags in English, so the translation of a tag is the
 * tag itself; only a field's tag, "Foo.name", becomes the field's name. A tag missing here shows in brackets in the
 * demo: add it.
 */
const TAGS = [
    // qlive-ts
    "Additional filter active",
    "All",
    "Also sorted by other fields",
    "Any",
    "Filter {0}",
    "Filter {0} from",
    "Filter {0} until",
    "First page",
    "Last page",
    "Next page",
    "No",
    "No rows",
    "Page {0}",
    "Pagination",
    "Previous page",
    "Reload",
    "Reset filters",
    "Rows changed elsewhere",
    "Rows not updated",
    "Rows not updated: {0}",
    "Rows per page",
    "Yes",
    // qlive-test's views
    "Current sorting order",
    "Flagged first, then largest num",
    "Last digit of num",
    "Owner, then last digit of num",
    "Owner, then name",
    "Sort by …",
    "ascending",
    "descending"
];

/**
 * The translations for the given config: every tag above as itself, every field of every object type as its name.
 *
 * @param config    config the demos run on
 */
export function translationsFor(config: QLiveConfig): Record<string, string>
{
    const translations: Record<string, string> = {};
    for (const type of config.schema.types)
    {
        if (type.kind === "OBJECT")
        {
            for (const field of type.fields ?? [])
            {
                translations[type.name + "." + field.name] = field.name;
            }
        }
    }
    for (const tag of TAGS)
    {
        translations[tag] = tag;
    }
    return translations;
}
