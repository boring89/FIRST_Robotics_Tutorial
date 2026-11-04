# 保持朝向

--8<-- "alpha-warning.md"

## 這頁學什麼

- 為什麼放開右搖桿之後，機器人的朝向還是會慢慢偏掉
- **Heading lock（保持朝向）**：沒有在轉的時候，用 PID 鎖住朝向
- 等機器人轉慢下來才鎖定
- 一鍵轉到指定方向：按十字鍵轉到 0、90、180、−90 度

## 觀念

### 朝向為什麼會偏

駕駛放開右搖桿，`omega` 是 0，但機器人的朝向不一定會保持不變：

- 被其他機器人**撞到**、擦過
- 一邊平移一邊加減速時，輪子打滑
- 四個模組的反應不完全一樣（[第 14 章](../14-swerve-tuning/driver-input.md#discretize一邊走一邊轉) 的斜移）

`omega = 0` 只代表「不要主動轉」，沒有人會把它**轉回來**。駕駛只好一直用右搖桿微調。

### Heading lock

放開右搖桿時，記下當時的朝向，之後用一個 PID 保持在這個朝向：

```text
駕駛在轉     → omega = 右搖桿 × 最高旋轉速度，取消鎖定
駕駛放開     → 鎖定當時的朝向
鎖定中       → omega = PID(目前朝向, 鎖定的朝向)
```

254 的 2022 程式碼裡也有同樣功能的 [`SwerveHeadingController`](https://github.com/Team254/FRC-2022-Public/blob/main/src/main/java/com/team254/lib/control/SwerveHeadingController.java)。這個 PID 和[第 8 章的 `TurnToAngle`](../08-control/pid.md) 一樣，只是輸出直接當作 `omega`（弧度 / 秒）。`HEADING_KP = 5.0` 的意思是：差 1 弧度（約 57 度）時，用 5 rad/s 轉回來。

### 等轉慢下來再鎖定

駕駛高速旋轉時放開右搖桿，機器人還會**繼續轉一小段**才停下來。如果在放開的那一瞬間就鎖定，PID 會把機器人**拉回**放開時的朝向，駕駛會覺得機器人「彈回去」。

所以放開之後，先讓 `omega = 0`，等量到的旋轉速度小於 `HEADING_LOCK_SPEED`（0.3 rad/s），才鎖定**那時候**的朝向。

### 一鍵轉向

既然有了「鎖定的朝向」，只要把它改成指定的角度，PID 就會自動轉過去。例如十字鍵往上，就轉到「遠離駕駛」的方向。角度用**駕駛視角**（[第 16 章](../16-swerve-auto/odometry.md#場地座標和駕駛視角)）：0 度是遠離駕駛，紅色聯盟要加 180 度。

### 為什麼寫成一個 Command 類別

保持朝向需要**記住**「鎖定的朝向」，而且要能從外面（十字鍵）改它。這種需要狀態、又有好幾個步驟的邏輯，寫成一個 `Command` 類別比 `run(() -> ...)` 清楚（[第 5 章](../05-commands/commands.md)）。

## 範例

### Constants

```java
// ---------- 保持朝向 ----------
/** 朝向差 1 弧度時，轉多快（弧度 / 秒）。 */
public static final double HEADING_KP = 5.0;
/** 機器人轉得比這個慢（弧度 / 秒），才開始鎖定朝向。 */
public static final double HEADING_LOCK_SPEED = 0.3;
```

### TeleopDrive

```java title="src/main/java/first/robot/commands/TeleopDrive.java"
package first.robot.commands;

import first.robot.Constants.SwerveConstants;
import first.robot.subsystems.Drivetrain;
import java.util.function.DoubleSupplier;
import org.wpilib.command2.Command;
import org.wpilib.math.controller.PIDController;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.ChassisVelocities;

/** 駕駛用的 Command：放開右搖桿時，自動保持機器人的朝向。 */
public class TeleopDrive extends Command {
  private final Drivetrain drivetrain;
  private final DoubleSupplier forward;
  private final DoubleSupplier left;
  private final DoubleSupplier rotation;
  private final PIDController headingController =
      new PIDController(SwerveConstants.HEADING_KP, 0.0, 0.0);

  /** 要保持的朝向（場地座標）。null 代表目前沒有鎖定。 */
  private Rotation2d targetHeading = null;

  /** 三個輸入的範圍都是 -1～1，和 Drivetrain.driveCommand 一樣。 */
  public TeleopDrive(
      Drivetrain drivetrain, DoubleSupplier forward, DoubleSupplier left, DoubleSupplier rotation) {
    this.drivetrain = drivetrain;
    this.forward = forward;
    this.left = left;
    this.rotation = rotation;
    headingController.enableContinuousInput(-Math.PI, Math.PI);
    addRequirements(drivetrain);
  }

  /**
   * 讓機器人轉到指定的朝向，之後一直保持。
   *
   * @param degrees 駕駛視角的角度：0 是遠離駕駛，90 是駕駛的左邊
   */
  public void snapTo(double degrees) {
    Rotation2d heading = Rotation2d.fromDegrees(degrees);
    targetHeading = drivetrain.isRedAlliance() ? heading.plus(Rotation2d.PI) : heading;
  }

  @Override
  public void initialize() {
    targetHeading = null;
  }

  @Override
  public void execute() {
    Rotation2d current = drivetrain.getPose().getRotation();
    double input = rotation.getAsDouble();
    double omega;

    if (input != 0.0) {
      // 駕駛在轉：照搖桿轉，取消鎖定
      targetHeading = null;
      omega = input * SwerveConstants.MAX_ANGULAR_SPEED;
    } else {
      // 駕駛放開右搖桿：等機器人轉慢下來，再鎖定當時的朝向
      double measuredOmega = drivetrain.getRobotRelativeVelocities().omega;
      if (targetHeading == null && Math.abs(measuredOmega) < SwerveConstants.HEADING_LOCK_SPEED) {
        targetHeading = current;
        headingController.reset();
      }
      omega = targetHeading == null
          ? 0.0
          : headingController.calculate(current.getRadians(), targetHeading.getRadians());
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

- **`targetHeading = null`** 代表沒有鎖定。`initialize()` 時清掉，每次這個 Command 重新開始都不會沿用舊的鎖定
- **`input != 0.0`**：右搖桿經過 `shape()` 的 Deadband，放開時剛好是 0
- **`headingController.reset()`**：鎖定新的朝向時，清掉 PID 之前的狀態
- **`snapTo()`** 是一般的方法，不是 Command。它只改 `targetHeading`，下一次 `execute()` 就會開始轉

### RobotContainer

```java title="src/main/java/first/robot/RobotContainer.java"
package first.robot;

import com.pathplanner.lib.auto.AutoBuilder;
import first.robot.Constants.OperatorConstants;
import first.robot.commands.AimAtTarget;
import first.robot.commands.TeleopDrive;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Vision;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.button.CommandGamepad;
import org.wpilib.math.util.MathUtil;
import org.wpilib.tunable.Selectable;
import org.wpilib.tunable.Tunables;

public class RobotContainer {
  private final Drivetrain drivetrain = new Drivetrain();
  private final Vision vision = new Vision(drivetrain);

  private final CommandGamepad driver = new CommandGamepad(OperatorConstants.DRIVER_CONTROLLER_PORT);

  private final TeleopDrive teleopDrive =
      new TeleopDrive(
          drivetrain,
          () -> shape(-driver.getLeftY()) * speedScale(),
          () -> shape(-driver.getLeftX()) * speedScale(),
          () -> shape(-driver.getRightX()) * speedScale());

  private final Selectable<Command> autoChooser;

  public RobotContainer() {
    autoChooser = buildAutoChooser();
    Tunables.publish("Auto Chooser", autoChooser);

    configureDefaultCommands();
    configureBindings();
  }

  private Selectable<Command> buildAutoChooser() {
    if (AutoBuilder.isConfigured()) {
      return AutoBuilder.buildAutoChooser();   // 包含所有 PathPlanner 的自動程式
    }
    Selectable<Command> chooser = new Selectable<>();
    chooser.addDefault("什麼都不做", Commands.none());
    return chooser;
  }

  /** 搖桿中間 10% 不動作，其他部分取平方：推一點點時更好微調。 */
  private static double shape(double input) {
    return MathUtil.copyDirectionPow(MathUtil.applyDeadband(input, 0.1), 2);
  }

  /** 按住左肩鍵時，速度降到 30%。 */
  private double speedScale() {
    return driver.leftBumper().getAsBoolean() ? 0.3 : 1.0;
  }

  private void configureDefaultCommands() {
    drivetrain.setDefaultCommand(teleopDrive);
  }

  private void configureBindings() {
    driver.start().onTrue(drivetrain.resetHeadingCommand());
    driver.faceLeft().whileTrue(drivetrain.lockCommand());
    // 按住右扳機時，一邊移動一邊對準目標
    driver.rightTrigger().whileTrue(
        new AimAtTarget(
            drivetrain, vision, () -> shape(-driver.getLeftY()), () -> shape(-driver.getLeftX())));

    // 十字鍵：轉到駕駛視角的 0、90、180、-90 度
    driver.dpadUp().onTrue(Commands.runOnce(() -> teleopDrive.snapTo(0)));
    driver.dpadLeft().onTrue(Commands.runOnce(() -> teleopDrive.snapTo(90)));
    driver.dpadDown().onTrue(Commands.runOnce(() -> teleopDrive.snapTo(180)));
    driver.dpadRight().onTrue(Commands.runOnce(() -> teleopDrive.snapTo(-90)));
  }

  public Command getAutonomousCommand() {
    return autoChooser.getSelected();
  }
}
```

- **`teleopDrive` 存成欄位**：十字鍵要呼叫同一個物件的 `snapTo()`
- **`Commands.runOnce(() -> teleopDrive.snapTo(90))`**：沒有指定 Subsystem，所以**不會**中斷正在執行的 `teleopDrive`，只是改它的目標
- **慢速模式**改成 `speedScale()`：按住左肩鍵時三個輸入都乘上 0.3。這樣慢速時也一樣有保持朝向，不用另外建立一個 Command

### 在模擬裡看到的

| 動作 | 結果 |
| --- | --- |
| 右搖桿推 30% 轉 0.5 秒後放開 | 朝向從 63 度繼續轉到約 94 度才停下，鎖定在那裡 |
| 鎖定後，往前、再往左各平移 1 秒 | 朝向從 93.7 度變成 94.8 度 |
| 靜止時按十字鍵往上（轉到 0 度） | 約 0.6 秒轉到，之後保持在 ±1.3 度以內 |

## 常見錯誤

!!! bug "常見錯誤：放開右搖桿時機器人彈回去"
    一放開就鎖定，沒有等機器人轉慢下來。檢查有沒有判斷 `HEADING_LOCK_SPEED`。

!!! bug "常見錯誤：PID 沒有開 Continuous input"
    鎖定在 170 度，機器人被撞到 −170 度，PID 會繞一大圈轉回去。和[轉向的 PID](../13-swerve-drive/module.md#角度是一個圓) 一樣，要 `enableContinuousInput(-Math.PI, Math.PI)`。

!!! bug "常見錯誤：鎖定時朝向一直小幅抖動"
    `HEADING_KP` 太大。先調小，或是加一點容許誤差：誤差小於 1 度時 `omega` 直接給 0。

!!! bug "常見錯誤：十字鍵的 Command 中斷了 TeleopDrive"
    寫成 `drivetrain.runOnce(() -> teleopDrive.snapTo(90))`：`drivetrain.runOnce` 會佔用 `Drivetrain`，按下的那一瞬間 `TeleopDrive` 被中斷再重新開始，`initialize()` 把剛設定的目標清掉了。用 `Commands.runOnce`，不要佔用任何 Subsystem。

## 小練習

1. 加上容許誤差：鎖定時誤差小於 1 度，`omega` 直接給 0。在模擬裡比較抖動有沒有變小。
2. 加一個按鈕：按住時朝向一直對著場地上的某個點（例如得分區，用 `Pose2d` 算出角度），駕駛只控制平移。
3. 自動對準目標（[第 16 章的 `AimAtTarget`](../16-swerve-auto/vision.md)）和保持朝向都在控制 `omega`。如果想讓 `TeleopDrive` 看到目標時自動對準、看不到時保持朝向，要怎麼改？

??? question "第 1 題參考答案"
    在 `execute()` 計算 PID 的地方改成：

    ```java
    double error = targetHeading.minus(current).getDegrees();
    omega = Math.abs(error) < 1.0
        ? 0.0
        : headingController.calculate(current.getRadians(), targetHeading.getRadians());
    ```

    `minus` 之後的 `getDegrees()` 在 −180～180 度之間，所以不用另外處理繞一圈的問題。

??? question "第 2 題參考答案"
    用目標點和機器人位置的差，算出要面對的方向：

    ```java
    /** 讓機器人一直面向場地上的一個點。 */
    public void faceToward(Translation2d point) {
      Translation2d toPoint = point.minus(drivetrain.getPose().getTranslation());
      // 機器人剛好在那個點上時沒有方向，getAngle() 會回傳空的 Optional
      toPoint.getAngle().ifPresent(angle -> targetHeading = angle);
    }
    ```

    2027 的 `Translation2d.getAngle()` 回傳的是 `Optional<Rotation2d>`，用 `ifPresent` 只在有方向的時候更新。

    綁在按鈕上，按住時每次迴圈都呼叫：`driver.rightBumper().whileTrue(Commands.run(() -> teleopDrive.faceToward(得分區的位置)))`。`Commands.run` 一樣不佔用 Subsystem。
