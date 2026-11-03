# 用 AdvantageScope 看 log

--8<-- "alpha-warning.md"

## 這頁學什麼

- Telemetry 和 log 的差別
- 用 `DataLogManager` 把資料記錄到檔案
- 從 Systemcore 下載 log，用 AdvantageScope 回放比賽
- 用 `Alert` 提醒駕駛

## 觀念

### 即時看 vs 事後看

| | Telemetry（即時） | Log（記錄檔） |
| --- | --- | --- |
| 看的時機 | 機器人執行的當下 | 事後打開檔案看 |
| 需要連線 | 要，電腦要連著機器人 | 不用 |
| 比賽中 | 駕駛站的電腦連得到，但沒空看 | **一直在記錄**，比賽後再分析 |
| 資料會不會遺失 | 連線斷了就沒了 | 存在檔案裡 |

比賽中機器人突然停住 3 秒，Telemetry 沒辦法告訴你發生了什麼，因為當時沒有人在看。**Log 可以**：下場後打開檔案，回到那 3 秒，看電池電壓、馬達電流、Command 的狀態。

### `DataLogManager`

WPILib 的 **`DataLogManager`** 會把資料記錄成 `.wpilog` 檔案。在 `Robot` 的建構子呼叫一次 `DataLogManager.start()`，它就會自動記錄：

- 所有 NetworkTables 的資料，包含 `Telemetry.log()` 送出的所有東西
- `System.out.println()` 印出的訊息

Driver Station 的狀態（模式、Enable、手把輸入）要另外呼叫 `DriverStation.startDataLog(DataLogManager.getLog())` 才會記錄。

log 檔存在：

| 情況 | 位置 |
| --- | --- |
| Systemcore 有插 USB 隨身碟 | 隨身碟裡的 `logs` 資料夾 |
| 沒有插隨身碟 | Systemcore 上的 `/home/systemcore/logs` |

!!! tip "小技巧：插一支隨身碟"
    Systemcore 的內部空間有限，log 檔又會越存越多。比賽時插一支隨身碟，log 會自動存到隨身碟裡，下場後拔下來就能在電腦上看。

### `Alert`：提醒駕駛

有些問題要**立刻**讓駕駛知道，例如相機斷線、某顆馬達控制器沒有回應。**`Alert`** 會把訊息顯示在 Dashboard 上，也會被記錄到 log 裡：

```java
private final Alert disconnectedAlert =
    new Alert("limelightDisconnected", "Limelight 沒有連線", Alert.Level.HIGH);

// 在 periodic() 裡
disconnectedAlert.set(!limelight.isConnected());
```

`set(true)` 時顯示，`set(false)` 時消失。`Level` 有 `HIGH`、`MEDIUM`、`LOW` 三種等級。

!!! info "2027 新改動"
    `Alert` 在 2027 搬到了 `org.wpilib.util` 套件。

## 範例

在範本的 `Robot.java` 的建構子加上兩行：

```java title="src/main/java/first/robot/Robot.java"
package first.robot;

import org.wpilib.command2.Command;
import org.wpilib.command2.CommandScheduler;
import org.wpilib.driverstation.DriverStation;
import org.wpilib.framework.TimedRobot;
import org.wpilib.system.DataLogManager;

public class Robot extends TimedRobot {
  private Command autonomousCommand;

  private final RobotContainer robotContainer;

  public Robot() {
    // 開始記錄 log：NetworkTables、console 輸出
    DataLogManager.start();
    // 也記錄 Driver Station 的狀態（模式、Enable、手把）
    DriverStation.startDataLog(DataLogManager.getLog());

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

在[第 10 章的 Vision](../10-vision/limelight-photonvision.md#vision-subsystem) 加上 Limelight 斷線的提醒：

```java
private final Alert disconnectedAlert =
    new Alert("limelightDisconnected", "Limelight 沒有連線", Alert.Level.HIGH);

@Override
public void periodic() {
  disconnectedAlert.set(!limelight.isConnected());
  // ...原本的程式...
}
```

### 下載和分析 log

1. 比賽結束後，用 **Data Log Tool**（**WPILib: Start Tool**）連上 Systemcore，下載 `.wpilog` 檔（或直接拔下隨身碟）
2. 打開 **AdvantageScope**，選 **File → Open Log(s)**，打開剛剛的檔案
3. 依照想看的東西新增分頁：

| 分頁 | 適合看 | 例子 |
| --- | --- | --- |
| **Line Graph** | 數值隨時間的變化 | `Shooter/RPM`、電池電壓、馬達電流 |
| **2D Field** | 機器人在場上的位置 | `Drivetrain/Pose`、`Vision/Pose` |
| **Table** | 精確的數值、文字 | Command 的名稱、Alert |
| **Console** | 印出的訊息和錯誤 | stack trace |

4. 拖曳下方的時間軸，就能「回到」比賽中的任何時間點

### 用 log 找問題的例子

駕駛說：「第 2 場比賽中間，機器人突然沒力了大概兩秒。」

1. 打開第 2 場的 log，在 Line Graph 加入**電池電壓**
2. 找到電壓掉到很低的時間點，看看是不是 [Brownout](../02-hardware/further.md#電壓不足-brownout)
3. 同一張圖加入各個馬達的電流，找出是哪個機構在那時候吃了大量電流
4. 再看那時候在執行什麼 Command，判斷是駕駛操作的問題，還是程式的問題（例如電流限制沒設）

## 常見錯誤

!!! bug "常見錯誤：比賽後才發現沒有 log"
    忘了呼叫 `DataLogManager.start()`，或是隨身碟滿了。在練習時就確認 log 有正常產生。

!!! bug "常見錯誤：分不出哪個 log 是哪一場"
    連上場地系統 (FMS) 時，`DataLogManager` 會在檔名加上比賽的編號。練習時的 log 則只有時間。每場比賽後記下時間和場次，或比賽後馬上下載並重新命名。

!!! bug "常見錯誤：Telemetry 太少，log 裡找不到需要的資料"
    log 只會記錄你有送出的東西。重要的狀態（感測器讀值、目標值、Command 是否在執行）平常就用 `Telemetry.log()` 送出，出問題時才有資料可以查。

## 小練習

1. 在 `Robot.java` 加上 `DataLogManager.start()`，開幾分鐘的車，下載 log，在 AdvantageScope 的 2D Field 上回放機器人走過的路線。
2. 在 Line Graph 同時顯示 `Shooter/RPM` 和電池電壓，觀察發射時電壓的變化。
3. 為 Shooter 加一個 Alert：如果 Shooter 已經轉了 3 秒還沒到達目標轉速，顯示「Shooter 轉速不足」。
