# 加速度 Feedforward

--8<-- "alpha-warning.md"

## 這頁學什麼

- kA：加速時需要的額外電壓
- 從 Setpoint Generator 拿到每個輪子的加速度
- `VelocityVoltage.withAcceleration()`：把加速度告訴 TalonFX
- 輪子反轉時，加速度的方向也要反過來

## 觀念

### 速度目標一直在變

[第 8 章的 Feedforward](../08-control/feedforward.md) 講過：

```text
電壓 = kS × 方向 + kV × 速度 + kA × 加速度
```

前面幾章只用了 kS 和 kV。Shooter 的飛輪一直維持同一個轉速，加速度是 0，所以不需要 kA。但 Swerve 的速度目標**一直在變**：加速時，光是維持目前的速度（kV）還不夠，還要多給電壓讓它加速。沒有 kA 的話，這部分只能等誤差出現之後由 P 來補，速度就會一直落後一點。

### 加速度從哪裡來

[Setpoint Generator](setpoint-generator.md) 每 20 ms 算出的 `SwerveSetpoint` 裡，除了每個模組的速度和角度，還有 **`feedforwards()`**，其中的 `accelerationsMPSSq()` 是每個輪子這一步的加速度（公尺 / 秒²）。它本來就知道自己這一步要讓輪子加速多少，直接拿來用就好。

### kA 怎麼估

kA 的單位是「伏特 / (輪子每秒轉數 / 秒)」：輪子每秒多轉 1 圈的加速度，需要多少伏特。可以用物理估一個起點：

```text
輪子每秒多轉 1 圈 = 0.319 m/s² 的加速度
四分之一台機器人（12.5 kg）要用 12.5 × 0.319 ≈ 4.0 N 的力
→ 輪子的扭力 4.0 × 0.0508 ≈ 0.20 N·m
→ 馬達的扭力 0.20 ÷ 6.75 ≈ 0.030 N·m
→ Kraken X60 的電流 ≈ 0.030 ÷ 0.0194 ≈ 1.55 A
→ 電壓 ≈ 1.55 × 0.0328 Ω ≈ 0.05 V
```

所以 kA 大約是 **0.05**。實際的值最好用 [SysId](../08-control/sysid.md) 量。

### 輪子反轉時

`SwerveModule.setTarget()` 裡的 `optimize` 可能會把輪子反過來（角度加 180 度、速度變負）。這時候加速度也要**跟著變號**，不然 TalonFX 會往反方向加速。

## 範例

### Constants

```java
public static final double DRIVE_KA = 0.05; // 伏特 / (輪子每秒轉數 / 秒)
```

### SwerveModule

TalonFX 的設定加上 `Slot0.kA`，`setTarget` 多一個接收加速度的版本：

