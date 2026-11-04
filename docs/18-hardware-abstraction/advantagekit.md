# AdvantageKit

--8<-- "alpha-warning.md"

## 這頁學什麼

- 安裝 AdvantageKit 2027 alpha，修改 `build.gradle`
- 三種執行模式：真的機器人、模擬、重播
- `LoggedRobot` 和 `Logger` 的設定
- `@AutoLog`、`processInputs`（輸入）和 `recordOutput`（輸出）
- 在 AdvantageScope 看 AdvantageKit 的資料

## 觀念

### AdvantageKit 是什麼

**AdvantageKit** 是 FRC 6328 隊（Mechanical Advantage）開發的 log 框架。[第 11 章](../11-practice/logging.md) 的 `DataLogManager` 會記錄 NetworkTables 上的資料；AdvantageKit 更進一步，記錄**所有**進到程式的資料（感測器、Driver Station、手把），而且記錄的方式讓它可以被**重播**。

它是 6328 隊自己在用的工具，也是 AdvantageScope 的作者寫的，兩者整合得很好。

### 三種執行模式

| 模式 | 什麼時候 | IO 用哪一種 | log 寫到哪裡 |
| --- | --- | --- | --- |
| `REAL` | 部署到機器人上 | `ModuleIOReal`、`GyroIOOnboard` | 機器人上的 log 檔 + NetworkTables |
| `SIM` | 在電腦上模擬 | `ModuleIOSim`、空的 `GyroIO` | 專案的 `logs` 資料夾 + NetworkTables |
| `REPLAY` | 在電腦上重播一份 log | 全部是空的 IO | 原本的 log 旁邊，多一份 `_sim` 結尾的檔案 |

部署到機器人上時一定是 `REAL`。在電腦上執行時，由 `Constants.SIM_MODE` 決定是模擬還是重播。

### 輸入和輸出

AdvantageKit 把資料分成兩種：

| | 輸入 (Inputs) | 輸出 (Outputs) |
| --- | --- | --- |
| 是什麼 | 從外面進到程式的資料：感測器、手把 | 程式算出來的結果：Pose、目標值 |
| 怎麼記錄 | `Logger.processInputs("名稱", inputs)` | `Logger.recordOutput("名稱", 值)` |
| 重播時 | 從 log **讀回來**，蓋掉 `inputs` | **重新計算**，寫到新的 log |
| 在 log 裡的位置 | `/Drivetrain/...` | `/RealOutputs/...`（重播時多一份 `/ReplayOutputs/...`） |

`processInputs` 是硬體抽象化和重播的關鍵：一般執行時，它把 `inputs` **寫進** log；重播時，它反過來從 log **讀出**同一個時間點的值，填進 `inputs`。邏輯拿到的 `inputs` 一模一樣，算出的輸出也就一樣。

### `@AutoLog` 在做什麼

要把 `ModuleIOInputs` 的每個欄位寫進 log、再讀回來，需要一段「每個欄位都要寫一行」的程式。**`@AutoLog`** 讓 AdvantageKit 在編譯時自動產生這段程式，產生的類別叫 **`ModuleIOInputsAutoLogged`**（原本的名字加上 `AutoLogged`）。所以 `SwerveModule` 裡建立的是：

```java
private final ModuleIOInputsAutoLogged inputs = new ModuleIOInputsAutoLogged();
```

這種「在編譯時根據註解產生程式碼」的工具叫 **Annotation processor（註解處理器）**，要在 `build.gradle` 裡加上才會執行。

## 步驟

### 1. 安裝 vendordep

AdvantageKit 2027 alpha 還不在 WPILib 的 vendordep 清單裡，要用 JSON 的網址安裝：

1. 在 VS Code 按 ++ctrl+shift+p++，選 **WPILib: Manage Vendor Libraries**
2. 選 **Install new libraries (online)**
3. 貼上這個網址：

```text
https://github.com/Mechanical-Advantage/AdvantageKit/releases/download/v27.0.0-alpha-7/AdvantageKit.json
```

