# 把結果填回去

--8<-- "alpha-warning.md"

## 這頁學什麼

- 三個測試的結果分別填到哪裡
- 別忘了 PathPlanner 的機器人設定
- 怎麼確認量測之後真的變好了
- 什麼時候要重新量

## 觀念

### 填到哪裡

| 測試 | 量到的 | `Constants` | PathPlanner 的 Robot Config |
| --- | --- | --- | --- |
| [輪子半徑](wheel-radius.md) | 半徑 r | `WHEEL_DIAMETER_METERS = 2 × r` | Wheel Radius |
| [打滑電流](slip-current.md) | 打滑電流 I、摩擦係數 μ | `DRIVE_CURRENT_LIMIT`（比 I 低一點，取整數）、`WHEEL_COF = μ` | Drive Current Limit、Wheel COF |
| [SysId](drive-sysid.md) | kS、kV、kA（四個模組的平均） | `DRIVE_KS`、`DRIVE_KV`、`DRIVE_KA` | （不用填） |

例如量到半徑 0.0497 公尺、打滑電流 55 A、摩擦係數 1.15，SysId 的平均是 kS 0.17、kV 0.82、kA 0.061：

```java
public static final double WHEEL_DIAMETER_METERS = 0.0994;    // 量到的半徑 0.0497 × 2
public static final double DRIVE_CURRENT_LIMIT = 52;          // 打滑電流 55 A，留一點餘裕
public static final double DRIVE_KS = 0.17;                   // SysId，四個模組的平均
public static final double DRIVE_KV = 0.82;
public static final double DRIVE_KA = 0.061;
public static final double WHEEL_COF = 1.15;                  // 打滑電流算出的摩擦係數
```

電流限制設得比打滑電流**低一點**（大約 5%）：電池電壓、地毯的狀況每場都不一樣，留一點餘裕比較不會打滑。

[第 17 章的 Setpoint Generator](../17-swerve-advanced/setpoint-generator.md) 用 `WHEEL_COF` 和 `DRIVE_CURRENT_LIMIT` 決定機器人最多能加速多快，這兩個值準了，它算出的加速度才會剛好用滿抓地力。

### 別忘了 PathPlanner

[第 16 章](../16-swerve-auto/pathplanner.md) 的 `configurePathPlanner()` 用的是 `RobotConfig.fromGUISettings()`：PathPlanner 跟隨路徑時用的機器人設定，是在 **PathPlanner 程式的 Settings → Robot Config** 裡填的，**不是** `Constants`。改了 `Constants` 之後，PathPlanner 那邊也要一起改，否則路徑規劃用的還是舊的數字。

## 範例

### Constants

`OperatorConstants` 加上測試手把的連接埠：

```java
public static final int TESTER_CONTROLLER_PORT = 1;
```

### RobotContainer

```java title="src/main/java/first/robot/RobotContainer.java"
package first.robot;

import com.pathplanner.lib.auto.AutoBuilder;
import first.robot.Constants.OperatorConstants;
import first.robot.commands.AimAtTarget;
import first.robot.commands.SlipCurrentTest;
import first.robot.commands.TeleopDrive;
import first.robot.commands.WheelRadiusCharacterization;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Vision;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.button.CommandGamepad;
import org.wpilib.command2.sysid.SysIdRoutine;
import org.wpilib.math.util.MathUtil;
import org.wpilib.tunable.Selectable;
import org.wpilib.tunable.Tunables;

public class RobotContainer {
  private final Drivetrain drivetrain = new Drivetrain();
  private final Vision vision = new Vision(drivetrain);

  private final CommandGamepad driver = new CommandGamepad(OperatorConstants.DRIVER_CONTROLLER_PORT);
  /** 量測用的第二支手把，比賽時不用接。 */
  private final CommandGamepad tester = new CommandGamepad(OperatorConstants.TESTER_CONTROLLER_PORT);

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

    configureCalibrationBindings();
  }

  /** 量測用的按鈕：都是按住才執行，放開就停。 */
  private void configureCalibrationBindings() {
    tester.faceUp().whileTrue(drivetrain.sysIdQuasistatic(SysIdRoutine.Direction.FORWARD));
    tester.faceDown().whileTrue(drivetrain.sysIdQuasistatic(SysIdRoutine.Direction.REVERSE));
    tester.faceRight().whileTrue(drivetrain.sysIdDynamic(SysIdRoutine.Direction.FORWARD));
    tester.faceLeft().whileTrue(drivetrain.sysIdDynamic(SysIdRoutine.Direction.REVERSE));
    tester.leftBumper().whileTrue(new WheelRadiusCharacterization(drivetrain));
    tester.rightBumper().whileTrue(new SlipCurrentTest(drivetrain));
  }

  public Command getAutonomousCommand() {
    return autoChooser.getSelected();
  }
}
```

