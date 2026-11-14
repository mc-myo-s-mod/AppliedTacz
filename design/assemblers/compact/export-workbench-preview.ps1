param([ValidateSet('1.20.1', '1.21.1')][string]$AssetVersion = '1.21.1')
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$workspace = Split-Path $root
$module = Join-Path $root 'neoforge-1.21.1'
$java = Join-Path $workspace '.jdk-codex/jdk-21.0.11+10/bin'
$cache = Join-Path $HOME '.gradle/caches/modules-2/files-2.1'
$properties = Get-Content (Join-Path $module 'gradle.properties') -Raw | ConvertFrom-StringData
$classpath = @(
    (Join-Path $module 'build/classes/java/main')
    (Get-ChildItem (Join-Path $module 'build/moddev/artifacts/*-merged.jar')).FullName
    (Get-Content (Join-Path $module 'build/moddev/clientLegacyClasspath.txt'))
    (Get-ChildItem "$cache/maven.modrinth/timeless-and-classics-zero/$($properties.TaCZ_version)" -Recurse -Filter '*.jar').FullName
    (Get-ChildItem "$cache/org.appliedenergistics/appliedenergistics2/$($properties.AE2_version)" -Recurse -Filter '*.jar').FullName
) -join ';'
$output = Join-Path $env:TEMP 'appliedtacz-workbench-preview'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& "$java/javac.exe" -proc:none -cp $classpath -d $output (Join-Path $PSScriptRoot 'ExportWorkbenchPreview.java')
if ($LASTEXITCODE -ne 0) { throw "Preview exporter compilation failed: $LASTEXITCODE" }
$pack = Join-Path $workspace "fork/Tacz_$($AssetVersion.Replace('.', '_'))/src/main/resources/assets/tacz/custom/tacz_default_gun"
$mesh = Join-Path $output "workbenches-$AssetVersion.json"
& "$java/java.exe" -ea -cp "$output;$classpath" me.myogoo.appliedtacz.client.renderer.ExportWorkbenchPreview $pack $mesh
if ($LASTEXITCODE -ne 0) { throw "Preview export failed: $LASTEXITCODE" }
Write-Output "Exported offline preview mesh: $mesh"
