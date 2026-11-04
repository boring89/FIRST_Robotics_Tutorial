# 驅動的 SysId

--8<-- "alpha-warning.md"

## 這頁學什麼

- 對 Swerve 的四顆驅動馬達跑 SysId
- Dynamic 測試的電壓要怎麼選，為什麼選錯會讓 kA 不準
- 用 SysId 工具分析四個模組，比較它們的差異

## 觀念

### Swerve 的 SysId

[第 8 章](../08-control/sysid.md) 對 Shooter 跑過 SysId：讓馬達照固定的方式加電壓，記錄電壓、位置、速度，再算出 kS、kV、kA。Swerve 的做法一樣，只是：

- **四個輪子都朝正前方**，四顆驅動馬達給**同樣的電壓**，機器人直直往前（或往後）走
- **四個模組分開記錄**（`drive-FrontLeft`、`drive-FrontRight`……），在 SysId 工具裡可以一個一個分析，看看四個模組是不是差不多

### 單位：輪子的圈數

驅動馬達設定了 `SensorToMechanismRatio`，`getPosition()` 和 `getVelocity()` 的單位是**輪子的圈數**和**輪子每秒轉數**。記錄給 SysId 的也是這個單位，所以算出來的 kV 單位是「伏特 / (輪子每秒轉數)」，和 `Constants` 裡 `DRIVE_KV` 的單位一樣，**可以直接填回去**。

這也是為什麼 SysId 和輪子半徑無關：它量的是電壓和**輪子轉速**的關係，不管輪子多大。

### Dynamic 的電壓要選多少

Dynamic 測試是電壓**突然**跳到一個固定值。輪子還沒轉的那一刻，馬達沒有反電動勢，電流 ≈ 電壓 ÷ 馬達電阻：

| Dynamic 電壓 | 一開始的電流（Kraken X60，電阻約 0.033 Ω） |
| --- | --- |
| 7 V（`SysIdRoutine` 的預設值） | 約 210 A |
| 4 V | 約 120 A |
| 2 V | 約 60 A |

電流限制是 60 A：電壓太高時，電流被限制住，馬達的扭力就**比電壓應該產生的小**，機器人加速得比較慢。SysId 不知道有電流限制，會以為「這台機器人很難加速」，算出**偏大的 kA**。在真的地毯上，電流超過[打滑電流](slip-current.md) 輪子就會打滑，資料也會亂掉。

所以 Dynamic 的電壓要讓一開始的電流**低於電流限制和打滑電流**：

**Dynamic 電壓 ≤ 電流限制 × 馬達電阻 ≈ 60 × 0.033 ≈ 2 V**

