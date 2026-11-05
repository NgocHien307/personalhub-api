package com.personalhub.dashboard.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardPageController {
    @GetMapping(value = {"/dashboard", "/dashboard/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> dashboard() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ClassPathResource("static/dashboard/index.html"));
    }
}
