package com.dataciders.app.wiring;

import com.dataciders.backend.Greeting;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public String hello(@RequestParam(defaultValue = "") String name) {
        return Greeting.forName(name);
    }
}
