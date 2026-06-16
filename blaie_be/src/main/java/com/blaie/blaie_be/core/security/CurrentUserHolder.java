package com.blaie.blaie_be.core.security;

import java.util.Optional;
import java.util.function.Supplier;

public final class CurrentUserHolder {
    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(CurrentUser currentUser) {
        CURRENT_USER.set(currentUser);
    }

    public static Optional<CurrentUser> current() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public static CurrentUser requireCurrentUser() {
        CurrentUser currentUser = CURRENT_USER.get();
        if (currentUser == null) {
            throw new IllegalStateException("current user is not available");
        }
        return currentUser;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }

    public static <T> T runAs(CurrentUser currentUser, Supplier<T> supplier) {
        CurrentUser previous = CURRENT_USER.get();
        CURRENT_USER.set(currentUser);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                CURRENT_USER.remove();
            } else {
                CURRENT_USER.set(previous);
            }
        }
    }
}
