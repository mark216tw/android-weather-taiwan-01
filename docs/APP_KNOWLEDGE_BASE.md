# 台灣天氣 APP 知識庫

本文件整理「台灣天氣」目前的產品行為、資料來源、架構、資料規則與維護注意事項。內容以版本 `1.3.0` 的程式實作為準，適合開發、測試、除錯與專案交接時快速查閱。

## 1. 產品定位

「台灣天氣」是以 Kotlin 與 Jetpack Compose 開發的 Android APP，支援台灣 22 縣市及鄉鎮市區。APP 沒有自有後端，裝置會直接向中央氣象署 CWA 與環境部 MOENV 查詢資料。

使用者必須自行申請並輸入：

- 中央氣象署 CWA 授權碼：天氣預報、測站觀測及警特報。
- 環境部 MOENV API Key：空氣品質。

最低支援 Android 8.0（API 26），Target SDK 與 compileSdk 均為 API 35。

## 2. 目前功能

### 首頁

- 顯示目前溫度、體感溫度、天氣現象、濕度、降雨機率及風況。
- 顯示最近有效氣象測站名稱、觀測時間及距離。
- 顯示當日日出、日落時間及今日天氣摘要。
- 顯示紫外線分級、防曬建議、露點、舒適度、氣壓、雨量、最大陣風及蒲福風級。
- 顯示 AQI、PM2.5、PM10、O3、CO、SO2、NO2、主要污染物與健康建議。
- 顯示有效中的中央氣象署警特報。
- 未來幾天天氣以全寬卡片垂直排列。
- 顯示快取、過期及部分資料更新失敗狀態。

### 地點與收藏

- 使用 GPS 與 Android Geocoder 辨識台灣行政區。
- 手動搜尋及選擇縣市、鄉鎮市區。
- 收藏、移除及調整收藏順序。
- 保存最近選擇的地點。
- 同一地點不會重複收藏。

### 設定

- 儲存 CWA 與 MOENV 授權碼。
- 分別測試兩個資料服務的連線與授權狀態。
- 提供官方 API Key 申請連結。

### 顯示模式

- 日出至日落自動使用淺色模式。
- 日落至隔日日出自動使用深色模式。
- 每分鐘重新判斷日夜狀態。
- 尚未取得座標時暫時跟隨 Android 系統主題。
- 沒有手動淺色、深色或系統模式設定。

## 3. 畫面未顯示但內部使用的資料

Repository 仍會解析未來 48 小時的官方分時資料，但首頁不直接顯示分時預報或 24 小時趨勢。分時資料目前用於：

- 今日天氣摘要。
- 午後降雨判斷。
- 紫外線與防曬建議。
- 今日高低溫、體感溫度與舒適度彙整。
- 未來每日預報彙整。

不要因畫面沒有分時卡片就直接移除 `HourlyForecast` 或相關解析；移除前必須先替代上述依賴。

## 4. 官方資料來源

### 中央氣象署 CWA

Base URL：

```text
https://opendata.cwa.gov.tw/
```

目前使用的資料集：

| 用途 | Endpoint／資料集 |
| --- | --- |
| 各縣市鄉鎮預報 | `api/v1/rest/datastore/F-D0047-*` |
| 自動氣象站觀測 | `api/v1/rest/datastore/O-A0003-001` |
| 氣象警特報 | `api/v1/rest/datastore/W-C0033-001` |

各縣市使用不同 `F-D0047-*` dataset ID，對照表定義於 `data/Models.kt` 的 `TaiwanCounties`。

預報解析的主要欄位：

- 天氣現象與綜合描述。
- 平均、最低及最高溫度。
- 最低及最高體感溫度。
- 相對濕度、露點與舒適度。
- 降雨機率。
- 風速、風向及蒲福風級。
- 紫外線指數。

觀測解析的主要欄位：

- 溫度、濕度、露點及氣壓。
- 風速、風向、最大陣風及蒲福風級。
- 目前或累積雨量。
- 測站名稱、座標及觀測時間。

### 環境部 MOENV

Base URL：

```text
https://data.moenv.gov.tw/
```

目前使用：

```text
api/v2/aqx_p_432
```

