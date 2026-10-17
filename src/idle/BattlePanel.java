package idle;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import idle.GameState.Hero;

// turn completed combat events into timed pixel scenes
// keep presentation history here so save files only need gameplay state, not animation frames
public final class BattlePanel extends JPanel {
    // logical action frames are stretched by this factor while redraws keep their normal cadence
    // the departure hold covers late impacts and deaths before the walking segment begins
    private static final double MOTION_SCALE = 1.4;
    private static final int DEPARTURE_HOLD = 59, TRANSITION_END = 122, ROUND_FRAMES = 42;
    private final GameState state;
    private long animation, distance, tickStarted = -100;
    private int observedTick = -1, transition = DEPARTURE_HOLD, observedKills, previousStage;
    private int defeatedStage;
    private boolean reducedMotion, damageNumbers = true;

    // compare successive rounds for hurt reactions and remember the last drawn positions
    // death positions prevent a hero who falls during a lunge from jumping back to formation
    private final java.util.Map<Hero, Double> previousHp = new java.util.HashMap<>();
    private final java.util.Set<Hero> wounded = new java.util.HashSet<>();
    private final java.util.Map<Hero, Long> fallenAt = new java.util.HashMap<>();
    private final java.util.Map<Hero, Point> heroDrawPositions = new java.util.HashMap<>();
    private final java.util.Map<Hero, Point> heroDeathPositions = new java.util.HashMap<>();

    // a negative time means no death has started for that slot
    // separate departed times keep existing corpses down when the final enemy is defeated
    private final long[] enemyFallenAt = {-1, -1, -1, -1};
    private final long[] departedFallenAt = {-1, -1, -1, -1};
    private static final int HEART_COUNT = 6;
    private final java.util.Map<String, HeartState> hearts = new java.util.HashMap<>();
    private static final class HeartState {
        double from, target;
        long changed;

        // start a heart animation history at the current hp fraction
        // the first draw should not pretend the character was just damaged
        HeartState(double hp) { from = target = hp; changed = -100; }

        // get the delayed heart fill for the current animation frame
        // a second hp change can start from the displayed value rather than jumping backward
        double value(long now) { return steppedHealth(from, target, now - changed); }
    }

    // hold the previous hp briefly, then move through discrete half-heart steps
    // finish at the exact fraction so small hits do not disappear or overshoot
    static double steppedHealth(double from, double target, long elapsed) {
        if (elapsed < 6) return from;
        if (elapsed >= 18) return target;
        double progress = Math.min(1, (1 + (elapsed - 6) / 3) / 4.0);

        // half-heart steps keep the retro hud deliberately choppy
        double value = from + (target - from) * progress;
        return Math.max(Math.min(from, target), Math.min(Math.max(from, target), Math.round(value * 12) / 12.0));
    }

