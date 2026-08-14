# install_to_device.ps1
# Ayame Keyboard デバイスインストール支援スクリプト

$ErrorActionPreference = "Stop"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  Ayame Keyboard デバイスインストールツール" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. adb コマンドの確認
$adbPath = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbPath) {
    Write-Host "[Error] adb コマンドが見つかりません。" -ForegroundColor Red
    Write-Host "Android SDK プラットフォームツール (adb.exe) にパスが通っているか確認してください。"
    Write-Host "通常は以下に配置されています:"
    Write-Host "C:\Users\<ユーザー名>\AppData\Local\Android\Sdk\platform-tools"
    Exit 1
}

# 2. デバイスリストの取得
Write-Host "接続されているデバイスを検出中..." -ForegroundColor Yellow
$devicesOutput = adb devices
$devices = @()

foreach ($line in $devicesOutput) {
    if ($line -match "^\s*([^\s]+)\s+device\b") {
        $devices += $Matches[1]
    }
}

if ($devices.Count -eq 0) {
    Write-Host "[Error] 接続されているデバイスが見つかりません。" -ForegroundColor Red
    Write-Host "Android実機またはエミュレータが起動しており、'USBデバッグ' が有効になっているか確認してください。"
    Exit 1
}

$targetDevice = ""
if ($devices.Count -eq 1) {
    $targetDevice = $devices[0]
    Write-Host "デバイス '$targetDevice' が見つかりました。" -ForegroundColor Green
} else {
    Write-Host "複数のデバイスが検出されました。インストール先を選択してください:" -ForegroundColor Yellow
    for ($i = 0; $i -lt $devices.Count; $i++) {
        $model = adb -s $devices[$i] shell getprop ro.product.model
        Write-Host "  [$i] $($devices[$i]) ($($model.Trim()))"
    }
    
    $selection = -1
    while ($selection -lt 0 -or $selection -ge $devices.Count) {
        $input = Read-Host "選択するデバイスの番号を入力してください"
        if ($input -as [int] -ne $null) {
            $selection = [int]$input
        }
    }
    $targetDevice = $devices[$selection]
}

# 3. APKのビルド確認
$localApkPath = "app/build/outputs/apk/debug/app-debug.apk"
$externalApkPath = "C:/Users/nyama/gradle_builds/JapaneseKeyboard/app/outputs/apk/debug/app-debug.apk"
$apkPath = ""

if (Test-Path $externalApkPath) {
    $apkPath = $externalApkPath
} elseif (Test-Path $localApkPath) {
    $apkPath = $localApkPath
}

$buildRequired = $true

if ($apkPath -ne "") {
    $lastWriteTime = (Get-Item $apkPath).LastWriteTime
    Write-Host "ビルド済みのAPKが見つかりました (パス: $apkPath, 最終更新: $lastWriteTime)。" -ForegroundColor Green
    $choice = Read-Host "再ビルドしてインストールしますか？ (y: 再ビルド / n: 既存のAPKを使用) [y]"
    if ($choice.ToLower() -eq "n") {
        $buildRequired = $false
    }
} else {
    # デフォルトの期待される出力先
    $apkPath = $externalApkPath
}

if ($buildRequired) {
    Write-Host "アプリをビルドしています (gradlew assembleDebug)..." -ForegroundColor Yellow
    try {
        Start-Process -FilePath "./gradlew.bat" -ArgumentList "assembleDebug" -NoNewWindow -Wait
        
        # ビルド成功後、生成されたパスを再検出
        if (Test-Path $externalApkPath) {
            $apkPath = $externalApkPath
        } elseif (Test-Path $localApkPath) {
            $apkPath = $localApkPath
        } else {
            throw "APKファイルが生成されませんでした。"
        }
        Write-Host "ビルドが成功しました。" -ForegroundColor Green
    } catch {
        Write-Host "[Error] ビルドに失敗しました。" -ForegroundColor Red
        Write-Host $_
        Exit 1
    }
}

# 4. インストールの実行
Write-Host "デバイス '$targetDevice' にインストールしています..." -ForegroundColor Yellow
$installOutput = ""
$installSuccess = $false

try {
    $installOutput = adb -s $targetDevice install -r $apkPath 2>&1
    Write-Host $installOutput
    if ($installOutput -match "Success") {
        $installSuccess = $true
    }
} catch {
    $installOutput = $_.ToString()
}

# 5. 署名競合等のエラーハンドリング
if (-not $installSuccess -and ($installOutput -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE" -or $installOutput -match "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE")) {
    Write-Host "`n[警告] 署名競合エラー (INSTALL_FAILED_UPDATE_INCOMPATIBLE) が発生しました。" -ForegroundColor Yellow
    Write-Host "すでに異なる署名（Google Play版など）のアプリがインストールされている可能性があります。"
    $uninstallChoice = Read-Host "既存のアプリをアンインストールし、クリーンインストールを試みますか？ (y/n) [n]"
    
    if ($uninstallChoice.ToLower() -eq "y") {
        Write-Host "既存アプリ (jp.yama3nomori.ayame) をアンインストール中..." -ForegroundColor Yellow
        adb -s $targetDevice uninstall jp.yama3nomori.ayame
        
        Write-Host "再インストール中..." -ForegroundColor Yellow
        $reinstallOutput = adb -s $targetDevice install -r $apkPath 2>&1
        Write-Host $reinstallOutput
        if ($reinstallOutput -match "Success") {
            $installSuccess = $true
        }
    }
}

# 6. 結果と通知音
if ($installSuccess) {
    Write-Host "`nインストールが完了しました！" -ForegroundColor Green
    
    # 完了音を鳴らす (ffplay があれば利用、無ければ OS のビープ音)
    $soundFile = Join-Path (Get-Location) "sound/nt_musicbox.mp3"
    $ffplayExe = Join-Path (Get-Location) "ffplay.exe"
    
    if ((Test-Path $ffplayExe) -and (Test-Path $soundFile)) {
        Start-Process $ffplayExe -ArgumentList "-nodisp", "-autoexit", $soundFile -NoNewWindow
    } else {
        [System.Console]::Beep(1000, 300)
        [System.Console]::Beep(1200, 400)
    }
} else {
    Write-Host "`n[Error] インストールに失敗しました。" -ForegroundColor Red
    [System.Console]::Beep(400, 500)
}
