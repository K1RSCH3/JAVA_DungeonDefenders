# Asset setup and credits

Dungeon Defenders uses third-party character sprites, scenery, UI artwork, and a font. **These files are not included in the repository.** The full character packs are paid assets and must be obtained separately. All third-party assets remain local, including the free UI pack and font.

## Sources

| Category | Asset source | Used in the game |
| --- | --- | --- |
| Characters and enemies | [Tiny RPG Character Asset Pack 01 V2.0 by Zerie](https://zerie.itch.io/tiny-rpg-character-asset-pack) | Nine playable classes and thirteen enemies |
| Characters and enemies | [Tiny RPG Character Asset Pack 02 by Zerie](https://zerie.itch.io/tiny-rpg-character-asset-pack-02) | Twenty additional enemies |
| Background | [Gothicvania Cold Corridors by Ansimuz](https://ansimuz.itch.io/gothicvania-cold-corridors) | Corridor layers, floor tiles, and animated torches |
| User interface | [Free Medieval Fantasy UI Pack by Kibyra](https://kibyra.itch.io/free-medieval-fantasy-ui-pack) | Panels, buttons, tabs, hearts, coins, and equipment icons |
| Font | [Pixelta on DaFont](https://www.dafont.com/pixelta.font) | Menu and gameplay text |

Consult the source pages and documents supplied with each download for usage terms. Keep those documents with your local copies. Credits and links do not include or grant access to the asset files.

## Install locally

1. Obtain the **full 22-character** version of Pack 01 and **full 20-character** version of Pack 02. The free samples do not include the complete roster.
2. Extract the character packs, background, and UI pack into `assets/packs/`, using the directory names below.
3. Place the font at `assets/fonts/Pixelta.ttf`. An operating-system font installation is not required.
4. Keep each pack's internal filenames and folder structure intact. Avoid an extra nested archive folder between the named pack folder and its contents.
5. Restart the game after adding or replacing assets. Loaded images are cached during a session.

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

Enemies share several procedural placeholder body types until their PNGs are supplied. Normal encounters group enemies by species, and health and damage scale with stage. Enemy-specific skills are not implemented yet.
