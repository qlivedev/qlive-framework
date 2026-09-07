---
title: Server setup
description: The Spring beans an application wires up.
sidebar:
  order: 9
---

The Java half is a Spring Boot application depending on `qlive`, which
brings DomainQL and jOOQ with it. `qlive-test` is the reference wiring.

## What QLive provides

`QLiveConfiguration` is a `@Configuration` that contributes:

| Bean | |
|---|---|
| `BootstrapService` | assembles the config, CSRF token and injections for a path |
| `GraphQL` | built from the DomainQL schema |
| `ConditionParser` | parses FilterDSL JSON into the condition model |

It requires a `DomainQL` bean and a `StaticAnalysisProvider` bean from your
application.

The static analysis provider is **required, not optional**. Without one the
bootstrap service could not tell which paths declare `noSchema()`, and a
`null` from a provider has to keep meaning one thing -- "not ready yet, ask
again" -- rather than doubling as "this application never configured one".
Forgetting the bean gets you a missing-bean failure at startup instead of
pages that answer 503 forever.

## What your application wires up

### The domain

```java
@Bean
public DomainQL domainQL() throws IOException
{
    return newDomainQL(
        dslContext,
        applicationContext.getBeansWithAnnotation(GraphQLLogic.class).values(),
        applicationContext.getBeansOfType(MetadataProvider.class).values()
    );
}

static DomainQL newDomainQL(
    DSLContext dslContext,
    Collection<Object> logicBeans,
    Collection<MetadataProvider> metadataProviders
) throws IOException
{
    return QLiveDomain.newDomain(dslContext, metadataProviders)
        .logicBeans(logicBeans)
        .objectTypes(Public.PUBLIC)
        // ...
        .build();
}
```

`QLiveDomain.newDomain()` is a builder helper that standardises the QLive
scalars (`QueryConfig`, `Condition`, `FieldExpression`, `ComputedValue` and
the rest) into a DomainQL environment.

Keep the domain definition separable from the bean wiring: nothing in it
touches the `DSLContext` until a query executes, so a test can build the
same schema with `null` and assert on it without a database. Hand it the
same logic beans and metadata providers, or you are testing a different
schema than you ship.

### Static analysis, per profile

```java
@Profile("dev")
@Bean
public DevStaticAnalysisProvider devStaticAnalysisProvider()
{
    return new DevStaticAnalysisProvider();
}

@Profile("prod")
@Bean
public ProdStaticAnalysisProvider prodStaticAnalysisProvider()
{
    return new ProdStaticAnalysisProvider();
}
```

The dev provider holds the last snapshot pushed by the Vite dev server; the
production one reads `track-usage.json` from the classpath, where the Maven
build puts Vite's output. The production one is also where an application's
use of the analysis is **checked**: the whole of it is present before the
first request, so a build whose injections are not where they belong fails
at startup rather than on the page that happens to hit the mistake. The dev
provider reports the same thing and carries on -- you are mid-edit.

### Page rendering

```java
@Bean
public VitePageRenderer vitePageRenderer()
{
    return new VitePageRenderer(bootstrapService);
}

@Bean
public ViteIndexController viteIndexController(VitePageRenderer renderer)
{
    return new ViteIndexController(bootstrapService, renderer);
}
```

`ViteIndexController` serves everything below the Vite base, hands Vite's
emitted assets back to static resource handling, and answers
`/api/bootstrap` and `/api/update`. Share one `VitePageRenderer` across
controllers -- it caches the entry point templates, which only change when
the frontend is rebuilt.

### The GraphQL endpoint

```java
@Bean
public GraphQLController graphQLController(GraphQL graphQL)
{
    return new GraphQLController(graphQL);
}
```

`/graphql` is under normal Spring Security protection including CSRF. There
is a second, dev-only endpoint exempt from CSRF, enabled with the `dev`
profile.

### Dev-only: the query typing service

```java
@Bean
@Profile("dev")
public GraphQLQueryTypingService hotReloadTypingService(
    DomainQL domainQL,
    @Value("${qlive.dev.ts-source:frontend/src}") String tsSource
) throws IOException { ... }

@Bean
@Profile("dev")
public TrackUsageDevController trackUsageDevController(
    GraphQLQueryTypingService typingService,
    DevStaticAnalysisProvider provider
) { ... }
```

This is what writes generated result types back into your query modules.
`qlive.dev.ts-source` has to name the same directory as the Vite plugin's
`sourceRoot`, because the module paths in the pushed data are relative to
it. A relative value resolves against the backend's working directory,
which is a good reason to set it explicitly.

## Query logic

See [Query documents](../query-documents/) for the document query bean --
one generic method with `@GraphQLTypeParam` covers every type.

## Security

Two things are worth knowing because they are easy to get wrong.

**The GraphQL endpoint wants GraphQL-shaped errors.** Spring Security's own
answers are aimed at a browser following links: an unauthenticated request
is redirected to the login page, a denied one gets an empty 403. The
frontend parses every response from `/graphql` as a GraphQL result, so both
arrive as a parse error about HTML rather than the reason the call failed.
Register `GraphQLSecurityErrorHandler` for that URL alone, as both entry
point and access-denied handler:

```java
.exceptionHandling(exceptions -> {
    final GraphQLSecurityErrorHandler graphQLErrors = new GraphQLSecurityErrorHandler();
    final PathPatternRequestMatcher graphQLEndpoint =
        PathPatternRequestMatcher.withDefaults().matcher(GraphQLController.GRAPHQL_URI);

    exceptions
        .defaultAuthenticationEntryPointFor(graphQLErrors, graphQLEndpoint)
        .defaultAccessDeniedHandlerFor(graphQLErrors, graphQLEndpoint);
})
```

The HTTP status still says what happened -- 401 for "not authenticated",
403 for "not allowed" -- so a caller can tell an expired session from a
missing role without inspecting the error body. Your other URLs are served
to a browser and want the redirect.

**The login POST stays CSRF-protected.** The login page is reachable
without authentication, but do not put it in the list of URIs excluded from
CSRF: that POST is exactly the request that has to stay protected, so a
foreign page cannot log a user in as someone else.

## Metadata providers

Every `MetadataProvider` bean is picked up automatically and writes into
the `DomainQLMeta` the server embeds in the page, on two levels: an
addendum next to `types`, `genericTypes` and `relations`, and field meta
data on individual fields.

The client-side counterpart is declaration merging -- name your addenda
once and they are typed everywhere the application reads `config().meta`:

```ts
declare module "@quinscape/qlive-ts" {
    interface DomainQLMeta {
        quickSearchTypes: string[]
    }
    interface DomainQLFieldMeta {
        quickSearch?: boolean
    }
}
```

Nothing but a test on the Java side notices when the two drift apart, so it
is worth having one.
