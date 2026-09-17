package com.iaaops.shared;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * 对外可见的标识符。
 *
 * 不用自增：对外露出自增 id 等于告诉别人总量与增速，也方便被人按序枚举。
 * 形如 {@code aud_k3f9x2m1p0}，前缀说明对象类型。
 */
public final class Ids {

    private static final String ALPHABET = "0123456789abcdefghijkmnpqrstuvwxyz";
    private static final int LENGTH = 12;
    private static final RandomGenerator RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String next(String prefix) {
        StringBuilder id = new StringBuilder(prefix).append('_');
        for (int index = 0; index < LENGTH; index++) {
            id.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }
}
