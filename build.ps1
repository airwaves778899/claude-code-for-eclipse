# ============================================================
# build.ps1  —  Claude Code for Eclipse 完整建置腳本
# 用法：在專案根目錄執行 .\build.ps1
#       可選參數：-Deploy  (同時部署到 dropins)
#                -UpdateSite (同時更新 p2 Update Site)
# ============================================================
param(
    [switch]$Deploy,
    [switch]$UpdateSite
)

$ROOT    = $PSScriptRoot
$SRC     = "$ROOT\src"
$BIN     = "$ROOT\bin"
$ECLIPSE = "C:\eclipse\eclipse-jee-2024-06-R-win32-x86_64"
$PLUGINS = "$ECLIPSE\plugins"
$DROPINS = "$ECLIPSE\dropins"
$JAVAC21 = "$ECLIPSE\plugins\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_21.0.11.v20260515-1531\jre\bin\javac.exe"
$JAVA21  = $JAVAC21.Replace("javac.exe","java.exe")
$LAUNCHER= Get-ChildItem "$PLUGINS\org.eclipse.equinox.launcher_*" -Filter "*.jar" | Select-Object -First 1 -ExpandProperty FullName
$JAR_NAME = "io.github.airwaves778899.claudecode_1.0.0.jar"
$VERSION  = "1.0.0.$(Get-Date -Format 'yyyyMMdd')"

Write-Host "==================================================="
Write-Host "  Claude Code for Eclipse — Build Script"
Write-Host "  Version: $VERSION"
Write-Host "==================================================="

# ── 1. Collect Eclipse JARs for classpath ─────────────────
Write-Host ""
Write-Host "[1/4] Collecting classpath..."
$CP_PATTERNS = @(
    "org.eclipse.ui_*","org.eclipse.core.runtime_*","org.eclipse.jface_*","org.eclipse.jface.text_*",
    "org.eclipse.swt.win32*","org.eclipse.core.resources_*","org.eclipse.ui.workbench_*",
    "org.eclipse.ui.editors_*","org.eclipse.ui.workbench.texteditor_*",
    "org.eclipse.text_*","org.eclipse.core.commands_*",
    "org.eclipse.equinox.preferences_*","org.eclipse.equinox.app_*","org.eclipse.equinox.common_*",
    "org.eclipse.equinox.registry_*","org.eclipse.osgi_*","org.eclipse.core.jobs_*",
    "org.eclipse.ui.console_*","org.eclipse.debug.core_*","org.eclipse.debug.ui_*",
    "org.eclipse.osgi.services_*","org.osgi.service.prefs_*"
)
$CP_LIST = @()
foreach ($p in $CP_PATTERNS) {
    $jar = Get-ChildItem "$PLUGINS\$p" -Filter "*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
    if ($jar) { $CP_LIST += $jar.Replace('\','/') }
}
$BIN_FWD = $BIN.Replace('\','/')
$CP = ($CP_LIST -join ";")
Write-Host "  Found $($CP_LIST.Count) Eclipse JARs"

# ── 2. Compile ────────────────────────────────────────────
Write-Host ""
Write-Host "[2/4] Compiling Java sources..."
$SOURCES = Get-ChildItem -Path $SRC -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName

$ARGS_FILE = "$env:TEMP\javac_build_args.txt"
$sw = New-Object System.IO.StreamWriter($ARGS_FILE, $false, [System.Text.Encoding]::ASCII)
$sw.WriteLine("-encoding UTF-8")
$sw.WriteLine("--release 17")
$sw.WriteLine("-cp")
$sw.WriteLine('"' + $BIN_FWD + ';' + $CP + '"')
$sw.WriteLine("-d")
$sw.WriteLine('"' + $BIN_FWD + '"')
foreach ($src in $SOURCES) { $sw.WriteLine('"' + $src.Replace('\','/') + '"') }
$sw.Close()

$result = & $JAVAC21 "@$ARGS_FILE" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "  ERROR: Compilation failed!"
    $result | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host "  Compilation OK ($($SOURCES.Count) source files)"

# ── 3. Package JAR ────────────────────────────────────────
Write-Host ""
Write-Host "[3/4] Packaging JAR..."
$TMPJAR = "$env:TEMP\$JAR_NAME"
Push-Location $BIN
& jar cfm $TMPJAR "$ROOT\META-INF\MANIFEST.MF" . 2>&1 | Out-Null
Pop-Location

if (-not (Test-Path $TMPJAR)) {
    Write-Host "  ERROR: JAR build failed!"
    exit 1
}
$sz = (Get-Item $TMPJAR).Length
Write-Host "  JAR: $sz bytes"

# Copy to project root
Copy-Item -Force $TMPJAR "$ROOT\$JAR_NAME"

# ── 4. Deploy ─────────────────────────────────────────────
if ($Deploy) {
    Write-Host ""
    Write-Host "[4a] Deploying to Eclipse dropins..."
    Copy-Item -Force $TMPJAR "$DROPINS\$JAR_NAME"
    Write-Host "  Deployed → $DROPINS\$JAR_NAME"
}

# ── 5. Update Site ────────────────────────────────────────
if ($UpdateSite) {
    Write-Host ""
    Write-Host "[4b] Building p2 Update Site..."

    $SITE = "$ROOT\updatesite"
    New-Item -ItemType Directory -Force "$SITE\features" | Out-Null
    New-Item -ItemType Directory -Force "$SITE\plugins" | Out-Null

    # Build feature JAR
    $FEAT_TMP = "$env:TEMP\feature_$VERSION"
    New-Item -ItemType Directory -Force $FEAT_TMP | Out-Null
    $xml = Get-Content "$ROOT\feature\feature.xml" -Raw
    $xml = $xml -replace '1\.0\.0\.qualifier', $VERSION
    Set-Content "$FEAT_TMP\feature.xml" $xml -Encoding UTF8
    $FEAT_JAR = "$SITE\features\io.github.airwaves778899.claudecode.feature_$VERSION.jar"
    Push-Location $FEAT_TMP
    & jar cf $FEAT_JAR "feature.xml" 2>&1 | Out-Null
    Pop-Location
    Remove-Item $FEAT_TMP -Recurse -Force

    # Copy plugin JAR
    $PLUGIN_DST = "$SITE\plugins\io.github.airwaves778899.claudecode_$VERSION.jar"
    Copy-Item -Force $TMPJAR $PLUGIN_DST

    # Run p2 publisher
    $SITE_URI = "file:/" + $SITE.Replace('\','/')
    & $JAVA21 -jar $LAUNCHER `
        -application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher `
        -metadataRepository $SITE_URI `
        -artifactRepository $SITE_URI `
        -source $SITE `
        -compress `
        -publishArtifacts 2>&1 | Out-Null

    Write-Host "  Update site: $SITE"
    Write-Host "  Files: $(Get-ChildItem $SITE -Recurse | Where-Object {-not $_.PSIsContainer} | Measure-Object | Select-Object -ExpandProperty Count)"
}

Remove-Item $TMPJAR -ErrorAction SilentlyContinue
Write-Host ""
Write-Host "=== Build complete ==="
if ($Deploy)     { Write-Host "  ✓ Deployed to Eclipse dropins" }
if ($UpdateSite) { Write-Host "  ✓ Update Site generated in updatesite/" }
Write-Host "  Output: $ROOT\$JAR_NAME"
