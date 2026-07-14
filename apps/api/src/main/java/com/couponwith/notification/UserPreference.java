package com.couponwith.notification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="user_preferences")
public class UserPreference {
    @Id @Column(name="user_id") private UUID userId;
    @Column(name="app_notifications",nullable=false) private boolean appNotifications;
    @Column(name="email_notifications",nullable=false) private boolean emailNotifications;
    @Column(name="event_reminders",nullable=false) private boolean eventReminders;
    @Column(name="post_activity",nullable=false) private boolean postActivity;
    @Column(name="coupon_activity",nullable=false) private boolean couponActivity;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected UserPreference(){} public UserPreference(UUID userId){this.userId=userId;update(true,false,true,true,true);}
    public void update(boolean app,boolean email,boolean events,boolean posts,boolean coupons){appNotifications=app;emailNotifications=email;eventReminders=events;postActivity=posts;couponActivity=coupons;updatedAt=Instant.now();}
    public UUID getUserId(){return userId;} public boolean isAppNotifications(){return appNotifications;} public boolean isEmailNotifications(){return emailNotifications;} public boolean isEventReminders(){return eventReminders;} public boolean isPostActivity(){return postActivity;} public boolean isCouponActivity(){return couponActivity;}
}
