# 編碼器

--8<-- "alpha-warning.md"

## 這頁學什麼

- 讀取馬達內建編碼器的位置和速度
- 把「馬達轉了幾圈」換算成機構的角度、距離
- 用轉速判斷 Shooter 準備好了沒
- 用絕對式編碼器讀取手臂角度

## 觀念

### 讀取內建編碼器

無刷馬達都有內建編碼器，不用另外接線，透過馬達控制器就能讀到：

=== "REV"

    ```java
    private final RelativeEncoder encoder = motor.getEncoder();

    double rotations = encoder.getPosition().get(0.0);   // 圈數
    double rpm = encoder.getVelocity().get(0.0);         // 每分鐘轉數 (RPM)
    ```

    REVLib 2027 的讀值會包在 `Signal` 裡：`get(0.0)` 取出數值，如果資料無效（例如控制器斷線）就回傳 `0.0`。

=== "CTRE"

    ```java
    double rotations = motor.getPosition().getValueAsDouble();   // 圈數
    double rps = motor.getVelocity().getValueAsDouble();         // 每秒轉數
    ```

    Phoenix 6 的讀值是 `StatusSignal`，`getValueAsDouble()` 取出數值。

位置可以歸零：REV 用 `encoder.setPosition(0)`，CTRE 用 `motor.setPosition(0)`。

### 單位換算

編碼器讀到的是**馬達**轉了幾圈，但我們關心的是**機構**：手臂轉了幾度、底盤走了幾公尺、Shooter 的飛輪轉多快。中間差了齒輪比和輪子大小。

假設底盤的齒輪比是 6.75:1（馬達轉 6.75 圈，輪子轉 1 圈），輪子直徑 4 英寸：

```java
// 放在 Constants 裡
public static final double DRIVE_GEAR_RATIO = 6.75;
public static final double WHEEL_DIAMETER_METERS = 0.1016;

// 馬達圈數 → 輪子圈數 → 公尺
double meters = motorRotations / DRIVE_GEAR_RATIO * Math.PI * WHEEL_DIAMETER_METERS;
```

