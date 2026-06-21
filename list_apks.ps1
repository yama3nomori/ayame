$url = "https://github.com/fcitx5-android/fcitx5-android/releases"
$output = "releases.html"
try {
    Invoke-WebRequest -Uri $url -OutFile $output
} catch {
    Write-Error "Failed to fetch releases page: $_"
    exit 1
}

$content = Get-Content $output -Raw
# Regex to find href="/... .apk"
$regex = 'href="([^"]+\.apk)"'
$matches = [regex]::Matches($content, $regex)

if ($matches.Count -eq 0) {
    Write-Warning "No APK links found on the main releases page. Trying 'expanded_assets' logic if possible (GitHub loads assets dynamically)."
    # GitHub releases often load assets via a separate request or hide them.
    # But often the latest release has assets visible or linked.
}

foreach ($match in $matches) {
    $path = $match.Groups[1].Value
    if ($path -match "plugin-mozc") {
        Write-Host "FOUND MOZC PLUGIN: https://github.com$path"
    } else {
        Write-Host "Other APK: https://github.com$path"
    }
}
