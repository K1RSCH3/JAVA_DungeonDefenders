package idle;

import java.util.*;
import idle.Catalog.*;

// own the playable campaign data and rules independently of swing
// tests can run combat and purchases without opening a window, and the ui displays these results
public final class GameState {
    public static final int MAX_STAGE = 10000, MAX_LEVEL = 100;
    public static final double MAX_GOLD = 1e12;

    // each upgrade has its own rank and price, so improving one aspect does not buy the others
    public enum Upgrade { ATTACK, HEALTH, HEALING, GUARD }
    public static final class Hero {
        public final Role role;
        public int level;
        public boolean active;
        public double hp;
        public final EnumMap<Upgrade, Integer> upgrades = new EnumMap<>(Upgrade.class);

        // read the purchased rank for one upgrade track
        // missing entries count as zero when a hero has never bought that upgrade
        public int rank(Upgrade upgrade) { return upgrades.getOrDefault(upgrade, 0); }
        public final EnumMap<Slot, Item> gear = new EnumMap<>(Slot.class);

        // create one roster entry for a class
        // recruitment, equipment, and starting health are assigned by the surrounding game setup
        Hero(Role role) { this.role = role; }

        // read the strength tier of an equipped item
        // empty slots contribute zero to the stat formulas
        public int tier(Slot slot) { return gear.containsKey(slot) ? gear.get(slot).tier : 0; }

        // calculate this hero's current health limit from old levels, vitality upgrades, and gear
        // use this after equipment changes and when drawing hearts
        public double maxHp() { return 75 + level * 12 + rank(Upgrade.HEALTH) * 18 + tier(Slot.HAT) * 12 + tier(Slot.ARMOR) * 30 + tier(Slot.SHOES) * 8; }

        // calculate base attack power before round bonuses and the priest's damage reduction
        // attack upgrades change this value without improving healing
        public double power() { return (4 + level * 3 + rank(Upgrade.ATTACK) * 3 + tier(Slot.WEAPON) * 5 + tier(Slot.HAT)) * (1 + tier(Slot.SHOES) * .05); }

        // calculate a priest's healing amount before limiting it to the ally's missing hp
        // healing has its own upgrades so buying spell damage does not secretly boost healing
        public double healingPower() { return (4 + level * 3 + tier(Slot.WEAPON) * 5 + tier(Slot.HAT)) * (1 + tier(Slot.SHOES) * .05) * 1.3 + rank(Upgrade.HEALING) * 4; }

        // calculate protection from armor, guard upgrades, and front-line class bonuses
        // combat shares this protection across normal enemy attacks
        public double guard() { return rank(Upgrade.GUARD) + tier(Slot.ARMOR) * 2 + (role == Role.KNIGHT || role == Role.KNIGHT_TEMPLAR ? level * .8 : 0); }

        // check whether a hero can participate in the active party
        // a recruited hero at camp does not attack or receive combat healing
        public boolean alive() { return active && hp > 0; }
    }

    // keep every class in stable enum order, including locked classes and heroes waiting at camp
    // inventory counts are shared by the roster rather than attached to one hero
    public final List<Hero> heroes = new ArrayList<>();
    public final Map<String, Integer> inventory = new LinkedHashMap<>();
    public double gold = 120, enemyHp;
    public int stage = 1, kills = 0, ticks = 0, rest = 0;
    public long savedAt = System.currentTimeMillis();
    public String message = "The defenders march. Hold the line against the dark.";
    public double lastDamage = 0, lastHealing = 0;

    /** transient events identify the actual target, including damage carried to the next enemy */
    public static final class CombatEvent {
        public final Hero hero, ally;
        public final int enemy, stage;
        public final double amount;
        public final boolean healing, incoming;

        // record who acted, who was hit or healed, and how much hp changed
        // the battlefield uses these facts to place effects on the correct target
        CombatEvent(Hero hero, Hero ally, int enemy, int stage, double amount, boolean healing, boolean incoming) {
            this.hero = hero; this.ally = ally; this.enemy = enemy; this.stage = stage;
            this.amount = amount; this.healing = healing; this.incoming = incoming;
        }
    }
    public final List<CombatEvent> combatEvents = new ArrayList<>();

