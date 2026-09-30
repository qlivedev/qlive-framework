/**
 * Internationalization helper function
 *
 * @param tag       translation tag
 * @param args      non-static arguments 
 */
export default function i18n(tag : string, ... args: string[])
{
    let joinedArgs = ""

    if (args && args.length)
    {
        joinedArgs = ":" + args.join(", ")
    }

    return "[" + tag + joinedArgs + "]"
}
