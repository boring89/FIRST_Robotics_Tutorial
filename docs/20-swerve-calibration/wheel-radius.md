# 輪子半徑

--8<-- "alpha-warning.md"

## 這頁學什麼

- 為什麼規格上的輪子直徑不能直接用
- 用「原地旋轉」量出輪子真正的半徑
- 寫一個量測用的 Command，結果怎麼讀

## 觀念

### 規格上的直徑不等於實際的直徑

輪子的規格寫 4 英寸（0.1016 公尺），但實際在地毯上滾的時候：

- **胎皮會磨損**：用了幾場比賽之後，輪子變小
- **地毯會被壓下去**：機器人的重量讓輪子陷進地毯一點點，有效的半徑變小
- **製造誤差**：不同批的輪子本來就有一點差異

Odometry 用「輪子轉了幾圈 × 圓周長」算出走了多遠。半徑差 2%，距離就差 2%：開 5 公尺差 10 公分，PathPlanner 的路徑終點就跟著偏掉。

### 用 IMU 當尺

要量輪子的半徑，需要一個**和輪子無關**的距離。最方便的是 IMU：

1. 機器人**原地旋轉**。每個模組都繞著機器人中心，在半徑 0.409 公尺的圓上走（模組到中心的距離，`hypot(0.578 / 2, 0.578 / 2)`）
2. IMU 說機器人轉了 θ 弧度，所以每個輪子在地上走了 **θ × 0.409 公尺**
3. 輪子的編碼器說輪子轉了 φ 弧度
4. 輪子在地上走的距離 = 輪子半徑 × 輪子轉的角度，所以：

**輪子半徑 = θ × 0.409 ÷ φ**

例如機器人轉了 2 圈（θ = 4π），輪子轉了 101.2 弧度：半徑 = 4π × 0.409 ÷ 101.2 = 0.0508 公尺。

