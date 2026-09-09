package com.dataciders.qlivetest.runtime;

import com.dataciders.qlive.runtime.config.QLiveConfiguration;
import com.dataciders.qlivetest.runtime.config.DevConfiguration;
import com.dataciders.qlivetest.runtime.config.DomainQLConfiguration;
import com.dataciders.qlivetest.runtime.config.JOQQConfiguration;
import com.dataciders.qlivetest.runtime.config.SecurityConfiguration;
import com.dataciders.qlivetest.runtime.config.WebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
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
