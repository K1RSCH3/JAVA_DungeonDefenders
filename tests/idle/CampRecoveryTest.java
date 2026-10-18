package idle;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.swing.*;
import javax.imageio.ImageIO;

// verify recovery and dismissible guidance without changing the player's save
public final class CampRecoveryTest {
    // stop with a clear description of a broken recovery rule
    private static void check(boolean value, String text) { if (!value) throw new AssertionError(text); }

    // find controls by their visible label for player-flow checks
    private static JButton button(Container parent, String text) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JButton && ((JButton) c).getText().contains(text)) return (JButton) c;
            if (c instanceof Container) { JButton found = button((Container) c, text); if (found != null) return found; }
        }
        return null;
    }

    // prepare a screenshot after changing tabs or opening a guide
    private static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container) layout((Container) child); }

    // cover partial recovery, manual replacements, empty parties, and persisted countdowns
    static void run() throws Exception {
        GameState state = new GameState(); state.gold = 10000;
        GameState.Hero knight = state.heroes.get(Catalog.Role.KNIGHT.ordinal());
        knight.hp = 0; state.rescueDowned();
        check(!knight.active && state.activeCount() == 3 && knight.recoverySeconds == 30, "Rescue frees a party slot");
        state.advanceCamp(15);
        check(knight.hp == knight.maxHp() / 2 && !state.toggle(knight), "Partial camp healing cannot redeploy");
        GameState.Hero lancer = state.heroes.get(Catalog.Role.LANCER.ordinal());
        check(state.train(lancer) && !lancer.active && state.toggle(lancer), "Player can recruit and add a replacement");
        state.advanceCamp(15);
        check(knight.hp == knight.maxHp() && knight.recoverySeconds == 0 && !knight.active && !state.toggle(knight), "Ready hero waits when party is full");
        check(state.toggle(lancer) && state.toggle(knight), "Player chooses who returns");
        knight.hp = 0; state.rescueDowned(); state.advanceCamp(7);
        Path folder = Files.createTempDirectory("camp-test-"); Path file = folder.resolve("campaign.properties");
        try (SaveStore saves = new SaveStore(file)) {
            saves.save(state); GameState loaded = saves.load();
            GameState.Hero recovered = loaded.heroes.get(Catalog.Role.KNIGHT.ordinal());
            check(recovered.recoverySeconds == 23 && recovered.hp == knight.hp && !recovered.active, "Save keeps partial recovery");
            long now = System.currentTimeMillis(); loaded.savedAt = now - 30000; loaded.offlineReward(now);
            check(recovered.recoverySeconds == 0 && recovered.hp == recovered.maxHp() && !recovered.active, "Offline recovery stays at camp");
            loaded.heroes.stream().filter(h -> h.active).forEach(h -> h.hp = 0); loaded.rescueDowned();
            saves.save(loaded); GameState empty = saves.load();
            check(empty.activeCount() == 0, "Empty party save remains valid");
            int stage = empty.stage; double gold = empty.gold;
            for (int i = 0; i < 20; i++) empty.tick();
            check(empty.stage == stage && empty.gold == gold, "Empty party cannot progress or farm rewards");
            java.util.Properties legacy = new java.util.Properties();
            try (java.io.Reader reader = Files.newBufferedReader(file)) { legacy.load(reader); }
            legacy.setProperty("version", "3");
            legacy.setProperty("KNIGHT.hp", "0"); legacy.setProperty("KNIGHT.active", "false");
            try (java.io.Writer writer = Files.newBufferedWriter(file)) { legacy.store(writer, "legacy camp fixture"); }
            check(saves.load().heroes.get(Catalog.Role.KNIGHT.ordinal()).recoverySeconds == 30, "Legacy unconscious camper can recover");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(folder); }
        SwingUtilities.invokeAndWait(() -> {
            GameState campaign = new GameState(); campaign.gold = 10000;
            IdleGame ui = new IdleGame(campaign, new SaveStore(Paths.get("out", "camp-guide-test.properties")));
            JPanel view = ui.view(); view.setSize(1280, 900); layout(view);
            button(view, "<html>Lancer").doClick(); button(view, "Recruit ").doClick();
            check(button(view, "Close guide").getParent().isVisible(), "Recruit guide appears");
            check(Boolean.TRUE.equals(button(view, "<html>Wizard").getClientProperty("guideGlow")), "Full party guides selecting an active hero first");
            button(view, "<html>Wizard").doClick();
            check(Boolean.TRUE.equals(button(view, "Send to camp").getClientProperty("guideGlow")), "Selected active hero advances to freeing a slot");
            button(view, "Send to camp").doClick();
            check(Boolean.TRUE.equals(button(view, "<html>Lancer").getClientProperty("guideGlow")), "Free slot advances back to recruit selection");
            button(view, "<html>Lancer").doClick();
            check(Boolean.TRUE.equals(button(view, "Add to party").getClientProperty("guideGlow")), "Recruit selection advances to deployment");
            button(view, "Add to party").doClick();
            check(!button(view, "Close guide").getParent().isVisible(), "Deployment completes the sequence");
            button(view, "<html>Soldier").doClick();
            button(view, "Close guide").doClick(); button(view, "<html>Lancer").doClick();
            check(!button(view, "Close guide").getParent().isVisible(), "Dismissed guide stays closed after refresh");
            button(view, "<html>Knight").doClick(); button(view, "Shop").doClick(); button(view, "Buy for").doClick();
            check(Boolean.TRUE.equals(button(view, "Gear").getClientProperty("guideGlow")), "Purchase highlights Gear tab");
            button(view, "Gear").doClick(); layout(view);
            try {
                BufferedImage image = new BufferedImage(1280, 900, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics(); view.printAll(g); g.dispose(); ImageIO.write(image, "png", Paths.get("out", "equipment-guide.png").toFile());
            } catch (Exception e) { throw new RuntimeException(e); }
            button(view, "Equip").doClick();
            check(!button(view, "Close guide").getParent().isVisible() && campaign.heroes.get(Catalog.Role.KNIGHT.ordinal()).gear.containsKey(Catalog.Slot.WEAPON), "Equip completes guide");
            campaign.heroes.stream().filter(h -> h.active).forEach(h -> h.hp = 0);
            campaign.rescueDowned();
            IdleGame regroup = new IdleGame(campaign, new SaveStore(Paths.get("out", "camp-guide-test.properties")));
            JPanel recovery = regroup.view();
            check(button(recovery, "Close guide").getParent().isVisible(), "Empty party opens regrouping guide");
            button(recovery, "Upgrades").doClick();
            check(Boolean.TRUE.equals(button(recovery, "Party").getClientProperty("guideGlow")), "Regrouping guides navigation back to Party");
            button(recovery, "Party").doClick(); button(recovery, "<html>Knight").doClick();
            check(!button(recovery, "Recovering /").isEnabled(), "Recovering hero cannot be deployed early");
            button(recovery, "Close guide").doClick();
            button(recovery, "Upgrades").doClick();
            check(!button(recovery, "Close guide").getParent().isVisible(), "Dismissed recovery guide stays closed on navigation");
            button(recovery, "Party").doClick(); button(recovery, "<html>Wizard").doClick();
            check(Boolean.TRUE.equals(button(recovery, "Add to party").getClientProperty("guideGlow")), "Ready replacement advances to Add to party");
            button(recovery, "Add to party").doClick();
            check(campaign.activeCount() == 1 && !button(recovery, "Close guide").getParent().isVisible(), "Replacement deployment finishes regrouping guidance");
        });
        Files.deleteIfExists(Paths.get("out", "camp-guide-test.properties"));
    }
}
