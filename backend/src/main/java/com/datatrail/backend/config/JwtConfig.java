package com.datatrail.backend.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtConfig {

    private static final Path KEY_DIRECTORY = Path.of(".keys");
    private static final Path PRIVATE_KEY_FILE = KEY_DIRECTORY.resolve("jwt-private.key");
    private static final Path PUBLIC_KEY_FILE = KEY_DIRECTORY.resolve("jwt-public.key");

    @Bean
    public KeyPair keyPair() throws Exception {

        boolean privateKeyExists = Files.exists(PRIVATE_KEY_FILE);
        boolean publicKeyExists = Files.exists(PUBLIC_KEY_FILE);

        if (privateKeyExists != publicKeyExists) {
            throw new IllegalStateException(
                    "JWT key pair is incomplete; both key files must exist together");
        }

        if (!privateKeyExists) {
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance("RSA");

            generator.initialize(2048);

            KeyPair generatedKeyPair = generator.generateKeyPair();
            Files.createDirectories(KEY_DIRECTORY);
            Files.write(PRIVATE_KEY_FILE, generatedKeyPair.getPrivate().getEncoded());
            Files.write(PUBLIC_KEY_FILE, generatedKeyPair.getPublic().getEncoded());

            return generatedKeyPair;
        }

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(Files.readAllBytes(PRIVATE_KEY_FILE)));
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new X509EncodedKeySpec(Files.readAllBytes(PUBLIC_KEY_FILE)));

        return new KeyPair(publicKey, privateKey);
    }

    @Bean
    public JwtEncoder jwtEncoder(KeyPair keyPair) {

        RSAPublicKey publicKey =
                (RSAPublicKey) keyPair.getPublic();

        RSAPrivateKey privateKey =
                (RSAPrivateKey) keyPair.getPrivate();

        return NimbusJwtEncoder
                .withKeyPair(publicKey, privateKey)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(KeyPair keyPair) {

        RSAPublicKey publicKey =
                (RSAPublicKey) keyPair.getPublic();

        return NimbusJwtDecoder
                .withPublicKey(publicKey)
                .build();
    }
}