# 用時間控制

--8<-- "alpha-warning.md"

## 這頁學什麼

- 用 `withTimeout` 寫出最簡單的自動程式
- 把自動程式整理在 `Autos` 類別裡
- 為什麼用時間控制不準

## 觀念

### 開 2 秒

最簡單的自動程式：以 40% 的速度往前開 2 秒，然後停下來。

```java
drivetrain.arcadeDriveCommand(() -> 0.4, () -> 0.0).withTimeout(2.0)
```

- `arcadeDriveCommand` 是[第 7 章](../07-drivetrain/differential.md#範例) 寫的，參數是兩個 `DoubleSupplier`。這裡不需要讀搖桿，直接用 `() -> 0.4` 回傳固定的值
- `withTimeout(2.0)`：2 秒後中斷這個 Command
- Command 結束後，底盤的 default command（搖桿開車）會接手。自動階段沒有人推搖桿，搖桿的值是 0，所以底盤會停下來

### `Autos` 類別

自動程式越來越多時，把它們整理成一個類別的 `static` 方法，放在 `commands/Autos.java`。範本建立專案時就附了一個 `Autos.java`，我們把它改寫成自己的：

```java title="src/main/java/first/robot/commands/Autos.java"
package first.robot.commands;

import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import org.wpilib.command2.Command;

public final class Autos {
  /** 往前開 2 秒，離開起始區。 */
  public static Command driveForwardTimed(Drivetrain drivetrain) {
    return drivetrain.arcadeDriveCommand(() -> 0.4, () -> 0.0)
        .withTimeout(2.0)
        .withName("DriveForwardTimed");
  }

  /** 發射預載的遊戲物件，再往後開 1.5 秒。 */
  public static Command shootThenBackUpTimed(Drivetrain drivetrain, Intake intake, Shooter shooter) {
    return ScoringCommands.shoot(intake, shooter)
        .andThen(drivetrain.arcadeDriveCommand(() -> -0.4, () -> 0.0).withTimeout(1.5))
        .withName("ShootThenBackUpTimed");
  }

  private Autos() {}
}
```

在 `RobotContainer` 把它們加進[自動程式選單](index.md#選擇自動程式selectable)：

```java
autoChooser.addDefault("什麼都不做", Commands.none());
autoChooser.add("往前開 2 秒", Autos.driveForwardTimed(drivetrain));
autoChooser.add("發射後後退", Autos.shootThenBackUpTimed(drivetrain, intake, shooter));
```

### 為什麼用時間控制不準

「以 40% 的速度開 2 秒」實際走多遠，取決於：

- **電池電量**：電量低時馬達比較慢，走得比較短
- **地面摩擦**：比賽場地和練習場地的地毯不一樣
- **輪子打滑、被撞**：自動階段其他機器人也在動

所以用時間控制只適合「離開起始區」這種不需要精準的動作。要走到特定的位置，需要[編碼器](encoder-based.md)。

!!! tip "小技巧：TimedRobot 的寫法"
    如果不是用 Command-based，在 `TimedRobot` 裡可以用 WPILib 的 `Timer` 計時：在 `autonomousInit()` 呼叫 `timer.restart()`，在 `autonomousPeriodic()` 用 `timer.get()` 讀取經過的秒數。

## 常見錯誤

!!! bug "常見錯誤：同一個 Command 物件放進選單兩次"
    `Autos.driveForwardTimed(drivetrain)` 每次呼叫都會建立新的 Command。如果把同一個物件同時放進選單和別的組合裡，會出現「已經被組合過」的錯誤（見 [組合 Command](../05-commands/composition.md#常見錯誤)）。

!!! bug "常見錯誤：自動程式結束後機器人還在動"
    如果底盤沒有 default command，`withTimeout` 結束後馬達會停在最後的輸出。Command 的 `end` 要記得停止馬達，或確保 default command 會接手。

## 小練習

1. 把 `Autos.java` 和選單加進你的專案，在 Elastic 上選擇自動程式，用 Driver Station 切換到 Autonomous 測試。
2. 寫一個自動程式：往前開 1 秒，原地向左轉 0.5 秒，再往前開 1 秒。電池充飽和電量較低時，結果有什麼不同？
3. 在 `autoChooser` 加一個「只發射」的選項。

??? question "第 2 題參考答案"
    ```java
    public static Command driveTurnDriveTimed(Drivetrain drivetrain) {
      return drivetrain.arcadeDriveCommand(() -> 0.4, () -> 0.0).withTimeout(1.0)
          .andThen(drivetrain.arcadeDriveCommand(() -> 0.0, () -> 0.4).withTimeout(0.5))
          .andThen(drivetrain.arcadeDriveCommand(() -> 0.4, () -> 0.0).withTimeout(1.0))
          .withName("DriveTurnDriveTimed");
    }
    ```

    電量低時馬達比較慢：兩段直線都**走得比較短**，轉彎的**角度也比較小**，最後停的位置會差很多。這就是為什麼要改用[編碼器](encoder-based.md)和 IMU。

??? question "第 3 題參考答案"
    ```java
    autoChooser.add("只發射", ScoringCommands.shoot(intake, shooter));
    ```
