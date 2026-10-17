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

public final class IdleGame {
    private static final Color BG = new Color(0x12141d), PANEL = new Color(0x1c1f2b), LINE = new Color(0x363a48), GOLD = new Color(0xd7ba7d), TEXT = new Color(0xe3dfd5), MUTED = new Color(0xa5a7b3);
    private final GameState state;
    private final SaveStore saves;
    private final JPanel root = new JPanel(new BorderLayout(18, 18));
    private final JLabel resources = new JLabel(), stage = new JLabel(), event = new JLabel(), saveStatus = new JLabel("Autosave every 15s");
    private final JButton[] roster = new JButton[9];
    private final JLabel heroTitle = new JLabel(), heroStats = new JLabel(), weaponRule = new JLabel();
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
    private Timer combat, animation;

    @SuppressWarnings("unchecked")
    public IdleGame(GameState state, SaveStore saves) {
        this.state = state; this.saves = saves; selected = state.heroes.get(Role.KNIGHT.ordinal());
        choices = (JComboBox<Item>[]) new JComboBox<?>[4];
        root.setBackground(BG); root.setBorder(new EmptyBorder(20, 22, 16, 22));
        JPanel header = panel(new BorderLayout());
        JPanel brand = panel(new GridLayout(0, 1, 0, 4));
        JLabel title = label("ASHEN DEFENDERS", GOLD, 27); brand.add(title);
        brand.add(label("AN IDLE CHRONICLE OF IRON & ASH", MUTED, 11));
        header.add(brand, BorderLayout.WEST); resources.setForeground(GOLD); resources.setFont(font(15)); header.add(resources, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);
        JPanel body = panel(new BorderLayout(18, 0));
        JPanel left = panel(new BorderLayout(0, 14));
        JPanel scene = panel(new BorderLayout());
        stage.setForeground(TEXT); stage.setBorder(new EmptyBorder(0, 0, 10, 0)); scene.add(stage, BorderLayout.NORTH);
        battlefield = new BattlePanel(state); scene.add(battlefield, BorderLayout.CENTER);
        JLabel tip = label("AUTO BATTLE   /   Skills every 5s   /   Loot every 3 victories", MUTED, 11);
        tip.setBorder(new EmptyBorder(10, 0, 0, 0)); scene.add(tip, BorderLayout.SOUTH); left.add(scene, BorderLayout.CENTER);
        JPanel rosterBlock = panel(new BorderLayout(0, 9));
        rosterBlock.add(label("THE DEFENDERS   /   Select a hero to manage gear", GOLD, 12), BorderLayout.NORTH);
        JPanel cards = panel(new GridLayout(3, 3, 9, 9));
        for (Hero h : state.heroes) {
            JButton card = button(""); roster[h.role.ordinal()] = card; card.setHorizontalAlignment(SwingConstants.LEFT);
            card.setIcon(new ImageIcon(PixelArt.portrait(h.role)));
            card.setIconTextGap(9); card.addActionListener(e -> { selected = h; bagSignature = ""; refresh(); }); cards.add(card);
        }
        cards.setPreferredSize(new Dimension(768, 220)); rosterBlock.add(cards, BorderLayout.CENTER); left.add(rosterBlock, BorderLayout.SOUTH);
        body.add(left, BorderLayout.CENTER);
        JPanel sidebar = panel(new BorderLayout(0, 12)); sidebar.setPreferredSize(new Dimension(370, 660));
        JPanel character = panel(new GridLayout(0, 1, 0, 7));
        heroTitle.setForeground(GOLD); heroTitle.setFont(font(19)); character.add(heroTitle);
        heroStats.setForeground(TEXT); character.add(heroStats); weaponRule.setForeground(MUTED); weaponRule.setFont(font(10)); character.add(weaponRule);
        JPanel actions = panel(new GridLayout(1, 2, 8, 0)); actions.add(train); actions.add(deploy); character.add(actions);
        train.addActionListener(e -> change(() -> state.train(selected)));
        deploy.addActionListener(e -> change(() -> state.toggle(selected)));
        sidebar.add(character, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        JPanel gear = panel(new GridLayout(4, 1, 0, 12)); gear.setBorder(new EmptyBorder(14, 10, 12, 10));
        for (Slot slot : Slot.values()) {
            int i = slot.ordinal(); JPanel row = panel(new GridLayout(0, 1, 0, 5));
            equipped[i] = label("", GOLD, 11); row.add(equipped[i]); choices[i] = new JComboBox<>(); choices[i].setFont(font(11));
            choices[i].setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                    return super.getListCellRendererComponent(list, value == null ? "No compatible gear in stash" : value, index, sel, focus);
                }
            });
            row.add(choices[i]);
            JPanel buttons = panel(new GridLayout(1, 2, 6, 0)); equip[i] = button("Equip"); remove[i] = button("Remove");
            choices[i].addActionListener(e -> equip[i].setEnabled(selected.level > 0 && choices[i].getSelectedItem() != null));
            equip[i].addActionListener(e -> { Item item = (Item) choices[i].getSelectedItem(); if (item != null) change(() -> state.equip(selected, item)); });
            remove[i].addActionListener(e -> change(() -> state.unequip(selected, slot)));
            buttons.add(equip[i]); buttons.add(remove[i]); row.add(buttons); gear.add(row);
        }
        tabs.addTab("EQUIPMENT", gear);
        JPanel forge = panel(new BorderLayout(0, 10)); forge.setBorder(new EmptyBorder(12, 10, 12, 10));
        forge.add(label("Shared stash / buy, then equip", MUTED, 11), BorderLayout.NORTH);
        shop.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        shop.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                JLabel cell = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
                Item item = (Item) value; cell.setText("<html><b>" + item.name + "</b>  /  " + item.price + "g<br>" + item.bonus() + "  |  Stash: " + state.inventory.getOrDefault(item.id, 0) + "</html>");
                cell.setBorder(new EmptyBorder(9, 7, 9, 7)); cell.setBackground(sel ? new Color(0x373445) : PANEL); cell.setForeground(sel ? GOLD : TEXT); return cell;
            }
        });
        shop.setSelectedIndex(0); shop.addListSelectionListener(e -> refreshBuy()); forge.add(new JScrollPane(shop), BorderLayout.CENTER);
        buy.addActionListener(e -> { Item item = shop.getSelectedValue(); if (item != null) change(() -> state.buy(item)); }); forge.add(buy, BorderLayout.SOUTH);
        tabs.addTab("BLACKSMITH", forge); sidebar.add(tabs, BorderLayout.CENTER);
        JLabel guide = label("Four march. Nine answer the call.", MUTED, 11); sidebar.add(guide, BorderLayout.SOUTH);
        body.add(sidebar, BorderLayout.EAST); root.add(body, BorderLayout.CENTER);
        JPanel footer = panel(new BorderLayout(10, 0)); event.setForeground(GOLD); event.setFont(font(11)); footer.add(event, BorderLayout.CENTER);
        saveStatus.setForeground(MUTED); saveStatus.setFont(font(10)); footer.add(saveStatus, BorderLayout.EAST); root.add(footer, BorderLayout.SOUTH);
        refresh();
    }
    private static Font font(int size) { return new Font(Font.MONOSPACED, Font.PLAIN, size); }
    private static JPanel panel(LayoutManager layout) { JPanel panel = new JPanel(layout); panel.setOpaque(false); return panel; }
    private static JLabel label(String text, Color color, int size) { JLabel label = new JLabel(text); label.setForeground(color); label.setFont(font(size)); return label; }
    private static JButton button(String text) {
        JButton b = new JButton(text); b.setFont(font(11)); b.setBackground(PANEL); b.setForeground(TEXT); b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(LINE), new EmptyBorder(8, 9, 8, 9))); return b;
    }
    private void change(java.util.function.BooleanSupplier operation) {
        if (operation.getAsBoolean()) { bagSignature = ""; persist(); refresh(); }
    }
    private void refreshBuy() {
        Item item = shop.getSelectedValue();
        buy.setEnabled(item != null && state.gold >= item.price && state.inventory.getOrDefault(item.id, 0) < 999);
        buy.setText(item == null ? "Select an item" : "Forge  /  " + item.price + " gold");
    }
    private void refresh() {
        resources.setText(new DecimalFormat("#,##0").format(state.gold) + " GOLD   |   " + state.activeCount() + "/4 PARTY");
        stage.setText("EXPEDITION " + String.format("%03d", state.stage) + "  /  " + state.enemyName() + (state.boss() ? " [ELITE]" : "") + "  /  " + (int) state.enemyHp + " HP");
        for (Hero h : state.heroes) {
            JButton card = roster[h.role.ordinal()];
            card.setText("<html><b>" + h.role.label + "</b><br>" + (h.level == 0 ? "UNRECRUITED" : "Lv " + h.level + " / " + (h.active ? (h.hp > 0 ? "MARCHING" : "FALLEN") : "CAMP")) + "</html>");
            card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(h == selected ? GOLD : LINE, h == selected ? 2 : 1), new EmptyBorder(7, 8, 7, 8)));
        }
        heroTitle.setText(selected.role.label.toUpperCase());
        heroStats.setText("Lv " + selected.level + "  |  HP " + (int) selected.hp + "/" + (int) selected.maxHp() + "  |  POW " + (int) selected.power());
        weaponRule.setText("Weapons: " + selected.role.weapons.toString().replace("[", "").replace("]", "") + (selected.role == Role.PRIEST ? " / heals allies" : ""));
        train.setText(selected.level == 100 ? "MAX LEVEL" : (selected.level == 0 ? "Recruit " : "Train ") + (int) state.levelCost(selected) + "g");
        train.setEnabled(selected.level < 100 && state.gold >= state.levelCost(selected));
        deploy.setText(selected.active ? "Return to camp" : "Deploy");
        deploy.setEnabled(selected.level > 0 && (selected.active ? state.activeCount() > 1 : state.activeCount() < 4));
        String signature = selected.role.name() + state.inventory.toString();
        for (Slot slot : Slot.values()) {
            int i = slot.ordinal(); Item current = selected.gear.get(slot);
            equipped[i].setText(slot + " / " + (current == null ? "Empty" : current.name));
            equipped[i].setToolTipText(current == null ? "No item equipped" : current.bonus());
            if (!signature.equals(bagSignature)) {
                Item previous = (Item) choices[i].getSelectedItem(); choices[i].removeAllItems();
                for (Item item : Catalog.ITEMS) if (item.slot == slot && selected.role.accepts(item) && state.inventory.getOrDefault(item.id, 0) > 0) choices[i].addItem(item);
                if (previous != null && state.inventory.getOrDefault(previous.id, 0) > 0 && selected.role.accepts(previous)) choices[i].setSelectedItem(previous);
            }
            equip[i].setEnabled(selected.level > 0 && choices[i].getSelectedItem() != null); remove[i].setEnabled(current != null);
        }
        bagSignature = signature; refreshBuy(); shop.repaint(); event.setText(state.message); battlefield.repaint();
    }
    private boolean persist() {
        try { saves.save(state); saveStatus.setText("Progress saved"); return true; }
        catch (IOException e) { saveStatus.setText("SAVE FAILED"); saveStatus.setToolTipText(e.getMessage()); return false; }
    }
    public JPanel view() { return root; }
    public void show() {
        window = new JFrame("Ashen Defenders | Iron & Ash"); window.setContentPane(root);
        window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        window.setMinimumSize(new Dimension(1240, 870)); window.setSize(1280, 900); window.setLocationRelativeTo(null);
        combat = new Timer(1000, e -> { state.tick(); if (++seconds % 15 == 0) persist(); refresh(); });
        animation = new Timer(33, e -> battlefield.animate());
        window.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (!persist() && JOptionPane.showConfirmDialog(window, "Saving failed. Exit without saving?", "Ashen Defenders", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
                combat.stop(); animation.stop(); try { saves.close(); } catch (IOException e) { System.err.println(e.getMessage()); } window.dispose();
            }
        });
        window.setVisible(true); combat.start(); animation.start();
    }
    public static void theme() {
        UIManager.put("Panel.background", PANEL); UIManager.put("Label.foreground", TEXT);
        UIManager.put("List.background", PANEL); UIManager.put("List.foreground", TEXT);
        UIManager.put("ComboBox.background", PANEL); UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", LINE); UIManager.put("ComboBox.selectionForeground", GOLD);
        UIManager.put("TabbedPane.background", BG); UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("TabbedPane.selected", LINE); UIManager.put("TabbedPane.contentAreaColor", LINE);
        UIManager.put("ScrollPane.background", PANEL); UIManager.put("ScrollBar.thumb", LINE);
        UIManager.put("OptionPane.background", PANEL); UIManager.put("OptionPane.messageForeground", TEXT);
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            theme(); SaveStore saves = new SaveStore(Paths.get("ashen-save.properties"));
            try {
                saves.lock(); GameState state = saves.load(); double reward = state.offlineReward(System.currentTimeMillis());
                if (reward > 0) state.message = "Welcome back. Camp expeditions earned " + (int) reward + " gold while you were away.";
                saves.save(state); new IdleGame(state, saves).show();
            } catch (Exception e) {
                try { saves.close(); } catch (IOException ignored) { }
                JOptionPane.showMessageDialog(null, e.getMessage(), "Unable to start Ashen Defenders", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
