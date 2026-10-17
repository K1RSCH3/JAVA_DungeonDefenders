# Dungeon Defenders

**An idle chronicle of iron & ash.** A medieval dark-fantasy party RPG with a pixel battlefield, automatic combat, a recruitable roster, equipment, and a blacksmith. Built with Java 11+, Swing, and Java2D; no external libraries required. Third-party asset packs are kept locally and are not included in this repository.

## Asset packs are not included

This project uses paid asset packs. Those packs are **not included in this repository** and must be obtained separately from their creators. Other third-party artwork, fonts, source archives, and generated artwork previews are also excluded. Pack references and purchase links will be added later.

The source code is provided here, but running the complete GUI and asset-dependent tests requires the matching assets to be installed locally. See the [asset guide](assets/README.md) for the expected layout. The ignore rules keep local assets out of new commits without deleting them from your computer.

## Main menu

The game opens to an animated Cold Corridors menu using the supplied Pixelta font and a wood-and-iron Free Medieval Fantasy UI Pack skin by Kibyra. When supplied locally, the font is loaded directly without an OS font installation. Matching wooden panels and buttons, icon tabs, reinforced selection markers, parchment notices, and field borders continue into gameplay. See the [asset guide](assets/README.md) for attribution and integration details.

- **Continue Expedition** resumes your save and claims offline gold. It is disabled until a save exists.
- **New Expedition** starts a fresh party. If a save exists, confirmation is required and a timestamped `ashen-save-backup-*.properties` copy is made first.
- **Settings** saves reduced-motion and damage/healing-number preferences in `settings.properties`. Reduced motion keeps scenery and character poses still without stopping combat or transitions.
- **Credits** lists the character and background packs.
- **Quit Game** closes the application.

During an expedition, **Main Menu** saves progress and stops combat timers before returning. Time spent at the menu counts toward offline rewards on Continue. Opening or quitting the menu alone does not create or overwrite a campaign. New campaigns and settings remain local to this folder.

## Run

The game is titled **Dungeon Defenders**, in `JAVA_DungeonDefenders`. The launchers resolve their own directory, so folder renames require no path changes.

From the parent GitHub folder:

```powershell
cd JAVA_DungeonDefenders
.\run.cmd
```

Requires a **JDK 11 or newer**. The launcher uses `JAVA_HOME` if set, otherwise `javac` and `java` on PATH. Reopen your terminal after changing Java environment settings. `run.cmd` works when PowerShell scripts are disabled. `run.ps1` is also available.

Manual launch from the project directory:

```powershell
New-Item -ItemType Directory -Force out
javac --release 11 -d out src\idle\*.java
java -cp out idle.IdleGame
```

Default window: 1280 x 900; minimum: 1240 x 870. OS display scaling affects how much physical screen space it needs.

## Play

You begin with a **Knight, Wizard, Priest, and Archer**, plus 120 gold. Battles run automatically, with roughly 1.4 seconds per combat round so attacks and return movements finish before the next round. Regrouping countdowns still use one-second steps. Your priest heals wounded allies; knights take priority as front-line targets. Every fifth combat tick boosts party attacks.

1. Select a hero in the roster to inspect their level, HP, power, and equipment.
2. Use **Recruit** to unlock a class, or **Upgrades** to open its **Upgrades** tab. Purchase attack/spell damage (+3 base), vitality (+18 maximum HP), healing (+4 per cast, priests only), or guard (+1) independently. Each track has its own increasing cost and caps at rank 100. Attack upgrades do not increase healing. Vitality adds only the newly gained HP to a living hero, rather than fully healing them.
3. Use **Send to camp** and **Add to party** to swap the active party. At most four heroes can march; at least one must remain.
4. Open **Shop**, choose an item, and press **Buy** to buy it for the shared stash.
5. Open **Gear**, select a compatible item under its slot, and press **Equip**. Replaced items return to the stash. **Remove** also returns an item to the stash.

Only the following nine classes are recruitable. All hats, armor, and shoes work for every class.

