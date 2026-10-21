package com.beckytidus.servantmod.entity;

import net.minecraft.util.math.random.Random;

/** All the things a servant says. No romance - this is staff. */
public final class ServantSpeech {

    private static final String[] ACKNOWLEDGE = {
        "As you command.",
        "At once.",
        "Right away, master.",
        "Consider it done.",
        "Very good.",
        "On it."
    };

    private static final String[] TIRED = {
        "I'm spent... might I have something to eat?",
        "I need a moment. My arms are heavy.",
        "Begging your pardon, I'm famished.",
        "I can't lift another block without a bite."
    };

    private static final String[] FULL = {
        "My pack is full, master.",
        "I've no room left to carry anything.",
        "Shall I store this somewhere? My pack is full.",
        "I'm loaded to the brim."
    };

    private static final String[] IDLE = {
        "At your service.",
        "Awaiting orders.",
        "All is well.",
        "Standing by.",
        "Whenever you're ready, master."
    };

    private static final String[] GUARD_ALERT = {
        "Intruders! I'll handle it.",
        "Something's coming. Stay behind me.",
        "I see them. Hold your ground.",
        "Trouble. I'll deal with it."
    };

    private static final String[] DEPOSIT_DONE = {
        "Stored and accounted for.",
        "Everything's put away.",
        "Chest's filled. What next?",
        "All tucked away, master."
    };

    private static final String[] NOTHING = {
        "Nothing to store, master.",
        "My pack is already empty.",
        "I've nothing to put away."
    };

    private static final String[] RECALL = {
        "Coming!",
        "At once, master.",
        "On my way.",
        "Right behind you."
    };

    private ServantSpeech() {}

    public static String acknowledge(Random r) { return pick(ACKNOWLEDGE, r); }
    public static String tired(Random r) { return pick(TIRED, r); }
    public static String full(Random r) { return pick(FULL, r); }
    public static String idle(Random r) { return pick(IDLE, r); }
    public static String guardAlert(Random r) { return pick(GUARD_ALERT, r); }
    public static String depositDone(Random r) { return pick(DEPOSIT_DONE, r); }
    public static String nothing(Random r) { return pick(NOTHING, r); }
    public static String recall(Random r) { return pick(RECALL, r); }

    private static String pick(String[] pool, Random r) {
        return pool[r.nextInt(pool.length)];
    }
}
