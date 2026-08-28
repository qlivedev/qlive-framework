package dev.qlive.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreetingTest {

    @Test
    void greetsGivenName() {
        assertEquals("Hello, Ada, from qlive backend-lib!", Greeting.forName("Ada"));
    }

    @Test
    void fallsBackToWorldWhenNameMissing() {
        assertEquals("Hello, world, from qlive backend-lib!", Greeting.forName(null));
    }
}
