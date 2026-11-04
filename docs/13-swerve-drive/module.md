# SwerveModule

--8<-- "alpha-warning.md"

## 這頁學什麼

- 為什麼 `SwerveModule` 不是 Subsystem
- 驅動：用 TalonFX 的速度控制，讓輪子用指定的速度滾
- 轉向：用 Systemcore 上的 `PIDController` 讀 CANcoder，讓輪子轉到指定角度
- `enableContinuousInput`：角度是一個圓

## 觀念

### 一個模組只做一件事

`SwerveModule` 對外只提供一件事：**「轉到這個角度，用這個速度滾」**。WPILib 用 `SwerveModuleVelocity` 描述這件事（[第 7 章](../07-drivetrain/swerve.md)）：

```java
new SwerveModuleVelocity(2.0, Rotation2d.fromDegrees(90))   // 朝左，每秒 2 公尺
```

`Drivetrain` 算出四個模組各自的 `SwerveModuleVelocity`，交給每個模組的 `setTarget()`。模組自己決定怎麼控制馬達。

### 為什麼不是 Subsystem

[第 5 章](../05-commands/subsystem.md) 說過，Subsystem 是「一個可以被 Command 佔用的機構」。四個模組永遠是一起動的，不會有一個 Command 只想控制左前的模組，所以**整個底盤才是一個 Subsystem**（`Drivetrain`），`SwerveModule` 只是一個普通的類別，由 `Drivetrain` 建立和呼叫。

### 驅動：TalonFX 的速度控制

和[第 8 章 Shooter 的 CTRE 版本](../08-control/feedforward.md)一樣，用 `VelocityVoltage` 讓 TalonFX 自己跑 Feedforward + PID。差別是這裡設定了 `SensorToMechanismRatio`，所以目標值是**輪子**每秒轉幾圈：

```text
目標速度 2.0 m/s ÷ 輪子周長 0.319 m = 每秒 6.27 圈
```

### 轉向：在 Systemcore 上跑 PID

轉向就是「轉到指定角度」，和[第 8 章的 `TurnToAngle`](../08-control/pid.md) 是同一件事：量到的是 CANcoder 的角度，目標是 `target.angle`，PID 算出要給多少電壓。

```java
double steerVolts = steerController.calculate(目前角度, 目標角度);
steerMotor.setVoltage(steerVolts);
```

這一版的 PID 在 **Systemcore** 上每 20 ms 算一次，直接讀 CANcoder，最容易理解。[第 14 章](../14-swerve-tuning/onboard-pid.md) 會把它搬到 SparkMax 上，每 1 ms 算一次。

### 角度是一個圓

輪子從 170 度要轉到 −170 度，最近的路是**再轉 20 度**，不是倒轉 340 度。一般的 PID 只會算 −170 − 170 = −340 度的誤差。

**`enableContinuousInput(-Math.PI, Math.PI)`** 告訴 PID：−π 和 π 是同一個點，計算誤差時永遠走比較近的那一邊。

## 範例

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
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import first.robot.Constants.ModuleConstants;
import first.robot.Constants.SwerveConstants;
import org.wpilib.hardware.bus.CANPort;
import org.wpilib.math.controller.PIDController;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.SwerveModuleVelocity;

/** 一個 Swerve 模組：TalonFX 驅動輪子，SparkMax 轉向，CANcoder 量輪子指向的角度。 */
public class SwerveModule {
  private final TalonFX driveMotor;
  private final SparkMax steerMotor;
  private final CANcoder encoder;

  private final VelocityVoltage velocityRequest = new VelocityVoltage(0);
  private final PIDController steerController =
      new PIDController(SwerveConstants.STEER_KP, 0.0, SwerveConstants.STEER_KD);

  public SwerveModule(ModuleConstants constants) {
    driveMotor = new TalonFX(constants.driveId(), new CANBus(CANPort.CAN_S0));
    steerMotor = new SparkMax(CANPort.CAN_S0, constants.steerId(), MotorType.kBrushless);
    encoder = new CANcoder(constants.encoderId(), new CANBus(CANPort.CAN_S0));

    configureDriveMotor();
    configureSteerMotor();
    configureEncoder(constants.encoderOffsetRotations());

    // 角度是一個圓：-180 度和 180 度是同一個方向
    steerController.enableContinuousInput(-Math.PI, Math.PI);
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
    driveMotor.getConfigurator().apply(config);
  }

