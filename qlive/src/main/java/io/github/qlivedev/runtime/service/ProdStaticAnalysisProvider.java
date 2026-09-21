package io.github.qlivedev.runtime.service;

import io.github.qlivedev.model.ts.TrackUsageData;
import io.github.qlivedev.util.JSONUtil;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/// Provides a production implementation of {@link StaticAnalysisProvider} that reads static build output data
/// from a classpath resource.
///
/// This is also where an application's use of the analysis is checked, because here the whole of it is
/// present before the first request: a build whose injections are not where they belong fails at startup
/// rather than on the page that happens to hit the mistake.
///
/// @see DevStaticAnalysisProvider
///
public class ProdStaticAnalysisProvider
    implements StaticAnalysisProvider
{
    /// Where the Maven build puts Vite's output, i.e. what `frontend/dist` is on the classpath, plus the file
    /// name the track-usage plugin writes there.
    public final static String DEFAULT_RESOURCE = "static/track-usage.json";

    private final TrackUsageData trackUsageData;


    public ProdStaticAnalysisProvider()
    {
        this(DEFAULT_RESOURCE);
    }


    public ProdStaticAnalysisProvider(String resource)
    {
        this.trackUsageData = read(resource);

        final List<String> outsideViews = InjectionService.injectionsOutsideViews(trackUsageData);
        if (!outsideViews.isEmpty())
        {
            throw new IllegalStateException(InjectionService.describeInjectionsOutsideViews(outsideViews));
        }
    }


    @Override
    public TrackUsageData getTrackUsageData()
    {
        return trackUsageData;
    }


    private static TrackUsageData read(String resource)
    {
        try
        {
            final String json = new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);

            return JSONUtil.DEFAULT_PARSER.parse(TrackUsageData.class, json);
        }
        catch (IOException | RuntimeException e)
        {
            throw new IllegalStateException(
                "Could not read " + resource + " -- has the frontend been built (pnpm build), and does its " +
                    "Vite config declare the track-usage plugin?",
                e
            );
        }
    }
}
