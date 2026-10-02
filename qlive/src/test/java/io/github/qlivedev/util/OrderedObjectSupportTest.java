package io.github.qlivedev.util;

import org.junit.jupiter.api.Test;
import org.svenson.JSONProperty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class OrderedObjectSupportTest
{
    /// Properties a `HashMap` iterates as zebra, banana, apple, kiwi, mango, plus one with a priority.
    public static class Fruits
    {
        public String getZebra() { return "z"; }
        public String getBanana() { return "b"; }
        public String getApple() { return "a"; }
        public String getKiwi() { return "k"; }
        public String getMango() { return "m"; }

        @JSONProperty(priority = 10)
        public String getType() { return "t"; }
    }


    @Test
    void writesPropertiesByPriorityThenName()
    {
        assertThat(
            JSONUtil.DEFAULT_GENERATOR.forValue(new Fruits()),
            is("{\"type\":\"t\",\"apple\":\"a\",\"banana\":\"b\",\"kiwi\":\"k\",\"mango\":\"m\",\"zebra\":\"z\"}")
        );
    }
}