解析欄位包括：

- AQI 與官方狀態。
- PM2.5、PM10 及其平均值。
- O3 與 8 小時平均。
- CO 與 8 小時平均。
- SO2、NO2。
- 主要污染物。
- 測站名稱、發布時間與座標。

## 5. 資料解析原則

官方 JSON 欄位可能存在大小寫、命名及巢狀結構差異，因此 Repository 採防禦式 JSON tree parsing。

重要規則：

- 未知欄位會忽略。
- `-99`、`-999`、`NA`、空字串及 `--` 視為缺值。
- 預報元素不可依陣列 index 對齊。
- 時間完全相同時優先 exact match。
- 不同粒度元素使用 `[StartTime, EndTime)` 時間區間配對。
- 不對缺少的官方資料進行內插或推估。
- 畫面缺值使用 `--` 或隱藏該詳細列。

相關程式：

- `data/WeatherRepository.kt`
- `data/Models.kt`
- `data/WeatherAdvice.kt`

## 6. 測站選擇

目標座標來源：

- GPS 定位：使用當次取得的座標，只保留在記憶體。
- 手動地點：以 Android Geocoder 取得鄉鎮近似座標。

氣象站與空品站均會解析 WGS84 座標，再使用 Haversine 公式計算距離。選擇規則為：

1. 有目標座標時，從全台有效測站中選擇距離最近者，允許跨縣市。
2. 沒有目標座標時，優先同鄉鎮，再退回同縣市。
3. 缺座標或缺關鍵觀測值的測站不參與最近距離選擇。

距離計算位於 `location/GeoDistance.kt`。

## 7. 定位規則

`TaiwanLocationResolver` 使用 Fused Location Provider 與 Android Geocoder。

- `lastLocation` 必須在 15 分鐘內且精確度優於 2,000 公尺。
- 不符合條件時主動要求一次平衡耗電精度的新位置。
- Android 13 以上使用非同步 Geocoder API。
- 舊版 Android 在 IO dispatcher 執行同步 Geocoder。
- 國碼不是 `TW` 或縣市不在白名單時拒絕查詢。
- APP 不要求背景定位，也不建立位置歷史。

## 8. 日出日落與自動主題

日出日落不是由 CWA API 取得，而是由 `SunCalculator` 使用日期及座標在本機計算。

- 時區固定使用 `Asia/Taipei`。
- 日出至日落為淺色模式。
- 日出前及日落後為深色模式。
- 每分鐘重新計算日夜狀態。
- 日期跨日後會重新計算當日資料。
- 無座標時由系統主題暫時決定明暗模式。

相關程式：

- `data/SunCalculator.kt`
- `AppViewModel.updateSunState()`
- `MainActivity.kt`
- `ui/Theme.kt`

## 9. 快取與離線規則

天氣快取使用 Room，資料表以 `kind + key` 為複合主鍵，保存原始 JSON、取得時間及 schema version。

| 資料來源 | TTL |
| --- | ---: |
| 鄉鎮預報 | 60 分鐘 |
| 即時觀測 | 15 分鐘 |
| 空氣品質 | 30 分鐘 |
| 警特報 | 10 分鐘 |

行為規則：

- 自動載入時，快取在 TTL 內就直接使用。
- 快取過期時嘗試網路更新。
- 網路失敗且有舊快取時，繼續顯示並標示可能過期。
- 各資料來源獨立成功或失敗，不因空品或警特報失敗而中斷整頁。
- 警特報即使來自快取，也會依有效期限排除已過期項目。
- 手動更新會略過 TTL，強制查詢。

Room schema 位於：

```text
app/schemas/tw.app.taiwanweather.data.local.WeatherCacheDatabase/
```

## 10. 更新時機與頻率

天氣資料更新時機：

- APP 啟動且已設定 CWA Key。
- 切換手動地點。
- GPS 定位成功並切換地點。
- 使用者按下更新按鈕。
- APP 從背景回到前景，而且最近成功更新已超過 30 分鐘。

前景更新規則：

