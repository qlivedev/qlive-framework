/*
 * Layouts: what a form renders, as plain data, and the layout derived from a row where none is given.
 */
import type {ComponentType, ReactNode} from "react";

import config from "../config";
import type {FilterExpression} from "../FilterDSL";
import {manyToManyField} from "../merge/meta";
import {isListType, objectFields, unwrapAll} from "../type-utils";
import type {FieldMode} from "./fieldMode";
import type {Widget} from "./widgets";

/**
 * What a form renders for one entity: its fields, grouped and arranged by the nodes.
 */
export interface Layout
{
    /** the name overrides match it by (WidgetOverride), if it has one */
    name?: string
    /** the type of the row the form edits */
    domainType: string
    nodes: LayoutNode[]
}

/**
 * One field, rendered by Field with these options.
 */
export interface LayoutFieldNode
{
    /** field path, see useField() */
    field: string
    /** the widget, or the name it is registered under (registerWidget()) */
    widget?: Widget | string
    /** the mode the field starts in */
    mode?: FieldMode
    label?: ReactNode
    /** which related rows a to-one relation offers, see FieldOptions.condition */
    condition?: FilterExpression | null
}

/**
 * A labeled group of nodes, a `<fieldset>` whose legend is the i18n() of the section.
 */
export interface LayoutSectionNode
{
    section: string
    nodes: LayoutNode[]
}

/**
 * A component of the application's, with the nodes as its children.
 */
export interface LayoutComponentNode
{
    /** the component, or the name it is registered under (registerComponent()) */
    component: ComponentType<any> | string
    props?: Record<string, unknown>
    nodes?: LayoutNode[]
}

/**
 * One node of a layout.
 */
export type LayoutNode = LayoutFieldNode | LayoutSectionNode | LayoutComponentNode

const componentRegistry = new Map<string, ComponentType<any>>()

/**
 * Gives a component the name a layout refers to it by. A layout written in TSX holds the component itself and needs
 * no name.
 *
 * @param name          component name
 * @param component     the component, rendered with the node's props and its nodes as children
 */
export function registerComponent(name: string, component: ComponentType<any>): void
{
    componentRegistry.set(name, component)
}

/**
 * The component a name stands for.
 *
 * @throws Error for a name no component is registered under
 *
 * @internal
 */
export function namedComponent(name: string): ComponentType<any>
{
    const component = componentRegistry.get(name)
    if (!component)
    {
        throw new Error(
            "No component is registered as " + JSON.stringify(name) + ". Register it with registerComponent()."
        )
    }
    return component
}

/**
 * The to-one relation of a type whose single foreign key is the given field, `null` for a field that is no such key.
 */
function relationByKey(type: string, field: string)
{
    return config().meta.relations.find(
        r => r.sourceType === type && r.leftSideObjectName && r.sourceFields.length === 1 && r.sourceFields[0] === field
    ) ?? null
}

/**
 * The layout of a form nobody wrote one for: a field node for every field the row has, in the order it has them,
 * which is the order the query selected them in. A new row has every field, in schema order after those set.
 *
 * - A foreign key and the relation object it is the key of become one node, the relation's, where the first of the
 *   two is: `ownerId` and `owner` are edited as `owner`.
 * - A list is dropped unless it is a many-to-many field.
 * - A field the schema doesn't have is dropped.
 *
 * Every node is a plain field node, so the modes are the defaults (defaultFieldMode()).
 *
 * @param type      the row's type
 * @param row       a row, or a draft of one
 */
export function deriveLayout(type: string, row: object): Layout
{
    const fields = new Map(objectFields(type).map(f => [f.name, f]))
    const paths: string[] = []
    const add = (path: string) => {
        if (!paths.includes(path))
        {
            paths.push(path)
        }
    }

    // a new row holds only what was set, and has every field of its type
    const names = [...Object.keys(row), ...[...fields.keys()].filter(name => name in row)]
    for (const name of names)
    {
        const field = fields.get(name)
        if (!field)
        {
            continue
        }
        if (isListType(field.type))
        {
            if (manyToManyField(type, name))
            {
                add(name)
            }
            continue
        }
        if (unwrapAll(field.type).kind === "OBJECT")
        {
            const relation = config().meta.relations.find(r => r.sourceType === type && r.leftSideObjectName === name)
            if (relation?.sourceFields.length === 1)
            {
                add(name)
            }
            continue
        }
        add(relationByKey(type, name)?.leftSideObjectName ?? name)
    }

    return {domainType: type, nodes: paths.map(field => ({field}))}
}