在[模擬](#模擬實測) 裡實際比較，4 V 時 kA 偏大了七成，2 V 時幾乎完全準確。

Quasistatic 測試的電壓是慢慢增加的，加速度很小，電流不大，不會碰到這個問題。

## 範例

### SwerveModule：記錄給 SysId

`SwerveModule` 加上（`import org.wpilib.sysid.SysIdRoutineLog`）：

```java
/** 把驅動馬達的電壓、位置、速度記錄給 SysId。位置的單位是輪子的圈數，和 Slot0 一樣。 */
public void logSysId(SysIdRoutineLog log) {
  log.motor("drive-" + name)
      .voltage(driveMotor.getMotorVoltage().getValue())
      .angularPosition(driveMotor.getPosition().getValue())
      .angularVelocity(driveMotor.getVelocity().getValue());
}
```

給電壓用的是[上一頁](slip-current.md#swervemodule-和-drivetrain直接給電壓讀電流) 的 `runCharacterization()`。

### Drivetrain：SysIdRoutine

```java
/** 驅動馬達的 SysId：四個輪子都朝前，給同樣的電壓。 */
private final SysIdRoutine driveSysId =
    new SysIdRoutine(
        // 每秒加 1 V；Dynamic 用 2 V，電流才不會碰到 60 A 的限制；每個測試最多 5 秒
        new SysIdRoutine.Config(Volts.of(1).per(Second), Volts.of(2), Seconds.of(5)),
        new SysIdRoutine.Mechanism(
            volts -> runDriveCharacterization(volts.in(Volts)),
            log -> {
              for (SwerveModule module : modules) {
                module.logSysId(log);
              }
            },
            this));

/** 先讓輪子轉到正前方（0.5 秒），再開始 SysId 的 Quasistatic 測試。 */
public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {
  return run(() -> runDriveCharacterization(0.0))
      .withTimeout(0.5)
      .andThen(driveSysId.quasistatic(direction))
      .finallyDo(this::stop)
      .withName("DriveSysIdQuasistatic");
}
```

`sysIdDynamic()` 一樣，只是把 `quasistatic` 換成 `dynamic`。

- **`Config` 的三個參數**：Quasistatic 每秒加多少電壓、Dynamic 的電壓、每個測試最多幾秒。5 秒時，Quasistatic 最後到 5 V（約 2 公尺 / 秒），整個測試走大約 4～5 公尺
- **先轉 0.5 秒**：Dynamic 一開始就是 2 V，如果輪子還沒轉到正前方，機器人會往奇怪的方向衝。先給 0 V、讓轉向轉到 0 度，再開始測試
- **`finallyDo(this::stop)`**：測試結束或放開按鈕時停下來，並重設 Setpoint Generator（[第 17 章](../17-swerve-advanced/setpoint-generator.md)），回到開車時才不會從奇怪的速度開始
- `Volts`、`Second`、`Seconds` 要 `import static org.wpilib.units.Units.*`

??? example "完整的 SwerveModule.java"

    ```java title="src/main/java/first/robot/subsystems/SwerveModule.java"
    package first.robot.subsystems;

    import com.ctre.phoenix6.CANBus;
    import com.ctre.phoenix6.configs.CANcoderConfiguration;
    import com.ctre.phoenix6.configs.CurrentLimitsConfigs;
    import com.ctre.phoenix6.configs.TalonFXConfiguration;
    import com.ctre.phoenix6.controls.VelocityVoltage;
    import com.ctre.phoenix6.controls.VoltageOut;
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
    import org.wpilib.sysid.SysIdRoutineLog;
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
      private final VoltageOut voltageRequest = new VoltageOut(0);
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

      // ---------- 量測 ----------

      /** 轉向固定在 angle，驅動馬達直接給電壓（不經過 PID 和 Feedforward）。量測時用。 */
      public void runCharacterization(double volts, Rotation2d angle) {
        steerController.setSetpoint(
            angle.getRotations() * SwerveConstants.STEER_GEAR_RATIO, ControlType.kPosition);
        driveMotor.setControl(voltageRequest.withOutput(volts));
      }

      /** 輪子總共轉了幾弧度（輪子本身，不是馬達）。量輪子半徑時用。 */
      public double getWheelRadians() {
        return driveMotor.getPosition().getValueAsDouble() * 2 * Math.PI;
      }

      /** 驅動馬達的定子電流，單位：安培。 */
      public double getDriveCurrentAmps() {
        return driveMotor.getStatorCurrent().getValueAsDouble();
      }

      /** 改變驅動馬達的定子電流限制。量打滑電流時暫時放寬用。 */
      public void setDriveCurrentLimit(double amps) {
        driveMotor.getConfigurator().apply(
            new CurrentLimitsConfigs()
                .withStatorCurrentLimit(amps)
                .withStatorCurrentLimitEnable(true));
      }

      /** 把驅動馬達的電壓、位置、速度記錄給 SysId。位置的單位是輪子的圈數，和 Slot0 一樣。 */
      public void logSysId(SysIdRoutineLog log) {
        log.motor("drive-" + name)
            .voltage(driveMotor.getMotorVoltage().getValue())
            .angularPosition(driveMotor.getPosition().getValue())
            .angularVelocity(driveMotor.getVelocity().getValue());
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

??? example "完整的 Drivetrain.java"

    ```java title="src/main/java/first/robot/subsystems/Drivetrain.java"
    package first.robot.subsystems;

    import static org.wpilib.units.Units.Second;
    import static org.wpilib.units.Units.Seconds;
    import static org.wpilib.units.Units.Volts;

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
    import org.wpilib.command2.sysid.SysIdRoutine;
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

      /** 驅動馬達的 SysId：四個輪子都朝前，給同樣的電壓。 */
      private final SysIdRoutine driveSysId =
          new SysIdRoutine(
              // 每秒加 1 V；Dynamic 用 2 V，電流才不會碰到 60 A 的限制；每個測試最多 5 秒
              new SysIdRoutine.Config(Volts.of(1).per(Second), Volts.of(2), Seconds.of(5)),
              new SysIdRoutine.Mechanism(
                  volts -> runDriveCharacterization(volts.in(Volts)),
                  log -> {
                    for (SwerveModule module : modules) {
                      module.logSysId(log);
                    }
                  },
                  this));

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

      // ---------- 量測 ----------

      /** 四個輪子都朝正前方，驅動馬達給同樣的電壓。 */
      public void runDriveCharacterization(double volts) {
        for (SwerveModule module : modules) {
          module.runCharacterization(volts, Rotation2d.ZERO);
        }
      }

      /** 四個輪子各自轉了幾弧度。 */
      public double[] getWheelRadians() {
        double[] radians = new double[modules.length];
        for (int i = 0; i < modules.length; i++) {
          radians[i] = modules[i].getWheelRadians();
        }
        return radians;
      }

      /** 四顆驅動馬達的平均定子電流，單位：安培。 */
      public double getAverageDriveCurrentAmps() {
        double sum = 0.0;
        for (SwerveModule module : modules) {
          sum += module.getDriveCurrentAmps();
        }
        return sum / modules.length;
      }

      public void setDriveCurrentLimit(double amps) {
        for (SwerveModule module : modules) {
          module.setDriveCurrentLimit(amps);
        }
      }

      /** 先讓輪子轉到正前方（0.5 秒），再開始 SysId 的 Quasistatic 測試。 */
      public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {
        return run(() -> runDriveCharacterization(0.0))
            .withTimeout(0.5)
            .andThen(driveSysId.quasistatic(direction))
            .finallyDo(this::stop)
            .withName("DriveSysIdQuasistatic");
      }

      /** 先讓輪子轉到正前方（0.5 秒），再開始 SysId 的 Dynamic 測試。 */
      public Command sysIdDynamic(SysIdRoutine.Direction direction) {
        return run(() -> runDriveCharacterization(0.0))
            .withTimeout(0.5)
            .andThen(driveSysId.dynamic(direction))
            .finallyDo(this::stop)
            .withName("DriveSysIdDynamic");
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

綁在測試手把上，和[第 8 章](../08-control/sysid.md) 一樣：

```java
tester.faceUp().whileTrue(drivetrain.sysIdQuasistatic(SysIdRoutine.Direction.FORWARD));
tester.faceDown().whileTrue(drivetrain.sysIdQuasistatic(SysIdRoutine.Direction.REVERSE));
tester.faceRight().whileTrue(drivetrain.sysIdDynamic(SysIdRoutine.Direction.FORWARD));
tester.faceLeft().whileTrue(drivetrain.sysIdDynamic(SysIdRoutine.Direction.REVERSE));
```

## 步驟

1. 機器人放在地毯上，**前後各留 5 公尺**。正向測試往前走，反向測試往後走
2. 啟用，依序**按住**四個按鈕。每個測試最多 5 秒會自己停，快撞到東西就提早放開
3. 四個測試做完後停用，用 **Data Log Tool** 下載 log 檔（和[第 8 章](../08-control/sysid.md#執行測試和分析) 一樣）
4. 在 **SysId** 工具載入 log 檔，機構選 `drive-FrontLeft`，分析類型選 **Simple**
5. 記下 kS、kV、kA，再換 `drive-FrontRight`、`drive-BackLeft`、`drive-BackRight` 各做一次

四個模組的結果應該很接近（差 10% 以內）。某一個差很多，通常是那個模組的機構有問題：齒輪太緊、軸承壞了、輪子歪了。先修好機構再重新量。

最後取**四個模組的平均**，填回 `DRIVE_KS`、`DRIVE_KV`、`DRIVE_KA`。

## 模擬實測

在模擬裡用同樣的電壓跑 Quasistatic 和 Dynamic，記錄輪子的轉速，再用最小平方法算出 kS、kV、kA（和 SysId 工具做的事一樣），和模擬的馬達模型本身的值比較：

| | kS | kV | kA | Dynamic 一開始的電流 |
| --- | --- | --- | --- | --- |
| 馬達模型的真正值 | 0 | 0.806 | 0.0473 | |
| Dynamic 4 V | 0.004 | 0.795 | **0.0812** | 被限制在約 57 A |
| Dynamic 2 V | 0.009 | 0.805 | 0.0474 | 約 57 A，沒碰到限制 |

4 V 時 kS、kV 都準（它們主要來自 Quasistatic），但 kA 大了 72%：馬達被電流限制卡住，加速度一直維持在大約 26 圈 / 秒²，SysId 只好用比較大的 kA 解釋「為什麼加速這麼慢」。換成 2 V，三個值都和真正的值幾乎一樣。

模擬的機器人沒有摩擦力，所以 kS 是 0。真的機器人上 kS 通常在 0.1～0.3 V 之間。

## 常見錯誤

!!! bug "常見錯誤：某個模組的方向是反的"
    某個模組的 CANcoder 零點差了 180 度，那個輪子朝**後**，給正的電壓時往後滾。機器人會一邊前進一邊轉彎，那個模組的 SysId 資料方向也是反的。先照[第 13 章的第一次上電](../13-swerve-drive/first-drive.md) 確認每個輪子朝前、往前滾，再跑 SysId。

!!! bug "常見錯誤：用預設的 Dynamic 電壓"
    `new SysIdRoutine.Config()` 的 Dynamic 是 7 V，一開始的電流遠超過電流限制和打滑電流，量出來的 kA 會偏大很多。驅動馬達一定要自己設定 Dynamic 的電壓。

!!! bug "常見錯誤：架高時跑 SysId"
    輪子離地時沒有負重，kS 幾乎是 0，kA 也小很多，放到地上就完全不準。和[第 14 章](../14-swerve-tuning/tuning.md#2-驅動的-kvks) 手動調整時一樣，驅動一定要**在地上**量。

## 小練習

1. 你們的驅動馬達是 NEO（電阻約 0.114 Ω），電流限制是 50 A，Dynamic 的電壓最多可以設多少？
2. 為什麼量出來的 kV 可以直接填進 `DRIVE_KV`，不用乘以任何換算？如果 `logSysId` 記錄的是**馬達**的圈數（沒有設定 `SensorToMechanismRatio`），要怎麼換算？
3. 四個模組的 kS 分別是 0.18、0.21、0.19、0.45。你會怎麼做？

??? question "第 1 題參考答案"
    50 × 0.114 ≈ 5.7 V。NEO 的電阻比 Kraken 大很多，同樣的電壓產生的電流比較小，所以 Dynamic 的電壓可以設高一點。保守一點用 5 V。

??? question "第 2 題參考答案"
    因為記錄的單位（輪子的圈數）和 TalonFX 的 Slot0 用的單位一樣：都經過 `SensorToMechanismRatio` 換算。如果記錄的是馬達的圈數，算出來的 kV 是「伏特 / (馬達每秒轉數)」，馬達轉得比輪子快 6.75 倍，所以要**乘以 6.75** 才是輪子的 kV（kA 也一樣）。

??? question "第 3 題參考答案"
    最後一個模組的 kS 是其他的兩倍多，代表它的摩擦力特別大。不要直接取平均（會讓其他三個模組的 kS 都太大），先檢查那個模組：輪子有沒有卡到東西、齒輪有沒有太緊、軸承有沒有壞。修好之後重新量一次。