- 首次 `ON_START` 不重複觸發初始化請求。
- 超過 30 分鐘才更新，剛好 30 分鐘不更新。
- 更新進行中不重複請求。
- 自動嘗試後有 5 分鐘冷卻，避免頻繁切換前背景消耗 API 配額。
- 手動更新不受冷卻限制。
- 沒有使用 WorkManager，因此 APP 停留背景時不會定時更新。

同一 request key 的同時請求會由 single-flight 機制合併。預報、觀測、空品與警特報使用 coroutine 平行查詢，個別失敗互不取消。

## 11. 健康與生活建議

### AQI 分級

| AQI | 等級 |
| ---: | --- |
| 0–50 | 良好 |
| 51–100 | 普通 |
| 101–150 | 對敏感族群不健康 |
| 151–200 | 對所有族群不健康 |
| 201–300 | 非常不健康 |
| 301 以上 | 危害 |

每個等級會提供一般族群、敏感族群及口罩建議。畫面同時使用官方分級顏色與文字，不能只靠顏色傳達狀態。

### 紫外線分級

| UVI | 等級 |
| ---: | --- |
| 0–2 | 低量級 |
| 3–5 | 中量級 |
| 6–7 | 高量級 |
| 8–10 | 過量級 |
| 11 以上 | 危險級 |

### 今日摘要優先順序

1. 午後高降雨機率。
2. 全天高降雨機率。
3. 一般降雨可能。
4. 36°C 以上高溫。
5. 10°C 以下低溫。
6. 10 m/s 以上強風。
7. 天氣描述含雨。
8. 一般穩定天氣提示。

## 12. 狀態與錯誤處理

首頁主要狀態為 `Idle`、`Loading`、`Success`、`Error`。

- 有既有資料時，更新不會清空畫面。
- 更新失敗時保留舊報告並顯示錯誤。
- Repository 透過 `SourceIssue` 保存個別來源問題。
- 401／403：授權碼無效或過期。
- 429：API 配額已達上限。
- DNS 失敗：無法連網。
- Timeout：連線逾時。
- 沒有 MOENV Key：顯示設定引導，不影響天氣功能。

## 13. 安全與隱私

API Key 保存方式：

1. Android Keystore 產生不可匯出的 AES 金鑰。
2. 使用 AES-GCM 加密 CWA 與 MOENV Key。
3. 密文保存於 Preferences DataStore。

其他原則：

- API Key 不寫入原始碼、Room 天氣快取或 Log。
- 所有 API 使用 HTTPS。
- 沒有自有後端。
- GPS 精確座標只保留於記憶體，不持久化。
- DataStore 保存收藏及最近選擇地點。
- APP 不要求背景定位。

## 14. 程式架構

主要資料流：

```text
Compose UI
  -> AppViewModel
  -> WeatherRepository
  -> CWA API / MOENV API / Room cache
  -> domain models
  -> StateFlow<AppUiState>
  -> Compose UI
```

重要檔案：

| 檔案 | 責任 |
| --- | --- |
| `MainActivity.kt` | Compose 入口、定位權限、lifecycle 前景事件、系統列配色 |
| `AppViewModel.kt` | APP 狀態、更新協調、地點、收藏、日出日落 |
| `data/WeatherApi.kt` | Retrofit API 定義及 client |
| `data/WeatherRepository.kt` | API 協調、快取、解析、測站選擇、資料聚合 |
| `data/Models.kt` | domain models 與縣市 dataset 對照 |
| `data/WeatherAdvice.kt` | AQI、UVI 與今日摘要規則 |
| `data/ForegroundRefreshPolicy.kt` | 前景 30 分鐘更新與 5 分鐘冷卻 |
| `data/SunCalculator.kt` | 本機日出日落計算 |
| `data/local/WeatherCache.kt` | Room entity、DAO、database 及 cache adapter |
| `data/SecureStore.kt` | API Key 加密與 Preferences DataStore |
| `location/TaiwanLocationResolver.kt` | GPS 與行政區解析 |
| `location/GeoDistance.kt` | Haversine 距離 |
| `ui/AppScreen.kt` | 首頁、地點、設定及導覽畫面 |
| `ui/WeatherDetails.kt` | 紫外線、詳細氣象與污染物展開區 |
| `ui/Theme.kt` | Material 3 淺色、深色主題 |

