package idle;

import java.util.*;
import idle.Catalog.*;

public final class GameState {
    public static final int MAX_STAGE = 10000, MAX_LEVEL = 100;
    public static final double MAX_GOLD = 1e12;
    public static final class Hero {
        public final Role role;
        public int level;
        public boolean active;
        public double hp;
        public final EnumMap<Slot, Item> gear = new EnumMap<>(Slot.class);
        Hero(Role role) { this.role = role; }
        public int tier(Slot slot) { return gear.containsKey(slot) ? gear.get(slot).tier : 0; }
        public double maxHp() { return 75 + level * 12 + tier(Slot.HAT) * 12 + tier(Slot.ARMOR) * 30 + tier(Slot.SHOES) * 8; }
        public double power() { return (4 + level * 3 + tier(Slot.WEAPON) * 5 + tier(Slot.HAT)) * (1 + tier(Slot.SHOES) * .05); }
        public double guard() { return tier(Slot.ARMOR) * 2 + (role == Role.KNIGHT || role == Role.KNIGHT_TEMPLAR ? level * .8 : 0); }
        public boolean alive() { return active && hp > 0; }
    }
    public final List<Hero> heroes = new ArrayList<>();
    public final Map<String, Integer> inventory = new LinkedHashMap<>();
    public double gold = 120, enemyHp;
    public int stage = 1, kills = 0, ticks = 0, rest = 0;
    public long savedAt = System.currentTimeMillis();
    public String message = "The defenders march. Hold the line against the dark.";
    public double lastDamage = 0, lastHealing = 0;
    public GameState() {
        for (Role role : Role.values()) {
            Hero h = new Hero(role);
            if (role == Role.KNIGHT || role == Role.WIZARD || role == Role.PRIEST || role == Role.ARCHER) {
                h.level = 1; h.active = true;
                h.gear.put(Slot.WEAPON, Catalog.item(role.weapons.iterator().next().name().toLowerCase(Locale.ROOT) + "_1"));
            }
            h.hp = h.maxHp(); heroes.add(h);
        }
        enemyHp = enemyMaxHp();
    }
    public boolean boss() { return stage % 10 == 0; }
    public String enemyName() { return Catalog.ENEMIES[(stage - 1) % Catalog.ENEMIES.length]; }
    public String region() { return stage < 12 ? "THE HOLLOW MARCH" : stage < 28 ? "GRAVES OF THE FORSAKEN" : "THE EMBER ABYSS"; }
    public double enemyMaxHp() { return (45 + stage * 20.0) * (boss() ? 2.5 : 1); }
    public double reward() { return (14 + stage * 3.0) * (boss() ? 3 : 1); }
    public int activeCount() { return (int) heroes.stream().filter(h -> h.active).count(); }
    public double dps() { return heroes.stream().filter(h -> h.active).mapToDouble(h -> h.power() * (h.role == Role.PRIEST ? .4 : 1)).sum(); }
    public double levelCost(Hero h) { return h.level == 0 ? 140 + h.role.ordinal() * 20 : Math.ceil(30 * Math.pow(h.level, 1.55)); }
    public boolean train(Hero h) {
        if (h.level >= MAX_LEVEL || gold < levelCost(h)) return false;
        gold -= levelCost(h); h.level++; h.hp = h.maxHp();
        message = h.role.label + (h.level == 1 ? " has joined your roster." : " has grown stronger."); return true;
    }
    public boolean toggle(Hero h) {
        if (h.level == 0 || (!h.active && activeCount() >= 4) || (h.active && activeCount() == 1)) return false;
        if (h.alive() && rest == 0 && heroes.stream().noneMatch(other -> other != h && other.alive())) return false;
        h.active = !h.active;
        message = h.role.label + (h.active ? " joins the expedition." : " returns to camp."); return true;
    }
    public boolean buy(Item item) {
        if (gold < item.price || inventory.getOrDefault(item.id, 0) >= 999) return false;
        gold -= item.price; inventory.merge(item.id, 1, Integer::sum); message = "Forged " + item.name + ". Select a hero to equip it."; return true;
    }
    public boolean equip(Hero h, Item item) {
        int owned = inventory.getOrDefault(item.id, 0);
        if (h.level == 0 || owned == 0 || !h.role.accepts(item)) return false;
        inventory.put(item.id, owned - 1);
        Item old = h.gear.put(item.slot, item);
        if (old != null) inventory.merge(old.id, 1, Integer::sum);
        h.hp = Math.min(h.hp, h.maxHp()); message = h.role.label + " equipped " + item.name + "."; return true;
    }
    public boolean unequip(Hero h, Slot slot) {
        Item old = h.gear.remove(slot);
        if (old == null) return false;
        inventory.merge(old.id, 1, Integer::sum); h.hp = Math.min(h.hp, h.maxHp()); return true;
    }
    public void addGold(double amount) { gold = Math.min(MAX_GOLD, gold + amount); }
    public void tick() {
        ticks = (ticks + 1) % 1000000; lastDamage = 0; lastHealing = 0;
        if (rest > 0) {
            if (--rest == 0) {
                heroes.forEach(h -> h.hp = h.maxHp()); enemyHp = enemyMaxHp(); message = "Rested at the campfire. The expedition resumes.";
            }
            return;
        }
        for (Hero h : heroes) if (h.alive()) {
            if (h.role == Role.PRIEST) {
                Hero target = heroes.stream().filter(Hero::alive).min(Comparator.comparingDouble(a -> a.hp / a.maxHp())).orElse(h);
                double healing = Math.min(target.maxHp() - target.hp, h.power() * 1.3);
                target.hp += healing; lastHealing += healing;
            }
            lastDamage += h.power() * (h.role == Role.PRIEST ? .4 : 1) * (ticks % 5 == 0 ? 1.8 : 1);
        }
        enemyHp -= lastDamage;
        if (enemyHp <= 0) {
            String defeated = enemyName(); addGold(reward()); kills++;
            message = defeated + " defeated. +" + (int) reward() + " gold.";
            if (kills % 3 == 0) {
                int tier = Math.min(3, 1 + stage / 20);
                Item drop = Catalog.ITEMS.get((tier - 1) * 9 + (kills / 3 - 1) % 9);
                inventory.merge(drop.id, 1, (a, b) -> Math.min(999, a + b)); message += " Found " + drop.name + "!";
            }
            stage = Math.min(MAX_STAGE, stage + 1); enemyHp = enemyMaxHp();
            heroes.stream().filter(Hero::alive).forEach(h -> h.hp = Math.min(h.maxHp(), h.hp + h.maxHp() * .12));
            return;
        }
        // Front-line roles absorb attacks before ranged/support classes.
        Hero target = heroes.stream().filter(Hero::alive).min(Comparator.comparingInt(h ->
            h.role == Role.KNIGHT || h.role == Role.KNIGHT_TEMPLAR ? 0 : h.role == Role.PRIEST ? 3 : h.role == Role.WIZARD || h.role == Role.ARCHER ? 2 : 1)).orElse(null);
        if (target != null) target.hp = Math.max(0, target.hp - Math.max(1, (5 + stage * 1.7) * (boss() ? 1.5 : 1) - target.guard()));
        if (heroes.stream().noneMatch(Hero::alive)) {
            rest = 5; stage = Math.max(1, stage - 1); enemyHp = enemyMaxHp();
            message = "The party fell. Resting 5 seconds, then returning one stage earlier.";
        }
    }
    public double offlineReward(long now) {
        long seconds = Math.min(8 * 3600, Math.max(0, (now - savedAt) / 1000));
        double before = gold;
        addGold(seconds * Math.min(1, dps() / enemyMaxHp()) * reward() * .35);
        savedAt = now; return gold - before;
    }
}
