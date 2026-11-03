# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 用命令列編譯和部署

VS Code 的 WPILib 指令背後其實是在執行 Gradle。你也可以在專案資料夾的終端機直接執行：

=== "Windows"

    ```bash
    .\gradlew build
    .\gradlew deploy
    ```

=== "macOS / Linux"

    ```bash
    ./gradlew build
    ./gradlew deploy
    ```

在 WPILib VS Code 內建的終端機執行最保險，因為它已經設定好要用 WPILib 附的 Java。

## 模擬

沒有機器人也可以執行機器人程式：Command Palette 選 **WPILib: Simulate Robot Code**，程式會在你的電腦上執行，搭配模擬介面就能測試按鍵和自動程式。[第 4 章的延伸學習](../04-robot-basics/further.md) 會介紹。

## 升級 2026 的舊專案

2027 改動很多，舊專案不能直接打開來用。用 Command Palette 的 **WPILib: Import a WPILib 2025-2026 Gradle project**，它會建立一個新的 2027 專案，並自動修正一部分不相容的地方（例如 package 名稱）。匯入之後通常還要手動修改一些地方，依照編譯錯誤一個一個處理。

從較早的 2027 alpha 升級時，也建議用匯入的方式，並看該版本的 [版本說明](https://github.com/wpilibsuite/allwpilib/releases) 有沒有要手動修改的地方。

## 比賽前的離線準備

比賽會場的網路通常很差，甚至沒有網路。出發前：

- [ ] 所有程式組筆電都裝好**同一個版本**的 WPILib
- [ ] 每台筆電都在有網路的地方，把比賽用的專案**完整編譯過一次**，確定 vendordep 都下載好了
- [ ] 下載 vendordep 的離線安裝檔（例如 REVLib 提供的 offline zip），以防需要在沒有網路的筆電上安裝
- [ ] 準備好 Systemcore 映像檔和各裝置韌體的檔案

WPILib 安裝程式的 **Create archives** 選項可以把 VS Code 存成離線安裝檔，方便在沒有網路的電腦上安裝。

## 用 Git 管理專案

WPILib 建立的專案已經附了 `.gitignore`，可以直接用 Git 管理。[第 11 章 用 Git 分工](../11-practice/git.md) 會詳細介紹。現在可以先做的事：建立專案後就 `git init`，每次改完能動的版本就 commit 一次，改壞了才有辦法回到上一個能動的版本。
