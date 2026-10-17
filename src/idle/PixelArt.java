package idle;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import idle.Catalog.*;

/** original procedural placeholders, with optional user-supplied sprite overrides */
public final class PixelArt {
    private static final Map<String, BufferedImage> cache = new HashMap<>();
    private static final Map<Role, BufferedImage> portraits = new EnumMap<>(Role.class);

    // prevent construction because drawing and image caches are shared
    private PixelArt() {}

    // build a small transparent roster portrait
    // use the same fitting rules as the selected hero card
    public static BufferedImage portrait(Role role) {
        BufferedImage image = new BufferedImage(96, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        drawPortrait(g, role, 0, 0, 96, 40);
        g.dispose(); return image;
    }

    // load and crop the visible idle art for a hero, including its weapon
    // respect custom png overrides and cache the crop for later repaints
    private static BufferedImage portraitSource(Role role) {
        if (portraits.containsKey(role)) return portraits.get(role);
        AssetPack.Actor actor = Files.exists(Paths.get("assets", "characters", role.sprite() + ".png")) ? null : AssetPack.actor("characters", role.sprite());
        BufferedImage source = actor == null ? sprite("characters", role.sprite(), role) : actor.portraitImage();
        int left = source.getWidth(), top = source.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) if ((source.getRGB(x, y) >>> 24) > 0) {
            left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        if (right >= left) source = source.getSubimage(left, top, right - left + 1, bottom - top + 1);
        portraits.put(role, source); return source;
    }

    /** a shared visible height lets even the widest weapon fit without cropping or distortion */
    // fit all classes to a shared visible height based on the widest portrait
    // center the result so long swords and bows stay inside the same frame
    public static void drawPortrait(Graphics2D graphics, Role role, int x, int y, int width, int height) {
        double widest = 1;
        for (Role member : Role.values()) {
            BufferedImage source = portraitSource(member);
            widest = Math.max(widest, source.getWidth() / (double) source.getHeight());
        }
        BufferedImage source = portraitSource(role);
        int h = Math.max(1, (int) Math.floor(Math.min(height, width / widest)));
        int w = Math.max(1, (int) Math.floor(h * source.getWidth() / (double) source.getHeight()));
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(source, x + (width - w) / 2, y + (height - h) / 2, w, h, null); g.dispose();
    }

    // load a custom sprite or create placeholder art when needed
    // cache the result so repainting does not repeat file access
    public static BufferedImage sprite(String group, String id, Role role) {
        String key = group + "/" + id;
        if (cache.containsKey(key)) return cache.get(key);
        BufferedImage art = null;
        Path file = Paths.get("assets", group, id + ".png");
        if (Files.exists(file)) {
            try { art = ImageIO.read(file.toFile()); }
            catch (Exception e) { System.err.println("Cannot read sprite " + file + ": " + e.getMessage()); }
        }
        if (art == null) art = role == null ? enemy(id) : hero(role);
        cache.put(key, art); return art;
    }

    // choose a custom override, a supplied animation sheet, or fallback art
    // this is the common drawing entry point for battlefield actors
    public static void drawActor(Graphics2D g, String group, String id, Role role, String action, int frame, int x, int y, int w, int h) {
        // explicit user overrides take precedence over the bundled pack mapping
        AssetPack.Actor packed = Files.exists(Paths.get("assets", group, id + ".png")) ? null : AssetPack.actor(group, id);
        if (packed != null) packed.draw(g, action, frame, x, y, w, h, group.equals("enemies"));
        else draw(g, sprite(group, id, role), x, y, w, h, frame);
    }

    // draw one colored pixel-art block
    // placeholder heroes and enemies are assembled from these simple shapes
    private static void rect(Graphics2D g, int color, int x, int y, int w, int h) {
        g.setColor(new Color(color)); g.fillRect(x, y, w, h);
    }

    // generate a recognizable fallback hero when no supplied art is available
    // colors and weapon shapes distinguish the classes
    private static BufferedImage hero(Role role) {
        BufferedImage img = new BufferedImage(32, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        boolean mage = role == Role.WIZARD || role == Role.PRIEST;
        rect(g, 0x15141e, 8, 8, 16, 29);
        rect(g, role.color, 9, 19, 14, 12);
        rect(g, 0x393744, 10, 31, 5, 7); rect(g, 0x393744, 18, 31, 5, 7);
        rect(g, 0xdbc0a0, 12, 10, 10, 10); rect(g, 0x292630, 18, 14, 2, 2);
        rect(g, role.color, 10, 7, 14, 6);
        rect(g, role.color, 6, 20, 4, 10); rect(g, 0xe1c4a3, 23, 22, 4, 5);
        rect(g, 0xd3ba78, 11, 29, 12, 2);
        if (mage) {
            rect(g, role.color, 13, 3, 7, 5); rect(g, role.color, 15, 0, 3, 4);
            rect(g, role.color, 9, 30, 15, 7); rect(g, 0x927047, 27, 11, 2, 25);
            rect(g, role == Role.PRIEST ? 0xffe4a4 : 0x96dce9, 25, 6, 6, 6);
        } else if (role == Role.ARCHER) {
            rect(g, 0x9e7447, 28, 13, 2, 19); rect(g, 0x9e7447, 25, 10, 3, 3);
            rect(g, 0x9e7447, 25, 32, 3, 3); rect(g, 0xcbbb9d, 25, 13, 1, 19);
        } else if (role == Role.LANCER || role == Role.SOLDIER) {
            rect(g, 0x927047, 28, 8, 2, 30); rect(g, 0xd1d6da, 26, 3, 5, 7);
        } else if (role == Role.ARMORED_AXEMAN) {
            rect(g, 0x927047, 27, 12, 2, 25); rect(g, 0xa9b8be, 23, 8, 9, 9);
        } else {
            rect(g, 0xc6d4d9, 27, 10, 3, 16); rect(g, 0xd3ba78, 24, 26, 8, 2);
            rect(g, 0x4b566e, 4, 22, 7, 11); rect(g, 0xc6d4d9, 5, 24, 5, 2);
            rect(g, 0x3a3b48, 11, 13, 11, 3);
        }
        g.dispose(); return img;
    }

    // generate fallback enemy art from its identifier
    // this keeps an encounter drawable even if a pack or override is missing
    private static BufferedImage enemy(String id) {
        BufferedImage img = new BufferedImage(40, 44, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        int color = id.contains("skeleton") ? 0xc4c2b0 : id.contains("orc") ? 0x75835c : id.contains("were") ? 0x817a83 : 0x9f4b59;
        if (id.contains("slime") || id.contains("blood") || id.contains("ghostfire") || id.contains("eyeball")) {
            rect(g, color, 8, 24, 25, 14); rect(g, color, 12, 18, 17, 9);
            rect(g, 0xeee0bd, 15, 26, 4, 4); rect(g, 0xeee0bd, 24, 26, 4, 4);
        } else if (id.contains("bat")) {
            rect(g, 0x765478, 2, 15, 36, 7); rect(g, 0x765478, 5, 22, 30, 5);
            rect(g, 0x352b46, 16, 12, 9, 20); rect(g, 0xffb075, 17, 17, 2, 2); rect(g, 0xffb075, 22, 17, 2, 2);
        } else {
            rect(g, 0x25212c, 10, 8, 23, 32); rect(g, color, 13, 9, 16, 13);
            rect(g, color, 10, 23, 22, 11); rect(g, color, 5, 23, 6, 12); rect(g, color, 32, 23, 5, 12);
            rect(g, 0x504350, 12, 34, 7, 8); rect(g, 0x504350, 24, 34, 7, 8);
            rect(g, 0xf5ba83, 15, 14, 4, 3); rect(g, 0xf5ba83, 24, 14, 4, 3);
            rect(g, 0x2b2330, 18, 19, 7, 3);
            if (id.contains("demon") || id.contains("minotaur") || id.contains("were")) {
                rect(g, 0xbcaa92, 10, 3, 4, 9); rect(g, 0xbcaa92, 28, 3, 4, 9);
            }
            if (id.contains("armored") || id.contains("knight")) rect(g, 0x616775, 10, 23, 22, 7);
            if (id.contains("skeleton")) { rect(g, 0x40333b, 15, 26, 13, 2); rect(g, 0x40333b, 15, 30, 13, 2); }
        }
        g.dispose(); return img;
    }

    /** optional sheets contain horizontal equal-size frames. png height is the frame size */
    // fit an override image or equal-size horizontal frame sheet into a drawing area
    // keep its proportions and align its feet to the bottom
    public static void draw(Graphics2D g, BufferedImage image, int x, int y, int w, int h, int frame) {
        int size = image.getHeight();
        int count = image.getWidth() >= size * 2 && image.getWidth() % size == 0 ? image.getWidth() / size : 1;
        int sw = count > 1 ? size : image.getWidth(), sx = (frame % count) * sw;
        double scale = Math.min(w / (double) sw, h / (double) size);
        int dw = (int) (sw * scale), dh = (int) (size * scale);
        g.drawImage(image, x + (w - dw) / 2, y + h - dh, x + (w + dw) / 2, y + h, sx, 0, sx + sw, size, null);
    }
}
