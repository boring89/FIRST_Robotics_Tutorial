# PathPlanner

--8<-- "alpha-warning.md"

## 這頁學什麼

- PathPlanner 的 Holonomic（Swerve）設定要填什麼
- `PPHolonomicDriveController`：修正位置和朝向的誤差
- 完整的 `Drivetrain` 和 `RobotContainer`
- 在模擬裡跑自動程式

## 觀念

### 和第 9 章哪裡不一樣

[第 9 章的 PathPlanner](../09-autonomous/pathplanner.md) 介紹了 `AutoBuilder`、Named commands 和自動程式的選單，這些在 Swerve 上完全一樣。不一樣的只有兩件事：

1. **路徑跟隨控制器**換成 **`PPHolonomicDriveController`**
2. PathPlanner 的 Settings 選 **Holonomic**，填 Swerve 的參數

### `PPHolonomicDriveController`

Swerve 可以同時往任何方向移動和旋轉，所以 PathPlanner 用兩個獨立的 PID 修正誤差：

| PID | 修正什麼 | 這裡的 P |
| --- | --- | --- |
| Translation（平移） | 位置的誤差（公尺） | 5.0：差 1 公尺時，多給 5 m/s |
| Rotation（旋轉） | 朝向的誤差（弧度） | 5.0：差 1 弧度時，多給 5 rad/s |

PathPlanner 每 20 ms 算出「路徑上這時候應該用什麼速度」，再加上 PID 的修正，交給 `driveRobotRelative()`。路徑本身已經包含大部分的速度，PID 只是修正，所以通常只需要 P。

### Swerve 的 Robot Config

在 PathPlanner 應用程式的 **Settings** 選 **Holonomic** 模式，填入這幾章的參數：

