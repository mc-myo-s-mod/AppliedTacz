$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$common = Join-Path $repo 'common'
$screenPath = Join-Path $common 'src/main/java/me/myogoo/appliedtacz/client/TaCZPatternEncodingScreen.java'
$stylePath = Join-Path $common 'src/main/resources/assets/ae2/screens/appliedtacz_pattern_encoding_terminal.json'
$screenSource = Get-Content -Raw $screenPath
$style = Get-Content -Raw $stylePath | ConvertFrom-Json
$viewportWidth = 320
$viewportHeight = 240

function Assert-That([bool] $condition, [string] $message) {
    if (-not $condition) {
        throw $message
    }
}

function Read-Text([string] $path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing required file: $path"
    }
    return Get-Content -Raw -LiteralPath $path
}

function Assert-Json([string] $path) {
    $json = Read-Text $path | ConvertFrom-Json
    return $json
}

Assert-That ($style.terminalStyle.slotsPerRow -eq 13) 'Cached screen style must retain the 13-column wide default.'
Assert-That ($screenSource -match 'private static final int WIDTH\s*=\s*(\d+)\s*;') 'Screen width constant is missing.'
$screenWidth = [int]$Matches[1]
Assert-That ($screenWidth -gt 0 -and $screenWidth -le $viewportWidth) "Screen width does not fit a ${viewportWidth}px viewport: $screenWidth."
Assert-That ($screenWidth -eq 272) 'Compact and wide modes must retain the same 272px overall screen width.'
$minimumGridRows = 2
$screenHeight = [int]$style.terminalStyle.header.srcRect[3] + 18 * $minimumGridRows + [int]$style.terminalStyle.bottom.srcRect[3]
Assert-That ($screenHeight -gt 0 -and $screenHeight -le $viewportHeight) `
    "Style canvas must fit a ${viewportHeight}px viewport: $screenHeight."
$screenOffsetX = [int][Math]::Floor(($viewportWidth - $screenWidth) / 2)
$screenOffsetY = [int][Math]::Floor(($viewportHeight - $screenHeight) / 2)
Assert-That ($screenSource -match 'private static final int VISIBLE_RECIPES\s*=\s*3\s*;') 'Screen must show three recipe rows.'
Assert-That ($screenSource -match '(?s)public void renderSlot\(GuiGraphics graphics, Slot slot\)\s*\{\s*if \(menu\.getSlotSemantic\(slot\) == SlotSemantics\.ENCODED_PATTERN\).*?graphics\.renderItem\(pattern, slot\.x, slot\.y\);\s*if \(hasShiftDown\(\)\).*?patternOutput\.amount\(\).*?return;\s*\}\s*super\.renderSlot') `
    'Encoded slot must draw its output quantity only while Shift is held and bypass the normal pattern-count decoration.'
Assert-That ($screenSource -match '(?s)if \(!ItemStack\.matches\(pattern, displayedPattern\)\)\s*\{\s*displayedPattern = pattern\.copy\(\);\s*var details = PatternDetailsHelper\.decodePattern\(pattern, minecraft\.level\);') `
    'Output quantity decoding must be cached by pattern content, without changing the real slot stack.'
Assert-That ($screenSource -match '(?s)if \(isQuickCrafting \|\| \(minecraft\.options\.touchscreen\(\)\.get\(\).*?isLeftPressed\(\).*?isRightPressed\(\).*?super\.renderSlot\(graphics, slot\);\s*return;') `
    'Encoded slot must preserve vanilla quick-craft and touchscreen pickup previews while dragging.'
