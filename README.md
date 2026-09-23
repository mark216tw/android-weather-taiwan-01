# 台灣天氣

「台灣天氣」是一款以 Kotlin 與 Jetpack Compose 開發的 Android 天氣應用程式，提供台灣 22 縣市及鄉鎮市區的即時天氣、七日預報與空氣品質資訊。使用者自行申請中央氣象署及環境部 API 授權碼，資料直接由裝置向政府開放資料平台查詢。

## 主要功能

- 顯示目前溫度、體感溫度、天氣、濕度、降雨機率與風況。
- 依目前地點顯示當日日出、日落時間。
- 顯示未來七日天氣預報。
- 顯示環境部 AQI、PM2.5、空品狀態及測站資訊。
- 使用 GPS 辨識台灣縣市與鄉鎮，台灣以外地點不提供查詢。
- 以可搜尋的全頁清單選擇縣市及鄉鎮市區。
- 收藏常用地點、切換地點、刪除收藏及調整順序。
- CWA 與 MOENV 授權碼獨立連線測試。
- 使用 Android Keystore 與 AES-GCM 加密授權碼。
- 日出後自動使用淺色模式，日落後自動使用深色模式。
- 粉彩、活潑、友善的 Jetpack Compose 介面。

## 畫面流程

- 主畫面：天氣摘要、空氣品質、七日預報、GPS、更新與設定。
- 地點與收藏：選擇地點、搜尋行政區、收藏及排序。
- 設定：兩組 API 授權碼、連線測試及官方申請連結。

## 資料來源

- [交通部中央氣象署氣象資料開放平台](https://opendata.cwa.gov.tw/index)
- [環境部環境資料開放平台](https://data.moenv.gov.tw/)

本專案不是中央氣象署或環境部的官方應用程式。資料內容、更新頻率及服務可用性以資料提供機關為準。

## 系統需求

| 項目 | 需求 |
| --- | --- |
| 最低 Android 版本 | Android 8.0 / API 26 |
| Target SDK | API 35 |
| 編譯 SDK | API 35 |
| Java | JDK 17 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |

## 快速開始

1. 複製專案。

```powershell
git clone https://github.com/mark216tw/android-weather-taiwan-01.git
```

2. 進入專案並執行測試與 Debug 建置。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

3. 連接已開啟 USB 偵錯的 Android 裝置並安裝。

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

4. 在 APP 設定頁申請、輸入並測試 CWA 與 MOENV 授權碼。

## Prerelease 建置

`prerelease` Build Type 會啟用 R8 程式壓縮與資源縮減，並使用 Android Debug 金鑰簽署，只適合測試，不可視為正式發行簽章。

```powershell
.\gradlew.bat assemblePrerelease
```

輸出：

```text
app/build/outputs/apk/prerelease/app-prerelease.apk
```

版本名稱：`1.2.0-prerelease`

## 權限

| 權限 | 用途 |
| --- | --- |
| `INTERNET` | 查詢 CWA 與 MOENV API |
| `ACCESS_NETWORK_STATE` | 判斷網路狀態 |
| `ACCESS_COARSE_LOCATION` | 取得概略位置以辨識行政區 |
| `ACCESS_FINE_LOCATION` | 提高行政區辨識準確度 |

APP 不要求背景定位；拒絕定位後仍可手動選擇地點。

## 專案文件

- [使用指南](docs/USER_GUIDE.md)
- [系統架構](docs/ARCHITECTURE.md)
- [技術文件](docs/TECHNICAL_REFERENCE.md)
- [系統設計](docs/SYSTEM_DESIGN.md)
- [建置與發行](docs/BUILD_AND_RELEASE.md)
- [隱私說明](docs/PRIVACY.md)
- [開發藍圖](docs/ROADMAP.md)
- [安全政策](SECURITY.md)
- [貢獻指南](CONTRIBUTING.md)
- [版本紀錄](CHANGELOG.md)

## 已知限制

- GPS 使用 Android Geocoder 判斷行政區，沒有 Geocoder 服務的裝置可能無法自動辨識。
- 即時觀測優先使用相同鄉鎮測站，沒有相符測站時退回同縣市測站及預報資料。
- 空品測站目前選擇同縣市第一筆有效資料，尚未依實際距離排序。
- CWA 未提供的預報欄位顯示 `--`，APP 不自行產生推估值。
- API 配額、金鑰效期與服務條款由各資料平台管理。

## 授權

本專案採用 [MIT License](LICENSE)。政府開放資料仍依各資料提供機關的授權及服務條款使用。
