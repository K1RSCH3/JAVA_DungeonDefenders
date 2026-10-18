package idle;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;

/** pixelta and kibyra's medieval wood, iron, parchment, and icon assets */
public final class UiSkin {
    private static final Font PIXELTA = loadFont();
    private static final Map<String, BufferedImage> sprites = new HashMap<>();

    // prevent construction because theme resources are shared
    private UiSkin() {}

    // load and register the bundled pixelta font
    // fall back to a system serif font if the file cannot be read
    private static Font loadFont() {
        try {
            Font font = Font.createFont(Font.TRUETYPE_FONT, Paths.get("assets", "fonts", "Pixelta.ttf").toFile());
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font); return font;
        }
        catch (Exception e) { System.err.println("Pixelta unavailable: " + e.getMessage()); return new Font(Font.SERIF, Font.PLAIN, 16); }
    }

    // return the game font at a readable size
    // small requested sizes are raised to the minimum instead of becoming illegible
    public static Font font(int size) { return PIXELTA.deriveFont(Font.PLAIN, (float) Math.max(18, size)); }

    // load an image from the selected medieval ui pack
    // missing required interface art is reported rather than silently replaced
    public static BufferedImage asset(String relative) {
        BufferedImage image = AssetPack.image(Paths.get("assets", "packs", "Free_Medieval_Fantasy_UI_Pack").resolve(relative));
        if (image == null) throw new IllegalStateException("Missing medieval UI asset: " + relative);
        return image;
    }

    // scale an individual pack icon with crisp pixel edges
    // sheet and icon numbers follow the original asset filenames
    public static Icon icon(int sheet, int id, int size) {
        String folder = sheet == 1 ? "UI Icons" : "UI Icons " + sheet;
        BufferedImage source = asset(folder + "/icon" + id + ".png");
        return new ImageIcon(source.getScaledInstance(size, size, Image.SCALE_REPLICATE));
    }

    // show equipment artwork large enough to recognize its material and shape
    public static Icon itemIcon(Catalog.Item item) {
        return itemIcon(item, 32);
    }

    // use the selected raven artwork in shops, equipped slots, and inventory choices
    // retain the medieval slot icon if the optional local equipment pack is missing
    public static Icon itemIcon(Catalog.Item item, int size) {
        BufferedImage source = AssetPack.image(Paths.get("assets", "packs", "Free - Raven Fantasy Icons", "Selected", "fa" + item.iconId() + ".png"));
        if (source != null) return new ImageIcon(source.getScaledInstance(size, size, Image.SCALE_REPLICATE));
        int id = item.slot == Catalog.Slot.HAT ? 13 : item.slot == Catalog.Slot.ARMOR ? 12 : item.slot == Catalog.Slot.SHOES ? 44 :
            item.weapon == Catalog.Weapon.SWORD ? 9 : item.weapon == Catalog.Weapon.SPEAR ? 56 : item.weapon == Catalog.Weapon.AXE ? 16 : item.weapon == Catalog.Weapon.BOW ? 11 : 25;
        return icon(3, id, size);
    }

    // choose a visual hint from an action's label
    // unknown actions receive a generic shield icon
    public static Icon actionIcon(String title) {
        String t = title.toLowerCase(Locale.ROOT);
        if (t.contains("how") || t.contains("credit")) return icon(1, 32, 24);
        if (t.contains("train") || t.contains("recruit")) return icon(1, 31, 24);
        return icon(3, t.contains("continue") ? 37 : t.contains("new") ? 9 : t.contains("setting") ? 35 : t.contains("how") ? 22 : t.contains("credit") ? 22 : t.contains("quit") ? 40 : t.contains("train") || t.contains("recruit") ? 27 : t.contains("forge") ? 38 : t.contains("menu") ? 33 : t.contains("expand") || t.contains("show party") ? 37 : 12, 24);
    }

    // resolve a named frame or panel and trim its transparent outer padding
    // cache the prepared image for repeated ui painting
    public static BufferedImage sprite(String name) {
        if (sprites.containsKey(name)) return sprites.get(name);
        int id = name.equals("panel") ? 58 : name.equals("selection") ? 14 : name.equals("parchment") ? 25 : name.equals("field") ? 12 : name.equals("scroll") ? 59 : 51;
        BufferedImage source = asset("UI Icons 2/icon" + id + ".png");
        int left = source.getWidth(), top = source.getHeight(), right = 0, bottom = 0;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) if ((source.getRGB(x, y) >>> 24) > 0) {
            left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        BufferedImage image = source.getSubimage(left, top, right - left + 1, bottom - top + 1);
        sprites.put(name, image); return image;
    }

    // resize a frame in nine pieces so corners stay intact
    // the center flag lets callers draw a full panel or just its border
    public static void draw(Graphics g, String name, int x, int y, int w, int h, boolean center) {
        BufferedImage image = sprite(name);
        Graphics2D art = (Graphics2D) g.create(); art.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int cut = 6, edge = Math.min(center ? 12 : 4, Math.min(w, h) / 3);
        int[] sx = {0, cut, image.getWidth() - cut, image.getWidth()}, sy = {0, cut, image.getHeight() - cut, image.getHeight()};
        int[] dx = {x, x + edge, x + w - edge, x + w}, dy = {y, y + edge, y + h - edge, y + h};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) if (center || row != 1 || col != 1)
            art.drawImage(image, dx[col], dy[row], dx[col + 1], dy[row + 1], sx[col], sy[row], sx[col + 1], sy[row + 1], null);
        art.dispose();
    }

    // create a transparent swing panel that paints a medieval frame and background
    // its layout remains the caller's choice
    public static JPanel panel(LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            // paint this small control's themed background before any foreground symbol
            // this is called whenever swing repaints the component
            @Override protected void paintComponent(Graphics g) { draw(g, "panel", 0, 0, getWidth(), getHeight(), true); }
        };
        panel.setOpaque(false); panel.setBorder(new EmptyBorder(12, 12, 12, 12)); return panel;
    }

    // create the shared framed border and content padding for fields and scroll panes
    public static Border border() {
        return new AbstractBorder() {
            // return the content space reserved by the frame
            // the overload can reuse swing's supplied insets object
            @Override public Insets getBorderInsets(Component c) { return new Insets(3, 9, 3, 9); }
            // return the content space reserved by the frame
            // the overload can reuse swing's supplied insets object
            @Override public Insets getBorderInsets(Component c, Insets insets) { insets.set(3, 9, 3, 9); return insets; }
            // draw only the field frame around the control, leaving its content visible
            @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) { draw(g, "field", x, y, w, h, false); }
        };
    }

    // register the font, icons, and custom control painters with swing
    // call this before building the menu or game screens
    public static void install() {
        java.util.List<Object> keys = new ArrayList<>();
        for (Object key : UIManager.getDefaults().keySet()) if (UIManager.get(key) instanceof Font) keys.add(key);
        for (Object key : keys) UIManager.put(key, new FontUIResource(font(16)));
        UIManager.put("ButtonUI", SkinButton.class.getName());
        UIManager.put("TabbedPaneUI", SkinTabs.class.getName());
        UIManager.put("ComboBoxUI", SkinCombo.class.getName());
        UIManager.put("ScrollBarUI", SkinScroll.class.getName());
        UIManager.put("CheckBox.icon", new Icon() {
            // reserve the fixed checkbox icon dimensions used by the settings dialog
            public int getIconWidth() { return 24; } public int getIconHeight() { return 24; }

            // show the checked or unchecked pack icon according to the checkbox model
            public void paintIcon(Component c, Graphics g, int x, int y) { icon(((AbstractButton) c).isSelected() ? 1 : 2, ((AbstractButton) c).isSelected() ? 8 : 13, 24).paintIcon(c, g, x, y); }
        });
        UIManager.put("ComboBox.border", border()); UIManager.put("ScrollPane.border", border());
        UIManager.put("Button.foreground", new Color(0xf0dab0)); UIManager.put("Button.background", new Color(0x252532));
    }
    public static class SkinButton extends BasicButtonUI {
        private javax.swing.Timer tween;
        private javax.swing.event.ChangeListener changes;
        private float hover;

        // create a fresh painter for this control type
        // swing uses this factory when applying the registered theme
        public static ComponentUI createUI(JComponent c) { return new SkinButton(); }

        // configure a button and attach its hover timer and model listener
        // hidden buttons update immediately so preview rendering does not need a live window
        @Override public void installUI(JComponent c) {
            super.installUI(c); AbstractButton b = (AbstractButton) c; b.setOpaque(false); b.setContentAreaFilled(false); b.setRolloverEnabled(true); b.setBorder(new EmptyBorder(9, 12, 9, 12));
            tween = new javax.swing.Timer(16, e -> {
                float target = b.isEnabled() && b.getModel().isRollover() ? 1 : 0;
                hover += Math.signum(target - hover) * Math.min(.16f, Math.abs(target - hover));
                b.repaint(); if (Math.abs(target - hover) < .01) tween.stop();
            });
            changes = e -> { if (b.isShowing()) tween.start(); else { hover = b.isEnabled() && b.getModel().isRollover() ? 1 : 0; b.repaint(); } };
            b.addChangeListener(changes);
        }

        // stop the hover timer and detach its listener when the button skin is removed
        // this avoids leaving unused timers running
        @Override public void uninstallUI(JComponent c) {
            if (tween != null) tween.stop(); if (changes != null) ((AbstractButton) c).removeChangeListener(changes); super.uninstallUI(c);
        }

        // paint the button frame, hover tint, selected marker, and pressed offset
        // move the label with the frame so pressing feels like one solid control
        @Override public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            boolean pressed = b.isEnabled() && b.getModel().isPressed() && b.getModel().isArmed();
            String state = pressed ? "1" : "4";
            Graphics2D art = (Graphics2D) g.create();
            if (!b.isEnabled()) art.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .45f));
            draw(art, "button_" + state, 0, pressed ? 3 : 0, c.getWidth(), c.getHeight() - (pressed ? 3 : 0), true);
            if (b.isEnabled() && hover > 0) { art.setColor(new Color(235, 195, 115, (int) (hover * (pressed ? 22 : 44)))); art.fillRoundRect(5, 4, c.getWidth() - 10, c.getHeight() - 10, 8, 8); }
            art.dispose();
            if (Boolean.TRUE.equals(b.getClientProperty("selectedHero"))) draw(g, "selection", 0, 0, c.getWidth(), c.getHeight(), false);
            Graphics2D text = (Graphics2D) g.create(); text.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (pressed) text.translate(0, 3);
            super.paint(text, c); text.dispose();
        }

        // skip the default pressed fill because paint already draws the themed pressed state
        @Override protected void paintButtonPressed(Graphics g, AbstractButton b) { }
        // draw enabled or disabled button text and preserve keyboard mnemonic underlines
        @Override protected void paintText(Graphics g, AbstractButton b, Rectangle rect, String text) {
            g.setColor(b.isEnabled() ? b.getForeground() : new Color(0x9995a0));
            BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, b.getDisplayedMnemonicIndex(), rect.x, rect.y + g.getFontMetrics().getAscent());
        }

        // draw the selection frame around keyboard-focused buttons so focus is visible without a mouse
        @Override protected void paintFocus(Graphics g, AbstractButton b, Rectangle view, Rectangle text, Rectangle icon) { draw(g, "selection", 1, 1, b.getWidth() - 2, b.getHeight() - 2, false); }
    }
    public static class SkinTabs extends BasicTabbedPaneUI {
        // create a fresh painter for this control type
        // swing uses this factory when applying the registered theme
        public static ComponentUI createUI(JComponent c) { return new SkinTabs(); }

        // reserve padding around tab text and icons
        // this keeps labels away from the frame edges
        @Override protected Insets getTabInsets(int placement, int index) { return new Insets(8, 12, 8, 12); }
        // draw each tab using the same wooden assets as buttons
        // add a lighter tint when the pointer is over it
        @Override protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) {
            draw(g, selected ? "button_1" : "button_4", x, y, w, h, true);
            if (index == getRolloverTab()) { g.setColor(new Color(235, 195, 115, 40)); g.fillRect(x + 5, y + 4, w - 10, h - 8); }
        }

        // skip swing's standard border because the asset already includes one
        @Override protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) { }
        // leave the content border to the surrounding themed panels
        @Override protected void paintContentBorder(Graphics g, int placement, int selectedIndex) { }
    }
    public static class SkinCombo extends BasicComboBoxUI {
        // create a fresh painter for this control type
        // swing uses this factory when applying the registered theme
        public static ComponentUI createUI(JComponent c) { return new SkinCombo(); }

        // build a dropdown arrow that fits inside the skinned combo box
        // draw the arrow explicitly so it remains visible in narrow controls
        @Override protected JButton createArrowButton() {
            JButton button = new JButton() {
                // paint this small control's themed background before any foreground symbol
                // this is called whenever swing repaints the component
                @Override protected void paintComponent(Graphics graphics) {
                    super.paintComponent(graphics);
                    graphics.setColor(new Color(0xe0c18a)); int x = getWidth() / 2, y = getHeight() / 2;
                    graphics.fillPolygon(new int[]{x - 4, x + 4, x}, new int[]{y - 2, y - 2, y + 3}, 3);
                }
            };
            button.setBorder(new EmptyBorder(0, 0, 0, 0)); return button;
        }
    }
    public static class SkinScroll extends BasicScrollBarUI {
        // create a fresh painter for this control type
        // swing uses this factory when applying the registered theme
        public static ComponentUI createUI(JComponent c) { return new SkinScroll(); }

        // draw the scrollbar's movable wooden thumb when it has a visible area
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) { if (!r.isEmpty()) draw(g, "scroll", r.x, r.y, r.width, r.height, true); }
        // fill the scrollbar track with a dark color so the thumb remains easy to locate
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { g.setColor(new Color(0x191617)); g.fillRect(r.x, r.y, r.width, r.height); }
        // create a themed scrollbar arrow for the requested orientation
        @Override protected JButton createDecreaseButton(int orientation) { return new BasicArrowButton(orientation, new Color(0x34251f), new Color(0x171319), new Color(0xd2ae78), new Color(0x7e6348)); }
        // reuse the same arrow styling for the opposite scrolling direction
        @Override protected JButton createIncreaseButton(int orientation) { return createDecreaseButton(orientation); }
    }
}
