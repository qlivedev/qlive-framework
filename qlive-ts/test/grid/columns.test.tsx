import {beforeAll, describe, expect, test} from "vitest";
import {Temporal} from "temporal-polyfill";
import {field, value} from "../../src/FilterDSL";
import {FieldPath, resolveColumn, rowKey} from "../../src/grid/columns";
import {operatorFilter} from "../../src/grid/filters";
import {initGridConfig} from "../fixtures/gridConfig";

beforeAll(initGridConfig);

const created = Temporal.Instant.from("2026-09-04T10:15:30Z");

const row = {
    id: "foo-1",
    name: "Foo #1",
    num: 12,
    ratio: null,
    flag: true,
    created,
    owner: {id: "user-1", login: "admin"},
    fooType: {name: "TypeA", ordinal: 1}
};

describe("resolveColumn", () => {

    test("derives a String column from its path", () => {
        const column = resolveColumn("Foo", "name");

        expect(column.field).toBe("name");
        expect(column.heading).toBe("[Foo.name]");
        expect(column.render(row)).toBe("Foo #1");
        expect(column.sort).toBe("name");
        expect(column.filter!.field).toBe("name");
        expect(column.filter!.filter.toCondition(field("name"), ["oo"])).toMatchObject({name: "containsIgnoreCase"});
        expect(column.nowrap).toBe(false);
        expect(column.className(row)).toBeUndefined();
    });

    test("titles the header only as the column says", () => {
        expect(resolveColumn("Foo", "name").title).toBeUndefined();
        expect(resolveColumn("Foo", {field: "name", title: "the name"}).title).toBe("the name");
        expect(resolveColumn("Foo", {field: "owner", title: column => "by " + String(column.sort)}).title)
            .toBe("by owner.login");
        expect(resolveColumn("Foo", {heading: "Actions", render: () => null, title: c => String(c.heading)}).title)
            .toBe("Actions");
    });

    test("filters numbers by eq, of their type", () => {
        const num = resolveColumn("Foo", "num").filter!;
        expect(num.filter.toCondition(field("num"), ["12"])).toMatchObject({
            name: "eq",
            operands: [{type: "Field", name: "num"}, {type: "Value", scalarType: "Int", value: 12}]
        });
        const ratio = resolveColumn("Foo", "ratio").filter!;
        expect(ratio.filter.toCondition(field("ratio"), ["0.5"])).toMatchObject({
            operands: [{}, {scalarType: "Float", value: 0.5}]
        });
    });

    test("filters Booleans with a select", () => {
        const flag = resolveColumn("Foo", "flag");
        expect(flag.filter!.filter.Input).toBeDefined();
        expect(flag.filter!.filter.toCondition(field("flag"), ["true"])).toMatchObject({
            name: "eq",
            operands: [{}, {scalarType: "Boolean", value: true}]
        });
        expect(flag.render(row)).toBe("true");
    });

    test("formats values through their converters, and filters dates and times by a date range", () => {
        const column = resolveColumn("Foo", "created");
        expect(column.render(row)).toBe(created.toLocaleString());
        expect(column.sort).toBe("created");
        expect(column.filter!.field).toBe("created");
        expect(column.filter!.filter.arity).toBe(2);
        expect(column.filter!.filter.partial).toBe(true);
        expect(resolveColumn("Foo", "day").filter!.filter.toCondition(field("day"), ["2026-09-01", null]))
            .toEqual(field("day").ge(value("2026-09-01", "Date")));
    });

    test("shows null as nothing", () => {
        expect(resolveColumn("Foo", "ratio").render(row)).toBe("");
    });

    test("labels a path into a relation by the type owning the field", () => {
        const column = resolveColumn("Foo", "owner.login");
        expect(column.heading).toBe("[AppUser.login]");
        expect(column.render(row)).toBe("admin");
        expect(column.sort).toBe("owner.login");
        expect(column.render({...row, owner: null})).toBe("");
    });

    test("shows, sorts and filters a relation by its name fields", () => {
        const column = resolveColumn("Foo", "owner");

        expect(column.heading).toBe("[Foo.owner]");
        expect(column.render(row)).toBe("admin");
        expect(column.render({...row, owner: null})).toBe("");
        expect(column.sort).toBe("owner.login");
        expect(column.filter!.field).toBe("owner.login");
    });

    test("hands a relation column's own filter the first name field, or the foreign key to one picking the row", () => {
        const filter = {arity: 1, toCondition: () => null};
        expect(resolveColumn("Foo", {field: "owner", filter}).filter).toEqual({field: "owner.login", filter});

        const picking = {...filter, key: true};
        expect(resolveColumn("Foo", {field: "owner", filter: picking}).filter).toEqual({field: "ownerId", filter: picking});
        expect(resolveColumn("Foo", {field: "fooType", filter: picking, render: () => "", sort: false}).filter!.field)
            .toBe("fooTypeId");
        expect(resolveColumn("Foo", {field: "name", filter: picking}).filter!.field).toBe("name");
    });

    test("names a name field the query doesn't select", () => {
        const column = resolveColumn("Foo", "owner");
        expect(() => column.render({...row, owner: {id: "user-1"}}))
            .toThrow("Column \"owner\": the rows have no \"owner.login\". Select it in the grid's query.");
    });

    test("names a field the query doesn't select", () => {
        const column = resolveColumn("Foo", "num");
        expect(() => column.render({id: "foo-1"})).toThrow("the rows have no \"num\"");
    });

    test("needs name fields for a relation column deriving anything", () => {
        expect(() => resolveColumn("Foo", "fooType"))
            .toThrow("Column \"fooType\": FooType has no name fields to show, sort and filter the relation by.");

        const column = resolveColumn("Foo", {
            field: "fooType",
            render: (r: typeof row) => r.fooType.name,
            sort: "fooType.ordinal",
            filter: false
        });
        expect(column.render(row)).toBe("TypeA");
        expect(column.sort).toBe("fooType.ordinal");
    });

    test("refuses paths the schema doesn't have or a column can't show", () => {
        expect(() => resolveColumn("Foo", "nope")).toThrow("Column \"nope\": Foo has no field \"nope\".");
        expect(() => resolveColumn("Foo", "tags")).toThrow("tags of Foo is a list");
        expect(() => resolveColumn("Foo", "name.x")).toThrow("name of Foo is a String, with no fields below it.");
        expect(() => resolveColumn("Foo", "embedded")).toThrow("neither a scalar nor a to-one relation");
    });

    test("takes what the object form states over what is derived", () => {
        const column = resolveColumn("Foo", {
            field: "name",
            heading: "Title",
            sort: false,
            filter: false,
            className: r => r.flag ? "flagged" : undefined,
            nowrap: true,
            maxWidth: "20em"
        });

        expect(column.heading).toBe("Title");
        expect(column.sort).toBeNull();
        expect(column.filter).toBeNull();
        expect(column.className(row)).toBe("flagged");
        expect(column.nowrap).toBe(true);
        expect(column.maxWidth).toBe("20em");

        expect(resolveColumn("Foo", {field: "num", sort: {type: "Field", name: "ratio"}}).sort)
            .toEqual({type: "Field", name: "ratio"});
    });

    test("formats what render returns unless it is an element", () => {
        const plain = resolveColumn("Foo", {field: "created", render: () => created});
        expect(plain.render(row)).toBe(created.toLocaleString());

        const element = <b>bold</b>;
        expect(resolveColumn("Foo", {field: "name", render: () => element}).render(row)).toBe(element);
    });

    test("takes a column without a field if it renders", () => {
        const column = resolveColumn("Foo", {heading: "Actions", render: () => <button/>});
        expect(column.field).toBeNull();
        expect(column.sort).toBeNull();
        expect(column.filter).toBeNull();

        expect(() => resolveColumn("Foo", {heading: "Nothing"})).toThrow("needs a render function");
        expect(() => resolveColumn("Foo", {render: () => "", filter: {arity: 1, toCondition: () => null}}))
            .toThrow("filters by what it sorts by");
    });

    test("filters a computed column by its sort key", () => {
        const sum = field("num").add(field("num"));
        const filter = operatorFilter("between", "Int");
        const column = resolveColumn("Foo", {heading: "Twice", render: () => "", sort: sum, filter});
        expect(column.filter).toEqual({field: sum, filter, label: "Twice"});

        expect(resolveColumn("Foo", {heading: <b/>, render: () => "", sort: "num", filter}).filter)
            .toEqual({field: "num", filter, label: undefined});
        expect(resolveColumn("Foo", {render: () => "", sort: sum}).filter).toBeNull();
        expect(() => resolveColumn("Foo", {render: () => "", sort: sum, filter: {...filter, key: true}}))
            .toThrow("no related row");
    });

    test("names the field of the row whose status the cells show", () => {
        expect(resolveColumn("Foo", "name").statusField).toBe("name");
        expect(resolveColumn("Foo", "owner").statusField).toBe("ownerId");
        expect(resolveColumn("Foo", "owner.login").statusField).toBeNull();
        expect(resolveColumn("Foo", {render: () => ""}).statusField).toBeNull();
    });
});

