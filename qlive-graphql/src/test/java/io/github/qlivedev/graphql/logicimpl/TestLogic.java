package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLField;
import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.beans.ComplexInput;
import io.github.qlivedev.graphql.testdomain.Tables;
import io.github.qlivedev.graphql.testdomain.tables.pojos.SourceEight;
import io.github.qlivedev.graphql.testdomain.tables.pojos.SourceThree;
import io.github.qlivedev.graphql.testdomain.tables.pojos.TargetEight;
import io.github.qlivedev.graphql.testdomain.tables.pojos.TargetFive;
import io.github.qlivedev.graphql.testdomain.tables.pojos.TargetNine;
import io.github.qlivedev.graphql.testdomain.tables.pojos.TargetNineCounts;
import io.github.qlivedev.graphql.testdomain.tables.pojos.TargetSix;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;

@GraphQLLogic
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


    @GraphQLQuery
    public boolean queryTruth()
    {
        return true;
    }


    @GraphQLQuery
    public String queryString(String value)
    {
        return "VALUE:" + value;
    }


    @GraphQLQuery
    public String queryString2(@GraphQLField(notNull = true) String value, @GraphQLField(notNull = true) String second)
    {
        return value + ":" + second;
    }


    @GraphQLQuery
    public int queryInt(int value)
    {
        return value * 2;
    }


    @GraphQLQuery
    public Timestamp queryTimestamp()
    {
        return new Timestamp(System.currentTimeMillis());
    }


    @GraphQLQuery
    public boolean queryWithComplexInput(ComplexInput complexInput)
    {
        return false;
    }


    @GraphQLMutation
    public String mutateString(String value)
    {
        return "<<" + value + ">>";
    }


    @GraphQLQuery
    public List<SourceThree> walkForwardRef()
    {
        return dslContext.select()
            .from(Tables.SOURCE_THREE)
            .fetchInto(SourceThree.class);
    }


    @GraphQLQuery
    public List<TargetSix> walkBackMany()
    {
        final List<TargetSix> targets = dslContext.select()
            .from(Tables.TARGET_SIX)
            .fetchInto(TargetSix.class);

        //log.info("targets: {}", targets);

        return targets;
    }


    @GraphQLQuery
    public TargetFive walkBackOne()
    {
        final TargetFive target = dslContext.select()
            .from(Tables.TARGET_FIVE)
            .fetchOneInto(TargetFive.class);

        //log.info("target: {}", target);

        return target;
    }


    @GraphQLQuery
    public List<SourceEight> walkMultiKey()
    {
        final List<SourceEight> sourceEights = dslContext.select()
            .from(Tables.SOURCE_EIGHT)
            .fetchInto(SourceEight.class);

        //log.info("target: {}", target);

        return sourceEights;
    }


    @GraphQLQuery
    public List<TargetEight> walkMultiKeyBackWards()
    {
        final List<TargetEight> targetEights = dslContext.select()
            .from(Tables.TARGET_EIGHT)
            .fetchInto(TargetEight.class);

        //log.info("target: {}", target);

        return targetEights;
    }


    @GraphQLQuery
    public List<TargetNineCounts> walkViewPojoRelation()
    {
        final List<TargetNineCounts> targetNineCounts = dslContext.select()
            .from(Tables.TARGET_NINE_COUNTS)
            .fetchInto(TargetNineCounts.class);

        //log.info("target: {}", target);

        return targetNineCounts;
    }


    @GraphQLQuery
    public List<TargetNine> walkViewPojoRelationBackwards()
    {
        final List<TargetNine> targetNines = dslContext.select()
            .from(Tables.TARGET_NINE)
            .fetchInto(TargetNine.class);

        //log.info("target: {}", target);

        return targetNines;
    }
}