量測的按鈕集中在 `configureCalibrationBindings()`，而且都在測試手把上。比賽時不接測試手把，這些按鈕就不會被按到。

## 確認有沒有變好

### 1. Odometry 的距離

1. 在地毯上用捲尺量出 3 公尺，貼兩條膠帶
2. 機器人放在第一條膠帶上，重設位置（`resetPose`）
3. 開到第二條膠帶停下，看 AdvantageScope 的 `Drivetrain/Pose`

X 應該在 3.00 公尺附近（差 2 公分以內）。用改之前的輪子直徑再做一次，比較兩次的差別。

### 2. 速度追得準不準

照[第 14 章調整參數](../14-swerve-tuning/tuning.md#2-驅動的-kvks) 的方法，把 `DRIVE_KP` 設成 0，只靠 Feedforward 開車，比較 `Velocity` 和 `TargetVelocity`。kS、kV、kA 準的話，光靠 Feedforward 就應該追得很好，加速時也不會明顯落後。最後再把 `DRIVE_KP` 加回來。

### 3. 加速時不打滑

搖桿從 0 突然推到底，看 `Drivetrain/Pose` 和實際走的距離。打滑時輪子轉得比機器人走得多，Odometry 會**超過**實際的距離。

### 4. PathPlanner 的路徑

跑一條直線 3 公尺的路徑，終點和膠帶比較。路徑的終點準了，自動階段才可靠。

## 什麼時候要重新量

| 情況 | 要重量的 |
| --- | --- |
| 換胎皮、換輪子 | 輪子半徑、打滑電流 |
| 機器人的重量變了很多（加了機構、換了電池的放法） | 打滑電流（摩擦係數不變，但打滑電流會變）、SysId 的 kA |
| 換了驅動馬達、齒輪比 | 全部 |
| 到了新的比賽場地 | 輪子半徑（地毯不一樣，可以在練習場快速量一下） |

## 常見錯誤

!!! bug "常見錯誤：只改了 `Constants`"
    `Constants` 改了，PathPlanner 的 Robot Config 沒改，自動階段的路徑跟隨用的還是舊的輪子半徑和電流限制。兩邊要一起改。

!!! bug "常見錯誤：一次改好幾個值，結果變差了不知道是哪個"
    三個測試的結果最好**一個一個**填回去，每改一個就確認一次。結果變差時，才知道是哪一個量錯了。

## 小練習

1. 照這一章的三個測試，量出你們機器人的值，填進上面的表格。
2. 比較改之前和改之後，開 3 公尺時 Odometry 差多少？
3. `DRIVE_CURRENT_LIMIT` 改小了，最高速度會變慢嗎？為什麼？

??? question "第 3 題參考答案"
    幾乎不會。定子電流限制的是**扭力**，影響的是加速。等速前進時只需要克服摩擦力，電流很小，不會碰到限制。最高速度主要由電池電壓和 kV 決定（[第 13 章](../13-swerve-drive/hardware.md#最高速度)）。只有在加速、或被推的時候，電流限制才會讓機器人「沒那麼有力」。
