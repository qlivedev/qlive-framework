package com.dataciders.qlive.runtime.config;

import de.quinscape.spring.jsview.util.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves Vite's own production build ({@code classpath:/static/index.html}) under
 * {@code /app/**}, splicing the current {@link com.dataciders.qlive.model.bootstrap.QLiveConfig}
 * into the empty {@code #root-data} placeholder script tag that
 * {@code qlive-test/frontend/index.html} carries. In {@code vite dev}, nobody touches that
 * template, so the placeholder stays empty and the frontend falls back to fetching the same
 * data live from {@link #bootstrap()}.
 */
@Controller
public class ViteIndexController
{
    private final static Logger log = LoggerFactory.getLogger(ViteIndexController.class);

    private final static String PLACEHOLDER =
        "<script id=\"root-data\" type=\"x-application/view-data\"></script>";

    private final QLiveConfigService qLiveConfigService;

    private String indexHtml;


    public ViteIndexController(QLiveConfigService qLiveConfigService)
    {
        this.qLiveConfigService = qLiveConfigService;
    }


    @GetMapping("/api/bootstrap")
    public ResponseEntity<String> bootstrap()
    {
        return new ResponseEntity<>(
            JSONUtil.DEFAULT_GENERATOR.forValue(qLiveConfigService.provideConfig()),
            HttpStatus.OK
        );
    }


    @GetMapping("/")
    public String root()
    {
        return "redirect:/app/";
    }


    @RequestMapping("/app/**")
    public ResponseEntity<String> app()
    {
        final String template = loadTemplate();
        if (template == null)
        {
            return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        }

        final String data = JSONUtil.DEFAULT_GENERATOR.forValue(qLiveConfigService.provideConfig());
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
