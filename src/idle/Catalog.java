package idle;

import java.util.*;

/** stable ids here are also used by save files and optional sprite filenames */
public final class Catalog {
    // prevent construction because this class holds shared definitions
    private Catalog() {}
    public enum Slot { WEAPON, HAT, ARMOR, SHOES }
    public enum Weapon { SWORD, SPEAR, AXE, BOW, STAFF, WAND }
    public enum Role {
        LANCER("Lancer", 0xb4b8bd, Weapon.SPEAR),
        WIZARD("Wizard", 0x9682c4, Weapon.STAFF, Weapon.WAND),
        PRIEST("Priest", 0xe0cf99, Weapon.STAFF, Weapon.WAND),
        ARCHER("Archer", 0x88a87b, Weapon.BOW),
        SOLDIER("Soldier", 0xb0a291, Weapon.SPEAR),
        KNIGHT("Knight", 0xa6b9cf, Weapon.SWORD),
        KNIGHT_TEMPLAR("Knight Templar", 0xd6bc77, Weapon.SWORD),
        SWORDSMAN("Swordsman", 0xb58179, Weapon.SWORD),
        ARMORED_AXEMAN("Armored Axeman", 0x8f9b9c, Weapon.AXE);
        public final String label;
        public final int color;
        public final Set<Weapon> weapons;

        // define a recruitable class, its display color, and allowed weapon types
        // store the weapon set as read-only so equipment rules cannot change accidentally
        Role(String label, int color, Weapon... weapons) {
            this.label = label; this.color = color;
            this.weapons = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(weapons)));
        }

        // check whether a hero class can equip an item
        // armor slots are universal, while weapons must match the class's allowed set
        public boolean accepts(Item item) { return item.slot != Slot.WEAPON || weapons.contains(item.weapon); }

        // convert the class name into the stable asset identifier
        // this is used for pack lookup and optional custom png overrides
        public String sprite() { return name().toLowerCase(Locale.ROOT); }
    }
    public static final class Item {
        public final String id, name;
        public final Slot slot;
        public final Weapon weapon;
        public final int tier, price;

        // create a gear definition with a stable id, tier, slot, and price
        // items are shared definitions rather than individual inventory copies
        Item(String id, String name, Slot slot, Weapon weapon, int tier) {
            this.id = id; this.name = name; this.slot = slot; this.weapon = weapon; this.tier = tier;
            this.price = tier == 1 ? 35 : tier == 2 ? 240 : 1200;
        }

        // describe the item's stat contribution for inventory and shop labels
        // the actual combat formulas live in gamestate.hero
        public String bonus() {
            switch (slot) {
                case WEAPON: return "+" + tier * 5 + " power";
                case HAT: return "+" + tier * 12 + " HP, +" + tier + " power";
                case ARMOR: return "+" + tier * 30 + " HP, +" + tier * 2 + " guard";
                default: return "+" + tier * 8 + " HP, +" + tier * 5 + "% speed";
            }
        }

        // provide a readable item label for swing controls that use the object's default text
        @Override public String toString() { return name + "  /  " + bonus(); }
    }
    public static final List<Item> ITEMS;
    static {
        List<Item> items = new ArrayList<>();
        String[] materials = {"", "Iron", "Runebound", "Eclipse"};
        for (int tier = 1; tier <= 3; tier++) {
            for (Weapon weapon : Weapon.values()) {
                String type = weapon.name().toLowerCase(Locale.ROOT);
                items.add(new Item(type + "_" + tier, materials[tier] + " " + type, Slot.WEAPON, weapon, tier));
            }
            items.add(new Item("hat_" + tier, materials[tier] + " hood", Slot.HAT, null, tier));
            items.add(new Item("armor_" + tier, materials[tier] + " armor", Slot.ARMOR, null, tier));
            items.add(new Item("shoes_" + tier, materials[tier] + " boots", Slot.SHOES, null, tier));
        }
        ITEMS = Collections.unmodifiableList(items);
    }

    // find a known item by its saved id
    // reject unknown ids so damaged saves cannot create undefined equipment
    public static Item item(String id) {
        return ITEMS.stream().filter(i -> i.id.equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown item: " + id));
    }
    public static final String[] ENEMIES = {
        "Skeleton", "Orc", "Slime", "Bat", "Armored Skeleton", "Skeleton Archer",
        "Werewolf", "Armored Orc", "Greatsword Skeleton", "Werebear", "Orc rider", "Elite Orc",
        "Necromancer", "Demon_A", "Demon_B", "Demon_C", "Demon_D", "Demon_E",
        "Demoness_A", "Demoness_B", "Hellhound", "Blood-Monster_A", "Blood-Monster_B",
        "Eyeball Monster", "Lava Slime", "Hellbat", "Ghostfire", "Minotaur",
        "Black-Knight_A", "Black-Knight_B", "Black-Knight_C", "Flame-Golem", "Warlock"
    };

    // normalize an enemy display name into an asset identifier
    // spaces and hyphens become underscores so both naming styles resolve consistently
    public static String enemySprite(String name) { return name.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_'); }
}
