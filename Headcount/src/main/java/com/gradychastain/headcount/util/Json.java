package com.gradychastain.headcount.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small JSON helper for persisting maps of simple values (strings, numbers, booleans, lists, maps). */
public final class Json {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Object>>() {}.getType();

    private Json() {}

    public static String write(Object value) {
        return GSON.toJson(value);
    }

    public static Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        Map<String, Object> m = GSON.fromJson(json, MAP_TYPE);
        return m == null ? new LinkedHashMap<>() : m;
    }
}
