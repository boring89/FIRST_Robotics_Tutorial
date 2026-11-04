# 改寫 Swerve

--8<-- "alpha-warning.md"

## 這頁學什麼

- `ModuleIO`：模組的介面和輸入資料
- `ModuleIOReal`、`ModuleIOSim`：真的硬體和模擬
- `GyroIO`：沒有 IMU 時，用模組的資料估計朝向
- 改寫後的 `SwerveModule`、`Drivetrain` 和 `RobotContainer`

這一頁的程式用到 AdvantageKit 的 `@AutoLog` 和 `Logger`，要先照[下一頁](advantagekit.md) 安裝 AdvantageKit 才能編譯。兩頁的內容是一起的，先讀完這頁了解架構，再到下一頁安裝。

## 觀念

### 檔案怎麼分

| 檔案 | 內容 | 從哪裡來 |
| --- | --- | --- |
| `ModuleIO.java` | 介面 + `ModuleIOInputs` | 新的 |
| `ModuleIOReal.java` | TalonFX、SparkMax、CANcoder 的設定、讀取、控制 | 第 17 章 `SwerveModule` 的硬體部分 |
| `ModuleIOSim.java` | 繼承 `ModuleIOReal`，每次讀取前先更新馬達模型 | 第 15 章的 `ModuleSim` |
| `GyroIO.java`、`GyroIOOnboard.java` | IMU 的介面和實作 | 第 17 章 `Drivetrain` 裡的 `OnboardIMU` |
| `SwerveModule.java` | 只剩邏輯：`optimize`、`cosineScale`、加速度變號 | 第 17 章 `SwerveModule` 的邏輯部分 |

第 15 章的 `ModuleSim.java` 不需要了，可以刪掉。

### `ModuleIOSim` 為什麼繼承 `ModuleIOReal`

[第 15 章](../15-swerve-sim/module-sim.md) 的模擬用的是**廠商的模擬**：TalonFX、SparkMax、CANcoder 的物件和真的一樣，只是背後換成模擬。所以模擬的 IO 和真的 IO **大部分程式都一樣**，差別只在每次讀取之前，要先用馬達模型算出輪子怎麼轉。用繼承最省事：

```java
@Override
public void updateInputs(ModuleIOInputs inputs) {
  // 先更新馬達模型 ...
  super.updateInputs(inputs);   // 再用和真的機器人一模一樣的方法讀取
}
```

`ModuleIOReal` 的三個裝置是 **`protected`**：子類別（`ModuleIOSim`）看得到，其他類別看不到。

### 模擬時的 IMU

