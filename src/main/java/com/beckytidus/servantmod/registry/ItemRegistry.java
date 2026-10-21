package com.beckytidus.servantmod.registry;

import com.beckytidus.servantmod.ServantMod;
import com.beckytidus.servantmod.item.ServantLedgerItem;
import com.beckytidus.servantmod.item.ServantSummonerItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ItemRegistry {
    public static Item SERVANT_SUMMONER;
    public static Item SERVANT_LEDGER;

    public static void register() {
        SERVANT_SUMMONER = register("servant_summoner", ServantSummonerItem::new);
        SERVANT_LEDGER = register("servant_ledger", ServantLedgerItem::new);
    }

    private static Item register(String path, java.util.function.Function<Item.Settings, Item> factory) {
        Identifier id = Identifier.of(ServantMod.MOD_ID, path);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings settings = new Item.Settings().maxCount(1).registryKey(key);
        return Registry.register(Registries.ITEM, key, factory.apply(settings));
    }
}
