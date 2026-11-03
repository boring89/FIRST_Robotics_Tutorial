# 用編碼器控制

--8<-- "alpha-warning.md"

## 這頁學什麼

- 用編碼器讓底盤走指定的距離
- 結合第 8 章的 `TurnToAngle` 轉到指定方向
- 把走直線和轉彎組合成完整的自動程式

## 觀念

### 走到指定距離為止

[第 7 章的 Drivetrain](../07-drivetrain/differential.md#範例) 已經有 `getDistanceMeters()`，可以知道輪子走了多遠。「走 1.5 公尺」就是：

1. 記下**開始時**的距離
2. 一直往前開
3. 走過的距離（現在 - 開始）達到 1.5 公尺時停下

為什麼不直接把編碼器歸零？因為[下一頁的 Odometry](odometry.md) 會一直用編碼器的累積距離計算位置，中途歸零會讓 Odometry 算錯。記下開始的值再相減，就不會影響其他程式。

在 `Drivetrain` 加上：

```java
private double startDistance = 0.0;

/** 往前（正）或往後（負）走指定的距離，單位：公尺。 */
public Command driveDistanceCommand(double meters) {
  double direction = Math.signum(meters);
  return runOnce(() -> startDistance = getDistanceMeters())
      .andThen(run(() -> arcadeDrive(0.4 * direction, 0.0)))
      .until(() -> Math.abs(getDistanceMeters() - startDistance) >= Math.abs(meters))
      .finallyDo(() -> arcadeDrive(0.0, 0.0))
      .withName("DriveDistance");
}
```

- `arcadeDrive(forward, rotation)` 是[第 8 章](../08-control/pid.md#範例) 加的方法
- `Math.signum(meters)`：取正負號，往後走時速度也要是負的
- `.until(...)`：整個組合在走夠距離時結束
- `.finallyDo(...)`：不管是正常結束還是被中斷，最後都停止底盤

!!! tip "小技巧：更準確的做法"
    固定用 40% 的速度開，到了才突然停，機器人會因為慣性多滑一段。進階的做法是用[第 8 章](../08-control/pid.md)的 PID 控制距離，越接近目標開得越慢。不過下一頁的 PathPlanner 會幫你處理這些，所以這裡用簡單的做法就好。

### 轉到指定方向

[第 8 章的 `TurnToAngle`](../08-control/pid.md#範例) 用 IMU 和 PID 轉到指定的朝向，直接拿來用：

```java
new TurnToAngle(drivetrain, 180)
```

注意 `TurnToAngle` 的角度是 IMU 的朝向。自動階段開始時，機器人的朝向就是 IMU 開機（或重設）時的方向，所以**每次比賽擺放機器人的方向要一致**。

## 範例

把走直線和轉彎組合起來：發射後往後走 1.5 公尺、轉 180 度、再往前走 1 公尺去吸下一個遊戲物件。

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

  /** 發射預載的遊戲物件，往後走 1.5 公尺。 */
  public static Command shootThenBackUp(Drivetrain drivetrain, Intake intake, Shooter shooter) {
    return ScoringCommands.shoot(intake, shooter)
        .andThen(drivetrain.driveDistanceCommand(-1.5))
        .withName("ShootThenBackUp");
  }

  /** 發射、後退、轉身，一邊往前走一邊吸下一個遊戲物件。 */
  public static Command shootAndPickUp(Drivetrain drivetrain, Intake intake, Shooter shooter) {
    return ScoringCommands.shoot(intake, shooter)
        .andThen(drivetrain.driveDistanceCommand(-1.5))
        .andThen(new TurnToAngle(drivetrain, 180))
        .andThen(drivetrain.driveDistanceCommand(1.0).deadlineFor(intake.intakeCommand()))
        .withName("ShootAndPickUp");
  }

  private Autos() {}
}
```

最後一步的 `driveDistanceCommand(1.0).deadlineFor(intake.intakeCommand())`：一邊往前走、一邊吸，走完 1 公尺就結束（如果先拿到物件，Intake 會自己停，底盤繼續走完）。

## 常見錯誤

!!! bug "常見錯誤：走的距離和預期差很多"
    先檢查 `getDistanceMeters()` 準不準：推著機器人走 1 公尺，看讀值是不是 1。不準的話，檢查 `GEAR_RATIO` 和 `WHEEL_DIAMETER_METERS`（[第 7 章小練習](../07-drivetrain/differential.md#小練習)）。

!!! bug "常見錯誤：往後走卻一直往前"
    `driveDistanceCommand(-1.5)` 的速度方向用 `Math.signum` 決定，結束條件用 `Math.abs` 比較。如果自己改寫時忘了其中一個，機器人可能會往錯的方向一直開，因為「距離」永遠達不到。

!!! bug "常見錯誤：轉彎後方向不對"
    `TurnToAngle(drivetrain, 180)` 是轉到**朝向 180 度**，不是「再轉 180 度」。如果機器人開始時不是朝向 0 度，結果就會不一樣。

## 小練習

1. 寫一個自動程式：往前走 2 公尺、向左轉到 90 度、再往前走 1 公尺。用捲尺量量看，機器人最後停在哪裡，和預期差多少？
2. 修改 `driveDistanceCommand`，加上逾時保護：最多執行 5 秒。為什麼自動程式的每個步驟都應該有逾時保護？
3. 在 `autoChooser` 加入 `shootThenBackUp` 和 `shootAndPickUp`。
