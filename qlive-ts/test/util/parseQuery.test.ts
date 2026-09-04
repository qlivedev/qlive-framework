import {describe, expect, test} from "vitest";
import {parseQuery} from "../../src/util/parseQuery";

/**
 * The parser sees what building a conversion map needs: operation, name, variable
 * definitions and the selections with both sides of an alias, nested all the way
 * down. Arguments and directives only have to be skipped without tripping over
 * their syntax.
 */
describe("parseQuery", () => {

    /** a selection without a selection set of its own */
    const leaf = (name: string, alias: string | null = null) =>
        ({alias, name, key: alias ?? name, selections: []});

    const Q_FOO = `query Q_Foo($config: QueryConfig!) {
        xxx: queryFooDocument(config: $config) {
            type
            config
            rows {
                id
                name
                desc: description

                owner {
                    id
                    login
                }
                fooType {
                    id: name
                    ordinal
                }
            }
        }
    }`;

    test("extracts operation, name, variables and the selection tree", () => {
        expect(parseQuery(Q_FOO)).toEqual({
            operation: "query",
            name: "Q_Foo",
            variables: {config: "QueryConfig!"},
            usesFragments: false,
            selections: [
                {
                    alias: "xxx", name: "queryFooDocument", key: "xxx", selections: [
                        leaf("type"),
                        leaf("config"),
                        {
                            alias: null, name: "rows", key: "rows", selections: [
                                leaf("id"),
                                leaf("name"),
                                leaf("description", "desc"),
                                {
                                    alias: null, name: "owner", key: "owner", selections: [
                                        leaf("id"),
                                        leaf("login")
                                    ]
                                },
                                {
                                    alias: null, name: "fooType", key: "fooType", selections: [
                                        leaf("name", "id"),
                                        leaf("ordinal")
                                    ]
                                }
                            ]
                        }
                    ]
                }
            ]
        });
    });

    test("reads variable definitions with defaults and lists", () => {
        const parsed = parseQuery(`query Q_Vars(
            $config: QueryConfig! = { pageSize: 10 }
            $ids: [String!]
            $since: Timestamp = "2026-01-02T03:04:05Z"
        ) { foo }`);

        expect(parsed.variables).toEqual({
            config: "QueryConfig!",
            ids: "[String!]",
            since: "Timestamp"
        });
    });

    test("handles multiple selections, aliased and not", () => {
        const parsed = parseQuery(`mutation M_Update($foo: FooInput!) {
            a: updateFoo(foo: $foo) { id }
            deleteFoo(id: "123")
            b: countFoos
        }`);

        expect(parsed.operation).toBe("mutation");
        expect(parsed.name).toBe("M_Update");
        expect(parsed.variables).toEqual({foo: "FooInput!"});
        expect(parsed.selections).toEqual([
            {alias: "a", name: "updateFoo", key: "a", selections: [leaf("id")]},
            leaf("deleteFoo"),
            leaf("countFoos", "b")
        ]);
    });

    test("accepts the anonymous shorthand", () => {
        expect(parseQuery(`{ currentUser { id } }`)).toEqual({
            operation: "query",
            name: null,
            variables: {},
            usesFragments: false,
            selections: [
                {alias: null, name: "currentUser", key: "currentUser", selections: [leaf("id")]}
            ]
        });
    });

    test("ignores comments, commas and directives", () => {
        const parsed = parseQuery(`
            # leading comment with a brace { and a quote "
            query Q_Commented @cached {
                x: foo(name: "a } b", other: "\\"") @include(if: true) { id },
                # commented selection
                bar
            }`);

        expect(parsed.name).toBe("Q_Commented");
        expect(parsed.selections).toEqual([
            {alias: "x", name: "foo", key: "x", selections: [leaf("id")]},
            leaf("bar")
        ]);
    });

    test("skips fragment definitions and spreads, but says so", () => {
        const parsed = parseQuery(`
            fragment FooFields on Foo {
                id
                name
            }
            query Q_WithFragment {
                ...FooFields
                ... on Bar { id }
                foo { ...FooFields }
            }`);

        expect(parsed.name).toBe("Q_WithFragment");
        expect(parsed.usesFragments).toBe(true);
        expect(parsed.selections).toEqual([
            {alias: null, name: "foo", key: "foo", selections: []}
        ]);
    });

    test("survives block strings and multi-line arguments", () => {
        const parsed = parseQuery(`query Q_Block {
            foo(
                text: """
                  a } b { c
                """
            ) {
                id
            }
        }`);

        expect(parsed.selections).toEqual([
            {alias: null, name: "foo", key: "foo", selections: [leaf("id")]}
        ]);
    });

    test("subscriptions are recognized, too", () => {
        expect(parseQuery("subscription S_Foo { fooChanged { id } }")).toMatchObject({
            operation: "subscription",
            name: "S_Foo"
        });
    });

    test("complains about a document without operation", () => {
        expect(() => parseQuery("fragment FooFields on Foo { id }")).toThrow(/query or mutation/);
    });
});
