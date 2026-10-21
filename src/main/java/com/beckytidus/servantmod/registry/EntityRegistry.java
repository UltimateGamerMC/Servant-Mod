package com.beckytidus.servantmod.registry;

import com.beckytidus.servantmod.ServantMod;
import com.beckytidus.servantmod.entity.ServantEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class EntityRegistry {
    public static EntityType<ServantEntity> SERVANT;

    public static void register() {
        Identifier id = Identifier.of(ServantMod.MOD_ID, "servant");
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);

        SERVANT = Registry.register(
                Registries.ENTITY_TYPE,
                id,
                FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, ServantEntity::new)
                        .dimensions(EntityDimensions.changing(0.6f, 1.9f))
                        .build(key)
        );
    }
}
