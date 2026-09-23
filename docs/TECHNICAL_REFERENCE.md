# 技術文件

## 技術棧

| 類別 | 技術 |
| --- | --- |
| 語言 | Kotlin 2.0.21 |
| UI | Jetpack Compose、Material 3 |
| 非同步 | Kotlin Coroutines、StateFlow |
| 網路 | Retrofit 2、OkHttp 4 |
| JSON | kotlinx.serialization |
| 本機設定 | Preferences DataStore |
| 機密資料 | Android Keystore、AES/GCM/NoPadding |
| 定位 | Google Play Services Location、Android Geocoder |
| 測試 | JUnit 4、kotlinx-coroutines-test |
| 壓縮 | R8、Resource Shrinker |

## APP 內部模型

- `TaiwanCounty`：縣市名稱、CWA 資料集 ID、預設鄉鎮。
- `Place`：縣市及鄉鎮組合。
- `CurrentWeather`：目前天氣顯示欄位。
- `DailyForecast`：每日預報。
- `AirQuality`：AQI、PM2.5、狀態及測站。
- `WeatherReport`：主畫面所需的完整聚合資料。
- `LoadState`：Idle、Loading、Success、Error。
- `SunTimes`：目前地點當日的日出與日落時間。

## CWA API

Base URL：

```text
https://opendata.cwa.gov.tw/
```

使用的資料集：

- `F-D0047-xxx`：各縣市鄉鎮七日預報。
- `O-A0003-001`：自動氣象站即時觀測。

縣市與資料集 ID 映射定義在 `Models.kt`。預報請求使用 `locationName` 限制鄉鎮；即時觀測先比對縣市與鄉鎮，找不到時退回同縣市測站。

CWA 成功回應需包含：

```json
{
  "success": "true"
}
```

主要解析欄位：

- 天氣現象
- 平均溫度
- 最高／最低溫度
- 最高體感溫度
- 平均相對濕度
- 12 小時降雨機率
- 風速、風向

## MOENV API

Endpoint：

```text
https://data.moenv.gov.tw/api/v2/aqx_p_432
```

參數：

```text
api_key=<使用者金鑰>&offset=0&limit=1000&format=json
```

資料層使用原始 `ResponseBody`，原因是平台在部分錯誤情況可能回傳純文字。`parseMoenvBody()` 同時接受 JSON Object 與 JSON Array，並保留純文字錯誤供 UI 顯示。

## API Key 安全

1. `SecureStore` 從 Android Keystore 取得或建立 AES-256 類型的裝置金鑰。
2. 每次加密產生新的 GCM IV。
3. DataStore 儲存 `IV + ciphertext` 的 Base64 字串。
4. APP 不在原始碼、Gradle 設定或 Log 保存明文金鑰。

這個設計防止一般檔案直接讀出金鑰，但已 Root、被偵錯注入或作業系統遭入侵的裝置不在完整防護範圍內。

## 定位與行政區驗證

`TaiwanLocationResolver` 使用：

1. `FusedLocationProviderClient.lastLocation` 取得位置。
2. `Geocoder` 解析地址。
3. 檢查 `countryCode == TW`。
4. 將「台」正規化為「臺」。
5. 比對內建 22 縣市白名單。

無法辨識鄉鎮時會使用該縣市預設行政區。APP 不使用「距離最近的台灣城市」猜測境外地點。

## 主題與系統列

`SunCalculator` 依日期與地點座標在本機計算日出日落。日出至日落使用淺色主題，其餘時間使用深色主題；無座標時暫時使用 `isSystemInDarkTheme()`。`MainActivity` 使用 `enableEdgeToEdge` 與 `SystemBarStyle` 同步狀態列及系統 Navigation Bar。

## R8 規則

`app/proguard-rules.pro` 保留 Retrofit 執行期註解及 API 介面。`prerelease` 建置會產生：

```text
app/build/outputs/mapping/prerelease/mapping.txt
```

若分析壓縮版崩潰，需保留同一次建置的 mapping 檔案。

## 測試重點

- 22 縣市映射不重複且資料集 ID 格式正確。
- `Place.title` 格式。
- MOENV Object、Array 及純文字回應解析。

目前尚未涵蓋 Compose UI 自動化、實際 API 契約測試與定位整合測試。
