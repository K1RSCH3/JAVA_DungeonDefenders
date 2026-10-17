# Dungeon Defenders

**Current version: V1 (1.0.0)** — the first playable version. See the [V1 release notes](CHANGELOG.md) for the feature baseline.

A medieval dark-fantasy idle RPG built with **Java, Swing, and Java2D**. Assemble a party of four, battle through a haunted corridor, and spend your rewards on the upgrades and equipment needed to reach the next stage.

Combat runs automatically. You choose who fights, how they grow, and what they carry.

## V1 features

- **Nine recruitable classes** with melee, ranged, magic, and healing roles
- **Four-enemy patrols** grouped by species, with a boss every 25 stages
- **Separate upgrades** for attack or spell damage, maximum HP, healing, and guard
- **Equipment management** with class-specific weapons, hats, armor, and shoes
- **Animated pixel art** with character actions, targeted combat effects, heart indicators, and scrolling scenery
- **Persistent progress** with automatic saving and up to eight hours of offline gold
- **Recoverable defeats** that retry the current stage without taking away your gold, gear, or progress

## Before you run

You need **JDK 11 or newer** and the required assets installed locally. The game uses standard Java libraries and does not require Maven or Gradle.

**Asset packs are not included in this repository.** The project uses paid character packs, and all third-party artwork, fonts, source archives, and generated artwork previews are excluded from Git. Obtain the assets from their creators using the links below, then follow the [local asset setup guide](assets/README.md).

A fresh clone contains the source code, but the complete GUI and asset-dependent tests require the local packs. In particular, missing required UI images prevent the interface from loading.

## Run the game

From the folder containing this repository, open PowerShell and run:

```powershell
cd JAVA_DungeonDefenders
.\run.cmd
```

If your terminal is already inside `JAVA_DungeonDefenders`, run only `.\run.cmd`.

The launcher compiles the source into `out/` and starts the game. It uses `JAVA_HOME` when set, otherwise `javac` and `java` must be available on `PATH`. Launchers resolve their own folder, so they also work when called by their full path.

To compile and launch manually from the project folder:

```powershell
New-Item -ItemType Directory -Force out
javac --release 11 -d out src\idle\*.java
java -cp out idle.IdleGame
```

## Playing

Start a **New Expedition**, or choose **Continue Expedition** to resume a save and collect offline gold. New expeditions begin with a Knight, Wizard, Priest, and Archer, plus 120 gold.

Use the management panel beside the battlefield:

| Tab | What to do |
| --- | --- |
| **Party** | Select a hero, recruit another class, or swap members between camp and your active party |
| **Upgrades** | Improve the selected hero's damage, vitality, guard, or priest healing |
| **Shop** | Spend gold on equipment for the shared stash |
| **Gear** | Equip compatible items or return equipped items to the stash |

Up to four heroes can fight at once, and at least one must stay in the party. Upgrade previews show the next improvement and its cost. Equipment changes stats, but does not change the character's sprite.

**Expand battlefield** hides the management panel. **Show party & gear** brings it back. Settings include reduced motion and damage/healing number controls.

### Classes and equipment

Each hero has four equipment slots: **weapon, hat, armor, and shoes**. Hats, armor, and shoes can be worn by every class.

| Class | Weapons | Role |
| --- | --- | --- |
| Lancer | Spear | Melee damage |
| Wizard | Staff, wand | Magic damage |
| Priest | Staff, wand | Healing and supporting attacks |
| Archer | Bow | Ranged damage |
| Soldier | Spear | Melee damage |
| Knight | Sword | Front-line guard |
| Knight Templar | Sword | Front-line guard |
| Swordsman | Sword | Melee damage |
| Armored Axeman | Axe | Melee damage |

### Progression and recovery

Enemy health and damage increase with the stage. Normal patrols contain four enemies of the same species, while every 25th stage introduces a boss. Victories award gold, every third victory drops equipment, and bosses guarantee an equipment drop.

If the party is defeated, it recovers and retries the same stage after a short regrouping period. Enemies also recover to full health. Improve your party to get past a difficult encounter without losing the progress you have already earned.

Offline rewards provide gold for up to eight hours away. They do not advance stages or award equipment.

## Asset credits and sources

The visual assets and font come from the following sources. They are obtained separately and are not redistributed with this repository.

| Used for | Asset | Creator / source |
| --- | --- | --- |
| Characters and enemies | [Tiny RPG Character Asset Pack 01 V2.0](https://zerie.itch.io/tiny-rpg-character-asset-pack) | Zerie |
| Characters and enemies | [Tiny RPG Character Asset Pack 02](https://zerie.itch.io/tiny-rpg-character-asset-pack-02) | Zerie |
| Background | [Gothicvania Cold Corridors](https://ansimuz.itch.io/gothicvania-cold-corridors) | Ansimuz |
| User interface | [Free Medieval Fantasy UI Pack](https://kibyra.itch.io/free-medieval-fantasy-ui-pack) | Kibyra |
| Font | [Pixelta](https://www.dafont.com/pixelta.font) | DaFont listing |

The character mappings use the **full 22-character and 20-character packs**. The free sample versions do not cover the complete roster. Consult each creator's asset page and included license for usage terms.

See [assets/README.md](assets/README.md) for folder names, animation integration, and custom sprite overrides.

## Saves and local files

Progress is saved automatically every 15 seconds, after purchases and party changes, and when the game closes normally.

| File | Purpose |
| --- | --- |
| `ashen-save.properties` | Current campaign |
| `ashen-save-backup-*.properties` | Backups made before replacing an existing campaign |
| `settings.properties` | Display and animation preferences |
| `out/` | Compiled classes and generated test previews |

The `ashen-` save names are retained for compatibility with earlier versions of the project. Keep them with the game when renaming its folder. These local files are excluded from Git.

To start over, use **New Expedition**. To reset manually, close the game and move `ashen-save.properties` somewhere safe. The older `save.properties` format is not imported.

## Development

| Source file | Responsibility |
| --- | --- |
| `Catalog.java` | Classes, weapons, gear, and enemy definitions |
| `GameState.java` | Combat, upgrades, inventory, rewards, and progression |
| `Encounters.java` | Patrol composition, bosses, and creature sizes |
| `SaveStore.java` | Save validation, migration, and file locking |
| `AssetPack.java` | Animation sheets and background layers |
| `PixelArt.java` | Sprite overrides, portraits, and placeholder art |
| `BattlePanel.java` | Battlefield rendering and combat animation |
| `IdleGame.java` | Main window and party management |
| `MainMenu.java` | Menu, settings, and campaign selection |
| `UiSkin.java` | Font, frames, icons, and interface controls |

Source files are in `src/idle/`, and tests are in `tests/idle/`.

With the local assets installed, run:

```powershell
.\run.cmd test
```

Checks cover combat, upgrades, equipment, encounters, saves, offline rewards, asset mappings, and rendering. Generated previews are written to `out/`.
