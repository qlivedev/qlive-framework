import {currentConfig} from "./config";

/**
 * Translates the given tag, filling in the given arguments.
 *
 * The tag is the static text, the arguments what varies: `i18n("Filter {0}", label)`. The translation comes from
 * `config().translations` and has the same placeholders, `{0}` for the first argument, `{1}` for the second and so
 * on, as in Java's MessageFormat. A tag without a translation renders as itself in brackets, its arguments appended:
 * `[Filter {0}:Name]`, so a missing one shows.
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

    let joinedArgs = ""

    if (args && args.length)
    {
        joinedArgs = ":" + args.join(", ")
    }

    return "[" + tag + joinedArgs + "]"
}