| 設定 | 這幾章的值 | 從哪裡來 |
| --- | --- | --- |
| Robot Mass | 機器人含電池、保險桿的重量 | 秤 |
| Robot MOI | 機器人的轉動慣量 | 估計或用 SysId 量 |
| Wheel Radius | 0.0508 m | `WHEEL_DIAMETER_METERS / 2` |
| Drive Gearing | 6.75 | `DRIVE_GEAR_RATIO` |
| Max Drive Speed | 4.5 m/s | [實際量到的最高速度](../14-swerve-tuning/tuning.md#4-確認最高速度) |
| Wheel COF | 1.2 左右 | 輪子和地毯的摩擦係數，看輪子的規格 |
| Drive Motor | Kraken X60 | |
| Drive Current Limit | 60 A | `DRIVE_CURRENT_LIMIT` |
| Module Offsets | 四個模組的位置 | `Constants.java` 的四個 `Translation2d` |

**Module Offsets 的順序**也是左前、右前、左後、右後，和程式一樣。

## 範例

### Constants

加上 PathPlanner 的 PID 參數：

```java
public static final class AutoConstants {
  /** PathPlanner 修正位置誤差的 P：差 1 公尺時，多給 5 公尺 / 秒。 */
  public static final double TRANSLATION_KP = 5.0;
  /** PathPlanner 修正朝向誤差的 P：差 1 弧度時，多給 5 弧度 / 秒。 */
  public static final double ROTATION_KP = 5.0;
}
```

### Drivetrain

這是第 13～16 章最後完整的 `Drivetrain`，包含[上一頁](odometry.md) 的 Pose estimator 和駕駛視角、[第 14 章](../14-swerve-tuning/index.md) 的優化、[第 15 章](../15-swerve-sim/index.md) 的模擬，以及[下一頁](vision.md) 會用到的 `addVisionMeasurement`：

```java title="src/main/java/first/robot/subsystems/Drivetrain.java"
package first.robot.subsystems;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.config.PIDConstants;
import com.pathplanner.lib.config.RobotConfig;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;
import first.robot.Constants.AutoConstants;
import first.robot.Constants.SwerveConstants;
import java.util.function.DoubleSupplier;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchState;
import org.wpilib.hardware.imu.OnboardIMU;
import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;
import org.wpilib.math.estimator.SwerveDrivePoseEstimator;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.SwerveDriveKinematics;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
import org.wpilib.math.linalg.Matrix;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N3;
import org.wpilib.simulation.OnboardIMUSim;
import org.wpilib.telemetry.Telemetry;

public class Drivetrain extends SubsystemBase {
  // 順序固定是左前、右前、左後、右後，Kinematics 也用同樣的順序
  private final SwerveModule[] modules = {
    new SwerveModule(SwerveConstants.FRONT_LEFT),
    new SwerveModule(SwerveConstants.FRONT_RIGHT),
    new SwerveModule(SwerveConstants.BACK_LEFT),
    new SwerveModule(SwerveConstants.BACK_RIGHT),
  };

  private final SwerveDriveKinematics kinematics =
      new SwerveDriveKinematics(
          SwerveConstants.FRONT_LEFT.location(),
          SwerveConstants.FRONT_RIGHT.location(),
          SwerveConstants.BACK_LEFT.location(),
          SwerveConstants.BACK_RIGHT.location());

  private final OnboardIMU imu = new OnboardIMU(MountOrientation.FLAT);
  private double simYawRadians = 0.0;

  private final SwerveDrivePoseEstimator poseEstimator;

  public Drivetrain() {
    poseEstimator =
        new SwerveDrivePoseEstimator(kinematics, getHeading(), getModulePositions(), Pose2d.ZERO);
    configurePathPlanner();
  }

  private void configurePathPlanner() {
    RobotConfig config;
    try {
      config = RobotConfig.fromGUISettings();
    } catch (Exception e) {
      System.err.println("無法讀取 PathPlanner 的機器人設定：" + e.getMessage());
      return;
    }
    AutoBuilder.configure(
        this::getPose,
        this::resetPose,
        this::getRobotRelativeVelocities,
        (velocities, feedforwards) -> driveRobotRelative(velocities),
        new PPHolonomicDriveController(
            new PIDConstants(AutoConstants.TRANSLATION_KP, 0.0, 0.0),
            new PIDConstants(AutoConstants.ROTATION_KP, 0.0, 0.0)),
        config,
        this::isRedAlliance,
        this);
  }

  // ---------- 感測器 ----------

  /** IMU 讀到的朝向，逆時針為正。只給 Odometry 用，其他地方用 getPose().getRotation()。 */
  public Rotation2d getHeading() {
    return imu.getRotation2d();
  }

  /** 四個模組目前的速度和角度。 */
  public SwerveModuleVelocity[] getModuleVelocities() {
    SwerveModuleVelocity[] velocities = new SwerveModuleVelocity[modules.length];
    for (int i = 0; i < modules.length; i++) {
      velocities[i] = modules[i].getVelocity();
    }
    return velocities;
  }

  /** 四個模組走過的距離和角度。 */
  public SwerveModulePosition[] getModulePositions() {
    SwerveModulePosition[] positions = new SwerveModulePosition[modules.length];
    for (int i = 0; i < modules.length; i++) {
      positions[i] = modules[i].getPosition();
    }
    return positions;
  }

  /** 機器人在場地上的位置和朝向。 */
  public Pose2d getPose() {
    return poseEstimator.getEstimatedPosition();
  }

  public void resetPose(Pose2d pose) {
    poseEstimator.resetPose(pose);
  }

  /** 加入一筆視覺算出的位置。 */
  public void addVisionMeasurement(Pose2d pose, double timestampSeconds, Matrix<N3, N1> stdDevs) {
    poseEstimator.addVisionMeasurement(pose, timestampSeconds, stdDevs);
  }

  /** 機器人座標的速度（PathPlanner 使用）。 */
  public ChassisVelocities getRobotRelativeVelocities() {
    return kinematics.toChassisVelocities(getModuleVelocities());
  }

  public boolean isRedAlliance() {
    return MatchState.getAlliance().orElse(Alliance.BLUE) == Alliance.RED;
  }

  // ---------- 控制 ----------

  /** 用機器人座標的速度移動：vx 往前、vy 往左、omega 逆時針。 */
  public void driveRobotRelative(ChassisVelocities velocities) {
    // 修正「一邊走一邊轉」時往旁邊偏的問題
    velocities = velocities.discretize(0.02);
    SwerveModuleVelocity[] targets = kinematics.toSwerveModuleVelocities(velocities);
    targets = SwerveDriveKinematics.desaturateWheelVelocities(targets, SwerveConstants.MAX_SPEED);
    for (int i = 0; i < modules.length; i++) {
      modules[i].setTarget(targets[i]);
    }
    Telemetry.log("Drivetrain/TargetVelocities", targets);
  }

  /** 用場地座標的速度移動：vx 是往紅色聯盟的方向（+X）。 */
  public void driveFieldRelative(ChassisVelocities velocities) {
    driveRobotRelative(velocities.toRobotRelative(getPose().getRotation()));
  }

  /** 用駕駛的視角移動：vx 是遠離駕駛的方向。紅色聯盟的駕駛站在場地的另一端，所以方向相反。 */
  public void driveDriverRelative(ChassisVelocities velocities) {
    if (isRedAlliance()) {
      velocities = new ChassisVelocities(-velocities.vx, -velocities.vy, velocities.omega);
    }
    driveFieldRelative(velocities);
  }

  public void stop() {
    for (SwerveModule module : modules) {
      module.stop();
    }
  }

  /** 把四個輪子轉成 X 字形，讓機器人很難被推動。 */
  public void lock() {
    modules[0].setTarget(new SwerveModuleVelocity(0.0, Rotation2d.fromDegrees(45)));   // 左前
    modules[1].setTarget(new SwerveModuleVelocity(0.0, Rotation2d.fromDegrees(-45)));  // 右前
    modules[2].setTarget(new SwerveModuleVelocity(0.0, Rotation2d.fromDegrees(-45)));  // 左後
    modules[3].setTarget(new SwerveModuleVelocity(0.0, Rotation2d.fromDegrees(45)));   // 右後
  }

  // ---------- Command ----------

  /**
   * 用搖桿開車（駕駛視角）。三個輸入的範圍都是 -1～1。
   *
   * @param forward 遠離駕駛
   * @param left 往駕駛的左邊
   * @param rotation 逆時針旋轉
   */
  public Command driveCommand(DoubleSupplier forward, DoubleSupplier left, DoubleSupplier rotation) {
    return run(() -> driveDriverRelative(
            new ChassisVelocities(
                forward.getAsDouble() * SwerveConstants.MAX_SPEED,
                left.getAsDouble() * SwerveConstants.MAX_SPEED,
                rotation.getAsDouble() * SwerveConstants.MAX_ANGULAR_SPEED)))
        .finallyDo(this::stop)
        .withName("SwerveDrive");
  }

  /** 按住時把輪子轉成 X 字形。 */
  public Command lockCommand() {
    return run(this::lock).withName("SwerveLock");
  }

  /** 駕駛讓機器人面向遠離自己的方向，再按這個按鈕：重設朝向，位置不變。 */
  public Command resetHeadingCommand() {
    return runOnce(() -> {
          Rotation2d awayFromDriver = isRedAlliance() ? Rotation2d.PI : Rotation2d.ZERO;
          resetPose(new Pose2d(getPose().getTranslation(), awayFromDriver));
        })
        .ignoringDisable(true)
        .withName("ResetHeading");
  }

  @Override
  public void periodic() {
    for (SwerveModule module : modules) {
      module.log();
    }
    poseEstimator.update(getHeading(), getModulePositions());
    Telemetry.log("Drivetrain/Pose", getPose());
    Telemetry.log("Drivetrain/ModuleVelocities", getModuleVelocities());
    Telemetry.log("Drivetrain/HeadingDegrees", getPose().getRotation().getDegrees());
  }

  @Override
  public void simulationPeriodic() {
    for (SwerveModule module : modules) {
      module.simulationPeriodic();
    }
    // 模擬的 IMU：用四個模組實際的速度，算出機器人轉得多快，再累加成朝向
    ChassisVelocities actual = kinematics.toChassisVelocities(getModuleVelocities());
    simYawRadians += actual.omega * 0.02;
    OnboardIMUSim.setYaw(simYawRadians);
  }
}
```

- **`configurePathPlanner()`** 和第 9 章的寫法一樣，只是控制器換成 `PPHolonomicDriveController`
- **`getRobotRelativeVelocities()`** 用四個模組**量到的**速度，透過 Kinematics 算出機器人實際的速度
- **`isRedAlliance()`** 同時用在 PathPlanner 的路徑翻轉和駕駛視角
- PathPlanner 呼叫的是 **`driveRobotRelative()`**，不經過駕駛視角的翻轉：PathPlanner 自己會處理紅藍聯盟

### RobotContainer

```java title="src/main/java/first/robot/RobotContainer.java"
package first.robot;

import com.pathplanner.lib.auto.AutoBuilder;
import first.robot.Constants.OperatorConstants;
import first.robot.commands.AimAtTarget;
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

  private void configureDefaultCommands() {
    drivetrain.setDefaultCommand(
        drivetrain.driveCommand(
            () -> shape(-driver.getLeftY()),
            () -> shape(-driver.getLeftX()),
            () -> shape(-driver.getRightX())));
  }

  private void configureBindings() {
    driver.start().onTrue(drivetrain.resetHeadingCommand());
    driver.faceLeft().whileTrue(drivetrain.lockCommand());
    // 按住左肩鍵時，速度降到 30%
    driver.leftBumper().whileTrue(
        drivetrain.driveCommand(
            () -> shape(-driver.getLeftY()) * 0.3,
            () -> shape(-driver.getLeftX()) * 0.3,
            () -> shape(-driver.getRightX()) * 0.3));
    // 按住右扳機時，一邊移動一邊對準目標
    driver.rightTrigger().whileTrue(
        new AimAtTarget(
            drivetrain, vision, () -> shape(-driver.getLeftY()), () -> shape(-driver.getLeftX())));
  }

  public Command getAutonomousCommand() {
    return autoChooser.getSelected();
  }
}
```

`Vision` 和 `AimAtTarget` 在[下一頁](vision.md)。沒有設定 PathPlanner 時（例如還沒打開過 PathPlanner 應用程式），`AutoBuilder.isConfigured()` 是 `false`，選單裡只有「什麼都不做」。

## 步驟

### 在模擬裡跑自動程式

PathPlanner 在模擬裡也能用，不用機器人就可以測試路徑：

1. 用 PathPlanner 打開專案，填好 Settings，畫一條路徑，做成一個自動程式
2. 啟動模擬，AdvantageScope 連線到模擬，打開 2D Field 分頁顯示 `Telemetry/Drivetrain/Pose`
3. 用 **Elastic** 或 AdvantageScope 在 `Auto Chooser` 選擇自動程式（[第 9 章](../09-autonomous/pathplanner.md)）
4. 在 Sim GUI 的 Robot State 點 **Autonomous**

模擬的機器人會沿著路徑走。PathPlanner 應用程式的 **Telemetry** 頁面也可以連線到模擬，同時顯示目標路徑和機器人實際的位置。

!!! warning "注意：模擬跑得好，不代表真的機器人也一樣"
    [模擬的限制](../15-swerve-sim/module-sim.md#模擬的限制) 在這裡也成立：沒有摩擦力、輪子不會打滑、四個模組各自獨立。模擬適合檢查**路徑和程式的邏輯**（起點對不對、紅色聯盟有沒有翻轉、Named commands 有沒有觸發），PID 參數還是要在真的機器人上調。

## 常見錯誤

!!! bug "常見錯誤：PathPlanner 的 Module Offsets 和程式不一樣"
    PathPlanner 用 Module Offsets 計算每個輪子的速度限制。和 `Constants.java` 不一樣時，路徑規劃出來的速度可能超過輪子能跑的速度，機器人跟不上路徑。

!!! bug "常見錯誤：PathPlanner 的速度經過駕駛視角翻轉"
    把 PathPlanner 的輸出接到 `driveDriverRelative` 或 `driveFieldRelative`：PathPlanner 給的是**機器人座標**的速度，再轉一次座標，機器人就會往錯的方向走。一定要接 `driveRobotRelative`。

!!! bug "常見錯誤：Rotation 的 P 太大"
    機器人跟著路徑走的時候，朝向一直左右擺動。先把 `ROTATION_KP` 降低，確認轉向（[第 14 章](../14-swerve-tuning/tuning.md)）已經調好。

## 小練習

1. 在 PathPlanner 畫一條路徑：機器人往前走 3 公尺，同時轉 180 度。在模擬裡跑，觀察 Swerve 分頁上四個輪子的變化。
2. 把聯盟改成紅色再跑一次，機器人在場地上的路徑有什麼不同？
3. 把 `TRANSLATION_KP` 改成 0，在模擬裡跑同一條路徑，終點差多少？

??? question "第 2 題參考答案"
    `AutoBuilder.configure` 的最後一個條件 `isRedAlliance()` 為 `true` 時，PathPlanner 會把路徑**翻轉**到紅色聯盟那一側（預設是繞場地中心旋轉 180 度，也可以用 `FlippingUtil.symmetryType` 改成鏡像，看當年的場地怎麼對稱）。機器人會從紅色聯盟的起始區出發，往藍色聯盟的方向走。路徑的起點也會被翻轉，所以自動程式開始時 `resetPose` 會把位置設在紅色聯盟那一側。

??? question "第 3 題參考答案"
    沒有平移的 PID 時，機器人只靠路徑上的速度走，任何誤差（加速太慢、旋轉時的偏移）都不會被修正，會一直累積，終點會偏離路徑的終點。偏多少取決於路徑的長度和彎度；在真的機器人上，因為有打滑和摩擦，誤差通常比模擬更大。
