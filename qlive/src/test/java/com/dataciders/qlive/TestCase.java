package com.dataciders.qlive;

import de.quinscape.spring.jsview.util.JSONUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.dataciders.qlive.runtime.scalar.FilterDSL.field;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.value;

public class TestCase
{
    private final static Logger log = LoggerFactory.getLogger(TestCase.class);


    // svenson's generic JSON dumper cannot serialize the Condition model -- it fails with a
    // CyclicStructure error -- so dumping a condition needs a custom serializer.
    @Disabled("Condition model needs a custom JSON serializer for JSONUtil.DEFAULT_GENERATOR")
    @Test
    void name()
    {
        log.info(JSONUtil.DEFAULT_GENERATOR.forValue(
            field("aaa").plus(field("bbb")).between(value(10), value(12))

        ));
    }
}
