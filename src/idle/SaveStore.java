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
    public SaveStore(Path file) { this.file = file.toAbsolutePath(); }
    public void lock() throws IOException {
        Files.createDirectories(file.getParent());
        channel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try { lock = channel.tryLock(); }
        catch (OverlappingFileLockException e) { throw new IOException("Ashen Defenders is already running.", e); }
        if (lock == null) throw new IOException("Ashen Defenders is already running.");
    }
    public GameState load() throws IOException {
        GameState s = new GameState();
        if (!Files.exists(file)) return s;
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(file)) {
            p.load(in);
            if (!"1".equals(p.getProperty("version"))) throw new IllegalArgumentException("Unsupported save version");
            s.stage = integer(p, "stage", 1, GameState.MAX_STAGE);
            s.gold = number(p, "gold", 0, GameState.MAX_GOLD);
            s.enemyHp = number(p, "enemyHp", .001, s.enemyMaxHp());
            s.kills = integer(p, "kills", 0, Integer.MAX_VALUE);
            s.ticks = integer(p, "ticks", 0, 999999); s.rest = integer(p, "rest", 0, 5);
            s.savedAt = Long.parseLong(p.getProperty("savedAt"));
            if (s.savedAt < 0) throw new IllegalArgumentException("Invalid timestamp");
            for (Hero h : s.heroes) {
                String key = h.role.name() + ".";
                h.level = integer(p, key + "level", 0, GameState.MAX_LEVEL);
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
            }
            if (s.activeCount() < 1 || s.activeCount() > 4) throw new IllegalArgumentException("Party size");
            if (s.rest == 0 && s.heroes.stream().noneMatch(Hero::alive)) throw new IllegalArgumentException("Party cannot fight");
            for (Item item : Catalog.ITEMS) s.inventory.put(item.id, integer(p, "bag." + item.id, 0, 1008));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IOException("Invalid save: " + file.getFileName() + ". Preserve it and move it aside to start fresh.", e);
        }
        return s;
    }
    private static int integer(Properties p, String key, int min, int max) {
        int n = Integer.parseInt(p.getProperty(key));
        if (n < min || n > max) throw new IllegalArgumentException(key); return n;
    }
    private static double number(Properties p, String key, double min, double max) {
        double n = Double.parseDouble(p.getProperty(key));
        if (!Double.isFinite(n) || n < min || n > max) throw new IllegalArgumentException(key); return n;
    }
    public void save(GameState s) throws IOException {
        Properties p = new Properties();
        p.setProperty("version", "1"); p.setProperty("stage", "" + s.stage); p.setProperty("gold", "" + s.gold);
        p.setProperty("enemyHp", "" + s.enemyHp); p.setProperty("kills", "" + s.kills);
        p.setProperty("ticks", "" + s.ticks); p.setProperty("rest", "" + s.rest);
        p.setProperty("savedAt", "" + System.currentTimeMillis());
        for (Hero h : s.heroes) {
            String key = h.role.name() + ".";
            p.setProperty(key + "level", "" + h.level); p.setProperty(key + "active", "" + h.active); p.setProperty(key + "hp", "" + h.hp);
            h.gear.forEach((slot, item) -> p.setProperty(key + slot.name(), item.id));
        }
        for (Item item : Catalog.ITEMS) p.setProperty("bag." + item.id, "" + s.inventory.getOrDefault(item.id, 0));
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "save-", ".tmp");
        try {
            try (Writer out = Files.newBufferedWriter(temp)) { p.store(out, "Ashen Defenders / save version 1"); }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    @Override public void close() throws IOException {
        if (lock != null) lock.release(); if (channel != null) channel.close();
    }
}
