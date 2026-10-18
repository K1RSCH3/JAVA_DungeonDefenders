package idle;

import java.io.*;
import java.nio.file.*;
import java.util.*;

// manage named campaigns without deriving file paths from player-entered names
final class CampaignLibrary {
    final Path directory, legacy;

    // keep the original campaign in place and store new campaigns beside it in a private folder
    CampaignLibrary(Path legacy) {
        this.legacy = legacy.toAbsolutePath().normalize();
        directory = this.legacy.getParent().resolve("saves");
    }

    // describe a save without claiming offline rewards or rewriting its timestamp
    static final class Entry {
        final Path path;
        final String name;
        final GameState state;
        Entry(Path path) throws IOException {
            this.path = path;
            GameState loaded;
            try (SaveStore store = new SaveStore(path)) { loaded = store.load(); }
            state = loaded;
            name = state.campaignName;
        }
    }

    // list only campaign files, leaving backups and lock files outside the selector
    List<Path> paths() throws IOException {
        List<Path> paths = new ArrayList<>();
        if (Files.exists(legacy)) paths.add(legacy);
        if (Files.isDirectory(directory)) try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.properties")) {
            for (Path path : stream) if (Files.isRegularFile(path)) paths.add(path);
        }
        paths.sort(Comparator.comparing(Path::toString));
        return paths;
    }

    // validate readable names and prevent ambiguous duplicate entries before writing anything
    String validateName(String value, Path except) throws IOException {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 32 || name.chars().anyMatch(Character::isISOControl))
            throw new IOException("Use a name with 1 to 32 characters and no control characters.");
        for (Path path : paths()) {
            if (path.equals(except)) continue;
            Properties p = read(path);
            if (name.equalsIgnoreCase(p.getProperty("campaignName", "Existing Expedition")))
                throw new IOException("Another expedition already uses that name.");
        }
        return name;
    }

    // create a separate campaign instead of replacing any existing progress
    Path create(String value) throws IOException {
        String name = validateName(value, null);
        Files.createDirectories(directory);
        Path path = directory.resolve(UUID.randomUUID() + ".properties");
        GameState state = new GameState(); state.campaignName = name;
        try (SaveStore store = new SaveStore(path)) { store.lock(); store.save(state); }
        return path;
    }

    // change only the display name so renaming never grants or consumes offline time
    void rename(Path path, String value) throws IOException {
        requireMember(path);
        String name = validateName(value, path);
        Properties p = read(path); p.setProperty("campaignName", name);
        Path temp = Files.createTempFile(path.getParent(), "save-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp)) { p.store(writer, "Dungeon Defenders campaign"); }
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    // delete only the selected campaign after the interface has asked for confirmation
    void delete(Path path) throws IOException { requireMember(path); Files.delete(path); }

    // reject paths outside the library even when called without the menu
    private void requireMember(Path path) throws IOException {
        if (!paths().contains(path)) throw new IOException("This expedition is no longer available.");
    }

    // read metadata directly so damaged gameplay data can still be renamed or removed
    private static Properties read(Path path) throws IOException {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { p.load(reader); }
        catch (IllegalArgumentException e) { throw new IOException("Unable to read expedition metadata.", e); }
        return p;
    }
}