```java title="src/main/java/first/robot/subsystems/SwerveModule.java"
package first.robot.subsystems;

import com.ctre.phoenix6.CANBus;
import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.controls.VelocityVoltage;
import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.signals.NeutralModeValue;
import com.ctre.phoenix6.signals.SensorDirectionValue;
import com.revrobotics.PersistMode;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.FeedbackSensor;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkLowLevel.ControlType;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import first.robot.Constants.ModuleConstants;
import first.robot.Constants.SwerveConstants;
import org.wpilib.framework.RobotBase;
import org.wpilib.hardware.bus.CANPort;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
import org.wpilib.telemetry.Telemetry;

/** 一個 Swerve 模組：TalonFX 驅動輪子，SparkMax 轉向，CANcoder 量輪子指向的角度。 */
public class SwerveModule {
  private final String name;
  private final TalonFX driveMotor;
  private final SparkMax steerMotor;
  private final CANcoder encoder;

  private final RelativeEncoder steerEncoder;
  private final SparkClosedLoopController steerController;
  private final VelocityVoltage velocityRequest = new VelocityVoltage(0);
  private SwerveModuleVelocity lastTarget = new SwerveModuleVelocity();

  /** 只有在模擬時才會建立，實際的機器人上是 null。 */
  private final ModuleSim sim;

  public SwerveModule(ModuleConstants constants) {
    name = constants.name();
    driveMotor = new TalonFX(constants.driveId(), new CANBus(CANPort.CAN_S0));
    steerMotor = new SparkMax(CANPort.CAN_S0, constants.steerId(), MotorType.kBrushless);
    encoder = new CANcoder(constants.encoderId(), new CANBus(CANPort.CAN_S0));
    steerEncoder = steerMotor.getEncoder();
    steerController = steerMotor.getClosedLoopController();

    configureDriveMotor();
    configureSteerMotor();
    configureEncoder(constants.encoderOffsetRotations());

    // 要在 syncSteerEncoder() 之前建立，模擬的 CANcoder 才會有讀值
    sim = RobotBase.isSimulation()
        ? new ModuleSim(driveMotor, steerMotor, encoder, constants.encoderOffsetRotations())
        : null;
    syncSteerEncoder();
  }

  private void configureDriveMotor() {
    TalonFXConfiguration config = new TalonFXConfiguration();
    config.MotorOutput.Inverted = SwerveConstants.DRIVE_INVERTED;
    config.MotorOutput.NeutralMode = NeutralModeValue.Brake;
    config.CurrentLimits.StatorCurrentLimit = SwerveConstants.DRIVE_CURRENT_LIMIT;
    config.CurrentLimits.StatorCurrentLimitEnable = true;
    // 讓位置和速度的單位變成「輪子的圈數」，不是馬達的圈數
    config.Feedback.SensorToMechanismRatio = SwerveConstants.DRIVE_GEAR_RATIO;
    config.Slot0.kS = SwerveConstants.DRIVE_KS;
    config.Slot0.kV = SwerveConstants.DRIVE_KV;
    config.Slot0.kP = SwerveConstants.DRIVE_KP;
    config.Slot0.kA = SwerveConstants.DRIVE_KA;
    driveMotor.getConfigurator().apply(config);
  }

  private void configureSteerMotor() {
    SparkMaxConfig config = new SparkMaxConfig();
    config
        .idleMode(IdleMode.kBrake)
        .smartCurrentLimit(SwerveConstants.STEER_CURRENT_LIMIT)
        .inverted(SwerveConstants.STEER_INVERTED);
    config.closedLoop
        .feedbackSensor(FeedbackSensor.kPrimaryEncoder)
        .p(SwerveConstants.STEER_KP)
        .d(SwerveConstants.STEER_KD)
        // 輪子轉一圈 = 馬達轉 STEER_GEAR_RATIO 圈，超過範圍就從另一端繞回來
        .positionWrappingEnabled(true)
        .positionWrappingInputRange(
            -SwerveConstants.STEER_GEAR_RATIO / 2, SwerveConstants.STEER_GEAR_RATIO / 2);
    steerMotor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
  }

  private void configureEncoder(double offsetRotations) {
    CANcoderConfiguration config = new CANcoderConfiguration();
    config.MagnetSensor.MagnetOffset = offsetRotations;
    config.MagnetSensor.SensorDirection = SensorDirectionValue.CounterClockwise_Positive;
    // 讀值範圍 -0.5～0.5 圈，也就是 -180～180 度
    config.MagnetSensor.AbsoluteSensorDiscontinuityPoint = 0.5;
    encoder.getConfigurator().apply(config);
  }

  /** 用 CANcoder 的絕對角度，設定 NEO 內建編碼器的位置。 */
  public void syncSteerEncoder() {
    double moduleRotations = encoder.getAbsolutePosition().waitForUpdate(0.1).getValueAsDouble();
    steerEncoder.setPosition(moduleRotations * SwerveConstants.STEER_GEAR_RATIO);
  }

  // ---------- 感測器 ----------

  /** 輪子指向的角度，逆時針為正，0 度是機器人的正前方。 */
  public Rotation2d getAngle() {
    return Rotation2d.fromRotations(encoder.getAbsolutePosition().getValueAsDouble());
  }

  /** 輪子的速度，單位：公尺 / 秒。 */
  public double getVelocityMetersPerSecond() {
    return driveMotor.getVelocity().getValueAsDouble() * SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
  }

  /** 目前的速度和角度。 */
  public SwerveModuleVelocity getVelocity() {
    return new SwerveModuleVelocity(getVelocityMetersPerSecond(), getAngle());
  }

  /** 輪子總共滾了多遠，單位：公尺。 */
  public double getDistanceMeters() {
    return driveMotor.getPosition().getValueAsDouble() * SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
  }

  /** 目前走過的距離和角度，給 Odometry 用。 */
  public SwerveModulePosition getPosition() {
    return new SwerveModulePosition(getDistanceMeters(), getAngle());
  }

  // ---------- 控制 ----------

  /** 讓模組轉到指定的角度，並用指定的速度滾動。 */
  public void setTarget(SwerveModuleVelocity target) {
    setTarget(target, 0.0);
  }

  /**
   * 讓模組轉到指定的角度，用指定的速度滾動，並告訴 TalonFX 預期的加速度。
   *
   * @param accelerationMetersPerSecSq 輪子的加速度，單位：公尺 / 秒²
   */
  public void setTarget(SwerveModuleVelocity target, double accelerationMetersPerSecSq) {
    Rotation2d current = getAngle();
    // 要轉超過 90 度時，改成反方向轉、輪子倒著滾
    SwerveModuleVelocity optimized = target.optimize(current);
    if (!optimized.angle.equals(target.angle)) {
      // 輪子倒著滾，加速度的方向也要反過來
      accelerationMetersPerSecSq = -accelerationMetersPerSecSq;
    }
    target = optimized;
    // 輪子還沒轉到位時先放慢，避免往錯的方向推
    target = target.cosineScale(current);
    lastTarget = target;

    steerController.setSetpoint(
        target.angle.getRotations() * SwerveConstants.STEER_GEAR_RATIO, ControlType.kPosition);

    double wheelRps = target.velocity / SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
    double wheelRpsPerSec = accelerationMetersPerSecSq / SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
    driveMotor.setControl(velocityRequest.withVelocity(wheelRps).withAcceleration(wheelRpsPerSec));
  }

  public void stop() {
    driveMotor.stopMotor();
    steerMotor.stopMotor();
  }

  /** 把目標和實際的角度、速度送到 Telemetry，調參數時用。 */
  public void log() {
    Telemetry.log("Swerve/" + name + "/AngleDegrees", getAngle().getDegrees());
    Telemetry.log("Swerve/" + name + "/TargetAngleDegrees", lastTarget.angle.getDegrees());
    Telemetry.log("Swerve/" + name + "/Velocity", getVelocityMetersPerSecond());
    Telemetry.log("Swerve/" + name + "/TargetVelocity", lastTarget.velocity);
  }

  /** 只在模擬時呼叫：更新馬達模型。 */
  public void simulationPeriodic() {
    sim.update(0.02);
  }
}
```

