package com.soham.ratelimiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class DemoController {

    @GetMapping("/api/ping")
    public String ping() {
        return "pong at " + Instant.now();
    }

    @GetMapping("/api/data")
    public String data() {
        return "here is some protected data, served at " + Instant.now();
    }
}
