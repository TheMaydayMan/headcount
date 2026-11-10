package com.gradychastain.headcount.vault;

/** Head counts for one vault: unique owners, total heads, and copies of the vault owner's own head. */
public record VaultTally(int unique, int total, int own) {
    public static final VaultTally EMPTY = new VaultTally(0, 0, 0);
}
