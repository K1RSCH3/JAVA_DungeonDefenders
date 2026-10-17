package idle;

import java.util.*;

/** deterministic encounter composition keeps saves compact and replayable */
public final class Encounters {
    public static final int BOSS_INTERVAL = 25;
    public static final String[] BOSSES = {"Elite Orc", "Necromancer", "Minotaur", "Black-Knight_C", "Flame-Golem"};
    public static final String[] TITLES = {"Gorath, the Iron Warlord", "Mordren, Keeper of Graves", "The Labyrinth Tyrant", "The Black Castellan", "The Cinder Colossus"};
    private static final java.util.List<String> NORMAL = new ArrayList<>();
    static { for (String enemy : Catalog.ENEMIES) if (!Arrays.asList(BOSSES).contains(enemy)) NORMAL.add(enemy); }

    // prevent construction because encounter rules are shared helpers
    private Encounters() {}

    // group variants by creature family
    // this lets armored skeletons join skeleton patrols without mixing them with orcs
    public static String species(String name) {
        String id = Catalog.enemySprite(name);
        for (String family : new String[]{"skeleton", "orc", "demoness", "demon", "blood_monster", "black_knight", "slime"})
            if (id.contains(family)) return family;
        if (id.equals("bat") || id.equals("hellbat")) return "bat";
        return id;
    }

    // check the fixed boss interval for a positive stage number
    // both state logic and rendering use this rule
    public static boolean boss(int stage) { return stage % BOSS_INTERVAL == 0; }

    // select the rotating boss for a boss stage
    // call this only after confirming the stage is a boss stage
    public static int bossIndex(int stage) { return (stage / BOSS_INTERVAL - 1) % BOSSES.length; }

    // build a repeatable lineup for the stage
    // normal patrols use four same-species members and repeat variants when needed
    public static String[] members(int stage) {
        if (boss(stage)) return new String[]{BOSSES[bossIndex(stage)]};
        String[] group = new String[4];
        String leader = NORMAL.get((stage - 1) % NORMAL.size());
        java.util.List<String> family = new ArrayList<>();
        for (String name : NORMAL) if (species(name).equals(species(leader))) family.add(name);
        for (int i = 0; i < 4; i++) group[i] = family.get((stage - 1 + i) % family.size());
        return group;
    }

    // return the named boss title or the normal patrol label
    // victory messages and stage displays use this text
    public static String title(int stage) { return boss(stage) ? TITLES[bossIndex(stage)] : "Hostile patrol"; }

    // choose a creature's visible battlefield height
    // small flyers stay small and bosses receive a larger presence
    public static int height(String name, boolean boss) {
        if (boss) return 66;
        String id = Catalog.enemySprite(name);
        if (id.contains("bat")) return 19;
        if (id.contains("slime")) return 23;
        if (id.contains("eyeball") || id.contains("ghostfire")) return 27;
        if (id.contains("werebear") || id.contains("orc_rider") || id.contains("armored_orc")) return 46;
        if (id.contains("hellhound")) return 29;
        return 36;
    }
}
