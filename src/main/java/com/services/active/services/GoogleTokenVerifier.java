package com.services.active.services;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.services.active.dto.GoogleUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;

@Service
@RequiredArgsConstructor
public class GoogleTokenVerifier {
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";

    @Value("${google.client.id}")
    private String googleClientId;

    public GoogleUserInfo verify(String idToken) {
        try {
            JWKSource<SecurityContext> keySource =
                    new RemoteJWKSet<>(new URL(GOOGLE_CERTS_URL));

            ConfigurableJWTProcessor<SecurityContext> processor =
                    new DefaultJWTProcessor<>();

            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);

            processor.setJWSKeySelector(keySelector);

            SecurityContext ctx = null;
            JWTClaimsSet claims = processor.process(idToken, ctx);

            if (!claims.getIssuer().equals(GOOGLE_ISSUER)) {
                throw new RuntimeException("Invalid issuer");
            }

            if (!claims.getAudience().contains(googleClientId)) {
                throw new RuntimeException("Invalid audience");
            }

            return new GoogleUserInfo(
                    claims.getSubject(),
                    (String) claims.getClaim("email"),
                    (String) claims.getClaim("name"),
                    (String) claims.getClaim("picture"),
                    (String) claims.getClaim("given_name"),
                    (String) claims.getClaim("family_name")
            );

        } catch (Exception e) {
            throw new RuntimeException("Invalid Google token", e);
        }
    }
}
