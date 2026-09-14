---
title: Wire up a Spring application
description: The beans an application contributes, and the ones QLive brings.
sidebar:
  order: 1
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

## The domain

```java
@Bean
public DomainQL domainQL() throws IOException
{
    return QLiveDomain.newDomainQL(
            dslContext,
            applicationContext.getBeansOfType(MetadataProvider.class).values()
        )   
        .logicBeans(applicationContext.getBeansWithAnnotation(GraphQLLogic.class).values())
        .objectTypes(Public.PUBLIC)
        // ...
        .build();
}
```

`QLiveDomain.newDomain()` is a builder helper that standardises the QLive
scalars (`QueryConfig`, `Condition`, `FieldExpression`, `ComputedValue` and
the rest) into a DomainQL environment.

You can keep the domain definition separable from the bean wiring: nothing in it
touches the `DSLContext` until a query executes, so a test can build the
same schema with `null` and assert on it without a database. Hand it the
same logic beans and metadata providers, or you are testing a different
schema than you ship. 

(See com.dataciders.qlivetest.runtime.config.GraphQLConfiguration.domainQL)

Every `MetadataProvider` bean the context holds is handed to the domain
here -- see
[Add schema metadata](/qlive-framework/how-to/add-schema-metadata/).

## Static analysis, per profile

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

The dev provider holds what the Vite dev server has pushed so far -- a push
carries the modules one save changed, and the provider merges it into the
rest; the production one reads `track-usage.json` from the classpath, where
the Maven build puts Vite's output. The production one is also where an application's
use of the analysis is **checked**: the whole of it is present before the
first request, so a build whose injections are not where they belong fails
at startup rather than on the page that happens to hit the mistake. The dev
provider reports the same thing and carries on -- you are mid-edit.

## Page rendering

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

## The GraphQL endpoint

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

## Dev-only: receiving the pushed analysis

```java
@Bean
@Profile("dev")
public DevStaticAnalysisProvider devStaticAnalysisProvider() { ... }

@Bean
@Profile("dev")
public TrackUsageDevController trackUsageDevController(
    DevStaticAnalysisProvider provider
) { ... }
```

`vite build` writes `track-usage.json` to disk for the backend to read, but
`vite dev` never touches disk. So in dev the plugin pushes the analysis
here instead, and this is what the backend answers page requests from --
which query a view injects, which paths declared `noSchema()`.

A push carries only the modules one save changed; the plugin collects them
for `pushDebounceMs` first. Nothing is generated here: the
[query result types](/qlive-framework/reference/graphql-and-typescript/) are written
by the plugin, in the frontend, where the sources are.

## Query logic

See [Query documents](/qlive-framework/reference/query-documents/) for the document query bean --
one generic method with `@GraphQLTypeParam` covers every type.

## Security

The security configuration is yours, and three of its rules are QLive's.
See [Secure an application](/qlive-framework/how-to/secure-an-application/)
-- the dev endpoints in particular are open until you close them.
