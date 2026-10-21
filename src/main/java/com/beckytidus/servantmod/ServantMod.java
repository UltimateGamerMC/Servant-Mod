package com.beckytidus.servantmod;

import com.beckytidus.servantmod.command.ServantCommand;
import com.beckytidus.servantmod.event.EntityAttributeHandler;
import com.beckytidus.servantmod.interaction.EntityInteractionHandler;
import com.beckytidus.servantmod.registry.EntityRegistry;
import com.beckytidus.servantmod.registry.ItemRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServantMod implements ModInitializer {
    public static final String MOD_ID = "servant-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Servant Mod...");

        EntityRegistry.register();
        ItemRegistry.register();
        EntityAttributeHandler.register();
        EntityInteractionHandler.register();
        ServantCommand.register();

        RegistryKey<ItemGroup> ingredientsKey = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.ofVanilla("ingredients"));
        ItemGroupEvents.modifyEntriesEvent(ingredientsKey).register(entries -> {
            entries.add(ItemRegistry.SERVANT_SUMMONER);
            entries.add(ItemRegistry.SERVANT_LEDGER);
        });

        LOGGER.info("Servant Mod loaded.");
    }
}
