package com.beckytidus.servantmod.entity;

/**
 * The orders a servant can be given. This is the heart of the mod: a servant is
 * defined by the duty it is currently carrying out, not by a relationship stat.
 */
public enum Duty {
    FOLLOW("Following", false),
    STAY("Holding Position", false),
    GUARD("Guarding", false),
    MINE("Mining", true),
    CHOP("Lumberjacking", true),
    FARM("Farming", true),
    HAUL("Collecting", true),
    DEPOSIT("Storing", true);

    private final String label;
    private final boolean work;

    Duty(String label, boolean work) {
        this.label = label;
        this.work = work;
    }

    public String label() {
        return label;
    }

    /** True for duties that consume energy and fill the servant's pack. */
    public boolean isWork() {
        return work;
    }

    public Duty next() {
        Duty[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Parses a duty name or a spoken order. Returns null when nothing matches. */
    public static Duty tryParse(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(java.util.Locale.ROOT);
        for (Duty d : values()) {
            if (d.name().toLowerCase(java.util.Locale.ROOT).equals(n) || d.label.toLowerCase(java.util.Locale.ROOT).equals(n)) {
                return d;
            }
        }
        return switch (n) {
            case "collect", "pickup", "pick", "gather", "sweep" -> HAUL;
            case "store", "chest", "unload", "deposit", "stow" -> DEPOSIT;
            case "lumber", "wood", "tree", "trees", "chop", "logging" -> CHOP;
            case "mine", "mining", "dig", "quarry" -> MINE;
            case "farm", "harvest", "crop", "crops", "sow" -> FARM;
            case "guard", "defend", "protect", "watch" -> GUARD;
            case "stay", "wait", "hold", "stop", "halt" -> STAY;
            case "follow", "come", "heel", "return" -> FOLLOW;
            default -> null;
        };
    }

    public static Duty byName(String name) {
        Duty parsed = tryParse(name);
        return parsed != null ? parsed : FOLLOW;
    }
}
