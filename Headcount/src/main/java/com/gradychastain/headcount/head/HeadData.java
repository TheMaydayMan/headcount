package com.gradychastain.headcount.head;

import java.util.UUID;

/**
 * The economy data on a head: whose head it is, which cycle it belongs to and its unique ID.
 *
 * @param source how it was made: {@code cycle-start}, {@code kill}, {@code admin}, {@code action}, ...
 * @param token  the token of the cycle run it was made in (see {@link com.gradychastain.headcount.cycle.Cycle#token()});
 *               null on heads made before 1.1
 */
public record HeadData(UUID id, UUID owner, String ownerName, int cycle, long created, String source, String token) {}
