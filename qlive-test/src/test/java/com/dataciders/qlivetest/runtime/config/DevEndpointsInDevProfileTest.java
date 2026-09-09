package com.dataciders.qlivetest.runtime.config;

import com.dataciders.qlive.runtime.controller.GraphQLController;
import com.dataciders.qlive.runtime.controller.TrackUsageDevController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QLive's development endpoints are open in the dev profile: unauthenticated and exempt from CSRF, which is
 * what lets the Vite dev server reach them at all.
 * <p>
 * This application runs on {@code spring.profiles.default=dev}, so a plain context is the dev one. The other
 * half of the rule is {@link DevEndpointsOutsideDevProfileTest}, and the two belong together -- either alone
 * would pass on a configuration that got the profile the wrong way round.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DevEndpointsInDevProfileTest
{
    @Autowired
    private MockMvc mockMvc;


    @Test
    void servesTheGraphQLEndpointToAnyone() throws Exception
    {
        mockMvc.perform(
            post(GraphQLController.GRAPHQL_DEV_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"{__typename}\"}")
        ).andExpect(status().isOk());
    }


    @Test
    void takesAnAnalysisPushFromAnyone() throws Exception
    {
        mockMvc.perform(
            post(TrackUsageDevController.TRACK_USAGE_DEV_URI + "?full=true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usages\":{}}")
        ).andExpect(status().isNoContent());
    }
}
