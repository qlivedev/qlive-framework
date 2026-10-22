package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveField;
import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.ComplexInput;
import io.github.qlivedev.qlive.graphql.testdomain.Tables;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceEight;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.SourceThree;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.TargetEight;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.TargetFive;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.TargetNine;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.TargetNineCounts;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.TargetSix;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;

@QLiveLogic
public class TestLogic
{
    private final static Logger log = LoggerFactory.getLogger(TestLogic.class);

    private final DSLContext dslContext;


    public TestLogic()
    {
        this(null);
    }


    public TestLogic(DSLContext dslContext)
    {
        this.dslContext = dslContext;
    }


    @QLiveQuery
    public boolean queryTruth()
    {
        return true;
    }


    @QLiveQuery
    public String queryString(String value)
    {
        return "VALUE:" + value;
    }


    @QLiveQuery
    public String queryString2(@QLiveField(notNull = true) String value, @QLiveField(notNull = true) String second)
    {
        return value + ":" + second;
    }


    @QLiveQuery
    public int queryInt(int value)
    {
        return value * 2;
    }


    @QLiveQuery
    public Timestamp queryTimestamp()
    {
        return new Timestamp(System.currentTimeMillis());
    }


    @QLiveQuery
    public boolean queryWithComplexInput(ComplexInput complexInput)
    {
        return false;
    }


    @QLiveMutation
    public String mutateString(String value)
    {
        return "<<" + value + ">>";
    }


    @QLiveQuery
    public List<SourceThree> walkForwardRef()
    {
        return dslContext.select()
            .from(Tables.SOURCE_THREE)
            .fetchInto(SourceThree.class);
    }


    @QLiveQuery
    public List<TargetSix> walkBackMany()
    {
        final List<TargetSix> targets = dslContext.select()
            .from(Tables.TARGET_SIX)
            .fetchInto(TargetSix.class);

        //log.info("targets: {}", targets);

        return targets;
    }


    @QLiveQuery
    public TargetFive walkBackOne()
    {
        final TargetFive target = dslContext.select()
            .from(Tables.TARGET_FIVE)
            .fetchOneInto(TargetFive.class);

        //log.info("target: {}", target);

        return target;
    }


    @QLiveQuery
    public List<SourceEight> walkMultiKey()
    {
        final List<SourceEight> sourceEights = dslContext.select()
            .from(Tables.SOURCE_EIGHT)
            .fetchInto(SourceEight.class);

        //log.info("target: {}", target);

        return sourceEights;
    }


    @QLiveQuery
    public List<TargetEight> walkMultiKeyBackWards()
    {
        final List<TargetEight> targetEights = dslContext.select()
            .from(Tables.TARGET_EIGHT)
            .fetchInto(TargetEight.class);

        //log.info("target: {}", target);

        return targetEights;
    }


    @QLiveQuery
    public List<TargetNineCounts> walkViewPojoRelation()
    {
        final List<TargetNineCounts> targetNineCounts = dslContext.select()
            .from(Tables.TARGET_NINE_COUNTS)
            .fetchInto(TargetNineCounts.class);

        //log.info("target: {}", target);

        return targetNineCounts;
    }


    @QLiveQuery
    public List<TargetNine> walkViewPojoRelationBackwards()
    {
        final List<TargetNine> targetNines = dslContext.select()
            .from(Tables.TARGET_NINE)
            .fetchInto(TargetNine.class);

        //log.info("target: {}", target);

        return targetNines;
    }
}
