package com.dataciders.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreetingTest {

    @Test
    void greetsGivenName() {
        assertEquals("Hello, Ada, from qlive!", Greeting.forName("Ada"));
    }

    @Test
    void fallsBackToWorldWhenNameMissing() {
        assertEquals("Hello, world, from qlive!", Greeting.forName(null));
    }
}
