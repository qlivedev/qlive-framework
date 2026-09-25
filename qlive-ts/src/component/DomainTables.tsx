import React, {useLayoutEffect, useState} from "react"
import { isListType, isNonNull, unwrapAll } from "../type-utils"
import config from "../config"
import type { DomainMeta } from "../config"
import type {
    GraphQLField,
    GraphQLObjectType,
    GraphQLSchema,
    GraphQLTypeRef
} from "../GraphQLSchema"


function domainFieldId(domainType: GraphQLObjectType, field : GraphQLField ) : string
{
    return "domain-field-" + domainType.name + "-" + field.name
}

function renderType(fieldType : GraphQLTypeRef)
{
    const isList = isListType(fieldType);

    let s = ""
    if (isList)
    {
        s += "["
    }

    s+= unwrapAll(fieldType).name

    if (isNonNull(fieldType))
    {
        s += "!"
    }
    if (isList)
    {
        s += "]"
    }
    return s;
}


function findOutputObjectType(schema: GraphQLSchema, targetType: string) : GraphQLObjectType
{
    const type = schema.types.find(t => t.kind === "OBJECT" && t.name === targetType);
    if (!type)
    {
        throw new Error("Could not find type '" + targetType + "'");
    }
    return type as GraphQLObjectType;
}

function findField(targetType: GraphQLObjectType, fieldName: string) : GraphQLField
{
    const field = targetType.fields.find(f => f.name === fieldName);
    if (!field)
    {
        throw new Error("Could not find field '" + fieldName + "' in " + targetType.name);
    }
    return field;
}

function handleJump(targetId: string, setFilter: (filter: string) => void, sourceType: string, targetType: string):boolean
{
    const element = document.getElementById(targetId);
    if (!element)
    {
        setFilter(`^(${sourceType}|${targetType})$`)

        setTimeout(
            () => {
                location.href = "#" + targetId;
            },
            200
        )

        return false
    }

    return true
}

function findRelations(schema : GraphQLSchema, meta: DomainMeta, type : GraphQLObjectType, field : GraphQLField, setFilter : (filter: string) => void)
{
    const outgoing = []
    for (let i = 0; i < meta.relations.length; i++)
    {
        const relation = meta.relations[i];

        if (relation.sourceType === type.name && relation.leftSideObjectName === field.name)
        {
            const targetType : GraphQLObjectType = findOutputObjectType(schema, relation.targetType);
            const targetField : GraphQLField = findField(targetType, relation.rightSideObjectName || relation.targetFields[0]);
            const targetId = domainFieldId(targetType, targetField);
            outgoing.push(
                <a
                    className="arrow"
                    key={outgoing.length}
                    href={ "#" + targetId}
                    data-type={ "start" }
                    data-end={ targetId }
                    data-relation={ relation.id }
                    title={ "Points to " + relation.targetType + "." + relation.rightSideObjectName }
                    onClick={ ev => {
                        if (!handleJump(targetId, setFilter, relation.sourceType, relation.targetType))
                        {
                            ev.preventDefault();
                        }
                    }}
                >
                    &gt;&gt;&gt;
                </a>
            )
        }
        if (relation.targetType === type.name && relation.rightSideObjectName === field.name)
        {
            const sourceType : GraphQLObjectType = findOutputObjectType(schema, relation.sourceType);
            const sourceField : GraphQLField = findField(sourceType, relation.leftSideObjectName || relation.sourceFields[0]);
            const targetId = domainFieldId(sourceType, sourceField);
            outgoing.push(
                <a
                    className="arrow"
                    data-type={ "end" }
                    data-start={ targetId }
                    data-relation={ relation.id }
                    key={outgoing.length}
                    href={ "#" + targetId}
                    title={ "Coming from " + relation.sourceType + "." + relation.leftSideObjectName  }
                    onClick={ ev => {
                        if (!handleJump(targetId, setFilter, relation.sourceType, relation.targetType))
                        {
                            ev.preventDefault();
                        }
                    }}
                >
                    &lt;&lt;&lt;
                </a>
            )
        }
    }

    return outgoing
}

type FilterNoticeProps = {
    filter: string
    setFilter : (filter: string) => void
}

