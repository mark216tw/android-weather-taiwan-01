# 貢獻指南

感謝你願意協助改善「台灣天氣」。提交變更前，請先閱讀以下規範。

## 開發流程

1. Fork 本儲存庫並建立功能分支。
2. 使用 JDK 17 與 Android SDK 35。
3. 遵循現有 Kotlin、Compose 與資料層結構。
4. 不得提交 API Key、簽章金鑰、個人位置或其他敏感資料。
5. 為資料解析或重要行為新增測試。
6. 在提交 Pull Request 前執行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assemblePrerelease
```

## Commit 與 Pull Request

- Commit 訊息應簡潔描述目的。
- Pull Request 請說明問題、解法、測試結果及畫面變更。
- UI 變更請附淺色與深色模式截圖。
- API 行為變更請註明資料集及回應格式。

## 程式風格

- 優先採最小、清楚且可測試的修改。
- UI 不直接解析遠端 JSON。
- 不在 Log、例外或畫面中輸出完整 API Key。
- 使用 MaterialTheme 語意色，避免在畫面中大量寫死色碼。

提交貢獻即表示你同意所提交內容以 MIT License 授權。
