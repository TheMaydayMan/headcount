package com.gradychastain.headcount.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only access to a block of settings from a Headcount config file. */
public interface ConfigView {
    Set<String> keys();

    Object get(String key);

    boolean has(String key);

    String getString(String key, String def);

    int getInt(String key, int def);

    long getLong(String key, long def);

    double getDouble(String key, double def);

    boolean getBool(String key, boolean def);

    /** A duration such as {@code 30s}, {@code 10m} or {@code 2d} in milliseconds; plain numbers are seconds. */
    long getMillis(String key, long def);

    List<String> getStringList(String key);

    ConfigView getSection(String key);

    Map<String, Object> raw();

    /** Location of this block in its file, for error messages. */
    String path();
}
