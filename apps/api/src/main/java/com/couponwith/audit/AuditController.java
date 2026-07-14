package com.couponwith.audit;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuditController {
    private final AuditService audits;
    public AuditController(AuditService audits){this.audits=audits;}
    @GetMapping("/spaces/{spaceId}/audit-logs") List<AuditService.AuditView> list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID spaceId){return audits.listSpace(UUID.fromString(jwt.getSubject()),spaceId);}
}
