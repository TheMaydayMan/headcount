package com.gradychastain.headcount.api.action;

import com.gradychastain.headcount.api.ConfigView;

/**
 * A kind of action usable as {@code type: <id>} in any action list. Register with
 * {@link com.gradychastain.headcount.api.HeadcountAPI#registerActionType(ActionType)}.
 */
public interface ActionType {
    /** The {@code type:} value, lowercase with dashes. */
    String id();

    /**
     * The setting a shorthand value fills in. With {@code primaryKey() == "text"}, {@code "my-type: hello"} in a list
     * means {@code {type: my-type, text: hello}}.
     */
    default String primaryKey() {
        return "value";
    }

    /**
     * Builds a handler from the action's settings (common options such as duration and chance are handled by
     * Headcount and are also visible here).
     *
     * @throws IllegalArgumentException when the settings are invalid; the message is shown to the server owner
     */
    ActionHandler parse(ConfigView settings);
}
