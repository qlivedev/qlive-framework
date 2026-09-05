package com.dataciders.qlive.runtime.controller;

import com.dataciders.qlive.model.bootstrap.Injection;
import com.dataciders.qlive.model.bootstrap.QLiveBoostrap;
import com.dataciders.qlive.runtime.service.BootstrapService;
import com.dataciders.qlive.runtime.view.VitePageRenderer;
import de.quinscape.spring.jsview.util.JSONUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Serves the application under {@code /app/**} via {@link VitePageRenderer}, which splices the current
 * {@link com.dataciders.qlive.model.bootstrap.QLiveConfig} into Vite's build output. In {@code vite dev},
 * nobody touches that template, so the placeholder stays empty and the frontend falls back to fetching the
 * same data live from {@link #bootstrap(String)}.
 */
@Controller
public class ViteIndexController
{
    /**
     * Vite's {@code build.assetsDir}, i.e. the directory below the build output that the emitted chunks land
     * in. Its default; an application that reconfigures it has to keep this in sync.
     */
    private final static String ASSETS_DIR = "assets";

    /**
     * Vite's default entry point, i.e. the application's own {@code index.html}.
     */
    private final static String INDEX_ENTRY_POINT = "index.html";

    private final BootstrapService bootstrapService;

    private final VitePageRenderer vitePageRenderer;


    public ViteIndexController(BootstrapService bootstrapService, VitePageRenderer vitePageRenderer)
    {
        this.bootstrapService = bootstrapService;
        this.vitePageRenderer = vitePageRenderer;
    }


    @GetMapping("/api/bootstrap")
    public ResponseEntity<String> bootstrap(
        @RequestParam(value = "path") String path,
        CsrfToken csrfToken
    )
    {
        final QLiveBoostrap bs = bootstrapService.provideConfig(csrfToken, path);
        if (bs == null)
        {
            return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        }

        return new ResponseEntity<>(
            JSONUtil.DEFAULT_GENERATOR.forValue(bs),
            HttpStatus.OK
        );
    }


    /**
     * Resolves the injections for the given path
     *
     * @param path  path within the application
     * @return new data injection map as JOSN
     */
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
        @PathVariable String path,
        CsrfToken csrfToken
    )
    {
        return vitePageRenderer.render(INDEX_ENTRY_POINT, path, csrfToken);
    }
}