[第 15 章](../15-swerve-sim/module-sim.md#imu-怎麼模擬) 用 `OnboardIMUSim.setYaw()` 告訴模擬的 IMU 機器人轉了多少。改寫之後有更簡單的做法：**模擬時根本不用 IMU**。

`GyroIOInputs` 有一個 `connected`。模擬時用空的 `new GyroIO() {}`，`connected` 一直是 `false`，`Drivetrain` 就改用四個模組這 20 ms 走的距離和角度，用 Kinematics 算出機器人轉了多少：

```java
if (gyroInputs.connected) {
  rawHeading = gyroInputs.yaw;
} else {
  Twist2d twist = kinematics.toTwist2d(lastModulePositions, positions);
  rawHeading = rawHeading.plus(new Rotation2d(twist.dtheta));
}
```

**`Twist2d`** 是「一小段時間裡，機器人往前、往左移動多少，轉了多少（`dtheta`）」。真的機器人上 IMU 斷線時，這段程式也會自動接手，不會因為 IMU 壞掉就完全失去朝向。

## 範例

### ModuleIO

```java title="src/main/java/first/robot/subsystems/ModuleIO.java"
package first.robot.subsystems;

import org.littletonrobotics.junction.AutoLog;
import org.wpilib.math.geometry.Rotation2d;

/** 一個 Swerve 模組的硬體：只負責讀感測器、送指令，不做任何判斷。 */
public interface ModuleIO {
  /** 每 20 ms 從硬體讀到的所有資料。 */
  @AutoLog
  class ModuleIOInputs {
    public double drivePositionMeters = 0.0;
    public double driveVelocityMetersPerSec = 0.0;
    public double driveAppliedVolts = 0.0;
    public double driveCurrentAmps = 0.0;
    /** CANcoder 量到的輪子角度。 */
    public Rotation2d steerAngle = Rotation2d.ZERO;
    public double steerCurrentAmps = 0.0;
  }

  /** 讀取硬體，把結果寫進 inputs。 */
  default void updateInputs(ModuleIOInputs inputs) {}

  /** 驅動輪的目標速度（公尺 / 秒）和加速度（公尺 / 秒²）。 */
  default void setDriveVelocity(double metersPerSec, double accelerationMetersPerSecSq) {}

  /** 輪子要轉到的角度。 */
  default void setSteerAngle(Rotation2d angle) {}

  default void stop() {}
}
```

- **`@AutoLog`**：AdvantageKit 會根據這個類別，自動產生一個 `ModuleIOInputsAutoLogged`，知道怎麼把每個欄位寫進 log、從 log 讀回來（[下一頁](advantagekit.md)）
- 輸入資料的單位都已經換算好：公尺、公尺 / 秒、`Rotation2d`
- 介面裡的 `class` 自動是 `public static` 的，不用另外寫

### ModuleIOReal

和第 17 章 `SwerveModule` 的硬體部分幾乎一樣，只是把「讀取」集中到 `updateInputs()`：

```java title="src/main/java/first/robot/subsystems/ModuleIOReal.java"
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
import org.wpilib.hardware.bus.CANPort;
import org.wpilib.math.geometry.Rotation2d;

/** 真正的硬體：TalonFX 驅動、SparkMax 轉向、CANcoder 量角度。 */
public class ModuleIOReal implements ModuleIO {
  protected final TalonFX driveMotor;
  protected final SparkMax steerMotor;
  protected final CANcoder encoder;

  private final RelativeEncoder steerEncoder;
  private final SparkClosedLoopController steerController;
  private final VelocityVoltage velocityRequest = new VelocityVoltage(0);

  public ModuleIOReal(ModuleConstants constants) {
    driveMotor = new TalonFX(constants.driveId(), new CANBus(CANPort.CAN_S0));
    steerMotor = new SparkMax(CANPort.CAN_S0, constants.steerId(), MotorType.kBrushless);
    encoder = new CANcoder(constants.encoderId(), new CANBus(CANPort.CAN_S0));
    steerEncoder = steerMotor.getEncoder();
    steerController = steerMotor.getClosedLoopController();

    configureDriveMotor();
    configureSteerMotor();
    configureEncoder(constants.encoderOffsetRotations());
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
  protected void syncSteerEncoder() {
    double moduleRotations = encoder.getAbsolutePosition().waitForUpdate(0.1).getValueAsDouble();
    steerEncoder.setPosition(moduleRotations * SwerveConstants.STEER_GEAR_RATIO);
  }

  @Override
  public void updateInputs(ModuleIOInputs inputs) {
    double circumference = SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
    inputs.drivePositionMeters = driveMotor.getPosition().getValueAsDouble() * circumference;
    inputs.driveVelocityMetersPerSec = driveMotor.getVelocity().getValueAsDouble() * circumference;
    inputs.driveAppliedVolts = driveMotor.getMotorVoltage().getValueAsDouble();
    inputs.driveCurrentAmps = driveMotor.getStatorCurrent().getValueAsDouble();
    inputs.steerAngle = Rotation2d.fromRotations(encoder.getAbsolutePosition().getValueAsDouble());
    inputs.steerCurrentAmps = steerMotor.getOutputCurrent().get(0.0);
  }

  @Override
  public void setDriveVelocity(double metersPerSec, double accelerationMetersPerSecSq) {
    double circumference = SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
    driveMotor.setControl(
        velocityRequest
            .withVelocity(metersPerSec / circumference)
            .withAcceleration(accelerationMetersPerSecSq / circumference));
  }

  @Override
  public void setSteerAngle(Rotation2d angle) {
    steerController.setSetpoint(
        angle.getRotations() * SwerveConstants.STEER_GEAR_RATIO, ControlType.kPosition);
  }

  @Override
  public void stop() {
    driveMotor.stopMotor();
    steerMotor.stopMotor();
  }
}
```

### ModuleIOSim

```java title="src/main/java/first/robot/subsystems/ModuleIOSim.java"
package first.robot.subsystems;

import com.ctre.phoenix6.sim.CANcoderSimState;
import com.ctre.phoenix6.sim.TalonFXSimState;
import com.revrobotics.sim.SparkMaxSim;
import first.robot.Constants.ModuleConstants;
import first.robot.Constants.SwerveConstants;
import org.wpilib.math.system.DCMotor;
import org.wpilib.math.system.Models;
import org.wpilib.simulation.DCMotorSim;
import org.wpilib.system.RobotController;

/**
 * 模擬的模組：和 ModuleIOReal 用同樣的 TalonFX、SparkMax、CANcoder 物件，每次讀取之前先用馬達模型算出輪子怎麼轉。
 */
public class ModuleIOSim extends ModuleIOReal {
  // 轉動慣量，單位：kg·m²。驅動輪要推動四分之一台機器人，所以比轉向大很多
  private static final double DRIVE_INERTIA = 0.03;
  private static final double STEER_INERTIA = 0.004;

  private final DCMotor driveGearbox = DCMotor.getKrakenX60(1);
  private final DCMotor steerGearbox = DCMotor.getNEO(1);

  private final DCMotorSim drivePhysics =
      new DCMotorSim(
          Models.singleJointedArmFromPhysicalConstants(
              driveGearbox, DRIVE_INERTIA, SwerveConstants.DRIVE_GEAR_RATIO),
          driveGearbox);
  private final DCMotorSim steerPhysics =
      new DCMotorSim(
          Models.singleJointedArmFromPhysicalConstants(
              steerGearbox, STEER_INERTIA, SwerveConstants.STEER_GEAR_RATIO),
          steerGearbox);

  private final TalonFXSimState driveSim;
  private final SparkMaxSim steerSim;
  private final CANcoderSimState encoderSim;
  private final double encoderOffsetRotations;

  public ModuleIOSim(ModuleConstants constants) {
    super(constants);
    driveSim = driveMotor.getSimState();
    steerSim = new SparkMaxSim(steerMotor, steerGearbox);
    encoderSim = encoder.getSimState();
    encoderOffsetRotations = constants.encoderOffsetRotations();

    // super() 校正的時候模擬的 CANcoder 還沒有讀值，設定好之後再校正一次
    updateEncoder();
    syncSteerEncoder();
  }

  @Override
  public void updateInputs(ModuleIOInputs inputs) {
    double dt = 0.02;
    double battery = RobotController.getBatteryVoltage();

    // 驅動：TalonFX 輸出的電壓 → 馬達模型 → 把轉子的位置和速度告訴 TalonFX
    driveSim.setSupplyVoltage(battery);
    drivePhysics.setInputVoltage(driveSim.getMotorVoltage());
    drivePhysics.update(dt);
    double driveRotorRotations =
        drivePhysics.getAngularPosition() / (2 * Math.PI) * SwerveConstants.DRIVE_GEAR_RATIO;
    double driveRotorRps =
        drivePhysics.getAngularVelocity() / (2 * Math.PI) * SwerveConstants.DRIVE_GEAR_RATIO;
    driveSim.setRawRotorPosition(driveRotorRotations);
    driveSim.setRotorVelocity(driveRotorRps);

    // 轉向：SparkMax 輸出的比例 × 電池電壓 → 馬達模型 → 把馬達轉速告訴 SparkMax
    steerPhysics.setInputVoltage(steerSim.getAppliedOutput() * battery);
    steerPhysics.update(dt);
    double steerMotorRpm =
        steerPhysics.getAngularVelocity() / (2 * Math.PI) * 60 * SwerveConstants.STEER_GEAR_RATIO;
    steerSim.iterate(steerMotorRpm, battery, dt);

    updateEncoder();

    // 物理模型更新完之後，用和真的機器人一模一樣的方法讀取
    super.updateInputs(inputs);
  }

  /** CANcoder 裝在轉向軸上，讀到的就是輪子的角度（再扣掉零點）。 */
  private void updateEncoder() {
    double moduleRotations = steerPhysics.getAngularPosition() / (2 * Math.PI);
    encoderSim.setRawPosition(moduleRotations - encoderOffsetRotations);
  }
}
```

- 建構子先呼叫 `super(constants)`，建立和設定好真的裝置，再取得它們的模擬狀態
- `super()` 裡的 `syncSteerEncoder()` 執行時，模擬的 CANcoder 還沒有讀值（[第 15 章的常見錯誤](../15-swerve-sim/module-sim.md#常見錯誤)），所以設定好之後再校正一次

### GyroIO

```java title="src/main/java/first/robot/subsystems/GyroIO.java"
package first.robot.subsystems;

import org.littletonrobotics.junction.AutoLog;
import org.wpilib.math.geometry.Rotation2d;

/** 量機器人朝向的硬體。 */
public interface GyroIO {
  @AutoLog
  class GyroIOInputs {
    /** 沒有 IMU 時是 false，Drivetrain 會改用模組的資料估計朝向。 */
    public boolean connected = false;
    public Rotation2d yaw = Rotation2d.ZERO;
    public double yawVelocityRadPerSec = 0.0;
  }

  default void updateInputs(GyroIOInputs inputs) {}
}
```

```java title="src/main/java/first/robot/subsystems/GyroIOOnboard.java"
package first.robot.subsystems;

import org.wpilib.hardware.imu.OnboardIMU;
import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;

/** Systemcore 內建的 IMU。 */
public class GyroIOOnboard implements GyroIO {
  private final OnboardIMU imu = new OnboardIMU(MountOrientation.FLAT);

  @Override
  public void updateInputs(GyroIOInputs inputs) {
    inputs.connected = true;
    inputs.yaw = imu.getRotation2d();
    inputs.yawVelocityRadPerSec = imu.getGyroRateZ();
  }
}
```

### SwerveModule

硬體都搬走之後，`SwerveModule` 只剩下邏輯：

```java title="src/main/java/first/robot/subsystems/SwerveModule.java"
package first.robot.subsystems;

import org.littletonrobotics.junction.Logger;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.math.kinematics.SwerveModuleVelocity;

/** 一個 Swerve 模組的邏輯。硬體的部分交給 ModuleIO，這裡不知道底下是真的馬達、模擬，還是 log 重播。 */
public class SwerveModule {
  private final ModuleIO io;
  private final ModuleIOInputsAutoLogged inputs = new ModuleIOInputsAutoLogged();
  private final String name;

  public SwerveModule(ModuleIO io, String name) {
    this.io = io;
    this.name = name;
  }

  /** 每 20 ms 呼叫一次：讀取硬體，並記錄到 log。要在使用任何讀值之前呼叫。 */
  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs("Drivetrain/Module/" + name, inputs);
  }

  // ---------- 感測器 ----------

  /** 輪子指向的角度，逆時針為正，0 度是機器人的正前方。 */
  public Rotation2d getAngle() {
    return inputs.steerAngle;
  }

  /** 目前的速度和角度。 */
  public SwerveModuleVelocity getVelocity() {
    return new SwerveModuleVelocity(inputs.driveVelocityMetersPerSec, getAngle());
  }

  /** 目前走過的距離和角度，給 Odometry 用。 */
  public SwerveModulePosition getPosition() {
    return new SwerveModulePosition(inputs.drivePositionMeters, getAngle());
  }

  // ---------- 控制 ----------

  /** 讓模組轉到指定的角度，並用指定的速度滾動。 */
  public void setTarget(SwerveModuleVelocity target) {
    setTarget(target, 0.0);
  }

  /**
   * 讓模組轉到指定的角度，用指定的速度滾動，並告訴驅動馬達預期的加速度。
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
    // 輪子還沒轉到位時先放慢，避免往錯的方向推
    target = optimized.cosineScale(current);

    io.setSteerAngle(target.angle);
    io.setDriveVelocity(target.velocity, accelerationMetersPerSecSq);
    Logger.recordOutput("Drivetrain/Module/" + name + "/Target", target);
  }

  public void stop() {
    io.stop();
  }
}
```

- **`periodic()`**：每 20 ms 讀一次硬體，並交給 `Logger.processInputs()`。重播時，`processInputs` 會反過來，**用 log 裡的值蓋掉** `inputs`
- 所有的讀值都來自 `inputs`，`setTarget()` 的邏輯和第 17 章完全一樣
- 目標值用 `Logger.recordOutput()` 記錄，[下一頁](advantagekit.md#輸入和輸出) 會說明它和 `processInputs` 的差別

### Drivetrain

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
import org.littletonrobotics.junction.Logger;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchState;
import org.wpilib.math.estimator.SwerveDrivePoseEstimator;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Twist2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.SwerveDriveKinematics;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
import org.wpilib.math.linalg.Matrix;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N3;
import org.wpilib.math.system.DCMotor;

public class Drivetrain extends SubsystemBase {
  private final GyroIO gyroIO;
  private final GyroIOInputsAutoLogged gyroInputs = new GyroIOInputsAutoLogged();
  // 順序固定是左前、右前、左後、右後，Kinematics 也用同樣的順序
  private final SwerveModule[] modules;

  private final SwerveDriveKinematics kinematics =
      new SwerveDriveKinematics(
          SwerveConstants.FRONT_LEFT.location(),
          SwerveConstants.FRONT_RIGHT.location(),
          SwerveConstants.BACK_LEFT.location(),
          SwerveConstants.BACK_RIGHT.location());

  /** Odometry 用的朝向：有 IMU 時是 IMU 的讀值，沒有時用模組的資料估計。 */
  private Rotation2d rawHeading = Rotation2d.ZERO;
  private SwerveModulePosition[] lastModulePositions;

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

  /** 四個模組的順序是左前、右前、左後、右後。 */
  public Drivetrain(
      GyroIO gyroIO,
      ModuleIO frontLeft,
      ModuleIO frontRight,
      ModuleIO backLeft,
      ModuleIO backRight) {
    this.gyroIO = gyroIO;
    modules =
        new SwerveModule[] {
          new SwerveModule(frontLeft, SwerveConstants.FRONT_LEFT.name()),
          new SwerveModule(frontRight, SwerveConstants.FRONT_RIGHT.name()),
          new SwerveModule(backLeft, SwerveConstants.BACK_LEFT.name()),
          new SwerveModule(backRight, SwerveConstants.BACK_RIGHT.name()),
        };
    lastModulePositions = getModulePositions();
    poseEstimator =
        new SwerveDrivePoseEstimator(kinematics, rawHeading, lastModulePositions, Pose2d.ZERO);
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
    // 電壓固定用 12 V：讓 log 重播時算出一樣的結果
    previousSetpoint =
        setpointGenerator.generateSetpoint(previousSetpoint, velocities, null, 0.02, 12.0);
    SwerveModuleVelocity[] targets = previousSetpoint.moduleStates();
    double[] accelerations = previousSetpoint.feedforwards().accelerationsMPSSq();
    for (int i = 0; i < modules.length; i++) {
      modules[i].setTarget(targets[i], accelerations[i]);
    }
    Logger.recordOutput("Drivetrain/TargetVelocities", targets);
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
    // 先讀取所有硬體，之後的計算都只用 inputs 裡的值
    gyroIO.updateInputs(gyroInputs);
    Logger.processInputs("Drivetrain/Gyro", gyroInputs);
    for (SwerveModule module : modules) {
      module.periodic();
    }

    SwerveModulePosition[] positions = getModulePositions();
    if (gyroInputs.connected) {
      rawHeading = gyroInputs.yaw;
    } else {
      // 沒有 IMU（模擬、重播）：用四個模組這 20 ms 走的距離，算出機器人轉了多少
      Twist2d twist = kinematics.toTwist2d(lastModulePositions, positions);
      rawHeading = rawHeading.plus(new Rotation2d(twist.dtheta));
    }
    lastModulePositions = positions;
    poseEstimator.update(rawHeading, positions);

    Logger.recordOutput("Drivetrain/Pose", getPose());
    Logger.recordOutput("Drivetrain/ModuleVelocities", getModuleVelocities());
  }
}
```

和第 17 章比較：

- **建構子收五個 IO**：一個 IMU、四個模組。`Drivetrain` 自己不建立任何硬體
- **`periodic()` 一開始先讀所有硬體**，之後的 Odometry 都只用讀到的值
- **`simulationPeriodic()` 不見了**：模擬的馬達模型在 `ModuleIOSim.updateInputs()` 裡更新，IMU 改用模組的資料估計
- **`getHeading()` 不見了**：Odometry 用的朝向是 `rawHeading`，其他地方本來就用 `getPose().getRotation()`
- **`Telemetry.log` 改成 `Logger.recordOutput`**：資料會進到 AdvantageKit 的 log，重播時可以比較
- **Setpoint Generator 的電壓固定用 12 V**：原本的寫法會讀 `RobotController` 的電池電壓，這個讀值沒有經過 IO，重播時會不一樣（[Log replay](replay.md#哪些寫法會讓重播失準)）

### RobotContainer

依照執行模式，決定 `Drivetrain` 底下接的是哪一種 IO：

```java title="src/main/java/first/robot/RobotContainer.java"
package first.robot;

import com.pathplanner.lib.auto.AutoBuilder;
import first.robot.Constants.OperatorConstants;
import first.robot.Constants.SwerveConstants;
import first.robot.commands.AimAtTarget;
import first.robot.commands.TeleopDrive;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.GyroIO;
import first.robot.subsystems.GyroIOOnboard;
import first.robot.subsystems.ModuleIO;
import first.robot.subsystems.ModuleIOReal;
import first.robot.subsystems.ModuleIOSim;
import first.robot.subsystems.Vision;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.button.CommandGamepad;
import org.wpilib.math.util.MathUtil;
import org.wpilib.tunable.Selectable;
import org.wpilib.tunable.Tunables;

public class RobotContainer {
  private final Drivetrain drivetrain = createDrivetrain();
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

  /** 依照執行模式，決定 Drivetrain 底下接的是哪一種硬體。 */
  private static Drivetrain createDrivetrain() {
    return switch (Constants.CURRENT_MODE) {
      case REAL -> new Drivetrain(
          new GyroIOOnboard(),
          new ModuleIOReal(SwerveConstants.FRONT_LEFT),
          new ModuleIOReal(SwerveConstants.FRONT_RIGHT),
          new ModuleIOReal(SwerveConstants.BACK_LEFT),
          new ModuleIOReal(SwerveConstants.BACK_RIGHT));
      case SIM -> new Drivetrain(
          new GyroIO() {},   // 模擬裡沒有 IMU，Drivetrain 會用模組的資料估計朝向
          new ModuleIOSim(SwerveConstants.FRONT_LEFT),
          new ModuleIOSim(SwerveConstants.FRONT_RIGHT),
          new ModuleIOSim(SwerveConstants.BACK_LEFT),
          new ModuleIOSim(SwerveConstants.BACK_RIGHT));
      case REPLAY -> new Drivetrain(
          // 重播時不碰任何硬體，所有讀值都從 log 檔來
          new GyroIO() {},
          new ModuleIO() {},
          new ModuleIO() {},
          new ModuleIO() {},
          new ModuleIO() {});
    };
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

`Constants.CURRENT_MODE` 在[下一頁](advantagekit.md#constants執行模式) 加上。`switch` 的 `->` 寫法是 Java 14 起的 **Switch expression**：每個 `case` 直接產生一個值，整個 `switch` 的結果就是 `createDrivetrain()` 的回傳值。

## 常見錯誤

!!! bug "常見錯誤：先用讀值、後讀硬體"
    `Drivetrain.periodic()` 裡先算 Odometry，最後才呼叫 `module.periodic()`：Odometry 用的是**上一次**的讀值，而且重播時順序不同，結果會不一樣。**一定要先讀所有硬體**，再做任何計算。

!!! bug "常見錯誤：模組的順序傳錯"
    `new Drivetrain(gyro, 左前, 左後, 右前, 右後)`：建構子的順序是左前、右前、左後、右後，和 Kinematics 一樣。傳錯的話，旋轉時四個輪子會亂掉。

!!! bug "常見錯誤：IO 的 `updateInputs` 裡沒有寫到某個欄位"
    忘了在 `ModuleIOReal.updateInputs()` 裡設定 `steerAngle`，它就一直是預設值 0 度。程式不會報錯，但輪子的角度永遠是 0，`optimize` 和 Odometry 全錯。新增欄位之後，**每個實作的 `updateInputs` 都要檢查一次**。

## 小練習

1. 把[第 8 章的 Shooter](../08-control/feedforward.md) 改成 `ShooterIO`、`ShooterIOReal` 的架構（REV 或 CTRE 擇一）。
2. `ModuleIOInputs` 加一個 `driveTemperatureCelsius`（TalonFX 的 `getDeviceTemp()`），在 AdvantageScope 上看四個驅動馬達的溫度。
3. 如果某一年換成全 CTRE 的模組（TalonFX 轉向），要改哪些檔案？`SwerveModule` 和 `Drivetrain` 要改嗎？

??? question "第 2 題參考答案"
    在 `ModuleIOInputs` 加一個欄位：

    ```java
    public double driveTemperatureCelsius = 0.0;
    ```

    在 `ModuleIOReal.updateInputs()` 讀取：

    ```java
    inputs.driveTemperatureCelsius = driveMotor.getDeviceTemp().getValueAsDouble();
    ```

    `ModuleIOSim` 繼承了 `ModuleIOReal`，所以不用另外改。`@AutoLog` 會自動把新欄位記錄到 log，在 AdvantageScope 的 `Drivetrain/Module/FrontLeft/DriveTemperatureCelsius` 就能看到。

??? question "第 3 題參考答案"
    只要新增一個 `ModuleIOTalonFX`（或改寫 `ModuleIOReal`），把 SparkMax 換成第二顆 TalonFX，轉向改用 TalonFX 的位置控制。模擬也要跟著改成 `TalonFXSimState`。

    `SwerveModule` 和 `Drivetrain` **都不用改**：它們只透過 `ModuleIO` 的方法和 `ModuleIOInputs` 溝通，不知道底下是哪一家的馬達。這就是硬體抽象化的好處。
