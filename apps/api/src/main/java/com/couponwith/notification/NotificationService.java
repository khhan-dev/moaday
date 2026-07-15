package com.couponwith.notification;

import com.couponwith.common.ApiException;
import com.couponwith.identity.UserRepository;
import com.couponwith.mail.EmailOutboxService;
import com.couponwith.space.SpaceMemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserPreferenceRepository preferences;
    private final UserRepository users;
    private final SpaceMemberRepository members;
    private final EmailOutboxService emailOutbox;
    private final String webBaseUrl;

    public NotificationService(NotificationRepository notifications, UserPreferenceRepository preferences,
                               UserRepository users, SpaceMemberRepository members,
                               EmailOutboxService emailOutbox,
                               @Value("${moaday.web-base-url:http://localhost:3000}") String webBaseUrl) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.users = users;
        this.members = members;
        this.emailOutbox = emailOutbox;
        this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void notifyUser(UUID userId, UUID spaceId, String type, String title, String message, String link) {
        var preference = getPreference(userId);
        if (!categoryEnabled(preference, type)) return;
        if (preference.isAppNotifications()) {
            notifications.save(new Notification(UUID.randomUUID(), userId, spaceId, type, title, message, link));
        }
        if (preference.isEmailNotifications()) sendEmail(userId, spaceId, title, message, link);
    }

    @Transactional
    public void notifySpace(UUID spaceId, UUID excludedUserId, String type, String title, String message, String link) {
        members.findBySpaceIdAndStatusOrderByJoinedAt(spaceId, "ACTIVE").stream()
                .map(member -> member.getUserId()).filter(id -> !id.equals(excludedUserId))
                .forEach(id -> notifyUser(id, spaceId, type, title, message, link));
    }

    @Transactional(readOnly = true)
    public List<NotificationView> list(UUID userId) {
        return notifications.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream().map(NotificationView::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) { return notifications.countByUserIdAndReadAtIsNull(userId); }

    @Transactional
    public NotificationView read(UUID userId, UUID id) {
        var item = notifications.findById(id).filter(notification -> notification.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."));
        item.read();
        return NotificationView.from(item);
    }

    @Transactional
    public void readAll(UUID userId) {
        notifications.findTop100ByUserIdOrderByCreatedAtDesc(userId).forEach(Notification::read);
    }

    @Transactional
    public PreferenceView preferences(UUID userId) { return PreferenceView.from(getPreference(userId)); }

    @Transactional
    public PreferenceView updatePreferences(UUID userId, PreferenceInput input) {
        var preference = getPreference(userId);
        preference.update(input.appNotifications(), input.emailNotifications(), input.eventReminders(),
                input.postActivity(), input.couponActivity());
        return PreferenceView.from(preference);
    }

    private UserPreference getPreference(UUID userId) {
        return preferences.findById(userId).orElseGet(() -> preferences.save(new UserPreference(userId)));
    }

    private boolean categoryEnabled(UserPreference preference, String type) {
        return type.startsWith("EVENT") ? preference.isEventReminders()
                : type.startsWith("COUPON") ? preference.isCouponActivity() : preference.isPostActivity();
    }

    private void sendEmail(UUID userId, UUID spaceId, String title, String content, String link) {
        users.findById(userId).ifPresent(user -> {
            var target = publicUrl(link, spaceId);
            var body = content + (target == null ? "" : "\n\n자세히 보기: " + target)
                    + "\n\nMoaDay 알림 설정에서 이메일 수신 여부를 변경할 수 있습니다.";
            emailOutbox.enqueue(spaceId, null, "ACTIVITY", user.getEmail(), "[MoaDay] " + title, body);
        });
    }

    private String publicUrl(String link, UUID spaceId) {
        if (link == null || link.isBlank()) return null;
        try {
            var uri = URI.create(link);
            if (uri.isAbsolute()) return uri.toString();
            var parts = uri.getPath().split("/");
            if (parts.length == 3) {
                var type = switch (parts[1]) {
                    case "events" -> "EVENT";
                    case "posts" -> "POST";
                    case "coupons" -> "COUPON";
                    default -> null;
                };
                if (type != null) return webBaseUrl + "/?space=" + spaceId + "&detail=" + type + ":" + parts[2];
            }
            return webBaseUrl;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record PreferenceInput(boolean appNotifications, boolean emailNotifications, boolean eventReminders,
                                  boolean postActivity, boolean couponActivity) {}

    public record PreferenceView(boolean appNotifications, boolean emailNotifications, boolean eventReminders,
                                 boolean postActivity, boolean couponActivity) {
        static PreferenceView from(UserPreference preference) {
            return new PreferenceView(preference.isAppNotifications(), preference.isEmailNotifications(),
                    preference.isEventReminders(), preference.isPostActivity(), preference.isCouponActivity());
        }
    }

    public record NotificationView(UUID id, UUID spaceId, String type, String title, String message, String link,
                                   java.time.Instant readAt, java.time.Instant createdAt) {
        static NotificationView from(Notification notification) {
            return new NotificationView(notification.getId(), notification.getSpaceId(), notification.getType(),
                    notification.getTitle(), notification.getMessage(), notification.getLink(), notification.getReadAt(), notification.getCreatedAt());
        }
    }
}
