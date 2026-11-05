package com.personalhub.common;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiDocumentationController {
    @GetMapping(value = "/api/v1/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Resource openApi() {
        return new ClassPathResource("openapi/personalhub-openapi.json");
    }
}
