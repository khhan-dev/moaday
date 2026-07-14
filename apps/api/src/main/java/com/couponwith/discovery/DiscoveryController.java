package com.couponwith.discovery;

import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1")
public class DiscoveryController {
    private final DiscoveryService service;

    public DiscoveryController(DiscoveryService service) { this.service = service; }

    @GetMapping("/dashboard")
    DiscoveryService.DashboardView dashboard(@AuthenticationPrincipal Jwt jwt) {
        return service.dashboard(userId(jwt));
    }

    @GetMapping("/search")
    List<DiscoveryService.SearchResult> search(@AuthenticationPrincipal Jwt jwt,
                                               @RequestParam @Size(min = 2, max = 100) String query) {
        return service.search(userId(jwt), query);
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
