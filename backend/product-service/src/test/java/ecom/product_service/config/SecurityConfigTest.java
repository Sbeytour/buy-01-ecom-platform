package ecom.product_service.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class SecurityConfigTest {

    private static KeyPair signingKeys;
    private static JwtDecoder decoder;

    @BeforeAll
    static void setUpDecoder() throws Exception {
        signingKeys = generateKeys();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] { '\n' })
                        .encodeToString(signingKeys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";

        decoder = new SecurityConfig().jwtDecoder(
                new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void decoder_ShouldAcceptValidSignedToken() throws Exception {
        String token = sign("seller-1", Instant.now().plusSeconds(300), signingKeys);

        assertEquals("seller-1", decoder.decode(token).getSubject());
    }

    @Test
    void decoder_ShouldRejectMissingExpiration() throws Exception {
        String token = sign("seller-1", null, signingKeys);
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decoder_ShouldRejectMissingSubject() throws Exception {
        String token = sign(null, Instant.now().plusSeconds(300), signingKeys);

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decoder_ShouldRejectEmptySubject() throws Exception {
        String token = sign("", Instant.now().plusSeconds(300), signingKeys);

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decoder_ShouldRejectWhitespaceSubject() throws Exception {
        String token = sign("   ", Instant.now().plusSeconds(300), signingKeys);

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decoder_ShouldStillRejectExpiredTokens() throws Exception {
        // Outside Spring's default clock-skew allowance.
        String token = sign("seller-1", Instant.now().minusSeconds(300), signingKeys);

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decoder_ShouldRejectTokensSignedByAnotherKey() throws Exception {
        String token = sign("seller-1", Instant.now().plusSeconds(300), generateKeys());

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decoder_ShouldRejectModifiedPayload() throws Exception {
        String token = sign("seller-1", Instant.now().plusSeconds(300), signingKeys);
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String changedPayload = payload.replace("seller-1", "victim-1");
        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(changedPayload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertThrows(JwtException.class, () -> decoder.decode(tampered));
    }

    private static KeyPair generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String sign(String subject, Instant expiration, KeyPair keys) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issueTime(Date.from(Instant.now().minusSeconds(600)))
                .claim("role", "SELLER");
        if (expiration != null) {
            claims.expirationTime(Date.from(expiration));
        }
        if (subject != null) {
            claims.subject(subject);
        }

        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        token.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
        return token.serialize();
    }
}
