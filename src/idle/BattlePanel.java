package idle;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import idle.GameState.Hero;

public final class BattlePanel extends JPanel {
    private final GameState state;
    private long animation;
    private int observedTick = -1;
    private long tickStarted = -100;
    private final java.util.Map<Hero, Long> fallenAt = new java.util.HashMap<>();
    private final BufferedImage scene = new BufferedImage(384, 180, BufferedImage.TYPE_INT_RGB);
    public BattlePanel(GameState state) { this.state = state; setPreferredSize(new Dimension(768, 360)); setBackground(new Color(0x11121b)); }
    public void animate() {
        animation++;
        if (state.ticks != observedTick) { observedTick = state.ticks; tickStarted = animation; }
        for (Hero h : state.heroes) {
            if (h.hp <= 0) fallenAt.putIfAbsent(h, animation); else fallenAt.remove(h);
        }
        repaint();
    }
    private void box(Graphics2D g, int c, int x, int y, int w, int h) { g.setColor(new Color(c)); g.fillRect(x, y, w, h); }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = scene.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        box(g, 0x161824, 0, 0, 384, 180);
        boolean corridor = AssetPack.drawCorridor(g, animation);
        if (!corridor) {
        box(g, 0xc4c4b0, 290, 18, 22, 22); box(g, 0x161824, 283, 14, 22, 22);
        for (int i = 0; i < 32; i++) box(g, 0x686875, (i * 73 + 19) % 384, (i * 17 + 11) % 65, 1, 1);
        g.setColor(new Color(0x242635));
        g.fillPolygon(new int[]{0, 45, 110, 155, 210, 275, 340, 384}, new int[]{100, 44, 102, 54, 104, 47, 95, 68}, 8);
        box(g, 0x292b37, 236, 49, 45, 64); box(g, 0x292b37, 230, 40, 12, 74);
        box(g, 0x292b37, 275, 35, 12, 79);
        for (int i = 0; i < 4; i++) box(g, 0x141824, 245 + (i % 2) * 16, 58 + (i / 2) * 20, 5, 10);
        box(g, 0x191e25, 0, 108, 384, 72); box(g, 0x38383b, 0, 134, 384, 46);
        for (int i = 0; i < 90; i++) box(g, i % 2 == 0 ? 0x494444 : 0x292e33, (i * 43) % 384, 138 + (i * 11) % 42, 6, 2);
        for (int x : new int[]{17, 356}) {
            box(g, 0x101820, x, 61, 5, 76); box(g, 0x101820, x - 8, 76, 21, 3);
            box(g, 0x101820, x - 11, 65, 3, 12); box(g, 0x101820, x + 12, 56, 3, 21);
        }
        // Firelight with deliberately hard pixel edges.
        box(g, 0x674839, 196, 136, 14, 4);
        box(g, 0xc36c43, 199, 126, 8, 11); box(g, 0xf3c275, 201, 123 + (int) (animation / 6 % 3), 4, 12);
        }
        int phase = (int) Math.min(100, animation - tickStarted);
        boolean attacking = state.rest == 0 && state.lastDamage > 0 && phase < 18;
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 8));
        box(g, 0x171624, 8, 4, 192, 15);
        g.setColor(new Color(0xb9ac91)); g.drawString(corridor ? "COLD CORRIDORS / " + state.stage : state.region(), 12, 14);
        int index = 0;
        for (Hero h : state.heroes) if (h.active) {
            int x = 30 + index * 40;
            int y = corridor ? 103 + index % 2 * 3 : 87 + index % 2 * 10;
            box(g, 0x23232c, x, y + 43, 30, 4);
            Composite old = g.getComposite();
            if (h.hp <= 0) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .3f));
            int bob = h.hp > 0 && animation / 12 % 2 == index % 2 ? 1 : 0;
            int lunge = attacking ? 3 : 0;
            String action = h.hp <= 0 ? "death" : attacking ? "attack" : "idle";
            int frame = h.hp <= 0 ? (int) ((animation - fallenAt.getOrDefault(h, animation)) / 4) : attacking ? phase / 3 : (int) (animation / 5);
            PixelArt.drawActor(g, "characters", h.role.sprite(), h.role, action, frame, x + lunge, y - bob, 32, 40);
            g.setComposite(old);
            box(g, 0x181820, x, y + 48, 32, 3); box(g, 0x82a991, x, y + 48, (int) (32 * h.hp / h.maxHp()), 3);
            index++;
        }
        int enemyX = 280 + (animation % 30 > 23 ? -3 : 0);
        box(g, 0x23232c, 276, 140, 48, 5);
        String enemyAction = state.rest > 0 ? "idle" : attacking ? "hurt" : phase < 29 && state.lastDamage > 0 ? "attack" : "idle";
        int enemyFrame = enemyAction.equals("idle") ? (int) (animation / 5) : enemyAction.equals("hurt") ? phase / 4 : (phase - 18) / 2;
        PixelArt.drawActor(g, "enemies", Catalog.enemySprite(state.enemyName()), null, enemyAction, enemyFrame, enemyX, corridor ? 87 : 80, 48, 60);
        box(g, 0x171822, 264, 70, 72, 4); box(g, 0xb9656c, 264, 70, (int) (72 * state.enemyHp / state.enemyMaxHp()), 4);
        g.setColor(new Color(0xe0c89d)); g.drawString(state.boss() ? "ELITE ENCOUNTER" : "ENEMY", 270, 63);
        if (animation % 30 < 19 && state.lastDamage > 0) {
            g.setColor(new Color(0xf5d4a0)); g.drawString("-" + (int) state.lastDamage, 295, 89 - (int) (animation % 30 / 2));
            if (state.lastHealing > 0) { g.setColor(new Color(0xa3c99a)); g.drawString("+" + (int) state.lastHealing, 77, 82 - (int) (animation % 30 / 2)); }
        }
        if (state.rest > 0) {
            box(g, 0x1b1c27, 83, 40, 218, 22); g.setColor(new Color(0xe8c58f));
            g.drawString("RESTING AT CAMP  /  " + state.rest + "s", 105, 54);
        }
        g.dispose();
        int scale = Math.max(1, Math.min(getWidth() / 384, getHeight() / 180));
        graphics.drawImage(scene, (getWidth() - 384 * scale) / 2, (getHeight() - 180 * scale) / 2, 384 * scale, 180 * scale, null);
    }
}
