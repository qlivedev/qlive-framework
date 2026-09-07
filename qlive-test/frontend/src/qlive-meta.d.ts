/*
    Application-level extension of the DomainQL meta data.

    The server-side meta data is an open map that every MetadataProvider bean adds to, so the DomainQLMeta type
    qlive-ts declares only covers what the framework itself knows about. Declaration merging is how an application
    adds the rest: name your addenda here once and config().meta is typed everywhere, with no casting at the use
    sites -- and the declarations stay in one place when a provider changes.

    Written by ExampleMetadataProvider on the Java side.
*/
import "@quinscape/qlive-ts"

declare module "@quinscape/qlive-ts" {

    interface DomainQLMeta {

        /**
         * Names of the GraphQL types taking part in the quick search, alphabetically.
         */
        quickSearchTypes: string[]
    }

    interface DomainQLFieldMeta {

        /**
         * true if this is the field the quick search of its type matches against.
         */
        quickSearch?: boolean
    }
}
