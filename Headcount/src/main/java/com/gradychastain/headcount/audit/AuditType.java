package com.gradychastain.headcount.audit;

import java.util.Locale;

/** Audit event types. Each can be switched off under {@code audit.types} in config.yml using its kebab-case name. */
public enum AuditType {
    HEAD_CREATE,
    HEAD_DELETE,
    HEAD_PLACE,
    HEAD_BREAK,
    HEAD_DESTROYED,
    HEAD_EXPIRED,
    VAULT_IN,
    VAULT_OUT,
    VAULT_ADMIN,
    VAULT_REJECT,
    TRADE_REQUEST,
    TRADE_START,
    TRADE_COMPLETE,
    TRADE_CANCEL,
    TRADE_RETURN,
    KILL_DROP,
    CYCLE_START,
    CYCLE_END,
    SCORE,
    OUTCOME,
    POINTS,
    SHOP_PURCHASE,
    ACTION_APPLY,
    ACTION_REVERT,
    TITLE,
    ADMIN,
    DUPLICATE,
    ITEM_DELIVERY,
    GUI_ITEM_REMOVED;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
