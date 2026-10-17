# Sprite integration

**Local asset setup only:** the paid packs are not included in this repository. All third-party artwork and fonts are kept out of Git. Pack references and purchase links will be added later. The paths and preserved files described below refer to the developer's local installation, not files supplied with a clone. The complete GUI and asset-dependent tests require these assets locally.

## Font and interface skin

The supplied **Pixelta** font is loaded from `fonts/Pixelta.ttf` and registered with Java for both plain and HTML UI labels. No OS font installation is needed. Its bundled information PDF is preserved beside it.

The interface now exclusively loads **Free Medieval Fantasy UI Pack** by **Kibyra** from `packs/Free_Medieval_Fantasy_UI_Pack/`. The previous UI Essential Pack is no longer referenced by the renderer; its preserved source files are unused.

Selected assets include wood-and-iron panels (`UI Icons 2/icon58`), plank buttons (`icon51`), reinforced selection frames (`icon14`), inset borders (`icon12`), parchment notices (`icon25`), and wooden scrollbar handles (`icon59`). The other icon sheets supply crossed swords, navigation, settings, scrolls, gold, armor, weapon types, boots, forging, and checkbox states. These appear throughout the menu, party list, equipment slots, stash dropdowns, forge, dialogs, and health-bar frames.

The pack contains static images; the game supplies hover fades, press movement, disabled dimming, and keyboard focus feedback. Frames use nine-slice rendering to preserve corners, with original wood/metal colors and nearest-neighbor image scaling. Source PNGs remain unmodified. Pixelta remains the interface font.

The original pack `Readme.txt` is preserved, and Credits names Kibyra. That document permits personal and commercial use and prohibits redistribution/resale of the raw pack as-is; keep it with your source assets when preparing a release.

Dungeon Defenders now uses the supplied Tiny RPG and Cold Corridors packs, extracted under `packs/` with their original filenames. The source ZIPs in Downloads were left untouched. Source art, Aseprite files, and bundled documents are preserved; macOS metadata files were omitted.

## Installed packs

| Pack | Game mapping |
| --- | --- |
| Tiny RPG Character Asset Pack 01 v2.0 - Full 22 Characters | All 9 playable classes and 13 enemies |
| Tiny RPG Character Asset Pack 02 - Full 20 Characters | All remaining 20 enemies |
| Cold Corridors Files | Scrolling corridor layers, floor tiles, animated torches |

The full archives replace the free packs in the runtime mapping and cover **all 42 characters: 9 playable classes and 33 enemies**. The older free files remain preserved, but are no longer selected. Existing campaigns and starting parties are unchanged. The ZIP originals in Downloads remain untouched.

`AssetPack.java` loads the split 100 x 100 action sheets directly. It uses opaque bounds from the idle frame to give allies a consistent 40-pixel visible idle height and enemies creature-specific heights and a shared foot anchor. Width follows the original aspect ratio, so long weapons no longer shrink their wielder. This scale remains fixed across actions to prevent size changes during attacks. The original files are never cropped or rewritten. Enemies are mirrored to face the party. Mappings support `Walk`, `Walk01`, and `Flying`, plus `Attack`/`Attack01` variants. Flying creatures use their flight sheet for both idle and movement. Priest healing and available embedded magic-effect attack sheets are used; every fifth combat tick selects the second attack variant where available.

On entering the game, the party walks into its first encounter. After a victory, the defeated enemy plays its death animation for about half a second, followed by roughly 1.5 seconds of walking toward the next enemy. Combat pauses during these visual transitions; saving continues. Living allies use walk animations and the incoming enemy walks or flies into view. Wounded allies use hurt animations, and fallen allies hold their death pose until revived. These travel phases add a short delay between battles; offline earnings remain an estimate based on party power.

Cold Corridors uses its `far`, `back`, `middle`, and `near` layers, plus `tileset.png` and `torch-sheet.png`. During travel, these layers scroll at different speeds for parallax; the floor moves at the party's travel speed and torches stay attached to the near wall. Movement stops during combat and rest, while flames keep animating. Layers repeat at their native pixel width, with a vertical crop into the game's 384 x 216 scene. The large foreground pillars are omitted to keep the combatants visible. Missing pack assets fall back to the original scene or character placeholders.

All shipped pack documents are retained, including `packs/Cold Corridors Files/public-license.pdf`. The rendered sprite weapons are part of the supplied art; equipping gear changes stats, not the artwork.

## Adding further sprites

Put transparent PNGs in `characters/` and `enemies/`, using the exact lowercase filenames below. These overrides take precedence over the installed pack mappings. Restart the game after replacing a file; images are cached during a session. Missing or unreadable override images fall back to the built-in placeholders.

## Simple override image format

- A single character image can be any reasonable size; 32 x 40 or 48 x 48 pixels works well.
- Animated sheets must have **one horizontal row of square frames**, with frame width equal to image height. For example, a 192 x 48 image contains four 48 x 48 frames.
- Widths that are exact multiples of height, with at least two frames, are interpreted as animation sheets automatically. Avoid that aspect ratio for single-frame art.
- Single-strip overrides reuse the same sheet for each action. The imported packs have separate action sheets; custom layouts need a mapping in `AssetPack.java`.
- Allies should face right and enemies left. Keep consistent foot positions inside frames. Transparent margins affect the apparent size.
- Simple PNG overrides fit their display box; allies use a 40-pixel visible height; bats use 19, slimes 23, standard enemies 36, larger creatures 46, and bosses 66. Portraits have wider canvases to accommodate weapons. The 384 x 216 scene is fitted to the available battlefield area with nearest-neighbor sampling.
- Different sheet layouts or dedicated action animations will need an importer once your sprite files are available.

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

Enemies share several procedural placeholder body types until their PNGs are supplied. Their names cycle in the game's encounter catalog; health and damage scale with stage. Enemy-specific skills are not implemented yet.