- **兩個 `setTarget`**：只有速度的版本（例如 `lock()`）呼叫有加速度的版本，加速度傳 0。這叫**方法多載 (Overloading)**：同一個名字、不同的參數，Java 會依照呼叫時傳入的參數決定用哪一個
- **判斷有沒有反轉**：`optimize` 之後角度變了，就代表輪子反轉了，加速度要變號
- **`withAcceleration()`**：單位和速度一樣是**輪子**的圈數，所以也要除以周長

### Drivetrain

`driveRobotRelative()` 把加速度一起交給模組。這是這一章完整的 `Drivetrain`，包含[上一頁](setpoint-generator.md) 的 Setpoint Generator：

```java title="src/main/java/first/robot/subsystems/Drivetrain.java"
package first.robot.subsystems;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.config.PIDConstants;
import com.pathplanner.lib.config.ModuleConfig;
import com.pathplanner.lib.config.RobotConfig;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;
import com.pathplanner.lib.util.DriveFeedforwards;
import com.pathplanner.lib.util.swerve.SwerveSetpoint;
import com.pathplanner.lib.util.swerve.SwerveSetpointGenerator;
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
import org.wpilib.math.system.DCMotor;
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

  /** 用 Constants 建立的機器人設定，給 Setpoint generator 用。 */
  private static final RobotConfig ROBOT_CONFIG =
      new RobotConfig(
          SwerveConstants.ROBOT_MASS_KG,
          SwerveConstants.ROBOT_MOI,
          new ModuleConfig(
              SwerveConstants.WHEEL_DIAMETER_METERS / 2,
              SwerveConstants.MAX_SPEED,
              SwerveConstants.WHEEL_COF,
              DCMotor.getKrakenX60(1),
              SwerveConstants.DRIVE_GEAR_RATIO,
              SwerveConstants.DRIVE_CURRENT_LIMIT,
              1),
          SwerveConstants.FRONT_LEFT.location(),
          SwerveConstants.FRONT_RIGHT.location(),
          SwerveConstants.BACK_LEFT.location(),
          SwerveConstants.BACK_RIGHT.location());

  private final SwerveSetpointGenerator setpointGenerator =
      new SwerveSetpointGenerator(ROBOT_CONFIG, SwerveConstants.MAX_STEER_VELOCITY);
  private SwerveSetpoint previousSetpoint;

  public Drivetrain() {
    poseEstimator =
        new SwerveDrivePoseEstimator(kinematics, getHeading(), getModulePositions(), Pose2d.ZERO);
    resetSetpoint();
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
    // Setpoint generator 會自己 discretize 和限速，這裡不用再做
    previousSetpoint = setpointGenerator.generateSetpoint(previousSetpoint, velocities, 0.02);
    SwerveModuleVelocity[] targets = previousSetpoint.moduleStates();
    double[] accelerations = previousSetpoint.feedforwards().accelerationsMPSSq();
    for (int i = 0; i < modules.length; i++) {
      modules[i].setTarget(targets[i], accelerations[i]);
    }
    Telemetry.log("Drivetrain/TargetVelocities", targets);
  }

  /** 從目前實際的速度重新開始，例如停止之後。 */
  private void resetSetpoint() {
    previousSetpoint =
        new SwerveSetpoint(
            getRobotRelativeVelocities(),
            getModuleVelocities(),
            DriveFeedforwards.zeros(modules.length));
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
    resetSetpoint();
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

### 在模擬裡比較

從靜止加速到 4 m/s（Setpoint Generator 給的目標速度和實際速度，每 0.1 秒記錄一次）：

| 時間 | 目標 | 實際（沒有 kA） | 實際（kA = 0.05） |
| --- | --- | --- | --- |
| 0.1 秒 | 0.83 | 0.31 | 0.51 |
| 0.2 秒 | 1.66 | 1.06 | 1.41 |
| 0.3 秒 | 2.50 | 1.88 | 2.32 |
| 0.4 秒 | 3.33 | 2.72 | 3.16 |
| 0.5 秒 | 4.00 | 3.56 | 4.08 |

加速的 0.8 秒裡，平均落後從 **0.37 m/s** 降到 **0.16 m/s**。速度跟得緊，Odometry 和 PathPlanner 的路徑跟隨都會更準。

## 常見錯誤

!!! bug "常見錯誤：反轉時沒有把加速度變號"
    輪子反轉、往後滾的時候還給它正的加速度，TalonFX 會一邊想往後、一邊想往前加速，速度控制會抖動。

!!! bug "常見錯誤：kA 的單位用錯"
    `withAcceleration()` 的單位和 `withVelocity()` 一樣，因為設了 `SensorToMechanismRatio`，是**輪子**的每秒轉數 / 秒。直接傳公尺 / 秒²，加速度會變成大約 3 倍（1 ÷ 0.319）。

!!! bug "常見錯誤：kA 太大"
    kA 太大時，每次加速都會衝過頭，速度在目標上下晃。先用估計的值，在 AdvantageScope 上比較 `Velocity` 和 `TargetVelocity`，加速時實際速度超過目標就調小。

## 小練習

1. 把 `DRIVE_KA` 改成 0.1、0.2，在模擬裡從 0 加速到 4 m/s，看實際速度會不會超過目標。
2. PathPlanner 的 `AutoBuilder.configure` 傳給我們的 `feedforwards` 裡也有加速度。這一章的 `driveRobotRelative` 只用了 `velocities`，PathPlanner 的路徑也會經過 Setpoint Generator。想一想：這樣做有什麼好處和壞處？

??? question "第 2 題參考答案"
    **好處**：自動和手動都經過同一套限制，程式比較簡單；路徑規劃得太激進時，Generator 也會擋下來，不會打滑。

    **壞處**：PathPlanner 規劃路徑時已經考慮過機器人的限制，再經過 Generator 一次，可能會多一點延遲，路徑跟隨稍微落後。另一種做法是 PathPlanner 的輸出不經過 Generator，直接用 PathPlanner 給的 `feedforwards` 裡的加速度。兩種都有隊伍在用。
