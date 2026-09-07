import React, {useLayoutEffect, useState} from "react"
import { isListType, isNonNull, unwrapAll } from "../type-utils"
import config from "../config"
import type { DomainQLMeta } from "../config"
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

function findRelations(schema : GraphQLSchema, meta: DomainQLMeta, type : GraphQLObjectType, field : GraphQLField, setFilter : (filter: string) => void)
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

type SearchBarProps = {
    filter: string
    setFilter : (filter: string) => void
}

const SearchBar = ({ filter, setFilter }: SearchBarProps) => {
    return (
        <span className="search-bar">
            <label aria-label="Search Types">
                <span style={{display: "none"}}>Search Types</span>
                <input
                    type="text"
                    value={filter}
                    onChange={ev => setFilter(ev.target.value)}
                    placeholder="Search Types"/>
            </label>
            <button
                className="btn"
                type="button"
                onClick={() => setFilter("")}
            >
                Clear
            </button>

        </span>
    )
}


type DomainTypeProps = {
    schema: GraphQLSchema
    type: GraphQLObjectType
    meta: DomainQLMeta
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


function filterTypes(schema: GraphQLSchema, meta: DomainQLMeta, filter: string, setFilter : (filter: string) => void) : GraphQLObjectType[]
{
    let re: null | RegExp = null;
    try
    {
        re = filter ? new RegExp(filter, "gi") : null;
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

    useLayoutEffect(() => {

        const container = document.getElementById(under);

        const rect = container?.getBoundingClientRect();
        if (!rect)
        {
            throw new Error("No getBoundingClientRect")
        }

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
    }, []);

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
                    <SearchBar
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
