# Changelog

## V1 — 1.0.0

The first playable version of Dungeon Defenders establishes the core party, combat, equipment, and progression systems. This entry records the current feature baseline for future revisions.

### Overview

Dungeon Defenders is a medieval dark-fantasy idle RPG built with Java, Swing, and Java2D. Recruit a party of four adventurers, watch them battle automatically, and use their rewards to improve their abilities and equipment. Animated pixel characters fight through a scrolling gothic corridor, facing stronger enemies and recurring bosses as stages advance.

### Features

- **Party building:** recruit from nine classes — Lancer, Wizard, Priest, Archer, Soldier, Knight, Knight Templar, Swordsman, and Armored Axeman. Swap heroes between camp and a four-member active party.
- **Automatic combat:** melee fighters, archers, casters, and healers act with role-based timing and effects directed at their targets.
- **Enemy encounters:** fight four-enemy patrols grouped by species, with stage-scaled health and damage. The roster includes skeletons, orcs, demons, beasts, and other creatures.
- **Boss stages:** face a named boss every 25 stages, with stronger stats and guaranteed equipment rewards.
- **Individual upgrades:** improve attack or spell damage, maximum HP, guard, and priest healing through separate upgrade tracks.
- **Equipment and shopping:** buy and manage class-compatible weapons, hats, armor, and shoes using a shared inventory. Equipment improves stats without changing sprite appearances.
- **Progression without permanent defeat:** defeated parties regroup and retry the current stage while retaining gold, equipment, and progress.
- **Offline rewards:** collect up to eight hours of offline gold. Offline time does not clear stages or grant equipment.
- **Pixel presentation:** animated character actions, scrolling background layers, torch animations, targeted combat effects, and stepped heart indicators.
- **Desktop interface:** a main menu, selected-hero portraits, party management, upgrade previews, equipment controls, and an expandable battlefield.
- **Settings and saves:** reduced-motion and combat-number options, automatic campaign saving, and backups before replacing an existing campaign.

### Requirements and scope

- Requires JDK 11 or newer and locally installed asset packs
- Third-party artwork and fonts are not included in the repository, with sources credited in the README
- Includes gameplay, persistence, encounter, and asset/rendering checks runnable with `run.cmd test`
- This version label documents the current work and does not indicate that a GitHub release has been published
