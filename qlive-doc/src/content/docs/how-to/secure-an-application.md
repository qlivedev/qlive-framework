---
title: Secure an application
description: GraphQL-shaped errors, the login POST, and closing the dev endpoints.
sidebar:
  order: 2
---

Spring Security configures a QLive application the way it configures any
other. Three things are worth knowing because they are easy to get wrong.

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

**The dev endpoints have to be closed outside the dev profile, by you.**
QLive maps `/_dev/graphql` and `/_dev/track-usage` for the frontend
tooling. They are unauthenticated and CSRF-exempt -- that is what makes
them usable from the Vite dev server, and what makes them a hole anywhere
else. `QLivePaths.DEV_URIS` is the pattern covering them:

```java
if (environment.acceptsProfiles(Profiles.of("dev")))
{
    auth.requestMatchers(QLivePaths.DEV_URIS).permitAll();
}
else
{
    auth.requestMatchers(QLivePaths.DEV_URIS).denyAll();
}
```

Ahead of your `/**` rule, or that one lets any logged-in user at them --
which is the point of the rule, and not something CSRF covers for you. CSRF
stops a foreign page using a visitor's session; it stops nobody who calls
the endpoint directly and fetches a token for their own session the way
your frontend does. Keep them CSRF-exempt only in dev as well, but do not
mistake that for the gate.

Do not reach for `@Profile` on the controller method instead: Spring
evaluates it for bean definitions, not for the request mappings of a bean
that exists, so it reads as a gate while being none. The mappings are there
in every profile; your security configuration is the whole of what decides
whether they answer. `acceptsProfiles` rather than a look at
`spring.profiles.active`, so that a `spring.profiles.default` counts too.

