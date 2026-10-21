import {currentConfig} from "./config";

/**
 * Translates the given tag, filling in the given arguments.
 *
 * The tag is the static text, the arguments what varies: `i18n("Filter {0}", label)`. The translation comes from
 * `config().translations`, which the server fills for the locale the page is served in, and has the same
 * placeholders, `{0}` for the first argument, `{1}` for the second and so on, as in Java's MessageFormat. A tag
 * without a translation renders as itself in brackets, its arguments appended: `[Filter {0}: Name]`, so a missing
 * translation shows, and a tag with placeholders doesn't read well with the arguments filled in.
 *
 * The analysis records the tag of every call, which is what the server offers translations of. It records only a
 * tag that is static: a string literal, not a variable holding one.
 *
 * @param tag       translation tag
 * @param args      non-static arguments
 */
export default function i18n(tag : string, ... args: string[])
{
    const translation = currentConfig()?.translations?.[tag]
    if (translation != null)
    {
        return translation.replace(
            /\{(\d+)\}/g,
            (placeholder, index: string) => Number(index) < args.length ? args[Number(index)] : placeholder
        )
    }

    return "[" + ( args.length ?
            tag + ": " + args.join(", ") :
            tag
    ) + "]"
}