| Class | Allowed weapons | Role |
| --- | --- | --- |
| Lancer | Spear | Melee |
| Wizard | Staff, wand | Magic damage |
| Priest | Staff, wand | Healing and reduced attack damage |
| Archer | Bow | Ranged damage |
| Soldier | Spear | Melee |
| Knight | Sword | Front-line guard |
| Knight Templar | Sword | Front-line guard |
| Swordsman | Sword | Melee |
| Armored Axeman | Axe | Melee |

Swords are restricted to Knight, Knight Templar, and Swordsman. Staffs and wands are restricted to Wizard and Priest. Spears, bows, and axes provide matching weapons for the other classes. Each hero can equip **one weapon, one hat, one armor, and one pair of shoes**.

## Progression

Normal encounters contain four enemies of the same species: skeletons with skeletons, orcs with orcs, and so on. Variants repeat when a species has fewer than four available units. Bosses remain separate encounters every 25 stages. The party focuses targets in order, with excess damage carrying to the next target. Defeated enemies stop attacking, and the stage advances only after the whole patrol falls.

The battlefield's top HUD uses a compact two-by-two grid for each side, with portraits, names, and six hearts per combatant. Each heart represents one sixth of maximum HP; partial hearts show intermediate health. Hearts hold briefly before draining or refilling in discrete half-heart steps, then settle to exact health. Damage flashes and shakes affected hearts; healing uses stepped green sparkles. Melee lunges ease fully back into formation, and fallen heroes keep their last position instead of snapping home. Empty hearts use the pack's dimmed broken-heart artwork. Reduced-motion mode updates heart fills immediately without movement. Exact HP remains available in the selected hero's details. It replaces the old stage banner and bars beneath sprites. Spell impacts, arrows, melee strikes, and green priest healing effects follow the actual combat targets, including damage that carries into another enemy.

| First appearance | Boss | Sprite |
| --- | --- | --- |
| Stage 25 | Gorath, the Iron Warlord | Elite Orc |
| Stage 50 | Mordren, Keeper of Graves | Necromancer |
| Stage 75 | The Labyrinth Tyrant | Minotaur |
| Stage 100 | The Black Castellan | Black Knight C |
| Stage 125 | The Cinder Colossus | Flame Golem |

This rotation repeats every 125 stages with stage-based stat growth. These five enemies are reserved for boss encounters. Failed attempts restore the party and retry the same stage after five seconds; progress never rolls back. Version-1 and version-2 saves load into version 3, preserving existing levels, gear, gold, and progress. New upgrade ranks start at zero; old level bonuses remain intact.

- Enemy encounter HP scales as `45 + 20 × stage` (four times that for bosses). Each patrol member deals `(5 + 1.7 × stage) / 4` base damage; bosses deal `(5 + 1.7 × stage) × 1.5`, before guard and heavy-strike modifiers.

- Weapons add power; hats add HP and power; armor adds HP and guard; shoes add HP and a damage speed multiplier.
- Iron, Runebound, and Eclipse gear offer three tiers. Gear changes stats; it does not change the sprite's appearance.
- Defeated enemies award gold. Every third victory drops an equipment item into the shared stash.
- Every 25th stage is a named boss encounter with four times normal encounter HP, five times gold, a heavy strike every fifth combat tick, and guaranteed gear.
- Surviving party members recover some HP after a victory. Fallen members stay down until trained or the party rests.
- A failed attempt immediately rescues and restores the roster, then regroups for five seconds before retrying the same stage. Enemies reset to full HP, so unsuccessful attempts cannot gradually chip through a progression wall. Gold, equipment, levels, and stage are preserved; failed attempts award no victory gold or loot. Train or improve gear to advance.
- Offline camp expeditions award gold at 35% of the current party's estimated earning rate, capped at eight hours. They do not simulate battles, loot, or stage progress. The game trusts the local clock.
- Levels cap at 100, stages at 10,000, and gold at one trillion. The last stage repeats.