    // calculate how far an attacker has moved toward its target
    // both ends reach zero so returning to an idle pose does not teleport the character home
    static double lungeProgress(double phase, int duration) {
        if (phase < 0 || phase >= duration - 1) return 0;
        double t = phase < 6 ? phase / 6.0 : phase < duration - 7 ? 1 : (duration - 1 - phase) / 6.0;
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    // find the filled part of one of six hearts
    // for example, a fraction between two full hearts produces one partially filled heart
    static double heartFill(double fraction, int index) {
        return Math.max(0, Math.min(1, fraction * HEART_COUNT - index));
    }

    // draw world art at a small fixed resolution, then enlarge it with crisp pixel scaling
    // hud text is added afterward at display resolution for readability
    private final BufferedImage scene = new BufferedImage(384, 216, BufferedImage.TYPE_INT_RGB);

    // connect a battlefield view to the game state and remember its starting hp values
    // this panel animates results but does not decide combat damage
    public BattlePanel(GameState state) {
        this.state = state; observedKills = state.kills; previousStage = state.stage;
        for (Hero h : state.heroes) previousHp.put(h, h.hp);
        setPreferredSize(new Dimension(960, 540)); setBackground(new Color(0x11121b));
    }

    // apply the player's motion and floating-number preferences
    // turning off motion keeps combat rules unchanged
    public void setDisplayOptions(boolean reducedMotion, boolean damageNumbers) { this.reducedMotion = reducedMotion; this.damageNumbers = damageNumbers; }

    // report whether an encounter is still ending or arriving
    // include a victory that happened before the next animation callback observes it
    public boolean transitioning() { return transition < TRANSITION_END || state.kills != observedKills; }

    // allow a new round only after movement and the previous action sequence finish
    // this prevents a timer callback from replacing attacks while they are still playing
    public boolean readyForCombat() {
        return !transitioning() && (state.ticks == 0 || state.ticks == observedTick)
            && (state.combatEvents.isEmpty() || animation - tickStarted >= ROUND_FRAMES);
    }

    // advance the visual clock, movement, and death histories once
    // keep departed enemy death times separate so old corpses never replay their death when the patrol is cleared
    public void animate() {
        animation++;
        if (state.kills != observedKills) {
            observedKills = state.kills; defeatedStage = previousStage; transition = 0;

            // keep completed deaths. only the final victims start a new death animation
            for (int i = 0; i < departedFallenAt.length; i++)
                departedFallenAt[i] = enemyFallenAt[i] >= 0 ? enemyFallenAt[i] : animation + 40;
        }
        if (previousStage != state.stage || transition == 0) java.util.Arrays.fill(enemyFallenAt, -1);
        previousStage = state.stage;
        if (transition < TRANSITION_END) {
            if (transition >= DEPARTURE_HOLD && state.rest == 0) distance += travelDistance((transition - DEPARTURE_HOLD + 1) / MOTION_SCALE) - travelDistance((transition - DEPARTURE_HOLD) / MOTION_SCALE);
            transition++;
        }
        if (state.ticks != observedTick) {
            observedTick = state.ticks; tickStarted = animation; wounded.clear();
            for (Hero h : state.heroes) { if (h.hp < previousHp.getOrDefault(h, h.hp)) wounded.add(h); previousHp.put(h, h.hp); }
        }
        for (Hero h : state.heroes) {
            if (h.hp <= 0) {
                fallenAt.putIfAbsent(h, animation);
                heroDeathPositions.putIfAbsent(h, heroDrawPositions.getOrDefault(h, heroPoint(h)));
            } else { fallenAt.remove(h); heroDeathPositions.remove(h); }
        }
        for (int i = 0; i < state.enemyCount(); i++) {
            if (state.memberHp(i) <= 0 && enemyFallenAt[i] < 0)
                enemyFallenAt[i] = wasHit(i, state.stage) ? tickStarted + 40 : animation;
            if (state.memberHp(i) > 0) enemyFallenAt[i] = -1;
        }
        repaint();
    }

    // return the elapsed death frame for a current or departing enemy
    // a negative result means its death has not started yet
    int enemyDeathFrame(int index, boolean departing) {
        long started = (departing ? departedFallenAt : enemyFallenAt)[index];
        return started < 0 || animation < started ? -1 : (int) ((animation - started) / (3 * MOTION_SCALE));
    }

    // ease the floor through the travel segment
    // starting and stopping gradually keeps the walking cycle from jerking
    static int travelDistance(double frame) {
        double t = Math.max(0, Math.min(1, frame / 45.0));
        return (int) Math.round(90 * t * t * (3 - 2 * t));
    }

    // place incoming enemies beyond the right edge and bring them into formation
    // the final offset is exactly zero to avoid a snap when combat starts
    static int entranceOffset(double frame) {
        double t = Math.max(0, Math.min(1, frame / 45.0));
        return (int) Math.round(280 * (1 - t * t * (3 - 2 * t)));
    }

    // check whether a specific enemy received outgoing damage in the recorded round
    // the stage check keeps effects from leaking into a new encounter
    private boolean wasHit(int enemy, int stage) {
        return state.combatEvents.stream().anyMatch(e -> !e.incoming && !e.healing && e.enemy == enemy && e.stage == stage);
    }

    // choose when each hero role begins its action in the round
    // these offsets stagger archery, melee attacks, healing, and slower spell casts
    static int attackDelay(Catalog.Role role) {
        switch (role) {
            case PRIEST: return 0;
            case ARCHER: return 2;
            case LANCER: case SOLDIER: return 4;
            case KNIGHT: case SWORDSMAN: return 6;
            case KNIGHT_TEMPLAR: case ARMORED_AXEMAN: return 8;
            case WIZARD: return 10;
            default: return 0;
        }
    }

    // choose a visual attack style from the enemy's identity
    // archers shoot, magical creatures cast, and other enemies use melee movement
    static String enemyStyle(String name) {
        String id = Catalog.enemySprite(name);
        if (id.contains("archer")) return "ranged";
        if (id.contains("warlock") || id.contains("necromancer") || id.contains("demon") || id.contains("ghostfire") || id.contains("eyeball")) return "magic";
        return "melee";
    }

    // stagger enemy actions by style and formation slot
    // the delay must leave enough time for the attack to finish before the next round
    static int enemyDelay(String name, int index) {
        String style = enemyStyle(name);
        return (style.equals("ranged") ? 6 : style.equals("magic") ? 12 : 8) + index % 2;
    }

    // draw overlapping translucent ovals beneath an actor
    // flying creatures use lighter shadows to suggest separation from the floor
    private static void shadow(Graphics2D g, int center, int feet, int width, boolean flying) {
        Graphics2D shade = (Graphics2D) g.create();
        shade.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int layer = 0; layer < 4; layer++) {
            int w = width - layer * 4, h = Math.max(3, width / 3 - layer);
            shade.setColor(new Color(8, 6, 19, flying ? 13 : 22));
            shade.fillOval(center - w / 2, feet + 2 - h / 2, w, h);
        }
        shade.dispose();
    }

