param (
    [string]$ApkPath = "",
    [string]$Url = ""
)

$targetAssetPath = "app/src/main/assets/system_full.dictionary"
$tempDir = "apk_extract_temp"
$apkFile = "target.apk"

# 1. Determine Source
if ($ApkPath -ne "" -and (Test-Path $ApkPath)) {
    Write-Host "Using local APK: $ApkPath"
    Copy-Item $ApkPath $apkFile -Force
} elseif ($Url -ne "") {
    Write-Host "Downloading APK from $Url..."
    try {
        Invoke-WebRequest -Uri $Url -OutFile $apkFile
    } catch {
        Write-Error "Download failed: $_"
        exit 1
    }
} else {
    # Try to find Fcitx5 Mozc Plugin URL automatically
    Write-Host "Searching for Fcitx5 Mozc Plugin APK..."
    $releasesUrl = "https://github.com/fcitx5-android/fcitx5-android/releases"
    try {
        $content = (Invoke-WebRequest -Uri $releasesUrl).Content
        # Regex for plugin-mozc apk
        if ($content -match 'href="([^"]+plugin-mozc[^"]+\.apk)"') {
            $apkUrl = "https://github.com" + $matches[1]
            Write-Host "Found URL: $apkUrl"
            Write-Host "Downloading..."
            Invoke-WebRequest -Uri $apkUrl -OutFile $apkFile
        } else {
            Write-Warning "Auto-detection failed. Please provide -ApkPath or -Url."
            Write-Host "Usage: .\extract_dictionary_from_apk.ps1 -ApkPath 'C:\path\to\app.apk' OR -Url 'https://...'"
            exit 1
        }
    } catch {
        Write-Error "Failed to fetch releases page: $_"
        exit 1
    }
}

# 2. Extract
if (Test-Path $tempDir) { Remove-Item $tempDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

Write-Host "Extracting APK (ZIP)..."
# Rename to .zip for Expand-Archive compatibility if needed, but tar/7z works on .apk usually
# Using tar because it's available and fast
try {
    tar -xf $apkFile -C $tempDir
} catch {
    Write-Warning "tar failed, trying Expand-Archive..."
    Rename-Item $apkFile "target.zip" -Force
    Expand-Archive "target.zip" -DestinationPath $tempDir -Force
}

# 3. Search and Extract Dictionary
Write-Host "Searching for system.dictionary..."
$dictFile = Get-ChildItem -Path $tempDir -Recurse -Filter "system.dictionary" | Select-Object -First 1

if ($dictFile) {
    $sizeMB = $dictFile.Length / 1MB
    Write-Host "Found system.dictionary. Size: $([math]::Round($sizeMB, 2)) MB"
    
    if ($sizeMB -gt 40) {
        Write-Host "Size is valid (>40MB)."
        
        # 4. Copy to Project
        $destDir = "app\src\main\assets"
        if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force }
        $destPath = Join-Path $destDir "system_full.dictionary"
        
        Write-Host "Copying to $destPath..."
        Copy-Item -Path $dictFile.FullName -Destination $destPath -Force
        
        Write-Host "SUCCESS: Dictionary extracted and saved."
    } else {
        Write-Warning "File is too small (<40MB). It might be a stub or headers only."
        # Copy anyway if user wants? No, user said >40MB is "atari".
        Write-Host "Copying anyway for inspection..."
        Copy-Item -Path $dictFile.FullName -Destination $targetAssetPath -Force
    }
} else {
    Write-Error "system.dictionary NOT found in the APK."
    # List assets to debug
    Get-ChildItem -Path $tempDir -Recurse | Where-Object { $_.FullName -like "*assets*" } | Select-Object -First 20 | ForEach-Object { Write-Host $_.FullName }
}

# Cleanup
# Remove-Item $tempDir -Recurse -Force
# Remove-Item $apkFile -Force
