package idle;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import javax.imageio.ImageIO;

/** Imports the supplied packs without renaming or modifying their original files. */
public final class AssetPack {
    private static final Path ROOT = Paths.get("assets", "packs");
    private static final Map<String, Actor> actors = new HashMap<>();
    private static final Map<String, BufferedImage> images = new HashMap<>();
    private static final String PACK1 = "Tiny RPG Character Asset Pack 01 v2.0 -Free Soldier&Orc";
    private static final String PACK2 = "Tiny RPG Character Asset Pack 02 -Free Demon_A&Blood Monster_A";
    private AssetPack() {}
    public static BufferedImage image(Path path) {
        String key = path.toString();
        if (!images.containsKey(key)) {
            BufferedImage image = null;
            if (Files.exists(path)) try { image = ImageIO.read(path.toFile()); }
            catch (IOException e) { System.err.println("Cannot load " + path + ": " + e.getMessage()); }
            images.put(key, image);
        }
        return images.get(key);
    }
    public static Actor actor(String group, String id) {
        String key = group + "/" + id;
        if (actors.containsKey(key)) return actors.get(key);
        String pack = null, name = null;
        if (group.equals("characters") && id.equals("soldier")) { pack = PACK1; name = "Soldier"; }
        if (group.equals("enemies")) {
            if (id.equals("orc")) { pack = PACK1; name = "Orc"; }
            if (id.equals("demon_a")) { pack = PACK2; name = "Demon_A"; }
            if (id.equals("blood_monster_a")) { pack = PACK2; name = "Blood Monster_A"; }
        }
        Actor actor = name == null ? null : new Actor(ROOT.resolve(pack).resolve("Characters(100x100 split)").resolve(name).resolve(name), name);
        if (actor != null && actor.clips.get("idle") == null) actor = null;
        actors.put(key, actor); return actor;
    }
    public static final class Actor {
        private final Map<String, BufferedImage> clips = new HashMap<>();
        private final Rectangle body;
        Actor(Path folder, String name) {
            String[][] actions = {{"idle", "Idle"}, {"attack", "Attack01"}, {"hurt", "Hurt"}, {"death", "Death"}, {"walk", "Walk"}};
            for (String[] action : actions) {
                BufferedImage clip = image(folder.resolve(name + "_" + action[1] + ".png"));
                if (clip != null && clip.getHeight() == 100 && clip.getWidth() % 100 == 0) clips.put(action[0], clip);
            }
            BufferedImage idle = clips.get("idle");
            int left = 100, top = 100, right = 0, bottom = 0;
            if (idle != null) for (int y = 0; y < 100; y++) for (int x = 0; x < 100; x++) {
                if ((idle.getRGB(x, y) >>> 24) > 32) { left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y); }
            }
            body = right < left ? new Rectangle(0, 0, 100, 100) : new Rectangle(left, top, right - left + 1, bottom - top + 1);
        }
        public int frames(String action) { BufferedImage clip = clips.getOrDefault(action, clips.get("idle")); return clip.getWidth() / 100; }
        public void draw(Graphics2D g, String action, int frame, int x, int y, int w, int h, boolean flip) {
            BufferedImage clip = clips.getOrDefault(action, clips.get("idle"));
            int count = clip.getWidth() / 100;
            int index = action.equals("idle") || action.equals("walk") ? Math.floorMod(frame, count) : Math.min(Math.max(0, frame), count - 1);
            // Use the idle body's anchor for every action, preserving attack reach and stable feet.
            double scale = Math.min(w / (double) body.width, h / (double) body.height);
            int size = (int) Math.round(100 * scale);
            double center = body.x + body.width / 2.0;
            int dx = (int) Math.round(x + w / 2.0 - (flip ? 100 - center : center) * scale);
            int dy = (int) Math.round(y + h - (body.y + body.height) * scale);
            int sx = index * 100;
            g.drawImage(clip, flip ? dx + size : dx, dy, flip ? dx : dx + size, dy + size, sx, 0, sx + 100, 100, null);
        }
    }
    private static Path corridor(String path) { return ROOT.resolve("Cold Corridors Files").resolve("Assets").resolve(path); }
    public static boolean drawCorridor(Graphics2D g, long animation) {
        BufferedImage far = image(corridor("Layers/far.png"));
        if (far == null) return false;
        for (String name : new String[]{"far", "back", "middle", "near"}) {
            BufferedImage layer = image(corridor("Layers/" + name + ".png"));
            if (layer == null) continue;
            for (int x = 0; x < 384; x += layer.getWidth()) g.drawImage(layer, x, -44, null);
        }
        BufferedImage torch = image(corridor("Torch/torch-sheet.png"));
        if (torch != null) {
            int sx = (int) (animation / 5 % 4) * 32;
            for (int x = 128; x < 384; x += 224) g.drawImage(torch, x, 54, x + 32, 86, sx, 0, sx + 32, 32, null);
        }
        return true;
    }
}
