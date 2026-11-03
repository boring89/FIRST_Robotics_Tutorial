# PathPlanner

--8<-- "alpha-warning.md"

## 這頁學什麼

- PathPlanner 是什麼、由哪些部分組成
- 讓 Drivetrain 能被 PathPlanner 控制
- 在 PathPlanner 裡畫路徑、組合自動程式
- 用 Named commands 在路徑中執行機構的動作
- 把 PathPlanner 的自動程式加進選單

## 觀念

### PathPlanner

**PathPlanner** 是 FRC 最常用的路徑規劃工具，由兩個部分組成：

| 部分 | 用途 |
| --- | --- |
| **PathPlanner 應用程式** | 在電腦上的場地圖畫路徑、組合自動程式 |
| **PathPlannerLib**（vendordep） | 機器人程式用的函式庫，讀取畫好的路徑並控制底盤跟著走 |

畫好的路徑存成檔案，放在專案的 `src/main/deploy/pathplanner/` 資料夾。部署程式時，這些檔案會一起送到 Systemcore 上。

### PathPlanner 需要知道什麼

PathPlannerLib 會一直比較「路徑上現在應該在哪裡」和「機器人實際在哪裡」，算出底盤應該怎麼動。所以 Drivetrain 要提供四件事：

| 需要 | Drivetrain 的方法 |
| --- | --- |
| 機器人現在在哪裡 | `getPose()`（[上一頁](odometry.md)的 Odometry） |
| 把位置重設成路徑的起點 | `resetPose(Pose2d)` |
| 機器人現在動得多快 | `getRobotRelativeVelocities()` |
| 讓機器人用指定的速度移動 | `driveRobotRelative(ChassisVelocities)` |

