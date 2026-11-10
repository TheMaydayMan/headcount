package com.gradychastain.headcount.action;

import java.util.List;

/** An ordered list of actions. */
public record ActionList(List<ActionSpec> actions, String path) {
    public static final ActionList EMPTY = new ActionList(List.of(), "");

    public boolean isEmpty() {
        return actions.isEmpty();
    }
}
