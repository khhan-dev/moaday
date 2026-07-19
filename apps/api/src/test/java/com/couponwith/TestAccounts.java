package com.couponwith;

import com.couponwith.identity.AuthService;
import com.couponwith.identity.UserRepository;
import com.couponwith.space.Space;
import com.couponwith.space.SpaceMember;
import com.couponwith.space.SpaceMemberRepository;
import com.couponwith.space.SpaceRepository;
import com.couponwith.space.SpaceRole;
import com.couponwith.space.SpaceType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Test-only fixture factory for legacy integration scenarios that need active accounts. */
@Component
public class TestAccounts {
    private final AuthService auth;
    private final UserRepository users;
    private final SpaceRepository spaces;
    private final SpaceMemberRepository members;

    public TestAccounts(AuthService auth, UserRepository users, SpaceRepository spaces, SpaceMemberRepository members) {
        this.auth = auth;
        this.users = users;
        this.spaces = spaces;
        this.members = members;
    }

    @Transactional
    public AuthService.AuthResult register(String email, String password, String displayName, String timezone) {
        auth.register(email, password, displayName, timezone);
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        user.activate();
        var space = spaces.save(new Space(UUID.randomUUID(), SpaceType.PERSONAL,
                user.getDisplayName() + "의 개인 공간", user.getId(), user.getTimezone(), "sky"));
        members.save(new SpaceMember(space.getId(), user.getId(), SpaceRole.OWNER));
        return auth.login(email, password);
    }
}