  private void configureSteerMotor() {
    SparkMaxConfig config = new SparkMaxConfig();
    config
        .idleMode(IdleMode.kBrake)
        .smartCurrentLimit(SwerveConstants.STEER_CURRENT_LIMIT)
        .inverted(SwerveConstants.STEER_INVERTED);
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

  // ---------- 控制 ----------

  /** 讓模組轉到指定的角度，並用指定的速度滾動。 */
  public void setTarget(SwerveModuleVelocity target) {
    double steerVolts = steerController.calculate(getAngle().getRadians(), target.angle.getRadians());
    steerMotor.setVoltage(steerVolts);

    double wheelRps = target.velocity / SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
    driveMotor.setControl(velocityRequest.withVelocity(wheelRps));
  }

  public void stop() {
    driveMotor.stopMotor();
    steerMotor.stopMotor();
  }
}
```

幾個重點：

- **建構子收一個 `ModuleConstants`**：四個模組用同一個類別，只是設定不同。新增模組不用複製程式碼
- **三個 `configure` 方法**：每個硬體的設定分開寫，比較好讀。CTRE 用 `getConfigurator().apply()`、REV 用 `configure()`，和[第 6 章](../06-motors-sensors/motor-config.md)一樣
- **`SensorDirection = CounterClockwise_Positive`**：從上往下看，輪子逆時針轉時角度增加，和 WPILib 的慣例一致（[第 6 章](../06-motors-sensors/gyro.md#角度的方向)）
- **`AbsoluteSensorDiscontinuityPoint = 0.5`**：讀值的範圍是 −0.5～0.5 圈。剛好對應 `Rotation2d` 的 −180～180 度
- **`STEER_KP = 6.0`**：誤差 1 弧度（約 57 度）時給 6 伏特。這是起點，[第 14 章](../14-swerve-tuning/tuning.md) 會調整

!!! info "這一版還缺什麼"
    這一版的轉向可能會轉到 180 度，而且輪子還沒轉到位就全速滾動。它可以動，但不夠好。[第 14 章](../14-swerve-tuning/optimize.md) 會一一改進。

## 常見錯誤

!!! bug "常見錯誤：轉向的 PID 沒有開 Continuous input"
    輪子從 170 度要轉到 −170 度時，會繞一大圈倒轉回去，看起來像是輪子「抽搐」一下。檢查有沒有呼叫 `enableContinuousInput(-Math.PI, Math.PI)`。

!!! bug "常見錯誤：PID 的單位和 `getAngle()` 不一致"
    `calculate()` 的兩個參數單位要一樣。`enableContinuousInput` 用的是弧度，`calculate` 就要傳 `getRadians()`，不能一個傳度、一個傳弧度。

!!! bug "常見錯誤：忘了設 `SensorToMechanismRatio`"
    沒有設的話，TalonFX 的速度單位是**馬達**的每秒轉數。速度目標用輪子的每秒轉數去算，機器人只會跑到預期速度的 1/6.75。

## 小練習

1. 目標速度是 3 m/s 時，`velocityRequest.withVelocity(...)` 的值是多少？
2. 不呼叫 `enableContinuousInput`，目前角度 −170 度、目標角度 170 度，PID 看到的誤差是幾度？呼叫之後呢？
3. 在 `SwerveModule` 加一個方法 `public double getDriveCurrentAmps()`，回傳驅動馬達的定子電流（提示：`getStatorCurrent()`）。

??? question "第 1 題參考答案"
    3 ÷ 0.319 ≈ **9.4**（輪子每秒轉 9.4 圈）。因為設了 `SensorToMechanismRatio`，這就是輪子的轉速，TalonFX 會自己乘上齒輪比換算成馬達的轉速。

??? question "第 2 題參考答案"
    沒有開的時候，誤差是 170 − (−170) = **340 度**，輪子會往反方向轉一大圈。開了之後，PID 知道 −180 和 180 是同一點，誤差是 **−20 度**，只要再轉 20 度就到了。

??? question "第 3 題參考答案"
    ```java
    /** 驅動馬達的定子電流，單位：安培。 */
    public double getDriveCurrentAmps() {
      return driveMotor.getStatorCurrent().getValueAsDouble();
    }
    ```
