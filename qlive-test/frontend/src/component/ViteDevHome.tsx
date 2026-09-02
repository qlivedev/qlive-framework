import React, {useState} from "react"
import { i18n, config, isListType, isNonNull, unwrapAll } from "@quinscape/qlive-ts"
import type {
    DomainQLMeta,
    GraphQLField, GraphQLInterfaceType, GraphQLObjectType,
    GraphQLSchema,
    GraphQLType,
    GraphQLTypeRef
} from "@quinscape/qlive-ts"


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
                location.ref = "#" + targetId;
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
                    key={outgoing.length}
                    href={ "#" + targetId}
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
                    key={outgoing.length}
                    href={ "#" + targetId}
                    title={ "Coming from" + relation.sourceType + "." + relation.leftSideObjectName  }
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

function renderTypes(schema: GraphQLSchema, meta: DomainQLMeta, filter: string, setFilter : (filter: string) => void) : GraphQLObjectType
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
    const elements = []
    for (let i = 0; i < types.length; i++)
    {
        const type = types[i];
        if (
            type.kind === "OBJECT" &&
            !objectTypeNegativeList.includes(type.name) &&
            (!re || re.test(type.name))
        )
        {
            elements.push(
                <DomainType
                    key={ type.name }
                    type={ type }
                    schema={ schema }
                    meta={ meta }
                    filter={filter}
                    setFilter={setFilter}
                />
            )

        }
    }
    return elements
}

const ViteDevHome = ({}) => {

    const [filter,setFilter] = useState("")

    const { schema, meta } = config();

    const renderedTypes = renderTypes(schema, meta, filter, setFilter);

    return (
        <>
            <h1>Vite Dev Root</h1>
            <p>
                {
                    i18n("ViteDevHome Message")
                }
            </p>
            <p>
                <a href="home">Home</a>
            </p>
            <h2>Domain</h2>
            <details>
                <summary> Details ...</summary>
                <span>
                    <SearchBar
                        filter={filter}
                        setFilter={setFilter}
                    />
                    {
                        renderedTypes
                    }
                </span>
            </details>
        </>
    )
}

export default ViteDevHome
