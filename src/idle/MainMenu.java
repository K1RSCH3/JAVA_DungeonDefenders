package idle;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import javax.swing.*;
import javax.swing.border.*;

/** owns the application window and the save lock across menu/game transitions */
public final class MainMenu {
    private static final Color GOLD = new Color(0xe0c18a), INK = new Color(0x14141e);
    private final JFrame window;
    private final SaveStore saves;
    private final Path savePath, settingsPath;
    private IdleGame game;
    private Timer ambience;
    private boolean reducedMotion, damageNumbers = true;
    private final JLabel status = new JLabel("Gather your defenders. Keep the darkness at bay.", SwingConstants.CENTER);

    // connect the menu to its window, save store, and settings paths
    // the path-taking version lets tests use temporary files without touching player data
    public MainMenu(JFrame window, SaveStore saves) {
        this(window, saves, Paths.get("ashen-save.properties"), Paths.get("settings.properties"));
    }

    // connect the menu to its window, save store, and settings paths
    // the path-taking version lets tests use temporary files without touching player data
    MainMenu(JFrame window, SaveStore saves, Path savePath, Path settingsPath) {
        this.window = window; this.saves = saves;
        this.savePath = savePath; this.settingsPath = settingsPath;
        if (Files.exists(settingsPath)) try (Reader reader = Files.newBufferedReader(settingsPath)) {
            Properties p = new Properties(); p.load(reader);
            reducedMotion = Boolean.parseBoolean(p.getProperty("reducedMotion", "false"));
            damageNumbers = Boolean.parseBoolean(p.getProperty("damageNumbers", "true"));
        } catch (IOException | IllegalArgumentException e) { status.setText("Settings could not be loaded; using defaults."); }
    }

