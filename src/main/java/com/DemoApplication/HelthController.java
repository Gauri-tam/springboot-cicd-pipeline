package com.DemoApplication;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HelthController {

    @RequestMapping("/check")
    public String checkHealth() {
        return "Application is healthy!";
    }
}
