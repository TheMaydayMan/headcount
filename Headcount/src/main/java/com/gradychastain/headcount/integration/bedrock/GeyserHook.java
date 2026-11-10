package com.gradychastain.headcount.integration.bedrock;

import org.geysermc.geyser.api.GeyserApi;

import java.util.UUID;

public final class GeyserHook implements BedrockHook {
    @Override public String name() { return "Geyser"; }

    @Override
    public boolean isBedrock(UUID uuid) {
        try {
            return GeyserApi.api().isBedrockPlayer(uuid);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