    // create a new expedition with all recruitable classes in the roster
    // only the starting four heroes begin recruited and active
    // equipment is bought in the shop, so new heroes start with empty gear slots
    public GameState() {
        for (Role role : Role.values()) {
            Hero h = new Hero(role);
            if (role == Role.KNIGHT || role == Role.WIZARD || role == Role.PRIEST || role == Role.ARCHER) {
                h.level = 1; h.active = true;
            }
            h.hp = h.maxHp(); heroes.add(h);
        }
        enemyHp = enemyMaxHp();
    }

    // check whether the current stage is a boss encounter
    // reward and combat rules use the same stage schedule
    public boolean boss() { return Encounters.boss(stage); }

    // return the first enemy's name for callers that need a single encounter representative
    // use enemies when showing the full patrol
    public String enemyName() { return Encounters.members(stage)[0]; }

    // rebuild the current stage's deterministic enemy lineup
    // loading a save does not need to store each enemy name separately
    public String[] enemies() { return Encounters.members(stage); }

    // return one target for a boss or four targets for a patrol
    // health allocation and attack loops use this count
    public int enemyCount() { return boss() ? 1 : 4; }

    // split the encounter health budget evenly across its members
    // a boss receives the entire budget
    public double memberMaxHp() { return enemyMaxHp() / enemyCount(); }

    /** the party focuses targets in order. remaining hp uniquely identifies each member's hp */
    // derive one enemy's hp from the remaining encounter total
    // earlier targets lose hp first, so this also reconstructs partially defeated patrols after loading
    public double memberHp(int index) { return Math.max(0, Math.min(memberMaxHp(), enemyHp - memberMaxHp() * (enemyCount() - 1 - index))); }

    // count targets that still have hp
    // dead patrol members must not keep attacking
    public int livingEnemies() { int n = 0; for (int i = 0; i < enemyCount(); i++) if (memberHp(i) > 0) n++; return n; }

    // choose a region label from the current stage
    // this is a display helper rather than a change to the combat rules
    public String region() { return stage < 12 ? "THE HOLLOW MARCH" : stage < 28 ? "GRAVES OF THE FORSAKEN" : "THE EMBER ABYSS"; }

    // calculate the health budget for any stage, including the boss multiplier
    // rendering can use an old stage while its final enemies are still dying
    public static double encounterMaxHp(int stage) { return (45 + stage * 20.0) * (Encounters.boss(stage) ? 4 : 1); }

    // get the current encounter's full health budget
    // retry and new-stage setup use this to reset enemies
    public double enemyMaxHp() { return encounterMaxHp(stage); }

    /** base damage per living enemy. boss heavy strikes are applied separately */
    // calculate one living enemy's base strike before guard and boss heavy attacks
    // later stages deal more damage
    public double enemyStrikeDamage() { return (5 + stage * 1.7) * (boss() ? 1.5 : .25); }

    // calculate gold for clearing this stage
    // boss victories receive a larger payout than ordinary patrols
    public double reward() { return (14 + stage * 3.0) * (boss() ? 5 : 1); }

    // count occupied party slots, including knocked-out heroes
    // recruitment does not automatically make room in a full party
    public int activeCount() { return (int) heroes.stream().filter(h -> h.active).count(); }

    // estimate the active party's base output for offline gold
    // this is a rough estimate, not a replay of live attack timing or survival
    public double dps() { return heroes.stream().filter(h -> h.active).mapToDouble(h -> h.power() * (h.role == Role.PRIEST ? .4 : 1)).sum(); }

    // price recruitment for a locked class or a legacy all-stat level increase
    // the current interface uses separate upgrade tracks once a hero is recruited
    public double levelCost(Hero h) { return h.level == 0 ? 140 + h.role.ordinal() * 20 : Math.ceil(30 * Math.pow(h.level, 1.55)); }

