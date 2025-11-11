package com.booking.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;


public final class PnrGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    private PnrGenerator() { }

    public static String generate() {
        long ts = Instant.now().toEpochMilli();
        byte[] rand = new byte[6];
        RANDOM.nextBytes(rand);
        String r = Base64.getUrlEncoder().withoutPadding().encodeToString(rand);
        String tsHex = Long.toHexString(ts);
        if (tsHex.length() > 6) tsHex = tsHex.substring(tsHex.length() - 6);
        return (tsHex + "-" + r).toUpperCase();
    }
}