package com.praneetkamble.urlshortener.util;

/**
 * Base62 encoding of non-negative {@code long} identifiers using the alphabet
 * {@code 0-9A-Za-z}. Produces short, URL-safe, case-sensitive strings.
 */
public final class Base62 {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative: " + value);
        }
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder(11);
        long remaining = value;
        while (remaining > 0) {
            sb.append(ALPHABET.charAt((int) (remaining % BASE)));
            remaining /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("encoded value must not be empty");
        }
        long result = 0;
        for (int i = 0; i < encoded.length(); i++) {
            int digit = ALPHABET.indexOf(encoded.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("Illegal base62 character '" + encoded.charAt(i) + "'");
            }
            try {
                result = Math.addExact(Math.multiplyExact(result, (long) BASE), digit);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Base62 value overflows a long: " + encoded, e);
            }
        }
        return result;
    }
}