    // recruit a hero or apply the older all-stat training operation
    // the interface uses this for recruitment, while existing levels keep their original bonuses
    public boolean train(Hero h) {
        if (h.level >= MAX_LEVEL || gold < levelCost(h)) return false;
        gold -= levelCost(h); h.level++; h.hp = h.maxHp();
        message = h.role.label + (h.level == 1 ? " has joined your roster." : " has grown stronger."); return true;
    }

    // price the next rank of one upgrade track
    // buying health does not increase the price of a separate attack upgrade
    public double upgradeCost(Hero h, Upgrade upgrade) { return Math.ceil(25 * Math.pow(h.rank(upgrade) + 1, 1.45)); }

    // buy one supported upgrade when the hero is recruited and enough gold is available
    // vitality adds only the extra hp to a living hero instead of giving a free full heal
    public boolean upgrade(Hero h, Upgrade upgrade) {
        if (h.level == 0 || h.rank(upgrade) >= MAX_LEVEL || (upgrade == Upgrade.HEALING && h.role != Role.PRIEST) || gold < upgradeCost(h, upgrade)) return false;
        gold -= upgradeCost(h, upgrade);
        double oldMax = h.maxHp();
        h.upgrades.put(upgrade, h.rank(upgrade) + 1);
        if (upgrade == Upgrade.HEALTH && h.hp > 0) h.hp += h.maxHp() - oldMax;
        message = h.role.label + " improved " + upgrade.name().toLowerCase(Locale.ROOT) + " to rank " + h.rank(upgrade) + ".";
        return true;
    }

    // move a recruited hero between camp and the active party
    // reject changes that exceed four slots or remove the last available fighter
    public boolean toggle(Hero h) {
        if (h.level == 0 || (!h.active && activeCount() >= 4) || (h.active && activeCount() == 1)) return false;
        if (h.alive() && rest == 0 && heroes.stream().noneMatch(other -> other != h && other.alive())) return false;
        h.active = !h.active;
        message = h.role.label + (h.active ? " joins the expedition." : " returns to camp."); return true;
    }

    // buy an item into shared inventory
    // this does not equip it, and full stacks or insufficient gold leave the state unchanged
    public boolean buy(Item item) {
        if (gold < item.price || inventory.getOrDefault(item.id, 0) >= 999) return false;
        gold -= item.price; inventory.merge(item.id, 1, Integer::sum); message = "Bought " + item.name + ". Select a hero to equip it."; return true;
    }

    // move an owned, compatible item into a hero's slot
    // the old item returns to inventory and current hp is capped if maximum hp falls
    public boolean equip(Hero h, Item item) {
        int owned = inventory.getOrDefault(item.id, 0);
        if (h.level == 0 || owned == 0 || !h.role.accepts(item)) return false;
        inventory.put(item.id, owned - 1);
        Item old = h.gear.put(item.slot, item);
        if (old != null) inventory.merge(old.id, 1, Integer::sum);
        h.hp = Math.min(h.hp, h.maxHp()); message = h.role.label + " equipped " + item.name + "."; return true;
    }

    // return a slot's item to shared inventory
    // removing health gear may lower the hero's current hp to the new limit
    public boolean unequip(Hero h, Slot slot) {
        Item old = h.gear.remove(slot);
        if (old == null) return false;
        inventory.merge(old.id, 1, Integer::sum); h.hp = Math.min(h.hp, h.maxHp()); return true;
    }

    // add a reward without exceeding the gold cap
    // combat and offline rewards share this limit
    public void addGold(double amount) { gold = Math.min(MAX_GOLD, gold + amount); }

