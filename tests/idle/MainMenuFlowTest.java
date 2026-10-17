package idle;

import java.awt.*;
import java.nio.file.*;
import javax.swing.*;

/** exercises menu/game ownership with a hidden window and an isolated save */
public final class MainMenuFlowTest {
    // find a menu button in the component tree
    // lifecycle checks use this to verify available actions after navigation
    private static JButton find(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton && ((JButton) child).getText().equals(name)) return (JButton) child;
            if (child instanceof Container) { JButton result = find((Container) child, name); if (result != null) return result; }
        }
        return null;
    }

    // exercise new game, return to menu, continue, and quit using a hidden window and temporary save
    // verify that opening the menu alone does not create a campaign
    public static void run() throws Exception {
        Path dir = Files.createTempDirectory("dungeon-menu-test-");
        Path path = dir.resolve("campaign.properties");
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFrame host = new JFrame();
                try (SaveStore saves = new SaveStore(path)) {
                    saves.lock(); MainMenu menu = new MainMenu(host, saves, path, dir.resolve("settings.properties"));
                    menu.show();
                    if (find(host, "How to Play") != null) throw new AssertionError("Guide button removed");
                    if (Files.exists(path) || find(host, "Continue Expedition").isEnabled()) throw new AssertionError("Menu must not create a campaign");
                    menu.start(true);
                    if (!Files.exists(path) || find(host, "Main Menu") == null) throw new AssertionError("New expedition opens gameplay");
                    find(host, "Main Menu").doClick();
                    if (!find(host, "Continue Expedition").isEnabled()) throw new AssertionError("Saved game can continue");
                    menu.start(false);
                    if (find(host, "Main Menu") == null || saves.load().activeCount() != 4) throw new AssertionError("Continue restores campaign");
                    menu.quit();
                    if (host.isDisplayable()) throw new AssertionError("Quit disposes window");
                } catch (Exception e) { throw new RuntimeException(e); }
                finally { host.dispose(); }
            });
        } finally {
            Files.deleteIfExists(path); Files.deleteIfExists(dir.resolve("campaign.properties.lock")); Files.deleteIfExists(dir);
        }
    }
}
