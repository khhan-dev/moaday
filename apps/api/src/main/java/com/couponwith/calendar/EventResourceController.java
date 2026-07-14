package com.couponwith.calendar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class EventResourceController {
    private final EventResourceService service;

    public EventResourceController(EventResourceService service) { this.service = service; }

    @GetMapping("/spaces/{spaceId}/linkable-resources")
    List<EventResourceService.ResourceView> listLinkable(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID spaceId) {
        return service.listLinkable(userId(jwt), spaceId);
    }

    @GetMapping("/events/{eventId}/resources")
    List<EventResourceService.ResourceView> listEventResources(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return service.listEventResources(userId(jwt), eventId);
    }

    @PutMapping("/events/{eventId}/resources")
    List<EventResourceService.ResourceView> replace(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId,
                                                    @Valid @RequestBody ResourceLinksRequest request) {
        return service.replace(userId(jwt), eventId, request.toServiceResources());
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    record ResourceLinksRequest(@NotNull @Size(max = 50) List<@Valid ResourceReferenceRequest> resources) {
        List<EventResourceService.ResourceReference> toServiceResources() {
            return resources.stream().map(item -> new EventResourceService.ResourceReference(item.type(), item.resourceId())).toList();
        }
    }

    record ResourceReferenceRequest(@NotNull EventResourceType type, @NotNull UUID resourceId) {}
}
