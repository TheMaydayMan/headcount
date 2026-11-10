package com.gradychastain.headcount.text;

/**
 * A resolved placeholder value. {@code mini} is true when the text is MiniMessage that should keep its formatting
 * (for example an equipped title); otherwise it is plain text and gets escaped before parsing.
 */
public record PlaceholderValue(String text, boolean mini) {
    public static PlaceholderValue plain(Object text) {
        return new PlaceholderValue(String.valueOf(text), false);
    }

    public static PlaceholderValue mini(String text) {
        return new PlaceholderValue(text == null ? "" : text, true);
    }
}