Assert-That ($screenSource -match 'super\(menu,\s*inventory,\s*title,\s*style\)' `
        -and $screenSource -match 'terminalLayout\s*=\s*getStyle\(\)\.getTerminalStyle\(\)') `
    'Terminal screen must use its dedicated cached style directly for the client-global column preference.'
Assert-That ($screenSource -match 'terminalLayout\.setSlotsPerRow\(AppliedTaczClientConfig\.compactPatternTerminal\(\)\s*\?\s*9\s*:\s*13\)') `
    'Client compact-layout preference must choose 9 columns, with 13 columns as the default.'
Assert-That ($screenSource -match '(?s)public void init\(\)\s*\{\s*repo\.setRowSize\(terminalLayout\.getSlotsPerRow\(\)\);\s*repo\.updateView\(\);\s*super\.init\(\);') `
    'Screen initialization must update the repository row size before AE2 creates its ME slots.'
Assert-That ($screenSource -match 'int offset\s*=\s*\(13\s*-\s*terminalLayout\.getSlotsPerRow\(\)\)\s*\*\s*9') `
    'Compact grid columns must be centered using the per-slot horizontal offset.'
Assert-That ($screenSource -match 'menu\.slots\.replaceAll\(slot\s*->\s*slot instanceof RepoSlot repoSlot\s*\?\s*new RepoSlot\(repo,\s*repoSlot\.getRepoViewIndex\(\),\s*slot\.x\s*\+\s*offset,\s*slot\.y\)\s*:\s*slot\)') `
    'Only AE2 repository slots should be horizontally shifted; workbench and player slots must stay fixed.'
Assert-That ($screenSource -match '(?s)private void setCompactLayout\(boolean compact\)\s*\{\s*AppliedTaczClientConfig\.setCompactPatternTerminal\(compact\);\s*terminalLayout\.setSlotsPerRow\(compact\s*\?\s*9\s*:\s*13\);\s*layoutToggle\.setState\(compact\);\s*resize\(minecraft,\s*width,\s*height\);') `
    'Layout toggle must persist the preference and rebuild this screen in place.'

$wideColumns = 13
$compactColumns = 9
$slotPitch = 18
$nativeGridLeft = 8
$nativeGridWidth = $wideColumns * $slotPitch
$compactOffset = ($wideColumns - $compactColumns) * 9
$wideStart = $nativeGridLeft
$compactStart = $nativeGridLeft + $compactOffset
$wideGridWidth = $wideColumns * $slotPitch
$compactGridWidth = $compactColumns * $slotPitch
Assert-That ($compactOffset -eq 36 -and $compactStart -eq 44 -and $wideStart -eq 8) `
    'ME slot coordinates must use x=8 for wide and x=44 for centered compact mode.'
Assert-That (($wideStart + $wideGridWidth / 2) -eq ($compactStart + $compactGridWidth / 2)) `
    'Compact and wide ME grids must share the same horizontal center.'
foreach ($mode in @(
        @{ Name = 'wide'; Columns = $wideColumns; Start = $wideStart; Width = $wideGridWidth },
        @{ Name = 'compact'; Columns = $compactColumns; Start = $compactStart; Width = $compactGridWidth }
    )) {
    $positions = @(0..($mode.Columns - 1) | ForEach-Object { $mode.Start + $_ * $slotPitch })
    Assert-That ($positions.Count -eq $mode.Columns) "$($mode.Name) ME grid has the wrong number of columns."
    Assert-That (($positions | Select-Object -Unique).Count -eq $mode.Columns) "$($mode.Name) ME slot positions overlap."
    Assert-That ($positions[0] -ge $nativeGridLeft -and $positions[-1] + 16 -le $nativeGridLeft + $nativeGridWidth) `
        "$($mode.Name) ME slots extend outside the unchanged terminal grid frame."
    Assert-That ($mode.Width -eq $mode.Columns * $slotPitch) "$($mode.Name) ME grid width does not match its column count."
}

$recipeRows = @('recipe0', 'recipe1', 'recipe2') | ForEach-Object { $style.widgets.$_ }
Assert-That ($recipeRows.Count -eq 3) 'Expected exactly three recipe row widgets.'
$left = @()
foreach ($row in $recipeRows) {
    $left += [pscustomobject]@{
        X = [int]$row.left
        Top = [int]$row.bottom
        Width = [int]$row.width
        Height = [int]$row.height
    }
}
$invalidLeftRows = @($left | Where-Object { $_.X -lt 0 -or $_.Width -lt 120 -or $_.Height -le 0 })
Assert-That ($invalidLeftRows.Count -eq 0) `
    'Recipe rows must remain wide, valid-height rows on the left.'
Assert-That (@($left | Where-Object { $_.X -ne 8 -or $_.Width -ne 124 -or $_.Height -ne 18 }).Count -eq 0) `
    'Recipe row positions and widths must remain unchanged between ME layout modes.'
