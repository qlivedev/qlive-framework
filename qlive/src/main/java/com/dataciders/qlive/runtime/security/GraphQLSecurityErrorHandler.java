package com.dataciders.qlive.runtime.security;

import com.dataciders.qlive.runtime.util.JSONUtil;
import graphql.ErrorClassification;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphqlErrorBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * <p>
 *     Answers security failures on the GraphQL endpoint in the GraphQL response format, i.e. with
 *     {@code data: null} and the reason in {@code errors}.
 * </p>
 * <p>
 *     Spring Security's own answers are aimed at a browser following links: an unauthenticated request is
 *     redirected to the login page, a denied one gets an empty 403. The endpoint is only ever spoken to by
 *     the frontend's {@code graphql()} though, which parses every response as a GraphQL result -- so both
 *     arrive there as a parse error about HTML instead of the reason the call failed. This hands it
 *     something it already knows how to read.
 * </p>
 * <p>
 *     The HTTP status still says what happened -- 401 for "not authenticated", 403 for "not allowed" -- so
 *     a caller that wants to tell an expired session from a missing role, and send the user to the login
 *     page for the first, can do so without inspecting the error.
 * </p>
 * <p>
 *     Register it for the GraphQL endpoint alone, as both entry point and access denied handler; the
 *     application's other URLs are served to a browser and want the redirect.
 * </p>
 */
public class GraphQLSecurityErrorHandler
    implements AuthenticationEntryPoint, AccessDeniedHandler
{
    private final static Logger log = LoggerFactory.getLogger(GraphQLSecurityErrorHandler.class);

    /**
     * Classification of a request without (or with an expired) authentication. Ends up in the error's
     * {@code extensions.classification}, where GraphQL errors carry their type.
     */
    private final static ErrorClassification UNAUTHENTICATED =
        ErrorClassification.errorClassification("UNAUTHENTICATED");

    /**
     * Classification of a request whose authentication is not enough for the endpoint -- a missing role, or
     * a missing CSRF token, which spring security denies the same way.
     */
    private final static ErrorClassification FORBIDDEN =
        ErrorClassification.errorClassification("FORBIDDEN");


    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException
    {
        log.debug("Unauthenticated GraphQL request", authException);

        writeError(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED, "Not authenticated");
    }


    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException accessDeniedException
    ) throws IOException
    {
        // the exception message names the reason (a missing CSRF token, most of the time) and stays in the
        // log rather than going out over the wire.
        log.debug("Denied GraphQL request", accessDeniedException);

        writeError(response, HttpStatus.FORBIDDEN, FORBIDDEN, "Access denied");
    }


    private void writeError(
        HttpServletResponse response,
        HttpStatus status,
        ErrorClassification classification,
        String message
    ) throws IOException
    {
        // built as an ExecutionResult so the response goes through the same toSpecification() the endpoint
        // itself answers with, rather than a second, hand-rolled idea of what a GraphQL result looks like.
        final ExecutionResult result = ExecutionResultImpl.newExecutionResult()
            .data(null)
            .addError(
                GraphqlErrorBuilder.newError()
                    .message(message)
                    .errorType(classification)
                    .build()
            )
            .build();

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
            JSONUtil.DEFAULT_GENERATOR.forValue(result.toSpecification())
        );
    }
}
