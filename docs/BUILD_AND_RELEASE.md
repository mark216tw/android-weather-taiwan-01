# 建置與發行

## 開發環境

- JDK 17
- Android SDK Platform 35
- Android Build Tools 35.0.0 或相容版本
- Git
- Android Studio（建議）或 Gradle Wrapper

不需要安裝全域 Gradle，專案已包含 Wrapper。

## 常用指令

### Windows PowerShell

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assemblePrerelease
```

### macOS / Linux

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assemblePrerelease
```

## Build Types

### debug

- 開發及本機測試使用。
- 使用 Debug 金鑰簽署。
- 不啟用 R8 壓縮。
- 輸出：`app/build/outputs/apk/debug/app-debug.apk`

### prerelease

- 目前版本名稱：`1.1.0-prerelease`。
- 繼承 release 最佳化設定。
- `isMinifyEnabled = true`。
- `isShrinkResources = true`。
- 使用 Debug 金鑰簽署。
- 輸出：`app/build/outputs/apk/prerelease/app-prerelease.apk`

Prerelease 僅供測試。正式公開發行必須建立獨立且妥善保管的 release keystore，不得使用 Debug 憑證。

## 安裝與驗證

```powershell
adb install -r app/build/outputs/apk/prerelease/app-prerelease.apk
```

查看 APK 版本：

```powershell
aapt dump badging app/build/outputs/apk/prerelease/app-prerelease.apk
```

驗證簽章：

```powershell
apksigner verify --verbose --print-certs app/build/outputs/apk/prerelease/app-prerelease.apk
```

計算 SHA-256：

```powershell
Get-FileHash -Algorithm SHA256 app/build/outputs/apk/prerelease/app-prerelease.apk
```

## 發行檢查表

- 測試與 Lint 通過。
- CWA 與 MOENV 連線測試可正常使用。
- GPS 權限允許及拒絕流程已測試。
- 系統、淺色、深色模式已測試。
- API Key 未出現在原始碼、Log 或 APK 資源。
- `versionCode` 已遞增。
- `versionName` 符合發行版本。
- 正式版使用 release keystore。
- 保存 R8 `mapping.txt`。
- 更新 CHANGELOG 與文件。

## 正式發行建議

目前專案只定義 Debug 與測試 prerelease 流程。正式上架前應另外完成：

- 建立 release signing config，使用環境變數或本機未追蹤設定載入密碼。
- 啟用 Play App Signing。
- 建立隱私權政策網址。
- 完成 Google Play Data Safety 表單。
- 準備手機與平板截圖、功能圖及商店說明。
- 在多個 Android API Level 上進行實機測試。
