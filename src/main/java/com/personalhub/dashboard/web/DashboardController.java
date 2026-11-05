package com.personalhub.dashboard.web;

import com.personalhub.dashboard.application.DashboardOverviewService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardOverviewService service;
    public DashboardController(DashboardOverviewService service) { this.service = service; }

    @GetMapping("/overview")
    public DashboardOverviewResponse overview(@RequestParam(defaultValue = "30") int days) {
        if (days < 1 || days > 365)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be in range 1..365");
        return service.overview(days);
    }
}
