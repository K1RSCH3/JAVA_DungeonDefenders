package idle;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import javax.imageio.ImageIO;

/** imports the supplied packs without renaming or modifying their original files */
public final class AssetPack {
    private static final Path ROOT = Paths.get("assets", "packs");
    private static final Map<String, Actor> actors = new HashMap<>();
    private static final Map<String, BufferedImage> images = new HashMap<>();
    private static final String PACK1 = "Tiny RPG Character Asset Pack 01 v2.0 -Full 22 Characters";
    private static final String PACK2 = "Tiny RPG Character Asset Pack 02 -Full 20 Characters";

    // prevent construction because asset access is shared through static helpers
    private AssetPack() {}

    // load an image once and cache the result, including missing files
    // repeated animation frames should not keep reading the disk
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

    // resolve a hero or enemy identifier to its supplied animation folder
    // return null when the pack cannot provide an idle clip so placeholder art can take over
    public static Actor actor(String group, String id) {
        String key = group + "/" + id;
        if (actors.containsKey(key)) return actors.get(key);
        String pack = null, name = null;
        if (group.equals("characters")) for (Catalog.Role role : Catalog.Role.values()) {
            if (role.sprite().equals(id)) { pack = PACK1; name = role.label; break; }
        }
        if (group.equals("enemies")) {
            for (int i = 0; i < Catalog.ENEMIES.length; i++) if (Catalog.enemySprite(Catalog.ENEMIES[i]).equals(id)) {
                pack = i < 13 ? PACK1 : PACK2; name = Catalog.ENEMIES[i].replace('-', ' '); break;
            }
        }
        Actor actor = name == null ? null : new Actor(ROOT.resolve(pack).resolve("Characters(100x100 split)").resolve(name).resolve(name), name);
        if (actor != null && actor.clips.get("idle") == null) actor = null;
        actors.put(key, actor); return actor;
    }
    public static final class Actor {
        private final Map<String, BufferedImage> clips = new HashMap<>();
        private final Rectangle body;
        private final double bodyScale;

        // load available action sheets and measure the visible idle pose
        // alternate filenames handle differences between the supplied packs
        Actor(Path folder, String name) {
            String[][] actions = {{"idle", "Idle", "Flying"}, {"attack", "Attack01", "Attack", "Attack01(With magic effects)", "Attack(With magic effects)"}, {"attack2", "Attack02", "Attack01", "Attack", "Attack02(With magic effects)"}, {"heal", "Heal", "Heal(With magic effects)"}, {"hurt", "Hurt"}, {"death", "Death"}, {"walk", "Walk", "Walk01", "Flying"}};
            for (String[] action : actions) {
                for (int i = 1; i < action.length; i++) {
                    BufferedImage clip = image(folder.resolve(name + "_" + action[i] + ".png"));
                    if (clip != null && clip.getHeight() == 100 && clip.getWidth() % 100 == 0) { clips.put(action[0], clip); break; }
                }
            }
            for (String suffix : new String[]{"Attack01_Effect", "Attack_effect"}) {
                BufferedImage effect = image(folder.resolve(name + "_" + suffix + ".png"));
                if (effect != null && effect.getHeight() == 100 && effect.getWidth() % 100 == 0) {
                    clips.put("impact", effect); break;
                }
            }
            BufferedImage idle = clips.get("idle");
            int left = 100, top = 100, right = 0, bottom = 0;
            if (idle != null) for (int y = 0; y < 100; y++) for (int x = 0; x < 100; x++) {
                if ((idle.getRGB(x, y) >>> 24) > 32) { left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y); }
            }
            body = right < left ? new Rectangle(0, 0, 100, 100) : new Rectangle(left, top, right - left + 1, bottom - top + 1);
            // the lancer's upright spear extends above his helmet and inflated the measured height
            // compensate for that extra reach while keeping the same foot anchor in every action
            bodyScale = name.equals("Lancer") ? 1.30 : 1.0;
        }

        // share body proportions with portraits without trimming the weapon from their artwork
        public double bodyScale() { return bodyScale; }

        // return the number of horizontal frames in an action sheet
        // missing actions use the idle sheet as a safe fallback
        public int frames(String action) { BufferedImage clip = clips.getOrDefault(action, clips.get("idle")); return clip.getWidth() / 100; }

        // check whether a named animation or standalone impact was loaded
        // callers can use this before requesting an optional effect
        public boolean has(String action) { return clips.containsKey(action); }

