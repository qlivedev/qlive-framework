package com.dataciders.qlive.runtime.controller;

import com.dataciders.qlive.model.ts.TrackUsageData;
import com.dataciders.qlive.runtime.domain.GraphQLQueryTypingService;
import com.dataciders.qlive.runtime.service.DevStaticAnalysisProvider;
import de.quinscape.spring.jsview.util.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Receives the live track-usage analysis pushed by the Vite dev server's track-usage plugin.
 * {@code vite build} writes track-usage.json to disk for the backend to read, but {@code vite dev}
 * never touches disk, so the frontend tooling pushes the analysis here instead as it changes.
 * <p>
 * A push carries only the modules one editing round changed, which is what the plugin has anyway and
 * saves both halves the whole analysis per keystroke.
 */
@Controller
public class TrackUsageDevController
{
    private final static Logger log = LoggerFactory.getLogger(TrackUsageDevController.class);

    /**
     * Special development end point that receives pushed track-usage analysis. Exempt
     * from CSRF protection requirements, like other "/_dev/**" endpoints.
     */
    public final static String TRACK_USAGE_DEV_URI = "/_dev/track-usage";

    private final GraphQLQueryTypingService graphQLQueryTypingService;

    private final DevStaticAnalysisProvider staticAnalysisProvider;


    public TrackUsageDevController(
        GraphQLQueryTypingService graphQLQueryTypingService,
        DevStaticAnalysisProvider staticAnalysisProvider
    )
    {
        this.graphQLQueryTypingService = graphQLQueryTypingService;
        this.staticAnalysisProvider = staticAnalysisProvider;
    }


    /**
     * Applies one pushed update.
     *
     * @param body  track-usage JSON holding the changed modules
     * @param full  true if the body is the complete analysis rather than a slice of it
     *
     * @return no content, or {@link HttpStatus#CONFLICT} if there is nothing to merge a slice into, which
     * asks the plugin to push the analysis in full
     */
    @RequestMapping(value = TRACK_USAGE_DEV_URI, method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(
        @RequestBody
        String body,
        @RequestParam(value = "full", defaultValue = "false")
        boolean full
    )
    {
        // The analysis lives in memory only, so a backend restarted mid-session has nothing a slice could be
        // merged into. Saying so is what gets the dev server to push everything it has.
        if (!full && staticAnalysisProvider.getTrackUsageData() == null)
        {
            log.info("Asking the dev server for the full track-usage analysis");
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        try
        {
            final TrackUsageData update = JSONUtil.DEFAULT_PARSER.parse(TrackUsageData.class, body);

            // Published before the codegen runs: the typing service debounces and writes files, while
            // everything reading the analysis for the current request -- the bootstrap service above all --
            // wants the update as soon as it arrives.
            if (full)
            {
                staticAnalysisProvider.replace(update);
            }
            else
            {
                staticAnalysisProvider.merge(update);
            }

            graphQLQueryTypingService.triggerDebouncedUpdate(update);
            return ResponseEntity.noContent().build();
        }
        catch (Exception e)
        {
            log.error("Error processing pushed track-usage data", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