    // build the animated menu and its action buttons
    // continuing is available only when a campaign file exists
    public JPanel view() {
        JPanel background = new JPanel(new GridBagLayout()) {
            private long frame;

            // paint the menu surface with its themed artwork
            // the background honors reduced motion and information dialogs use parchment
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                BufferedImage scene = new BufferedImage(384, 180, BufferedImage.TYPE_INT_RGB);
                Graphics2D art = scene.createGraphics(); AssetPack.drawCorridor(art, reducedMotion ? 0 : frame, reducedMotion ? 0 : frame / 3); art.dispose();
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int scale = Math.max(1, (int) Math.ceil(Math.max(getWidth() / 384.0, getHeight() / 180.0)));
                g.drawImage(scene, (getWidth() - 384 * scale) / 2, (getHeight() - 180 * scale) / 2, 384 * scale, 180 * scale, null);
                g.setColor(new Color(10, 11, 19, 185)); g.fillRect(0, 0, getWidth(), getHeight()); g.dispose(); frame++;
            }

            // enable readable text when painting the complete menu
            // use a graphics copy so settings do not leak into other components
            @Override public void paint(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                super.paint(g); g.dispose();
            }
        };
        JPanel card = UiSkin.panel(null); card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS)); card.setBackground(INK);
        card.setBorder(new EmptyBorder(32, 50, 32, 50));
        card.add(Box.createVerticalStrut(12)); addLabel(card, "DUNGEON DEFENDERS", 44, true);
        card.add(Box.createVerticalStrut(26));
        JButton resume = action("Continue Expedition", () -> start(false)); resume.setEnabled(Files.exists(savePath)); card.add(resume);
        card.add(Box.createVerticalStrut(10)); card.add(action("New Expedition", () -> start(true)));
        card.add(Box.createVerticalStrut(10)); card.add(action("Settings", this::settings));
        card.add(Box.createVerticalStrut(10)); card.add(action("Credits", () -> information("Chronicle & Credits", "<b>Dungeon Defenders</b><br>A Java desktop idle RPG.<br><br><b>Character art</b><br>Tiny RPG Character Asset Packs 01 and 02.<br><br><b>Environment</b><br>Cold Corridors background and torch assets.<br><br><b>Interface and font</b><br>Pixelta (bundled font).<br>Free Medieval Fantasy UI Pack by Kibyra.<br>Wood, iron, parchment, and interface icons.<br><br>Original pack files and included license documents<br>are preserved under assets/packs/.")));
        card.add(Box.createVerticalStrut(10)); card.add(action("Quit Game", this::quit));
        card.add(Box.createVerticalStrut(22)); status.setForeground(new Color(0xc3bdaf)); status.setFont(UiSkin.font(16)); status.setAlignmentX(.5f); card.add(status);
        card.add(Box.createVerticalStrut(10)); addLabel(card, "Progress saves automatically during expeditions", 13, false);
        background.add(card); return background;
    }

    // add a centered menu label and decorate large titles with the crossed-swords icon
    private static void addLabel(JPanel panel, String text, int size, boolean bold) {
        JLabel label = new JLabel(text); label.setFont(UiSkin.font(size)); label.setForeground(GOLD); label.setAlignmentX(.5f); panel.add(label);
        if (size >= 38) { label.setIcon(UiSkin.icon(2, 5, 40)); label.setIconTextGap(12); }
    }

    // create one menu button and connect its click handler
    // shared dimensions keep the menu actions aligned
    private static JButton action(String title, Runnable handler) {
        JButton button = new JButton(title); button.setFont(UiSkin.font(22)); button.setForeground(GOLD); button.setBackground(new Color(0x25232d));
        button.setIcon(UiSkin.actionIcon(title)); button.setIconTextGap(12);
        button.setFocusPainted(true); button.setBorder(new EmptyBorder(9, 20, 9, 20));
        button.setAlignmentX(.5f); button.setMaximumSize(new Dimension(390, 46)); button.setPreferredSize(new Dimension(390, 46)); button.addActionListener(e -> handler.run()); return button;
    }

    // replace gameplay with the menu and start the ambience timer
    // stop any previous menu timer before creating another one
    public void show() {
        game = null; if (ambience != null) ambience.stop();
        window.setContentPane(view()); window.revalidate(); window.repaint();
        ambience = new Timer(50, e -> window.repaint()); ambience.start();
    }

    // start a new expedition or continue a saved one
    // back up an existing campaign before replacement and checkpoint offline rewards before entering gameplay
    void start(boolean fresh) {
        if (fresh && Files.exists(savePath) && JOptionPane.showConfirmDialog(window, "Begin a new expedition? Your current save will be backed up before starting fresh.", "New Expedition", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            GameState state;
            if (fresh) {
                if (Files.exists(savePath)) Files.copy(savePath, savePath.resolveSibling("ashen-save-backup-" + System.currentTimeMillis() + ".properties"));
                state = new GameState();
            } else {
                if (!Files.exists(savePath)) throw new IOException("No saved expedition was found. Choose New Expedition.");
                state = saves.load(); double reward = state.offlineReward(System.currentTimeMillis());
                if (reward > 0) state.message = "Welcome back. Your camp earned " + (int) reward + " gold while you were away.";
            }
            saves.save(state);
            IdleGame next = new IdleGame(state, saves);
            ambience.stop(); game = next; next.show(window, this::returnToMenu, reducedMotion, damageNumbers);
        } catch (Exception e) { information("Unable to begin expedition", escape(e.getMessage())); }
    }

    // save and stop gameplay before reopening the menu
    // if saving fails, keep the expedition open and show an error
    void returnToMenu() {
        if (game != null && !game.saveAndStop()) { information("Save failed", "Could not save progress. The expedition is still running; try again."); return; }
        status.setText("Your expedition is saved. Rest awhile, defender."); show();
    }

    // edit and save display preferences after the player confirms them
    // cancelling leaves the current preferences unchanged
    private void settings() {
        JCheckBox motion = new JCheckBox("Reduce motion (static scenery and character poses)", reducedMotion);
        JCheckBox numbers = new JCheckBox("Show damage and healing numbers", damageNumbers);
        if (JOptionPane.showConfirmDialog(window, new Object[]{motion, numbers}, "Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        Properties p = new Properties(); p.setProperty("reducedMotion", "" + motion.isSelected()); p.setProperty("damageNumbers", "" + numbers.isSelected());
        try (Writer writer = Files.newBufferedWriter(settingsPath)) {
            p.store(writer, "Dungeon Defenders settings"); reducedMotion = motion.isSelected(); damageNumbers = numbers.isSelected(); status.setText("Settings saved.");
        } catch (IOException e) { information("Settings could not be saved", escape(e.getMessage())); }
    }

    // escape text that will be inserted into an html label
    // error messages must not accidentally become markup
    private static String escape(String value) { return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    // show a framed parchment message for credits or errors
    // the caller supplies already prepared html content
    private void information(String title, String content) {
        JPanel parchment = new JPanel(new BorderLayout()) {
            // paint the menu surface with its themed artwork
            // the background honors reduced motion and information dialogs use parchment
            @Override protected void paintComponent(Graphics g) { UiSkin.draw(g, "parchment", 0, 0, getWidth(), getHeight(), true); }
        };
        parchment.setOpaque(false); parchment.setBorder(new EmptyBorder(26, 30, 26, 30));
        JLabel text = new JLabel("<html><body style='width:380px;font-size:14px'>" + content + "</body></html>");
        text.setForeground(new Color(0x30251d)); parchment.add(text, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(window, parchment, title, JOptionPane.PLAIN_MESSAGE);
    }

    // save active gameplay, stop timers, release the save lock, and close the window
    // a save failure keeps the application open
    void quit() {
        if (game != null && !game.saveAndStop()) { information("Save failed", "Could not save progress. Return to your expedition and try again."); return; }
        if (ambience != null) ambience.stop();
        try { saves.close(); } catch (IOException e) { System.err.println(e.getMessage()); }
        window.dispose();
    }

    // acquire the save lock and create the shared window
    // route window-close requests through quit so they follow the same save rules
    public static void launch() {
        SaveStore saves = new SaveStore(Paths.get("ashen-save.properties"));
        try {
            saves.lock(); JFrame window = new JFrame("Dungeon Defenders / Iron & Ash");
            MainMenu menu = new MainMenu(window, saves);
            window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); window.setMinimumSize(new Dimension(1240, 870)); window.setSize(1280, 900); window.setLocationRelativeTo(null);
            window.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { menu.quit(); } });
            menu.show(); window.setVisible(true);
        } catch (Exception e) {
            try { saves.close(); } catch (IOException ignored) { }
            JOptionPane.showMessageDialog(null, e.getMessage(), "Unable to start Dungeon Defenders", JOptionPane.ERROR_MESSAGE);
        }
    }
}
