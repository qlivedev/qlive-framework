/**
 * Minimal structural parser for the GraphQL documents qlive works with.
 *
 * It is deliberately not a GraphQL parser: we only need the operation kind, the
 * operation name and the top-level selections with both sides of an alias. Every
 * other piece of syntax - arguments, variable definitions, directives, fragments,
 * nested selections - is skipped over in a balanced way so that documents using
 * those features still parse instead of failing the framework user.
 */

export type OperationType = "query" | "mutation" | "subscription"

export interface QuerySelection
{
    /** alias of the selection, null if the field was selected under its own name */
    alias: string | null
    /** field name as declared in the schema, e.g. "queryFooDocument" */
    name: string
    /** key the field's value appears under in the result: alias if given, else name */
    key: string
}

export interface ParsedQuery
{
    /** operation kind, "query" for the anonymous `{ ... }` shorthand */
    operation: OperationType
    /** operation name, e.g. "Q_Foo", null for an anonymous operation */
    name: string | null
    /** top-level selections of the operation */
    selections: QuerySelection[]
}

const NAME_START = /[_A-Za-z]/
const NAME_CHAR = /[_0-9A-Za-z]/

const OPERATIONS: { [name: string]: OperationType } = {
    query: "query",
    mutation: "mutation",
    subscription: "subscription"
}

/**
 * Cursor over a GraphQL document. All skip* methods leave the cursor on the
 * first character after the construct they consumed.
 */
class Scanner
{
    private readonly src: string;
    private pos: number;

    constructor(src: string)
    {
        this.src = src;
        this.pos = 0;
    }

    atEnd(): boolean
    {
        return this.pos >= this.src.length
    }

    peek(): string
    {
        return this.src.charAt(this.pos)
    }

    /** consumes one character, used to step over anything we do not understand */
    advance(): void
    {
        this.pos++
    }

    /** skips whitespace, commas and `#` comments, which GraphQL all treats as ignored tokens */
    skipIgnored(): void
    {
        while (!this.atEnd())
        {
            const c = this.peek();
            if (c === "#")
            {
                while (!this.atEnd() && this.peek() !== "\n")
                {
                    this.pos++
                }
            }
            else if (c === "," || c === "\uFEFF" || /\s/.test(c))
            {
                this.pos++
            }
            else
            {
                return
            }
        }
    }

    /** reads a GraphQL name at the cursor, null if there is none */
    readName(): string | null
    {
        if (this.atEnd() || !NAME_START.test(this.peek()))
        {
            return null
        }

        const start = this.pos;
        while (!this.atEnd() && NAME_CHAR.test(this.peek()))
        {
            this.pos++
        }
        return this.src.slice(start, this.pos)
    }

    /** skips a string value, both the `"..."` and the `"""..."""` form */
    skipString(): void
    {
        if (this.src.startsWith('"""', this.pos))
        {
            const end = this.src.indexOf('"""', this.pos + 3);
            this.pos = end < 0 ? this.src.length : end + 3
            return
        }

        // single quoted: an escaped quote does not end the string
        this.pos++
        while (!this.atEnd())
        {
            const c = this.peek();
            this.pos++
            if (c === "\\")
            {
                this.pos++
            }
            else if (c === '"' || c === "\n")
            {
                return
            }
        }
    }

    /**
     * Skips a balanced block starting at the cursor, e.g. an argument list or a
     * nested selection set. Braces and parens inside strings are ignored.
     */
    skipBalanced(open: string, close: string): void
    {
        let depth = 0;
        while (!this.atEnd())
        {
            const c = this.peek();
            if (c === '"')
            {
                this.skipString()
                continue
            }
            if (c === "#")
            {
                this.skipIgnored()
                continue
            }

            this.pos++
            if (c === open)
            {
                depth++
            }
            else if (c === close)
            {
                depth--
                if (depth === 0)
                {
                    return
                }
            }
        }
    }

    /** skips any number of directives, including their arguments */
    skipDirectives(): void
    {
        this.skipIgnored()
        while (this.peek() === "@")
        {
            this.pos++
            this.readName()
            this.skipIgnored()
            if (this.peek() === "(")
            {
                this.skipBalanced("(", ")")
            }
            this.skipIgnored()
        }
    }
}

