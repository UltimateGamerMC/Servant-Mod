package com.beckytidus.servantmod.event;

import com.beckytidus.servantmod.entity.ServantEntity;
import com.beckytidus.servantmod.registry.EntityRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public class EntityAttributeHandler {
    public static void register() {
        FabricDefaultAttributeRegistry.register(EntityRegistry.SERVANT, ServantEntity.createServantAttributes());
    }
}
