package com.gradychastain.headcount.api.status;

import com.gradychastain.headcount.api.ConfigView;

/**
 * A kind of status source usable as {@code source: <id>} in statuses.yml. Register with
 * {@link com.gradychastain.headcount.api.HeadcountAPI#registerStatusSource(StatusSourceType)}.
 */
public interface StatusSourceType {
    /** The {@code source:} value, lowercase with dashes, e.g. {@code my-plugin-flag}. */
    String id();

    /**
     * Builds a check from the status's settings.
     *
     * @param settings the status's block in statuses.yml
     * @param statuses lets combination sources test other statuses by name
     * @throws IllegalArgumentException when the settings are invalid (the message is shown to the server owner)
     */
    StatusCheck create(ConfigView settings, StatusResolver statuses);
}
