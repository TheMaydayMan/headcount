package com.gradychastain.headcount.integration.bedrock;

import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

public final class FloodgateHook implements BedrockHook {
    @Override public String name() { return "Floodgate"; }

    @Override
    public boolean isBedrock(UUID uuid) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