describe("rowKey", () => {

    test("reads the primary key, with its fields joined", () => {
        expect(rowKey("Foo", row)).toBe("foo-1");
        expect(rowKey("FooLink", {fooId: "a", tagId: "b"})).toBe("a,b");
    });

    test("falls back to the id", () => {
        expect(rowKey("AppUser", {id: "user-1"})).toBe("user-1");
    });

    test("names a key field the query doesn't select", () => {
        expect(() => rowKey("FooLink", {fooId: "a"}))
            .toThrow("The rows of FooLink have no \"tagId\", which is part of its key.");
    });
});

describe("FieldPath", () => {

    type Row = {
        id: string
        name: string
        created: Temporal.Instant
        owner: { id: string, login: string } | null
        tags: { id: string }[]
    };

    test("is the fields and the to-one paths, without lists", () => {
        const paths: FieldPath<Row>[] = ["id", "name", "created", "owner", "owner.id", "owner.login"];
        expect(paths).toHaveLength(6);

        // @ts-expect-error: not selected
        const typo: FieldPath<Row> = "nmae";
        // @ts-expect-error: a list is no column
        const list: FieldPath<Row> = "tags";
        // @ts-expect-error: a Temporal.Instant is a value, not a relation
        const inside: FieldPath<Row> = "created.epochMilliseconds";
        expect([typo, list, inside]).toHaveLength(3);
    });
});
