package idle;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import idle.Catalog.*;
import idle.GameState.Hero;

public final class SaveStore implements AutoCloseable {
    private final Path file;
    private FileChannel channel;
    private FileLock lock;

    // remember the save path as an absolute path
    // tests can pass an isolated temporary file instead of the player's campaign
    public SaveStore(Path file) { this.file = file.toAbsolutePath(); }

    // acquire exclusive access to the campaign's lock file
    // a second game instance must not overwrite the same save
    public void lock() throws IOException {
        Files.createDirectories(file.getParent());
        channel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try { lock = channel.tryLock(); }
        catch (OverlappingFileLockException e) { throw new IOException("Dungeon Defenders is already running.", e); }
        if (lock == null) throw new IOException("Dungeon Defenders is already running.");
    }

    // read and validate a campaign, or create a fresh state when no file exists
    // older versions keep their progress and start new upgrade tracks at zero
    public GameState load() throws IOException {
        GameState s = new GameState();
        if (!Files.exists(file)) return s;
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(file)) {
            p.load(in);
            s.campaignName = p.getProperty("campaignName", "Existing Expedition");
            String version = p.getProperty("version");
            if (!"1".equals(version) && !"2".equals(version) && !"3".equals(version) && !"4".equals(version)) throw new IllegalArgumentException("Unsupported save version");
            s.stage = integer(p, "stage", 1, GameState.MAX_STAGE);
            s.gold = number(p, "gold", 0, GameState.MAX_GOLD);
            double oldMax = "1".equals(version) ? (45 + s.stage * 20.0) * (s.stage % 10 == 0 ? 2.5 : 1) : s.enemyMaxHp();
            // preserve the remaining health fraction when loading the former first-boss balance
            boolean oldBossBalance = !"1".equals(version) && s.stage == 25 && !"2".equals(p.getProperty("combatBalance"));
            if (oldBossBalance) oldMax = (45 + s.stage * 20.0) * 4;
            double savedHp = number(p, "enemyHp", .001, oldMax);
            s.enemyHp = "1".equals(version) || oldBossBalance ? Math.max(.001, Math.min(s.enemyMaxHp(), savedHp / oldMax * s.enemyMaxHp())) : savedHp;
            s.kills = integer(p, "kills", 0, Integer.MAX_VALUE);
            s.ticks = integer(p, "ticks", 0, 999999); s.rest = integer(p, "rest", 0, 5);
            s.savedAt = Long.parseLong(p.getProperty("savedAt"));
            if (s.savedAt < 0) throw new IllegalArgumentException("Invalid timestamp");
            for (Hero h : s.heroes) {
                String key = h.role.name() + ".";
                h.level = integer(p, key + "level", 0, GameState.MAX_LEVEL);
                for (GameState.Upgrade upgrade : GameState.Upgrade.values()) {
                    int rank = ("3".equals(version) || "4".equals(version)) ? integer(p, key + "upgrade." + upgrade.name(), 0, GameState.MAX_LEVEL) : 0;
                    if (rank > 0 && (h.level == 0 || upgrade == GameState.Upgrade.HEALING && h.role != Role.PRIEST)) throw new IllegalArgumentException("Invalid upgrade");
                    h.upgrades.put(upgrade, rank);
                }
                String active = p.getProperty(key + "active");
                if (!"true".equals(active) && !"false".equals(active)) throw new IllegalArgumentException("Invalid party");
                h.active = Boolean.parseBoolean(active);
                if (h.active && h.level == 0) throw new IllegalArgumentException("Unrecruited party member");
                h.gear.clear();
                for (Slot slot : Slot.values()) {
                    String id = p.getProperty(key + slot.name(), "");
                    if (!id.isEmpty()) {
                        Item item = Catalog.item(id);
                        if (item.slot != slot || !h.role.accepts(item) || h.level == 0) throw new IllegalArgumentException("Incompatible equipment");
                        h.gear.put(slot, item);
                    }
                }
                h.hp = number(p, key + "hp", 0, h.maxHp());
                h.recoverySeconds = "4".equals(version) ? integer(p, key + "recoverySeconds", 0, GameState.CAMP_RECOVERY_SECONDS) : 0;
                if (h.recoverySeconds > 0 && (h.active || h.level == 0)) throw new IllegalArgumentException("Invalid recovery state");
                // older saves may contain an unconscious hero already waiting at camp
                // give them a recovery timer so they cannot remain permanently undeployable
                if (h.level > 0 && !h.active && h.hp <= 0 && h.recoverySeconds == 0) h.recoverySeconds = GameState.CAMP_RECOVERY_SECONDS;
            }
            if (s.activeCount() > 4 || s.heroes.stream().noneMatch(h -> h.level > 0)) throw new IllegalArgumentException("Party size");
            s.rescueDowned();
            for (Item item : Catalog.ITEMS) s.inventory.put(item.id, integer(p, "bag." + item.id, 0, 1008));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IOException("Invalid save: " + file.getFileName() + ". Preserve it and move it aside to start fresh.", e);
        }
        return s;
    }

    // read a required whole-number property within its allowed range
    // reject malformed data instead of quietly changing the player's progress
    private static int integer(Properties p, String key, int min, int max) {
        int n = Integer.parseInt(p.getProperty(key));
        if (n < min || n > max) throw new IllegalArgumentException(key); return n;
    }

    // read a required finite numeric value within its bounds
    // reject nan and infinity as well as impossible health or gold values
    private static double number(Properties p, String key, double min, double max) {
        double n = Double.parseDouble(p.getProperty(key));
        if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException(key); return n;
    }

    // write the complete state to a temporary file before replacing the campaign file
    // atomic replacement is preferred so a failed write does not leave half a save
    public void save(GameState s) throws IOException {
        Properties p = new Properties();
        p.setProperty("campaignName", s.campaignName);
        p.setProperty("combatBalance", "2");
        p.setProperty("version", "4"); p.setProperty("stage", "" + s.stage); p.setProperty("gold", "" + s.gold);
        p.setProperty("enemyHp", "" + s.enemyHp); p.setProperty("kills", "" + s.kills);
        p.setProperty("ticks", "" + s.ticks); p.setProperty("rest", "" + s.rest);
        p.setProperty("savedAt", "" + System.currentTimeMillis());
        for (Hero h : s.heroes) {
            String key = h.role.name() + ".";
            p.setProperty(key + "recoverySeconds", "" + h.recoverySeconds);
            p.setProperty(key + "level", "" + h.level); p.setProperty(key + "active", "" + h.active); p.setProperty(key + "hp", "" + h.hp);
            for (GameState.Upgrade upgrade : GameState.Upgrade.values()) p.setProperty(key + "upgrade." + upgrade.name(), "" + h.rank(upgrade));
            h.gear.forEach((slot, item) -> p.setProperty(key + slot.name(), item.id));
        }
        for (Item item : Catalog.ITEMS) p.setProperty("bag." + item.id, "" + s.inventory.getOrDefault(item.id, 0));
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "save-", ".tmp");
        try {
            try (Writer out = Files.newBufferedWriter(temp)) { p.store(out, "Dungeon Defenders / save version 4"); }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    // release the campaign lock and channel when leaving the application
    // repeated cleanup is safe after a partially failed startup
    @Override public void close() throws IOException {
        try { if (lock != null && lock.isValid()) lock.release(); }
        finally { lock = null; if (channel != null) { channel.close(); channel = null; } }
    }
}
