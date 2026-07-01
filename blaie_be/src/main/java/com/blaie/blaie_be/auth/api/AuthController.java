package com.blaie.blaie_be.auth.api;

import com.blaie.blaie_be.auth.api.request.LoginLocalRequest;
import com.blaie.blaie_be.auth.api.request.PasswordResetConfirmRequest;
import com.blaie.blaie_be.auth.api.request.PasswordResetRequest;
import com.blaie.blaie_be.auth.api.request.RegisterLocalRequest;
import com.blaie.blaie_be.auth.api.request.UpdatePasswordRequest;
import com.blaie.blaie_be.auth.api.request.UpdateUsernameRequest;
import com.blaie.blaie_be.auth.api.response.AuthUserEnvelope;
import com.blaie.blaie_be.auth.api.response.AuthUserResponse;
import com.blaie.blaie_be.auth.api.response.CsrfTokenResponse;
import com.blaie.blaie_be.auth.application.AuthService;
import com.blaie.blaie_be.auth.application.EmailVerificationService;
import com.blaie.blaie_be.auth.application.PasswordResetService;
import com.blaie.blaie_be.auth.application.command.ConfirmPasswordResetCommand;
import com.blaie.blaie_be.auth.application.command.LoginLocalCommand;
import com.blaie.blaie_be.auth.application.command.RegisterLocalCommand;
import com.blaie.blaie_be.auth.application.command.RequestPasswordResetCommand;
import com.blaie.blaie_be.auth.application.command.UpdatePasswordCommand;
import com.blaie.blaie_be.auth.application.command.UpdateUsernameCommand;
import com.blaie.blaie_be.auth.application.port.GoogleOAuthClientPort;
import com.blaie.blaie_be.auth.application.result.WebAuthResult;
import com.blaie.blaie_be.auth.infrastructure.google.GoogleOAuthProperties;
import com.blaie.blaie_be.auth.infrastructure.google.GoogleOAuthState;
import com.blaie.blaie_be.auth.infrastructure.google.GoogleOAuthStateCookieService;
import com.blaie.blaie_be.auth.infrastructure.security.AuthCookieService;
import com.blaie.blaie_be.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final AuthCookieService authCookieService;
    private final GoogleOAuthProperties googleOAuthProperties;
    private final GoogleOAuthStateCookieService googleOAuthStateCookieService;
    private final GoogleOAuthClientPort googleOAuthClientPort;

    public AuthController(
            AuthService authService,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService,
            AuthCookieService authCookieService,
            GoogleOAuthProperties googleOAuthProperties,
            GoogleOAuthStateCookieService googleOAuthStateCookieService,
            GoogleOAuthClientPort googleOAuthClientPort
    ) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.authCookieService = authCookieService;
        this.googleOAuthProperties = googleOAuthProperties;
        this.googleOAuthStateCookieService = googleOAuthStateCookieService;
        this.googleOAuthClientPort = googleOAuthClientPort;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthUserEnvelope>> register(
            @Valid @RequestBody RegisterLocalRequest request,
            HttpServletRequest httpRequest
    ) {
        WebAuthResult result = authService.registerLocal(
                new RegisterLocalCommand(request.username(), request.email(), request.displayName(), request.password()),
                httpRequest.getHeader(HttpHeaders.USER_AGENT)
        );
        return authResponse(result, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthUserEnvelope>> login(
            @Valid @RequestBody LoginLocalRequest request,
            HttpServletRequest httpRequest
    ) {
        WebAuthResult result = authService.loginLocal(
                new LoginLocalCommand(request.identifier(), request.password()),
                httpRequest.getHeader(HttpHeaders.USER_AGENT)
        );
        return authResponse(result, HttpStatus.OK);
    }

    @GetMapping("/me")
    public ApiResponse<AuthUserEnvelope> me() {
        return ApiResponse.of(new AuthUserEnvelope(AuthUserResponse.from(authService.currentUser())));
    }

    @PatchMapping("/me/username")
    public ApiResponse<AuthUserEnvelope> updateUsername(@Valid @RequestBody UpdateUsernameRequest request) {
        return ApiResponse.of(new AuthUserEnvelope(AuthUserResponse.from(
                authService.updateUsername(new UpdateUsernameCommand(request.username()))
        )));
    }

    @PatchMapping("/me/password")
    public ApiResponse<AuthUserEnvelope> updatePassword(@Valid @RequestBody UpdatePasswordRequest request) {
        return ApiResponse.of(new AuthUserEnvelope(AuthUserResponse.from(
                authService.updatePassword(new UpdatePasswordCommand(request.currentPassword(), request.newPassword()))
        )));
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ApiResponse.of(new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName()));
    }

    @PostMapping("/email/verification")
    public ResponseEntity<Void> resendEmailVerification() {
        emailVerificationService.resendVerificationForCurrentUser();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestPasswordReset(new RequestPasswordResetCommand(request.email()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmPasswordReset(new ConfirmPasswordResetCommand(
                request.email(),
                request.code(),
                request.newPassword()
        ));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/email/verify")
    public ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
        try {
            emailVerificationService.verifyEmailToken(token);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(emailVerificationService.verifiedRedirectUrl()))
                    .build();
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(emailVerificationService.failedRedirectUrl()))
                    .build();
        }
    }

    @GetMapping("/google/start")
    public ResponseEntity<Void> startGoogleLogin(
            @RequestParam(name = "next", required = false) String next
    ) {
        GoogleOAuthState oauthState = googleOAuthStateCookieService.create(next);
        URI authorizationUri = UriComponentsBuilder.fromUri(googleOAuthProperties.authorizationUri())
                .queryParam("client_id", googleOAuthProperties.clientId())
                .queryParam("redirect_uri", googleOAuthProperties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", oauthState.state())
                .queryParam("code_challenge", oauthState.codeChallenge())
                .queryParam("code_challenge_method", "S256")
                .queryParam("access_type", "online")
                .queryParam("prompt", "select_account")
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, googleOAuthStateCookieService.cookie(oauthState).toString())
                .location(authorizationUri)
                .build();
    }

    @GetMapping("/google/callback")
    public ResponseEntity<Void> completeGoogleLogin(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @CookieValue(name = GoogleOAuthStateCookieService.COOKIE_NAME, required = false) String stateCookie,
            HttpServletRequest httpRequest
    ) {
        try {
            GoogleOAuthState oauthState = googleOAuthStateCookieService.require(stateCookie, state);
            WebAuthResult result = authService.loginGoogle(
                    googleOAuthClientPort.exchangeCodeForProfile(code, oauthState.codeVerifier(), googleOAuthProperties.redirectUri()),
                    httpRequest.getHeader(HttpHeaders.USER_AGENT)
            );
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.SET_COOKIE, googleOAuthStateCookieService.clearCookie().toString())
                    .header(HttpHeaders.SET_COOKIE, authCookieService.accessCookie(result.accessToken(), result.accessTokenTtl()).toString())
                    .header(HttpHeaders.SET_COOKIE, authCookieService.refreshCookie(result.refreshToken(), result.refreshTokenTtl()).toString())
                    .location(URI.create(googleOAuthProperties.webBaseUrl() + oauthState.nextPath()))
                    .build();
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.SET_COOKIE, googleOAuthStateCookieService.clearCookie().toString())
                    .location(URI.create(googleOAuthProperties.webBaseUrl() + "/login?error=google_auth_failed"))
                    .build();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthUserEnvelope>> refresh(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest
    ) {
        WebAuthResult result = authService.refreshWeb(refreshToken, httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return authResponse(result, HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        authService.logoutWeb(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<ApiResponse<AuthUserEnvelope>> authResponse(WebAuthResult result, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, authCookieService.accessCookie(result.accessToken(), result.accessTokenTtl()).toString())
                .header(HttpHeaders.SET_COOKIE, authCookieService.refreshCookie(result.refreshToken(), result.refreshTokenTtl()).toString())
                .body(ApiResponse.of(new AuthUserEnvelope(AuthUserResponse.from(result.user()))));
    }
}
