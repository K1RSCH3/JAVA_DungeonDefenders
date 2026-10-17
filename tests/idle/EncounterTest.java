package idle;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class EncounterTest {
    // fail immediately with a useful label when a required behavior is wrong
    // this keeps the tests independent of an external test framework
    private static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); }

    // exercise encounter rules, upgrades, timing, portraits, death continuity, and save compatibility
    // render representative ui and battle states after the behavioral checks pass
    public static void run() throws Exception {
        BufferedImage portraits = new BufferedImage(900, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D portraitGallery = portraits.createGraphics();
        portraitGallery.setColor(new Color(0x171624)); portraitGallery.fillRect(0, 0, 900, 120);
        for (Catalog.Role role : Catalog.Role.values()) {
            BufferedImage portrait = new BufferedImage(100, 90, BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = portrait.createGraphics(); PixelArt.drawPortrait(pg, role, 12, 12, 76, 66); pg.dispose();
            int pixels = 0;
            for (int y = 0; y < 90; y++) for (int x = 0; x < 100; x++) if ((portrait.getRGB(x, y) >>> 24) > 0) {
                check(x >= 12 && x < 88 && y >= 12 && y < 78, "Portrait stays inside frame: " + role);
                pixels++;
            }
            check(pixels > 100, "Portrait remains visible: " + role);
            int x = role.ordinal() * 100;
            UiSkin.draw(portraitGallery, "panel", x, 0, 100, 90, true);
            portraitGallery.drawImage(portrait, x, 0, null);
            portraitGallery.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10)); portraitGallery.setColor(Color.WHITE);
            portraitGallery.drawString(role.label, x + 3, 107);
        }
        portraitGallery.dispose(); ImageIO.write(portraits, "png", Paths.get("out", "hero-portraits.png").toFile());
        GameState paced = new GameState(); paced.stage = 20; paced.enemyHp = paced.enemyMaxHp();
        BattlePanel pacing = new BattlePanel(paced);
        for (int i = 0; i < 90; i++) pacing.animate();
        check(pacing.readyForCombat(), "Arrival permits combat only once formation is reached");
        paced.tick();
        check(!pacing.readyForCombat(), "New combat events must be observed before another round");
        pacing.animate();
        for (int i = 0; i < 41; i++) pacing.animate();
        check(!pacing.readyForCombat(), "Attack and return motions cannot be overwritten early");
        pacing.animate(); check(pacing.readyForCombat(), "Next round starts after the complete slower timeline");
        for (int duration : new int[]{16, 18}) {
            check(BattlePanel.lungeProgress(0, duration) == 0 && BattlePanel.lungeProgress(duration - 1, duration) == 0, "Lunges start and finish at formation");
            check(BattlePanel.lungeProgress(duration - 2, duration) < .08, "Final return step is small enough to avoid snapping");
        }
        check(BattlePanel.steppedHealth(1, .5, 5) == 1, "Heart damage holds briefly");
        check(BattlePanel.steppedHealth(1, .5, 6) == BattlePanel.steppedHealth(1, .5, 8), "Heart changes hold discrete frames");
        check(BattlePanel.steppedHealth(1, .5, 18) == .5 && BattlePanel.steppedHealth(.5, .9, 18) == .9, "Hearts settle to exact HP after damage and healing");
        check(BattlePanel.steppedHealth(.8, .79, 6) <= .8 && BattlePanel.steppedHealth(.8, .79, 6) >= .79, "Small hits never overshoot");
        int previousDistance = 0;
        for (int i = 0; i <= 45; i++) {
            int distance = BattlePanel.travelDistance(i);
            check(distance >= previousDistance && distance - previousDistance <= 3, "Travel is smooth and monotonic");
            previousDistance = distance;
        }
        check(previousDistance == 90, "Travel ends at the enemy formation");
        check(BattlePanel.entranceOffset(0) == 280 && 228 + BattlePanel.entranceOffset(0) > 484, "Incoming enemies begin beyond the right edge with sprite padding");
        check(BattlePanel.entranceOffset(45) == 0 && BattlePanel.entranceOffset(46) == 0, "Arrival settles exactly at battle position");
        int previousOffset = 280;
        for (int i = 1; i <= 45; i++) {
            int offset = BattlePanel.entranceOffset(i);
            check(offset <= previousOffset && previousOffset - offset <= 10, "Continuous eased enemy entry");
            previousOffset = offset;
        }
        GameState upgrades = new GameState(); upgrades.gold = 10000;
        GameState.Hero priest = upgrades.heroes.get(Catalog.Role.PRIEST.ordinal());
        double healBefore = priest.healingPower(), powerBefore = priest.power(), hpBefore = priest.maxHp();
        check(upgrades.upgrade(priest, GameState.Upgrade.ATTACK) && priest.power() > powerBefore && priest.healingPower() == healBefore && priest.maxHp() == hpBefore, "Attack upgrades affect only damage");
        powerBefore = priest.power();
        check(upgrades.upgrade(priest, GameState.Upgrade.HEALING) && priest.healingPower() == healBefore + 4 && priest.power() == powerBefore, "Healing upgrades independent of damage");
        priest.hp = 20;
        check(upgrades.upgrade(priest, GameState.Upgrade.HEALTH) && priest.maxHp() == hpBefore + 18 && priest.hp == 38, "Vitality adds max and current HP without full healing");
        check(!upgrades.upgrade(upgrades.heroes.get(Catalog.Role.KNIGHT.ordinal()), GameState.Upgrade.HEALING), "Healing limited to priest");
        upgrades.gold = 0; int priorRank = priest.rank(GameState.Upgrade.ATTACK);
        check(!upgrades.upgrade(priest, GameState.Upgrade.ATTACK) && priest.rank(GameState.Upgrade.ATTACK) == priorRank, "Unaffordable upgrades do not mutate ranks");
        GameState deaths = new GameState(); deaths.stage = 10; deaths.enemyHp = deaths.memberMaxHp() * 3;
        BattlePanel deathPanel = new BattlePanel(deaths);
        for (int i = 0; i < 60; i++) {deathPanel.animate(); deathPanel.animate();}
        int completedFrame = deathPanel.enemyDeathFrame(0, false);
        check(completedFrame > 10, "Individual enemy death completes");
        deaths.enemyHp = 1; deaths.tick(); {deathPanel.animate(); deathPanel.animate();}
        check(deathPanel.enemyDeathFrame(0, true) >= completedFrame, "Stage clear preserves an earlier corpse's death frame");
        check(deathPanel.enemyDeathFrame(3, true) == -1, "Final victim waits for impact before dying");
        for (int i = 0; i < 30; i++) {deathPanel.animate(); deathPanel.animate();}
        check(deathPanel.enemyDeathFrame(0, true) >= completedFrame && deathPanel.enemyDeathFrame(3, true) >= 0, "Earlier corpse stays down while final victim dies");
        for (int i = 0; i < 60; i++) {deathPanel.animate(); deathPanel.animate();}
        check(deathPanel.enemyDeathFrame(0, false) == -1, "New encounter starts with living enemies");
        check(BattlePanel.heartFill(1, 5) == 1 && BattlePanel.heartFill(0, 0) == 0, "Full and depleted hearts");
        check(BattlePanel.heartFill(.25, 0) == 1 && BattlePanel.heartFill(.25, 1) == .5 && BattlePanel.heartFill(.25, 2) == 0, "Partial health uses partial hearts");
        check(BattlePanel.enemyStyle("Skeleton Archer").equals("ranged") && BattlePanel.enemyStyle("Warlock").equals("magic") && BattlePanel.enemyStyle("Orc").equals("melee"), "Enemy role animation profiles");
        for (String enemy : Catalog.ENEMIES) for (int i = 0; i < 4; i++) check(BattlePanel.enemyDelay(enemy, i) + 16 <= 30, "Enemy attack finishes within tick");
        check(BattlePanel.attackDelay(Catalog.Role.ARCHER) < BattlePanel.attackDelay(Catalog.Role.KNIGHT)
            && BattlePanel.attackDelay(Catalog.Role.KNIGHT) < BattlePanel.attackDelay(Catalog.Role.WIZARD), "Role attack animations are staggered");
        for (Catalog.Role role : Catalog.Role.values()) check(BattlePanel.attackDelay(role) + 18 <= 30, "Role animation completes within combat tick");
        for (int stage = 1; stage <= 150; stage++) {
            String[] members = Encounters.members(stage);
            check(members.length == (stage % 25 == 0 ? 1 : 4), "Encounter size at " + stage);
            for (String member : members) check(Encounters.species(member).equals(Encounters.species(members[0])), "Same-species patrol at " + stage);
            if (stage % 25 != 0) for (String member : members) check(!Arrays.asList(Encounters.BOSSES).contains(member), "Boss-only enemy");
        }
        check(Encounters.members(25)[0].equals("Elite Orc") && Encounters.members(150)[0].equals("Elite Orc"), "Boss rotation");
        check(Encounters.height("Bat", false) < Encounters.height("Skeleton", false) && Encounters.height("Minotaur", true) > Encounters.height("Werebear", false), "Creature size hierarchy");
        AssetPack.Actor wizard = AssetPack.actor("characters", "wizard");
        check(wizard != null && wizard.has("impact"), "Wizard standalone spell sheet loaded");
        BufferedImage spell = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D spellGraphics = spell.createGraphics();
        wizard.drawImpact(spellGraphics, 2, 10, 100, 100); spellGraphics.dispose();
        int visible = 0;
        for (int y = 70; y < 130; y++) for (int x = 70; x < 130; x++) if ((spell.getRGB(x, y) >>> 24) > 0) visible++;
        check(visible > 100 && spell.getRGB(10, 10) == 0, "Wizard impact visibly centered on its recipient");
        GameState full = new GameState(), depleted = new GameState();
        for (GameState s : new GameState[]{full, depleted}) {
            s.stage = 30; s.enemyHp = s.enemyMaxHp();
            s.heroes.forEach(h -> h.active = h.role == Catalog.Role.KNIGHT);
        }
        depleted.enemyHp = depleted.memberMaxHp();
        check(depleted.livingEnemies() == 1 && depleted.memberHp(0) == 0 && depleted.memberHp(3) > 0, "Individual deaths");
        full.tick(); depleted.tick();
        check(depleted.heroes.get(Catalog.Role.KNIGHT.ordinal()).hp > full.heroes.get(Catalog.Role.KNIGHT.ordinal()).hp, "Dead enemies stop attacking");
        check(depleted.stage == 30, "Partial clear cannot advance stage");
        GameState effects = new GameState(); effects.stage = 10; effects.enemyHp = effects.enemyMaxHp();
        GameState.Hero injured = effects.heroes.get(Catalog.Role.ARCHER.ordinal()); injured.hp = 20;
        effects.tick();
        check(effects.combatEvents.stream().anyMatch(e -> e.healing && e.ally == injured && e.hero.role == Catalog.Role.PRIEST && e.amount > 0), "Heal event targets wounded ally");
        check(effects.combatEvents.stream().anyMatch(e -> !e.healing && !e.incoming && e.hero.role == Catalog.Role.WIZARD && e.enemy == 0), "Wizard targets current enemy");
        check(effects.combatEvents.stream().filter(e -> e.incoming).allMatch(e -> e.ally.role == Catalog.Role.KNIGHT), "Enemy effects target tank");
        effects.enemyHp = effects.memberMaxHp() * 3 + 1; effects.tick();
        check(effects.combatEvents.stream().anyMatch(e -> !e.healing && !e.incoming && e.enemy == 0 && e.amount == 1), "Overkill effect records actual damage");
        check(effects.combatEvents.stream().anyMatch(e -> !e.healing && !e.incoming && e.enemy == 1), "Spillover effect follows next target");
        effects.rest = 1; effects.tick(); check(effects.combatEvents.isEmpty(), "Rest clears stale effects");
        GameState boss = new GameState(); boss.stage = 25; boss.enemyHp = 1; boss.tick();
        check(boss.stage == 26 && boss.gold == 565 && !boss.inventory.isEmpty(), "Boss rewards and guaranteed loot");
        Path dir = Files.createTempDirectory("dungeon-encounters-"); Path file = dir.resolve("save.properties");
        try (SaveStore saves = new SaveStore(file)) {
            saves.save(upgrades);
            GameState upgradedSave = saves.load();
            GameState.Hero restoredPriest = upgradedSave.heroes.get(Catalog.Role.PRIEST.ordinal());
            check(restoredPriest.hp == 38 && restoredPriest.maxHp() == priest.maxHp() && restoredPriest.healingPower() == priest.healingPower(), "Vitality and healing reload before HP validation");
            depleted.heroes.get(Catalog.Role.KNIGHT.ordinal()).upgrades.put(GameState.Upgrade.ATTACK, 3);
            saves.save(depleted); GameState restored = saves.load();
            check(restored.heroes.get(Catalog.Role.KNIGHT.ordinal()).rank(GameState.Upgrade.ATTACK) == 3, "Upgrade ranks survive saving");
            check(restored.livingEnemies() == 1 && restored.enemyHp == depleted.enemyHp, "Partial patrol persistence");
            Properties legacy = new Properties(); try (Reader r = Files.newBufferedReader(file)) { legacy.load(r); }
            legacy.setProperty("version", "2");
            try (Writer w = Files.newBufferedWriter(file)) { legacy.store(w, "Previous save"); }
            GameState previous = saves.load();
            check(previous.heroes.get(Catalog.Role.KNIGHT.ordinal()).rank(GameState.Upgrade.ATTACK) == 0 && previous.stage == depleted.stage, "Version 2 preserves progress with zero new ranks");
            legacy.setProperty("version", "1"); legacy.setProperty("stage", "10"); legacy.setProperty("enemyHp", "306.25");
            try (Writer w = Files.newBufferedWriter(file)) { legacy.store(w, "Legacy elite save"); }
            GameState migrated = saves.load(); check(!migrated.boss() && migrated.enemyHp == migrated.enemyMaxHp() / 2 && migrated.livingEnemies() == 2, "Legacy health percentage migration");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(dir); }
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage states = new BufferedImage(900, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = states.createGraphics(); g.setColor(new Color(0x14141e)); g.fillRect(0, 0, 900, 100);
            for (int i = 0; i < 4; i++) {
                JButton b = new JButton(new String[]{"Normal", "Hover", "Pressed", "Disabled"}[i]); b.setSize(200, 60);
                if (i == 1) b.getModel().setRollover(true);
                if (i == 2) { b.getModel().setArmed(true); b.getModel().setPressed(true); }
                if (i == 3) b.setEnabled(false);
                Graphics2D cell = (Graphics2D) g.create(10 + i * 220, 20, 200, 60); b.paint(cell); cell.dispose();
            }
            g.dispose();
            try {
                ImageIO.write(states, "png", Paths.get("out", "button-states.png").toFile());
                GameState s = new GameState(); s.stage = 25; s.enemyHp = s.enemyMaxHp();
                BattlePanel battle = new BattlePanel(s); battle.setSize(1152, 648); for (int i = 0; i < 60; i++) {battle.animate(); battle.animate();}
                BufferedImage art = new BufferedImage(1152, 648, BufferedImage.TYPE_INT_RGB); Graphics2D bg = art.createGraphics(); battle.paint(bg); bg.dispose();
                ImageIO.write(art, "png", Paths.get("out", "boss-preview.png").toFile());
                BattlePanel entry = new BattlePanel(new GameState()); entry.setSize(768, 432);
                BufferedImage arrival = new BufferedImage(1536, 864, BufferedImage.TYPE_INT_RGB);
                Graphics2D arrivalGraphics = arrival.createGraphics();
                for (int step = 0; step < 4; step++) {
                    if (step > 0) for (int j = 0; j < 15; j++) {entry.animate(); entry.animate();}
                    Graphics2D cell = (Graphics2D) arrivalGraphics.create(step % 2 * 768, step / 2 * 432, 768, 432);
                    entry.paint(cell); cell.dispose();
                }
                arrivalGraphics.dispose(); ImageIO.write(arrival, "png", Paths.get("out", "enemy-entry.png").toFile());
                GameState combat = new GameState(); combat.stage = 10; combat.enemyHp = combat.enemyMaxHp();
                combat.heroes.get(Catalog.Role.ARCHER.ordinal()).hp = 20;
                BattlePanel effectsPanel = new BattlePanel(combat); effectsPanel.setSize(1152, 648);
                for (int i = 0; i < 60; i++) {effectsPanel.animate(); effectsPanel.animate();}
                combat.tick(); for (int i = 0; i < 11; i++) {effectsPanel.animate(); effectsPanel.animate();}
                BufferedImage effectsArt = new BufferedImage(1152, 648, BufferedImage.TYPE_INT_RGB);
                Graphics2D fx = effectsArt.createGraphics(); effectsPanel.paint(fx); fx.dispose();
                ImageIO.write(effectsArt, "png", Paths.get("out", "combat-effects.png").toFile());
                for (int i = 0; i < 14; i++) {effectsPanel.animate(); effectsPanel.animate();}
                fx = effectsArt.createGraphics(); effectsPanel.paint(fx); fx.dispose();
                ImageIO.write(effectsArt, "png", Paths.get("out", "enemy-attacks.png").toFile());
                combat.rest = 3;
                fx = effectsArt.createGraphics(); effectsPanel.paint(fx); fx.dispose();
                ImageIO.write(effectsArt, "png", Paths.get("out", "regrouping-preview.png").toFile());
                JPanel game = new IdleGame(new GameState(), new SaveStore(Paths.get("out", "unused-expanded.properties"))).view();
                game.setSize(1600, 900); layout(game);
                BattlePanel field = findBattle(game); int before = field.getWidth();
                findButton(game, "Expand battlefield").doClick(); layout(game);
                check(field.getWidth() > before + 300, "Expand battlefield releases sidebar space");
                BufferedImage expanded = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);
                Graphics2D eg = expanded.createGraphics(); game.paint(eg); eg.dispose();
                ImageIO.write(expanded, "png", Paths.get("out", "expanded-preview.png").toFile());
                findButton(game, "Show party & gear").doClick(); layout(game);
                check(field.getWidth() == before, "Management panel can be restored");
            } catch (IOException e) { throw new RuntimeException(e); }
        });
    }

    // lay out nested swing containers and settle battlefield entry animations
    // this makes offscreen screenshots comparable without opening a game window
    private static void layout(Container c) { c.doLayout(); if (c instanceof BattlePanel) for (int i = 0; i < 60; i++) ((BattlePanel) c).animate(); for (Component child : c.getComponents()) if (child instanceof Container) layout((Container) child); }

    // find the battlefield inside the game screen
    // layout tests use it to check that expanding the scene releases sidebar space
    private static BattlePanel findBattle(Container c) {
        if (c instanceof BattlePanel) return (BattlePanel) c;
        for (Component child : c.getComponents()) if (child instanceof Container) { BattlePanel found = findBattle((Container) child); if (found != null) return found; }
        return null;
    }

    // find a button by its visible label
    // ui tests exercise the same action that a player would click
    private static JButton findButton(Container c, String name) {
        for (Component child : c.getComponents()) {
            if (child instanceof JButton && ((JButton) child).getText().equals(name)) return (JButton) child;
            if (child instanceof Container) { JButton found = findButton((Container) child, name); if (found != null) return found; }
        }
        return null;
    }
}
