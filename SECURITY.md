# 安全政策

## 支援範圍

目前只維護最新的 `master` 分支與最新 prerelease 建置。

## 回報安全問題

請勿在公開 Issue 張貼 API Key、裝置識別資訊、精確位置或可直接利用的敏感細節。請透過 GitHub 儲存庫擁有者的公開聯絡方式進行私下回報，並提供：

- 受影響版本與 Android 版本
- 問題重現步驟
- 預期與實際結果
- 風險及可能影響
- 建議修正方式（若有）

## 安全設計摘要

- API Key 使用 Android Keystore 管理的 AES-GCM 金鑰加密。
- 所有資料服務使用 HTTPS。
- APP 不使用自有後端。
- 不要求背景定位。
- Git 忽略簽章金鑰、建置輸出及本機設定。

Debug 或 prerelease APK 使用 Debug 金鑰簽署，不應用於正式公開發行。
