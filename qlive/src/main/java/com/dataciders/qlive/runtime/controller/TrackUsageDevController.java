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

/**
 * Receives live track-usage.json snapshots pushed by the Vite dev server's track-usage
 * plugin. {@code vite build} writes track-usage.json to disk for the backend to read, but
 * {@code vite dev} never touches disk, so the frontend tooling pushes snapshots here instead
 * as they change.
 */
@Controller
public class TrackUsageDevController
{
    private final static Logger log = LoggerFactory.getLogger(TrackUsageDevController.class);

    /**
     * Special development end point that receives pushed track-usage.json snapshots. Exempt
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


    @RequestMapping(value = TRACK_USAGE_DEV_URI, method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> update(@RequestBody String body)
    {
        try
        {
            final TrackUsageData data = JSONUtil.DEFAULT_PARSER.parse(TrackUsageData.class, body);

            // Published before the codegen runs: the typing service debounces and writes files, while
            // everything reading the data for the current request -- the bootstrap service above all -- wants
            // the newest snapshot as soon as it arrives.
            staticAnalysisProvider.update(data);
            graphQLQueryTypingService.triggerDebouncedUpdate(data);
            return ResponseEntity.noContent().build();
        }
        catch (Exception e)
        {
            log.error("Error processing pushed track-usage data", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