    // resolve one combat round or one regrouping countdown step
    // healing and damage create visual events, victories advance the stage, and failed attempts recover on the same stage
    public void tick() {
        ticks = (ticks + 1) % 1000000; lastDamage = 0; lastHealing = 0; combatEvents.clear();
        if (rest > 0) {
            if (--rest == 0) {
                heroes.forEach(h -> h.hp = h.maxHp()); enemyHp = enemyMaxHp(); message = "Regrouped. Retrying stage " + stage + ". Train or equip your party to push further.";
            }
            return;
        }

        // resolve support and outgoing attacks first
        // damage spills into the next target, and each applied part gets its own visual event
        for (Hero h : heroes) if (h.alive()) {
            if (h.role == Role.PRIEST) {
                Hero target = heroes.stream().filter(Hero::alive).min(Comparator.comparingDouble(a -> a.hp / a.maxHp())).orElse(h);
                double healing = Math.min(target.maxHp() - target.hp, h.healingPower());
                target.hp += healing; lastHealing += healing;
                if (healing > 0) combatEvents.add(new CombatEvent(h, target, -1, stage, healing, true, false));
            }
            double damage = h.power() * (h.role == Role.PRIEST ? .4 : 1) * (ticks % 5 == 0 ? 1.8 : 1);
            lastDamage += damage;
            for (int i = 0; i < enemyCount() && damage > 0; i++) {
                double applied = Math.min(memberHp(i), damage);
                if (applied <= 0) continue;
                combatEvents.add(new CombatEvent(h, null, i, stage, applied, false, false));
                enemyHp -= applied; damage -= applied;
            }
        }

        // a clear pays once and prepares the next stage immediately in the game state
        // battlepanel retains the departing stage long enough to finish its death animations
        if (enemyHp <= 0) {
            String defeated = Encounters.title(stage); addGold(reward()); kills++;
            message = defeated + " defeated. +" + (int) reward() + " gold.";
            // victories award gold only, leaving all new equipment purchases to the shop
            stage = Math.min(MAX_STAGE, stage + 1); enemyHp = enemyMaxHp();
            heroes.stream().filter(Hero::alive).forEach(h -> h.hp = Math.min(h.maxHp(), h.hp + h.maxHp() * .12));
            return;
        }

        // front-line roles absorb attacks before ranged/support classes
        for (int enemy = 0; enemy < enemyCount(); enemy++) if (memberHp(enemy) > 0) {
        Hero target = heroes.stream().filter(Hero::alive).min(Comparator.comparingInt(h ->
            h.role == Role.KNIGHT || h.role == Role.KNIGHT_TEMPLAR ? 0 : h.role == Role.PRIEST ? 3 : h.role == Role.WIZARD || h.role == Role.ARCHER ? 2 : 1)).orElse(null);
        double strike = enemyStrikeDamage();
        if (boss() && ticks % 5 == 0) { strike *= 1.7; message = Encounters.title(stage) + " unleashes a crushing blow!"; }
        if (target != null) {
            double applied = Math.min(target.hp, Math.max(.25, strike - target.guard() / (boss() ? 1 : 4)));
            target.hp -= applied;
            combatEvents.add(new CombatEvent(null, target, enemy, stage, applied, false, true));
        }
        }

        // failed attempts cannot gradually chip through a stronger encounter
        // restore both sides, preserve the stage and purchases, and let the player regroup
        if (heroes.stream().noneMatch(Hero::alive)) {
            rest = 5; enemyHp = enemyMaxHp();
            heroes.forEach(h -> h.hp = h.maxHp());
            combatEvents.clear(); lastDamage = 0; lastHealing = 0;
            message = "Progress stalled at stage " + stage + ". Party rescued; regrouping for 5 seconds. Train or equip better gear to advance.";
        }
    }

    // grant estimated camp gold for elapsed real time, capped at eight hours
    // update the checkpoint immediately so calling this again cannot claim the same time twice
    public double offlineReward(long now) {
        long seconds = Math.min(8 * 3600, Math.max(0, (now - savedAt) / 1000));
        double before = gold;
        addGold(seconds * Math.min(1, dps() / enemyMaxHp()) * reward() * .35);
        savedAt = now; return gold - before;
    }
}
