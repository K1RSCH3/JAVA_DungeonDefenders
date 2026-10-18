package idle;

import java.util.*;

// exercise sound mapping and animation triggers without sending audio to speakers
public final class AudioTest {
    // stop a check with a readable explanation
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    // decode assigned cues and verify healing, delayed attacks, and boss transitions
    static void run() throws Exception {
        Set<String> cues = new HashSet<>();
        for (Catalog.Role role : Catalog.Role.values()) {
            cues.add(GameAudio.heroCue(role, false));
            cues.add(GameAudio.impactCue(false, role, "Skeleton", false));
            cues.add(GameAudio.impactCue(false, role, "Werewolf", false));
            cues.add(GameAudio.impactCue(true, role, "Skeleton", false));
        }
        for (String enemy : Catalog.ENEMIES) cues.add(GameAudio.enemyCue(enemy));
        cues.add(GameAudio.heroCue(Catalog.Role.PRIEST, true));
        cues.add(GameAudio.impactCue(true, null, "Skeleton", false));
        cues.addAll(Arrays.asList("ambience/fr_ambience_crypt_air_loop", "ambience/fr_ambience_distant_roar_texture", "feedback/fr_feedback_combat_victory", "feedback/fr_feedback_combat_defeat", "ui/fr_ui_confirmation_seal"));
        for (String cue : cues) check(GameAudio.readPcm(GameAudio.ROOT.resolve(cue + ".wav")).length > 0, "Decodable cue: " + cue);
        check(GameAudio.enemyCue("Skeleton Archer").contains("arrow"), "Skeleton archer uses a bow");
        check(GameAudio.enemyCue("Necromancer").contains("shadow"), "Necromancer uses shadow magic");
        check(GameAudio.enemyCue("Flame-Golem").contains("ember"), "Flame boss uses fire");
        byte[] normal = GameAudio.musicPcm(false), boss = GameAudio.musicPcm(true);
        check(normal.length == 22050 * 16 * 2 && boss.length == 22050 * 12 * 2, "Complete music loop durations");
        check(!Arrays.equals(normal, boss), "Different boss theme");
        int peak = 0;
        for (int i = 0; i < normal.length; i += 2) peak = Math.max(peak, Math.abs((short) ((normal[i] & 255) | (normal[i + 1] << 8))));
        check(peak > 100 && peak < 30000, "Music has signal and mixing headroom");
        Properties settings = new Properties(); settings.setProperty("musicVolume", "500");
        check(MainMenu.volume(settings, "musicVolume", 35) == 100, "Clamp volume");
        settings.setProperty("musicVolume", "invalid");
        check(MainMenu.volume(settings, "musicVolume", 35) == 35 && MainMenu.volume(settings, "effectsVolume", 55) == 55, "Legacy settings defaults");
        List<String> heard = new ArrayList<>();
        try (GameAudio audio = new GameAudio(heard::add)) {
            GameState state = new GameState(); BattlePanel panel = new BattlePanel(state); panel.setAudio(audio);
            for (int i = 0; i < 130; i++) panel.animate();
            GameState.Hero priest = state.heroes.get(Catalog.Role.PRIEST.ordinal());
            GameState.Hero knight = state.heroes.get(Catalog.Role.KNIGHT.ordinal());
            GameState.Hero wizard = state.heroes.get(Catalog.Role.WIZARD.ordinal());
            state.ticks++;
            state.combatEvents.add(new GameState.CombatEvent(priest, knight, -1, 1, 3, true, false));
            state.combatEvents.add(new GameState.CombatEvent(wizard, null, 0, 1, 3, false, false));
            state.combatEvents.add(new GameState.CombatEvent(wizard, null, 1, 1, 2, false, false));
            state.combatEvents.add(new GameState.CombatEvent(null, knight, 0, 1, 2, false, true));
            panel.animate();
            check(heard.contains("magic/fr_magic_restoration") && !heard.contains("magic/fr_magic_ember_cast"), "Healing precedes delayed wizard cast");
            for (int i = 0; i < 50; i++) panel.animate();
            check(Collections.frequency(heard, "magic/fr_magic_ember_cast") == 1, "Spillover and redraws do not duplicate casting");
            check(heard.contains("combat/fr_combat_chain_armor_hit"), "Incoming impact handles null hero");
            state.combatEvents.clear(); state.stage = 25; state.enemyHp = state.enemyMaxHp(); panel.animate(); panel.animate();
            check(Collections.frequency(heard, "scene:boss") == 1, "Entering boss switches music once");
            state.rest = 5; panel.animate(); panel.animate();
            check(Collections.frequency(heard, "feedback/fr_feedback_combat_defeat") == 1, "Regroup cue plays once");
            state.rest = 0; state.stage = 26; panel.animate();
            check(Collections.frequency(heard, "scene:dungeon") == 2, "Dungeon music returns after boss");
            state.stage = 25; panel.animate();
            int beforeVictory = Collections.frequency(heard, "scene:dungeon");
            state.ticks++; state.kills++; state.stage = 26; panel.animate();
            check(Collections.frequency(heard, "scene:dungeon") == beforeVictory, "Boss music stays during its death scene");
            for (int i = 0; i < 130; i++) panel.animate();
            check(Collections.frequency(heard, "scene:dungeon") == beforeVictory + 1, "Normal music resumes when the next encounter enters");
            check(Collections.frequency(heard, "feedback/fr_feedback_combat_victory") == 1, "Victory feedback waits for attacks and plays once");
        }
    }
}
