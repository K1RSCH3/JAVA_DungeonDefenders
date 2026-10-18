package idle;

import java.awt.*;
import java.nio.file.*;
import javax.swing.*;

/** exercises menu/game ownership with a hidden window and an isolated save */
public final class MainMenuFlowTest {
    // lay out the hidden component tree before capturing the selector preview
    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child);
    }
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
                    if (Files.exists(path) || find(host, "Play") == null || find(host, "Continue Expedition") != null || find(host, "New Expedition") != null) throw new AssertionError("Menu uses a single play action without creating saves");
                    find(host, "Play").doClick();
                    if (find(host, "New Expedition") == null || Files.exists(path)) throw new AssertionError("Play opens the empty save selector");
                    CampaignLibrary library = new CampaignLibrary(path);
                    Path first = library.create("First journey");
                    Path second = library.create("Second journey");
                    String timestamp;
                    java.util.Properties original = new java.util.Properties();
                    try (java.io.Reader in = Files.newBufferedReader(first)) { original.load(in); }
                    timestamp = original.getProperty("savedAt");
                    library.rename(first, "Renamed journey");
                    java.util.Properties renamed = new java.util.Properties();
                    try (java.io.Reader in = Files.newBufferedReader(first)) { renamed.load(in); }
                    if (!timestamp.equals(renamed.getProperty("savedAt"))) throw new AssertionError("Rename preserves offline timestamp");
                    try { library.create("Renamed journey"); throw new AssertionError("Duplicate accepted"); } catch (java.io.IOException expected) { }
                    try { library.create("  "); throw new AssertionError("Blank accepted"); } catch (java.io.IOException expected) { }
                    menu.openCampaign(first);
                    if (!Files.exists(first) || find(host, "Main Menu") == null) throw new AssertionError("Named expedition opens gameplay");
                    find(host, "Main Menu").doClick();
                    find(host, "Play").doClick();
                    host.getContentPane().setSize(1280, 900); layout(host.getContentPane());
                    java.awt.image.BufferedImage preview = new java.awt.image.BufferedImage(1280, 900, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = preview.createGraphics(); host.getContentPane().paint(graphics); graphics.dispose();
                    javax.imageio.ImageIO.write(preview, "png", Paths.get("out", "campaign-selector.png").toFile());
                    menu.openCampaign(second);
                    if (find(host, "Main Menu") == null) throw new AssertionError("Second campaign opens independently");
                    find(host, "Main Menu").doClick();
                    library.delete(first);
                    if (Files.exists(first) || !Files.exists(second)) throw new AssertionError("Delete affects selected save only");
                    saves.save(new GameState());
                    if (!library.paths().contains(path.toAbsolutePath())) throw new AssertionError("Legacy save remains selectable");
                    menu.openCampaign(path);
                    if (find(host, "Main Menu") == null || saves.load().activeCount() != 4) throw new AssertionError("Legacy save restores campaign");
                    menu.quit();
                    if (host.isDisplayable()) throw new AssertionError("Quit disposes window");
                } catch (Exception e) { throw new RuntimeException(e); }
                finally { host.dispose(); }
            });
        } finally {
            try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
                for (Path file : (Iterable<Path>) files.sorted(java.util.Comparator.reverseOrder())::iterator) Files.deleteIfExists(file);
            }
        }
    }
}
