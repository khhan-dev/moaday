package com.couponwith.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class LoginProtectionService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final int maximumAttempts;
    private final long lockSeconds;
    private final String dummyPasswordHash;

    public LoginProtectionService(UserRepository users, PasswordEncoder passwordEncoder,
                                  @Value("${moaday.security.login.maximum-attempts:5}") int maximumAttempts,
                                  @Value("${moaday.security.login.lock-seconds:900}") long lockSeconds) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.maximumAttempts = Math.max(1, maximumAttempts);
        this.lockSeconds = Math.max(60, lockSeconds);
        this.dummyPasswordHash = passwordEncoder.encode("moaday-dummy-credential-check");
    }

    @Transactional
    public LoginDecision authenticate(String email, String password) {
        var found = users.findByEmailForUpdate(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyPasswordHash);
            return LoginDecision.invalid();
        }
        var user = found.get();
        if (!user.isActive()) {
            passwordEncoder.matches(password, user.getPasswordHash());
            return LoginDecision.invalid();
        }
        var now = Instant.now();
        user.refreshLoginLock(now);
        if (user.isLoginLocked(now)) return LoginDecision.locked();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordLoginFailure(now, maximumAttempts, lockSeconds);
            return LoginDecision.invalid();
        }
        user.recordLoginSuccess();
        return LoginDecision.authenticated(user);
    }

    public enum Status { AUTHENTICATED, INVALID, LOCKED }
    public record LoginDecision(Status status, UserAccount user) {
        static LoginDecision authenticated(UserAccount user) { return new LoginDecision(Status.AUTHENTICATED, user); }
        static LoginDecision invalid() { return new LoginDecision(Status.INVALID, null); }
        static LoginDecision locked() { return new LoginDecision(Status.LOCKED, null); }
    }
}