    // find the resting formation position of an active hero
    // return null for a hero at camp because that hero has no battlefield position
    private Point heroPoint(Hero hero) {
        int index = 0;
        for (Hero h : state.heroes) if (h.active) {
            if (h == hero) return new Point(41 + index % 2 * 72 + index / 2 * 12, 125 + index / 2 * 45);
            index++;
        }
        return null;
    }

    // find an enemy's normal impact position for its stage and size
    // flying creatures have raised targets so projectiles do not hit empty ground
    private Point enemyPoint(int index, int stage) {
        String name = Encounters.members(stage)[index];
        boolean boss = Encounters.boss(stage);
        String id = Catalog.enemySprite(name);
        int lift = id.contains("bat") || id.contains("ghostfire") || id.contains("eyeball") ? 14 : 0;
        return new Point(boss ? 300 : 244 + index % 2 * 66 + index / 2 * 8,
            (boss ? 180 : 145 + index / 2 * 45) - Encounters.height(name, boss) / 2 - lift);
    }

    // find a hero's current visual position during a lunge or death
    // incoming attacks and healing effects use this instead of targeting the empty starting spot
    private Point heroPosition(Hero hero, double phase) {
        if (hero.hp <= 0 && heroDeathPositions.get(hero) != null) return new Point(heroDeathPositions.get(hero));
        Point point = heroPoint(hero);
        double local = phase - attackDelay(hero.role);
        if (point == null || reducedMotion || hero.hp <= 0 || state.rest > 0 || local < 0 || local >= 18
            || hero.role == Catalog.Role.WIZARD || hero.role == Catalog.Role.PRIEST || hero.role == Catalog.Role.ARCHER) return point;
        GameState.CombatEvent strike = state.combatEvents.stream().filter(e -> e.hero == hero && !e.healing && !e.incoming).findFirst().orElse(null);
        if (strike != null) {
            Point target = enemyPoint(strike.enemy, strike.stage);
            double lunge = lungeProgress(local, 18);
            point.x += (int) ((target.x - 32 - point.x) * lunge);
            point.y += (int) ((target.y - point.y) * lunge);
        }
        return point;
    }

