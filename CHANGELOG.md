# Changelog

## 1.20.1-15.1.0 / 1.21.1-19.1.0
- Updated both Minecraft versions to official TaCZ 1.2.0. The official NeoForge TaCZ build is currently marked beta.
- Added dedicated single-block TaCZ Molecular Assemblers for guns, ammunition, and attachments, including acceleration upgrades and workbench-specific rendering.
- Added standalone Extended assemblers with eight concurrent crafting jobs and faster per-job processing.
- Added the TaCZ Pattern Encoding Terminal with workbench tabs, recipe selection, JEI transfer, and encoded-pattern restoration.
- Kept workbench tabs clear of AE2 view-cell panels and addon terminal upgrade panels.
- Fixed JEI pattern transfers being hidden by a client-side menu validity check while retaining server-side permission and recipe validation.
- Removed the old multi-block assemblers and their models. Existing large assemblers are not migrated; current single-block normal and Extended assemblers retain their saved data. Single-block universal variants remain available through commands only.

## 1.21.1-19.0.2
- Switched the Minecraft 1.21.1 dependency to the official TaCZ 1.2.0 NeoForge release.
- Fixed recipe selection not being restored when returning from AE2 ingredient autocrafting with TaCZ's held-item filter enabled.
- This release does not include the unreleased TaCZ Molecular Assembler or related assembler features.

## Features
- port 1.21.1 with [\[UNOFFICIAL\] TaCZ NeoForge Port](https://www.curseforge.com/minecraft/mc-mods/tacz-1-21-1)
- Jade indicate AE network status for AppliedTacz Workbench
- Request AutoCrafting in AppliedTacz Screen
