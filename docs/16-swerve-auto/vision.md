# 視覺

--8<-- "alpha-warning.md"

## 這頁學什麼

- 直接沿用第 10 章的 `Vision`，用 AprilTag 修正 Swerve 的位置
- 一邊移動一邊對準目標：駕駛控制平移，PID 控制旋轉
- 模擬裡沒有相機時會怎樣

## 觀念

### `Vision` 不用改

[第 10 章的 `Vision`](../10-vision/limelight-photonvision.md) 只需要 `Drivetrain` 提供兩個方法：

| 方法 | 用途 |
| --- | --- |
| `getPose()` | MegaTag2 需要知道機器人的朝向 |
| `addVisionMeasurement(pose, timestamp, stdDevs)` | 把視覺算出的位置交給 Pose estimator |

[上一頁的 `Drivetrain`](pathplanner.md#drivetrain) 已經有這兩個方法，所以 `Vision.java` 可以**原封不動**從第 10 章複製過來。這就是「Subsystem 對外只提供需要的方法」的好處：底盤從差速換成 Swerve，視覺的程式完全不用改。

### 一邊移動一邊對準

差速底盤對準目標時只能原地轉、或前進後退（[第 10 章的 `AimAtTarget`](../10-vision/limelight-photonvision.md)）。Swerve 的平移和旋轉是獨立的，所以可以：

- **平移**：駕駛用左搖桿控制，和平常一樣
- **旋轉**：交給 PID，讓 tx 變成 0

```text
目標在右邊 → tx = +10° → PID 輸出 −1 rad/s → 機器人順時針轉 → tx 變小
```

`AIM_KP = 0.1` 的意思是：差 1 度時轉 0.1 rad/s。PID 的輸出直接就是 `ChassisVelocities` 的 `omega`，單位是弧度 / 秒。

## 範例

### Constants

```java
public static final class VisionConstants {
  public static final String LIMELIGHT_NAME = "limelight";
  /** 對準目標的 P：差 1 度時，轉 0.1 弧度 / 秒。 */
  public static final double AIM_KP = 0.1;
  public static final double AIM_TOLERANCE_DEGREES = 1.0;
}
```

`AIM_KP` 和第 10 章的 0.02 不一樣：第 10 章的輸出是 `arcadeDrive` 的旋轉比例（−1～1），這裡是弧度 / 秒。

### AimAtTarget

```java title="src/main/java/first/robot/commands/AimAtTarget.java"
package first.robot.commands;

import first.robot.Constants.SwerveConstants;
import first.robot.Constants.VisionConstants;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Vision;
import java.util.function.DoubleSupplier;
import org.wpilib.command2.Command;
import org.wpilib.math.controller.PIDController;
import org.wpilib.math.kinematics.ChassisVelocities;

/** 駕駛控制移動的方向，機器人自動轉向對準目標。 */
public class AimAtTarget extends Command {
  private final Drivetrain drivetrain;
  private final Vision vision;
  private final DoubleSupplier forward;
  private final DoubleSupplier left;
  private final PIDController controller = new PIDController(VisionConstants.AIM_KP, 0.0, 0.0);

  public AimAtTarget(Drivetrain drivetrain, Vision vision, DoubleSupplier forward, DoubleSupplier left) {
    this.drivetrain = drivetrain;
    this.vision = vision;
    this.forward = forward;
    this.left = left;
    controller.setSetpoint(0.0);
    controller.setTolerance(VisionConstants.AIM_TOLERANCE_DEGREES);
    addRequirements(drivetrain);   // Vision 只是讀資料，不需要佔用
  }

  @Override
  public void initialize() {
    controller.reset();
  }

  @Override
  public void execute() {
    double omega = 0.0;
    if (vision.hasTarget()) {
      // 目標在右邊（tx 為正）時，PID 輸出負值，機器人順時針轉
      double limit = SwerveConstants.MAX_ANGULAR_SPEED * 0.5;
      omega = Math.clamp(controller.calculate(vision.getTXDegrees()), -limit, limit);
    }
    drivetrain.driveDriverRelative(
        new ChassisVelocities(
            forward.getAsDouble() * SwerveConstants.MAX_SPEED,
            left.getAsDouble() * SwerveConstants.MAX_SPEED,
            omega));
  }

  @Override
  public void end(boolean interrupted) {
    drivetrain.stop();
  }
}
```

- 平移的輸入和 `driveCommand` 一樣是 −1～1，乘上 `MAX_SPEED`
- 旋轉最多用一半的最高旋轉速度，避免離目標很遠時轉太猛
- 呼叫 **`driveDriverRelative`**：駕駛推搖桿往前，機器人就往遠離駕駛的方向走，不管機器人現在面向哪裡

[上一頁的 `RobotContainer`](pathplanner.md#robotcontainer) 把它綁在右扳機上：按住時一邊開一邊對準，放開後回到一般的駕駛。

### 在模擬裡

模擬裡沒有 Limelight，`hasTarget()` 永遠是 `false`：

- `Vision` 不會加入任何視覺資料，Pose estimator 只靠 Odometry
- `AimAtTarget` 的旋轉一直是 0，只剩駕駛控制平移

所以模擬可以測試「按住右扳機時還能正常移動」，但測不到對準的效果。PhotonVision 的函式庫有模擬相機的功能（`VisionSystemSim`），可以在模擬裡產生 AprilTag 的資料，但它在 2027 的支援狀況要先確認（見[第 10 章](../10-vision/limelight-photonvision.md)）。

## 常見錯誤

!!! bug "常見錯誤：機器人越轉離目標越遠"
    `omega` 的正負號反了。Limelight 的 tx 是目標在**右邊**時為正，WPILib 的 `omega` 是**逆時針**為正。PID 的目標是 0、量到的是 tx，`calculate(tx)` 在 tx 為正時會輸出負值，剛好是順時針。不要再自己加負號。

!!! bug "常見錯誤：對準時機器人往旁邊滑"
    一邊平移一邊旋轉時的偏移（[第 14 章](../14-swerve-tuning/driver-input.md#discretize一邊走一邊轉)）。`driveRobotRelative` 裡已經有 `discretize`，如果還是很明顯，可以試試[第 14 章延伸學習](../14-swerve-tuning/further.md#斜移補償) 的補償方法。

## 小練習

1. 修改 `AimAtTarget`，讓它在對準之後（`controller.atSetpoint()`）讓手把震動一下，提醒駕駛可以發射了。
2. 現在的 `AimAtTarget` 看不到目標時就不轉。改成看不到目標時，讓駕駛可以用右搖桿手動旋轉。
3. 比較 `AIM_KP` 是 0.05、0.1、0.2 時的對準速度和穩定度。

??? question "第 2 題參考答案"
    多收一個 `DoubleSupplier rotation`，看不到目標時用它：

    ```java
    double omega = rotation.getAsDouble() * SwerveConstants.MAX_ANGULAR_SPEED;
    if (vision.hasTarget()) {
      double limit = SwerveConstants.MAX_ANGULAR_SPEED * 0.5;
      omega = Math.clamp(controller.calculate(vision.getTXDegrees()), -limit, limit);
    }
    ```

    建構子多一個參數，`RobotContainer` 傳入 `() -> shape(-driver.getRightX())`。