目前仍是單一 `:app` 模組，且 `AppScreen.kt` 尚未改用 Navigation Compose。

## 15. Build 與依賴

核心版本：

| 元件 | 版本 |
| --- | --- |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Java | 17 |
| Compose BOM | 2025.01.01 |
| Retrofit | 2.11.0 |
| OkHttp | 4.12.0 |
| Room | 2.6.1 |
| Coroutines | 1.9.0 |

常用命令：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assemblePrerelease
```

APK 輸出：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/prerelease/app-prerelease.apk
```

`prerelease` 會啟用 R8 與資源縮減，但使用 Debug Key 簽署，只適合測試。

## 16. 測試範圍

目前 JVM tests 涵蓋：

- CWA 預報時間欄位與區間對齊。
- UVI、舒適度及豐富天氣欄位解析。
- 氣象觀測雨量、陣風及風級。
- MOENV 完整污染物欄位。
- 最近測站距離。
- Room cache policy 與重複請求抑制。
- AQI、UVI 及今日摘要邊界。
- 日出日落與日夜切換邊界。
- 前景更新 30 分鐘門檻及 5 分鐘冷卻。

Compose instrumentation test 驗證主要首頁資訊狀態，但需要連接裝置或模擬器才能實際執行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## 17. 已知限制與風險

- Android Geocoder 並非所有裝置都可用；失敗時仍可手動選地點，但可能無法取得距離與日出日落。
- 手動地點使用 Geocoder 近似座標，不等於官方行政區幾何中心。
- CWA 與 MOENV schema 可能調整，parser alias 與 fixtures 需持續維護。
- 警特報 schema 採防禦式解析；官方新增巢狀格式時可能需要補 alias。
- 分時資料保留於 domain model，但目前不直接顯示。
- 沒有真正的背景定時更新與通知。
- 沒有正式 release keystore、GitHub Actions 或 Google Play 發布流程。
- `prerelease` 使用 Debug Key，不可視為正式簽章版本。
- UI 中文字多數仍直接寫在 Kotlin，尚未完整移至 `strings.xml`。
- 目前沒有 Navigation Compose，程序重建時的畫面返回狀態有限。

## 18. 修改功能時的檢查清單

### 新增或修改 API 欄位

1. 保存匿名真實 JSON fixture。
2. 加入大小寫、缺值及 schema variant 測試。
3. 以時間區間對齊，不使用陣列 index。
4. 確認 sentinel 不會顯示到 UI。
5. 確認 Room payload schema 是否需要遞增。

### 修改更新策略

1. 區分手動更新與自動更新。
2. 不吞掉 `CancellationException`。
3. 保留 single-flight 及快速切換地點的競態保護。
4. 確認 API 配額與冷卻規則。
5. 離線失敗時保留舊資料。

### 修改首頁 UI

1. 優先保留摘要，詳細資訊使用展開區。
2. 缺值時隱藏詳細列，不顯示錯誤單位組合。
3. 狀態不能只依顏色或 Emoji 傳達。
4. 同時檢查淺色、深色及大字體。
5. 更新 `HomeScreenTest` 與使用指南。

### 發布前

1. 更新 `versionCode`、`versionName` 與 `CHANGELOG.md`。
2. 執行 unit tests、Compose test compilation、Lint 及 prerelease build。
3. 計算 APK SHA-256。
4. 確認工作樹沒有 API Key、keystore 或 build artifacts。
5. Prerelease 必須標示為 Debug Key 測試版本。

## 19. 相關文件

- `README.md`：專案入口與快速開始。
- `docs/USER_GUIDE.md`：使用者操作指南。
- `docs/ARCHITECTURE.md`：架構與元件責任。
- `docs/TECHNICAL_REFERENCE.md`：API 與程式技術細節。
- `docs/SYSTEM_DESIGN.md`：需求、資料及錯誤設計。
- `docs/BUILD_AND_RELEASE.md`：建置與發布流程。
- `docs/PRIVACY.md`：隱私與本機資料說明。
- `docs/ROADMAP.md`：未來優化方向。
- `CHANGELOG.md`：版本變更紀錄。