        // extract the visible first idle pose, including weapons
        // portrait layout can fit this crop without including the sheet's transparent padding
        public BufferedImage portraitImage() {
            return clips.get("idle").getSubimage(body.x, body.y, body.width, body.height);
        }

        /** center standalone magic frames on their recipient instead of the caster's anchor */
        // draw a standalone spell effect centered on its recipient
        // crop each effect frame so magic appears on the target instead of beside the caster
        public boolean drawImpact(Graphics2D g, int elapsed, int duration, int x, int y) {
            BufferedImage clip = clips.get("impact");
            if (clip == null) return false;
            int count = clip.getWidth() / 100;
            int frame = Math.min(count - 1, elapsed * count / duration);
            int left = 100, top = 100, right = -1, bottom = -1;
            for (int py = 0; py < 100; py++) for (int px = 0; px < 100; px++) {
                if ((clip.getRGB(frame * 100 + px, py) >>> 24) > 32) {
                    left = Math.min(left, px); right = Math.max(right, px);
                    top = Math.min(top, py); bottom = Math.max(bottom, py);
                }
            }
            if (right >= left) {
                int w = right - left + 1, h = bottom - top + 1;
                double scale = 48.0 / Math.max(w, h);
                int dw = (int) (w * scale), dh = (int) (h * scale);
                g.drawImage(clip, x - dw / 2, y - dh / 2, x + (dw + 1) / 2, y + (dh + 1) / 2,
                    frame * 100 + left, top, frame * 100 + right + 1, bottom + 1, null);
            }
            return true;
        }

        // draw one actor frame with a stable idle anchor and scale
        // loop walking and idle clips, but hold the final frame of attacks and deaths
        public void draw(Graphics2D g, String action, int frame, int x, int y, int w, int h, boolean flip) {
            BufferedImage clip = clips.getOrDefault(action, clips.get("idle"));
            int count = clip.getWidth() / 100;
            int index = action.equals("idle") || action.equals("walk") ? Math.floorMod(frame, count) : Math.min(Math.max(0, frame), count - 1);

            // use the idle body's anchor for every action, preserving attack reach and stable feet
            // match visible idle height, not the padded sheet or weapon width
            // keep this scale for every action so attacks never resize the actor
            double scale = h * bodyScale / body.height;
            int size = (int) Math.round(100 * scale);
            double center = body.x + body.width / 2.0;
            int dx = (int) Math.round(x + w / 2.0 - (flip ? 100 - center : center) * scale);
            int dy = (int) Math.round(y + h - (body.y + body.height) * scale);
            int sx = index * 100;
            g.drawImage(clip, flip ? dx + size : dx, dy, flip ? dx : dx + size, dy + size, sx, 0, sx + 100, 100, null);
        }
    }

    // resolve a file within the supplied corridor pack
    // keep the original pack layout intact
    private static Path corridor(String path) { return ROOT.resolve("Cold Corridors Files").resolve("Assets").resolve(path); }

    // paint the corridor layers, floor strip, and animated torches
    // the distance argument controls travel while the animation argument controls torch frames
    public static boolean drawCorridor(Graphics2D g, long animation) {
        return drawCorridor(g, animation, 0);
    }

    // paint the corridor layers, floor strip, and animated torches
    // the distance argument controls travel while the animation argument controls torch frames
    public static boolean drawCorridor(Graphics2D g, long animation, long distance) {
        BufferedImage far = image(corridor("Layers/far.png"));
        if (far == null) return false;
        int speed = 1;
        for (String name : new String[]{"far", "back", "middle", "near"}) {
            BufferedImage layer = image(corridor("Layers/" + name + ".png"));
            if (layer == null) continue;
            int offset = (int) (distance * speed / 4 % layer.getWidth());
            for (int x = -offset; x < 384; x += layer.getWidth()) g.drawImage(layer, x, -44, null);
            speed++;
        }
        BufferedImage tiles = image(corridor("Layers/tileset.png"));
        if (tiles != null) for (int x = -(int) (distance % tiles.getWidth()); x < 384; x += tiles.getWidth()) g.drawImage(tiles, x, 136, null);
        BufferedImage torch = image(corridor("Torch/torch-sheet.png"));
        if (torch != null) {
            int sx = (int) (animation / 5 % 4) * 32;
            for (int x = 128 - (int) (distance % 224) - 224; x < 384; x += 224) g.drawImage(torch, x, 54, x + 32, 86, sx, 0, sx + 32, 32, null);
        }
        return true;
    }
}
