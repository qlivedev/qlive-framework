package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.fetcher.FetcherContext;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceFive;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceSix;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.TargetFive;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.TargetSix;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.TargetThree;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceThree;
import org.atteo.evo.inflector.English;

import java.util.Collections;

@QLiveLogic
public class FetcherContextLogic
{
    @QLiveQuery
    public SourceThree sourceThreeWithFetcherContext()
    {
        final SourceThree sourceThree = new SourceThree();

        sourceThree.setId("source-three-0001");

        final TargetThree targetThree = new TargetThree();
        targetThree.setId("target-three-fetch-context");

        final FetcherContext fetcherContext = new FetcherContext();
        fetcherContext.setProperty("target", targetThree);
        sourceThree.provideFetcherContext(fetcherContext);

        return sourceThree;
    }

    @QLiveQuery
    public TargetFive targetFiveWithFetcherContext()
    {
        final TargetFive targetFive = new TargetFive();

        targetFive.setId("target-five-0001");

        final SourceFive sourceFive = new SourceFive();
        sourceFive.setId("source-five-fetch-context");

        final FetcherContext fetcherContext = new FetcherContext();
        fetcherContext.setProperty("sourceFive", sourceFive);
        targetFive.provideFetcherContext(fetcherContext);

        return targetFive;
    }

    @QLiveQuery
    public TargetSix targetSixWithFetcherContext()
    {
        final TargetSix targetSix = new TargetSix();

        targetSix.setId("target-six-0001");

        final SourceSix sourceSix = new SourceSix();
        sourceSix.setId("source-six-fetch-context");

        final FetcherContext fetcherContext = new FetcherContext();
        fetcherContext.setProperty("sourceSixes", Collections.singletonList(sourceSix));
        targetSix.provideFetcherContext(fetcherContext);

        return targetSix;
    }
}
