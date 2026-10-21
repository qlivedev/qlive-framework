import type {QLiveConfig} from "@qlivedev/qlive-ts";
import QLIVE_ENGLISH from "../../../qlive-ts/src/vite/i18n-en.json";

/*
 * The English the demos show. QLive's own comes with qlive-ts, as it does for the server; the tags of qlive-test's
 * views read as their own English, and a field's tag, "Foo.name", becomes the field's name. A tag missing here shows
 * in brackets in the demo: add it.
 */
const TAGS = [
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
 * The translations for the given config: every tag above as itself, QLive's as their English, every field of every
 * object type as its name.
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
    Object.assign(translations, QLIVE_ENGLISH);
    return translations;
}
