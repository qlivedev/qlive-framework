import {describe, expect, test} from "vitest";
import {parseQuery} from "../../src/util/parseQuery";

/**
 * The parser only needs to see the outline of a document - operation, name and
 * the top-level selections with both sides of an alias. Everything below that is
 * described by the TypeScript result type, so it only has to be skipped without
 * tripping over its syntax.
 */
describe("parseQuery", () => {

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

    test("extracts operation, name and aliased selection", () => {
        expect(parseQuery(Q_FOO)).toEqual({
            operation: "query",
            name: "Q_Foo",
            selections: [
                {alias: "xxx", name: "queryFooDocument", key: "xxx"}
            ]
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
        expect(parsed.selections).toEqual([
            {alias: "a", name: "updateFoo", key: "a"},
            {alias: null, name: "deleteFoo", key: "deleteFoo"},
            {alias: "b", name: "countFoos", key: "b"}
        ]);
    });

    test("accepts the anonymous shorthand", () => {
        expect(parseQuery(`{ currentUser { id } }`)).toEqual({
            operation: "query",
            name: null,
            selections: [
                {alias: null, name: "currentUser", key: "currentUser"}
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
            {alias: "x", name: "foo", key: "x"},
            {alias: null, name: "bar", key: "bar"}
        ]);
    });

    test("skips fragment definitions and spreads", () => {
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
        expect(parsed.selections).toEqual([
            {alias: null, name: "foo", key: "foo"}
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
            {alias: null, name: "foo", key: "foo"}
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
