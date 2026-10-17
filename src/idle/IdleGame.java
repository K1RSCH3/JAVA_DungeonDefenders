package idle;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import javax.swing.*;
import javax.swing.border.*;
import idle.Catalog.*;
import idle.GameState.Hero;

// build and refresh the active expedition screen
// gamestate handles rules, battlepanel handles action visuals, and this class connects player controls
public final class IdleGame {
    private static final Color BG = new Color(0x12141d), PANEL = new Color(0x1c1f2b), LINE = new Color(0x363a48), GOLD = new Color(0xd7ba7d), TEXT = new Color(0xe3dfd5), MUTED = new Color(0xa5a7b3);
    private final GameState state;
    private final SaveStore saves;
    private final JPanel root = new JPanel(new BorderLayout(18, 18)) {
        // paint the screen with readable text smoothing
        // work on a copied graphics context so the caller's settings are preserved
        @Override public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            super.paint(g); g.dispose();
        }
    };
    private final JLabel resources = new JLabel(), stage = new JLabel(), event = new JLabel(), saveStatus = new JLabel("Autosave every 15s");
    private final JButton[] roster = new JButton[9];
    private final JButton[] upgradeButtons = new JButton[GameState.Upgrade.values().length];
    private final JLabel[] upgradeLabels = new JLabel[GameState.Upgrade.values().length];
    private final JLabel heroTitle = new JLabel(), heroStats = new JLabel(), weaponRule = new JLabel();
    private final JLabel partyCount = new JLabel();
    private final JPanel heroActions = panel(new GridLayout(1, 0, 8, 0));
    private final JLabel[] equipped = new JLabel[4];
    private final JComboBox<Item>[] choices;
    private final JButton[] equip = new JButton[4], remove = new JButton[4];
    private final JButton train = button("Train"), deploy = button("Return to camp"), buy = button("Forge");
    private final JList<Item> shop = new JList<>(Catalog.ITEMS.toArray(new Item[0]));
    private final BattlePanel battlefield;
    private Hero selected;
    private JFrame window;
    private String bagSignature = "";
    private int seconds;
    private int combatWait = 10, heartbeat;
    private Timer combat, animation;

    @SuppressWarnings("unchecked")
    // build the gameplay screen around an existing campaign
    // wire purchases, party changes, and navigation without starting timers until show is called
    public IdleGame(GameState state, SaveStore saves) {
        this.state = state; this.saves = saves; selected = state.heroes.get(Role.KNIGHT.ordinal());
        choices = (JComboBox<Item>[]) new JComboBox<?>[4];
        root.setBackground(BG); root.setBorder(new EmptyBorder(20, 22, 16, 22));
        JPanel header = panel(new BorderLayout());
        JPanel brand = panel(new GridLayout(0, 1, 0, 4));
        JLabel title = label("DUNGEON DEFENDERS", GOLD, 32); brand.add(title);
        header.add(brand, BorderLayout.WEST); resources.setForeground(GOLD); resources.setFont(font(15));
        resources.setIcon(UiSkin.icon(3, 4, 28));
        JPanel headerActions = panel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        JButton expand = button("Expand battlefield"); headerActions.add(expand); header.add(headerActions, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);
        JPanel body = panel(new BorderLayout(18, 0));
        JPanel left = panel(new BorderLayout(0, 14));
        JPanel scene = panel(new BorderLayout());
        stage.setForeground(TEXT); stage.setBorder(new EmptyBorder(0, 0, 10, 0)); scene.add(stage, BorderLayout.NORTH);
        battlefield = new BattlePanel(state); scene.add(battlefield, BorderLayout.CENTER);
        JLabel tip = label("AUTO BATTLE / Four-enemy patrols / Boss every 25 stages", MUTED, 11);
        tip.setBorder(new EmptyBorder(10, 0, 0, 0)); scene.add(tip, BorderLayout.SOUTH); left.add(scene, BorderLayout.CENTER);
        JPanel rosterBlock = panel(new BorderLayout(0, 9));
        rosterBlock.setBorder(new EmptyBorder(14, 2, 2, 2));
        JPanel rosterHeading = panel(new GridLayout(2, 1, 0, 5));
        rosterHeading.setBorder(new EmptyBorder(0, 6, 5, 6));
        rosterHeading.add(label("YOUR HEROES", GOLD, 12));
        rosterHeading.add(label("Select a hero, then choose an action", MUTED, 12));
        rosterBlock.add(rosterHeading, BorderLayout.NORTH);
        JPanel cards = panel(new GridLayout(9, 1, 9, 9));
        for (Hero h : state.heroes) {
            JButton card = button(""); roster[h.role.ordinal()] = card; card.setHorizontalAlignment(SwingConstants.LEFT);
            card.setIcon(new ImageIcon(PixelArt.portrait(h.role)));
            card.setBorder(new EmptyBorder(12, 16, 12, 16));
            card.setIconTextGap(9); card.addActionListener(e -> { selected = h; bagSignature = ""; refresh(); }); cards.add(card);
        }
        cards.setBorder(new EmptyBorder(8, 6, 8, 6));
        cards.setPreferredSize(new Dimension(320, 860)); JScrollPane rosterScroll = new JScrollPane(cards); rosterScroll.getViewport().setBackground(BG); rosterScroll.getVerticalScrollBar().setUnitIncrement(24); rosterBlock.add(rosterScroll, BorderLayout.CENTER);
        body.add(left, BorderLayout.CENTER);

        // keep the wallet, selected portrait, and management actions together
        // this avoids sending the player to a separate screen just to compare a price with their gold
        JPanel sidebar = UiSkin.panel(new BorderLayout(0, 16)); sidebar.setPreferredSize(new Dimension(420, 660));
        sidebar.setBorder(new EmptyBorder(24, 24, 24, 24));
        JPanel character = panel(new BorderLayout(0, 14));
        JPanel details = panel(null); details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        heroTitle.setForeground(GOLD); heroTitle.setFont(font(19)); details.add(heroTitle);
        heroStats.setForeground(TEXT); details.add(heroStats); weaponRule.setForeground(MUTED); weaponRule.setFont(font(10)); details.add(weaponRule);
        heroTitle.setBorder(new EmptyBorder(2, 0, 7, 0)); heroStats.setBorder(new EmptyBorder(0, 0, 7, 0));
        JPanel portrait = new JPanel() {
            // draw the selected hero's framed portrait
            // the shared portrait fitter keeps weapons inside the available space
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                UiSkin.draw(g, "panel", 0, 0, getWidth(), getHeight(), true);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                PixelArt.drawPortrait(g, selected.role, 12, 12, getWidth() - 24, getHeight() - 24);
                UiSkin.draw(g, "selection", 0, 0, getWidth(), getHeight(), false); g.dispose();
            }
        };
        portrait.setPreferredSize(new Dimension(100, 90)); portrait.setOpaque(false);
        JPanel identity = panel(new BorderLayout(12, 0)); identity.add(portrait, BorderLayout.WEST); identity.add(details, BorderLayout.CENTER);
        character.add(identity, BorderLayout.CENTER);
        JPanel wallet = UiSkin.panel(new BorderLayout(8, 0)); wallet.setBorder(new EmptyBorder(10, 12, 10, 12));
        resources.setIconTextGap(8); wallet.add(resources, BorderLayout.WEST);
        partyCount.setForeground(TEXT); partyCount.setFont(font(18)); partyCount.setIcon(UiSkin.icon(2, 20, 24)); wallet.add(partyCount, BorderLayout.EAST);
        character.add(wallet, BorderLayout.NORTH);
        heroActions.add(deploy); character.add(heroActions, BorderLayout.SOUTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setUI(new UiSkin.SkinTabs() {
            // hide the built-in tab strip because the larger icon buttons provide navigation
            @Override protected int calculateTabAreaHeight(int placement, int runs, int height) { return 0; }
            // leave the hidden tab strip unpainted
            // the content pages still use jtabbedpane selection and keyboard behavior
            @Override protected void paintTabArea(Graphics g, int placement, int selectedIndex) { }
        });
        train.addActionListener(e -> { if (selected.level == 0) change(() -> state.train(selected)); else tabs.setSelectedIndex(3); });
        deploy.addActionListener(e -> change(() -> state.toggle(selected)));
        sidebar.add(character, BorderLayout.NORTH);
        tabs.addTab("Party", rosterBlock);
        JPanel gear = panel(new GridLayout(4, 1, 0, 12)); gear.setBorder(new EmptyBorder(14, 10, 12, 10));
        for (Slot slot : Slot.values()) {
            int i = slot.ordinal(); JPanel row = panel(new GridLayout(0, 1, 0, 5));
            equipped[i] = label("", GOLD, 11); row.add(equipped[i]); choices[i] = new JComboBox<>(); choices[i].setFont(font(11));
            choices[i].setRenderer(new DefaultListCellRenderer() {
                // build a readable item row for the current inventory or shop context
                // include the item icon and the information needed before equipping or buying
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value == null ? "No gear owned - buy in Shop" : value, index, sel, focus);
                    label.setIcon(value instanceof Item ? UiSkin.itemIcon((Item) value, 18) : null); return label;
                }
            });
            row.add(choices[i]);
            JPanel buttons = panel(new GridLayout(1, 2, 6, 0)); equip[i] = button("Equip"); remove[i] = button("Remove");
            choices[i].addActionListener(e -> equip[i].setEnabled(selected.level > 0 && choices[i].getSelectedItem() != null));
            equip[i].addActionListener(e -> { Item item = (Item) choices[i].getSelectedItem(); if (item != null) change(() -> state.equip(selected, item)); });
            remove[i].addActionListener(e -> change(() -> state.unequip(selected, slot)));
            buttons.add(equip[i]); buttons.add(remove[i]); row.add(buttons); gear.add(row);
        }
        gear.setPreferredSize(new Dimension(330, 440));
        JScrollPane gearScroll = new JScrollPane(gear); gearScroll.getViewport().setBackground(PANEL); gearScroll.getVerticalScrollBar().setUnitIncrement(24);
        tabs.addTab("Gear", gearScroll);
        JPanel forge = panel(new BorderLayout(0, 10)); forge.setBorder(new EmptyBorder(12, 10, 12, 10));
        forge.add(label("Buy gear here, then equip it in Gear", MUTED, 11), BorderLayout.NORTH);
        shop.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        shop.setCellRenderer(new DefaultListCellRenderer() {
            // build a readable item row for the current inventory or shop context
            // include the item icon and the information needed before equipping or buying
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                JLabel cell = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
                Item item = (Item) value; cell.setText("<html>" + item.name + " / " + item.price + " gold<br>" + item.bonus() + " / Owned: " + state.inventory.getOrDefault(item.id, 0) + "<br>" + (selected.role.accepts(item) ? "Usable by " + selected.role.label : "Requires " + item.weapon.name().toLowerCase() + " user") + "</html>");
                cell.setIcon(UiSkin.itemIcon(item)); cell.setIconTextGap(8);
                cell.setBorder(new EmptyBorder(9, 7, 9, 7)); cell.setBackground(sel ? new Color(0x373445) : PANEL); cell.setForeground(sel ? GOLD : TEXT); return cell;
            }
        });
        shop.setSelectedIndex(0); shop.addListSelectionListener(e -> refreshBuy()); forge.add(new JScrollPane(shop), BorderLayout.CENTER);
        buy.addActionListener(e -> { Item item = shop.getSelectedValue(); if (item != null) change(() -> state.buy(item)); }); forge.add(buy, BorderLayout.SOUTH);
        tabs.addTab("Shop", forge);
        JPanel training = panel(new GridLayout(4, 1, 0, 10)); training.setBorder(new EmptyBorder(14, 8, 8, 8));
        for (GameState.Upgrade upgrade : GameState.Upgrade.values()) {
            int i = upgrade.ordinal();
            JPanel row = panel(new BorderLayout(0, 6));
            upgradeLabels[i] = label("", GOLD, 18); row.add(upgradeLabels[i], BorderLayout.CENTER);
            upgradeButtons[i] = button("Upgrade");
            upgradeButtons[i].setIcon(UiSkin.icon(3, upgrade == GameState.Upgrade.HEALTH ? 1 : upgrade == GameState.Upgrade.HEALING ? 15 : upgrade == GameState.Upgrade.GUARD ? 12 : 9, 22));
            upgradeButtons[i].addActionListener(e -> change(() -> state.upgrade(selected, upgrade)));
            row.add(upgradeButtons[i], BorderLayout.SOUTH); training.add(row);
        }
        training.setPreferredSize(new Dimension(330, 450));
        JScrollPane trainingScroll = new JScrollPane(training); trainingScroll.getViewport().setBackground(PANEL); trainingScroll.getVerticalScrollBar().setUnitIncrement(24);
        tabs.addTab("Upgrades", trainingScroll);

        // use large icon buttons for navigation while retaining the tab container for page selection
        // this keeps the tests and page state simple without showing a cramped default tab strip
        JPanel navigation = panel(new GridLayout(2, 2, 8, 8));
        JButton[] navigationButtons = new JButton[4];
        int[] navigationIcons = {20, 12, 38, 35};
        for (int i = 0; i < 4; i++) {
            final int tabIndex = i;
            JButton nav = button(tabs.getTitleAt(i)); navigationButtons[i] = nav;
            nav.setIcon(UiSkin.icon(i == 0 ? 2 : 3, navigationIcons[i], 24));
            nav.addActionListener(e -> tabs.setSelectedIndex(tabIndex));
            nav.putClientProperty("selectedHero", i == 0); navigation.add(nav);
        }
        tabs.addChangeListener(e -> {
            for (int i = 0; i < 4; i++) { navigationButtons[i].putClientProperty("selectedHero", tabs.getSelectedIndex() == i); navigationButtons[i].repaint(); }
        });
        JPanel management = panel(new BorderLayout(0, 8)); management.add(navigation, BorderLayout.NORTH); management.add(tabs, BorderLayout.CENTER);
        sidebar.add(management, BorderLayout.CENTER);
        JLabel guide = label("", MUTED, 11);
        String[] hints = {"<html>Up to 4 heroes fight at once.<br>Recruited heroes can wait at camp.</html>", "<html>Only owned, compatible gear is listed.<br>Replaced items return to your inventory.</html>", "<html>Purchases go to shared inventory.<br>Buying an item does not equip it.</html>", "<html>Each upgrade is bought separately.<br>Gold is shared by all heroes.</html>"};
        guide.setText(hints[0]); tabs.addChangeListener(e -> guide.setText(hints[tabs.getSelectedIndex()]));
        guide.setBorder(new EmptyBorder(4, 6, 2, 6)); sidebar.add(guide, BorderLayout.SOUTH);
        body.add(sidebar, BorderLayout.EAST); root.add(body, BorderLayout.CENTER);
        expand.addActionListener(e -> { sidebar.setVisible(!sidebar.isVisible()); expand.setText(sidebar.isVisible() ? "Expand battlefield" : "Show party & gear"); body.revalidate(); body.repaint(); });
        JPanel footer = panel(new BorderLayout(10, 0)); event.setForeground(GOLD); event.setFont(font(11)); footer.add(event, BorderLayout.CENTER);
        saveStatus.setForeground(MUTED); saveStatus.setFont(font(10)); footer.add(saveStatus, BorderLayout.EAST); root.add(footer, BorderLayout.SOUTH);
        refresh();
    }

    // use the shared game font and its minimum readable size
    private static Font font(int size) { return UiSkin.font(size); }

    // translate a class into a short role description for the management panel
    // this helps players choose a party without knowing internal class rules
    private static String roleDescription(Role role) {
        switch (role) {
            case PRIEST: return "Healer";
            case WIZARD: return "Spellcaster";
            case ARCHER: return "Ranged attacker";
            case KNIGHT: case KNIGHT_TEMPLAR: return "Front-line defender";
            default: return "Melee attacker";
        }
    }

    // format stats with at most one decimal place
    // whole numbers stay short while small guard and damage differences remain visible
    private static String stat(double value) { return new DecimalFormat("0.#").format(value); }

    // create a transparent layout container
    // the surrounding asset-backed panel supplies the visible background
    private static JPanel panel(LayoutManager layout) { JPanel panel = new JPanel(layout); panel.setOpaque(false); return panel; }

    // create a consistently styled text label for the game interface
    private static JLabel label(String text, Color color, int size) { JLabel label = new JLabel(text); label.setForeground(color); label.setFont(font(size)); return label; }

    // create a padded action button with a matching icon
    // its animation and frame come from the shared skin
    private static JButton button(String text) {
        JButton b = new JButton(text); b.setFont(UiSkin.font(16)); b.setBackground(PANEL); b.setForeground(TEXT); b.setFocusPainted(true);
        if (!text.isEmpty()) { b.setIcon(UiSkin.actionIcon(text)); b.setIconTextGap(6); }
        b.setBorder(new EmptyBorder(9, 10, 9, 10)); return b;
    }

    // run a requested gameplay change, then save and refresh only if it succeeds
    // failed purchases should not pretend that progress changed
    private void change(java.util.function.BooleanSupplier operation) {
        if (operation.getAsBoolean()) { bagSignature = ""; persist(); refresh(); }
    }

    // update the shop purchase button for the selected item and available gold
    // show the shortfall when the player cannot afford it
    private void refreshBuy() {
        Item item = shop.getSelectedValue();
        buy.setEnabled(item != null && state.gold >= item.price && state.inventory.getOrDefault(item.id, 0) < 999);
        buy.setText(item == null ? "Select an item" : (state.gold < item.price ? "Need " + (int) Math.ceil(item.price - state.gold) + " more gold" : "Buy for " + item.price + " gold"));
    }

    // refresh hero details, party status, upgrade previews, and gear choices from the state
    // preserve a compatible inventory selection instead of rebuilding it unnecessarily
    private void refresh() {
        resources.setText(new DecimalFormat("#,##0").format(state.gold) + " gold");
        partyCount.setText(state.activeCount() + "/4 party");
        stage.setText("EXPEDITION " + String.format("%03d", state.stage) + " / " + Encounters.title(state.stage) + " / " + (int) state.enemyHp + " HP");
        for (Hero h : state.heroes) {
            JButton card = roster[h.role.ordinal()];
            card.setText("<html>" + h.role.label + "<br>" + roleDescription(h.role) + "<br>" + (h.level == 0 ? "Not recruited / " + (int) state.levelCost(h) + " gold" : h.active ? (h.hp > 0 ? "In party" : "In party / Knocked out") : "At camp") + "</html>");
            card.putClientProperty("selectedHero", h == selected);
        }
        heroTitle.setText(selected.role.label);
        heroStats.setText("<html>HP " + (int) selected.hp + "/" + (int) selected.maxHp() + "<br>Damage " + stat(selected.power() * (selected.role == Role.PRIEST ? .4 : 1)) + " / Guard " + stat(selected.guard()) + "</html>");
        weaponRule.setText(roleDescription(selected.role));
        weaponRule.setToolTipText("Weapons: " + selected.role.weapons + (selected.role == Role.PRIEST ? "; heals " + stat(selected.healingPower()) + " HP per cast" : ""));
        train.setText(selected.level == 0 ? "Recruit " + (int) state.levelCost(selected) + "g" : "Upgrades");
        if (selected.level == 0 && train.getParent() == null) { heroActions.add(train, 0); heroActions.revalidate(); }
        else if (selected.level > 0 && train.getParent() != null) { heroActions.remove(train); heroActions.revalidate(); }
        train.setEnabled(selected.level > 0 || state.gold >= state.levelCost(selected));
        for (GameState.Upgrade upgrade : GameState.Upgrade.values()) {
            int i = upgrade.ordinal(), rank = selected.rank(upgrade);
            boolean supported = upgrade != GameState.Upgrade.HEALING || selected.role == Role.PRIEST;
            String name = upgrade == GameState.Upgrade.ATTACK ? (selected.role == Role.WIZARD || selected.role == Role.PRIEST ? "Spell damage" : "Attack damage") : upgrade == GameState.Upgrade.HEALTH ? "Vitality / HP" : upgrade == GameState.Upgrade.HEALING ? "Healing" : "Guard";
            String bonus = upgrade == GameState.Upgrade.ATTACK ? "+3 base damage" : upgrade == GameState.Upgrade.HEALTH ? "+18 maximum HP" : upgrade == GameState.Upgrade.HEALING ? "+4 healing per cast" : "+1 damage protection";
            double current = upgrade == GameState.Upgrade.ATTACK ? selected.power() * (selected.role == Role.PRIEST ? .4 : 1) : upgrade == GameState.Upgrade.HEALTH ? selected.maxHp() : upgrade == GameState.Upgrade.HEALING ? selected.healingPower() : selected.guard();
            double gain = upgrade == GameState.Upgrade.ATTACK ? 3 * (1 + selected.tier(Slot.SHOES) * .05) * (selected.role == Role.PRIEST ? .4 : 1) : upgrade == GameState.Upgrade.HEALTH ? 18 : upgrade == GameState.Upgrade.HEALING ? 4 : 1;
            upgradeLabels[i].setText("<html>" + name + " / Rank " + rank + "<br>" + (supported ? stat(current) + " &rarr; " + stat(current + (rank < GameState.MAX_LEVEL ? gain : 0)) + (upgrade == GameState.Upgrade.HEALING ? " per cast" : "") : "Only priests can heal allies") + "</html>");
            int cost = (int) state.upgradeCost(selected, upgrade);
            upgradeButtons[i].setText(!supported ? "Priest only" : selected.level == 0 ? "Recruit this hero first" : rank >= GameState.MAX_LEVEL ? "Maximum rank" : state.gold < cost ? "Need " + (int) Math.ceil(cost - state.gold) + " more gold" : "Upgrade for " + cost + " gold");
            upgradeButtons[i].setToolTipText(bonus);
            upgradeButtons[i].setEnabled(supported && selected.level > 0 && rank < GameState.MAX_LEVEL && state.gold >= state.upgradeCost(selected, upgrade));
        }
        deploy.setText(selected.active ? "Send to camp" : "Add to party");
        deploy.setEnabled(selected.level > 0 && (selected.active ? state.activeCount() > 1 : state.activeCount() < 4));
        if (selected.level == 0) deploy.setText("Recruit first");
        else if (!selected.active && state.activeCount() >= 4) deploy.setText("Party full (4/4)");
        else if (selected.active && state.activeCount() == 1) deploy.setText("Keep one hero");
        if (selected.alive() && state.rest == 0 && state.heroes.stream().noneMatch(h -> h != selected && h.alive())) {
            deploy.setEnabled(false); deploy.setText("Last fighter");
            deploy.setToolTipText("At least one conscious hero must remain in the party.");
        } else deploy.setToolTipText(selected.active ? "Move this hero to camp to free a party slot." : "Add this hero to one of four party slots.");
        String signature = selected.role.name() + state.inventory.toString();
        for (Slot slot : Slot.values()) {
            int i = slot.ordinal(); Item current = selected.gear.get(slot);
            equipped[i].setText(slot.name().charAt(0) + slot.name().substring(1).toLowerCase() + ": " + (current == null ? "Nothing equipped" : current.name));
            equipped[i].setIcon(current == null ? UiSkin.icon(3, slot == Slot.HAT ? 13 : slot == Slot.ARMOR ? 12 : slot == Slot.SHOES ? 44 : 9, 24) : UiSkin.itemIcon(current));
            equipped[i].setToolTipText(current == null ? "No item equipped" : current.bonus());
            if (!signature.equals(bagSignature)) {
                Item previous = (Item) choices[i].getSelectedItem(); choices[i].removeAllItems();
                for (Item item : Catalog.ITEMS) if (item.slot == slot && selected.role.accepts(item) && state.inventory.getOrDefault(item.id, 0) > 0) choices[i].addItem(item);
                if (previous != null && state.inventory.getOrDefault(previous.id, 0) > 0 && selected.role.accepts(previous)) choices[i].setSelectedItem(previous);
            }
            equip[i].setEnabled(selected.level > 0 && choices[i].getSelectedItem() != null); remove[i].setEnabled(current != null);
            equip[i].setToolTipText(choices[i].getSelectedItem() == null ? "Buy compatible equipment in the Shop first." : "Equip the selected item; your old item returns to inventory.");
            remove[i].setToolTipText(current == null ? "This slot is empty." : "Return this item to shared inventory.");
        }
        bagSignature = signature; refreshBuy(); root.repaint(); event.setText(state.message); battlefield.repaint();
    }

    // save the current expedition and report the result in the footer
    // return false so menu or quit actions can avoid discarding unsaved progress
    private boolean persist() {
        try { saves.save(state); saveStatus.setText("Progress saved"); return true; }
        catch (IOException e) { saveStatus.setText("SAVE FAILED"); saveStatus.setToolTipText(e.getMessage()); return false; }
    }

    // expose the constructed screen without starting gameplay
    // tests use this to lay out and render the interface safely
    public JPanel view() { return root; }

    // attach gameplay to the shared window and start its timers
    // combat waits for completed animations while autosave uses its own periodic counter
    public void show(JFrame host, Runnable onMenu, boolean reducedMotion, boolean damageNumbers) {
        window = host;
        if (window.isVisible()) window.setExtendedState(window.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        battlefield.setDisplayOptions(reducedMotion, damageNumbers);
        JButton menu = button("Main Menu");
        menu.addActionListener(e -> onMenu.run());
        JPanel footer = (JPanel) ((BorderLayout) root.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
        JPanel controls = panel(new FlowLayout(FlowLayout.RIGHT, 12, 0)); controls.add(saveStatus); controls.add(menu);
        footer.add(controls, BorderLayout.EAST);
        window.setContentPane(root); window.revalidate(); window.repaint();

        // poll frequently, but only resolve a round once its waiting time and animation gate allow it
        // regrouping still advances in one-second steps, and autosave has a separate counter
        combat = new Timer(100, e -> {
            if (++combatWait >= 10 && battlefield.readyForCombat()) { state.tick(); combatWait = 0; }
            if (++heartbeat % 10 == 0 && ++seconds % 15 == 0) persist();
            refresh();
        });
        animation = new Timer(33, e -> battlefield.animate());
        combat.start(); animation.start();
    }

    // save before stopping gameplay timers
    // leave the game running if saving fails so the player can try again
    public boolean saveAndStop() {
        if (!persist()) return false;
        if (combat != null) combat.stop(); if (animation != null) animation.stop();
        return true;
    }

    // set the swing colors and install the shared asset skin before creating windows
    // this keeps dialogs and controls consistent
    public static void theme() {
        java.util.List<Object> fontKeys = new java.util.ArrayList<>();
        for (Object key : UIManager.getDefaults().keySet()) if (UIManager.get(key) instanceof Font) fontKeys.add(key);
        for (Object key : fontKeys) UIManager.put(key, new javax.swing.plaf.FontUIResource(Font.SANS_SERIF, Font.PLAIN, 14));
        UIManager.put("Panel.background", PANEL); UIManager.put("Label.foreground", TEXT);
        UIManager.put("List.background", PANEL); UIManager.put("List.foreground", TEXT);
        UIManager.put("ComboBox.background", PANEL); UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", LINE); UIManager.put("ComboBox.selectionForeground", GOLD);
        UIManager.put("TabbedPane.background", BG); UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("TabbedPane.selected", LINE); UIManager.put("TabbedPane.contentAreaColor", LINE);
        UIManager.put("ScrollPane.background", PANEL); UIManager.put("ScrollBar.thumb", LINE);
        UIManager.put("OptionPane.background", PANEL); UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("CheckBox.background", PANEL); UIManager.put("CheckBox.foreground", TEXT);
        UiSkin.install();
    }

    // start the application on swing's event thread
    // create the theme before opening the main menu
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> { theme(); MainMenu.launch(); });
    }
}
