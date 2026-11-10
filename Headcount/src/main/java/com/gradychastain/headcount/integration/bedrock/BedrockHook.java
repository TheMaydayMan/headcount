package com.gradychastain.headcount.integration.bedrock;

import java.util.UUID;

/** Detects Bedrock players joining through Geyser. */
public interface BedrockHook {
    String name();

    boolean isBedrock(UUID uuid);
}
