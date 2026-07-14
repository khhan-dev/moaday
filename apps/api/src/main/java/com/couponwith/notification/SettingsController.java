package com.couponwith.notification;

import com.couponwith.identity.AuthService;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.http.HttpStatus;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.security.oauth2.jwt.Jwt;import org.springframework.web.bind.annotation.*;import java.util.List;import java.util.UUID;
@RestController @RequestMapping("/api/v1")
public class SettingsController {
    private final NotificationService notifications;private final ProfileService profiles;public SettingsController(NotificationService notifications,ProfileService profiles){this.notifications=notifications;this.profiles=profiles;}
    @GetMapping("/notifications") List<NotificationService.NotificationView> notifications(@AuthenticationPrincipal Jwt jwt){return notifications.list(userId(jwt));}
    @GetMapping("/notifications/unread-count") UnreadCount unread(@AuthenticationPrincipal Jwt jwt){return new UnreadCount(notifications.unreadCount(userId(jwt)));}
    @PostMapping("/notifications/{id}/read") NotificationService.NotificationView read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return notifications.read(userId(jwt),id);}
    @PostMapping("/notifications/read-all") @ResponseStatus(HttpStatus.NO_CONTENT) void readAll(@AuthenticationPrincipal Jwt jwt){notifications.readAll(userId(jwt));}
    @GetMapping("/preferences") NotificationService.PreferenceView preferences(@AuthenticationPrincipal Jwt jwt){return notifications.preferences(userId(jwt));}
    @PatchMapping("/preferences") NotificationService.PreferenceView preferences(@AuthenticationPrincipal Jwt jwt,@RequestBody NotificationService.PreferenceInput input){return notifications.updatePreferences(userId(jwt),input);}
    @PatchMapping("/profile") AuthService.UserView profile(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ProfileRequest input){return profiles.update(userId(jwt),input.displayName(),input.timezone());}
    @DeleteMapping("/account") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteAccount(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody DeleteAccountRequest input){profiles.delete(userId(jwt),input.password());}
    private UUID userId(Jwt jwt){return UUID.fromString(jwt.getSubject());}record UnreadCount(long count){}record ProfileRequest(@NotBlank @Size(max=40) String displayName,@NotBlank @Size(max=64) String timezone){}record DeleteAccountRequest(@NotBlank @Size(max=72) String password){}
}