速度都用[第 7 章](../07-drivetrain/swerve.md#chassisvelocities機器人要怎麼動)介紹的 `ChassisVelocities` 表示。差速底盤只有 `vx`（前進）和 `omega`（旋轉），`vy` 永遠是 0。

### 用指定的速度移動

`driveRobotRelative` 收到的是「每秒幾公尺」的速度，要換算成馬達的電壓：

1. 用 `DifferentialDriveKinematics` 把機器人的速度換算成**左右輪**的速度
2. 用[第 8 章](../08-control/feedforward.md)的 **Feedforward**（`SimpleMotorFeedforward`）把輪速換算成電壓

PathPlannerLib 會一直修正誤差，所以這裡只用 Feedforward 就夠了。kS、kV 最好用 [SysId](../08-control/sysid.md) 量測。

### `AutoBuilder`

在 Drivetrain 的建構子裡呼叫 **`AutoBuilder.configure(...)`**，把上面四個方法告訴 PathPlannerLib，再加上：

- **路徑跟隨控制器**：差速底盤用 `PPLTVController`，Swerve 用 `PPHolonomicDriveController`
- **機器人的設定**（重量、輪距、馬達等）：在 PathPlanner 應用程式裡設定，用 `RobotConfig.fromGUISettings()` 讀取
- **要不要翻轉路徑**：紅色聯盟時回傳 `true`，PathPlanner 會把藍色聯盟畫的路徑鏡像到紅色聯盟

### Named commands

路徑走到一半要讓機構做事（例如發射、吸入），在 PathPlanner 裡用**名字**指定 Command，程式裡再用 `NamedCommands.registerCommand("名字", command)` 告訴 PathPlannerLib 這個名字對應哪個 Command。

**Named commands 一定要在建立自動程式之前註冊**，否則 PathPlanner 找不到它們。

## 步驟

### 1. 安裝

1. 從 [PathPlanner 的 GitHub](https://github.com/mjansen4857/pathplanner/releases) 下載 PathPlanner 應用程式，**版本要和 PathPlannerLib 對應**（2027 alpha 用 2027 的版本）
2. 在專案裡加入 **PathplannerLib** vendordep（WPILib Vendor Dependencies 面板的清單裡就有）

### 2. 設定機器人

1. 用 PathPlanner 打開你的機器人專案資料夾
2. 到 **Settings** 填入機器人的參數：重量、轉動慣量、輪距 (Track width)、輪子直徑、齒輪比、馬達型號、電流限制等
3. Drive mode 選 **Differential**（差速底盤）或 **Holonomic**（Swerve）

這些設定存在 `src/main/deploy/pathplanner/settings.json`，`RobotConfig.fromGUISettings()` 就是讀這個檔案。

### 3. 畫路徑和自動程式

1. 在 **Paths** 新增路徑，在場地圖上拖曳起點、終點和中間的控制點
2. 在 **Autos** 新增自動程式，把路徑和 Named commands 依序排起來，例如：`Shoot` → 路徑「後退到第二個物件」→ `Intake`
3. 路徑中間可以加 **Event marker**，走到那裡時觸發 Named command（例如一邊走一邊吸）

## 範例

在 `Constants.java` 的 `DrivetrainConstants` 加上：

```java
public static final double TRACK_WIDTH_METERS = 0.6;   // 左右輪的距離
public static final double KS = 0.2;                   // 伏特
public static final double KV = 2.5;                   // 伏特 / (公尺 / 秒)，用 SysId 量測更準
```

完整的 Drivetrain，包含第 7 章的開車、第 8 章的 `arcadeDrive`、這一章的走距離、Odometry 和 PathPlanner：

=== "REV"

    ```java title="src/main/java/first/robot/subsystems/Drivetrain.java"
    package first.robot.subsystems;

    import com.pathplanner.lib.auto.AutoBuilder;
    import com.pathplanner.lib.config.RobotConfig;
    import com.pathplanner.lib.controllers.PPLTVController;
    import com.revrobotics.PersistMode;
    import com.revrobotics.RelativeEncoder;
    import com.revrobotics.ResetMode;
    import com.revrobotics.spark.SparkLowLevel.MotorType;
    import com.revrobotics.spark.SparkMax;
    import com.revrobotics.spark.config.SparkBaseConfig;
    import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
    import com.revrobotics.spark.config.SparkMaxConfig;
    import first.robot.Constants.DrivetrainConstants;
    import java.util.function.DoubleSupplier;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.drive.DifferentialDrive;
    import org.wpilib.driverstation.Alliance;
    import org.wpilib.driverstation.MatchState;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.hardware.imu.OnboardIMU;
    import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;
    import org.wpilib.math.controller.SimpleMotorFeedforward;
    import org.wpilib.math.geometry.Pose2d;
    import org.wpilib.math.geometry.Rotation2d;
    import org.wpilib.math.kinematics.ChassisVelocities;
    import org.wpilib.math.kinematics.DifferentialDriveKinematics;
    import org.wpilib.math.kinematics.DifferentialDriveOdometry;
    import org.wpilib.math.kinematics.DifferentialDriveWheelVelocities;
    import org.wpilib.telemetry.Telemetry;

    public class Drivetrain extends SubsystemBase {
      private final SparkMax leftLeader =
          new SparkMax(CANPort.CAN_S0, DrivetrainConstants.LEFT_LEADER_ID, MotorType.kBrushless);
      private final SparkMax leftFollower =
          new SparkMax(CANPort.CAN_S0, DrivetrainConstants.LEFT_FOLLOWER_ID, MotorType.kBrushless);
      private final SparkMax rightLeader =
          new SparkMax(CANPort.CAN_S0, DrivetrainConstants.RIGHT_LEADER_ID, MotorType.kBrushless);
      private final SparkMax rightFollower =
          new SparkMax(CANPort.CAN_S0, DrivetrainConstants.RIGHT_FOLLOWER_ID, MotorType.kBrushless);

      private final RelativeEncoder leftEncoder = leftLeader.getEncoder();
      private final RelativeEncoder rightEncoder = rightLeader.getEncoder();
      private final OnboardIMU imu = new OnboardIMU(MountOrientation.FLAT);

      private final DifferentialDrive drive =
          new DifferentialDrive(leftLeader::setThrottle, rightLeader::setThrottle);
      private final DifferentialDriveKinematics kinematics =
          new DifferentialDriveKinematics(DrivetrainConstants.TRACK_WIDTH_METERS);
      private final SimpleMotorFeedforward feedforward =
          new SimpleMotorFeedforward(DrivetrainConstants.KS, DrivetrainConstants.KV);
      private final DifferentialDriveOdometry odometry;

      private double startDistance = 0.0;

      public Drivetrain() {
        configure(leftLeader, baseConfig());
        configure(rightLeader, baseConfig().inverted(true));
        configure(leftFollower, baseConfig().follow(leftLeader));
        configure(rightFollower, baseConfig().follow(rightLeader));

        odometry = new DifferentialDriveOdometry(getHeading(), getLeftDistanceMeters(), getRightDistanceMeters());
        configurePathPlanner();
      }

      private static SparkMaxConfig baseConfig() {
        SparkMaxConfig config = new SparkMaxConfig();
        config.idleMode(IdleMode.kBrake).smartCurrentLimit(DrivetrainConstants.CURRENT_LIMIT);
        return config;
      }

      private static void configure(SparkMax motor, SparkBaseConfig config) {
        motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
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
            new PPLTVController(0.02),
            config,
            () -> MatchState.getAlliance().orElse(Alliance.BLUE) == Alliance.RED,
            this);
      }

      // ---------- 單位換算 ----------

      private static double rotationsToMeters(double motorRotations) {
        return motorRotations / DrivetrainConstants.GEAR_RATIO
            * Math.PI * DrivetrainConstants.WHEEL_DIAMETER_METERS;
      }

      private static double rpmToMetersPerSecond(double motorRpm) {
        return rotationsToMeters(motorRpm / 60.0);
      }

      // ---------- 感測器 ----------

      public double getLeftDistanceMeters() {
        return rotationsToMeters(leftEncoder.getPosition().get(0.0));
      }

      public double getRightDistanceMeters() {
        return rotationsToMeters(rightEncoder.getPosition().get(0.0));
      }

      public double getDistanceMeters() {
        return (getLeftDistanceMeters() + getRightDistanceMeters()) / 2;
      }

      /** 機器人的朝向，逆時針為正。 */
      public Rotation2d getHeading() {
        return imu.getRotation2d();
      }

      public Pose2d getPose() {
        return odometry.getPose();
      }

      public void resetPose(Pose2d pose) {
        odometry.resetPose(pose);
      }

      public ChassisVelocities getRobotRelativeVelocities() {
        return kinematics.toChassisVelocities(
            new DifferentialDriveWheelVelocities(
                rpmToMetersPerSecond(leftEncoder.getVelocity().get(0.0)),
                rpmToMetersPerSecond(rightEncoder.getVelocity().get(0.0))));
      }

      // ---------- 控制 ----------

      public void arcadeDrive(double forward, double rotation) {
        drive.arcadeDrive(forward, rotation, false);
      }

      /** 用指定的速度移動（PathPlanner 使用）。 */
      public void driveRobotRelative(ChassisVelocities velocities) {
        DifferentialDriveWheelVelocities wheels = kinematics.toWheelVelocities(velocities);
        leftLeader.setVoltage(feedforward.calculate(wheels.left));
        rightLeader.setVoltage(feedforward.calculate(wheels.right));
        drive.feed();   // 告訴 Motor Safety 底盤有在被控制
      }

      // ---------- Command ----------

      /** 用 Arcade drive 開車：forward 正值往前，rotation 正值往左轉。 */
      public Command arcadeDriveCommand(DoubleSupplier forward, DoubleSupplier rotation) {
        return run(() -> drive.arcadeDrive(forward.getAsDouble(), rotation.getAsDouble()))
            .withName("ArcadeDrive");
      }

      /** 往前（正）或往後（負）走指定的距離，單位：公尺。 */
      public Command driveDistanceCommand(double meters) {
        double direction = Math.signum(meters);
        return runOnce(() -> startDistance = getDistanceMeters())
            .andThen(run(() -> arcadeDrive(0.4 * direction, 0.0)))
            .until(() -> Math.abs(getDistanceMeters() - startDistance) >= Math.abs(meters))
            .finallyDo(() -> arcadeDrive(0.0, 0.0))
            .withName("DriveDistance");
      }

      public Command resetHeadingCommand() {
        return runOnce(imu::resetYaw).ignoringDisable(true).withName("ResetHeading");
      }

      @Override
      public void periodic() {
        odometry.update(getHeading(), getLeftDistanceMeters(), getRightDistanceMeters());
        Telemetry.log("Drivetrain/Pose", getPose());
        Telemetry.log("Drivetrain/HeadingDegrees", getHeading().getDegrees());
      }
    }
    ```

=== "CTRE"

    ```java title="src/main/java/first/robot/subsystems/Drivetrain.java"
    package first.robot.subsystems;

    import com.ctre.phoenix6.CANBus;
    import com.ctre.phoenix6.configs.TalonFXConfiguration;
    import com.ctre.phoenix6.controls.Follower;
    import com.ctre.phoenix6.hardware.TalonFX;
    import com.ctre.phoenix6.signals.InvertedValue;
    import com.ctre.phoenix6.signals.MotorAlignmentValue;
    import com.ctre.phoenix6.signals.NeutralModeValue;
    import com.pathplanner.lib.auto.AutoBuilder;
    import com.pathplanner.lib.config.RobotConfig;
    import com.pathplanner.lib.controllers.PPLTVController;
    import first.robot.Constants.DrivetrainConstants;
    import java.util.function.DoubleSupplier;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.drive.DifferentialDrive;
    import org.wpilib.driverstation.Alliance;
    import org.wpilib.driverstation.MatchState;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.hardware.imu.OnboardIMU;
    import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;
    import org.wpilib.math.controller.SimpleMotorFeedforward;
    import org.wpilib.math.geometry.Pose2d;
    import org.wpilib.math.geometry.Rotation2d;
    import org.wpilib.math.kinematics.ChassisVelocities;
    import org.wpilib.math.kinematics.DifferentialDriveKinematics;
    import org.wpilib.math.kinematics.DifferentialDriveOdometry;
    import org.wpilib.math.kinematics.DifferentialDriveWheelVelocities;
    import org.wpilib.telemetry.Telemetry;

    public class Drivetrain extends SubsystemBase {
      private final CANBus bus = new CANBus(CANPort.CAN_S0);
      private final TalonFX leftLeader = new TalonFX(DrivetrainConstants.LEFT_LEADER_ID, bus);
      private final TalonFX leftFollower = new TalonFX(DrivetrainConstants.LEFT_FOLLOWER_ID, bus);
      private final TalonFX rightLeader = new TalonFX(DrivetrainConstants.RIGHT_LEADER_ID, bus);
      private final TalonFX rightFollower = new TalonFX(DrivetrainConstants.RIGHT_FOLLOWER_ID, bus);

      private final OnboardIMU imu = new OnboardIMU(MountOrientation.FLAT);

      private final DifferentialDrive drive =
          new DifferentialDrive(leftLeader::setThrottle, rightLeader::setThrottle);
      private final DifferentialDriveKinematics kinematics =
          new DifferentialDriveKinematics(DrivetrainConstants.TRACK_WIDTH_METERS);
      private final SimpleMotorFeedforward feedforward =
          new SimpleMotorFeedforward(DrivetrainConstants.KS, DrivetrainConstants.KV);
      private final DifferentialDriveOdometry odometry;

      private double startDistance = 0.0;

      public Drivetrain() {
        TalonFXConfiguration config = new TalonFXConfiguration();
        config.MotorOutput.NeutralMode = NeutralModeValue.Brake;
        config.CurrentLimits.StatorCurrentLimit = DrivetrainConstants.CURRENT_LIMIT;
        config.CurrentLimits.StatorCurrentLimitEnable = true;

        config.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;
        leftLeader.getConfigurator().apply(config);
        leftFollower.getConfigurator().apply(config);

        config.MotorOutput.Inverted = InvertedValue.Clockwise_Positive;
        rightLeader.getConfigurator().apply(config);
        rightFollower.getConfigurator().apply(config);

        leftFollower.setControl(new Follower(leftLeader.getDeviceID(), MotorAlignmentValue.Aligned));
        rightFollower.setControl(new Follower(rightLeader.getDeviceID(), MotorAlignmentValue.Aligned));

        odometry = new DifferentialDriveOdometry(getHeading(), getLeftDistanceMeters(), getRightDistanceMeters());
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
            new PPLTVController(0.02),
            config,
            () -> MatchState.getAlliance().orElse(Alliance.BLUE) == Alliance.RED,
            this);
      }

      // ---------- 單位換算 ----------

      private static double rotationsToMeters(double motorRotations) {
        return motorRotations / DrivetrainConstants.GEAR_RATIO
            * Math.PI * DrivetrainConstants.WHEEL_DIAMETER_METERS;
      }

      // ---------- 感測器 ----------

      public double getLeftDistanceMeters() {
        return rotationsToMeters(leftLeader.getPosition().getValueAsDouble());
      }

      public double getRightDistanceMeters() {
        return rotationsToMeters(rightLeader.getPosition().getValueAsDouble());
      }

      public double getDistanceMeters() {
        return (getLeftDistanceMeters() + getRightDistanceMeters()) / 2;
      }

      /** 機器人的朝向，逆時針為正。 */
      public Rotation2d getHeading() {
        return imu.getRotation2d();
      }

      public Pose2d getPose() {
        return odometry.getPose();
      }

      public void resetPose(Pose2d pose) {
        odometry.resetPose(pose);
      }

      public ChassisVelocities getRobotRelativeVelocities() {
        // Phoenix 6 的速度是每秒轉數，換算成公尺 / 秒
        return kinematics.toChassisVelocities(
            new DifferentialDriveWheelVelocities(
                rotationsToMeters(leftLeader.getVelocity().getValueAsDouble()),
                rotationsToMeters(rightLeader.getVelocity().getValueAsDouble())));
      }

      // ---------- 控制 ----------

      public void arcadeDrive(double forward, double rotation) {
        drive.arcadeDrive(forward, rotation, false);
      }

      /** 用指定的速度移動（PathPlanner 使用）。 */
      public void driveRobotRelative(ChassisVelocities velocities) {
        DifferentialDriveWheelVelocities wheels = kinematics.toWheelVelocities(velocities);
        leftLeader.setVoltage(feedforward.calculate(wheels.left));
        rightLeader.setVoltage(feedforward.calculate(wheels.right));
        drive.feed();   // 告訴 Motor Safety 底盤有在被控制
      }

      // ---------- Command ----------

      /** 用 Arcade drive 開車：forward 正值往前，rotation 正值往左轉。 */
      public Command arcadeDriveCommand(DoubleSupplier forward, DoubleSupplier rotation) {
        return run(() -> drive.arcadeDrive(forward.getAsDouble(), rotation.getAsDouble()))
            .withName("ArcadeDrive");
      }

      /** 往前（正）或往後（負）走指定的距離，單位：公尺。 */
      public Command driveDistanceCommand(double meters) {
        double direction = Math.signum(meters);
        return runOnce(() -> startDistance = getDistanceMeters())
            .andThen(run(() -> arcadeDrive(0.4 * direction, 0.0)))
            .until(() -> Math.abs(getDistanceMeters() - startDistance) >= Math.abs(meters))
            .finallyDo(() -> arcadeDrive(0.0, 0.0))
            .withName("DriveDistance");
      }

      public Command resetHeadingCommand() {
        return runOnce(imu::resetYaw).ignoringDisable(true).withName("ResetHeading");
      }

      @Override
      public void periodic() {
        odometry.update(getHeading(), getLeftDistanceMeters(), getRightDistanceMeters());
        Telemetry.log("Drivetrain/Pose", getPose());
        Telemetry.log("Drivetrain/HeadingDegrees", getHeading().getDegrees());
      }
    }
    ```

`RobotContainer` 註冊 Named commands，並把 PathPlanner 的自動程式和前兩頁寫的自動程式放進同一個選單：

```java title="src/main/java/first/robot/RobotContainer.java"
package first.robot;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.auto.NamedCommands;
import first.robot.Constants.ArmConstants;
import first.robot.Constants.OperatorConstants;
import first.robot.commands.Autos;
import first.robot.commands.ScoringCommands;
import first.robot.commands.TurnToAngle;
import first.robot.subsystems.Arm;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.button.CommandGamepad;
import org.wpilib.command2.button.Trigger;
import org.wpilib.math.filter.SlewRateLimiter;
import org.wpilib.tunable.Selectable;
import org.wpilib.tunable.Tunables;

public class RobotContainer {
  private final Drivetrain drivetrain = new Drivetrain();
  private final Intake intake = new Intake();
  private final Shooter shooter = new Shooter();
  private final Arm arm = new Arm();

  private final CommandGamepad driver = new CommandGamepad(OperatorConstants.DRIVER_CONTROLLER_PORT);
  private final SlewRateLimiter forwardLimiter = new SlewRateLimiter(3.0);

  private final Selectable<Command> autoChooser;

  public RobotContainer() {
    // Named commands 一定要在建立自動程式之前註冊
    registerNamedCommands();
    autoChooser = buildAutoChooser();
    Tunables.publish("Auto Chooser", autoChooser);

    configureDefaultCommands();
    configureBindings();
  }

  private void registerNamedCommands() {
    NamedCommands.registerCommand("Shoot", ScoringCommands.shoot(intake, shooter));
    NamedCommands.registerCommand("Intake", intake.intakeCommand().withTimeout(3.0));
  }

  private Selectable<Command> buildAutoChooser() {
    Selectable<Command> chooser;
    if (AutoBuilder.isConfigured()) {
      chooser = AutoBuilder.buildAutoChooser();   // 包含所有 PathPlanner 的自動程式
    } else {
      chooser = new Selectable<>();
      chooser.addDefault("什麼都不做", Commands.none());
    }
    chooser.add("往前開 2 秒（時間）", Autos.driveForwardTimed(drivetrain));
    chooser.add("發射後後退（編碼器）", Autos.shootThenBackUp(drivetrain, intake, shooter));
    chooser.add("發射並撿下一個（編碼器）", Autos.shootAndPickUp(drivetrain, intake, shooter));
    return chooser;
  }

  private void configureDefaultCommands() {
    drivetrain.setDefaultCommand(
        drivetrain.arcadeDriveCommand(
            () -> forwardLimiter.calculate(-driver.getLeftY()), () -> -driver.getRightX()));
    intake.setDefaultCommand(intake.stopCommand());
    arm.setDefaultCommand(arm.manualCommand(() -> -driver.getRightY() * 0.3));
  }

  private void configureBindings() {
    driver.faceDown().whileTrue(intake.intakeCommand());
    driver.faceRight().whileTrue(intake.ejectCommand());
    driver.rightBumper()
        .and(intake::hasGamePiece)
        .onTrue(ScoringCommands.shoot(intake, shooter));
    driver.start().onTrue(drivetrain.resetHeadingCommand());
    driver.leftBumper().whileTrue(
        drivetrain.arcadeDriveCommand(
            () -> -driver.getLeftY() * 0.3, () -> -driver.getRightX() * 0.3));

    driver.dpadUp().onTrue(new TurnToAngle(drivetrain, 0));
    driver.dpadLeft().onTrue(new TurnToAngle(drivetrain, 90));
    driver.dpadDown().onTrue(new TurnToAngle(drivetrain, 180));
    driver.dpadRight().onTrue(new TurnToAngle(drivetrain, -90));

    driver.faceUp().onTrue(arm.goToAngleCommand(ArmConstants.SCORE_ANGLE_DEGREES));
    driver.faceLeft().onTrue(arm.goToAngleCommand(ArmConstants.STOWED_ANGLE_DEGREES));
    new Trigger(() -> Math.abs(driver.getRightY()) > 0.2)
        .onTrue(arm.manualCommand(() -> -driver.getRightY() * 0.3));

    new Trigger(intake::hasGamePiece)
        .debounce(0.1)
        .onTrue(Commands.print("拿到遊戲物件！"));
  }

  public Command getAutonomousCommand() {
    return autoChooser.getSelected();
  }
}
```

`AutoBuilder.buildAutoChooser()` 回傳的就是 2027 的 `Selectable`，裡面已經放好了 PathPlanner 裡所有的自動程式，再用 `add()` 加上自己寫的就好。如果 PathPlanner 的設定檔讀不到（`AutoBuilder.isConfigured()` 是 `false`），就只放自己寫的自動程式，程式不會因此當掉。

## 常見錯誤

!!! bug "常見錯誤：Named command 沒有作用"
    在 `AutoBuilder.buildAutoChooser()` **之後**才註冊 Named commands，PathPlanner 建立自動程式時找不到它們，路徑會照走，但機構不會動。註冊一定要在前面。

!!! bug "常見錯誤：紅色聯盟時機器人往奇怪的方向走"
    `AutoBuilder.configure` 的翻轉條件寫錯，或是 Driver Station 沒有設定聯盟顏色。練習時記得在 Driver Station 選擇聯盟顏色，紅藍兩邊都要測試。

!!! bug "常見錯誤：機器人跟著路徑走，但越走越偏"
    通常是 Odometry 不準（見[上一頁](odometry.md#常見錯誤)），或是 kS、kV 差太多。先確認 AdvantageScope 上的位置和實際位置一致，再用 SysId 量測 Feedforward。

!!! bug "常見錯誤：PathPlanner 和 PathPlannerLib 版本不一致"
    PathPlanner 應用程式存的檔案格式要和 PathPlannerLib 對應。2027 alpha 期間兩邊都會更新，更新其中一個時，另一個也要一起更新。

## 小練習

1. 在 PathPlanner 裡畫一條從起始區往前 2 公尺的直線路徑，做成自動程式，在機器人上測試。
2. 做一個自動程式：`Shoot` → 走到第二個遊戲物件（路徑上加 Event marker 觸發 `Intake`）→ 走回來 → `Shoot`。
3. 在 AdvantageScope 上同時顯示機器人的實際位置和路徑，觀察機器人跟得好不好。（提示：找找看 NetworkTables 裡 PathPlanner 開頭的資料，或用 PathPlanner 應用程式的 Telemetry 頁面）
