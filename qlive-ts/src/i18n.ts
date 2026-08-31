export default function i18n(tag : string, ... args: string[])
{
    let joinedArgs = ""

    if (args && args.length)
    {
        joinedArgs = ":" + args.join(", ")
    }

    return "[" + tag + joinedArgs + "]"
}
