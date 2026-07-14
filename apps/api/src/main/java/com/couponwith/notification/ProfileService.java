package com.couponwith.notification;

import com.couponwith.common.ApiException;import com.couponwith.identity.AuthService;import com.couponwith.identity.UserAccount;import com.couponwith.identity.UserRepository;import com.couponwith.space.*;
import org.springframework.http.HttpStatus;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;import java.util.UUID;

@Service
public class ProfileService {
    private final UserRepository users;private final PasswordEncoder passwordEncoder;private final SpaceRepository spaces;private final SpaceMemberRepository members;private final InvitationRepository invitations;
    public ProfileService(UserRepository users,PasswordEncoder passwordEncoder,SpaceRepository spaces,SpaceMemberRepository members,InvitationRepository invitations){this.users=users;this.passwordEncoder=passwordEncoder;this.spaces=spaces;this.members=members;this.invitations=invitations;}
    @Transactional public AuthService.UserView update(UUID userId,String displayName,String timezone){var user=requireActive(userId);try{ZoneId.of(timezone);}catch(Exception e){throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_TIMEZONE","올바른 시간대를 입력해 주세요.");}user.updateProfile(displayName.trim(),timezone);return new AuthService.UserView(user.getId(),user.getEmail(),user.getDisplayName(),user.getTimezone());}
    @Transactional public void delete(UUID userId,String password){var user=requireActive(userId);if(!passwordEncoder.matches(password,user.getPasswordHash()))throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_PASSWORD","비밀번호가 올바르지 않습니다.");for(var space:spaces.findByOwnerUserId(userId)){space.archive();members.findBySpaceIdAndStatusOrderByJoinedAt(space.getId(),"ACTIVE").forEach(SpaceMember::remove);invitations.findBySpaceIdOrderByCreatedAtDesc(space.getId()).stream().filter(Invitation::isActive).forEach(Invitation::revoke);}members.findByUserIdAndStatusOrderByJoinedAt(userId,"ACTIVE").forEach(SpaceMember::remove);user.deleteAccount(passwordEncoder.encode(UUID.randomUUID().toString()));}
    private UserAccount requireActive(UUID id){return users.findById(id).filter(UserAccount::isActive).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"USER_NOT_FOUND","사용자를 찾을 수 없습니다."));}
}
