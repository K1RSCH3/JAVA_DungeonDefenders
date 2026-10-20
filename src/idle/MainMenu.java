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
    private final Path settingsPath;
    private final CampaignLibrary library;
    private SaveStore activeSave;
    private boolean choosingCampaign;
    private IdleGame game;
    private Timer ambience;
    private boolean reducedMotion, damageNumbers = true;
    private final GameAudio audio = Boolean.getBoolean("dungeon.audio.disabled") ? null : new GameAudio();
    private int musicVolume = 35, effectsVolume = 55;
    private final JLabel status = new JLabel("", SwingConstants.CENTER);

    // connect the menu to its window, save store, and settings paths
    // the path-taking version lets tests use temporary files without touching player data
    public MainMenu(JFrame window, SaveStore saves) {
        this(window, saves, Paths.get("ashen-save.properties"), Paths.get("settings.properties"));
    }

    // connect the menu to its window, save store, and settings paths
    // the path-taking version lets tests use temporary files without touching player data
    MainMenu(JFrame window, SaveStore saves, Path savePath, Path settingsPath) {
        this.window = window; this.saves = saves;
        this.settingsPath = settingsPath;
        this.library = new CampaignLibrary(savePath);
        if (Files.exists(settingsPath)) try (Reader reader = Files.newBufferedReader(settingsPath)) {
            Properties p = new Properties(); p.load(reader);
            reducedMotion = Boolean.parseBoolean(p.getProperty("reducedMotion", "false"));
            damageNumbers = Boolean.parseBoolean(p.getProperty("damageNumbers", "true"));
            musicVolume = volume(p, "musicVolume", 35);
            effectsVolume = volume(p, "effectsVolume", 55);
        } catch (IOException | IllegalArgumentException e) { status.setText("Settings could not be loaded; using defaults."); }
    }

    // accept older settings without audio keys and clamp hand-edited volume values
    static int volume(Properties properties, String key, int fallback) {
        try { return Math.max(0, Math.min(100, Integer.parseInt(properties.getProperty(key, "" + fallback)))); }
        catch (NumberFormatException e) { return fallback; }
    }

    // build the animated menu and its action buttons
    // the play screen lists independent campaigns without creating or loading progress
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
        if (choosingCampaign) {
            buildCampaigns(card); background.add(card); return background;
        }
        card.add(Box.createVerticalStrut(12)); addLabel(card, "DUNGEON DEFENDERS", 44, true);
        card.add(Box.createVerticalStrut(26));
        card.add(action("Play", () -> { choosingCampaign = true; show(); }));
        card.add(Box.createVerticalStrut(10)); card.add(action("Settings", this::settings));
        card.add(Box.createVerticalStrut(10)); card.add(action("Credits", () -> information("Chronicle & Credits", "<b>Dungeon Defenders</b><br>A Java desktop idle RPG.<br><br><b>Character art</b><br>Tiny RPG Character Asset Packs 01 and 02.<br><br><b>Equipment icons</b><br>Free - Raven Fantasy Icons.<br><br><b>Audio</b><br>NimbeKit Fantasy RPG SFX 160.<br>Dungeon and boss music.<br><br><b>Environment</b><br>Cold Corridors background and torch assets.<br><br><b>Interface and font</b><br>Pixelta (bundled font).<br>Free Medieval Fantasy UI Pack by Kibyra.<br>Wood, iron, parchment, and interface icons.<br><br>Original pack files and included license documents<br>are preserved under assets/packs/.")));
        card.add(Box.createVerticalStrut(10)); card.add(action("Quit Game", this::quit));
        status.setForeground(new Color(0xc3bdaf)); status.setFont(UiSkin.font(16)); status.setAlignmentX(.5f);
        if (!status.getText().isEmpty()) { card.add(Box.createVerticalStrut(16)); card.add(status); }
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

    // show framed expedition cards with a left-side options control and a separate play action
    // damaged saves remain visible so the player can remove them without blocking other campaigns
    private void buildCampaigns(JPanel card) {
        addLabel(card, "YOUR EXPEDITIONS", 38, true);
        card.add(Box.createVerticalStrut(10));
        addLabel(card, "Choose a journey or begin a new one", 18, false);
        card.add(Box.createVerticalStrut(20));
        JPanel rows = new JPanel(); rows.setOpaque(false); rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        try {
            java.util.List<Path> paths = library.paths();
            if (paths.isEmpty()) {
                JPanel empty = UiSkin.panel(new BorderLayout(16, 12));
                empty.setBorder(new EmptyBorder(35, 25, 35, 25));
                JLabel emblem = new JLabel(UiSkin.icon(3, 37, 48)); empty.add(emblem, BorderLayout.WEST);
                JLabel text = new JLabel("<html>Your chronicle begins here<br>Create your first expedition below</html>");
                text.setFont(UiSkin.font(20)); text.setForeground(GOLD); empty.add(text); rows.add(empty);
            }
            for (Path path : paths) {
                CampaignLibrary.Entry entry = null;
                try { entry = new CampaignLibrary.Entry(path); } catch (IOException ignored) { }
                final String name = entry == null ? path.getFileName().toString() : entry.name;
                JPanel row = UiSkin.panel(new BorderLayout(16, 0));
                row.setBorder(new EmptyBorder(16, 16, 16, 16));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118)); row.setPreferredSize(new Dimension(640, 118));
                JButton options = action("", () -> campaignOptions(path, name));
                options.setIcon(UiSkin.icon(3, 35, 26)); options.setPreferredSize(new Dimension(56, 52));
                options.setToolTipText("Rename or delete " + name);
                options.getAccessibleContext().setAccessibleName("Options for " + name);
                row.add(options, BorderLayout.WEST);
                JPanel details = new JPanel(new GridLayout(2, 1, 0, 6)); details.setOpaque(false);
                JLabel title = new JLabel(name); title.setFont(UiSkin.font(24)); title.setForeground(GOLD);
                title.putClientProperty("html.disable", Boolean.TRUE); title.setToolTipText(name);
                title.setIcon(UiSkin.icon(3, 37, 26)); title.setIconTextGap(10); details.add(title);
                String summary = entry == null ? "Save unavailable - use options to manage" : "Stage " + entry.state.stage + "   /   " + (long) entry.state.gold + " gold   /   " + entry.state.activeCount() + "/4 party";
                JLabel progress = new JLabel(summary); progress.setFont(UiSkin.font(18)); progress.setForeground(new Color(0xd0c4b4)); details.add(progress);
                row.add(details, BorderLayout.CENTER);
                JButton play = action("Play", () -> openCampaign(path)); play.setPreferredSize(new Dimension(140, 52)); play.setEnabled(entry != null);
                row.add(play, BorderLayout.EAST); rows.add(row); rows.add(Box.createVerticalStrut(10));
            }
        } catch (IOException e) { addLabel(rows, "Unable to list expeditions: " + e.getMessage(), 18, false); }
        JScrollPane scroll = new JScrollPane(rows); scroll.setPreferredSize(new Dimension(740, 380));
        scroll.setMaximumSize(new Dimension(740, 380)); scroll.setOpaque(false); scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24); card.add(scroll);
        card.add(Box.createVerticalStrut(18)); card.add(action("New Expedition", this::createCampaign));
        card.add(Box.createVerticalStrut(10)); card.add(action("Back", () -> { choosingCampaign = false; show(); }));
    }

    // reuse the wooden frame, pack icons, and themed buttons for naming and destructive choices
    private JDialog campaignDialog(String title, JPanel content) {
        JDialog dialog = new JDialog(window, title, true);
        JPanel frame = UiSkin.panel(new BorderLayout(16, 20)); frame.setBorder(new EmptyBorder(28, 30, 28, 30));
        JLabel heading = new JLabel(title, UiSkin.icon(3, 37, 32), SwingConstants.LEFT);
        heading.setFont(UiSkin.font(26)); heading.setForeground(GOLD); frame.add(heading, BorderLayout.NORTH);
        content.setOpaque(false); frame.add(content); dialog.setContentPane(frame);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return dialog;
    }

    // ask for a display name without ever using it as a filesystem path
    // validation errors stay inside the dialog so the player can correct the text
    private void nameCampaign(Path path, String initial) {
        JPanel body = new JPanel(new BorderLayout(12, 16));
        JTextField input = new JTextField(initial, 25); input.setFont(UiSkin.font(22)); input.setBorder(UiSkin.border());
        input.getAccessibleContext().setAccessibleName("Expedition name"); body.add(input, BorderLayout.NORTH);
        JLabel hint = new JLabel("Choose a unique name, up to 32 characters"); hint.setForeground(GOLD); body.add(hint);
        JDialog dialog = campaignDialog(path == null ? "Name your expedition" : "Rename expedition", body);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0)); buttons.setOpaque(false);
        JButton confirm = action(path == null ? "Create" : "Rename", () -> {
            try {
                Path selected = path;
                if (path == null) selected = library.create(input.getText()); else library.rename(path, input.getText());
                dialog.dispose(); show(); if (path == null) openCampaign(selected);
            } catch (IOException e) { hint.setText(e.getMessage()); dialog.pack(); }
        });
        buttons.add(confirm); buttons.add(action("Cancel", dialog::dispose)); body.add(buttons, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(confirm); dialog.pack(); dialog.setLocationRelativeTo(window);
        SwingUtilities.invokeLater(() -> { input.requestFocusInWindow(); input.selectAll(); }); dialog.setVisible(true);
    }

    // create a new named slot and leave every other expedition untouched
    private void createCampaign() { nameCampaign(null, ""); }

    // place rename and delete behind each card's left-side options button
    private void campaignOptions(Path path, String name) {
        JPanel body = new JPanel(new GridLayout(3, 1, 0, 12));
        JDialog dialog = campaignDialog("Expedition options", body);
        body.add(action("Rename", () -> { dialog.dispose(); nameCampaign(path, name); }));
        body.add(action("Delete", () -> { dialog.dispose(); confirmDelete(path, name); }));
        body.add(action("Cancel", dialog::dispose)); dialog.pack(); dialog.setLocationRelativeTo(window); dialog.setVisible(true);
    }

    // require an explicit confirmation naming the selected save before permanently removing it
    private void confirmDelete(Path path, String name) {
        JPanel body = new JPanel(new BorderLayout(12, 20));
        JLabel warning = new JLabel("<html>Delete <b>" + escape(name) + "</b>?<br>Its saved progress will be permanently removed.</html>");
        warning.setFont(UiSkin.font(20)); warning.setForeground(GOLD); body.add(warning);
        JDialog dialog = campaignDialog("Delete expedition", body);
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0)); buttons.setOpaque(false);
        JButton cancel = action("Cancel", dialog::dispose); buttons.add(cancel);
        buttons.add(action("Delete save", () -> {
            try { library.delete(path); dialog.dispose(); show(); }
            catch (IOException e) { information("Delete failed", escape(e.getMessage())); }
        })); body.add(buttons, BorderLayout.SOUTH); dialog.getRootPane().setDefaultButton(cancel);
        dialog.pack(); dialog.setLocationRelativeTo(window); dialog.setVisible(true);
    }

    // acquire the chosen slot before loading it and checkpoint offline rewards exactly once
    void openCampaign(Path path) {
        SaveStore selected = path.toAbsolutePath().normalize().equals(library.legacy) ? saves : new SaveStore(path);
        try {
            if (selected != saves) selected.lock();
            if (!Files.exists(path)) throw new IOException("This expedition no longer exists.");
            GameState state = selected.load(); double reward = state.offlineReward(System.currentTimeMillis());
            if (reward > 0) state.message = "Welcome back. Earned " + (long) reward + " offline gold.";
            selected.save(state);
            IdleGame next = new IdleGame(state, selected); next.setAudio(audio);
            if (ambience != null) ambience.stop(); activeSave = selected; game = next;
            next.show(window, this::returnToMenu, reducedMotion, damageNumbers);
        } catch (Exception e) {
            if (selected != saves) try { selected.close(); } catch (IOException ignored) { }
            information("Unable to begin expedition", escape(e.getMessage()));
        }
    }

    // release the selected slot only after gameplay has saved and stopped
    private void closeActiveSave() {
        if (activeSave != null && activeSave != saves) try { activeSave.close(); } catch (IOException e) { System.err.println(e.getMessage()); }
        activeSave = null;
    }

    // replace gameplay with the menu and start the ambience timer
    // stop any previous menu timer before creating another one
    public void show() {
        game = null; if (ambience != null) ambience.stop();
        if (audio != null) { audio.volumes(musicVolume, effectsVolume); audio.stopEffects(); audio.scene(false); }
        window.setContentPane(view()); window.revalidate(); window.repaint();
        ambience = new Timer(50, e -> window.repaint()); ambience.start();
    }

    // save and stop gameplay before reopening the menu
    // if saving fails, keep the expedition open and show an error
    void returnToMenu() {
        if (game != null && !game.saveAndStop()) { information("Save failed", "Could not save progress. The expedition is still running; try again."); return; }
        closeActiveSave(); choosingCampaign = false;
        status.setText("Expedition saved."); show();
    }

    // edit and save display preferences after the player confirms them
    // cancelling leaves the current preferences unchanged
    private void settings() {
        JCheckBox motion = new JCheckBox("Reduce motion (static scenery and character poses)", reducedMotion);
        JCheckBox numbers = new JCheckBox("Show damage and healing numbers", damageNumbers);
        JSlider music = new JSlider(0, 100, musicVolume), effects = new JSlider(0, 100, effectsVolume);
        for (JSlider slider : new JSlider[]{music, effects}) { slider.setMajorTickSpacing(25); slider.setPaintTicks(true); slider.setPaintLabels(true); }
        music.getAccessibleContext().setAccessibleName("Music volume"); effects.getAccessibleContext().setAccessibleName("Effects and ambience volume");
        if (JOptionPane.showConfirmDialog(window, new Object[]{motion, numbers, "Music volume (0 mutes)", music, "Effects and ambience volume (0 mutes)", effects}, "Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        Properties p = new Properties(); p.setProperty("reducedMotion", "" + motion.isSelected()); p.setProperty("damageNumbers", "" + numbers.isSelected());
        p.setProperty("musicVolume", "" + music.getValue()); p.setProperty("effectsVolume", "" + effects.getValue());
        try (Writer writer = Files.newBufferedWriter(settingsPath)) {
            p.store(writer, "Dungeon Defenders settings"); reducedMotion = motion.isSelected(); damageNumbers = numbers.isSelected(); status.setText("Settings saved.");
            musicVolume = music.getValue(); effectsVolume = effects.getValue();
            if (audio != null) audio.volumes(musicVolume, effectsVolume);
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
        closeActiveSave();
        if (audio != null) audio.close();
        try { saves.close(); } catch (IOException e) { System.err.println(e.getMessage()); }
        window.dispose();
    }

    // acquire the save lock and create the shared window
    // route window-close requests through quit so they follow the same save rules
    public static void launch() {
        SaveStore saves = new SaveStore(Paths.get("ashen-save.properties"));
        try {
            saves.lock(); JFrame window = new JFrame("Dungeon Defenders");
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
