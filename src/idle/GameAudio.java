package idle;

import javax.sound.sampled.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

// keep audio loading and mixing off the swing thread
// one owner follows the menu and expedition so returning to the menu cannot leave duplicate music
public final class GameAudio implements AutoCloseable {
    static final Path ROOT = Paths.get("assets", "packs", "NimbeKit_Fantasy_RPG_SFX_160", "WAV");
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dungeon-audio"); t.setDaemon(true); return t;
    });
    private final Map<String, Clip> effects = new HashMap<>();
    private final Set<String> unavailable = new HashSet<>();
    private final Map<String, Long> lastPlayed = new HashMap<>();
    private final Clip[] themes = new Clip[2];
    private final double[] levels = new double[2];
    private volatile boolean closed;
    private boolean initialized;
    private int desired;
    private double musicVolume = .35, effectsVolume = .55;
    private Clip ambience;
    private java.util.function.Consumer<String> recorder;

    // create a silent owner until the visible application starts audio
    public GameAudio() {}

    // record presentation cues in tests without opening a mixer or playing sound
    GameAudio(java.util.function.Consumer<String> recorder) { this.recorder = recorder; }

    // decode the pack's 24 bit wav files into portable signed 16 bit pcm
    // tests use this path too, without requiring a speaker or an audio mixer
    static byte[] readPcm(Path path) throws Exception {
        try (AudioInputStream original = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat source = original.getFormat();
            AudioFormat target = new AudioFormat(source.getSampleRate(), 16, source.getChannels(), true, false);
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(target, original)) { return decoded.readAllBytes(); }
        }
    }

    // select an attack cue by role even when a hero has no purchased equipment
    static String heroCue(Catalog.Role role, boolean healing) {
        if (healing) return "magic/fr_magic_restoration";
        switch (role) {
            case WIZARD: return "magic/fr_magic_ember_cast";
            case PRIEST: return "magic/fr_magic_sunburst";
            case ARCHER: return "combat/fr_combat_arrow_release";
            case LANCER: case SOLDIER: return "combat/fr_combat_spear_thrust";
            case ARMORED_AXEMAN: return "combat/fr_combat_heavy_attack";
            default: return "combat/fr_combat_sword_swing";
        }
    }

    // match enemy attacks to their weapons or creature family
    // textures substitute for missing creature recordings rather than pretending to be unique voices
    static String enemyCue(String name) {
        String id = Catalog.enemySprite(name);
        if (id.contains("archer")) return "combat/fr_combat_arrow_release";
        if (id.contains("necromancer") || id.contains("warlock")) return "magic/fr_magic_shadow_veil";
        if (id.contains("flame") || id.contains("lava") || id.contains("ghostfire")) return "magic/fr_magic_ember_cast";
        if (id.contains("demon")) return "magic/fr_magic_gravity_pulse";
        if (id.contains("eyeball")) return "magic/fr_magic_crystal_beam";
        if (id.contains("slime") || id.contains("blood_monster")) return "magic/fr_magic_poison_bubble";
        if (id.contains("bat")) return "ambience/fr_ambience_wing_rush";
        if (id.contains("were") || id.contains("hellhound")) return "combat/fr_combat_unarmed_hit";
        if (id.contains("minotaur") || id.contains("orc")) return "combat/fr_combat_heavy_attack";
        return "combat/fr_combat_sword_swing";
    }

    // choose a second cue for the contact moment of an attack
    static String impactCue(boolean incoming, Catalog.Role role, String enemy, boolean healing) {
        if (healing) return null;
        if (incoming) return role == Catalog.Role.WIZARD || role == Catalog.Role.PRIEST || role == Catalog.Role.ARCHER
            ? "combat/fr_combat_cloth_armor_hit" : "combat/fr_combat_chain_armor_hit";
        if (role == Catalog.Role.WIZARD || role == Catalog.Role.PRIEST) return "magic/fr_magic_rune_charge";
        String id = Catalog.enemySprite(enemy);
        boolean hard = id.contains("skeleton") || id.contains("armored") || id.contains("knight") || id.contains("golem");
        if (role == Catalog.Role.ARCHER) return hard ? "combat/fr_combat_arrow_hit_stone" : "combat/fr_combat_arrow_hit_wood";
        return hard ? "combat/fr_combat_blade_clash" : "combat/fr_combat_unarmed_hit";
    }

    // crossfade when the visible encounter changes, including a saved boss campaign
    // repeated animation callbacks for the same scene do not restart the music
    public void scene(boolean boss) {
        if (closed) return;
        if (recorder != null) { recorder.accept(boss ? "scene:boss" : "scene:dungeon"); return; }
        worker.execute(() -> {
            desired = boss ? 1 : 0;
            if (initialized) return;
            initialized = true;
            for (int i = 0; i < 2; i++) {
                try { themes[i] = open(musicPcm(i == 1), new AudioFormat(22050, 16, 1, true, false)); }
                catch (Exception e) { report("music", e); }
            }
            ambience = effect("ambience/fr_ambience_crypt_air_loop");
            worker.scheduleAtFixedRate(this::mix, 0, 50, TimeUnit.MILLISECONDS);
        });
    }

    // fade themes over about a second and stop muted loops to release playback activity
    private void mix() {
        if (closed) return;
        for (int i = 0; i < themes.length; i++) {
            double target = i == desired ? musicVolume : 0;
            levels[i] += Math.max(-.025, Math.min(.025, target - levels[i]));
            loop(themes[i], levels[i]);
        }
        loop(ambience, effectsVolume * .12);
    }

    // keep a loop running only while audible and apply its current gain
    private static void loop(Clip clip, double volume) {
        if (clip == null) return;
        gain(clip, volume);
        if (volume <= .001) clip.stop();
        else if (!clip.isRunning()) clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    // update both channels after settings have been saved
    // zero mutes a channel and also stops effects that are already playing
    public void volumes(int music, int effectsLevel) {
        if (closed) return;
        worker.execute(() -> {
            musicVolume = Math.max(0, Math.min(100, music)) / 100.0;
            effectsVolume = Math.max(0, Math.min(100, effectsLevel)) / 100.0;
            for (Clip c : effects.values()) { gain(c, effectsVolume); if (effectsVolume == 0) c.stop(); }
        });
    }

    // avoid stacked identical sounds from four enemies or a hit spilling across several targets
    // cap concurrent effects so a busy battle cannot exhaust mixer lines
    public void play(String cue) {
        if (closed || cue == null) return;
        if (recorder != null) { recorder.accept(cue); return; }
        worker.execute(() -> {
            if (effectsVolume <= 0) return;
            long now = System.nanoTime();
            if (now - lastPlayed.getOrDefault(cue, 0L) < 120_000_000L) return;
            if (effects.values().stream().filter(Clip::isRunning).count() >= 10) return;
            Clip c = effect(cue);
            if (c == null) return;
            lastPlayed.put(cue, now); c.stop(); c.setFramePosition(0); gain(c, effectsVolume * .6); c.start();
        });
    }

    // clear pending combat tails when leaving an expedition
    public void stopEffects() {
        if (!closed) worker.execute(() -> { for (Clip c : effects.values()) c.stop(); lastPlayed.clear(); });
    }

    // load each selected sound once and remember failures to avoid repeated disk errors
    private Clip effect(String cue) {
        if (effects.containsKey(cue)) return effects.get(cue);
        if (unavailable.contains(cue)) return null;
        try {
            Path path = ROOT.resolve(cue + ".wav");
            AudioFormat source = AudioSystem.getAudioFileFormat(path.toFile()).getFormat();
            Clip c = open(readPcm(path), new AudioFormat(source.getSampleRate(), 16, source.getChannels(), true, false));
            effects.put(cue, c); return c;
        } catch (Exception e) { unavailable.add(cue); report(cue, e); return null; }
    }

    // open an owned mixer line and close it if initialization fails
    private static Clip open(byte[] pcm, AudioFormat format) throws Exception {
        Clip clip = AudioSystem.getClip();
        try { clip.open(format, pcm, 0, pcm.length); return clip; }
        catch (Exception e) { clip.close(); throw e; }
    }

    // apply logarithmic volume while tolerating mixers without a gain control
    private static void gain(Clip clip, double level) {
        if (clip != null && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            control.setValue((float) Math.max(control.getMinimum(), Math.min(control.getMaximum(), 20 * Math.log10(Math.max(.0001, level)))));
        }
    }

    // report unavailable audio without stopping the game or opening repeated error dialogs
    private static void report(String cue, Exception e) { System.err.println("Audio unavailable (" + cue + "): " + e.getMessage()); }

    // compose original minor-key loops because the supplied pack contains ambience but no music
    // repeating bass harmony and a soft plucked melody keep the dungeon theme consistent
    // the boss variation uses a faster pulse, a lower bass, and stronger percussion
    static byte[] musicPcm(boolean boss) {
        int rate = 22050;
        double beat = boss ? .375 : .5;
        int samples = (int) (32 * beat * rate);
        byte[] pcm = new byte[samples * 2];
        int[] roots = {50, 46, 53, 48};
        int[] melody = {0, 7, 12, 15, 12, 7, 3, 7};
        for (int i = 0; i < samples; i++) {
            double time = i / (double) rate;
            int step = (int) (time / beat);
            double local = time % beat;
            int root = roots[step / 8];
            double f = 440 * Math.pow(2, (root + melody[step % 8] - 69) / 12.0);
            double envelope = Math.min(1, local / .015) * Math.pow(Math.max(0, 1 - local / beat), 2);
            double pluck = (Math.sin(2 * Math.PI * f * time) + .25 * Math.sin(4 * Math.PI * f * time)) * envelope;
            double bassFrequency = 440 * Math.pow(2, (root - (boss ? 24 : 12) - 69) / 12.0);
            double bass = Math.sin(2 * Math.PI * bassFrequency * time) * Math.sin(Math.PI * local / beat);
            double drum = boss && step % 2 == 0 ? Math.sin(2 * Math.PI * (75 * local - 32 * local * local)) * Math.exp(-18 * local) : 0;
            double value = .20 * pluck + .16 * bass + .20 * drum;
            short sample = (short) (Math.max(-1, Math.min(1, value)) * 32767);
            pcm[i * 2] = (byte) sample; pcm[i * 2 + 1] = (byte) (sample >> 8);
        }
        return pcm;
    }

    // release every mixer line on quit, even when files or audio devices were unavailable
    @Override public void close() {
        if (closed) return;
        closed = true;
        worker.execute(() -> {
            for (Clip c : effects.values()) c.close();
            for (Clip c : themes) if (c != null) c.close();
            worker.shutdown();
        });
    }
}
