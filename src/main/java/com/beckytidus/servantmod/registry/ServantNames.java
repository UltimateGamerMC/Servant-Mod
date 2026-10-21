package com.beckytidus.servantmod.registry;

import net.minecraft.util.math.random.Random;

import java.util.List;

/** Names a servant can be issued at summoning. */
public final class ServantNames {
    private static final List<String> NAMES = List.of(
        "Alfred", "Barrett", "Cedric", "Digby", "Edmund", "Fenwick", "Giles", "Hobson",
        "Ives", "Jarvis", "Kingsley", "Latham", "Marlowe", "Norwood", "Osgood", "Pemberton",
        "Quill", "Ralston", "Selby", "Thorne", "Ulric", "Vance", "Whitlock", "Yates",
        "Ada", "Beatrix", "Clara", "Doris", "Edith", "Flora", "Greta", "Hattie",
        "Iris", "Josephine", "Keziah", "Lottie", "Mabel", "Nell", "Opal", "Prudence",
        "Rowena", "Sybil", "Tessa", "Verity", "Winnie", "Agnes", "Blythe", "Cordelia"
    );

    private ServantNames() {}

    public static String pick(Random random) {
        return NAMES.get(random.nextInt(NAMES.size()));
    }
}
