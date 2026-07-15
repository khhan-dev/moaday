package com.couponwith.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final PasswordRecoveryService passwordRecovery;

    public AuthController(AuthService authService, PasswordRecoveryService passwordRecovery) {
        this.authService = authService;
        this.passwordRecovery = passwordRecovery;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthService.AuthResult register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.email(), request.password(), request.displayName(), request.timezone());
    }

    @PostMapping("/login")
    AuthService.AuthResult login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    GenericMessage requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordRecovery.requestReset(request.email());
        return new GenericMessage("가입된 계정이라면 비밀번호 재설정 메일을 보내드렸습니다.");
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody PasswordResetConfirm request) {
        passwordRecovery.resetPassword(request.token(), request.newPassword());
    }

    record RegisterRequest(
            @Email @NotBlank String email,
            @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 40) String displayName,
            @NotBlank String timezone) {}

    record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    record PasswordResetRequest(@Email @NotBlank String email) {}
    record PasswordResetConfirm(@NotBlank String token, @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    record GenericMessage(String message) {}
}
