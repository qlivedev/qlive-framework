package dev.qlive.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the framework-wiring layer only. Keep this class, and everything
 * it transitively imports under {@code dev.qlive.app.wiring}, buildable as a
 * standalone "hello world" of the framework - it is what later gets extracted
 * into the end-user template.
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
