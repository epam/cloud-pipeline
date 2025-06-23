package com.epam.pipeline.security.jwt;

import com.epam.pipeline.exception.GCPAuthorizationException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.google.auth.oauth2.TokenVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.epam.pipeline.manager.preference.SystemPreferences.GCP_ARTIFACT_REGISTRY_NOTIFICATION_AUDIENCE;


@Component
@RequiredArgsConstructor
public class GCPJwtTokenVerifier {
    private final PreferenceManager preferenceManager;
    public static final String GOOGLE_ISSUER = "https://accounts.google.com";
    public void validateToken(final String jwtToken) {
        try {
            TokenVerifier tokenVerifier = TokenVerifier.newBuilder()
                    .setIssuer(GOOGLE_ISSUER)
                    .setAudience(preferenceManager.getPreference(GCP_ARTIFACT_REGISTRY_NOTIFICATION_AUDIENCE))
                    .build();
            tokenVerifier.verify(jwtToken);
        } catch (TokenVerifier.VerificationException e) {
            throw new GCPAuthorizationException(e.getMessage());
        }
    }
}
