/*
 * Public API of @quinscape/qlive-ts.
 *
 * Value exports come first, type exports after -- keeping them apart makes it
 * obvious at a glance what an application can call and what it can only name.
 *
 * Everything reachable from an exported signature is exported here as well, so
 * an application can always name the types it is handed. What is deliberately
 * kept internal, and why, is listed at the bottom of this file.
 */

// ---------------------------------------------------------------------------
// VALUES
// ---------------------------------------------------------------------------

export { startup } from "./startup";
export { default as config } from "./config";
export { default as i18n } from "./i18n";

export { default as inject } from "./inject";
export { GraphQLQuery } from "./GraphQLQuery";
export { QueryDocument } from "./QueryDocument";
export { parseQuery } from "./util/parseQuery";

export { loadView, viewNames, routeNames } from "./views";
export { loadViewForPath, appBase, routeOf, urlOf } from "./router";

export {
    registerConverter,
    getConverter,
    convertToServer,
    convertSelectionFromServer,
    convertResultFromServer,
    convertVariablesToServer
} from "./converter";

export {
    unwrapAll,
    unwrapNonNull,
    isListType,
    isNonNull,
    findType,
    isQueryDocumentType,
    LIST,
    NON_NULL
} from "./type-utils";

// Namespaced rather than flattened: the DSL claims short, common names
// (and, or, not, field, value, condition), which would collide in an
// application's import list.
export * as FilterDSL from "./FilterDSL";

export { decompileFilter } from "./util/decompileFilter";

// ---------------------------------------------------------------------------
// TYPESCRIPT TYPES
// ---------------------------------------------------------------------------

export type { StartupOptions } from "./startup";

export type {
    QLiveBoostrap,
    QLiveConfig,
    InjectionSource,
    DomainQLMeta,
    DomainQLTypeMeta,
    GenericTypeInfo,
    RelationInfo,
    SourceField,
    TargetField
} from "./config";

export type { InjectParams } from "./inject";
export type { GraphQLParams } from "./util/graphql";
export type { QueryConfig, QueryDocumentMethods } from "./QueryDocument";
export type { ParsedQuery, QuerySelection, OperationType } from "./util/parseQuery";

export type { ViewModules } from "./views";

export type {
    Converter,
    ConversionFn,
    SelectionNode,
    QueryConversionMap
} from "./converter";

export type {
    GenericScalar,
    GenericBigDecimal,
    GenericByte,
    GenericComputedValue,
    GenericCondition,
    GenericDate,
    GenericDomainObject,
    GenericFieldExpression,
    GenericJSONB,
    GenericLong,
    GenericTimestamp
} from "./GraphQL";

export type {
    GraphQLSchema,
    GraphQLType,
    GraphQLNamedTypeBase,
    GraphQLScalarType,
    GraphQLObjectType,
    GraphQLInterfaceType,
    GraphQLUnionType,
    GraphQLEnumType,
    GraphQLInputObjectType,
    GraphQLField,
    GraphQLInputValue,
    GraphQLEnumValue,
    GraphQLTypeRef,
    GraphQLNamedTypeRef,
    GraphQLModifiedTypeRef,
    GraphQLList,
    GraphQLNonNull,
    GraphQLTypeKind,
    GraphQLNamedTypes,
    GraphQLModifiedTypes
} from "./GraphQLSchema";

/*
 * ---------------------------------------------------------------------------
 * DELIBERATELY NOT EXPORTED
 * ---------------------------------------------------------------------------
 *
 * These are exported from their module for use inside the framework, but not
 * from here. Anything added to this list needs a reason; anything left off it
 * that shows up in the public .d.ts is an oversight, not a decision.
 *
 * Lifecycle -- startup() runs these in the right order, and running them by
 * hand puts the framework in a half-initialized state:
 *
 *   config.init()                       bootstrap data -> config
 *   converter.initConverters()          built-in converters, needs the config
 *   data.initData()                     injections received with the page
 *   views.registerViews()               view modules from import.meta.glob()
 *
 * Internal plumbing -- an implementation detail of a public entry point, and
 * the public one is the supported way in:
 *
 *   util/graphql (default export)       raw fetch, does no value conversion.
 *                                       GraphQLQuery.execute() is the way to
 *                                       run a query.
 *   util/graphql.firstValue             unwraps a result by its single key,
 *                                       which GraphQLQuery/inject() already do
 *   util/conversionMap.buildConversionMap
 *                                       a GraphQLQuery builds its own map
 *   data (default export)               reads one injection by id; inject()
 *                                       is the way to read injected data
 *   views.viewNameForRoute()            resolution step inside
 *                                       loadViewForPath()
 *   util/delay                          a setTimeout promise, not framework API
 *   util/viteEnv.isViteDev/viteBaseUrl  reads Vite's import.meta.env, which an
 *                                       application has direct access to
 *
 * FilterDSL machinery -- the constants whose keys generate the fluent methods,
 * and the mapped types generated from them. What an application names is the
 * result: FilterDSL.Condition, FilterDSL.Field, FilterDSL.Value.
 *
 *   FIELD_CONDITIONS, CONDITION_METHODS, FIELD_OPERATIONS
 *   CondFn, OpFn, FieldConditions, FieldOperations
 *
 * Named locally only -- exporting the name would say more about the package
 * than it means:
 *
 *   GraphQL.Scalar                      "boolean | number | string | bigint",
 *                                       the argument type of a computed value
 *
 * Not API yet:
 *
 *   config.Injection                    the in-memory shape of an injection,
 *                                       free to change while inject() stays
 *   components/InjectionProvider.InjectionContext
 *                                       an empty stub, nothing to use yet
 */
