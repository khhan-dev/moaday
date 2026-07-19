package com.couponwith.identity;

import com.couponwith.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class EmailRegistrationService {
    private static final String PENDING_MESSAGE = "인증 이메일을 확인해 계정을 활성화해 주세요.";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerification;

    EmailRegistrationService(UserRepository users, PasswordEncoder passwordEncoder,
                             EmailVerificationService emailVerification) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.emailVerification = emailVerification;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AuthService.RegistrationPending register(String email, String password, String displayName, String timezone) {
        var existing = users.findByEmailForUpdate(email).orElse(null);
        if (existing != null && existing.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
        }
        if (existing != null) {
            emailVerification.issueFor(existing);
            return pending(email);
        }

        UserAccount user;
        try {
            user = users.saveAndFlush(new UserAccount(UUID.randomUUID(), email,
                    passwordEncoder.encode(password), displayName.trim(), timezone));
        } catch (DataIntegrityViolationException exception) {
            throw new EmailRegistrationConflictException(exception);
        }
        emailVerification.issueFor(user);
        return pending(email);
    }

    private AuthService.RegistrationPending pending(String email) {
        return new AuthService.RegistrationPending(email, PENDING_MESSAGE);
    }
}