foreach ($row in $left) {
    $top = $screenHeight - $row.Top
    Assert-That ($screenOffsetX + $row.X -ge 0 -and $screenOffsetX + $row.X + $row.Width -le $viewportWidth) `
        'A recipe row is clipped horizontally in a 320px viewport.'
    Assert-That ($screenOffsetY + $top -ge 0 -and $screenOffsetY + $top + $row.Height -le $viewportHeight) `
        'A recipe row is clipped vertically in a 240px viewport.'
}
$orderedRows = @($left | Sort-Object Top -Descending)
for ($i = 1; $i -lt $orderedRows.Count; $i++) {
    $gap = $orderedRows[$i - 1].Top - $orderedRows[$i].Top
    Assert-That ($gap -ge $orderedRows[$i - 1].Height) 'Recipe row hitboxes overlap vertically.'
}
$leftEdge = ($left | ForEach-Object { $_.X + $_.Width } | Measure-Object -Maximum).Maximum
$grid = $style.slots.PROCESSING_INPUTS
Assert-That ($leftEdge -lt [int]$grid.left) 'Recipe rows overlap the right-side ingredient grid.'

$columns = 3
$slotCount = 81
$visibleRows = 3
$gridScroll = 24
Assert-That ($slotCount % $columns -eq 0) '81 ingredient slots must divide into complete three-column rows.'
Assert-That ($screenSource -match 'ingredientScroll\.setRange\(0,\s*24,\s*3\)') 'Ingredient scrollbar no longer spans the expected 25 row positions.'
Assert-That ($screenSource -match 'ingredientBottom\s*=\s*ingredientLayout\.getBottom\(\)') 'Ingredient layout base bottom must come from the selected style.'
Assert-That ($screenSource -match 'ingredientLayout\.setBottom\(ingredientBottom\s*\+\s*ingredientScroll\.getCurrentScroll\(\)\s*\*\s*18\)') `
    'Ingredient grid must reposition from its captured style bottom in 18px row steps.'
Assert-That ($screenSource -match '(?s)finally\s*\{\s*(?://[^\r\n]*\s*)?ingredientLayout\.setBottom\(ingredientBottom\)') `
    'Ingredient scrolling must restore the per-screen base layout after repositioning.'
Assert-That ($screenSource -match 'int row\s*=\s*i\s*/\s*3\s*-\s*ingredientScroll\.getCurrentScroll\(\)') `
    'Ingredient visibility must map slots to three-column rows and subtract the scroll position.'
Assert-That ($screenSource -match 'slots\[i\]\.setActive\(row\s*>=\s*0\s*&&\s*row\s*<\s*3\)') `
    'Only the three visible ingredient rows should remain active.'

