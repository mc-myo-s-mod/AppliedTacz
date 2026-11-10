# TaCZ pattern terminal verification

The shared screen/style is used by Forge 1.20.1 and NeoForge 1.21.1. The loader-specific
parts, menus and JEI handlers use the matching AE2 and TaCZ APIs. No EMI integration is included.

## Automated checks

From the repository root on Windows:

```powershell
./design/pattern-terminal/check-layout.ps1
cmd.exe /c "gradlew.bat :forge-1.20.1:compileJava :neoforge-1.21.1:compileJava --console=plain"
cmd.exe /c "gradlew.bat :forge-1.20.1:runGameTestServer :neoforge-1.21.1:runGameTestServer --console=plain"
cmd.exe /c "gradlew.bat :forge-1.20.1:runData :neoforge-1.21.1:runData --console=plain"
cmd.exe /c "gradlew.bat :forge-1.20.1:build :neoforge-1.21.1:build --console=plain"
```

The layout check verifies the 320 × 240 minimum viewport, three recipe rows, all 81 inputs
across 25 scrollbar positions, vertical pattern-slot alignment and both loaders' resource wiring.
It does not render Minecraft or exercise mouse input.
Both Wide (13 columns) and centered Compact (9 columns) keep the same 272 px frame and lower layout.
The checks also cover the persisted mode setting and same-screen resize path.
The encoded-slot rendering check verifies the Shift-only output-count branch and cached pattern decoding;
it is a source check, not an interactive rendering test.

`PatternEncodingGameTests` checks the 1/9/10/81/82 ingredient boundaries, full amounts above
64, accepted/rejected item alternatives and decoded processing patterns. A powered test network
also checks recipe-ID validation, ghost-slot protection, blank-pattern shift-click priority,
one-pattern consumption, occupied output preservation, output shift-click, assembler acceptance,
NBT/drop preservation and the distance check.
The exact-workbench transfer regression exercises every loaded crafting category, overlapping workbench
filters, repeated selections, actual AE2 action payload decoding, invalid/null IDs and state preservation
when the menu is denied or the player moves out of range.
Pattern restoration regressions cover normal/shift insertion, reopening, unchanged-slot caching,
exact alternative choices and amounts above 64, reordered overlapping ingredients, and invalid or
nonmatching patterns clearing stale previews without consuming or overwriting real items.

On 2026-10-14 the full GameTest suites passed: Forge 29/29 and NeoForge 30/30.
Compilation, datagen and both builds also passed. Client startup/resource loading was checked,
but the interactive checklist below remains unverified. The standard `test` tasks have no sources.

## Interactive checklist (both versions)

1. Start the matching `:forge-1.20.1:runClient` or `:neoforge-1.21.1:runClient` task.
   Obtain `appliedtacz:tacz_pattern_encoding_terminal` and attach it to a powered ME cable.
2. Open it at normal GUI scale and at a 320 × 240 scaled viewport. Check the ME inventory/search,
   right-side workbench tabs, three recipe rows and both scrollbars for overlap.
   Toggle the layout button repeatedly: only the ME grid should change between 13 and 9 columns;
   the nine-column grid stays centered. Search, recipe selection, ingredient choices and real pattern
   slots must remain intact. Reopen/restart to verify the saved mode.
3. Select and scroll recipes; select each workbench. With more than three gunpack workbenches,
   check tab arrows/wheel and JEI transfer to an initially hidden workbench.
   Scroll the current recipe and current workbench tab out of view, then transfer the same recipe again;
   both must return into view. Transfer the same recipe from another eligible workbench category and
   verify that exact source tab is selected.
4. Use a recipe with more than nine ingredient entries. Scroll to the final entries, then reopen
   the screen: the grid must reset without shifting its layout. Click an ingredient with multiple
   accepted items and inspect its full amount tooltip.
5. Register an ingredient's crafting pattern in the ME network. Its preview should show `+`.
   In JEI, hover the transfer button: craftable input alternatives should be blue; other inputs
   should remain unhighlighted. The button must work without stored recipe ingredients.
6. Transfer a recipe, insert blank patterns and encode. Exactly one blank pattern is consumed;
   ingredients stay in storage. Another encode must not overwrite the occupied result slot.
   The encoded slot should show no number normally; hold Shift to see the result amount, including
   outputs of one and above 64. Release Shift to hide it again. Ingredient and blank counts stay unchanged.
   Drag-distribute patterns and test touchscreen pickup: native drag feedback is retained, with the
   Shift output-count label temporarily omitted while dragging.
   Take the pattern out, select a different recipe, then put it back: the matching workbench tab,
   recipe row and encoded ingredient choices must return. Repeat with shift-click insertion and by
   reopening the terminal while the pattern is still inside. Viewing must not consume blanks or patterns.
7. Insert the processing pattern into a compatible TaCZ assembler and request its output from
   the ME network. Check the output and quantities, then save/reload and repeat.

## GUI dummy image

[wide-compact-mockup.png](wide-compact-mockup.png) is an imagegen-created layout illustration, not a
captured game screen or a GUI texture. Its generation prompt is in [mockup-prompt.md](mockup-prompt.md).
