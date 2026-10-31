package com.sulu.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    public record RegisterReq(@NotBlank @Email @Size(max = 254) String email, @NotBlank String password,
                              @NotBlank @Size(max = 120) String fullName, @Size(max = 32) String phone,
                              @Pattern(regexp = "CUSTOMER|BUSINESS_OWNER") String role,
                              @Pattern(regexp = "kk|ru|en|tr") String language, @Size(max = 80) String city) {}
    public record LoginReq(@NotBlank String email, @NotBlank String password) {}
    public record RefreshReq(@NotBlank String refreshToken) {}
    public record ResetReq(@NotBlank @Email String email) {}
    public record ResetConfirmReq(@NotBlank String token, @NotBlank String password) {}
    public record InviteAcceptReq(@NotBlank String token, @NotBlank String password, @NotBlank @Size(max = 120) String fullName) {}

    @PostMapping("/register")
    public AuthService.AuthResponse register(@Valid @RequestBody RegisterReq r) {
        return auth.register(r.email(), r.password(), r.fullName(), r.phone(), r.role() == null ? "CUSTOMER" : r.role(), r.language(), r.city());
    }
    @PostMapping("/login") public AuthService.AuthResponse login(@Valid @RequestBody LoginReq r) { return auth.login(r.email(), r.password()); }
    @PostMapping("/refresh") public AuthService.AuthResponse refresh(@Valid @RequestBody RefreshReq r) { return auth.refresh(r.refreshToken()); }
    @PostMapping("/logout") public void logout(@RequestBody RefreshReq r) { auth.logout(r.refreshToken()); }
    @PostMapping("/password-reset/request") public void reset(@Valid @RequestBody ResetReq r) { auth.requestPasswordReset(r.email()); }
    @PostMapping("/password-reset/confirm") public void confirm(@Valid @RequestBody ResetConfirmReq r) { auth.confirmPasswordReset(r.token(), r.password()); }
    @PostMapping("/accept-invite") public AuthService.AuthResponse accept(@Valid @RequestBody InviteAcceptReq r) { return auth.acceptInvite(r.token(), r.password(), r.fullName()); }
}