這就是 [變數與型別](../01-java/variables.md#範例) 範例算過的東西。**把換算寫成方法**，Subsystem 對外只提供換算好的值，例如 `getDistanceMeters()`、`getAngleDegrees()`，其他程式就不用管齒輪比。

!!! info "2027 新改動：REVLib 移除了換算係數"
    2026 以前可以在 REV 的設定裡用 `positionConversionFactor()` 讓控制器直接回傳換算好的單位。REVLib 2027 移除了這個功能，讀到的一律是馬達的圈數和 RPM，換算要在自己的程式裡做。

    CTRE 仍然可以用 `config.Feedback.SensorToMechanismRatio` 設定齒輪比，讓讀值變成機構的圈數。這份教學為了讓兩家寫法一致，統一在程式裡換算。

### Absolute encoder（絕對式編碼器）

內建編碼器是**相對式**的：開機時位置是 0。手臂如果開機時不在固定位置，算出來的角度就全錯了。**絕對式編碼器**裝在機構的轉軸上，隨時知道真正的角度：

=== "REV"

    **Through Bore Encoder** 接在 SPARK MAX 的資料埠上：

    ```java
    private final AbsoluteEncoder absoluteEncoder = motor.getAbsoluteEncoder();

    double rotations = absoluteEncoder.getPosition().get(0.0);   // 0.0 ～ 1.0 圈
    ```

    用設定裡的 `config.absoluteEncoder.zeroOffset(...)` 設定零點。

=== "CTRE"

    **CANcoder** 是獨立的 CAN 裝置：

    ```java
    private final CANcoder cancoder = new CANcoder(40, new CANBus(CANPort.CAN_S2));

    double rotations = cancoder.getAbsolutePosition().getValueAsDouble();   // 預設 -0.5 ～ 0.5 圈
    ```

    用 `CANcoderConfiguration` 的 `MagnetSensor.MagnetOffset` 設定零點。

**零點 (Zero offset)**：編碼器裝上去的位置是隨機的，讀到 0 的地方不一定是你想要的位置。先把 offset 設成 0，把手臂擺到你想當作零點的位置，記下這時候的讀值：

- **REV**：`zeroOffset` 是「要當作 0 的位置」，直接填入這個讀值
- **CTRE**：`MagnetOffset` 是「加到讀值上的數」，要填入這個讀值的**負數**

之後讀值就會以這個位置為 0。

**零點要設在哪裡？** 絕對式編碼器的讀值在轉滿一圈的地方會「跳回去」（例如 REV 從 0.99 圈跳回 0）。所以零點要設在**手臂活動範圍的一端**，通常是**收起的位置**（靠在擋塊上），讓手臂從收起轉到最高點都不會經過跳躍點。程式裡再加上收起時的角度（`STOWED_ANGLE_DEGREES`），換算成「水平為 0 度」的角度，[第 8 章](../08-control/feedforward.md) 計算重力補償時會用到。

## 範例

### Shooter：轉速夠快才發射

第 5 章的發射是「轉 1 秒再送料」，但電池電量不同，1 秒後的轉速也不同。有了編碼器，就可以改成「**轉速到了再送料**」。

在 `Constants.java` 的 `ShooterConstants` 加上：

```java
public static final double MIN_SHOOT_RPM = 4000;
public static final double SPIN_UP_TIMEOUT_SECONDS = 2.0;
```

=== "REV"

    ```java title="src/main/java/first/robot/subsystems/Shooter.java"
    package first.robot.subsystems;

    import com.revrobotics.PersistMode;
    import com.revrobotics.RelativeEncoder;
    import com.revrobotics.ResetMode;
    import com.revrobotics.spark.SparkFlex;
    import com.revrobotics.spark.SparkLowLevel.MotorType;
    import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
    import com.revrobotics.spark.config.SparkFlexConfig;
    import first.robot.Constants.ShooterConstants;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.telemetry.Telemetry;

    public class Shooter extends SubsystemBase {
      private final SparkFlex motor =
          new SparkFlex(CANPort.CAN_S1, ShooterConstants.MOTOR_ID, MotorType.kBrushless);
      private final RelativeEncoder encoder = motor.getEncoder();

      public Shooter() {
        SparkFlexConfig config = new SparkFlexConfig();
        config
            .inverted(false)
            .idleMode(IdleMode.kCoast)
            .smartCurrentLimit(ShooterConstants.CURRENT_LIMIT);
        motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
      }

      /** 飛輪轉速，單位 RPM（飛輪直接裝在馬達上，齒輪比 1:1）。 */
      public double getRpm() {
        return encoder.getVelocity().get(0.0);
      }

      public boolean isAtSpeed() {
        return getRpm() >= ShooterConstants.MIN_SHOOT_RPM;
      }

      public Command spinCommand() {
        return startEnd(() -> motor.setThrottle(ShooterConstants.SHOOT_THROTTLE), motor::stopMotor)
            .withName("Spin");
      }

      @Override
      public void periodic() {
        Telemetry.log("Shooter/RPM", getRpm());
        Telemetry.log("Shooter/AtSpeed", isAtSpeed());
      }
    }
    ```

=== "CTRE"

    ```java title="src/main/java/first/robot/subsystems/Shooter.java"
    package first.robot.subsystems;

    import com.ctre.phoenix6.CANBus;
    import com.ctre.phoenix6.configs.TalonFXConfiguration;
    import com.ctre.phoenix6.hardware.TalonFX;
    import com.ctre.phoenix6.signals.InvertedValue;
    import com.ctre.phoenix6.signals.NeutralModeValue;
    import first.robot.Constants.ShooterConstants;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.telemetry.Telemetry;

    public class Shooter extends SubsystemBase {
      private final TalonFX motor = new TalonFX(ShooterConstants.MOTOR_ID, new CANBus(CANPort.CAN_S1));

      public Shooter() {
        TalonFXConfiguration config = new TalonFXConfiguration();
        config.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;
        config.MotorOutput.NeutralMode = NeutralModeValue.Coast;
        config.CurrentLimits.StatorCurrentLimit = ShooterConstants.CURRENT_LIMIT;
        config.CurrentLimits.StatorCurrentLimitEnable = true;
        motor.getConfigurator().apply(config);
      }

      /** 飛輪轉速，單位 RPM（飛輪直接裝在馬達上，齒輪比 1:1）。 */
      public double getRpm() {
        // Phoenix 6 的速度是每秒轉數，乘以 60 換成 RPM
        return motor.getVelocity().getValueAsDouble() * 60.0;
      }

      public boolean isAtSpeed() {
        return getRpm() >= ShooterConstants.MIN_SHOOT_RPM;
      }

      public Command spinCommand() {
        return startEnd(() -> motor.setThrottle(ShooterConstants.SHOOT_THROTTLE), motor::stopMotor)
            .withName("Spin");
      }

      @Override
      public void periodic() {
        Telemetry.log("Shooter/RPM", getRpm());
        Telemetry.log("Shooter/AtSpeed", isAtSpeed());
      }
    }
    ```

把 `ScoringCommands.shoot()` 的「等 1 秒」改成「等到轉速夠快」，並加上時間限制，避免轉速一直上不去時卡住：

```java title="src/main/java/first/robot/commands/ScoringCommands.java"
package first.robot.commands;

import first.robot.Constants.ShooterConstants;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;

public final class ScoringCommands {
  /** Shooter 轉到夠快（最多等 2 秒）後，Intake 送料 0.5 秒。 */
  public static Command shoot(Intake intake, Shooter shooter) {
    return Commands.waitUntil(shooter::isAtSpeed)
        .withTimeout(ShooterConstants.SPIN_UP_TIMEOUT_SECONDS)
        .andThen(intake.feedCommand().withTimeout(ShooterConstants.FEED_SECONDS))
        .deadlineFor(shooter.spinCommand())
        .withName("Shoot");
  }

  private ScoringCommands() {}
}
```

### Arm：用絕對式編碼器讀角度

新增一個手臂 Subsystem，先只讀角度和手動控制，[第 8 章](../08-control/index.md) 再讓它自動轉到指定角度。在 `Constants.java` 加上：

```java
public static final class ArmConstants {
  public static final int MOTOR_ID = 30;
  public static final int CANCODER_ID = 40;      // CTRE 才需要
  public static final int CURRENT_LIMIT = 40;
  public static final double ABSOLUTE_OFFSET_ROTATIONS = 0.0;   // 實際測量後填入
  public static final double STOWED_ANGLE_DEGREES = -30.0;      // 收起時的角度（水平為 0）
  public static final int BOTTOM_LIMIT_CHANNEL = 1;             // 下一頁用到
}
```

=== "REV"

    ```java title="src/main/java/first/robot/subsystems/Arm.java"
    package first.robot.subsystems;

    import com.revrobotics.AbsoluteEncoder;
    import com.revrobotics.PersistMode;
    import com.revrobotics.ResetMode;
    import com.revrobotics.spark.SparkLowLevel.MotorType;
    import com.revrobotics.spark.SparkMax;
    import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
    import com.revrobotics.spark.config.SparkMaxConfig;
    import first.robot.Constants.ArmConstants;
    import java.util.function.DoubleSupplier;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.telemetry.Telemetry;

    public class Arm extends SubsystemBase {
      private final SparkMax motor =
          new SparkMax(CANPort.CAN_S2, ArmConstants.MOTOR_ID, MotorType.kBrushless);
      private final AbsoluteEncoder absoluteEncoder = motor.getAbsoluteEncoder();

      public Arm() {
        SparkMaxConfig config = new SparkMaxConfig();
        config.idleMode(IdleMode.kBrake).smartCurrentLimit(ArmConstants.CURRENT_LIMIT);
        config.absoluteEncoder.zeroOffset(ArmConstants.ABSOLUTE_OFFSET_ROTATIONS);
        motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
      }

      /** 手臂角度，單位：度，水平為 0。Through Bore Encoder 裝在手臂的轉軸上，零點設在收起的位置。 */
      public double getAngleDegrees() {
        return absoluteEncoder.getPosition().get(0.0) * 360.0 + ArmConstants.STOWED_ANGLE_DEGREES;
      }

      public void setThrottle(double throttle) {
        motor.setThrottle(throttle);
      }

      public void stop() {
        motor.stopMotor();
      }

      public Command manualCommand(DoubleSupplier throttle) {
        return runEnd(() -> setThrottle(throttle.getAsDouble()), this::stop).withName("ArmManual");
      }

      @Override
      public void periodic() {
        Telemetry.log("Arm/AngleDegrees", getAngleDegrees());
      }
    }
    ```

=== "CTRE"

    ```java title="src/main/java/first/robot/subsystems/Arm.java"
    package first.robot.subsystems;

    import com.ctre.phoenix6.CANBus;
    import com.ctre.phoenix6.configs.CANcoderConfiguration;
    import com.ctre.phoenix6.configs.TalonFXConfiguration;
    import com.ctre.phoenix6.hardware.CANcoder;
    import com.ctre.phoenix6.hardware.TalonFX;
    import com.ctre.phoenix6.signals.NeutralModeValue;
    import first.robot.Constants.ArmConstants;
    import java.util.function.DoubleSupplier;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.telemetry.Telemetry;

    public class Arm extends SubsystemBase {
      private final CANBus bus = new CANBus(CANPort.CAN_S2);
      private final TalonFX motor = new TalonFX(ArmConstants.MOTOR_ID, bus);
      private final CANcoder cancoder = new CANcoder(ArmConstants.CANCODER_ID, bus);

      public Arm() {
        TalonFXConfiguration motorConfig = new TalonFXConfiguration();
        motorConfig.MotorOutput.NeutralMode = NeutralModeValue.Brake;
        motorConfig.CurrentLimits.StatorCurrentLimit = ArmConstants.CURRENT_LIMIT;
        motorConfig.CurrentLimits.StatorCurrentLimitEnable = true;
        motor.getConfigurator().apply(motorConfig);

        CANcoderConfiguration encoderConfig = new CANcoderConfiguration();
        encoderConfig.MagnetSensor.MagnetOffset = ArmConstants.ABSOLUTE_OFFSET_ROTATIONS;
        cancoder.getConfigurator().apply(encoderConfig);
      }

      /** 手臂角度，單位：度，水平為 0。CANcoder 裝在手臂的轉軸上，零點設在收起的位置。 */
      public double getAngleDegrees() {
        return cancoder.getAbsolutePosition().getValueAsDouble() * 360.0 + ArmConstants.STOWED_ANGLE_DEGREES;
      }

      public void setThrottle(double throttle) {
        motor.setThrottle(throttle);
      }

      public void stop() {
        motor.stopMotor();
      }

      public Command manualCommand(DoubleSupplier throttle) {
        return runEnd(() -> setThrottle(throttle.getAsDouble()), this::stop).withName("ArmManual");
      }

      @Override
      public void periodic() {
        Telemetry.log("Arm/AngleDegrees", getAngleDegrees());
      }
    }
    ```

在 `RobotContainer` 建立 `Arm`，並用右搖桿手動控制（限制在 30% 以內）：

```java
private final Arm arm = new Arm();

// 在 configureDefaultCommands() 裡
arm.setDefaultCommand(arm.manualCommand(() -> -driver.getRightY() * 0.3));
```

## 常見錯誤

!!! bug "常見錯誤：齒輪比乘除顛倒"
    齒輪比 6.75:1 代表馬達轉 6.75 圈、機構轉 1 圈，所以「馬達圈數 → 機構圈數」是**除以** 6.75。寫成乘以，算出來的距離會差 45 倍。不確定的時候，用手轉一圈機構，看讀值變化多少來驗證。

!!! bug "常見錯誤：REV 和 CTRE 的速度單位搞混"
    REV 是 RPM，CTRE 是每秒轉數。從 CTRE 換到 REV（或反過來）時，忘了乘除 60，轉速判斷就會完全錯。

!!! bug "常見錯誤：絕對式編碼器的讀值跨過跳躍點"
    REV Through Bore 的讀值是 0～1 圈，手臂如果轉過「1 圈 → 0 圈」的位置，角度會從 359 度突然跳到 0 度，控制程式會以為手臂瞬間轉了一整圈。把零點設在收起的位置，讓手臂的整個活動範圍都不會跨過這個跳躍點。

## 小練習

1. 在 Elastic 上觀察 `Shooter/RPM`，從按下發射到 `Shooter/AtSpeed` 變成 `true` 大約要多久？
2. 先把 `ABSOLUTE_OFFSET_ROTATIONS` 設成 0，把手臂放在收起的位置，記下這時候的讀值，算出正確的 `ABSOLUTE_OFFSET_ROTATIONS`（注意 REV 和 CTRE 的正負號不同，而且 offset 的單位是圈，不是度）。再用量角器量出收起時手臂和水平的夾角，填入 `STOWED_ANGLE_DEGREES`，確認手臂水平時 `Arm/AngleDegrees` 接近 0。
3. 在 `Intake` 加上 `getRpm()`，並用 Telemetry 顯示。Intake 卡住時轉速會有什麼變化？可以拿來做什麼？
