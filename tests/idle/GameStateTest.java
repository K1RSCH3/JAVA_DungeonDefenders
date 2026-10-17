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
    // fail immediately with a useful label when a required behavior is wrong
    // this keeps the tests independent of an external test framework
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    // find a roster entry by its stable class order
    // tests use this to target a particular hero without repeating lookup code
    private static Hero hero(GameState s, Role role) { return s.heroes.get(role.ordinal()); }

    // run gameplay, save, asset, and ui checks using isolated data
    // also render previews so layout changes can be reviewed without altering the player's campaign
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
        battle.stage = 24; battle.enemyHp = 1; battle.tick(); check(battle.stage == 25 && battle.boss(), "Boss transition");
        battle.enemyHp = 1; double gold = battle.gold; battle.tick(); check(battle.gold - gold == 445, "Boss reward");
        battle.enemyHp = 1; battle.tick(); check(!battle.inventory.isEmpty(), "Third victory drops loot");
        int stalledStage = battle.stage; double savedGold = battle.gold;
        battle.heroes.forEach(h -> h.hp = 0); battle.tick(); check(battle.rest == 5, "Defeat enters rest");
        check(battle.stage == stalledStage && battle.gold == savedGold && battle.enemyHp == battle.enemyMaxHp(), "Failed attempt preserves stage and gold, resets enemies");
        check(battle.heroes.stream().allMatch(h -> h.hp == h.maxHp()), "Party rescued immediately");
        for (int i = 0; i < 5; i++) battle.tick();
        check(battle.rest == 0 && battle.heroes.stream().allMatch(h -> h.hp == h.maxHp()), "Rest revives party");
        GameState wall = new GameState(); wall.stage = 1000; wall.enemyHp = wall.enemyMaxHp();
        for (int i = 0; i < 250; i++) wall.tick();
        check(wall.stage == 1000 && wall.kills == 0 && wall.gold == 120 && wall.inventory.isEmpty(), "Underpowered party cannot chip past a stage across retries or earn victory rewards");
        GameState scaling = new GameState(); scaling.stage = 10;
        double earlyHp = scaling.enemyMaxHp(), earlyDamage = scaling.enemyStrikeDamage(); scaling.stage = 20;
        check(scaling.enemyMaxHp() > earlyHp && scaling.enemyStrikeDamage() > earlyDamage, "Normal HP and damage grow with stage");
        scaling.stage = 25; earlyHp = scaling.enemyMaxHp(); earlyDamage = scaling.enemyStrikeDamage(); scaling.stage = 50;
        check(scaling.enemyMaxHp() > earlyHp && scaling.enemyStrikeDamage() > earlyDamage, "Boss HP and damage grow with stage");
        GameState upgraded = new GameState(); upgraded.stage = 30; upgraded.enemyHp = upgraded.enemyMaxHp();
        upgraded.heroes.stream().filter(h -> h.active).forEach(h -> { h.level = 100; h.hp = h.maxHp(); });
        upgraded.tick(); check(upgraded.stage == 31, "Upgraded party can break through the stage");
        GameState a = new GameState(), b = new GameState(); long now = 100000000;
        a.savedAt = now - 8 * 3600000L; b.savedAt = now - 24 * 3600000L;
        check(a.offlineReward(now) == b.offlineReward(now), "Offline cap");
        check(a.offlineReward(now) == 0, "No repeat claim");
        a.savedAt = now + 1000; check(a.offlineReward(now) == 0, "Future timestamp");
        Path dir = Files.createTempDirectory("dungeon-test-"); Path path = dir.resolve("save.properties");
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
        BufferedImage completeGallery = new BufferedImage(896, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D allGraphics = completeGallery.createGraphics();
        allGraphics.setColor(new Color(0x171824)); allGraphics.fillRect(0, 0, 896, 720);
        allGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int actorIndex = 0;
        for (int i = 0; i < 42; i++) {
            boolean ally = i < 9;
            String id = ally ? Role.values()[i].sprite() : Catalog.enemySprite(Catalog.ENEMIES[i - 9]);
            AssetPack.Actor actor = AssetPack.actor(ally ? "characters" : "enemies", id);
            check(actor != null, "Full pack mapping: " + id);
            for (String action : new String[]{"idle", "walk", "attack", "hurt", "death"}) check(actor.has(action), "Real action sheet: " + id + "/" + action);
            int x = actorIndex % 7 * 128, y = actorIndex / 7 * 120;
            actor.draw(allGraphics, "idle", 0, x + 36, y + 25, 50, 65, !ally);
            allGraphics.setColor(Color.WHITE); allGraphics.drawString(id, x + 3, y + 114); actorIndex++;
        }
        allGraphics.dispose(); ImageIO.write(completeGallery, "png", Paths.get("out", "complete-roster.png").toFile());
        GameState movement = new GameState();
        BattlePanel movingPanel = new BattlePanel(movement); movingPanel.setSize(384, 180);
        check(movingPanel.transitioning(), "Initial walking phase");
        BufferedImage movementPreview = new BufferedImage(768, 540, BufferedImage.TYPE_INT_RGB);
        Graphics2D movementGraphics = movementPreview.createGraphics();
        for (int step = 0; step < 6; step++) {
            if (step == 1) for (int i = 0; i < 23; i++) {movingPanel.animate(); movingPanel.animate();}
            if (step == 2) { for (int i = 0; i < 30; i++) {movingPanel.animate(); movingPanel.animate();} check(!movingPanel.transitioning(), "Arrival ends walking"); }
            if (step == 3) { movement.enemyHp = 1; movement.tick(); check(movingPanel.transitioning(), "Victory immediately blocks next combat tick"); for (int i = 0; i < 10; i++) {movingPanel.animate(); movingPanel.animate();} }
            if (step == 4) for (int i = 0; i < 20; i++) {movingPanel.animate(); movingPanel.animate();}
            if (step == 5) { for (int i = 0; i < 60; i++) {movingPanel.animate(); movingPanel.animate();} check(!movingPanel.transitioning(), "Next encounter resumes"); }
            Graphics2D cell = (Graphics2D) movementGraphics.create(step % 2 * 384, step / 2 * 180, 384, 180);
            movingPanel.paint(cell); cell.dispose();
        }
        movementGraphics.dispose(); ImageIO.write(movementPreview, "png", Paths.get("out", "movement-preview.png").toFile());
        BufferedImage corridorA = new BufferedImage(384, 180, BufferedImage.TYPE_INT_RGB), corridorB = new BufferedImage(384, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D ca = corridorA.createGraphics(), cb = corridorB.createGraphics();
        check(AssetPack.drawCorridor(ca, 0, 0) && AssetPack.drawCorridor(cb, 0, 23), "Corridor layers load"); ca.dispose(); cb.dispose();
        int changed = 0;
        for (int y = 0; y < 180; y++) for (int x = 0; x < 384; x++) if (corridorA.getRGB(x, y) != corridorB.getRGB(x, y)) changed++;
        check(changed > 1000, "Corridor moves during travel");
        check(AssetPack.actor("characters", "soldier") != null, "Soldier pack loads");
        for (String id : new String[]{"orc", "demon_a", "blood_monster_a"}) {
            AssetPack.Actor actor = AssetPack.actor("enemies", id);
            check(actor != null && actor.frames("idle") == 6 && actor.frames("death") == 4, "Packed animation frames: " + id);
        }
        BufferedImage gallery = new BufferedImage(768, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D galleryGraphics = gallery.createGraphics();
        galleryGraphics.setColor(new Color(0x171824)); galleryGraphics.fillRect(0, 0, 768, 480);
        galleryGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        String[] ids = {"soldier", "orc", "demon_a", "blood_monster_a"};
        String[] actions = {"idle", "attack", "hurt", "death"};
        for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) {
            AssetPack.Actor actor = AssetPack.actor(row == 0 ? "characters" : "enemies", ids[row]);
            actor.draw(galleryGraphics, actions[col], actions[col].equals("death") ? 3 : 2, col * 192 + 60, row * 120 + 20, 60, 75, row != 0);
            galleryGraphics.setColor(Color.WHITE); galleryGraphics.drawString(ids[row] + " / " + actions[col], col * 192 + 5, row * 120 + 113);
        }
        galleryGraphics.dispose(); ImageIO.write(gallery, "png", Paths.get("out", "asset-gallery.png").toFile());

        // render the actual swing layout without starting combat or touching player saves
        SwingUtilities.invokeAndWait(() -> {
            IdleGame.theme();
            JPanel menu = new MainMenu(null, new SaveStore(Paths.get("out", "unused-menu.properties"))).view();
            menu.setSize(1280, 860); layout(menu);
            BufferedImage menuPreview = new BufferedImage(1280, 860, BufferedImage.TYPE_INT_RGB);
            Graphics2D menuGraphics = menuPreview.createGraphics(); menu.printAll(menuGraphics); menuGraphics.dispose();
            try { ImageIO.write(menuPreview, "png", Paths.get("out", "menu-preview.png").toFile()); }
            catch (IOException e) { throw new RuntimeException(e); }
            JPanel view = new IdleGame(new GameState(), new SaveStore(Paths.get("out", "unused-preview.properties"))).view();
            view.setSize(1280, 860); layout(view);
            BufferedImage preview = new BufferedImage(1280, 860, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = preview.createGraphics(); view.printAll(graphics); graphics.dispose();
            try { ImageIO.write(preview, "png", Paths.get("out", "preview.png").toFile()); }
            catch (IOException e) { throw new RuntimeException(e); }
            for (int tab = 1; tab <= 3; tab++) {
                findTabs(view).setSelectedIndex(tab); layout(view);
                Graphics2D tabGraphics = preview.createGraphics(); view.printAll(tabGraphics); tabGraphics.dispose();
                try { ImageIO.write(preview, "png", Paths.get("out", tab == 1 ? "gear-preview.png" : tab == 2 ? "forge-preview.png" : "training-preview.png").toFile()); }
                catch (IOException e) { throw new RuntimeException(e); }
            }
            GameState demo = new GameState();
            hero(demo, Role.KNIGHT).active = false; hero(demo, Role.SOLDIER).active = true; hero(demo, Role.SOLDIER).level = 1;
            demo.stage = 2; demo.enemyHp = demo.enemyMaxHp();
            JPanel assetView = new IdleGame(demo, new SaveStore(Paths.get("out", "unused-preview.properties"))).view();
            assetView.setSize(1280, 860); layout(assetView);
            Graphics2D assetGraphics = preview.createGraphics(); assetView.printAll(assetGraphics); assetGraphics.dispose();
            try { ImageIO.write(preview, "png", Paths.get("out", "asset-preview.png").toFile()); }
            catch (IOException e) { throw new RuntimeException(e); }
        });
        EncounterTest.run();
        MainMenuFlowTest.run();
        System.out.println("All Dungeon Defenders checks passed, including main menu lifecycle. UI preview: out/preview.png");
    }

    // lay out nested swing containers and settle battlefield entry animations
    // this makes offscreen screenshots comparable without opening a game window
    private static void layout(Container c) {
        c.doLayout();
        if (c instanceof BattlePanel) for (int i = 0; i < 60; i++) ((BattlePanel) c).animate();
        for (Component child : c.getComponents()) if (child instanceof Container) layout((Container) child);
    }

    // locate the page container inside the nested ui
    // tests select each management page to generate its preview
    private static JTabbedPane findTabs(Container c) {
        if (c instanceof JTabbedPane) return (JTabbedPane) c;
        for (Component child : c.getComponents()) if (child instanceof Container) { JTabbedPane found = findTabs((Container) child); if (found != null) return found; }
        return null;
    }
}
