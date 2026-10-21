package com.beckytidus.servantmod.registry;

import net.minecraft.util.math.random.Random;

/** Texture variants for the servant's uniform. */
public final class ServantSkins {
    private static final int TEXTURE_COUNT = 6;

    private ServantSkins() {}

    public static String pick(Random random) {
        return String.valueOf(random.nextBetween(1, TEXTURE_COUNT));
    }

    public static int count() {
        return TEXTURE_COUNT;
    }
}
