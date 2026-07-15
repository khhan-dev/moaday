package com.couponwith;

import com.couponwith.identity.AuthService;
import com.couponwith.mail.EmailOutboxRepository;
import com.couponwith.mail.EmailOutboxStatus;
import com.couponwith.space.Invitation;
import com.couponwith.space.InvitationRepository;
import com.couponwith.space.SpaceRole;
import com.couponwith.space.SpaceService;
import com.couponwith.space.SpaceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class StageOneIntegrationTest {
    @Autowired AuthService authService;
    @Autowired SpaceService spaceService;
    @Autowired InvitationRepository invitationRepository;
    @Autowired EmailOutboxRepository emailOutboxRepository;

    @Test
    void registrationCreatesPersonalSpaceAndAllowsFamilySpaceAndInvitation() {
        var owner = authService.register("owner@example.com", "password123!", "훈", "Asia/Seoul");
        var initialSpaces = spaceService.list(owner.user().id());
        assertThat(initialSpaces).singleElement().satisfies(space -> {
            assertThat(space.type()).isEqualTo(SpaceType.PERSONAL);
            assertThat(space.role()).isEqualTo(SpaceRole.OWNER);
        });

        var family = spaceService.create(owner.user().id(), SpaceType.FAMILY, "우리 가족", "Asia/Seoul", "orange");
        var invitation = spaceService.invite(owner.user().id(), family.id(), "member@example.com", SpaceRole.MEMBER,
                "https://moaday.test");

        assertThat(spaceService.list(owner.user().id())).hasSize(2);
        assertThat(invitation.oneTimeToken()).isNotBlank();
        assertThat(invitation.email()).isEqualTo("member@example.com");
        assertThat(invitation.emailQueued()).isTrue();
        assertThat(emailOutboxRepository.findTop100BySpaceIdOrderByCreatedAtDesc(family.id()))
                .singleElement().satisfies(mail -> {
                    assertThat(mail.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
                    assertThat(mail.getInvitationId()).isEqualTo(invitation.id());
                });
    }

    @Test
    void invitationRequiresMatchingAccountEmail() {
        var owner = authService.register("a@example.com", "password123!", "A", "Asia/Seoul");
        var wrongUser = authService.register("wrong@example.com", "password123!", "B", "Asia/Seoul");
        var family = spaceService.create(owner.user().id(), SpaceType.FAMILY, "가족", "Asia/Seoul", "orange");
        var invitation = spaceService.invite(owner.user().id(), family.id(), "right@example.com", SpaceRole.MEMBER);

        assertThatThrownBy(() -> spaceService.accept(wrongUser.user().id(), invitation.oneTimeToken()))
                .hasMessageContaining("초대받은 이메일");
    }

    @Test
    void ownerCanListChangeAndRemoveMembers() {
        var owner = authService.register("owner-manage@example.com", "password123!", "소유자", "Asia/Seoul");
        var member = authService.register("member-manage@example.com", "password123!", "구성원", "Asia/Seoul");
        var family = spaceService.create(owner.user().id(), SpaceType.FAMILY, "가족", "Asia/Seoul", "orange");
        var invitation = spaceService.invite(owner.user().id(), family.id(), member.user().email(), SpaceRole.MEMBER);
        spaceService.accept(member.user().id(), invitation.oneTimeToken());

        assertThat(spaceService.listMembers(owner.user().id(), family.id())).hasSize(2);
        var changed = spaceService.changeMemberRole(owner.user().id(), family.id(), member.user().id(), SpaceRole.ADMIN);
        assertThat(changed.role()).isEqualTo(SpaceRole.ADMIN);

        spaceService.removeMember(owner.user().id(), family.id(), member.user().id());
        assertThat(spaceService.listMembers(owner.user().id(), family.id()))
                .extracting(SpaceService.MemberView::userId)
                .containsExactly(owner.user().id());
        assertThat(spaceService.list(member.user().id())).hasSize(1);
    }

    @Test
    void memberCannotManageAndOwnerIsProtected() {
        var owner = authService.register("protected-owner@example.com", "password123!", "소유자", "Asia/Seoul");
        var member = authService.register("plain-member@example.com", "password123!", "멤버", "Asia/Seoul");
        var family = spaceService.create(owner.user().id(), SpaceType.FAMILY, "친구들", "Asia/Seoul", "green");
        var invitation = spaceService.invite(owner.user().id(), family.id(), member.user().email(), SpaceRole.MEMBER);
        spaceService.accept(member.user().id(), invitation.oneTimeToken());

        assertThatThrownBy(() -> spaceService.removeMember(member.user().id(), family.id(), owner.user().id()))
                .hasMessageContaining("관리할 권한");
        assertThatThrownBy(() -> spaceService.removeMember(owner.user().id(), family.id(), owner.user().id()))
                .hasMessageContaining("자신");
    }

    @Test
    void invitationsCanBeListedRevokedAndReissuedAfterExpiry() {
        var owner = authService.register("invite-owner@example.com", "password123!", "소유자", "Asia/Seoul");
        var family = spaceService.create(owner.user().id(), SpaceType.FAMILY, "우리 집", "Asia/Seoul", "orange");
        var pending = spaceService.invite(owner.user().id(), family.id(), "pending@example.com", SpaceRole.MEMBER);

        var revoked = spaceService.revokeInvitation(owner.user().id(), family.id(), pending.id());
        assertThat(revoked.status()).isEqualTo("REVOKED");
        assertThat(spaceService.invite(owner.user().id(), family.id(), "pending@example.com", SpaceRole.VIEWER))
                .isNotNull();

        invitationRepository.save(new Invitation(UUID.randomUUID(), family.id(), "expired@example.com", SpaceRole.MEMBER,
                "expired-token-hash-" + UUID.randomUUID(), owner.user().id(), Instant.now().minusSeconds(1)));
        assertThat(spaceService.listInvitations(owner.user().id(), family.id()))
                .filteredOn(invitation -> invitation.email().equals("expired@example.com"))
                .singleElement()
                .extracting(SpaceService.InvitationSummaryView::status)
                .isEqualTo("EXPIRED");
        assertThat(spaceService.invite(owner.user().id(), family.id(), "expired@example.com", SpaceRole.MEMBER))
                .isNotNull();
    }

    @Test
    void registeredUserCanListDeclineAndAcceptReceivedInvitations() {
        var owner = authService.register("received-owner@example.com", "password123!", "초대자", "Asia/Seoul");
        var recipient = authService.register("received-member@example.com", "password123!", "수신자", "Asia/Seoul");
        var family = spaceService.create(owner.user().id(), SpaceType.FAMILY, "초대할 가족", "Asia/Seoul", "orange");
        var invitation = spaceService.invite(owner.user().id(), family.id(), recipient.user().email(), SpaceRole.MEMBER);

        assertThat(spaceService.listReceivedInvitations(recipient.user().id())).singleElement().satisfies(received -> {
            assertThat(received.id()).isEqualTo(invitation.id());
            assertThat(received.spaceName()).isEqualTo("초대할 가족");
            assertThat(received.invitedByName()).isEqualTo("초대자");
            assertThat(received.role()).isEqualTo(SpaceRole.MEMBER);
        });

        var declined = spaceService.declineReceivedInvitation(recipient.user().id(), invitation.id());
        assertThat(declined.status()).isEqualTo("DECLINED");
        assertThat(spaceService.listReceivedInvitations(recipient.user().id())).isEmpty();

        var reissued = spaceService.invite(owner.user().id(), family.id(), recipient.user().email(), SpaceRole.VIEWER);
        var accepted = spaceService.acceptReceivedInvitation(recipient.user().id(), reissued.id());
        assertThat(accepted.id()).isEqualTo(family.id());
        assertThat(accepted.role()).isEqualTo(SpaceRole.VIEWER);
        assertThat(spaceService.list(recipient.user().id())).extracting(SpaceService.SpaceView::id).contains(family.id());
    }

    @Test
    void memberCanLeaveAndOnlyOwnerCanArchiveGroupSpace() {
        var owner = authService.register("lifecycle-owner@example.com", "password123!", "소유자", "Asia/Seoul");
        var member = authService.register("lifecycle-member@example.com", "password123!", "멤버", "Asia/Seoul");
        var family = spaceService.create(owner.user().id(), SpaceType.FAMILY, "정리할 가족", "Asia/Seoul", "orange");
        var invitation = spaceService.invite(owner.user().id(), family.id(), member.user().email(), SpaceRole.MEMBER);
        spaceService.accept(member.user().id(), invitation.oneTimeToken());

        assertThatThrownBy(() -> spaceService.leave(owner.user().id(), family.id()))
                .hasMessageContaining("소유자");
        assertThatThrownBy(() -> spaceService.archive(member.user().id(), family.id()))
                .hasMessageContaining("소유자만");

        spaceService.leave(member.user().id(), family.id());
        assertThat(spaceService.list(member.user().id())).extracting(SpaceService.SpaceView::type)
                .containsExactly(SpaceType.PERSONAL);

        spaceService.archive(owner.user().id(), family.id());
        assertThat(spaceService.list(owner.user().id())).extracting(SpaceService.SpaceView::type)
                .containsExactly(SpaceType.PERSONAL);
    }

    @Test
    void pendingInvitationCanBeResentWithANewOneTimeToken() {
        var owner = authService.register("resend-owner@example.com", "password123!", "소유자", "Asia/Seoul");
        var recipient = authService.register("resend-member@example.com", "password123!", "수신자", "Asia/Seoul");
        var family = spaceService.create(owner.user().id(), SpaceType.FAMILY, "재발송 가족", "Asia/Seoul", "green");
        var original = spaceService.invite(owner.user().id(), family.id(), recipient.user().email(), SpaceRole.MEMBER,
                "https://moaday.test");

        var resent = spaceService.resendInvitation(owner.user().id(), family.id(), original.id(), "https://moaday.test");

        assertThat(resent.emailQueued()).isTrue();
        assertThat(resent.oneTimeToken()).isNotEqualTo(original.oneTimeToken());
        assertThatThrownBy(() -> spaceService.accept(recipient.user().id(), original.oneTimeToken()))
                .hasMessageContaining("초대를 찾을 수 없습니다");
        assertThat(spaceService.accept(recipient.user().id(), resent.oneTimeToken()).id()).isEqualTo(family.id());
        assertThat(emailOutboxRepository.findTop100BySpaceIdOrderByCreatedAtDesc(family.id()))
                .hasSize(2)
                .allMatch(item -> item.getStatus() == EmailOutboxStatus.CANCELLED);
    }
}
