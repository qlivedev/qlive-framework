package com.dataciders.backend;

public final class Greeting {

    private Greeting() {
    }

    public static String forName(String name) {
        String who = (name == null || name.isBlank()) ? "world" : name;
        return "Hello, " + who + ", from qlive!";
    }
}
