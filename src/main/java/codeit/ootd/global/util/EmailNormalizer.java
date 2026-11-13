package codeit.ootd.global.util;

import java.util.Locale;

public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}