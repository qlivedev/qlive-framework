package io.github.qlivedev.qlivetest.runtime.config;

import io.github.qlivedev.runtime.QLivePaths;
import io.github.qlivedev.runtime.controller.GraphQLController;
import io.github.qlivedev.runtime.controller.TrackUsageDevController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The same endpoints under a profile that is not dev, where they have to be refused.
 * <p>
 * Worth asserting rather than reading, because the annotation that used to look like the gate was a
 * {@code @Profile} on a handler method, which does nothing: Spring evaluates it for bean definitions, not
 * for the request mappings of a bean that exists. Against the configuration these tests were written for,
 * {@code POST /_dev/graphql} answered 200 here -- an unauthenticated, CSRF-exempt GraphQL endpoint in
 * production.
 * <p>
 * Which of the two rules does the work is worth being exact about, because it is easy to get backwards.
 * The unauthenticated cases below are refused by CSRF before authorization is ever consulted, so they pass
 * with no rule for {@link QLivePaths#DEV_URIS} at all. They are not the evidence. CSRF stops a foreign page
 * using a visitor's session; it stops nobody who calls the endpoint directly and fetches a token for their
 * own session the way the application's own frontend does. What is left holding the door is the
 * authorization rule, and {@link #refusesTheGraphQLEndpointToAnAuthenticatedCallerWithAToken()} is the one
 * that pins it: without {@code denyAll} that request is answered 200.
 * <p>
 * The datasource comes from the dev properties because the prod ones carry none. What is under test is the
 * profile, not where the rows live.
 */
@SpringBootTest(properties = "spring.profiles.active=prod")
@TestPropertySource(locations = "classpath:application-dev.properties")
@AutoConfigureMockMvc
class DevEndpointsOutsideDevProfileTest
{
    @Autowired
    private MockMvc mockMvc;


    /**
     * Mapped here as everywhere, and answered with 403 instead of the 200 it gives in dev. Refused by CSRF
     * rather than by authorization, this one -- see the class comment.
     */
    @Test
    void refusesTheGraphQLEndpoint() throws Exception
    {
        mockMvc.perform(
            post(GraphQLController.GRAPHQL_DEV_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"{__typename}\"}")
        ).andExpect(status().isForbidden());
    }


    /**
     * The one that pins the authorization rule: a caller who is logged in and carries a token satisfies
     * everything except the rule under test. Answered 200 before this commit, and 200 again if the
     * {@code denyAll} is taken back out.
     */
    @Test
    void refusesTheGraphQLEndpointToAnAuthenticatedCallerWithAToken() throws Exception
    {
        mockMvc.perform(
            post(GraphQLController.GRAPHQL_DEV_URI)
                .with(user("someone").roles("USER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"{__typename}\"}")
        ).andExpect(status().isForbidden());
    }


    /**
     * Forbidden rather than not-found, which is the whole assertion: this controller's bean really is
     * dev-only, so a 404 is what an unguarded pattern produced too. 403 says the request was turned away
     * before anything went looking for a handler.
     */
    @Test
    void refusesTheAnalysisPush() throws Exception
    {
        mockMvc.perform(
            post(TrackUsageDevController.TRACK_USAGE_DEV_URI + "?full=true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usages\":{}}")
        ).andExpect(status().isForbidden());
    }
}
