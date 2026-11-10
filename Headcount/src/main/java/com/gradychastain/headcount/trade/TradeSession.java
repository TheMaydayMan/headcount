package com.gradychastain.headcount.trade;

import com.gradychastain.headcount.util.Items;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An open trade between two players. Each player has their own view (their offer on the left). The real offer
 * items live in each player's own-offer slots; the other side shows display copies only.
 */
final class TradeSession {
    enum State { OPEN, COUNTDOWN, EXECUTING, DONE, CANCELLED }

    final UUID id = UUID.randomUUID();
    final Player a;
    final Player b;
    TradeGui viewA;
    TradeGui viewB;
    boolean confirmA;
    boolean confirmB;
    State state = State.OPEN;
    BukkitTask countdown;
    int secondsLeft;
    String confirmedSnapshot;
    final long started = System.currentTimeMillis();

    TradeSession(Player a, Player b) {
        this.a = a;
        this.b = b;
    }

    boolean has(UUID p) {
        return a.getUniqueId().equals(p) || b.getUniqueId().equals(p);
    }

    Player other(Player p) {
        return p.getUniqueId().equals(a.getUniqueId()) ? b : a;
    }

    TradeGui view(Player p) {
        return p.getUniqueId().equals(a.getUniqueId()) ? viewA : viewB;
    }

    boolean confirmed(Player p) {
        return p.getUniqueId().equals(a.getUniqueId()) ? confirmA : confirmB;
    }

    void setConfirmed(Player p, boolean v) {
        if (p.getUniqueId().equals(a.getUniqueId())) confirmA = v;
        else confirmB = v;
    }

    boolean finished() {
        return state == State.DONE || state == State.CANCELLED || state == State.EXECUTING;
    }

    boolean locked() {
        return state != State.OPEN;
    }

    /** The real items a player has offered right now (read from their own-offer slots). */
    List<ItemStack> offer(Player p) {
        TradeGui v = view(p);
        return v == null ? List.of() : v.readOwn();
    }

    /** A fingerprint of both offers, to check nothing changed between confirming and executing. */
    String snapshot() {
        StringBuilder sb = new StringBuilder();
        for (Player p : List.of(a, b)) {
            sb.append(p.getUniqueId()).append(':');
            for (ItemStack i : offer(p)) sb.append(java.util.Base64.getEncoder().encodeToString(Items.toBytes(i))).append(',');
            sb.append(';');
        }
        return sb.toString();
    }

    List<ItemStack> takeAll(Player p) {
        TradeGui v = view(p);
        if (v == null) return new ArrayList<>();
        return v.takeOwn();
    }
}
