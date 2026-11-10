package com.king.esb.app.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the admin console entry point for the bare {@code /console} and {@code /console/} paths. */
@Controller
public class ConsoleForwardController {

    @GetMapping({"/console", "/console/"})
    public String console() {
        return "forward:/console/index.html";
    }
}
