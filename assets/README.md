# Asset setup and credits

This guide covers **V1.5 (1.5.0)**, including the 27 selected Raven equipment icons added after V1. Raw asset packs remain local and are not included in the repository.

Dungeon Defenders uses third-party character sprites, scenery, UI artwork, and a font. **These files are not included in the repository.** The full character packs are paid assets and must be obtained separately. All third-party assets remain local, including the free UI pack and font.

## Sources

| Category | Asset source | Used in the game |
| --- | --- | --- |
| Characters and enemies | [Tiny RPG Character Asset Pack 01 V2.0 by Zerie](https://zerie.itch.io/tiny-rpg-character-asset-pack) | Nine playable classes and thirteen enemies |
| Characters and enemies | [Tiny RPG Character Asset Pack 02 by Zerie](https://zerie.itch.io/tiny-rpg-character-asset-pack-02) | Twenty additional enemies |
| Background | [Gothicvania Cold Corridors by Ansimuz](https://ansimuz.itch.io/gothicvania-cold-corridors) | Corridor layers, floor tiles, and animated torches |
| User interface | [Free Medieval Fantasy UI Pack by Kibyra](https://kibyra.itch.io/free-medieval-fantasy-ui-pack) | Panels, buttons, tabs, hearts, coins, and equipment icons |
| Equipment | [Raven Fantasy Icons by Clockwork Raven](https://clockworkraven.itch.io/raven-fantasy-icons) | Selected weapon, helm, armor, and boot icons |
| Font | [Pixelta on DaFont](https://www.dafont.com/pixelta.font) | Menu and gameplay text |

Consult the source pages and documents supplied with each download for usage terms. Keep those documents with your local copies. Credits and links do not include or grant access to the asset files.

## Install locally

1. Obtain the **full 22-character** version of Pack 01 and **full 20-character** version of Pack 02. The free samples do not include the complete roster.
2. Extract the character packs, background, and UI pack into `assets/packs/`, using the directory names below.
3. Place the font at `assets/fonts/Pixelta.ttf`. An operating-system font installation is not required.
4. Keep each pack's internal filenames and folder structure intact. Avoid an extra nested archive folder between the named pack folder and its contents.
5. Install the 27 selected Raven PNGs using the Equipment icons section below. These are required by the asset tests, with fallback icons available during gameplay.
6. Restart the game after adding or replacing assets. Loaded images are cached during a session.

Expected layout, relative to the project root:

```text
assets/
  README.md
  fonts/
    Pixelta.ttf
  packs/
    Tiny RPG Character Asset Pack 01 v2.0 -Full 22 Characters/
      Characters(100x100 split)/
        Knight/
          Knight/
            ... original action sheets ...
    Tiny RPG Character Asset Pack 02 -Full 20 Characters/
      Characters(100x100 split)/
        ... original character folders ...
    Cold Corridors Files/
      Assets/
        Layers/
        Torch/
    Free_Medieval_Fantasy_UI_Pack/
      UI Icons/
      UI Icons 2/
      UI Icons 3/
      ... remaining original pack contents ...
    Free - Raven Fantasy Icons/
      Selected/
        ... the 27 selected fa icons listed below ...
  characters/
    ... optional custom hero PNGs ...
  enemies/
    ... optional custom enemy PNGs ...
```

Directory names must match the loader, even if the downloaded archive has a different version suffix. The character path intentionally contains two folders with the character's name. The loader selects those sheets rather than the alternate sheets with baked-in shadows.

Only this README is tracked under `assets/`. Local packs, fonts, and custom sprites are ignored by Git.

## How the assets are used

**Characters:** `AssetPack.java` reads the split 100 x 100 animation sheets. The full packs cover nine playable classes and thirty-three enemies. Character scale stays consistent between actions, while enemy sizes vary by creature. Equipped items change stats rather than the sprite artwork.

**Background:** Cold Corridors provides the `far`, `back`, `middle`, and `near` layers, `tileset.png`, and `torch-sheet.png`. Layers scroll at different speeds during travel, and the floor follows the party's movement. Scenery stops during combat while torches continue animating.

**Interface:** `UiSkin.java` uses the medieval pack's wood-and-iron panels, plank buttons, reinforced borders, parchment notices, and icons. Hover, press, selection, and disabled states are handled in code. The older UI Essential Pack is not required.

**Font:** Pixelta is loaded directly from its local TTF file. If unavailable, the game uses a system serif font.

Some missing character and background art has placeholder fallbacks. **Required medieval UI images do not:** a missing image raises a `Missing medieval UI asset` error. A fresh clone therefore needs the local assets before the full interface and asset-dependent tests can run.

## Equipment icons

The locally supplied `Free - Raven Fantasy Icons.zip` provides 27 selected icons. Only the chosen 16 x 16 PNGs are extracted into `assets/packs/Free - Raven Fantasy Icons/Selected/`. All other archive artwork is unused. These files remain excluded from Git.

| Equipment | Iron / tier 1 | Gold / tier 2 | Crystal / tier 3 |
| --- | --- | --- | --- |
| Sword | fa1443 | fa1523 | fa1603 |
| Spear | fa1463 | fa1543 | fa1623 |
| Axe | fa1466 | fa1546 | fa1626 |
| Bow | fa1514 | fa1594 | fa1674 |
| Staff | fa1492 | fa1572 | fa1652 |
| Wand | fa1494 | fa1574 | fa1654 |
| Helm (hat slot) | fa1905 | fa1911 | fa1831 |
| Armor | fa1921 | fa1927 | fa1873 |
| Boots | fa1937 | fa1943 | fa2161 |

Each identifier refers to a `.png` in the archive's `Separated Files/16x16/` folder. Copy those files into `Selected/` without renaming them. Silver or basic equipment represents Iron, warm gold represents the middle tier, and blue crystal-style equipment represents the strongest tier. Material names are game assignments based on appearance.

Prices remain 35, 240, and 1,200 gold. Weapon power, gear HP, armor guard, and shoe speed bonuses increase with tier. Existing saved item IDs are unchanged, so previously owned Runebound and Eclipse items now display as Gold and Crystal without losing their stats or ownership.

The icons appear in equipped slots, inventory choices, and the shop. They do not replace weapons or clothing baked into character animations. If a selected icon is missing, the game uses its medieval UI slot icon instead. Asset tests require all 27 selected files.

## Check the installation

From the project root:

```powershell
.\run.cmd test
```

The tests check character mappings, action sheets, scenery, and UI rendering alongside gameplay behavior. Generated previews appear in `out/` and remain local.

If an asset cannot be found, compare the path in the loader or error message with your extracted folders. Check for spelling differences, extra nesting, or a free sample pack in place of the full version.

## Adding further sprites

Put transparent PNGs in `characters/` and `enemies/`, using the exact lowercase filenames below. These overrides take precedence over the installed pack mappings. Restart the game after replacing a file; images are cached during a session. Missing or unreadable override images fall back to the built-in placeholders.

## Simple override image format

- A single character image can be any reasonable size; 32 x 40 or 48 x 48 pixels works well.
- Animated sheets must have **one horizontal row of square frames**, with frame width equal to image height. For example, a 192 x 48 image contains four 48 x 48 frames.
- Widths that are exact multiples of height, with at least two frames, are interpreted as animation sheets automatically. Avoid that aspect ratio for single-frame art.
- Single-strip overrides reuse the same sheet for each action. The imported packs have separate action sheets; custom layouts need a mapping in `AssetPack.java`.
- Allies should face right and enemies left. Keep consistent foot positions inside frames. Transparent margins affect the apparent size.
- Simple PNG overrides fit their display box; allies use a 40-pixel visible height; bats use 19, slimes 23, standard enemies 36, larger creatures 46, and bosses 66. Portraits have wider canvases to accommodate weapons. The 384 x 216 scene is fitted to the available battlefield area with nearest-neighbor sampling.
- Different sheet layouts or dedicated action animations require changes to the loader.

## Playable characters

```text
lancer.png
wizard.png
priest.png
archer.png
soldier.png
knight.png
knight_templar.png
swordsman.png
armored_axeman.png
```

## Enemies

```text
orc.png
armored_orc.png
elite_orc.png
skeleton.png
armored_skeleton.png
slime.png
werewolf.png
werebear.png
orc_rider.png
greatsword_skeleton.png
skeleton_archer.png
bat.png
necromancer.png
demon_a.png
demon_b.png
demon_c.png
demon_d.png
demon_e.png
demoness_a.png
demoness_b.png
hellhound.png
blood_monster_a.png
blood_monster_b.png
eyeball_monster.png
lava_slime.png
hellbat.png
ghostfire.png
minotaur.png
black_knight_a.png
black_knight_b.png
black_knight_c.png
flame_golem.png
warlock.png
```

The installed character packs supply the enemy artwork. Procedural placeholders are used when matching artwork is unavailable. Normal encounters group enemies by species, and health and damage scale with stage. Dedicated enemy skill systems remain a possible future addition.
