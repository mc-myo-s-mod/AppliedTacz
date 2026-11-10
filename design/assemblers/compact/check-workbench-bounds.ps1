$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$workspace = Split-Path $root
$module = Join-Path $root 'neoforge-1.21.1'
$java = Join-Path $workspace '.jdk-codex/jdk-21.0.11+10/bin'
$cache = Join-Path $HOME '.gradle/caches/modules-2/files-2.1'
$properties = Get-Content (Join-Path $module 'gradle.properties') -Raw | ConvertFrom-StringData
$expectedBoundAssemblers = @(
    'TACZ_ASSEMBLER_GUN', 'TACZ_ASSEMBLER_AMMO', 'TACZ_ASSEMBLER_ATTACHMENT',
    'EXTENDED_TACZ_ASSEMBLER_GUN', 'EXTENDED_TACZ_ASSEMBLER_AMMO', 'EXTENDED_TACZ_ASSEMBLER_ATTACHMENT',
    'MOLECULAR_ASSEMBLER_GUN', 'MOLECULAR_ASSEMBLER_AMMO', 'MOLECULAR_ASSEMBLER_ATTACHMENT'
)
foreach ($loader in @(
    @{ Name = 'Forge'; Path = 'forge-1.20.1/src/main/java/me/myogoo/appliedtacz/client/AETaCZItemDecorators.java'; Interfaces = @('GUN_SMITH_TABLE', 'AMMO_ASSEMBLY_TABLE', 'ATTACHMENT_TABLE') },
    @{ Name = 'NeoForge'; Path = 'neoforge-1.21.1/src/main/java/me/myogoo/appliedtacz/client/AETaCZItemDecorators.java'; Interfaces = @('GUN_SMITH_TABLE', 'AMMO_WORKBENCH', 'ATTACHMENT_TABLE') }
)) {
    $source = Get-Content (Join-Path $root $loader.Path) -Raw
    $bound = @([regex]::Matches($source, 'event\.register\(\s*\w+\.(\w+)\.get\(\),\s*AssemblerWorkbenchBadge::render\s*\)') |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object)
    if (($bound -join ',') -cne (@($expectedBoundAssemblers | Sort-Object) -join ',')) {
        throw "$($loader.Name) bound assembler badges differ: $($bound -join ', ')"
    }
    $interfaces = @([regex]::Matches($source, 'event\.register\(\s*\w+\.(\w+)\.get\(\),\s*WORKBENCH_BADGE\s*\)') |
        ForEach-Object { $_.Groups[1].Value } | Sort-Object)
    if (($interfaces -join ',') -cne (@($loader.Interfaces | Sort-Object) -join ',')) {
        throw "$($loader.Name) interface badge registrations changed: $($interfaces -join ', ')"
    }
}
$classpath = @(
    (Join-Path $module 'build/classes/java/main')
    (Get-ChildItem (Join-Path $module 'build/moddev/artifacts/*-merged.jar')).FullName
    (Get-Content (Join-Path $module 'build/moddev/clientLegacyClasspath.txt'))
    (Get-ChildItem "$cache/maven.modrinth/tacz-1.21.1/$($properties.TaCZ_version)" -Recurse -Filter '*.jar').FullName
    (Get-ChildItem "$cache/org.appliedenergistics/appliedenergistics2/$($properties.AE2_version)" -Recurse -Filter '*.jar').FullName
) -join ';'
$output = Join-Path $env:TEMP 'appliedtacz-workbench-bounds-check'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& "$java/javac.exe" -proc:none -cp $classpath -d $output (Join-Path $PSScriptRoot 'WorkbenchBoundsCheck.java') (Join-Path $PSScriptRoot 'AssemblerVfxCheck.java') (Join-Path $PSScriptRoot 'AssemblerClientConfigCheck.java')
if ($LASTEXITCODE -ne 0) { throw "Bounds check compilation failed: $LASTEXITCODE" }
$models = foreach ($version in @('20', '21')) {
    Join-Path $workspace "fork/Tacz_1_${version}_1/src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/geo_models/block"
}
& "$java/java.exe" -ea -cp "$output;$classpath" me.myogoo.appliedtacz.client.renderer.WorkbenchBoundsCheck @models
if ($LASTEXITCODE -ne 0) { throw "Bounds check failed: $LASTEXITCODE" }
& "$java/java.exe" -ea -cp "$output;$classpath" me.myogoo.appliedtacz.client.renderer.AssemblerVfxCheck
if ($LASTEXITCODE -ne 0) { throw "VFX check failed: $LASTEXITCODE" }
& "$java/java.exe" -ea -cp "$output;$classpath" AssemblerClientConfigCheck
if ($LASTEXITCODE -ne 0) { throw "Client config check failed: $LASTEXITCODE" }
