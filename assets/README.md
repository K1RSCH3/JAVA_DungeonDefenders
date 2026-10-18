# Asset setup and credits

This guide covers the artwork, equipment icons, sound effects, and ambience used in **V1.5 (1.5.0)**. Raw asset packs remain local and are not included in the repository.

Dungeon Defenders uses third-party character sprites, scenery, UI artwork, equipment icons, a font, sound effects, and ambience. **These files are not included in the repository.** The full character packs are paid assets and must be obtained separately. All third-party assets remain local, including free packs and the font.

The equipment and audio additions are part of **1.5.0**, alongside camp recovery and closable action guides. See the [current feature summary](../README.md#whats-new-in-150) and [changelog](../CHANGELOG.md) for gameplay changes. This guide explains asset installation and coverage.

## Sources

| Category | Asset source | Used in the game |
| --- | --- | --- |
| Characters and enemies | [Tiny RPG Character Asset Pack 01 V2.0 by Zerie](https://zerie.itch.io/tiny-rpg-character-asset-pack) | Nine playable classes and thirteen enemies |
| Characters and enemies | [Tiny RPG Character Asset Pack 02 by Zerie](https://zerie.itch.io/tiny-rpg-character-asset-pack-02) | Twenty additional enemies |
| Background | [Gothicvania Cold Corridors by Ansimuz](https://ansimuz.itch.io/gothicvania-cold-corridors) | Corridor layers, floor tiles, and animated torches |
| User interface | [Free Medieval Fantasy UI Pack by Kibyra](https://kibyra.itch.io/free-medieval-fantasy-ui-pack) | Panels, buttons, tabs, hearts, coins, and fallback equipment icons |
| Equipment | [Raven Fantasy Icons by Clockwork Raven](https://clockworkraven.itch.io/raven-fantasy-icons) | Selected weapon, helm, armor, and boot icons |
| Font | [Pixelta on DaFont](https://www.dafont.com/pixelta.font) | Menu and gameplay text |
| Sound effects and ambience | [Fantasy RPG: Dungeon & Magic SFX Pack by NimbeKit](https://nimbekit.itch.io/fantasy-rpg-dungeon-magic-sfx-pack) | Combat, spells, healing, feedback, and dungeon ambience |

Consult the source pages and documents supplied with each download for usage terms. Keep those documents with your local copies. Credits and links do not include or grant access to the asset files.

## Install locally

1. Obtain the **full 22-character** version of Pack 01 and **full 20-character** version of Pack 02. The free samples do not include the complete roster.
2. Extract the character packs, background, and UI pack into `assets/packs/`, using the directory names below.
3. Place the font at `assets/fonts/Pixelta.ttf`. An operating-system font installation is not required.
4. Keep each pack's internal filenames and folder structure intact. Avoid an extra nested archive folder between the named pack folder and its contents.
5. Install the 27 selected Raven PNGs using the Equipment icons section below. These are required by the asset tests, with fallback icons available during gameplay.
6. Install the NimbeKit WAV files using the Audio section below for sound playback and audio tests.
7. Restart the game after adding or replacing assets. Loaded images are cached during a session.

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
    NimbeKit_Fantasy_RPG_SFX_160/
      WAV/
        ... original category folders and WAV files ...
      README.txt
      LICENSE.txt
      manifest.csv
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

**Expedition library:** the Play screen reuses medieval wood-and-iron panels, scroll controls, map icons, and gear-shaped options buttons. Naming, renaming, and delete-confirmation dialogs use the same frames and themed buttons. No additional asset pack is needed.

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

The icons appear in equipped slots, inventory choices, and the shop. All new equipment is purchased from the Shop and equipped manually, with class-specific weapon restrictions. There are no starter weapons or victory equipment drops in 1.5.0. Previously saved gear is retained.

Icons do not replace weapons or clothing baked into character animations. If a selected icon is missing, the game uses its medieval UI slot icon instead. Asset tests require all 27 selected files.

## Audio

Extract `WAV/`, `README.txt`, `LICENSE.txt`, and `manifest.csv` from the locally supplied **NimbeKit_Fantasy_RPG_SFX_160.zip** into `assets/packs/NimbeKit_Fantasy_RPG_SFX_160/`. For example, the sword cue lives at `WAV/combat/fr_combat_sword_swing.wav`. The loader converts the pack's 24-bit PCM to 16-bit PCM for Java playback. OGG files and the demo MP3 are not used. The pack and its documents remain excluded from Git.

The pack contains no music tracks. `GameAudio.java` generates original minor-key music: a 16-second dungeon loop and a faster 12-second boss loop. The pack's crypt-air loop plays quietly underneath as ambience. Music crossfades at encounter changes. Separate music and effects/ambience volume settings include zero-volume mute.

| Character or creature | Assigned attack sound |
| --- | --- |
| Wizard | Ember casting and rune impact |
| Priest | Restoration for healing, sunburst for attacks |
| Knight, Knight Templar, Swordsman | Sword swing and target-dependent impact |
| Lancer, Soldier | Spear thrust and target-dependent impact |
| Archer | Arrow release and hard/soft target impact |
| Armored Axeman | Heavy attack and target-dependent impact |
| Skeleton, Armored Skeleton, Greatsword Skeleton | Sword swing |
| Skeleton Archer | Arrow release |
| Orc, Armored Orc, Elite Orc, Orc rider | Heavy attack |
| Slime, Blood-Monster_A/B | Bubbling magic texture |
| Lava Slime, Ghostfire, Flame-Golem | Fire casting |
| Bat, Hellbat | Wing rush |
| Werewolf, Werebear, Hellhound | Physical hit |
| Demon_A–E, Demoness_A/B | Dark magic pulse |
| Eyeball Monster | Crystal beam |
| Necromancer, Warlock | Shadow magic |
| Minotaur | Heavy attack |
| Black-Knight_A/B/C | Sword swing |

The five bosses use their corresponding creature mappings: Elite Orc and Minotaur use heavy attacks, Necromancer uses shadow magic, Black Knight C uses sword attacks, and Flame Golem uses fire. Boss entry also uses a shared distant-roar texture. Victory, regrouping, and successful management actions have feedback cues.

**Limitations:** no hero or enemy is left without an attack cue, but these are shared effects rather than unique voice recordings. The pack does not provide dedicated skeleton rattles, orc grunts, wolf/bear/hound calls, bat screeches, demon voices, blood-monster voices, eyeball voices, minotaur calls, golem voices, or unique boss vocals. Slimes and Blood Monsters share a bubbling spell texture. Soft arrow impacts use the pack's wood-hit recording as an approximation. Characters and enemies do not yet have individual death or hurt vocalizations. All bosses share the same music theme, not separate compositions.

If audio is missing or the computer has no usable mixer, playback failures are reported once per unavailable cue and the game continues. Tests decode all assigned effects and check timing, duplicate suppression, and music transitions without opening speakers.

## Check the installation

From the project root:

```powershell
.\run.cmd test
```

The tests check character mappings, action sheets, scenery, UI rendering, all 27 equipment icons, audio decoding and timing, and gameplay behavior including camp recovery and guide dismissal. Audio tests run silently. Generated previews appear in `out/` and remain local.

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
