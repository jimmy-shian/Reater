# 平台圖示（Threads / Instagram / 全部）－ 來源與授權說明

主畫面「3 合 1」平台篩選按鈕所使用的三個圖示。

## 來源

1. **Instagram** — Simple Icons
   - 官方頁：https://simpleicons.org/icons/instagram
   - 原始路徑：`instagram.svg`（viewBox `0 0 24 24`，單一填色 path）

2. **Threads** — Simple Icons
   - 官方頁：https://simpleicons.org/icons/threads
   - 原始路徑：`threads.svg`（viewBox `0 0 24 24`，單一填色 path）

3. **全部（九宮格）** — Material Symbols「apps」
   - 官方頁：https://fonts.google.com/icons?selected=Material+Symbols+Outlined:apps
   - 原始路徑：`all.svg`（viewBox `0 0 24 24`，單一填色 path）

## 授權

- **Simple Icons**：CC0-1.0（public domain 貢獻），可自由使用、修改、商用，無須署名。
- **Material Symbols**：Apache License 2.0，可商用；本檔僅取用單一 path data，並在
  App 內以 Compose `Icon` 的 `tint` 依主題著色，未內嵌 Material 字型檔。

## App 內對應檔案

| SVG | VectorDrawable | 用途 |
| --- | --- | --- |
| `instagram.svg` | `app/src/main/res/drawable/ic_instagram.xml` | IG 平台篩選圖示 |
| `threads.svg` | `app/src/main/res/drawable/ic_threads.xml` | Threads 平台篩選圖示 |
| `all.svg` | `app/src/main/res/drawable/ic_platform_all.xml` | 全部平台篩選圖示 |

## 路徑正規化說明

Simple Icons 的 SVG 為壓縮格式，數字之間沒有分隔（例如 `-1.2768.0602`）。Android 的
`PathParser` 會把同一個數字裡的第二個小數點視為格式錯誤，因此在轉成 VectorDrawable 時
將路徑改寫為「每個指令獨立、數字以空白分隔」的形式：

```
M 7.0301 0.084 C -1.2768 0.0602 -2.1487 0.264 -2.911 0.5634 ...
```

改寫前後以 96×96 點陣比對，渲染結果**逐像素完全相同**（diff = 0）。

下載日期：2026-11-01
