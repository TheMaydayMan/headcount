package com.gradychastain.headcount.text;

import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * {@code <score>} or {@code <score:positive:negative:zero>}: a score coloured by its sign. Each colour is anything
 * MiniMessage takes as a tag name (green, gold, #ff8800, bold, ...); a missing or empty one uses the configured default.
 * An unknown score (the vault's owner is offline) shows as {@code unknown} in the zero colour.
 */
public final class ScoreTag {
    private ScoreTag() {}

    public static TagResolver of(String name, Integer score, String positive, String negative, String zero, String unknown) {
        return TagResolver.resolver(name, (args, ctx) -> {
            String pos = arg(args.hasNext() ? args.pop().value() : null, positive);
            String neg = arg(args.hasNext() ? args.pop().value() : null, negative);
            String nil = arg(args.hasNext() ? args.pop().value() : null, zero);
            String color = score == null || score == 0 ? nil : score > 0 ? pos : neg;
            String text = score == null ? unknown : Integer.toString(score);
            return Tag.selfClosingInserting(ctx.deserialize("<" + color + ">" + text));
        });
    }

    private static String arg(String given, String fallback) {
        return given == null || given.isBlank() ? fallback : given;
    }
}