/*
 * The way out of a filter. Nothing sets one at the moment, so this does not
 * render: the type search was a regular expression, which is not something to
 * ask a reader to type -- an unfinished "(" is invalid, and the catch in
 * {@link filterTypes} turns that into "no filter", so the list silently shows
 * everything while the reader believes they are searching.
 *
 * The filter itself is kept rather than removed, because what replaces the
 * search is a FilterDSL expression built on the client rather than a string
 * the reader has to spell. {@link handleJump} already sets a filter to reveal
 * the far side of a relation, and needs this to exist for the reader not to
 * be stranded on two types once anything can set one again.
 */
const FilterNotice = ({ filter, setFilter }: FilterNoticeProps) => {

    if (!filter)
    {
        return false
    }

    return (
        <span className="search-bar">
            Showing the types of one relation.
            {" "}
            <button
                className="btn"
                type="button"
                onClick={() => setFilter("")}
            >
                Show all
            </button>
        </span>
    )
}


type DomainTypeProps = {
    schema: GraphQLSchema
    type: GraphQLObjectType
    meta: DomainMeta
    filter: string
    setFilter : (filter: string) => void
}

const DomainType = ({ schema, type, meta, filter, setFilter } : DomainTypeProps) => {

    if (!type.fields)
    {
        return false
    }

    return (

        <div className="domain-type">
            <h1>
                { type.name }
                {
                    !!type.description && (
                        <>
                            <br/>
                            <small>
                                { type.description }
                            </small>
                        </>
                    )
                }
            </h1>
            <table>
                <thead>
                <tr>
                    <th>Name</th>
                    <th>Type</th>
                    <th>Description</th>
                    <th aria-label="Relations" title="Relations">Rel</th>
                </tr>
                </thead>
                <tbody>
                {
                    type.fields.map(
                        (field, i) => {

                            const outgoing = findRelations(schema, meta, type, field, setFilter)

                            return (
                                <tr key={i} id={domainFieldId(type, field)}>
                                    <td>{field.name}</td>
                                    <td>{renderType(field.type)}</td>
                                    <td className="small">{field.description}</td>
                                    <td>{outgoing}</td>
                                </tr>
                            );
                        })
                }
                </tbody>
            </table>
        </div>
    );
}

const objectTypeNegativeList = [
    "MutationType",
    "QueryType",
    "__Directive",
    "__EnumValue",
    "__Field",
    "__InputValue",
    "__Schema",
    "__Type"
]


function filterTypes(schema: GraphQLSchema, meta: DomainMeta, filter: string, setFilter : (filter: string) => void) : GraphQLObjectType[]
{
    // Nothing sets a filter today -- see FilterNotice. What arrives here when
    // something does is a regular expression, which is what the FilterDSL work
    // is meant to replace.
    let re: null | RegExp = null;
    try
    {
        // no "g": it makes test() stateful through lastIndex, so a run over the
        // type list skips roughly every other match
        re = filter ? new RegExp(filter, "i") : null;
    }
    catch(e)
    {

    }

    const { types } = schema

    const filtered : GraphQLObjectType[] = []
    for (let i = 0; i < types.length; i++)
    {
        const type = types[i];
        if (
            type.kind === "OBJECT" &&
            !objectTypeNegativeList.includes(type.name) &&
            (!re || re.test(type.name))
        )
        {
            filtered.push(type);
        }
    }

    return filtered

}

type DomainRelationsLayerProps = {
    under: string
    objectTypes: GraphQLObjectType[]
}

type Layout = {
    pos: Point,
    size: Dimension,
    arrows : ArrowLayout[]
}

type Dimension = [w : number,h : number]
type Point = [x : number,y : number]

type ArrowLayout = {
    start: Point,
    end: Point
}

const EMPTY : Layout = {
    pos: [0,0],
    size: [0,0],
    arrows: []
}

function getElementRect(param: HTMLElement | null) : DOMRect
{
    if (!param)
    {
        throw new Error("Element referenced by arrow cannot be found")
    }
    return param.getBoundingClientRect()
}

