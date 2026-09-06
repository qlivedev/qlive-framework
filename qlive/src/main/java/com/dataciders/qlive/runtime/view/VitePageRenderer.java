package com.dataciders.qlive.runtime.view;

import com.dataciders.qlive.model.bootstrap.QLiveBoostrap;
import com.dataciders.qlive.runtime.service.BootstrapService;
import de.quinscape.spring.jsview.util.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 *     Renders one of Vite's HTML entry points with the current {@link QLiveBoostrap} spliced into the empty
 *     {@code #root-data} placeholder script tag it carries.
 * </p>
 * <p>
 *     {@link com.dataciders.qlive.runtime.controller.ViteIndexController} serves the application's
 *     {@code index.html} with this. An application that declares further entry points in its Vite config --
 *     a login page, say, with its own HTML and its own startup script -- serves them the same way by naming
 *     them here, and they boot exactly like the application does: bootstrap data embedded in production,
 *     and in {@code vite dev}, where nobody touches the placeholder, fetched from {@code /api/bootstrap} by
 *     the frontend instead.
 * </p>
 */
public class VitePageRenderer
{
    private final static Logger log = LoggerFactory.getLogger(VitePageRenderer.class);

    /**
     * Where the Maven build puts Vite's output, i.e. what {@code frontend/dist} is on the classpath.
     */
    private final static String BUILD_OUTPUT = "static/";

    private final static String PLACEHOLDER =
        "<script id=\"root-data\" type=\"x-application/view-data\"></script>";

    private final BootstrapService bootstrapService;

    /**
     * Entry point name to its HTML. Every full page load needs the template again, and it only changes when
     * the frontend is rebuilt, which does not happen while the server runs.
     */
    private final Map<String, String> templates = new ConcurrentHashMap<>();


    public VitePageRenderer(BootstrapService bootstrapService)
    {
        this.bootstrapService = bootstrapService;
    }


    /**
     * Renders the given entry point for the given path.
     *
     * @param entryPoint    HTML file of the entry point as Vite emits it, e.g. {@code "index.html"} or
     *                      {@code "login.html"} -- an application's own entry points have to be declared in
     *                      its Vite config's {@code build.rollupOptions.input} to end up in the build
     * @param path          the request URI, i.e. exactly what the browser has in {@code location.pathname}
     *                      -- including the context path, and percent-encoded the way the browser encodes it.
     *                      It decides what the bootstrap carries, and the frontend sends the same string to
     *                      {@code /api/bootstrap} when it fetches that data itself in {@code vite dev}, so
     *                      the two routes have to agree on it down to the character
     * @param csrfToken     CSRF token of the current session, handed to the frontend with the bootstrap
     *
     * @return the page, or 503 while there is nothing to serve it from yet
     */
    public ResponseEntity<String> render(String entryPoint, String path, CsrfToken csrfToken)
    {
        final String template = loadTemplate(entryPoint);
        if (template == null)
        {
            return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        }

        final QLiveBoostrap bs = bootstrapService.provideConfig(csrfToken, path);

        // we replace a null config with "" to trigger the frontend's live fetch
        final String data = bs != null ? JSONUtil.DEFAULT_GENERATOR.forValue(bs) : "";
        final String html = template.replace(
            PLACEHOLDER,
            "<script id=\"root-data\" type=\"x-application/view-data\">" + data + "</script>"
        );

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html);
    }


    private String loadTemplate(String entryPoint)
    {
        return templates.computeIfAbsent(
            entryPoint,
            name -> {
                final String resource = BUILD_OUTPUT + name;
                try
                {
                    final String html = new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
                    if (!html.contains(PLACEHOLDER))
                    {
                        log.warn(
                            "{} does not contain the expected placeholder ({}) -- was it built from a " +
                                "version of the entry point without it?",
                            resource,
                            PLACEHOLDER
                        );
                    }
                    return html;
                }
                catch (IOException e)
                {
                    log.error(
                        "Could not load " + resource + " -- has the frontend been built (pnpm build), and " +
                            "is this entry point part of the build?",
                        e
                    );
                    // computeIfAbsent stores nothing for null, so a later request tries again -- which is
                    // what we want while the frontend simply has not been built yet.
                    return null;
                }
            }
        );
    }
}
