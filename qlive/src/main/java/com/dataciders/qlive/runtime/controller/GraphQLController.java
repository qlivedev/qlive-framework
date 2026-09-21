package com.dataciders.qlive.runtime.controller;

import com.dataciders.qlive.runtime.QLivePaths;
import com.dataciders.qlive.runtime.util.GraphQLUtil;
import com.dataciders.qlive.runtime.util.JSONUtil;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;

@Controller
public class GraphQLController
{
    private final static Logger log = LoggerFactory.getLogger(GraphQLController.class);


    private final GraphQL graphQL;

    /**
     * URI for the normal application data usage. Is always under general Spring security protection which includes
     * CSRF protection.
     */
    public final static String GRAPHQL_URI = "/graphql";

    /**
     * Special development GraphQL end point. Unauthenticated and exempt from CSRF protection, which is what
     * lets the frontend tooling use it, and why it is refused outside the dev profile -- see
     * {@link QLivePaths#DEV_URIS}.
     */
    public final static String GRAPHQL_DEV_URI = "/_dev/graphql";

    
    public GraphQLController(
        @Lazy GraphQL graphQL
    )
    {
        this.graphQL = graphQL;

        log.info("Created GraphQLController: graphQL={}", graphQL);
    }


    @RequestMapping(value = GRAPHQL_URI, method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> serveGraphQL(
        @RequestBody Map<String,Object> body
        //@RequestParam("cid") String connectionId
    )
    {
        try
        {
            return executeGraphQLQuery(body);
        }
        catch(Exception e)
        {
            log.error("Error executing query", e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    /**
     * The development endpoint, mapped in every profile. Spring evaluates {@code @Profile} for bean
     * definitions and not for the request mappings of a bean that exists, so an annotation here would
     * read as a gate while being none. What decides whether this can be reached is the application's
     * security configuration refusing {@link QLivePaths#DEV_URIS} outside the dev profile.
     */
    @RequestMapping(value = GRAPHQL_DEV_URI, method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> serveGraphQLDev(@RequestBody Map<String,Object> body)
    {
        try
        {
            return executeGraphQLQuery(body);
        }
        catch(Exception e)
        {
            log.error("Error executing dev query", e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    private ResponseEntity<String> executeGraphQLQuery(@RequestBody Map<String, Object> queryMap)
    {
        ExecutionResult executionResult = GraphQLUtil.executeGraphQLQuery(
            graphQL,
            queryMap,
            null
        );

        final List<GraphQLError> errors = executionResult.getErrors();
        if (errors.size() > 0)
        {
            log.warn("Errors in graphql query: " + GraphQLUtil.formatErrors(errors));
        }

        // result may contain data and/or errors
        Object result = executionResult.toSpecification();
        return new ResponseEntity<>(
            JSONUtil.DEFAULT_GENERATOR.forValue(
                result
            ),
            errors.size() == 0 ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
