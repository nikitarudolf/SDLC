package by.bsuir.labqueue.security;

import java.security.SecureRandom;
import java.util.Base64;

/** Случайные токены для ссылок подтверждения e-mail и приглашения. */
final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Tokens() {
    }

    static String next() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
