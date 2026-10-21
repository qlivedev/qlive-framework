package io.github.qlivedev.qlivetest.runtime.service;

import io.github.qlivedev.graphql.util.JSONHolder;
import io.github.qlivedev.runtime.layout.LayoutService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/// The application's named layouts, from `layouts.json` on the classpath, the same in every locale.
///
/// A file is the simplest source there is, read once and kept. A layout with sections would need them translated
/// per locale, which these don't have; a table, an editor or a generator over the domain would serve as well.
@Service
public class ExampleLayoutService implements LayoutService {

    private final JSONHolder layouts;

    public ExampleLayoutService() {
        try {
            layouts = new JSONHolder(
                    new ClassPathResource("layouts.json").getContentAsString(StandardCharsets.UTF_8)
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public JSONHolder getLayouts(Locale locale) {
        return layouts;
    }
}
