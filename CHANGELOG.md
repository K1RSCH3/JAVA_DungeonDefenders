# Changelog

## V1.5 — 1.5.0

Equipment, audio, camp recovery, and guidance update, recorded on October 19, 2026. These changes are all part of V1.5.

### Equipment

- Added 27 selected Raven icons for weapons, helms, armor, and boots
- Displayed equipment icons in Shop, Gear slots, and shared inventory choices without changing character sprites
- Matched Iron, Gold, and Crystal visuals to increasing equipment bonuses and prices
- Made new equipment shop-only, removing starter weapons and patrol/boss item drops
- New campaigns begin with 120 gold and empty equipment slots. Purchases enter the shared inventory and must be equipped manually
- Preserved existing gear, saved item IDs, and class-specific weapon restrictions

### Audio

- Added NimbeKit combat, healing, enemy, victory, regrouping, and management effects
- Covered all nine heroes and 33 enemy types with attack cues, using shared effects where dedicated creature recordings are unavailable
- Synchronized attack and impact cues with combat animations and limited duplicate sounds
- Added original procedural dungeon and boss music with crossfades and quiet crypt ambience
- Added saved music and effects/ambience volume controls, with mute at zero
- Kept audio optional at runtime so missing sound files or unavailable audio devices do not stop gameplay
- Documented shared creature effects and missing dedicated voices in the asset guide

### Camp and party

- Downed heroes automatically return to camp and heal over 30 seconds, freeing party slots
- Injured heroes sent to camp manually also recover there
- Recruits and recovered heroes wait for the player to add them to the party
- Full-party defeat on a normal stage pauses there. A boss wipe retreats five stages to earn upgrade gold, retaining gold, equipment, and upgrades
- Surviving heroes continue fighting. After a full defeat, enemies reset to full HP at the retry stage and the player must deploy a ready hero to resume
- Recovery continues offline and is stored in save format 4, with support for older save formats 1–3
- Kept the game version at 1.5.0, independent of the internal save-format number

### Interface and checks

- Added a portrait hero selector available on every management tab, distinct page headings, framed equipment/stat cards, and a compatible-equipment Shop filter
- Reduced stage 25 boss HP from 2,180 to 1,090, base damage from 71.25 to 30.875, and the heavy-hit multiplier from 1.7 to 1.3
- Preserved existing first-boss save health percentages and verified a starting party with Iron gear and rank-two upgrades can clear the encounter
- Changed recruitment, shopping, and equipment guides into action-driven sequences with next-control highlights and dismissal at every step
- Added regrouping guidance for ready replacements, recovery countdowns, full-party swaps, and manual redeployment, including empty-party saves
- Guided players back to hidden management controls and added regression checks for navigation, recruitment, deployment, recovery, and dismissal
- Corrected the Lancer's body scale to account for his upright spear, with consistent action scaling and portraits that retain the full weapon
- Refreshed the 1.5.0 documentation screenshots for Play, named expeditions, party management, Raven equipment, upgrades, combat, and bosses
- Added current camp-recovery and closable equipment-guide screenshots in place of the old defeat-flow example
- Replaced the main menu's continue/new actions with Play and a framed expedition library using medieval UI assets
- Added independent named campaigns, stage/gold/party summaries, and left-side options for renaming or confirmed deletion
- Preserved the original campaign in place as Existing Expedition, kept offline timestamps unchanged during renaming, and excluded the new saves folder from Git
- Added campaign lifecycle checks for separate saves, duplicate and blank names, renaming, deletion, and legacy loading
- Added closable recruitment, shopping, and equipment guides with golden next-step highlights
- Guided manual deployment and compatible equipment selection. Closing a guide clears its highlights, and reduced motion uses a steady highlight
- Added rescue and recovery notices, countdowns, and deployment restrictions during recovery
- Preserved departing heroes' death animations while camp recovery begins
- Added checks for equipment progression, audio timing, boss music transitions, camp saves, offline healing, manual deployment, and guide dismissal

Raw third-party assets remain local and excluded from Git. This version record does not publish a GitHub release.

## V1 — 1.0.0

The first playable version of Dungeon Defenders established the core party, combat, equipment, and progression systems. This is a historical baseline. See V1.5 above and the README for current behavior.

### Overview

Dungeon Defenders is a medieval dark-fantasy idle RPG built with Java, Swing, and Java2D. Recruit a party of four adventurers, watch them battle automatically, and use their rewards to improve their abilities and equipment. Animated pixel characters fight through a scrolling gothic corridor, facing stronger enemies and recurring bosses as stages advance.

### Features

- **Party building:** recruit from nine classes — Lancer, Wizard, Priest, Archer, Soldier, Knight, Knight Templar, Swordsman, and Armored Axeman. Swap heroes between camp and a four-member active party.
- **Automatic combat:** melee fighters, archers, casters, and healers act with role-based timing and effects directed at their targets.
- **Enemy encounters:** fight four-enemy patrols grouped by species, with stage-scaled health and damage. The roster includes skeletons, orcs, demons, beasts, and other creatures.
- **Boss stages:** face a named boss every 25 stages with stronger stats. The original equipment rewards were removed by the shop-only equipment update listed above.
- **Individual upgrades:** improve attack or spell damage, maximum HP, guard, and priest healing through separate upgrade tracks.
- **Equipment and shopping:** buy and manage class-compatible weapons, hats, armor, and shoes using a shared inventory. Equipment improves stats without changing sprite appearances.
- **Original defeat recovery:** V1 regrouped and retried with the defeated party. Version 1.5.0 replaces this with timed camp recovery and manual redeployment.
- **Offline rewards:** collect up to eight hours of offline gold. Offline time does not clear stages or grant equipment.
- **Pixel presentation:** animated character actions, scrolling background layers, torch animations, targeted combat effects, and stepped heart indicators.
- **Desktop interface:** a main menu, selected-hero portraits, party management, upgrade previews, equipment controls, and an expandable battlefield.
- **Settings and saves:** reduced-motion and combat-number options, automatic campaign saving, and backups before replacing an existing campaign.

### Requirements and scope

- Requires JDK 11 or newer and locally installed asset packs
- Third-party artwork and fonts are not included in the repository, with sources credited in the README
- Includes gameplay, persistence, encounter, and asset/rendering checks runnable with `run.cmd test`
- This section records the historical 1.0.0 baseline, not the current version or a published GitHub release