    // draw attacks and healing from the recorded combat events
    // match each effect to its real recipient and combine simultaneous numbers at the same position
    private void drawEffects(Graphics2D g, int phase, int stage) {
        if (phase >= 30 || state.rest > 0) return;
        java.util.Map<Point, Double> numbers = new java.util.LinkedHashMap<>();
        for (GameState.CombatEvent e : state.combatEvents) {
            if (e.stage != stage) continue;
            String enemyName = e.incoming ? Encounters.members(stage)[e.enemy] : "";
            int local = phase - (e.incoming ? enemyDelay(enemyName, e.enemy) : attackDelay(e.hero.role));
            if (local < 0 || local >= (e.incoming ? 16 : 18)) continue;
            Point source = e.incoming ? enemyPoint(e.enemy, stage) : heroPoint(e.hero);
            Point target = e.incoming || e.healing ? heroPosition(e.ally, (animation - tickStarted) / MOTION_SCALE) : enemyPoint(e.enemy, stage);
            if (source == null || target == null) continue;
            boolean magic = e.healing || (e.incoming ? enemyStyle(enemyName).equals("magic") : e.hero.role == Catalog.Role.WIZARD || e.hero.role == Catalog.Role.PRIEST);
            int color = e.healing ? 0x86efac : magic ? 0xc39bff : e.incoming ? 0xff8c82 : 0xffe2a0;
            g.setColor(new Color(color));
            if (!reducedMotion) {
                if (e.healing) {
                    g.drawOval(target.x - 12, target.y + 12, 24, 8);
                    int y = target.y - local / 2;
                    g.fillRect(target.x - 1, y - 5, 3, 11); g.fillRect(target.x - 5, y - 1, 11, 3);
                } else if (local < 8) {
                    int x = source.x + (target.x - source.x) * local / 8;
                    int y = source.y + (target.y - source.y) * local / 8;
                    if (magic) { g.fillOval(x - 3, y - 3, 6, 6); g.drawLine(x - 7, y, x, y); }
                    else if (e.incoming ? enemyStyle(enemyName).equals("ranged") : e.hero.role == Catalog.Role.ARCHER) {
                        int direction = e.incoming ? -1 : 1;
                        g.drawLine(x - 9 * direction, y, x + 3 * direction, y); g.drawLine(x, y - 2, x + 3 * direction, y); g.drawLine(x, y + 2, x + 3 * direction, y);
                    }
                } else {
                    int radius = 3 + (local - 8);
                    if (magic) {
                        AssetPack.Actor caster = AssetPack.actor(e.incoming ? "enemies" : "characters", e.incoming ? Catalog.enemySprite(enemyName) : e.hero.role.sprite());
                        if (caster != null) caster.drawImpact(g, local - 8, e.incoming ? 8 : 10, target.x, target.y);
                        g.setColor(new Color(e.incoming ? 0xff795e : 0xc39bff));
                        g.setStroke(new BasicStroke(2));
                        g.drawOval(target.x - radius - 5, target.y - radius - 5, radius * 2 + 10, radius * 2 + 10);
                    } else {
                        g.setStroke(new BasicStroke(3));
                        g.drawArc(target.x - 18, target.y - 23, 36, 46, -65, 135);
                        g.setColor(new Color(0xfff3cd));
                        g.drawLine(target.x - 11, target.y + 14, target.x + 11, target.y - 14);
                        g.drawLine(target.x - 9, target.y - 9, target.x + 9, target.y + 9);
                    }
                    g.setStroke(new BasicStroke(1));
                }
            }
            if (damageNumbers && local >= 8) {
                numbers.merge(target, e.healing ? e.amount : -e.amount, Double::sum);
            }
        }
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 7));
        for (java.util.Map.Entry<Point, Double> number : numbers.entrySet()) {
            Point target = number.getKey(); double amount = number.getValue();
            String label = (amount > 0 ? "+" : "-") + (int) Math.ceil(Math.abs(amount));
            int x = target.x - g.getFontMetrics().stringWidth(label) / 2, y = target.y - 21;
            g.setColor(new Color(0x10121a)); g.drawString(label, x + 1, y + 1);
            g.setColor(new Color(amount > 0 ? 0x86efac : 0xffe2a0)); g.drawString(label, x, y);
        }
    }

    // draw compact party and enemy cards above the scene
    // departing enemies show depleted hearts while their death animations finish
    private void drawHud(Graphics2D g, int ox, int oy, double scale, int stage, boolean dying) {
        java.util.List<Hero> party = new java.util.ArrayList<>();
        for (Hero h : state.heroes) if (h.active) party.add(h);
        String[] enemies = Encounters.members(stage);
        for (int side = 0; side < 2; side++) {
            int count = side == 0 ? party.size() : enemies.length;
            for (int i = 0; i < count; i++) {
                int x = ox + (int) ((6 + side * 190 + i % 2 * 92) * scale);
                int y = oy + (int) ((4 + i / 2 * 22) * scale);
                int w = (int) (90 * scale), h = (int) (20 * scale), portrait = (int) (13 * scale);
                g.setColor(new Color(0x17, 0x16, 0x24, 128));
                g.fillRect(x, y, w, h);
                Hero hero = side == 0 ? party.get(i) : null;
                String name = hero != null ? hero.role.label : enemies[i];
                double hp = hero != null ? hero.hp : dying ? 0 : state.memberHp(i);
                double max = hero != null ? hero.maxHp() : GameState.encounterMaxHp(stage) / enemies.length;
                Graphics2D portraitGraphics = (Graphics2D) g.create(x, y, portrait, h);
                PixelArt.drawActor(portraitGraphics, hero != null ? "characters" : "enemies", hero != null ? hero.role.sprite() : Catalog.enemySprite(name), hero == null ? null : hero.role, "idle", 0, 0, 2, portrait, portrait);
                portraitGraphics.dispose();
                int tx = x + portrait + 4, bw = w - portrait - 10;
                g.setFont(UiSkin.font(Math.max(18, (int) (6 * scale))));
                g.setColor(new Color(0xf1dcae));
                int available = bw;
                String label = name;
                while (label.length() > 1 && g.getFontMetrics().stringWidth(label) > available) label = label.substring(0, label.length() - 1);
                if (!label.equals(name) && label.length() > 1) label = label.substring(0, label.length() - 1) + "…";
                g.drawString(label, tx, y + g.getFontMetrics().getAscent());
                String key = hero != null ? "hero:" + hero.role : "enemy:" + stage + ":" + i;
                drawHearts(g, key, hp / max, tx, y + (int) (9 * scale), bw, scale);
            }
        }
    }

    // draw six proportional hearts using the ui pack and per-character history
    // damage flashes and healing marks are disabled in reduced-motion mode
    private void drawHearts(Graphics2D g, String key, double fraction, int x, int y, int width, double scale) {
        fraction = Math.max(0, Math.min(1, fraction));
        HeartState status = hearts.get(key);
        if (status == null) {
            status = new HeartState(fraction); hearts.put(key, status);

            // only current and departing encounters need hud animation history
            if (hearts.size() > 24) hearts.keySet().removeIf(k -> k.startsWith("enemy:") && !k.startsWith("enemy:" + state.stage + ":") && !k.startsWith("enemy:" + defeatedStage + ":"));
        }
        if (Math.abs(status.target - fraction) > .000001) {
            status.from = status.value(animation); status.target = fraction; status.changed = animation;
        }
        double shown = reducedMotion ? fraction : status.value(animation);
        int size = Math.min((int) (9 * scale), width / HEART_COUNT - 2), gap = Math.max(2, (int) scale);
        BufferedImage full = UiSkin.asset("UI Icons 3/icon1.png"), empty = UiSkin.asset("UI Icons 3/icon2.png");
        for (int i = 0; i < HEART_COUNT; i++) {
            double fill = heartFill(shown, i);
            boolean changed = heartFill(status.from, i) != heartFill(status.target, i) && animation - status.changed < 18;
            boolean healing = status.target > status.from;
            int hx = x + i * (size + gap), hy = y;
            if (changed && !reducedMotion) hy += healing ? ((animation - status.changed) / 3 % 2 == 0 ? -2 : 0) : (animation - status.changed) / 3 % 2 == 0 ? 1 : -1;
            Graphics2D heart = (Graphics2D) g.create();
            heart.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .28f));
            heart.drawImage(empty, hx, hy, size, size, null);
            heart.setComposite(AlphaComposite.SrcOver);
            heart.clipRect(hx, hy, (int) Math.ceil(size * fill), size);
            heart.drawImage(full, hx, hy, size, size, null); heart.dispose();
            if (changed && !healing && !reducedMotion && (animation - status.changed) / 3 % 2 == 0) {
                Graphics2D flash = (Graphics2D) g.create();
                flash.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .65f));
                flash.drawImage(empty, hx, hy, size, size, null); flash.dispose();
            }
            if (changed && !reducedMotion) {
                g.setColor(new Color(healing ? 0x86efac : 0xffa092));
                if (healing) { g.drawLine(hx + size / 2, hy - 3, hx + size / 2, hy + 1); g.drawLine(hx + size / 2 - 2, hy - 1, hx + size / 2 + 2, hy - 1); }
                else g.drawLine(hx + 2, hy + size + 1, hx + size - 2, hy + size + 1);
            }
        }
    }

    // fill a solid rectangle for simple scene backgrounds and progress tracks
    // this small helper does not draw text or asset borders
    private static void box(Graphics2D g, int color, int x, int y, int w, int h) { g.setColor(new Color(color)); g.fillRect(x, y, w, h); }

    // draw a compact framed bar for older callers
    // the current combat hp display uses drawhearts instead
    private static void bar(Graphics2D g, int x, int y, int width, double fraction, int color) {
        box(g, 0x10121a, x - 1, y - 1, width + 2, 5); box(g, color, x, y, (int) (width * Math.max(0, Math.min(1, fraction))), 3);
        UiSkin.draw(g, "field", x - 2, y - 2, width + 4, 7, false);
    }

    // compose the corridor, actors, effects, and hud without advancing combat
    // draw the pixel scene first, then readable interface text at the display size
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = scene.createGraphics(); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        box(g, 0x171725, 0, 0, 384, 216);
        AssetPack.drawCorridor(g, reducedMotion ? 0 : animation, reducedMotion ? 0 : distance);
        BufferedImage floor = AssetPack.image(java.nio.file.Paths.get("assets", "packs", "Cold Corridors Files", "Assets", "Layers", "tileset.png"));
        if (floor != null) for (int y = 160; y < 216; y += 14) for (int x = -(int) ((reducedMotion ? 0 : distance) % 48); x < 384; x += 48)
            g.drawImage(floor, x, y, x + 48, y + 14, 0, 10, 48, 24, null);
        int phase = (int) Math.min(100, (animation - tickStarted) / MOTION_SCALE);
        boolean marching = transition >= DEPARTURE_HOLD && transition < TRANSITION_END && state.rest == 0;


        int index = 0;
        for (Hero hero : state.heroes) if (hero.active) {
            int x = 25 + index % 2 * 72 + index / 2 * 12, feet = 145 + index / 2 * 45;
            int localPhase = phase - attackDelay(hero.role);
            boolean attacking = !marching && state.rest == 0 && localPhase >= 0 && localPhase < 18 && state.combatEvents.stream().anyMatch(e -> e.hero == hero);
            String action = hero.hp <= 0 ? "death" : marching ? "walk" : attacking ? (hero.role == Catalog.Role.PRIEST && state.lastHealing > 0 ? "heal" : state.ticks % 5 == 0 ? "attack2" : "attack") : phase >= 18 && phase < 30 && wounded.contains(hero) ? "hurt" : "idle";
            AssetPack.Actor actor = AssetPack.actor("characters", hero.role.sprite());
            int frames = actor == null ? 6 : actor.frames(action);
            int frame = hero.hp <= 0 ? (int) ((animation - fallenAt.getOrDefault(hero, animation)) / (4 * MOTION_SCALE)) : marching ? (int) (distance * frames / 32) : action.equals("idle") ? (int) (animation / 4) : attacking ? localPhase * frames / 18 : Math.max(0, phase - 18) * frames / 11;
            Composite old = g.getComposite(); if (hero.hp <= 0) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .45f));
            int drawX = x, drawFeet = feet;
            if (!reducedMotion && attacking && hero.role != Catalog.Role.WIZARD && hero.role != Catalog.Role.PRIEST && hero.role != Catalog.Role.ARCHER) {
                GameState.CombatEvent strike = state.combatEvents.stream().filter(e -> e.hero == hero && !e.healing && !e.incoming).findFirst().orElse(null);
                if (strike != null) {
                    Point target = enemyPoint(strike.enemy, strike.stage);
                    double lunge = lungeProgress((animation - tickStarted) / MOTION_SCALE - attackDelay(hero.role), 18);
                    drawX += (int) ((target.x - 48 - x) * lunge);
                    drawFeet += (int) ((target.y + 20 - feet) * lunge);
                }
            }
            if (hero.hp <= 0 && heroDeathPositions.get(hero) != null) {
                Point deathPosition = heroDeathPositions.get(hero); drawX = deathPosition.x - 16; drawFeet = deathPosition.y + 20;
            }
            heroDrawPositions.put(hero, new Point(drawX + 16, drawFeet - 20));
            shadow(g, drawX + 16, drawFeet, 28, false);
            PixelArt.drawActor(g, "characters", hero.role.sprite(), hero.role, reducedMotion ? "idle" : action, reducedMotion ? 0 : frame, drawX, drawFeet - 40, 32, 40);
            g.setComposite(old); index++;
        }
        boolean dying = transition < DEPARTURE_HOLD && defeatedStage > 0;
        int stage = dying ? defeatedStage : state.stage;
        String[] enemies = Encounters.members(stage);
        boolean boss = Encounters.boss(stage);
        for (int i = 0; i < enemies.length; i++) {
            int x = boss ? 270 : 228 + i % 2 * 66 + i / 2 * 8, feet = boss ? 180 : 145 + i / 2 * 45;
            if (marching && !reducedMotion) x += entranceOffset((transition - DEPARTURE_HOLD) / MOTION_SCALE);
            String name = enemies[i], id = Catalog.enemySprite(name);
            int height = Encounters.height(name, boss);
            int deathFrame = enemyDeathFrame(i, dying);
            boolean dead = deathFrame >= 0;
            int enemyPhase = phase - enemyDelay(name, i);
            final int enemyIndex = i;
            GameState.CombatEvent strike = state.combatEvents.stream().filter(e -> e.incoming && e.stage == stage && e.enemy == enemyIndex).findFirst().orElse(null);
            boolean attacking = strike != null && !marching && state.rest == 0 && enemyPhase >= 0 && enemyPhase < 16;
            boolean hitNow = state.combatEvents.stream().anyMatch(e -> !e.incoming && !e.healing && e.stage == stage && e.enemy == enemyIndex && phase >= attackDelay(e.hero.role) + 8 && phase < attackDelay(e.hero.role) + 18);
            int hitStart = state.combatEvents.stream().filter(e -> !e.incoming && !e.healing && e.stage == stage && e.enemy == enemyIndex && phase >= attackDelay(e.hero.role) + 8)
                .mapToInt(e -> attackDelay(e.hero.role) + 8).max().orElse(phase);
            String action = dead ? "death" : marching ? "walk" : state.rest > 0 ? "idle" : attacking ? "attack" : hitNow ? "hurt" : "idle";
            AssetPack.Actor actor = AssetPack.actor("enemies", id); int frames = actor == null ? 6 : actor.frames(action);
            int frame = dead ? deathFrame : action.equals("walk") || action.equals("idle") ? (int) (animation / 4) : action.equals("hurt") ? Math.min(frames - 1, Math.max(0, phase - hitStart)) : Math.max(0, enemyPhase) * frames / 16;

            // their approach includes floor motion plus their own steps toward the party
            if (marching && !dead) frame = (280 - entranceOffset((transition - DEPARTURE_HOLD) / MOTION_SCALE) - travelDistance((transition - DEPARTURE_HOLD) / MOTION_SCALE)) * frames / 32;
            int width = boss ? 60 : 32;
            Composite old = g.getComposite(); if (dead) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .45f));
            int lift = id.contains("bat") || id.contains("ghostfire") || id.contains("eyeball") ? 14 : 0;
            if (!reducedMotion && attacking && !dead && enemyStyle(name).equals("melee")) {
                Point target = heroPosition(strike.ally, (animation - tickStarted) / MOTION_SCALE);
                if (target != null) {
                    double lunge = lungeProgress((animation - tickStarted) / MOTION_SCALE - enemyDelay(name, i), 16);
                    x += (int) ((target.x + 20 - x) * lunge);
                    feet += (int) ((target.y + height / 2 + lift - feet) * lunge);
                }
            }
            shadow(g, x + width / 2, feet, Math.max(18, height * 3 / 4), lift > 0);
            PixelArt.drawActor(g, "enemies", id, null, reducedMotion && !dead ? "idle" : action, reducedMotion ? dead ? frames - 1 : 0 : frame, x, feet - height - lift, width, height);
            g.setComposite(old);

        }
        drawEffects(g, phase, stage);
        g.dispose();
        double scale = Math.min(getWidth() / 384.0, getHeight() / 216.0);
        int width = (int) (384 * scale), height = (int) (216 * scale), ox = (getWidth() - width) / 2, oy = (getHeight() - height) / 2;
        Graphics2D display = (Graphics2D) graphics.create(); display.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        display.drawImage(scene, ox, oy, width, height, null);
        display.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        display.setFont(UiSkin.font(Math.max(18, (int) (7 * scale)))); display.setColor(new Color(0xf1dcae));
        drawHud(display, ox, oy, scale, stage, dying);
        if (state.rest > 0) drawNotice(display, ox, oy, width, scale);
        display.dispose();
    }

    // draw the regrouping card with an hourglass and countdown progress
    // this appears during recovery while the next attempt waits
    private void drawNotice(Graphics2D g, int ox, int oy, int sceneWidth, double scale) {
        int w = Math.min(sceneWidth - 24, (int) (210 * scale)), h = (int) (39 * scale);
        int x = ox + (sceneWidth - w) / 2, y = oy + (int) (54 * scale);
        UiSkin.draw(g, "panel", x, y, w, h, true);
        int icon = (int) (21 * scale), pad = (int) (9 * scale);
        g.drawImage(UiSkin.asset("UI Icons 3/icon59.png"), x + pad, y + pad, icon, icon, null);
        int tx = x + pad + icon + (int) (7 * scale);
        g.setFont(UiSkin.font(Math.max(18, (int) (7 * scale)))); g.setColor(new Color(0xf1dcae));
        g.drawString("REGROUPING", tx, y + (int) (14 * scale));
        g.setFont(UiSkin.font(Math.max(18, (int) (6 * scale)))); g.setColor(new Color(0xe3dfd5));
        g.drawString("Retry in " + state.rest + "s / Stage " + state.stage, tx, y + (int) (25 * scale));
        int track = w - (tx - x) - pad, by = y + (int) (30 * scale);
        box(g, 0x27212b, tx, by, track, Math.max(3, (int) (2 * scale)));
        box(g, 0xd7ba7d, tx, by, (int) (track * (5 - state.rest) / 5.0), Math.max(3, (int) (2 * scale)));
    }
}
