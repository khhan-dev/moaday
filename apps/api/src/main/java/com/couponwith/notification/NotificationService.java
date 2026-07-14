package com.couponwith.notification;

import com.couponwith.common.ApiException;import com.couponwith.identity.UserRepository;import com.couponwith.space.SpaceMemberRepository;
import org.springframework.http.HttpStatus;import org.springframework.mail.MailException;import org.springframework.mail.SimpleMailMessage;import org.springframework.mail.javamail.JavaMailSender;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.util.List;import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository notifications;private final UserPreferenceRepository preferences;private final UserRepository users;private final SpaceMemberRepository members;private final JavaMailSender mailSender;
    public NotificationService(NotificationRepository notifications,UserPreferenceRepository preferences,UserRepository users,SpaceMemberRepository members,JavaMailSender mailSender){this.notifications=notifications;this.preferences=preferences;this.users=users;this.members=members;this.mailSender=mailSender;}
    @Transactional public void notifyUser(UUID userId,UUID spaceId,String type,String title,String message,String link){var pref=getPreference(userId);if(!categoryEnabled(pref,type))return;if(pref.isAppNotifications())notifications.save(new Notification(UUID.randomUUID(),userId,spaceId,type,title,message,link));if(pref.isEmailNotifications())sendEmail(userId,title,message);}
    @Transactional public void notifySpace(UUID spaceId,UUID excludedUserId,String type,String title,String message,String link){members.findBySpaceIdAndStatusOrderByJoinedAt(spaceId,"ACTIVE").stream().map(member->member.getUserId()).filter(id->!id.equals(excludedUserId)).forEach(id->notifyUser(id,spaceId,type,title,message,link));}
    @Transactional(readOnly=true) public List<NotificationView> list(UUID userId){return notifications.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream().map(NotificationView::from).toList();}
    @Transactional(readOnly=true) public long unreadCount(UUID userId){return notifications.countByUserIdAndReadAtIsNull(userId);}
    @Transactional public NotificationView read(UUID userId,UUID id){var item=notifications.findById(id).filter(n->n.getUserId().equals(userId)).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"NOTIFICATION_NOT_FOUND","알림을 찾을 수 없습니다."));item.read();return NotificationView.from(item);}
    @Transactional public void readAll(UUID userId){notifications.findTop100ByUserIdOrderByCreatedAtDesc(userId).forEach(Notification::read);}
    @Transactional public PreferenceView preferences(UUID userId){return PreferenceView.from(getPreference(userId));}
    @Transactional public PreferenceView updatePreferences(UUID userId,PreferenceInput input){var pref=getPreference(userId);pref.update(input.appNotifications(),input.emailNotifications(),input.eventReminders(),input.postActivity(),input.couponActivity());return PreferenceView.from(pref);}
    private UserPreference getPreference(UUID userId){return preferences.findById(userId).orElseGet(()->preferences.save(new UserPreference(userId)));}
    private boolean categoryEnabled(UserPreference pref,String type){return type.startsWith("EVENT")?pref.isEventReminders():type.startsWith("COUPON")?pref.isCouponActivity():pref.isPostActivity();}
    private void sendEmail(UUID userId,String title,String content){users.findById(userId).ifPresent(user->{try{var mail=new SimpleMailMessage();mail.setFrom("no-reply@moaday.local");mail.setTo(user.getEmail());mail.setSubject("[MoaDay] "+title);mail.setText(content+"\n\n이 메일은 로컬 MoaDay 알림입니다.");mailSender.send(mail);}catch(MailException ignored){}});}
    public record PreferenceInput(boolean appNotifications,boolean emailNotifications,boolean eventReminders,boolean postActivity,boolean couponActivity){}
    public record PreferenceView(boolean appNotifications,boolean emailNotifications,boolean eventReminders,boolean postActivity,boolean couponActivity){static PreferenceView from(UserPreference p){return new PreferenceView(p.isAppNotifications(),p.isEmailNotifications(),p.isEventReminders(),p.isPostActivity(),p.isCouponActivity());}}
    public record NotificationView(UUID id,UUID spaceId,String type,String title,String message,String link,java.time.Instant readAt,java.time.Instant createdAt){static NotificationView from(Notification n){return new NotificationView(n.getId(),n.getSpaceId(),n.getType(),n.getTitle(),n.getMessage(),n.getLink(),n.getReadAt(),n.getCreatedAt());}}
}
