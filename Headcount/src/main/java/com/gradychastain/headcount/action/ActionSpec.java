package com.gradychastain.headcount.action;

import com.gradychastain.headcount.api.action.ActionHandler;
import com.gradychastain.headcount.util.Cfg;

/**
 * One parsed action: its type, handler, common options and raw settings (kept so a tracked instance can be rebuilt
 * after a restart even if the config changed).
 *
 * @param path where it was defined, e.g. {@code outcomes.yml:rules.tier-1.actions[0]}; the default stacking key
 */
public record ActionSpec(String type, ActionHandler handler, CommonOptions common, Cfg raw, String path) {
    public String stackKey() {
        if (common.key() != null && !common.key().isBlank()) return common.key();
        String k = handler.stackKey();
        return k != null ? type + ":" + k : path;
    }
}