The enemy catalog includes all 33 supplied enemy names, from orcs and skeletons through demons, werewolves, the Minotaur, Black Knights, and Warlock. They currently share stage-scaled combat rules; distinct enemy abilities are a future extension.

## Sprites

Walk cycles are driven by distance travelled, with eased starts and stops. Entrances take about two seconds; movement is updated at the normal redraw rate while attack and death sequences play at a slower pace. Incoming enemies start fully offscreen to the right and walk or fly into formation with eased movement. Their animation follows their approach distance relative to the moving floor; combat waits until arrival. Background layers retain parallax. Reduced-motion mode keeps the encounter stationary. The battlefield uses **Cold Corridors**, with parallax-scrolling arches, a moving tiled floor, and animated torches. All **9 playable classes and 33 enemies** use the complete Tiny RPG packs. Characters walk or fly into encounters, attack, react to hits, heal, and play death animations. After a victory, combat pauses briefly for the death and walking transition; the scenery scrolls during travel and stops during battle. It renders at 384 x 216 and fits the available display area using nearest-neighbor scaling. Gameplay maximizes when opened, and **Expand battlefield** hides the management panel; **Show party & gear** restores it. Party selection now lives in the sidebar Party tab.

With the required local packs installed, the starting party uses the complete sprites. Add PNGs in `assets/characters/` or `assets/enemies/` for further overrides. Read the [sprite guide](assets/README.md) for installed pack details, exact filenames, and sheet requirements. Existing saves are compatible.

## Saves

Progress saves to **`ashen-save.properties`** every 15 seconds, after roster/equipment purchases and changes, and on normal close. This legacy filename and the `ashen-save-backup-*` backup names are retained so the title change does not interrupt existing campaigns. Keep these files inside the project folder when renaming it. Offline rewards are checkpointed when opening the game. A lock prevents two game instances from using the same save simultaneously.

The old `save.properties` is preserved and is not imported: the new roster and equipment model start a fresh campaign. Saves use versioned, validated properties and atomic replacement where supported. Invalid data produces an error instead of silently overwriting it. To reset, close the game and move `ashen-save.properties` aside. Launch scripts always use the project directory for saves and assets.

## Code and checks

| File | Responsibility |
| --- | --- |
| `src/idle/Catalog.java` | Playable classes, weapon restrictions, items, enemy names |
| `src/idle/GameState.java` | Party, gear, combat, healing, loot, offline rewards |
| `src/idle/Encounters.java` | Four-enemy patrols, boss rotation, and creature sizes |
| `src/idle/SaveStore.java` | Versioned saves, validation, instance lock |
| `src/idle/PixelArt.java` | Placeholder art and optional PNG loading |
| `src/idle/AssetPack.java` | Supplied action sheets, frame anchors, and Cold Corridors layers |
| `src/idle/BattlePanel.java` | Pixel scene and visual animation |
| `src/idle/IdleGame.java` | Desktop GUI and timers |
| `src/idle/MainMenu.java` | Main menu, settings, campaign entry, and application lifecycle |
| `src/idle/UiSkin.java` | Pixelta font, medieval asset frames/icons, shared button/tab/control skin |

```powershell
.\run.cmd test
```

Checks cover party limits, class restrictions, gear stats and inventory accounting, healing, boss rewards, four-enemy patrols, save migration, loot, defeat/recovery, offline rewards, saves, instance locking, all 42 character mappings, required action sheets, corridor scrolling, and encounter transitions. They render the GUI, complete roster, movement sequence, and sample action sheets into `out/`, without launching a window or modifying player saves.

The management tabs explain their next step in the footer. Party cards show each hero's role and whether they are in the party, at camp, or unrecruited. Upgrade previews show current and next values, and unavailable actions explain gold shortfalls or restrictions. Shop items show owned quantities and whether the selected hero can use them.

The management panel keeps your coin balance and party count beside the selected hero's framed portrait. Four icon buttons open Party, Gear, Shop, and Upgrades; longer gear and upgrade panels scroll independently.
