# 建立第一個專案

--8<-- "alpha-warning.md"

## 這頁學什麼

- 用 WPILib 範本建立一個 Command-based 機器人專案
- 看懂專案資料夾裡每個檔案的用途
- 編譯程式
- 加入 REV / CTRE 的廠商函式庫

## 觀念

### 範本 (Template)

WPILib 提供了很多 **Template（範本）**，建立專案時選一個，就會自動產生基本的程式架構。2027 的 Java 範本有：

| 範本 | 說明 |
| --- | --- |
| **Command v2 Robot** | Command-based（v2）架構，有註解和範例程式。**本教學使用這個** |
| Command v2 Robot Skeleton (Advanced) | 同上，但沒有註解和範例 |
| Command v3 Robot | 新的 Commands v3 架構 |
| OpMode Robot | 2027 新增的 OpMode 架構，和 FTC 的寫法類似 |
| Timed Robot | 最基本的架構，所有程式寫在 `Robot.java` 裡 |
| Educational Robot | 教學用，不適合比賽 |

另外還有 Romi、XRP 開頭的範本，是給這兩種小型教學機器人用的。

## 步驟

### 1. 建立專案

1. 打開 **2027 WPILib VS Code**
2. 按 ++ctrl+shift+p++ 打開 Command Palette，輸入並選擇 **WPILib: Create a new project**
3. 在打開的頁面依序設定：
    - **Project Type**：Template
    - **Language**：java
    - **Project Base**：Command v2 Robot
    - **Base Folder**：選一個放專案的資料夾
    - **Project Name**：專案名稱，例如 `MyFirstRobot`
    - **Team Number**：你們的隊號
4. 按 **Generate Project**，問你要不要開啟專案時，選 **Yes (Current Window)**

!!! tip "小技巧：專案放哪裡"
    專案資料夾的路徑**不要有中文和空格**，例如 `C:\Users\you\FRC\MyFirstRobot`。路徑有中文或空格時，某些工具可能會出錯。也不要放在 OneDrive、Google 雲端硬碟這類會自動同步的資料夾。

### 2. 看懂專案結構

建立好的專案長這樣：

```text
MyFirstRobot/
├── build.gradle                  ← 專案設定：WPILib 版本、相依套件
├── settings.gradle
├── gradlew, gradlew.bat          ← Gradle 執行檔（不用動）
├── gradle/                       ← Gradle 相關檔案（不用動）
├── .wpilib/
│   └── wpilib_preferences.json   ← 隊號、WPILib 年度
├── vendordeps/
│   └── CommandsV2.json           ← 廠商函式庫清單
└── src/main/
    ├── deploy/                   ← 要一起部署到 Systemcore 的檔案（例如路徑檔）
    └── java/first/
        ├── Main.java             ← 程式進入點（不用動）
        └── robot/
            ├── Robot.java        ← 機器人主類別：各模式的進入點
            ├── RobotContainer.java ← 建立 Subsystem、綁定按鍵
            ├── Constants.java    ← 常數：CAN ID、手把編號等
            ├── commands/         ← 放 Command
            │   ├── Autos.java
            │   └── ExampleCommand.java
            └── subsystems/       ← 放 Subsystem
                └── ExampleSubsystem.java
```

你平常會改的檔案都在 `src/main/java/first/robot/` 裡面。其他檔案除非知道自己在做什麼，否則不要動。

!!! info "2027 新改動：package 名稱"
    2026 以前，專案的 package 是 `frc.robot`；2027 改成 **`first.robot`**。WPILib 本身的 package 也從 `edu.wpi.first` 改成 **`org.wpilib`**，例如 `org.wpilib.command2.Command`。網路上舊的範例程式要改過 `import` 才能用。

!!! info "2027 新改動：Commands v2 是一個 vendordep"
    2027 起 Command-based 函式庫也放在 `vendordeps/` 裡。用 Command v2 範本建立專案時會自動加入 `CommandsV2.json`。同一個專案**不能同時**使用 Commands v2 和 v3。

### 3. 編譯程式

按 ++ctrl+shift+p++，選擇 **WPILib: Build Robot Code**。第一次編譯會比較久。下方的終端機最後出現：

```text
BUILD SUCCESSFUL
```

就代表成功了。如果出現 `BUILD FAILED`，往上捲找第一個 `error:`，那一行會告訴你是哪個檔案的第幾行出錯。

### 4. 加入廠商函式庫

要控制 REV 或 CTRE 的馬達，需要加入對應的 **Vendor dependency（廠商函式庫，簡稱 vendordep）**。

1. 點左側活動列的 **WPILib 圖示**，打開 **WPILib Vendor Dependencies** 面板
2. 在 **Available Dependencies** 清單裡找到要的函式庫，按 **Install**
3. 安裝完成後，`vendordeps/` 資料夾會多出一個 `.json` 檔
4. 再編譯一次。這次需要網路，Gradle 會下載函式庫

=== "REV"

    在清單裡找 **REVLib** 安裝。清單裡找不到時，用 Command Palette 執行 **WPILib: Manage Vendor Libraries** → **Install new libraries (online)**，貼上：

    ```text
    https://software-metadata.revrobotics.com/REVLib-2027.json
    ```

=== "CTRE"

    在清單裡找 **CTRE-Phoenix (v6)** 安裝，確認版本是 **26.70.0-alpha-2**（對應 WPILib 2027 alpha-7 / alpha-8）。

    注意版本號：CTRE 的 2027 alpha 版本號是 **26.x**，不是 27.x，這是正常的。

!!! warning "注意：vendordep 版本要和 WPILib 對應"
    alpha 期間每個版本的 WPILib 都需要對應版本的 vendordep。WPILib 升級到新的 alpha 時，記得 vendordep 也要一起更新，對照表在 [第 3 章首頁](index.md#版本對照)。

## 常見錯誤

!!! bug "常見錯誤：照著舊教學寫 `import edu.wpi.first...`"
    網路上大部分的範例都是 2026 以前的，`import` 寫的是 `edu.wpi.first.wpilibj...`，在 2027 會編譯錯誤 `package edu.wpi.first... does not exist`。要改成 `org.wpilib...` 開頭。

!!! bug "常見錯誤：改了 package 或類別名稱，程式就不能跑了"
    `Main.java` 裡寫死了 `first.robot.Robot::new`。如果你把 `Robot` 改名或搬到別的 package，`Main.java` 也要跟著改。

!!! bug "常見錯誤：第一次加 vendordep 後編譯失敗"
    加入 vendordep 後的第一次編譯需要網路下載函式庫。在沒有網路的地方（例如比賽會場）才第一次編譯，會失敗。**出發比賽前，一定要在有網路的地方先編譯過一次。**

## 小練習

1. 建立一個 **Command v2 Robot** 專案並編譯成功。
2. 打開 `.wpilib/wpilib_preferences.json`，找到你設定的隊號。
3. 打開 `Constants.java`，找到手把的編號常數。它的命名符合 [變數與型別](../01-java/variables.md) 講的常數命名慣例嗎？
4. 依照隊上使用的馬達，加入 REVLib 或 Phoenix 6，再編譯一次。
