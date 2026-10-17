package idle;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import idle.Catalog.*;
import idle.GameState.Hero;

public final class GameStateTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static Hero hero(GameState s, Role role) { return s.heroes.get(role.ordinal()); }
    public static void main(String[] args) throws Exception {
        GameState s = new GameState();
        check(s.heroes.size() == 9 && s.activeCount() == 4, "Playable roster and party cap");
        check(Catalog.ENEMIES.length == 33, "Complete enemy roster");
        s.gold = 10000;
        Hero knight = hero(s, Role.KNIGHT), wizard = hero(s, Role.WIZARD), lancer = hero(s, Role.LANCER);
        check(s.train(lancer) && !s.toggle(lancer), "Cannot deploy fifth member");
        check(s.toggle(wizard) && s.toggle(lancer), "Party swaps");
        Item sword = Catalog.item("sword_2"), staff = Catalog.item("staff_2"), hat = Catalog.item("hat_1");
        check(s.buy(sword), "Purchase weapon");
        check(!s.equip(wizard, sword) && !s.equip(lancer, sword), "Sword class restriction");
        double power = knight.power();
        check(s.equip(knight, sword) && knight.power() > power, "Sword raises power");
        check(s.inventory.get("sword_1") == 1 && s.inventory.get("sword_2") == 0, "Swap returns old item to stash");
        check(!s.equip(knight, sword), "Cannot duplicate equipped weapon");
        check(s.buy(staff) && !s.equip(knight, staff) && s.equip(wizard, staff), "Staff restrictions");
        double hp = knight.maxHp();
        check(s.buy(hat) && s.equip(knight, hat) && knight.maxHp() > hp, "Hat stats");
        check(s.unequip(knight, Slot.HAT) && s.inventory.get(hat.id) == 1, "Unequip returns item");
        for (Role role : Role.values()) for (Item item : Catalog.ITEMS) {
            if (item.slot != Slot.WEAPON) check(role.accepts(item), "Universal gear");
            if (item.weapon == Weapon.SWORD) check(role.accepts(item) == (role == Role.KNIGHT || role == Role.KNIGHT_TEMPLAR || role == Role.SWORDSMAN), "Sword permissions");
            if (item.weapon == Weapon.STAFF || item.weapon == Weapon.WAND) check(role.accepts(item) == (role == Role.WIZARD || role == Role.PRIEST), "Magic permissions");
        }
        s.gold = 0; check(!s.train(knight) && !s.buy(hat), "Cannot spend missing gold");
        GameState battle = new GameState();
        hero(battle, Role.KNIGHT).hp = 25; battle.tick();
        check(battle.lastHealing > 0 && hero(battle, Role.KNIGHT).hp > 25, "Priest heals wounded tank");
        battle.stage = 9; battle.enemyHp = 1; battle.tick(); check(battle.stage == 10 && battle.boss(), "Elite transition");
        battle.enemyHp = 1; double gold = battle.gold; battle.tick(); check(battle.gold - gold == 132, "Elite reward");
        battle.enemyHp = 1; battle.tick(); check(!battle.inventory.isEmpty(), "Third victory drops loot");
        battle.heroes.forEach(h -> h.hp = 0); battle.tick(); check(battle.rest == 5, "Defeat enters rest");
        for (int i = 0; i < 5; i++) battle.tick();
        check(battle.rest == 0 && battle.heroes.stream().allMatch(h -> h.hp == h.maxHp()), "Rest revives party");
        GameState a = new GameState(), b = new GameState(); long now = 100000000;
        a.savedAt = now - 8 * 3600000L; b.savedAt = now - 24 * 3600000L;
        check(a.offlineReward(now) == b.offlineReward(now), "Offline cap");
        check(a.offlineReward(now) == 0, "No repeat claim");
        a.savedAt = now + 1000; check(a.offlineReward(now) == 0, "Future timestamp");
        Path dir = Files.createTempDirectory("ashen-test-"); Path path = dir.resolve("save.properties");
        try (SaveStore store = new SaveStore(path)) {
            store.lock();
            try (SaveStore second = new SaveStore(path)) {
                boolean rejected = false;
                try { second.lock(); } catch (IOException expected) { rejected = true; }
                check(rejected, "Concurrent instance is blocked");
            }
            store.save(s); GameState loaded = store.load();
            check(loaded.gold == s.gold && loaded.activeCount() == 4 && hero(loaded, Role.KNIGHT).gear.get(Slot.WEAPON) == sword, "Save round trip");
            for (Item item : Catalog.ITEMS) check(loaded.inventory.getOrDefault(item.id, 0).equals(s.inventory.getOrDefault(item.id, 0)), "Saved inventory counts");
            Properties invalid = new Properties();
            try (Reader reader = Files.newBufferedReader(path)) { invalid.load(reader); }
            invalid.setProperty("KNIGHT.WEAPON", "staff_1");
            try (Writer writer = Files.newBufferedWriter(path)) { invalid.store(writer, "invalid equipment"); }
            boolean rejected = false; try { store.load(); } catch (IOException expected) { rejected = true; }
            check(rejected, "Reject incompatible saved weapon");
            Files.writeString(path, "version=99\n");
            rejected = false; try { store.load(); } catch (IOException expected) { rejected = true; }
            check(rejected, "Reject unsupported save version");
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(dir.resolve("save.properties.lock")); Files.deleteIfExists(dir); }
        // Render the actual Swing layout without starting combat or touching player saves.
        SwingUtilities.invokeAndWait(() -> {
            IdleGame.theme();
            JPanel view = new IdleGame(new GameState(), new SaveStore(Paths.get("out", "unused-preview.properties"))).view();
            view.setSize(1280, 860); layout(view);
            BufferedImage preview = new BufferedImage(1280, 860, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = preview.createGraphics(); view.printAll(graphics); graphics.dispose();
            try { ImageIO.write(preview, "png", Paths.get("out", "preview.png").toFile()); }
            catch (IOException e) { throw new RuntimeException(e); }
        });
        System.out.println("All Ashen Defenders checks passed. UI preview: out/preview.png");
    }
    private static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container) layout((Container) child); }
}
