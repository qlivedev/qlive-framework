package io.github.qlivedev.qlivetest.runtime;

import io.github.qlivedev.runtime.config.QLiveConfiguration;
import io.github.qlivedev.qlivetest.runtime.config.DevConfiguration;
import io.github.qlivedev.qlivetest.runtime.config.DomainQLConfiguration;
import io.github.qlivedev.qlivetest.runtime.config.JOQQConfiguration;
import io.github.qlivedev.qlivetest.runtime.config.SecurityConfiguration;
import io.github.qlivedev.qlivetest.runtime.config.WebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * <p>
 *     Component scan covers the two packages whose contents are found by type rather than named: {@code logic}
 *     holds the {@code @GraphQLLogic} beans {@link DomainQLConfiguration} collects with
 *     {@code getBeansWithAnnotation()}, and {@code service} holds the application's own services.
 * </p>
 * <p>
 *     Everything else is declared explicitly -- configurations here, controllers and metadata providers as
 *     {@code @Bean} methods next to what they belong to. Nothing reaches the context without something naming
 *     it, which is also what a module's beans have to do: a module lives outside every package scanned here
 *     and could never be found by widening this list.
 * </p>
 */
@SpringBootApplication(
	scanBasePackages = {
		"io.github.qlivedev.qlivetest.runtime.logic",
		"io.github.qlivedev.qlivetest.runtime.service"
	}
)
@Import({
	JOQQConfiguration.class,
	DevConfiguration.class,
	DomainQLConfiguration.class,
	QLiveConfiguration.class,
	SecurityConfiguration.class,
	WebConfiguration.class
})
public class QLiveTestApplication {

	public static void main(String[] args) {
		SpringApplication.run(QLiveTestApplication.class, args);
	}

}
