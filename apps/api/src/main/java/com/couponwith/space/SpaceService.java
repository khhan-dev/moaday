package com.couponwith.space;

import com.couponwith.audit.AuditService;
import com.couponwith.common.ApiException;
import com.couponwith.identity.UserRepository;
import com.couponwith.mail.EmailOutboxService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SpaceService {
    private final SpaceRepository spaces;
    private final SpaceMemberRepository members;
    private final InvitationRepository invitations;
    private final UserRepository users;
    private final AuditService audits;
    private final InvitationMailService invitationMail;
    private final EmailOutboxService emailOutbox;
    private final SecureRandom secureRandom = new SecureRandom();

    public SpaceService(SpaceRepository spaces, SpaceMemberRepository members,
                        InvitationRepository invitations, UserRepository users, AuditService audits,
                        InvitationMailService invitationMail, EmailOutboxService emailOutbox) {
        this.spaces = spaces;
        this.members = members;
        this.invitations = invitations;
        this.users = users;
        this.audits = audits;
        this.invitationMail = invitationMail;
        this.emailOutbox = emailOutbox;
    }

    @Transactional(readOnly = true)
    public List<SpaceView> list(UUID userId) {
        return members.findByUserIdAndStatusOrderByJoinedAt(userId, "ACTIVE").stream()
                .map(member -> {
                    var space = spaces.findById(member.getSpaceId())
                            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "공간을 찾을 수 없습니다."));
                    return SpaceView.from(space, member.getRole());
                }).toList();
    }

    @Transactional
    public SpaceView create(UUID userId, SpaceType type, String name, String timezone, String color) {
        if (type == SpaceType.PERSONAL) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SPACE_TYPE", "개인 공간은 자동으로 생성됩니다.");
        }
        requireUser(userId);
        var space = spaces.save(new Space(UUID.randomUUID(), type, name.trim(), userId, timezone, color));
        members.save(new SpaceMember(space.getId(), userId, SpaceRole.OWNER));
        return SpaceView.from(space, SpaceRole.OWNER);
    }

    @Transactional
    public InvitationView invite(UUID actorId, UUID spaceId, String rawEmail, SpaceRole role) {
        return invite(actorId, spaceId, rawEmail, role, null);
    }

    @Transactional
    public InvitationView invite(UUID actorId, UUID spaceId, String rawEmail, SpaceRole role, String webBaseUrl) {
        var actor = requireMembership(spaceId, actorId);
        if (actor.getRole() != SpaceRole.OWNER && actor.getRole() != SpaceRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVITE_NOT_ALLOWED", "구성원을 초대할 권한이 없습니다.");
        }
        if (role == SpaceRole.OWNER) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_INVITE_ROLE", "초대 단계에서는 소유자 역할을 부여할 수 없습니다.");
        }
        if (actor.getRole() == SpaceRole.ADMIN && role == SpaceRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_INVITE_NOT_ALLOWED", "관리자 지정은 소유자만 할 수 있습니다.");
        }
        var email = rawEmail.trim().toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (members.findBySpaceIdAndUserIdAndStatus(spaceId, user.getId(), "ACTIVE").isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "MEMBER_ALREADY_EXISTS", "이미 공간에 참여 중인 구성원입니다.");
            }
        });
        if (invitations.existsBySpaceIdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNullAndDeclinedAtIsNullAndExpiresAtAfter(
                spaceId, email, Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_INVITATION_EXISTS", "이미 대기 중인 초대가 있습니다.");
        }
        var rawToken = generateToken();
        var invitation = invitations.save(new Invitation(UUID.randomUUID(), spaceId, email, role,
                hash(rawToken), actorId, Instant.now().plus(Duration.ofDays(7))));
        var space = requireSpace(spaceId);
        var inviter = requireUser(actorId);
        var invitationUrl = webBaseUrl == null ? null
                : webBaseUrl.replaceAll("/+$", "") + "/?invite=" + rawToken;
        var emailQueued = invitationMail.enqueue(spaceId, invitation.getId(), email, inviter.getDisplayName(),
                space.getName(), role, invitation.getExpiresAt(), invitationUrl);
        return new InvitationView(invitation.getId(), invitation.getSpaceId(), email, role,
                invitation.getExpiresAt(), rawToken, emailQueued);
    }

    @Transactional
    public SpaceView accept(UUID userId, String rawToken) {
        var user = requireUser(userId);
        var invitation = invitations.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "초대를 찾을 수 없습니다."));
        return acceptInvitation(userId, user.getEmail(), invitation);
    }

    @Transactional(readOnly = true)
    public List<ReceivedInvitationView> listReceivedInvitations(UUID userId) {
        var user = requireUser(userId);
        return invitations.findByEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNullAndDeclinedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getEmail(), Instant.now()).stream()
                .map(invitation -> {
                    var space = requireSpace(invitation.getSpaceId());
                    var inviter = requireUser(invitation.getInvitedBy());
                    return ReceivedInvitationView.from(invitation, space, inviter.getDisplayName());
                }).toList();
    }

    @Transactional
    public SpaceView acceptReceivedInvitation(UUID userId, UUID invitationId) {
        var user = requireUser(userId);
        var invitation = invitations.findById(invitationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "초대를 찾을 수 없습니다."));
        return acceptInvitation(userId, user.getEmail(), invitation);
    }

    @Transactional
    public InvitationSummaryView declineReceivedInvitation(UUID userId, UUID invitationId) {
        var user = requireUser(userId);
        var invitation = invitations.findById(invitationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "초대를 찾을 수 없습니다."));
        requireActiveInvitationForEmail(invitation, user.getEmail());
        emailOutbox.cancelInvitationDeliveries(invitation.getId());
        invitation.decline();
        return InvitationSummaryView.from(invitation);
    }

    private SpaceView acceptInvitation(UUID userId, String email, Invitation invitation) {
        requireActiveInvitationForEmail(invitation, email);
        emailOutbox.cancelInvitationDeliveries(invitation.getId());
        if (members.findBySpaceIdAndUserIdAndStatus(invitation.getSpaceId(), userId, "ACTIVE").isEmpty()) {
            members.save(new SpaceMember(invitation.getSpaceId(), userId, invitation.getRole()));
        }
        invitation.accept();
        var space = spaces.findById(invitation.getSpaceId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "공간을 찾을 수 없습니다."));
        return SpaceView.from(space, invitation.getRole());
    }

    private void requireActiveInvitationForEmail(Invitation invitation, String email) {
        if (!invitation.isActive()) {
            throw new ApiException(HttpStatus.GONE, "INVITATION_EXPIRED", "초대가 만료되었거나 취소되었습니다.");
        }
        if (!invitation.getEmail().equalsIgnoreCase(email)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVITATION_EMAIL_MISMATCH", "초대받은 이메일 계정으로 로그인해 주세요.");
        }
    }

    @Transactional(readOnly = true)
    public List<MemberView> listMembers(UUID actorId, UUID spaceId) {
        requireMembership(spaceId, actorId);
        return members.findBySpaceIdAndStatusOrderByJoinedAt(spaceId, "ACTIVE").stream()
                .map(member -> {
                    var user = users.findById(member.getUserId())
                            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "구성원을 찾을 수 없습니다."));
                    return new MemberView(user.getId(), user.getDisplayName(), user.getEmail(), member.getRole(),
                            member.getJoinedAt(), user.getId().equals(actorId));
                }).toList();
    }

    @Transactional
    public MemberView changeMemberRole(UUID actorId, UUID spaceId, UUID memberUserId, SpaceRole role) {
        var actor = requireManager(spaceId, actorId);
        var target = requireMembership(spaceId, memberUserId);
        validateMemberMutation(actorId, actor, memberUserId, target);
        if (role == SpaceRole.OWNER) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "OWNER_TRANSFER_REQUIRED", "소유자 변경은 별도 이전 절차가 필요합니다.");
        }
        if (actor.getRole() == SpaceRole.ADMIN && role == SpaceRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_PROMOTION_NOT_ALLOWED", "관리자 지정은 소유자만 할 수 있습니다.");
        }
        var previousRole=target.getRole();
        target.changeRole(role);
        var user = requireUser(memberUserId);
        audits.record(spaceId,actorId,"MEMBER_ROLE_CHANGED","MEMBER",memberUserId,
                user.getDisplayName()+" 역할 변경: "+previousRole+" → "+role,null);
        return new MemberView(user.getId(), user.getDisplayName(), user.getEmail(), target.getRole(),
                target.getJoinedAt(), false);
    }

    @Transactional
    public void removeMember(UUID actorId, UUID spaceId, UUID memberUserId) {
        var actor = requireManager(spaceId, actorId);
        var target = requireMembership(spaceId, memberUserId);
        validateMemberMutation(actorId, actor, memberUserId, target);
        var user=requireUser(memberUserId);
        target.remove();
        audits.record(spaceId,actorId,"MEMBER_REMOVED","MEMBER",memberUserId,user.getDisplayName()+" 구성원 추방",null);
    }

    @Transactional
    public void leave(UUID userId, UUID spaceId) {
        var space = requireSpace(spaceId);
        var membership = requireMembership(spaceId, userId);
        if (space.getType() == SpaceType.PERSONAL) {
            throw new ApiException(HttpStatus.CONFLICT, "PERSONAL_SPACE_REQUIRED", "개인 공간에서는 탈퇴할 수 없습니다.");
        }
        if (membership.getRole() == SpaceRole.OWNER) {
            throw new ApiException(HttpStatus.CONFLICT, "OWNER_CANNOT_LEAVE", "공간 소유자는 공간을 삭제하거나 소유권을 이전해야 합니다.");
        }
        membership.remove();
        audits.record(spaceId,userId,"MEMBER_LEFT","MEMBER",userId,"구성원 자진 탈퇴",null);
    }

    @Transactional
    public void archive(UUID actorId, UUID spaceId) {
        var space = requireSpace(spaceId);
        var membership = requireMembership(spaceId, actorId);
        if (space.getType() == SpaceType.PERSONAL) {
            throw new ApiException(HttpStatus.CONFLICT, "PERSONAL_SPACE_REQUIRED", "개인 공간은 삭제할 수 없습니다.");
        }
        if (membership.getRole() != SpaceRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SPACE_DELETE_NOT_ALLOWED", "공간 소유자만 공간을 삭제할 수 있습니다.");
        }
        audits.record(spaceId,actorId,"SPACE_ARCHIVED","SPACE",spaceId,space.getName()+" 공간 삭제",null);
        invitations.findBySpaceIdOrderByCreatedAtDesc(spaceId).stream()
                .filter(Invitation::isActive)
                .forEach(invitation -> {
                    emailOutbox.cancelInvitationDeliveries(invitation.getId());
                    invitation.revoke();
                });
        members.findBySpaceIdAndStatusOrderByJoinedAt(spaceId, "ACTIVE").forEach(SpaceMember::remove);
        space.archive();
    }

    @Transactional(readOnly = true)
    public List<InvitationSummaryView> listInvitations(UUID actorId, UUID spaceId) {
        requireManager(spaceId, actorId);
        return invitations.findBySpaceIdOrderByCreatedAtDesc(spaceId).stream()
                .map(InvitationSummaryView::from)
                .toList();
    }

    @Transactional
    public InvitationSummaryView revokeInvitation(UUID actorId, UUID spaceId, UUID invitationId) {
        requireManager(spaceId, actorId);
        var invitation = invitations.findById(invitationId)
                .filter(item -> item.getSpaceId().equals(spaceId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "초대를 찾을 수 없습니다."));
        if (!invitation.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "INVITATION_NOT_PENDING", "대기 중인 초대만 취소할 수 있습니다.");
        }
        emailOutbox.cancelInvitationDeliveries(invitation.getId());
        invitation.revoke();
        return InvitationSummaryView.from(invitation);
    }

    @Transactional
    public InvitationView resendInvitation(UUID actorId, UUID spaceId, UUID invitationId, String webBaseUrl) {
        requireManager(spaceId, actorId);
        var invitation = invitations.findById(invitationId)
                .filter(item -> item.getSpaceId().equals(spaceId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "초대를 찾을 수 없습니다."));
        if (!invitation.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "INVITATION_NOT_PENDING", "대기 중인 초대만 재발송할 수 있습니다.");
        }
        emailOutbox.cancelInvitationDeliveries(invitation.getId());
        var rawToken = generateToken();
        invitation.rotateToken(hash(rawToken));
        var space = requireSpace(spaceId);
        var inviter = requireUser(actorId);
        var invitationUrl = webBaseUrl == null ? null : webBaseUrl.replaceAll("/+$", "") + "/?invite=" + rawToken;
        var emailQueued = invitationMail.enqueue(spaceId, invitation.getId(), invitation.getEmail(),
                inviter.getDisplayName(), space.getName(), invitation.getRole(), invitation.getExpiresAt(), invitationUrl);
        audits.record(spaceId, actorId, "INVITATION_RESENT", "INVITATION", invitationId,
                invitation.getEmail() + " 초대 메일 재발송 요청", null);
        return new InvitationView(invitation.getId(), spaceId, invitation.getEmail(), invitation.getRole(),
                invitation.getExpiresAt(), rawToken, emailQueued);
    }

    @Transactional(readOnly = true)
    public List<EmailOutboxService.DeliveryView> listEmailDeliveries(UUID actorId, UUID spaceId) {
        requireManager(spaceId, actorId);
        return emailOutbox.list(spaceId);
    }

    private SpaceMember requireManager(UUID spaceId, UUID actorId) {
        var actor = requireMembership(spaceId, actorId);
        if (actor.getRole() != SpaceRole.OWNER && actor.getRole() != SpaceRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MEMBER_MANAGEMENT_NOT_ALLOWED", "구성원을 관리할 권한이 없습니다.");
        }
        return actor;
    }

    private void validateMemberMutation(UUID actorId, SpaceMember actor, UUID targetUserId, SpaceMember target) {
        if (actorId.equals(targetUserId)) {
            throw new ApiException(HttpStatus.CONFLICT, "SELF_MANAGEMENT_NOT_ALLOWED", "자신의 역할을 변경하거나 자신을 추방할 수 없습니다.");
        }
        if (target.getRole() == SpaceRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_PROTECTED", "공간 소유자는 변경하거나 추방할 수 없습니다.");
        }
        if (actor.getRole() == SpaceRole.ADMIN && target.getRole() == SpaceRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_MANAGEMENT_NOT_ALLOWED", "관리자는 다른 관리자를 변경하거나 추방할 수 없습니다.");
        }
    }

    private SpaceMember requireMembership(UUID spaceId, UUID userId) {
        return members.findBySpaceIdAndUserIdAndStatus(spaceId, userId, "ACTIVE")
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "공간을 찾을 수 없습니다."));
    }

    private Space requireSpace(UUID spaceId) {
        return spaces.findById(spaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "공간을 찾을 수 없습니다."));
    }

    private com.couponwith.identity.UserAccount requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private String generateToken() {
        var bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record SpaceView(UUID id, SpaceType type, String name, String timezone, String color, SpaceRole role) {
        static SpaceView from(Space space, SpaceRole role) {
            return new SpaceView(space.getId(), space.getType(), space.getName(), space.getTimezone(), space.getColor(), role);
        }
    }

    public record InvitationView(UUID id, UUID spaceId, String email, SpaceRole role, Instant expiresAt,
                                 String oneTimeToken, boolean emailQueued) {}

    public record MemberView(UUID userId, String displayName, String email, SpaceRole role, Instant joinedAt,
                             boolean currentUser) {}

    public record InvitationSummaryView(UUID id, UUID spaceId, String email, SpaceRole role, Instant expiresAt,
                                        String status, Instant createdAt) {
        static InvitationSummaryView from(Invitation invitation) {
            return new InvitationSummaryView(invitation.getId(), invitation.getSpaceId(), invitation.getEmail(),
                    invitation.getRole(), invitation.getExpiresAt(), invitation.status(), invitation.getCreatedAt());
        }
    }

    public record ReceivedInvitationView(UUID id, UUID spaceId, String spaceName, SpaceType spaceType,
                                         SpaceRole role, String invitedByName, Instant expiresAt, Instant createdAt) {
        static ReceivedInvitationView from(Invitation invitation, Space space, String invitedByName) {
            return new ReceivedInvitationView(invitation.getId(), space.getId(), space.getName(), space.getType(),
                    invitation.getRole(), invitedByName, invitation.getExpiresAt(), invitation.getCreatedAt());
        }
    }
}
