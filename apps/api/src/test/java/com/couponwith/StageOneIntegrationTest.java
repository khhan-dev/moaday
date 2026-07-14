package com.couponwith;

import com.couponwith.identity.AuthService;
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

    @Test
    void registrationCreatesPersonalSpaceAndAllowsFamilySpaceAndInvitation() {
        var owner = authService.register("owner@example.com", "password123!", "훈", "Asia/Seoul");
        var initialSpaces = spaceService.list(owner.user().id());
        assertThat(initialSpaces).singleElement().satisfies(space -> {
            assertThat(space.type()).isEqualTo(SpaceType.PERSONAL);
            assertThat(space.role()).isEqualTo(SpaceRole.OWNER);
        });

        var family = spaceService.create(owner.user().id(), SpaceType.FAMILY, "우리 가족", "Asia/Seoul", "orange");
        var invitation = spaceService.invite(owner.user().id(), family.id(), "member@example.com", SpaceRole.MEMBER);

        assertThat(spaceService.list(owner.user().id())).hasSize(2);
        assertThat(invitation.oneTimeToken()).isNotBlank();
        assertThat(invitation.email()).isEqualTo("member@example.com");
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
}
