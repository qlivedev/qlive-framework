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

// Re-exported rather than left to the application: QLive picked this polyfill,
// converts Date and Timestamp into it, and generates types that name it. An
// application declaring temporal-polyfill itself would be free to resolve a
// second copy, and instants from one do not typecheck against the other.
export { Temporal } from "temporal-polyfill";
export { default as config } from "./config";
export { default as i18n } from "./i18n";

export { useInjection } from "./useInjection";
export { default as data, injectionSource } from "./data";
export { GraphQLQuery } from "./GraphQLQuery";
export { default as graphql, firstValue } from "./util/graphql";
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
    objectFields,
    isQueryDocumentType,
    LIST,
    NON_NULL
} from "./type-utils";

// Namespaced rather than flattened: the DSL claims short, common names
// (and, or, not, field, value, condition), which would collide in an
// application's import list.
export * as FilterDSL from "./FilterDSL";

export { default as DomainTables } from "./component/DomainTables";
export { default as ErrorBoundary } from "./component/ErrorBoundary";

export { decompileFilter } from "./util/decompileFilter";

// What the schema and the type meta data say about merging a type. Namespaced
// the way FilterDSL is: isLinkType, versionedTypes and ignoredFields say what
// they mean next to a merge prefix and not much without one.
export * as MergeMeta from "./merge/meta";

export { subscribeToTopic, PubSubConnection } from "./pubsub";

// Push's first consumer: what other people's writes mean for the rows this
// page is showing or editing.
export { useLiveRows, useLiveWorkingSet } from "./push/useLive";
export { watchDocument, watchWorkingSet, ENTITY_VERSION } from "./push/entityVersion";

export { WorkingSet } from "./merge/WorkingSet";
export { useWorkingSet } from "./merge/useWorkingSet";
export { useMerge } from "./merge/useMerge";
export { mergeWorkingSet } from "./merge/mergeWorkingSet";

/**
 * Declares that this entry point needs no domain schema.
 *
 * Call it at the top level of an entry module. The call does nothing at runtime and exists to be seen by the
 * build's track-usage analysis, which the server reads: a path whose module declares this gets the reduced
 * bootstrap -- context path, CSRF token and injections, with an empty schema and empty meta data in place of
 * the domain. On a large domain that is the difference between shipping the whole introspection result and
 * shipping none of it.
 *
 * Only for entry points that issue no queries and render no application view: a login page, an error page, a
 * public landing page. Anything that resolves a route or reads an injection with a query needs the schema and
 * will fail on its first type lookup without it.
 */
export function noSchema()
{
    // only exists for static analysis
}

export { default as findRoot } from "./util/findRoot";

// ---------------------------------------------------------------------------
// TYPESCRIPT TYPES
// ---------------------------------------------------------------------------

export type { StartupOptions } from "./startup";

export type {
    QLiveBoostrap,
    QLiveConfig,
    Authentication,
    CSRFToken,
    Injection,
    InjectionSource,
    DomainQLMeta,
    DomainQLTypeMeta,
    DomainQLTypeMetaProps,
    DomainQLFieldMeta,
    GenericTypeInfo,
    RelationInfo,
    SourceField,
    TargetField
} from "./config";

export type { InjectParams } from "./inject";
export type { GraphQLParams } from "./util/graphql";
export type { QueryConfig, QueryConfigDelta, QueryDocumentSnapshot, QueryDocumentMethods } from "./QueryDocument";
export type { ParsedQuery, QuerySelection, OperationType } from "./util/parseQuery";

export type { ViewModules } from "./views";

export type { ErrorViewProps } from "./component/ErrorView";
export type { ErrorBoundaryProps } from "./component/ErrorBoundary";

export type { DomainTablesProps } from "./component/DomainTables";

export type { PubSubStatus, PubSubConnectionSnapshot, TopicHandler } from "./pubsub";

export type {
    EntityVersionMessage,
    MovedRow,
    DocumentWatch,
    DocumentWatchSnapshot
} from "./push/entityVersion";

export type { MergeTypeMeta, LinkRelation } from "./merge/meta";

export type {
    RegisteredDocument,
    WorkingSetSnapshot,
    WorkingSetOptions,
    StoredState
} from "./merge/WorkingSet";

export type { HeldRows } from "./util/rows";

export type {
    MergeAccessor,
    MergeField,
    MergeFieldStatus,
    MergeView,
    Resolution
} from "./merge/MergeAccessor";

export type {
    MergeStatus,
    FieldChange,
    EntityChange,
    EntityDeletion,
    MergeConfig,
    MergeConflictField,
    MergeConflict,
    MergeResult
} from "./merge/types";

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
 *   pubsub.initPubSub()                 drops the push connection a previous
 *                                       startup left and clears its subscriptions
 *
 * Internal plumbing -- an implementation detail of a public entry point, and
 * the public one is the supported way in:
 *
 *   inject.inject()                     the plain read of an injection, without
 *                                       the subscription that makes an update
 *                                       show up. useInjection() is the way in;
 *                                       data() covers the value + meta case, and
 *                                       injectionSource() the ids no view claimed
 *   data.storeInjection()               the other half of inject(): what it
 *                                       converted, kept for the next read
 *   util/conversionMap.buildConversionMap
 *                                       a GraphQLQuery builds its own map
 *   views.viewNameForRoute()            resolution step inside
 *                                       loadViewForPath()
 *   component/ErrorBoundary.ErrorBoundaryState
 *                                       the boundary's own state. A class
 *                                       declaration names its state type
 *                                       whether or not anyone else may say it
 *   component/ErrorView.DefaultErrorView
 *                                       what config().errorView holds until an
 *                                       application assigns its own. Reached
 *                                       through the config, and replacing it is
 *                                       an assignment rather than a composition
 *   merge/MergeAccessor.createAccessor  what WorkingSet#accessor() calls. The
 *                                       accessor is made by the working set
 *                                       holding the entity, and there is no
 *                                       entity to make one for outside it
 *   merge/MergeAccessor.MergeHost/MergeEntity
 *                                       the two shapes createAccessor() is
 *                                       handed, named so that the accessor
 *                                       depends on no store rather than to be
 *                                       implemented by anyone
 *   merge/WorkingSet.workingSetOf       how useMerge() gets from a draft to the
 *                                       working set that made it. An
 *                                       application holds the working set it
 *                                       made and needs no way back to it
 *   QueryDocument.documentOf               how a working set gets from the
 *                                       snapshot a view registered to the
 *                                       document behind it. An application
 *                                       holds both already -- it has the
 *                                       document it injected and the snapshot
 *                                       it rendered -- and needs no lookup
 *   util/rows.walkRows/RowVisit/RowRelation
 *                                       the schema-driven walk a store does
 *                                       over its own rows. What comes out of
 *                                       one is HeldRows, which is exported;
 *                                       the visit itself is how a store builds
 *                                       that and not a thing to drive
 *   merge/fieldMask.maskOf/maskedFields/fieldOrder
 *                                       bit positions of a type's fields,
 *                                       which have to agree with FieldLayout on
 *                                       the Java side exactly. A caller that
 *                                       needs a mask is building a subscription
 *                                       by hand, and the watchers do that
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
 */
