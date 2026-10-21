import type {QLiveConfig} from "@qlivedev/qlive-ts";

/*
 * The English the demos show. Most tags read as their own English and are listed as they are; a tag that is short for
 * a longer message is in MESSAGES, and a field's tag, "Foo.name", becomes the field's name. A tag missing here shows
 * in brackets in the demo: add it.
 */
const TAGS = [
    // qlive-ts
    "All",
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

const MESSAGES: Record<string, string> = {
    // qlive-ts
    "Extra filter": "Additional filter active",
    "Sorted further": "Also sorted by other fields",
    "Stale rows": "Rows changed elsewhere"
};

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
    Object.assign(translations, MESSAGES);
    return translations;
}