IMU 量角度很準，原地旋轉時也不受輪子打滑以外的因素影響，所以這是很多隊伍用的方法。[AdvantageKit 的 Swerve 範本](https://github.com/Mechanical-Advantage/AdvantageKit/blob/main/template_projects/sources/spark_swerve/src/main/java/frc/robot/commands/DriveCommands.java) 也有同樣的量測，它轉得更慢（0.25 弧度 / 秒）。如果你們量出來的結果每次差很多，也可以把下面的 `OMEGA` 調小。

## 範例

### SwerveModule 和 Drivetrain：讀出輪子轉過的角度

`SwerveModule` 加上：

```java
/** 輪子總共轉了幾弧度（輪子本身，不是馬達）。量輪子半徑時用。 */
public double getWheelRadians() {
  return driveMotor.getPosition().getValueAsDouble() * 2 * Math.PI;
}
```

驅動馬達設定了 `SensorToMechanismRatio`（[第 13 章](../13-swerve-drive/module.md)），所以 `getPosition()` 已經是**輪子**的圈數，乘以 2π 就是弧度。這裡**不經過**輪子半徑的換算，量出來的才是獨立的資料。

`Drivetrain` 加上：

```java
/** 四個輪子各自轉了幾弧度。 */
public double[] getWheelRadians() {
  double[] radians = new double[modules.length];
  for (int i = 0; i < modules.length; i++) {
    radians[i] = modules[i].getWheelRadians();
  }
  return radians;
}
```

### WheelRadiusCharacterization

```java title="src/main/java/first/robot/commands/WheelRadiusCharacterization.java"
package first.robot.commands;

import first.robot.Constants.SwerveConstants;
import first.robot.subsystems.Drivetrain;
import org.wpilib.command2.Command;
import org.wpilib.math.filter.SlewRateLimiter;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.telemetry.Telemetry;

/** 原地慢慢旋轉，比較 IMU 轉過的角度和輪子轉過的角度，算出輪子真正的半徑。 */
public class WheelRadiusCharacterization extends Command {
  /** 旋轉速度，單位：弧度 / 秒。轉慢一點，輪子比較不會打滑。 */
  private static final double OMEGA = 1.0;
  /** 旋轉速度每秒最多增加多少，單位：弧度 / 秒²。 */
  private static final double RAMP_RATE = 0.5;
  /** 模組到機器人中心的距離，單位：公尺。原地旋轉時，每個輪子都繞著這個半徑的圓走。 */
  private static final double DRIVE_BASE_RADIUS =
      Math.hypot(SwerveConstants.TRACK_WIDTH_METERS / 2, SwerveConstants.WHEEL_BASE_METERS / 2);

  private final Drivetrain drivetrain;
  private final SlewRateLimiter omegaLimiter = new SlewRateLimiter(RAMP_RATE);

  private double[] startWheelRadians;
  private Rotation2d lastHeading;
  private double gyroRadians;
  private double radiusMeters;

  public WheelRadiusCharacterization(Drivetrain drivetrain) {
    this.drivetrain = drivetrain;
    addRequirements(drivetrain);
  }

  @Override
  public void initialize() {
    omegaLimiter.reset(0.0);
    startWheelRadians = drivetrain.getWheelRadians();
    lastHeading = drivetrain.getHeading();
    gyroRadians = 0.0;
    radiusMeters = 0.0;
  }

  @Override
  public void execute() {
    drivetrain.driveRobotRelative(new ChassisVelocities(0.0, 0.0, omegaLimiter.calculate(OMEGA)));

    // IMU：把每 20 ms 轉過的角度累加起來（不受 -180～180 度的限制）
    Rotation2d heading = drivetrain.getHeading();
    gyroRadians += heading.minus(lastHeading).getRadians();
    lastHeading = heading;

    // 輪子：四個輪子從開始到現在轉過的角度，取平均
    double[] wheelRadians = drivetrain.getWheelRadians();
    double wheelSum = 0.0;
    for (int i = 0; i < wheelRadians.length; i++) {
      wheelSum += Math.abs(wheelRadians[i] - startWheelRadians[i]);
    }
    double averageWheelRadians = wheelSum / wheelRadians.length;

    // 機器人轉了 gyroRadians，每個輪子走了 gyroRadians × DRIVE_BASE_RADIUS 公尺。
    // 輪子轉了 averageWheelRadians，所以半徑 = 走的距離 ÷ 轉的角度
    if (averageWheelRadians > 0.0) {
      radiusMeters = Math.abs(gyroRadians) * DRIVE_BASE_RADIUS / averageWheelRadians;
    }

    Telemetry.log("WheelRadius/GyroDegrees", Math.toDegrees(gyroRadians));
    Telemetry.log("WheelRadius/WheelDegrees", Math.toDegrees(averageWheelRadians));
    Telemetry.log("WheelRadius/RadiusMeters", radiusMeters);
  }

  @Override
  public void end(boolean interrupted) {
    drivetrain.stop();
    System.out.printf(
        "轉了 %.0f 度，輪子半徑 %.4f 公尺（直徑 %.4f 公尺）%n",
        Math.toDegrees(gyroRadians), radiusMeters, radiusMeters * 2);
  }
}
```

- **慢慢轉**：最高 1 弧度 / 秒，輪子在地上只走 0.4 公尺 / 秒，不容易打滑。`SlewRateLimiter` 讓速度慢慢增加，一開始不會突然扭一下
- **累加 IMU 的角度**：`getHeading()` 超過 180 度會跳回 -180 度，所以每 20 ms 算一次「這次轉了多少」再加起來，轉幾圈都不會出錯
- **四個輪子取平均**：原地旋轉時，有的輪子往前滾、有的往後滾（看 `optimize` 怎麼選），所以取絕對值
- 用 `driveRobotRelative`（經過 Setpoint Generator），輪子會先轉到旋轉的方向才開始滾

在 `RobotContainer` 綁在測試手把上，**按住**才執行：

```java
tester.leftBumper().whileTrue(new WheelRadiusCharacterization(drivetrain));
```

完整的 `RobotContainer` 在[把結果填回去](results.md#robotcontainer)。

## 步驟

1. 機器人放在比賽用的地毯上，周圍至少留 1 公尺
2. 啟用（Teleop），**按住**測試手把的 `leftBumper`，讓機器人原地轉**至少 2 圈**（大約 15 秒）
3. 放開。終端機會印出：`轉了 737 度，輪子半徑 0.0508 公尺（直徑 0.1017 公尺）`
4. 再做兩次，取平均

轉的圈數越多，IMU 和編碼器的小誤差影響越小。也可以在 AdvantageScope 看 `WheelRadius/RadiusMeters`，轉越久它應該越穩定。

## 模擬實測

在[第 15 章的模擬](../15-swerve-sim/module-sim.md) 裡，模擬的 IMU 用 `Constants` 的輪子半徑算朝向，所以量出來的一定等於設定值。為了測試這個 Command，把模擬改成「真正的輪子比設定的小一點」，再量一次：

| 模擬中真正的半徑 | 量到的半徑 | 機器人轉了 |
| --- | --- | --- |
| 0.0508 公尺（和設定一樣） | 0.0508 公尺 | 737 度 |
| 0.0495 公尺（磨損了 2.6%） | 0.0496 公尺 | 716 度 |

`Constants` 裡寫的都是 0.0508，Command 仍然量出了真正的值。

## 常見錯誤

!!! bug "常見錯誤：轉太快"
    把 `OMEGA` 調大想快點量完，輪子在地毯上打滑。打滑時輪子轉得比實際走的多，φ 變大，**量出來的半徑會偏小**。慢慢轉，多轉幾圈。

!!! bug "常見錯誤：在磁磚或木板上量"
    輪子陷進地毯的程度和硬地板不一樣，量出來的有效半徑也不一樣。一定要在**比賽用的地毯**上量。

!!! bug "常見錯誤：量完就再也不量"
    胎皮會磨損。換了新的胎皮、或用了幾場比賽之後，再量一次。比賽期間可以在 pit 旁邊的練習場快速量一下，只要 15 秒。

## 小練習

1. 你們的模組位置和這一章不一樣（例如長方形底盤，前後 0.6 公尺、左右 0.5 公尺），`DRIVE_BASE_RADIUS` 是多少？
2. 為什麼量輪子半徑時，用的是 `getWheelRadians()`，而不是 `getDistanceMeters()`（已經換算成公尺的距離）？
3. 量到的半徑是 0.0497 公尺，`WHEEL_DIAMETER_METERS` 應該改成多少？開 5 公尺時，原本的設定會讓 Odometry 差多少？

??? question "第 1 題參考答案"
    `Math.hypot(0.5 / 2, 0.6 / 2)` = hypot(0.25, 0.3) ≈ 0.391 公尺。程式裡用的是 `TRACK_WIDTH_METERS` 和 `WHEEL_BASE_METERS`，所以改 `Constants` 就好。

??? question "第 2 題參考答案"
    `getDistanceMeters()` 是「輪子圈數 × 圓周長」，圓周長用的就是**還沒校正的**輪子半徑。用它來量輪子半徑，等於拿一把刻度錯的尺量自己，算出來的永遠是設定值。要用不經過半徑換算的原始資料（輪子轉的角度）。

??? question "第 3 題參考答案"
    `WHEEL_DIAMETER_METERS = 0.0994`（0.0497 × 2）。原本的 0.1016 比實際大 2.2%，Odometry 以為輪子每圈走得比較遠：實際開 5 公尺，Odometry 會說走了 5 × 0.0508 ÷ 0.0497 ≈ 5.11 公尺，差 11 公分。
