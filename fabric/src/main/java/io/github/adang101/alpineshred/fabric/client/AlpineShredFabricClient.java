package io.github.adang101.alpineshred.fabric.client;

import io.github.adang101.alpineshred.client.AlpineShredClient;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entry point. */
public final class AlpineShredFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AlpineShredClient.init();
    }
}
