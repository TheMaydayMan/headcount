package com.gradychastain.headcount.action;

import com.gradychastain.headcount.text.Vars;

import java.util.Map;
import java.util.UUID;

/** A timed action being tracked until it ends. Persisted in the active_actions table. */
final class ActiveAction {
    enum State { ACTIVE, PENDING_APPLY, PENDING_REVERT }

    final String id;
    final UUID player;
    final String source;
    final String key;
    final ActionSpec spec;
    final Vars vars;
    final Map<String, Object> data;
    State state;
    final long createdMs;
    long endsMs;        // real time: when it ends (0 = no end)
    long remainingMs;   // online time: what is left
    int cyclesLeft;
    long nextRepeatMs;
    int cycle;          // the cycle it belongs to (actions made during a cycle end belong to the next one)
    boolean dirty;

    ActiveAction(String id, UUID player, String source, String key, ActionSpec spec, Vars vars, Map<String, Object> data,
                 State state, long createdMs, long endsMs, long remainingMs, int cyclesLeft, long nextRepeatMs, int cycle) {
        this.id = id;
        this.player = player;
        this.source = source;
        this.key = key;
        this.spec = spec;
        this.vars = vars;
        this.data = data;
        this.state = state;
        this.createdMs = createdMs;
        this.endsMs = endsMs;
        this.remainingMs = remainingMs;
        this.cyclesLeft = cyclesLeft;
        this.nextRepeatMs = nextRepeatMs;
        this.cycle = cycle;
    }

    DurationSpec duration() {
        return spec.common().duration();
    }

    /** Milliseconds left for time-based durations, or -1 when the end is not time-based. */
    long remaining() {
        DurationSpec d = duration();
        if (d.kind() != DurationSpec.Kind.TIME) return -1;
        return spec.common().onlineTime() ? Math.max(0, remainingMs) : Math.max(0, endsMs - System.currentTimeMillis());
    }
}