const DomainRelationsLayer = ({ under, objectTypes   } : DomainRelationsLayerProps) => {

    const [layout, setLayout] = useState<Layout>(EMPTY)

    // The rendered types as one value, so the arrows are measured again when
    // the set changes without depending on the array's identity.
    const typeKey = objectTypes.map(t => t.name).join("\u0000")

    /*
     * Measured through a ResizeObserver rather than only on mount.
     *
     * Every coordinate here comes from getBoundingClientRect(), so the layer
     * is wrong the moment anything reflows the tables -- a window resize, the
     * browser's zoom, a webfont arriving, the type set changing. Observing the
     * container catches all of them, because each one changes its box.
     *
     * Being inside a <details> is not one of them: closing it leaves every
     * descendant rect as it was, so a layer measured while collapsed matches
     * the open one.
     *
     * Deliberately not debounced. The observer already delivers at most once
     * per frame, the callback only reads geometry and sets state, and a
     * debounce would leave the arrows detached from the tables for the whole
     * of a drag and snap them back at the end.
     */
    useLayoutEffect(() => {

        const container = document.getElementById(under);

        if (!container)
        {
            throw new Error("No element with id '" + under + "' to measure")
        }

        const measure = () => {

            const rect = container.getBoundingClientRect();

            const relations = new Set()

            const arrows : ArrowLayout[] = Array.from(
                document.querySelectorAll("#domain-types-container a.arrow")
            )
                // make sure every relation only occurs once
                .filter((e : Element) => {
                    const relation = (e as HTMLElement).dataset.relation!;
                    if (relations.has(relation))
                    {
                        return false;
                    }
                    relations.add(relation)
                    return true
                })
                .map((e : Element) : ArrowLayout =>  {

                    const arrowElement = e as HTMLElement;
                    const type = arrowElement.dataset.type;
                    const start = arrowElement.dataset.start!;
                    const end = arrowElement.dataset.end!;

                    const startRect = getElementRect(
                        type === "start" ? arrowElement :
                            document.getElementById(start)
                    )

                    const endRect = getElementRect(
                        type === "start" ? document.getElementById(end) :
                            arrowElement
                    )

                    return ({
                        start: [
                            Math.round(startRect.x + startRect.width),
                            Math.round(startRect.y + startRect.height / 2)
                        ],
                        end: [
                            Math.round(endRect.x + endRect.width),
                            Math.round(endRect.y + endRect.height / 2)
                        ]
                    })

                })

            arrows.sort(
                (a, b) => Math.abs(a.end[1] - a.start[1]) - Math.abs(b.end[1] - b.start[1])
            )

            setLayout({
                pos: [
                    rect.x,
                    rect.y
                ],
                size: [
                    rect.width,
                    rect.height
                ],
                arrows
            })
        }

        measure()

        const observer = new ResizeObserver(measure)
        observer.observe(container)

        return () => observer.disconnect()

    }, [under, typeKey]);

    if (!layout.arrows.length)
    {
        return false;
    }

    const {pos, size, arrows} = layout

    const offset = 10
    let slidingWidth = 40
    let slidingWidthStep = 20

    return (
        <svg
            style={{
                display: "block",
                position: "absolute",
                left: 0,
                top: 0,
                width: "98vw",
                height: size[1],
                zIndex: 1,
                pointerEvents: "none"
            }}
        >
            {
                arrows.map((arrow, i)   => {

                    const { start, end } = arrow

                    slidingWidth += slidingWidthStep

                    return (
                        <path
                            key={i}
                            className="arrow-path"
                            d={
                                `M${
                                    start[0] - pos[0] + offset },${ start[1]- pos[1]
                                } C${
                                    start[0] - pos[0] + slidingWidth * 2 },${ start[1]- pos[1]
                                } ${
                                    end[0] - pos[0] + slidingWidth * 2 },${ end[1]- pos[1]
                                } ${
                                    end[0] - pos[0] + offset },${ end[1]- pos[1]
                                }`
                            }
                        />
                    );
                })
            }
        </svg>
    )
}

export type DomainTablesProps = {
    filter: string
    setFilter : (filter: string) => void
}

const DomainTables = ({filter, setFilter} : DomainTablesProps) => {

    const { schema, meta } = config();
    
    const filtered: GraphQLObjectType[] = filterTypes(schema, meta, filter, setFilter);

    return (
        <div>

            <div id="domain-types-wrapper">
                <div id="domain-types-container">
                    <FilterNotice
                        filter={filter}
                        setFilter={setFilter}
                    />
                    <hr/>
                    {
                        filtered.map(type => (
                            <DomainType
                                key={type.name}
                                type={type}
                                schema={schema}
                                meta={meta}
                                filter={filter}
                                setFilter={setFilter}
                            />
                        ))
                    }
                    <DomainRelationsLayer
                        under="domain-types-container"
                        objectTypes={ filtered }
                    />
                </div>
            </div>

        </div>
    );
};

export default DomainTables;
