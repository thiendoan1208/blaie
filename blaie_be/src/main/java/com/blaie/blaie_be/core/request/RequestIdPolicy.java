package com.blaie.blaie_be.core.request;

public final class RequestIdPolicy {
    public static final int MAX_LENGTH = 128;

    private RequestIdPolicy() {
    }

    public static boolean isValid(String requestId) {
        if (requestId == null || requestId.isEmpty() || requestId.length() > MAX_LENGTH) {
            return false;
        }
        for (int index = 0; index < requestId.length(); index++) {
            char character = requestId.charAt(index);
            boolean allowed = character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '.'
                    || character == '_'
                    || character == ':'
                    || character == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
