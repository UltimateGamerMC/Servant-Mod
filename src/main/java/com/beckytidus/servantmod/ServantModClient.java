package com.beckytidus.servantmod;

import com.beckytidus.servantmod.client.render.ServantEntityRenderer;
import com.beckytidus.servantmod.registry.EntityRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ServantModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(EntityRegistry.SERVANT, ServantEntityRenderer::new);
    }
}