$seen = [System.Collections.Generic.HashSet[int]]::new()
$gridX = [int]$grid.left
$gridBottom = [int]$grid.bottom
for ($scroll = 0; $scroll -le $gridScroll; $scroll++) {
    $visible = 0
    $visibleSlots = @()
    for ($slot = 0; $slot -lt $slotCount; $slot++) {
        $row = [int][Math]::Floor($slot / $columns)
        $column = $slot % $columns
        $displayRow = $row - $scroll
        if ($displayRow -ge 0 -and $displayRow -lt $visibleRows) {
            $visible++
            [void]$seen.Add($slot)
            $x = $gridX + 18 * $column
            $slotTop = $screenHeight - ($gridBottom + 18 * $scroll) + 18 * $row
            Assert-That ($x -ge $gridX -and $x + 16 -le $gridX + 18 * $columns - 2) `
                "Visible slot $slot at scroll $scroll leaves the three-column grid horizontally."
            Assert-That ($slotTop -ge $screenHeight - $gridBottom -and $slotTop + 16 -le $screenHeight - $gridBottom + 18 * $visibleRows - 2) `
                "Visible slot $slot at scroll $scroll leaves the three-row viewport."
            Assert-That ($screenOffsetY + $slotTop -ge 0 -and $screenOffsetY + $slotTop + 16 -le $viewportHeight) `
                "Visible slot $slot at scroll $scroll is clipped by a 320x240 viewport."
            $visibleSlots += [pscustomobject]@{ Index = $slot; X = $x; Y = $slotTop }
        }
    }
    Assert-That ($visible -eq 9) "Expected nine visible ingredient slots at scroll position $scroll; found $visible."
    for ($i = 0; $i -lt $visibleSlots.Count; $i++) {
        for ($j = $i + 1; $j -lt $visibleSlots.Count; $j++) {
            $a = $visibleSlots[$i]
            $b = $visibleSlots[$j]
            $overlap = $a.X -lt $b.X + 16 -and $a.X + 16 -gt $b.X `
                -and $a.Y -lt $b.Y + 16 -and $a.Y + 16 -gt $b.Y
            Assert-That (-not $overlap) "Ingredient slots $($a.Index) and $($b.Index) overlap at scroll position $scroll."
        }
    }
}
Assert-That ($seen.Count -eq $slotCount) "Scrolling reaches $($seen.Count) of 81 ingredient slots."

$blank = $style.slots.BLANK_PATTERN
$encoded = $style.slots.ENCODED_PATTERN
$gridRight = $gridX + 18 * $columns - 2
Assert-That ([int]$blank.left -gt $gridRight -and [int]$encoded.left -eq [int]$blank.left) `
    'Blank and encoded pattern slots must sit vertically to the right of the 3-column grid.'
Assert-That ([int]$blank.bottom -eq $gridBottom -and [int]$encoded.bottom -eq $gridBottom - 36) `
    'Pattern slots must align with ingredient grid rows zero and two.'
