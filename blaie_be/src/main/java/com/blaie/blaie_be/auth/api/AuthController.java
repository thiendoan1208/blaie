package com.blaie.blaie_be.auth.api;

import com.blaie.blaie_be.auth.api.request.LoginLocalRequest;
import com.blaie.blaie_be.auth.api.request.RegisterLocalRequest;
import com.blaie.blaie_be.auth.api.response.AuthUserEnvelope;
import com.blaie.blaie_be.auth.api.response.AuthUserResponse;
import com.blaie.blaie_be.auth.api.response.CsrfTokenResponse;
import com.blaie.blaie_be.auth.application.AuthService;
import com.blaie.blaie_be.auth.application.command.LoginLocalCommand;
import com.blaie.blaie_be.auth.application.command.RegisterLocalCommand;
import com.blaie.blaie_be.auth.application.result.WebAuthResult;
import com.blaie.blaie_be.auth.infrastructure.security.AuthCookieService;
import com.blaie.blaie_be.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieService authCookieService;

    public AuthController(AuthService authService, AuthCookieService authCookieService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
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

    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ApiResponse.of(new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName()));
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