/**
 * Extracts the structural information qlive needs from a GraphQL document: the
 * kind and name of the first operation and its top-level selections.
 *
 * Fragment definitions preceding the operation are skipped, so a document can
 * carry both.
 *
 * @param query     GraphQL query or mutation document
 *
 * @returns operation kind, name and top-level selections
 * @throws if the document contains no operation
 */
export function parseQuery(query: string): ParsedQuery
{
    const scanner = new Scanner(query);

    while (true)
    {
        scanner.skipIgnored()
        if (scanner.atEnd())
        {
            throw new Error("Could not find a query or mutation in: " + query)
        }

        if (scanner.peek() === "{")
        {
            // anonymous shorthand: `{ ... }` is a query without name
            return {
                operation: "query",
                name: null,
                selections: parseSelectionSet(scanner)
            }
        }

        const keyword = scanner.readName();
        if (keyword === null)
        {
            // not a name and not a selection set: step over it and keep looking
            scanner.advance()
            continue
        }

        const operation = OPERATIONS[keyword];
        if (operation)
        {
            return parseOperation(scanner, operation)
        }

        // fragment definition or anything else we do not model: skip its body
        scanner.skipIgnored()
        while (!scanner.atEnd() && scanner.peek() !== "{")
        {
            if (scanner.peek() === "(")
            {
                scanner.skipBalanced("(", ")")
            }
            else
            {
                scanner.advance()
            }
        }
        if (!scanner.atEnd())
        {
            scanner.skipBalanced("{", "}")
        }
    }
}

function parseOperation(scanner: Scanner, operation: OperationType): ParsedQuery
{
    scanner.skipIgnored()
    const name = scanner.readName();

    scanner.skipIgnored()
    if (scanner.peek() === "(")
    {
        // variable definitions
        scanner.skipBalanced("(", ")")
    }
    scanner.skipDirectives()

    scanner.skipIgnored()
    if (scanner.peek() !== "{")
    {
        throw new Error("Expected selection set of " + operation + " " + (name || "") )
    }

    return {
        operation,
        name,
        selections: parseSelectionSet(scanner)
    }
}

/**
 * Parses the selection set at the cursor, collecting its own selections. Nested
 * selection sets are skipped - the structure below the top level is described by
 * the result type, not by us.
 */
function parseSelectionSet(scanner: Scanner): QuerySelection[]
{
    const selections: QuerySelection[] = [];

    // consume the opening brace
    scanner.advance()

    while (true)
    {
        scanner.skipIgnored()
        if (scanner.atEnd())
        {
            throw new Error("Unterminated selection set")
        }

        const c = scanner.peek();
        if (c === "}")
        {
            scanner.advance()
            return selections
        }

        if (c === ".")
        {
            // fragment spread or inline fragment: contributes no field of its own
            while (scanner.peek() === ".")
            {
                scanner.advance()
            }
            scanner.skipIgnored()
            // "on Type" of an inline fragment, or the name of a fragment spread
            scanner.readName()
            scanner.skipIgnored()
            scanner.readName()
            scanner.skipDirectives()
            if (scanner.peek() === "{")
            {
                scanner.skipBalanced("{", "}")
            }
            continue
        }

        let name = scanner.readName();
        if (name === null)
        {
            // unexpected character, step over it rather than failing the document
            scanner.advance()
            continue
        }

        let alias: string | null = null;
        scanner.skipIgnored()
        if (scanner.peek() === ":")
        {
            scanner.advance()
            scanner.skipIgnored()
            const aliased = scanner.readName();
            if (aliased !== null)
            {
                alias = name
                name = aliased
            }
        }

        scanner.skipIgnored()
        if (scanner.peek() === "(")
        {
            scanner.skipBalanced("(", ")")
        }
        scanner.skipDirectives()

        scanner.skipIgnored()
        if (scanner.peek() === "{")
        {
            scanner.skipBalanced("{", "}")
        }

        selections.push({
            alias,
            name,
            key: alias || name
        })
    }
}