!!! warning "注意：不要用「檢查更新」更新 AdvantageKit"
    AdvantageKit 的 vendordep 檔案裡記錄的更新網址指向 GitHub 的「最新正式版」，撰寫本章時（2026 年 11 月）那是 **2026** 的 v26.0.2，不能用在 WPILib 2027。要更新 2027 alpha 時，到 [AdvantageKit 的 Releases](https://github.com/Mechanical-Advantage/AdvantageKit/releases) 找最新的 `v27.0.0-alpha-…`，用它的 `AdvantageKit.json` 重新安裝。

### 2. 修改 `build.gradle`

照 AdvantageKit 範本的設定，改四個地方：

```groovy
// Set this to true to enable desktop support.
def includeDesktopSupport = true

// AdvantageKit：log replay 用的任務
task(replayWatch, type: JavaExec) {
    mainClass = "org.littletonrobotics.junction.ReplayWatch"
    classpath = sourceSets.main.runtimeClasspath
}
```

在 `dependencies { ... }` 裡加上註解處理器：

```groovy
    // AdvantageKit：讓 @AutoLog 自動產生程式碼
    def akitJson = new groovy.json.JsonSlurper().parseText(new File(projectDir.getAbsolutePath() + "/vendordeps/AdvantageKit.json").text)
    annotationProcessor "org.littletonrobotics.akit:akit-autolog:$akitJson.version"
```

把 Sim GUI 的預設值改成**關閉**：

```groovy
wpi.sim.addGui().defaultEnabled = false
```

重播時不能有任何 HAL 的模擬擴充（Sim GUI 也是其中一個），所以預設關閉。**模擬時要開車**，在 VS Code 選 **WPILib: Simulate Robot Code** 之後，自己勾選 **Sim GUI**。

改完之後要在有網路的時候編譯一次，下載 AdvantageKit 和它的電腦版函式庫。

## 範例

### Constants：執行模式

在 `Constants` 的最上面加上：

```java
/** AdvantageKit 的執行模式。 */
public enum Mode {
  /** 真的機器人。 */
  REAL,
  /** 在電腦上模擬。 */
  SIM,
  /** 在電腦上重播 log 檔。 */
  REPLAY
}

/** 在電腦上執行時要用哪一種模式：模擬（SIM）還是重播（REPLAY）。 */
public static final Mode SIM_MODE = Mode.SIM;
/** 現在的模式：部署到機器人上時一定是 REAL。 */
public static final Mode CURRENT_MODE = RobotBase.isReal() ? Mode.REAL : SIM_MODE;
```

`RobotBase` 在 `org.wpilib.framework.RobotBase`。

### Robot

`Robot` 改成繼承 AdvantageKit 的 **`LoggedRobot`**（取代 `TimedRobot`），並在建立 `RobotContainer` **之前**設定好 `Logger`：

```java title="src/main/java/first/robot/Robot.java"
package first.robot;

import org.littletonrobotics.junction.LogFileUtil;
import org.littletonrobotics.junction.LoggedRobot;
import org.littletonrobotics.junction.Logger;
import org.littletonrobotics.junction.networktables.NT4Publisher;
import org.littletonrobotics.junction.wpilog.WPILOGReader;
import org.littletonrobotics.junction.wpilog.WPILOGWriter;
import org.wpilib.command2.Command;
import org.wpilib.command2.CommandScheduler;

public class Robot extends LoggedRobot {
  private Command autonomousCommand;

  private final RobotContainer robotContainer;

  public Robot() {
    // Logger 要在建立任何 Subsystem 之前設定好
    switch (Constants.CURRENT_MODE) {
      case REAL -> {
        // 真的機器人：寫到隨身碟上的 log 檔，同時送到 NetworkTables
        Logger.addDataReceiver(new WPILOGWriter());
        Logger.addDataReceiver(new NT4Publisher());
      }
      case SIM -> {
        // 模擬：也寫一份 log 檔（在專案的 logs 資料夾），之後可以拿來練習重播
        Logger.addDataReceiver(new WPILOGWriter());
        Logger.addDataReceiver(new NT4Publisher());
      }
      case REPLAY -> {
        // 重播：讀取 log 檔，用最快的速度跑，結果寫到 _sim 結尾的新檔案
        setUseTiming(false);
        String logPath = LogFileUtil.findReplayLog();
        Logger.setReplaySource(new WPILOGReader(logPath));
        Logger.addDataReceiver(new WPILOGWriter(LogFileUtil.addPathSuffix(logPath, "_sim")));
      }
    }
    Logger.start();

    robotContainer = new RobotContainer();
  }

  @Override
  public void robotPeriodic() {
    CommandScheduler.getInstance().run();
  }

  @Override
  public void autonomousInit() {
    autonomousCommand = robotContainer.getAutonomousCommand();
    if (autonomousCommand != null) {
      CommandScheduler.getInstance().schedule(autonomousCommand);
    }
  }

  @Override
  public void autonomousExit() {
    if (autonomousCommand != null) {
      autonomousCommand.cancel();
    }
  }

  @Override
  public void utilityInit() {
    CommandScheduler.getInstance().cancelAll();
  }
}
```

- **`WPILOGWriter`**：把資料寫成 `.wpilog` 檔，AdvantageScope 可以直接打開
- **`NT4Publisher`**：同時送到 NetworkTables，AdvantageScope 可以即時連線看
- **`Logger.start()`** 之後才能建立 Subsystem：Subsystem 的建構子可能會讀硬體，那時候 `Logger` 要已經在運作
- 重播時 **`setUseTiming(false)`**：不用等 20 ms，用電腦最快的速度跑完整份 log

### 在 AdvantageScope 看資料

模擬時，AdvantageScope 選 **File → Connect to Simulator**。AdvantageKit 的資料都在 **`AdvantageKit`** 底下：

| 名稱 | 內容 |
| --- | --- |
| `AdvantageKit/Drivetrain/Module/FrontLeft/...` | 左前模組的輸入資料：位置、速度、電流、角度 |
| `AdvantageKit/Drivetrain/Gyro/...` | IMU 的輸入資料 |
| `AdvantageKit/RealOutputs/Drivetrain/Pose` | 機器人在場地上的位置 |
| `AdvantageKit/RealOutputs/Drivetrain/ModuleVelocities` | 四個模組實際的速度和角度，拖到 Swerve 分頁 |
| `AdvantageKit/DriverStation/...` | Driver Station 的狀態、手把的輸入 |

模擬時寫出的 log 檔在專案的 **`logs`** 資料夾，檔名像是 `akit_26-11-04_16-15-33.wpilog`。[下一頁](replay.md) 會用它來練習重播。

## 常見錯誤

!!! bug "常見錯誤：找不到 `ModuleIOInputsAutoLogged`"
    `build.gradle` 沒有加 `annotationProcessor` 那一行，`@AutoLog` 不會產生程式碼。加上之後重新編譯（VS Code 有時候要重新載入 Java 專案才會認得新產生的類別）。

!!! bug "常見錯誤：模擬時沒有 Sim GUI"
    `defaultEnabled = false` 之後，模擬預設不會打開 Sim GUI。選 **WPILib: Simulate Robot Code** 時記得勾選它，不然沒辦法啟用機器人、接手把。

!!! bug "常見錯誤：在 `Logger.start()` 之前建立 Subsystem"
    把 `robotContainer = new RobotContainer()` 寫在 `Logger` 設定之前，Subsystem 的建構子執行時 `Logger` 還沒開始，重播時讀不到那段時間的資料。順序一定是：設定 `Logger` → `Logger.start()` → 建立 `RobotContainer`。

## 小練習

1. 在模擬裡開一陣子車，關掉模擬後，用 AdvantageScope 打開 `logs` 資料夾裡的 log 檔，找到 `RealOutputs/Drivetrain/Pose`，在 2D Field 上回放剛剛開的路線。
2. `Vision` 和 `TeleopDrive` 裡還有一些 `Telemetry.log`。把它們改成 `Logger.recordOutput`，在 AdvantageScope 的 `RealOutputs` 底下找到它們。
3. 為什麼 `REAL` 模式也要寫 log 檔，不能只送到 NetworkTables？

??? question "第 3 題參考答案"
    NetworkTables 只有在電腦連著機器人的時候才看得到，而且不會自動保存。比賽時駕駛站的電腦不一定一直開著 AdvantageScope，FMS 的網路也可能不穩。log 檔寫在機器人上，**整場比賽的所有資料**都會留下來，賽後才能拿回來分析和重播。