Assert-That ([int]$grid.left -eq 157 -and [int]$grid.bottom -eq 161 `
        -and [int]$blank.left -eq 245 -and [int]$encoded.left -eq 245) `
    'Compact/wide mode must not move the lower ingredient or pattern slots.'
Assert-That ($screenOffsetY + $screenHeight - [int]$blank.bottom -ge 0 `
        -and $screenOffsetY + $screenHeight - [int]$encoded.bottom + 16 -le $viewportHeight) `
    'Blank or encoded pattern slot is clipped vertically at 320x240.'
Assert-That ($screenOffsetX + [int]$blank.left + 16 -le $viewportWidth `
        -and $screenOffsetX + [int]$encoded.left + 16 -le $viewportWidth) `
    'Pattern slots extend beyond a 320px viewport.'
$tabs = $style.widgets.workbenches
Assert-That ($screenOffsetX + [int]$tabs.left -ge 0 `
        -and $screenOffsetX + [int]$tabs.left + [int]$tabs.width -le $viewportWidth) `
    'Workbench tabs extend beyond a 320px viewport.'
Assert-That ($screenOffsetY + $screenHeight - [int]$tabs.bottom -ge 0 `
        -and $screenOffsetY + $screenHeight - [int]$tabs.bottom + [int]$tabs.height -le $viewportHeight) `
    'Workbench tabs extend beyond a 240px viewport.'

$localizationSources = @($screenSource, (Read-Text $stylePath))
$menuRelativePaths = @(
    'forge-1.20.1/src/main/java/me/myogoo/appliedtacz/menu/TaCZPatternEncodingMenu.java',
    'neoforge-1.21.1/src/main/java/me/myogoo/appliedtacz/menu/TaCZPatternEncodingMenu.java'
)
foreach ($relativePath in $menuRelativePaths) {
    $localizationSources += Read-Text (Join-Path $repo $relativePath)
}
$locKeys = @($localizationSources | ForEach-Object {
    [regex]::Matches($_, '(?:item|gui)\.appliedtacz\.[a-z0-9_.]+') | ForEach-Object { $_.Value }
} | Sort-Object -Unique)
Assert-That ($locKeys.Count -gt 0) 'No terminal item or GUI localization keys were discovered.'

$parentModels = @(
    @{ Path = 'models/item/tacz_pattern_encoding_terminal.json'; Parent = 'ae2:item/pattern_encoding_terminal' },
    @{ Path = 'models/part/tacz_pattern_encoding_terminal_off.json'; Parent = 'ae2:part/pattern_encoding_terminal_off' },
    @{ Path = 'models/part/tacz_pattern_encoding_terminal_on.json'; Parent = 'ae2:part/pattern_encoding_terminal_on' }
)
$recipePath = 'src/main/resources/data/appliedtacz/recipes/tacz_pattern_encoding_terminal.json'
$loaders = @(
    @{ Name = 'Forge 1.20.1'; Root = 'forge-1.20.1'; ItemFile = 'src/main/java/me/myogoo/appliedtacz/init/AETaCZBlock.java'; MenuFile = 'src/main/java/me/myogoo/appliedtacz/init/AETaczMenu.java'; ClientFile = 'src/main/java/me/myogoo/appliedtacz/client/AppliedTaczClient.java'; Langs = @('src/main/resources/assets/appliedtacz/lang/en_us.json', 'src/main/resources/assets/appliedtacz/lang/ko_kr.json'); Recipe = $recipePath },
    @{ Name = 'NeoForge 1.21.1'; Root = 'neoforge-1.21.1'; ItemFile = 'src/main/java/me/myogoo/appliedtacz/registry/ModItems.java'; MenuFile = 'src/main/java/me/myogoo/appliedtacz/registry/ModMenus.java'; ClientFile = 'src/main/java/me/myogoo/appliedtacz/client/AppliedTaczClient.java'; Langs = @('src/main/resources/assets/appliedtacz/lang/en_us.json', 'src/main/resources/assets/appliedtacz/lang/ko_kr.json'); Recipe = 'src/main/resources/data/appliedtacz/recipe/tacz_pattern_encoding_terminal.json' }
)

foreach ($loader in $loaders) {
    $loaderRoot = Join-Path $repo $loader.Root
    $itemSource = Read-Text (Join-Path $loaderRoot $loader.ItemFile)
    $menuSource = Read-Text (Join-Path $loaderRoot $loader.MenuFile)
    $clientSource = Read-Text (Join-Path $loaderRoot $loader.ClientFile)
    $clientConfig = Read-Text (Join-Path $loaderRoot 'src/main/java/me/myogoo/appliedtacz/config/AppliedTaczClientConfig.java')
    Assert-That ($itemSource -match 'TACZ_PATTERN_ENCODING_TERMINAL' -and $itemSource -match 'tacz_pattern_encoding_terminal' `
            -and $itemSource -match 'PartItem' -and $itemSource -match 'TaCZPatternEncodingPart' `
            -and $itemSource -match 'PartModels\.registerModels') `
        "$($loader.Name): terminal item registry entry is missing."
    Assert-That ($menuSource -match 'TACZ_PATTERN_ENCODING_TERMINAL' -and $menuSource -match 'TaCZPatternEncodingMenu\.TYPE') `
        "$($loader.Name): terminal menu type registration is missing."
    Assert-That ($clientSource -match 'TaCZPatternEncodingScreen' -and $clientSource -match 'TaCZPatternEncodingMenu\.TYPE' `
            -and $clientSource -match 'StyleManager\.loadStyleDoc\("/screens/appliedtacz_pattern_encoding_terminal\.json"\)') `
        "$($loader.Name): dedicated pattern terminal style or client screen registration is missing."
    Assert-That ($clientConfig -match 'define\("compactLayout",\s*false\)' `
            -and $clientConfig -match 'compactPatternTerminal\(\)\s*\{\s*return COMPACT_LAYOUT\.get\(\);\s*\}' `
            -and $clientConfig -match '(?s)setCompactPatternTerminal\(boolean compact\)\s*\{\s*COMPACT_LAYOUT\.set\(compact\);\s*COMPACT_LAYOUT\.save\(\);') `
        "$($loader.Name): compact mode must default to wide and persist the client preference."
    foreach ($model in $parentModels) {
        $modelJson = Assert-Json (Join-Path $loaderRoot "src/main/resources/assets/appliedtacz/$($model.Path)")
        Assert-That ($modelJson.parent -eq $model.Parent) "$($loader.Name): model $($model.Path) must inherit $($model.Parent)."
    }
    $recipe = Assert-Json (Join-Path $loaderRoot $loader.Recipe)
    $ingredients = @($recipe.ingredients | ForEach-Object { $_.item } | Sort-Object)
    Assert-That ($recipe.type -eq 'minecraft:crafting_shapeless' -and $ingredients -join ',' -eq 'ae2:pattern_encoding_terminal,tacz:gun_smith_table') `
        "$($loader.Name): acquisition recipe inputs/type differ from the intended simple shapeless recipe."
    $resultId = if ($recipe.result.PSObject.Properties.Name -contains 'item') {
        $recipe.result.item
    } else {
        $recipe.result.id
    }
    Assert-That ($resultId -eq 'appliedtacz:tacz_pattern_encoding_terminal' -and $recipe.result.count -eq 1) `
        "$($loader.Name): acquisition recipe must produce one pattern encoding terminal."
    foreach ($langPath in $loader.Langs) {
        $lang = Get-Content -Raw (Join-Path $loaderRoot $langPath) | ConvertFrom-Json
        $available = @($lang.PSObject.Properties.Name)
        $missing = @($locKeys | Where-Object { $_ -notin $available })
        Assert-That ($missing.Count -eq 0) "$($loader.Name): $langPath is missing keys: $($missing -join ', ')"
    }
}

$buildFiles = @('build.gradle', 'forge-1.20.1/build.gradle', 'neoforge-1.21.1/build.gradle')
foreach ($buildFile in $buildFiles) {
    Assert-That ((Read-Text (Join-Path $repo $buildFile)) -notmatch '(?i)\bemi\b') "Unexpected EMI dependency/reference in $buildFile."
}
foreach ($version in @('forge-1.20.1', 'neoforge-1.21.1')) {
    $plugin = Read-Text (Join-Path $repo "$version/src/main/java/me/myogoo/appliedtacz/compat/jei/AppliedTaczJeiPlugin.java")
    $transfer = Read-Text (Join-Path $repo "$version/src/main/java/me/myogoo/appliedtacz/compat/jei/TaCZPatternTransferHandler.java")
    Assert-That ($plugin -match '@JeiPlugin' -and $transfer -match 'TaCZPatternEncodingMenu\.TYPE') `
        "${version}: JEI plugin or terminal transfer handler is missing."
    Assert-That ($transfer -match '(?s)if \(doTransfer\)\s*\{\s*menu\.selectRecipe\(workbenchId\.toString\(\),\s*(?:recipe\.getId\(\)|recipe\.id\(\))\.toString\(\)\);\s*return null;' `
            -and $transfer -match 'return Type\.COSMETIC;') `
        "${version}: JEI preview must remain cosmetic and separate from recipe selection."
    Assert-That ($transfer -match '(?s)getButtonHighlightColor\(\)\s*\{\s*return 0;' `
            -and $transfer -match 'slot\.drawHighlight\(graphics, TransferHelper\.BLUE_SLOT_HIGHLIGHT_COLOR\)') `
        "${version}: JEI craftable highlighting must not mark the transfer button as an error."
}

Write-Output "Pattern terminal layout/resource checks passed for Forge 1.20.1 and NeoForge 1.21.1 (3 recipe rows, 81 inputs across 25 scroll positions, item/menu/screen/model/recipe/localization registrations, JEI-only)."
