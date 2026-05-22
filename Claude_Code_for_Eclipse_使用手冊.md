# Claude Code for Eclipse 使用手冊

> 版本：1.1.0 | 最後更新：2026-05-20

---

## 目錄

1. [環境需求](#1-環境需求)
2. [匯入專案到 Eclipse PDE](#2-匯入專案到-eclipse-pde)
3. [設定 API Key](#3-設定-api-key)
4. [啟動測試](#4-啟動測試)
5. [功能與快捷鍵](#5-功能與快捷鍵)
6. [打包發布（.jar）](#6-打包發布jar)
7. [安裝到現有 Eclipse](#7-安裝到現有-eclipse)
8. [專案結構說明](#8-專案結構說明)
9. [常見問題](#9-常見問題)

---

## 1. 環境需求

| 項目 | 需求 |
|------|------|
| Java | 11 或以上（建議 17） |
| Eclipse | 2022-03 或以上（需含 PDE）|
| Node.js | 18 或以上（用於安裝 Claude Code CLI）|
| Claude Code CLI | `npm install -g @anthropic-ai/claude-code` |
| Claude 帳號 | Claude Team / Enterprise（透過 CLI OAuth 登入）|

> **不需要 Anthropic API Key！** 本外掛透過 Claude Code CLI 呼叫 Claude，
> 使用您的 Claude Team 帳號 OAuth 驗證，無需另外申請 API Key。

> **Eclipse 版本確認**：`Help > About Eclipse IDE`，需看到 Plug-in Development Environment（PDE）。
> 若未安裝 PDE，請至 `Help > Install New Software` 安裝。

---

## 2. 匯入專案到 Eclipse PDE

### 步驟一：開啟 Import 對話框

```
File > Import...
```

### 步驟二：選擇匯入類型

```
Plug-in Development > Plug-ins and Fragments
```
點選 **Next**。

### 步驟三：指定來源目錄

- **Import From**：選擇 `Directory`
- **Directory**：輸入或瀏覽至

```
D:\VSCode_Java\extension\Claude_Code_for_eclipse
```

點選 **Next**。

### 步驟四：選取插件

勾選 `com.holtek.claudecode`，點選 **Finish**。

### 步驟五：確認匯入成功

Package Explorer 中應出現 `com.holtek.claudecode` 專案，且沒有紅色錯誤標記。

> 若出現錯誤，請確認 Target Platform 包含所需的 Eclipse 套件（見步驟 2-補充）。

### 步驟二補充：設定 Target Platform（如有錯誤）

```
Window > Preferences > Plug-in Development > Target Platform
```

確認目前 Target 包含：
- `org.eclipse.ui`
- `org.eclipse.core.runtime`
- `org.eclipse.core.resources`
- `org.eclipse.ui.console`
- `org.eclipse.jdt.ui`（選用）

若 Target 為空，點選 **Add** → **Default** → **Finish** → **Apply and Close**。

---

## 3. 安裝與登入 Claude Code CLI

### 步驟一：安裝 CLI

在終端（命令提示字元 / PowerShell）執行：

```bash
npm install -g @anthropic-ai/claude-code
```

### 步驟二：登入 Claude Team 帳號

```bash
claude
```

瀏覽器將自動開啟 Claude 登入頁面。使用您的 Claude Team 帳號完成 OAuth 授權後，CLI 即會記住登入狀態。

> 登入僅需執行一次。之後 Eclipse 外掛直接呼叫 CLI，不再需要瀏覽器。

### 步驟三：在 Eclipse 設定 CLI 路徑

```
Window > Preferences > Claude Code
```

| 欄位 | 說明 |
|------|------|
| **Claude CLI 路徑** | 通常保留預設 `claude` 即可；若找不到，填入完整路徑，例如 `C:\Users\<你的帳號>\AppData\Roaming\npm\claude.cmd` |
| **Claude 模型** | 建議選擇 `claude-sonnet-4-6 (建議)` |

點選 **測試連線** 確認 CLI 可正常執行，然後 **Apply and Close**。

> 設定儲存在 Eclipse 的 Preference Store，重啟後仍保留。

---

## 4. 啟動測試

### 建立 Run Configuration

```
Run > Run Configurations...
```

左側選擇 **Eclipse Application**，點選左上角 **New Configuration** 圖示。

設定如下：

| 欄位 | 值 |
|------|----|
| **Name** | `Claude Code Test` |
| **Workspace** | 保留預設或指定測試 workspace |
| **Location** | 保留預設 |

點選 **Run**。

### 在測試 Eclipse 中驗證

新的 Eclipse 視窗開啟後：

1. 按 **Alt+Shift+C** → 應出現 Claude Chat 面板
2. 點選 `Claude` 主選單 → 確認所有功能項目存在
3. 輸入一則測試訊息，確認 API 呼叫正常

---

## 5. 功能與快捷鍵

### Chat 面板

| 快捷鍵 | 功能 |
|--------|------|
| `Alt+Shift+C` | 開啟 Claude Chat 面板 |
| `Ctrl+Enter`（在輸入框內）| 送出訊息 |

### 編輯器動作（需先選取程式碼）

| 快捷鍵 | 功能 | 說明 |
|--------|------|------|
| `Alt+Shift+E` | **Explain Code** | 解釋選取程式碼的功能與邏輯 |
| `Alt+Shift+F` | **Fix / Improve Code** | 找 Bug、效能問題並提供修正 |
| `Alt+Shift+A` | **Ask Claude...** | 對話框輸入自訂問題，自動附帶選取程式碼 |

> 上述功能也可透過**右鍵選單 → Claude 子選單**觸發。

### 測試與文件

| 快捷鍵 | 功能 | 說明 |
|--------|------|------|
| `Alt+Shift+T` | **Generate Unit Tests** | 產生 JUnit 5 + Mockito 單元測試 |
| *(右鍵 → Claude → Add Comments)* | **Add Comments** | 加上 Javadoc 與行內註解 |

### 程式碼套用

| 快捷鍵 | 功能 | 說明 |
|--------|------|------|
| `Alt+Shift+P` | **Apply Code to Editor** | 開啟 Diff 預覽對話框，套用 Claude 建議的程式碼 |

Apply 對話框功能：
- 左側顯示現有程式碼，右側顯示 Claude 建議（可手動編輯後再套用）
- 綠色行 = 新增，紅色行 = 移除
- 可選擇「套用到選取範圍」、「套用到整個檔案」或「複製到剪貼簿」
- 套用後可用 `Ctrl+Z` 復原

### 專案與除錯

| 快捷鍵 | 功能 | 說明 |
|--------|------|------|
| `Alt+Shift+D` | **Debug with Claude** | 擷取 Console 輸出（Stack Trace）傳給 Claude 診斷 |
| `Alt+Shift+R` | **Fix Compile Errors** | 讀取 Eclipse Problems 清單，請 Claude 修正編譯錯誤 |
| `Alt+Shift+J` | **Ask about Project** | 附帶完整專案結構摘要（pom.xml、套件樹）提問 |

### Project Context 切換（Chat 面板 Header）

點選 Chat 面板右上角的 **📁 Project** 切換按鈕：

- **開啟**：每則對話訊息自動在前面附上專案摘要（pom.xml 依賴、套件結構等），讓 Claude 理解整體背景
- **關閉**：一般對話模式，不附帶專案資訊

---

## 6. 打包發布（.jar）

### 方法一：Export as Deployable Plugin

```
File > Export > Plug-in Development > Deployable plug-ins and fragments
```

- **Plugins**：勾選 `com.holtek.claudecode`
- **Destination**：選擇輸出目錄（例如 `D:\output\`）
- 點選 **Finish**

輸出的 `com.holtek.claudecode_1.0.0.jar` 即可安裝到任意 Eclipse。

### 方法二：Export as Update Site（建議用於團隊分發）

```
File > Export > Plug-in Development > Deployable features
```

需先建立一個 Feature Project 包含此 Plugin。

---

## 7. 安裝到現有 Eclipse

### 手動安裝（.jar 方式）

1. 將 `com.holtek.claudecode_1.0.0.jar` 複製到 Eclipse 的 `dropins/` 目錄：
   ```
   C:\Users\<你的帳號>\eclipse\<版本>\eclipse\dropins\
   ```
2. 重新啟動 Eclipse：
   ```
   Help > Restart
   ```
3. 前往 `Window > Preferences > Claude Code` 輸入 API Key

### 確認安裝成功

```
Help > About Eclipse IDE > Installation Details > Plug-ins
```
搜尋 `com.holtek.claudecode`，應出現版本 1.0.0。

---

## 8. 專案結構說明

```
com.holtek.claudecode/
├── META-INF/MANIFEST.MF          # Bundle 宣告、依賴清單
├── plugin.xml                    # 指令、Handler、選單、快捷鍵宣告
├── build.properties              # PDE 建置設定
└── src/com/holtek/claudecode/
    ├── Activator.java            # 插件啟動入口（Singleton）
    │
    ├── api/                      # Claude CLI 通訊層
    │   ├── ChatMessage.java      # 對話訊息 POJO（role + content）
    │   ├── StreamCallback.java   # 串流回呼介面
    │   └── ClaudeCliClient.java  # Claude CLI 子程序呼叫（stream-json 解析）
    │
    ├── views/
    │   └── ClaudeView.java       # 主要 Chat UI（SWT 深色主題）
    │
    ├── preferences/
    │   ├── ClaudePreferencePage.java        # API Key 設定頁
    │   └── ClaudePreferenceInitializer.java # 預設值
    │
    ├── editor/
    │   └── EditorContextHelper.java  # 從 Eclipse Editor 取得程式碼與檔名
    │
    ├── apply/                    # 程式碼套用功能
    │   ├── CodeBlock.java        # 程式碼區塊 POJO
    │   ├── CodeBlockParser.java  # 解析 markdown ``` 圍欄
    │   └── ApplyCodeDialog.java  # 左右 Diff 預覽對話框
    │
    ├── context/                  # 專案與環境 Context 提供者
    │   ├── ProjectContextProvider.java  # 掃描專案結構、解析 pom.xml
    │   ├── ConsoleOutputCapture.java    # 讀取 Eclipse Console 輸出
    │   └── ProblemsHelper.java          # 讀取 Eclipse 編譯錯誤標記
    │
    └── handlers/                 # 指令 Handler（9 個）
        ├── OpenClaudeHandler.java        # 開啟面板
        ├── EditorActionHandler.java      # 抽象基底 Handler
        ├── ExplainCodeHandler.java       # 解釋程式碼
        ├── FixCodeHandler.java           # 修正程式碼
        ├── AddCommentsHandler.java       # 加 Javadoc
        ├── GenerateTestsHandler.java     # 產生測試
        ├── AskWithCodeHandler.java       # 自訂問題
        ├── ApplyCodeHandler.java         # 套用 Diff
        ├── DebugWithClaudeHandler.java   # Console 除錯
        ├── FixErrorsHandler.java         # 修正編譯錯誤
        └── ProjectChatHandler.java       # 專案 Context 對話
```

---

## 9. 常見問題

### Q1：Chat 面板顯示「找不到 Claude Code CLI」

請確認：
1. 已安裝 CLI：`npm install -g @anthropic-ai/claude-code`
2. 已登入：在終端執行 `claude`，完成瀏覽器 OAuth 授權
3. CLI 路徑正確：`Window > Preferences > Claude Code`，點選**測試連線**驗證

若 CLI 安裝在非標準位置，請填入完整路徑（例如 `C:\Users\<帳號>\AppData\Roaming\npm\claude.cmd`）。

### Q2：出現「尚未登入 Claude Code CLI」錯誤

在終端執行 `claude`，瀏覽器將開啟 Claude 登入頁面。
使用 Claude Team 帳號登入後即可正常使用。

### Q3：回應速度較慢

CLI 每次呼叫需啟動 Node.js 程序，首次呼叫約需 1–2 秒。
後續呼叫速度正常。若明顯較慢，請確認網路連線正常。

### Q4：Console 輸出無法擷取（Debug with Claude 無反應）

確認：
1. Eclipse Console 視窗有輸出內容（`Window > Show View > Console`）
2. 程式必須已執行過且產生輸出

### Q5：右鍵選單沒有出現 Claude 子選單

確認目前開啟的是文字編輯器（例如 Java Editor）。非文字編輯器（如 Properties 視圖）不顯示 Claude 選單。

### Q6：Apply Code 套用後想還原

使用 `Ctrl+Z`（Undo）即可還原。Eclipse 的 Document undo 支援多步驟復原。

### Q7：如何更換 Claude 模型

```
Window > Preferences > Claude Code > Claude 模型
```

可選擇：
- `claude-sonnet-4-6`（建議，速度與品質平衡）
- `claude-opus-4-6`（最高品質，較慢）
- `claude-haiku-4-5`（最快，適合簡單問答）

### Q8：想在多台電腦使用，需要重新設定嗎？

是的，每台電腦需要：
1. 各自安裝 Claude Code CLI（`npm install -g @anthropic-ai/claude-code`）
2. 各自執行 `claude` 完成 OAuth 登入
3. Eclipse 中確認 CLI 路徑設定正確

---

*Claude Code for Eclipse — Built with Claude Code CLI (claude.ai Team OAuth)*
