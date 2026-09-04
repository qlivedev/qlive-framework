package com.dataciders.qlive.runtime.controller;

import com.dataciders.qlive.model.bootstrap.Injection;
import com.dataciders.qlive.model.bootstrap.QLiveBoostrap;
import com.dataciders.qlive.runtime.service.BootstrapService;
import de.quinscape.spring.jsview.util.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Serves Vite's own production build ({@code classpath:/static/index.html}) under
 * {@code /app/**}, splicing the current {@link com.dataciders.qlive.model.bootstrap.QLiveConfig}
 * into the empty {@code #root-data} placeholder script tag that
 * {@code qlive-test/frontend/index.html} carries. In {@code vite dev}, nobody touches that
 * template, so the placeholder stays empty and the frontend falls back to fetching the same
 * data live from {@link #bootstrap(String)}.
 */
@Controller
public class ViteIndexController
{
    private final static Logger log = LoggerFactory.getLogger(ViteIndexController.class);

    /**
     * Vite's {@code build.assetsDir}, i.e. the directory below the build output that the emitted chunks land
     * in. Its default; an application that reconfigures it has to keep this in sync.
     */
    private final static String ASSETS_DIR = "assets";

    private final static String PLACEHOLDER =
        "<script id=\"root-data\" type=\"x-application/view-data\"></script>";

    private final BootstrapService bootstrapService;

    private String indexHtml;


    public ViteIndexController(BootstrapService bootstrapService)
    {
        this.bootstrapService = bootstrapService;
    }


    @GetMapping("/api/bootstrap")
    public ResponseEntity<String> bootstrap(
        @RequestParam(value = "path") String path
    )
    {
        final QLiveBoostrap bs = bootstrapService.provideConfig(path);
        if (bs == null)
        {
            return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        }

        return new ResponseEntity<>(
            JSONUtil.DEFAULT_GENERATOR.forValue(bs),
            HttpStatus.OK
        );
    }


    @GetMapping("/api/update")
    public ResponseEntity<String> update(
        @RequestParam(value = "path") String path
    )
    {
        final Map<String, Injection> data = bootstrapService.provideInjectionData(path);

        return new ResponseEntity<>(
            JSONUtil.DEFAULT_GENERATOR.forValue(data),
            HttpStatus.OK
        );
    }


    @GetMapping("/")
    public String root()
    {
        return "redirect:/app/home";
    }


    /**
     * <p>
     *     Hands requests for Vite's emitted assets back to the regular static resource handling.
     * </p>
     * <p>
     *     The application's Vite {@code base} is {@code /app/}, so the built index.html references its chunks
     *     as {@code /app/assets/...} -- which {@link #app(String)} ()}'s {@code /app/**} mapping would otherwise answer
     *     with the index page. This mapping is the more specific one and wins, forwarding to the location the
     *     frontend build actually lands in ({@code classpath:/static/assets/}).
     * </p>
     */
    @RequestMapping("/app/" + ASSETS_DIR + "/{*path}")
    public String assets(@PathVariable String path)
    {
        return "forward:/" + ASSETS_DIR + path;
    }


    @RequestMapping("/app/{*path}")
    public ResponseEntity<String> app(
        @PathVariable String path
    )
    {
        final String template = loadTemplate();
        if (template == null)
        {
            return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        }

        final QLiveBoostrap bs = bootstrapService.provideConfig(path);

        // we replace a null config with "" to trigger
        final String data = bs != null ? JSONUtil.DEFAULT_GENERATOR.forValue(bs) :"";
        final String html = template.replace(
            PLACEHOLDER,
            "<script id=\"root-data\" type=\"x-application/view-data\">" + data + "</script>"
        );

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html);
    }


    private synchronized String loadTemplate()
    {
        if (indexHtml == null)
        {
            try
            {
                indexHtml = new ClassPathResource("static/index.html").getContentAsString(StandardCharsets.UTF_8);
                if (!indexHtml.contains(PLACEHOLDER))
                {
                    log.warn(
                        "static/index.html does not contain the expected placeholder ({}) -- " +
                            "was it built from a version of qlive-test/frontend/index.html without it?",
                        PLACEHOLDER
                    );
                }
            }
            catch (IOException e)
            {
                log.error(
                    "Could not load static/index.html -- has the frontend been built (pnpm build)?",
                    e
                );
            }
        }
        return indexHtml;
    }
}
