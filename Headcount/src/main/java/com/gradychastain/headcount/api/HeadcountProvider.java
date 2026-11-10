package com.gradychastain.headcount.api;

/** Static access to the API while Headcount is enabled. */
public final class HeadcountProvider {
    private static HeadcountAPI api;

    private HeadcountProvider() {}

    /** @throws IllegalStateException if Headcount is not enabled */
    public static HeadcountAPI get() {
        if (api == null) throw new IllegalStateException("Headcount is not enabled");
        return api;
    }

    public static boolean isAvailable() {
        return api != null;
    }

    /** Internal: set by Headcount on enable and cleared on disable. */
    public static void set(HeadcountAPI value) {
        api = value;
    }
}
