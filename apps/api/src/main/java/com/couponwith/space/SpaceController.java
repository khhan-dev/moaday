package com.couponwith.space;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SpaceController {
    private final SpaceService spaceService;

    public SpaceController(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    @GetMapping("/spaces")
    List<SpaceService.SpaceView> list(@AuthenticationPrincipal Jwt jwt) {
        return spaceService.list(userId(jwt));
    }

    @PostMapping("/spaces")
    @ResponseStatus(HttpStatus.CREATED)
    SpaceService.SpaceView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateSpaceRequest request) {
        return spaceService.create(userId(jwt), request.type(), request.name(), request.timezone(), request.color());
    }

    @PostMapping("/spaces/{spaceId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    SpaceService.InvitationView invite(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID spaceId,
                                       @Valid @RequestBody InviteRequest request, HttpServletRequest httpRequest) {
        return spaceService.invite(userId(jwt), spaceId, request.email(), request.role(), webBaseUrl(httpRequest));
    }

    @PostMapping("/invitations/accept")
    SpaceService.SpaceView accept(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AcceptInvitationRequest request) {
        return spaceService.accept(userId(jwt), request.token());
    }

    @GetMapping("/invitations")
    List<SpaceService.ReceivedInvitationView> receivedInvitations(@AuthenticationPrincipal Jwt jwt) {
        return spaceService.listReceivedInvitations(userId(jwt));
    }

    @PostMapping("/invitations/{invitationId}/accept")
    SpaceService.SpaceView acceptReceivedInvitation(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID invitationId) {
        return spaceService.acceptReceivedInvitation(userId(jwt), invitationId);
    }

    @PostMapping("/invitations/{invitationId}/decline")
    SpaceService.InvitationSummaryView declineReceivedInvitation(@AuthenticationPrincipal Jwt jwt,
                                                                 @PathVariable UUID invitationId) {
        return spaceService.declineReceivedInvitation(userId(jwt), invitationId);
    }

    @GetMapping("/spaces/{spaceId}/members")
    List<SpaceService.MemberView> listMembers(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID spaceId) {
        return spaceService.listMembers(userId(jwt), spaceId);
    }

    @PatchMapping("/spaces/{spaceId}/members/{memberUserId}")
    SpaceService.MemberView changeMemberRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID spaceId,
                                             @PathVariable UUID memberUserId,
                                             @Valid @RequestBody ChangeMemberRoleRequest request) {
        return spaceService.changeMemberRole(userId(jwt), spaceId, memberUserId, request.role());
    }

    @DeleteMapping("/spaces/{spaceId}/members/{memberUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID spaceId,
                      @PathVariable UUID memberUserId) {
        spaceService.removeMember(userId(jwt), spaceId, memberUserId);
    }

    @DeleteMapping("/spaces/{spaceId}/membership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leaveSpace(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID spaceId) {
        spaceService.leave(userId(jwt), spaceId);
    }

    @DeleteMapping("/spaces/{spaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archiveSpace(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID spaceId) {
        spaceService.archive(userId(jwt), spaceId);
    }

    @GetMapping("/spaces/{spaceId}/invitations")
    List<SpaceService.InvitationSummaryView> listInvitations(@AuthenticationPrincipal Jwt jwt,
                                                             @PathVariable UUID spaceId) {
        return spaceService.listInvitations(userId(jwt), spaceId);
    }

    @DeleteMapping("/spaces/{spaceId}/invitations/{invitationId}")
    SpaceService.InvitationSummaryView revokeInvitation(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID spaceId,
                                                        @PathVariable UUID invitationId) {
        return spaceService.revokeInvitation(userId(jwt), spaceId, invitationId);
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    private String webBaseUrl(HttpServletRequest request) {
        var proto = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));
        if (!"http".equalsIgnoreCase(proto) && !"https".equalsIgnoreCase(proto)) proto = request.getScheme();
        var host = firstForwardedValue(request.getHeader("X-Forwarded-Host"));
        if (host == null || host.isBlank()) host = request.getHeader("Host");
        if (host == null || host.isBlank() || host.contains("\r") || host.contains("\n")) {
            throw new IllegalArgumentException("초대 링크 주소를 확인할 수 없습니다.");
        }
        return proto.toLowerCase() + "://" + host;
    }

    private String firstForwardedValue(String value) {
        if (value == null || value.isBlank()) return null;
        return value.split(",", 2)[0].trim();
    }

    record CreateSpaceRequest(@NotNull SpaceType type, @NotBlank @Size(max = 60) String name,
                              @NotBlank String timezone, @NotBlank String color) {}
    record InviteRequest(@Email @NotBlank String email, @NotNull SpaceRole role) {}
    record AcceptInvitationRequest(@NotBlank String token) {}
    record ChangeMemberRoleRequest(@NotNull SpaceRole role) {}
}
