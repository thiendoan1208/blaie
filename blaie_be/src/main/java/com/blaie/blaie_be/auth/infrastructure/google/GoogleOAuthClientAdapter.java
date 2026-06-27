package com.blaie.blaie_be.auth.infrastructure.google;

import com.blaie.blaie_be.auth.application.port.GoogleOAuthClientPort;
import com.blaie.blaie_be.auth.application.port.GoogleOAuthProfile;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GoogleOAuthClientAdapter implements GoogleOAuthClientPort {
    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClientAdapter.class);

    private final GoogleOAuthProperties properties;
    private final RestClient restClient;

    public GoogleOAuthClientAdapter(GoogleOAuthProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public GoogleOAuthProfile exchangeCodeForProfile(String code, String codeVerifier, URI redirectUri) {
        if (isBlank(code) || isBlank(codeVerifier)) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        try {
            TokenResponse tokenResponse = exchangeCodeForToken(code, codeVerifier, redirectUri);
            UserInfoResponse userInfo = fetchUserInfo(tokenResponse.accessToken());
            if (isBlank(userInfo.subject()) || isBlank(userInfo.email())) {
                throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
            }
            return new GoogleOAuthProfile(
                    userInfo.subject(),
                    userInfo.email(),
                    Boolean.TRUE.equals(userInfo.emailVerified()),
                    userInfo.name(),
                    userInfo.picture()
            );
        } catch (AppException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("Google OAuth communication failed: {}", exception.getMessage(), exception);
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    private TokenResponse exchangeCodeForToken(String code, String codeVerifier, URI redirectUri) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", properties.clientId());
        body.add("client_secret", properties.clientSecret());
        body.add("redirect_uri", redirectUri.toString());
        body.add("grant_type", "authorization_code");
        body.add("code_verifier", codeVerifier);

        TokenResponse response = restClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || isBlank(response.accessToken())) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        return response;
    }

    private UserInfoResponse fetchUserInfo(String accessToken) {
        UserInfoResponse response = restClient.get()
                .uri(properties.userInfoUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(UserInfoResponse.class);
        if (response == null) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        return response;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("id_token") String idToken
    ) {
    }

    private record UserInfoResponse(
            @JsonProperty("sub") String subject,
            @JsonProperty("email") String email,
            @JsonProperty("email_verified") Boolean emailVerified,
            @JsonProperty("name") String name,
            @JsonProperty("picture") String picture
    ) {
    }
}
