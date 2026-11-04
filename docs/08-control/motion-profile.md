# Motion Profile

--8<-- "alpha-warning.md"

## 這頁學什麼

- 為什麼位置控制需要 Motion Profile
- Trapezoid profile（梯形速度曲線）
- 用 REV 的 MAXMotion 或 CTRE 的 Motion Magic 讓手臂平順地轉到指定角度
- 手臂的重力補償

## 觀念

### 直接用 PID 轉到目標的問題

用 PID 讓手臂從收起（-30 度）轉到 90 度：一開始誤差有 120 度，P 會給出**非常大**的輸出，手臂猛然加速，快到的時候又要急煞。結果是：

- 機構承受很大的衝擊，皮帶可能跳齒、手臂上的東西可能被甩出去
- 很容易衝過頭、震盪
- 為了不衝過頭只好把 P 調小，結果到了目標附近又推不動

### Motion Profile：先規劃好路線

**Motion Profile（運動曲線）** 的做法是：不要一下子把目標設成 90 度，而是**事先規劃**好每個時間點手臂應該在哪裡、速度多少，再讓 PID 一路跟著這條曲線走。

最常用的是 **Trapezoid profile（梯形速度曲線）**，速度對時間的圖形是一個梯形：

```text
速度
 ▲
 │      ┌───────────┐  ← 巡航速度 (Cruise velocity)
 │     ╱             ╲
 │    ╱               ╲
 │   ╱                 ╲
 └──┴───────────────────┴──► 時間
    加速              減速
   （最大加速度）    （最大加速度）
```

只需要兩個參數：

- **Cruise velocity（巡航速度）**：最快能轉多快
- **Max acceleration（最大加速度）**：加速和減速有多快

### 交給馬達控制器

REV 和 CTRE 的控制器都內建了 Trapezoid profile，送出目標位置後，控制器會自己規劃曲線並跟著走：

| | REV | CTRE |
| --- | --- | --- |
| 名稱 | **MAXMotion** | **Motion Magic** |
| 參數設定 | `config.closedLoop.maxMotion` | `config.MotionMagic` |
| 送出目標 | `ControlType.kMAXMotionPositionControl` | `MotionMagicVoltage` 請求 |
| 速度單位 | 感測器的 RPM | 感測器的每秒轉數 |

WPILib 也有在 Systemcore 上計算的 `TrapezoidProfile` 和 `ProfiledPIDController`，用在控制器做不到的情況（[延伸學習](further.md)）。

### 手臂的重力補償

手臂需要的「撐住重力」的電壓和角度有關：`kG × cos(角度)`。WPILib 的 **`ArmFeedforward`** 幫你算：

```java
ArmFeedforward feedforward = new ArmFeedforward(kS, kG, kV);
double volts = feedforward.calculate(角度（弧度，水平為 0）, 速度);
```

這裡只用它來算重力的部分（速度設為 0），速度相關的 Feedforward 交給控制器的 kV。算出來的電壓每次迴圈都送給控制器，當作 **Arbitrary feedforward（額外的前饋）**，加在控制器自己算的輸出上。

### 角度和編碼器讀值的換算

[第 6 章](../06-motors-sensors/encoders.md) 把絕對式編碼器的零點設在**收起的位置**，而角度是「水平為 0 度」：

```text
編碼器圈數 = (角度 - STOWED_ANGLE_DEGREES) ÷ 360
```

控制器用的是編碼器的單位，所以送出目標之前要先換算。

## 範例

在 `Constants.java` 的 `ArmConstants` 加上（數值是起始值，要實際調整）：

=== "REV"

    ```java
    public static final double MAX_ANGLE_DEGREES = 110.0;
    public static final double SCORE_ANGLE_DEGREES = 90.0;
    public static final double TOLERANCE_DEGREES = 3.0;
    public static final double KP = 1.0;
    public static final double KD = 0.0;
    public static final double KG = 0.3;                 // 伏特，水平時撐住手臂所需的電壓
    public static final double KV = 0.12;                // 伏特 / 手臂 RPM
    public static final double CRUISE_VELOCITY = 30.0;   // 手臂 RPM（每秒 180 度）
    public static final double MAX_ACCELERATION = 60.0;  // 手臂 RPM / 秒
    ```

=== "CTRE"

    ```java
    public static final double MAX_ANGLE_DEGREES = 110.0;
    public static final double SCORE_ANGLE_DEGREES = 90.0;
    public static final double TOLERANCE_DEGREES = 3.0;
    public static final double KP = 20.0;                // 伏特 / 手臂圈數
    public static final double KD = 0.0;
    public static final double KG = 0.3;                 // 伏特，水平時撐住手臂所需的電壓
    public static final double KV = 7.0;                 // 伏特 / 手臂每秒轉數
    public static final double CRUISE_VELOCITY = 0.5;    // 手臂每秒轉數（每秒 180 度）
    public static final double MAX_ACCELERATION = 1.0;   // 手臂每秒轉數 / 秒
    ```

完整的 Arm：

=== "REV"

    ```java title="src/main/java/first/robot/subsystems/Arm.java"
    package first.robot.subsystems;

    import com.revrobotics.AbsoluteEncoder;
    import com.revrobotics.PersistMode;
    import com.revrobotics.ResetMode;
    import com.revrobotics.spark.ClosedLoopSlot;
    import com.revrobotics.spark.FeedbackSensor;
    import com.revrobotics.spark.SparkClosedLoopController;
    import com.revrobotics.spark.SparkClosedLoopController.ArbFFUnits;
    import com.revrobotics.spark.SparkLowLevel.ControlType;
    import com.revrobotics.spark.SparkLowLevel.MotorType;
    import com.revrobotics.spark.SparkMax;
    import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
    import com.revrobotics.spark.config.SparkMaxConfig;
    import first.robot.Constants.ArmConstants;
    import java.util.function.DoubleSupplier;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.hardware.discrete.DigitalInput;
    import org.wpilib.math.controller.ArmFeedforward;
    import org.wpilib.telemetry.Telemetry;

    public class Arm extends SubsystemBase {
      private final SparkMax motor =
          new SparkMax(CANPort.CAN_S2, ArmConstants.MOTOR_ID, MotorType.kBrushless);
      private final AbsoluteEncoder absoluteEncoder = motor.getAbsoluteEncoder();
      private final SparkClosedLoopController controller = motor.getClosedLoopController();
      private final DigitalInput bottomLimit = new DigitalInput(ArmConstants.BOTTOM_LIMIT_CHANNEL);
      private final ArmFeedforward gravity = new ArmFeedforward(0.0, ArmConstants.KG, 0.0);

      public Arm() {
        SparkMaxConfig config = new SparkMaxConfig();
        config.idleMode(IdleMode.kBrake).smartCurrentLimit(ArmConstants.CURRENT_LIMIT);
        config.absoluteEncoder.zeroOffset(ArmConstants.ABSOLUTE_OFFSET_ROTATIONS);
        config.closedLoop
            .feedbackSensor(FeedbackSensor.kAbsoluteEncoder)
            .p(ArmConstants.KP)
            .d(ArmConstants.KD);
        config.closedLoop.feedForward.kV(ArmConstants.KV);
        config.closedLoop.maxMotion
            .cruiseVelocity(ArmConstants.CRUISE_VELOCITY)
            .maxAcceleration(ArmConstants.MAX_ACCELERATION);
        motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
      }

      /** 手臂角度，單位：度，水平為 0。Through Bore Encoder 裝在手臂的轉軸上，零點設在收起的位置。 */
      public double getAngleDegrees() {
        return absoluteEncoder.getPosition().get(0.0) * 360.0 + ArmConstants.STOWED_ANGLE_DEGREES;
      }

      public boolean isAtBottom() {
        return !bottomLimit.get();
      }

      public boolean isAtAngle(double degrees) {
        return Math.abs(getAngleDegrees() - degrees) < ArmConstants.TOLERANCE_DEGREES;
      }

      /** 角度（水平為 0）換算成編碼器的圈數（收起為 0）。 */
      private static double toEncoderRotations(double degrees) {
        return (degrees - ArmConstants.STOWED_ANGLE_DEGREES) / 360.0;
      }

      /** 撐住手臂需要的電壓，跟著目前的角度變化。 */
      private double gravityVolts() {
        return gravity.calculate(Math.toRadians(getAngleDegrees()), 0.0);
      }

      /** 用 MAXMotion 轉到指定角度，並一直停在那裡。 */
      public Command goToAngleCommand(double degrees) {
        double target = Math.clamp(degrees, ArmConstants.STOWED_ANGLE_DEGREES, ArmConstants.MAX_ANGLE_DEGREES);
        return run(() -> controller.setSetpoint(
                toEncoderRotations(target),
                ControlType.kMAXMotionPositionControl,
                ClosedLoopSlot.kSlot0,
                gravityVolts(),
                ArbFFUnits.kVoltage))
            .withName("ArmToAngle");
      }

      public void setThrottle(double throttle) {
        if (isAtBottom() && throttle < 0) {
          throttle = 0;
        }
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
        Telemetry.log("Arm/AtBottom", isAtBottom());
      }
    }
    ```

    `feedbackSensor(FeedbackSensor.kAbsoluteEncoder)` 讓控制器用絕對式編碼器當回授，所以目標值和 MAXMotion 的速度都是**手臂**的單位（編碼器裝在手臂的轉軸上）。

=== "CTRE"

    ```java title="src/main/java/first/robot/subsystems/Arm.java"
    package first.robot.subsystems;

    import com.ctre.phoenix6.CANBus;
    import com.ctre.phoenix6.configs.CANcoderConfiguration;
    import com.ctre.phoenix6.configs.TalonFXConfiguration;
    import com.ctre.phoenix6.controls.MotionMagicVoltage;
    import com.ctre.phoenix6.hardware.CANcoder;
    import com.ctre.phoenix6.hardware.TalonFX;
    import com.ctre.phoenix6.signals.FeedbackSensorSourceValue;
    import com.ctre.phoenix6.signals.NeutralModeValue;
    import first.robot.Constants.ArmConstants;
    import java.util.function.DoubleSupplier;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.hardware.discrete.DigitalInput;
    import org.wpilib.math.controller.ArmFeedforward;
    import org.wpilib.telemetry.Telemetry;

    public class Arm extends SubsystemBase {
      private final CANBus bus = new CANBus(CANPort.CAN_S2);
      private final TalonFX motor = new TalonFX(ArmConstants.MOTOR_ID, bus);
      private final CANcoder cancoder = new CANcoder(ArmConstants.CANCODER_ID, bus);
      private final DigitalInput bottomLimit = new DigitalInput(ArmConstants.BOTTOM_LIMIT_CHANNEL);
      private final ArmFeedforward gravity = new ArmFeedforward(0.0, ArmConstants.KG, 0.0);
      private final MotionMagicVoltage motionRequest = new MotionMagicVoltage(0);

      public Arm() {
        CANcoderConfiguration encoderConfig = new CANcoderConfiguration();
        encoderConfig.MagnetSensor.MagnetOffset = ArmConstants.ABSOLUTE_OFFSET_ROTATIONS;
        cancoder.getConfigurator().apply(encoderConfig);

        TalonFXConfiguration motorConfig = new TalonFXConfiguration();
        motorConfig.MotorOutput.NeutralMode = NeutralModeValue.Brake;
        motorConfig.CurrentLimits.StatorCurrentLimit = ArmConstants.CURRENT_LIMIT;
        motorConfig.CurrentLimits.StatorCurrentLimitEnable = true;
        motorConfig.Feedback.FeedbackRemoteSensorID = ArmConstants.CANCODER_ID;
        motorConfig.Feedback.FeedbackSensorSource = FeedbackSensorSourceValue.RemoteCANcoder;
        motorConfig.Slot0.kP = ArmConstants.KP;
        motorConfig.Slot0.kD = ArmConstants.KD;
        motorConfig.Slot0.kV = ArmConstants.KV;
        motorConfig.MotionMagic.MotionMagicCruiseVelocity = ArmConstants.CRUISE_VELOCITY;
        motorConfig.MotionMagic.MotionMagicAcceleration = ArmConstants.MAX_ACCELERATION;
        motor.getConfigurator().apply(motorConfig);
      }

      /** 手臂角度，單位：度，水平為 0。CANcoder 裝在手臂的轉軸上，零點設在收起的位置。 */
      public double getAngleDegrees() {
        return cancoder.getAbsolutePosition().getValueAsDouble() * 360.0 + ArmConstants.STOWED_ANGLE_DEGREES;
      }

      public boolean isAtBottom() {
        return !bottomLimit.get();
      }

      public boolean isAtAngle(double degrees) {
        return Math.abs(getAngleDegrees() - degrees) < ArmConstants.TOLERANCE_DEGREES;
      }

      /** 角度（水平為 0）換算成 CANcoder 的圈數（收起為 0）。 */
      private static double toEncoderRotations(double degrees) {
        return (degrees - ArmConstants.STOWED_ANGLE_DEGREES) / 360.0;
      }

      /** 撐住手臂需要的電壓，跟著目前的角度變化。 */
      private double gravityVolts() {
        return gravity.calculate(Math.toRadians(getAngleDegrees()), 0.0);
      }

      /** 用 Motion Magic 轉到指定角度，並一直停在那裡。 */
      public Command goToAngleCommand(double degrees) {
        double target = Math.clamp(degrees, ArmConstants.STOWED_ANGLE_DEGREES, ArmConstants.MAX_ANGLE_DEGREES);
        return run(() -> motor.setControl(
                motionRequest.withPosition(toEncoderRotations(target)).withFeedForward(gravityVolts())))
            .withName("ArmToAngle");
      }

      public void setThrottle(double throttle) {
        if (isAtBottom() && throttle < 0) {
          throttle = 0;
        }
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
        Telemetry.log("Arm/AtBottom", isAtBottom());
      }
    }
    ```

    `FeedbackSensorSource = RemoteCANcoder` 讓 Talon FX 用 CANcoder 當回授，目標值和 Motion Magic 的速度都是**手臂**的單位。（`FusedCANcoder` 效果更好，但需要 Phoenix Pro 授權。）

重點：

- `goToAngleCommand` 用 `run()`：**每次迴圈都重新送出目標**，因為重力補償的電壓會隨著角度改變。Command 不會自己結束，手臂會一直停在目標角度
- `Math.clamp(...)`：目標角度限制在收起和最高點之間，這是**軟體限位**，避免程式要求手臂轉到機構做不到的地方

在 `RobotContainer` 綁定按鈕：

```java
driver.faceUp().onTrue(arm.goToAngleCommand(ArmConstants.SCORE_ANGLE_DEGREES));
driver.faceLeft().onTrue(arm.goToAngleCommand(ArmConstants.STOWED_ANGLE_DEGREES));
```

按一下 `faceUp` 手臂轉到 90 度並停在那裡；按 `faceLeft` 收回。

注意 `goToAngleCommand` 不會自己結束，所以手臂到位之後，[第 6 章](../06-motors-sensors/encoders.md#arm用絕對式編碼器讀角度)設定的手動控制 default command **不會**自動回來（default command 只在沒有其他 Command 使用 Arm 時才執行）。想要推搖桿就切回手動，可以加一個 Trigger：

```java
new Trigger(() -> Math.abs(driver.getRightY()) > 0.2)
    .onTrue(arm.manualCommand(() -> -driver.getRightY() * 0.3));
```

### 調整的順序

1. **先調 kG**：其他都設 0，把手臂擺到水平，慢慢加大 kG，直到手臂**剛好能停在水平不掉下來**
2. **設定巡航速度和加速度**：先設小一點（例如每秒 90 度），確認動作平順
3. **加 kV**：讓手臂能跟上規劃的速度
4. **加 kP**：修正剩下的誤差，讓手臂準確停在目標
5. 慢慢把巡航速度和加速度調高到需要的程度

## 常見錯誤

!!! bug "常見錯誤：角度的零點不是水平"
    `ArmFeedforward` 假設 0 度是水平。如果 `STOWED_ANGLE_DEGREES` 量錯，重力補償在某些角度會太大、某些角度太小，手臂會往上飄或往下掉。

!!! bug "常見錯誤：目標用了度，控制器要的是圈"
    直接送 `setSetpoint(90, ...)`，控制器會以為目標是 90 **圈**，手臂會全速轉到撞到擋塊。送出前一定要換算成編碼器的單位。

!!! bug "常見錯誤：用 `runOnce` 送出目標"
    `runOnce` 只送一次目標。控制器會繼續跟著 profile 走，但重力補償的電壓不會更新，手臂到不同角度時就撐不住。

## 小練習

1. 照著調整順序調出你們手臂的參數，記錄每個參數的值。
2. 寫一個 Trigger：手臂到達 `SCORE_ANGLE_DEGREES` 時印出「手臂到位」。（提示：`arm.isAtAngle(...)`）
3. 修改 `ScoringCommands.shoot()`：先把手臂轉到發射角度，等手臂到位、Shooter 轉速到了，再送料。（提示：`Commands.waitUntil()` 搭配 `deadlineFor()`）

??? question "第 2 題參考答案"
    ```java
    new Trigger(() -> arm.isAtAngle(ArmConstants.SCORE_ANGLE_DEGREES))
        .onTrue(Commands.print("手臂到位"));
    ```

    `onTrue` 只在條件**從 false 變成 true** 的那一刻觸發，所以手臂停在目標角度時只會印一次。

??? question "第 3 題參考答案"
    在 `ScoringCommands` 加一個新的方法，原本的 `shoot()` 保留給不需要動手臂的情況：

    ```java title="src/main/java/first/robot/commands/ScoringCommands.java"
    package first.robot.commands;

    import first.robot.Constants.ArmConstants;
    import first.robot.Constants.ShooterConstants;
    import first.robot.subsystems.Arm;
    import first.robot.subsystems.Intake;
    import first.robot.subsystems.Shooter;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.Commands;

    public final class ScoringCommands {
      public static Command shoot(Intake intake, Shooter shooter) {
        return Commands.waitUntil(shooter::isAtSpeed)
            .withTimeout(ShooterConstants.SPIN_UP_TIMEOUT_SECONDS)
            .andThen(intake.feedCommand().withTimeout(ShooterConstants.FEED_SECONDS))
            .deadlineFor(shooter.spinCommand())
            .withName("Shoot");
      }

      /** 手臂轉到發射角度、Shooter 轉到夠快，兩個都好了才送料。 */
      public static Command shootWithArm(Intake intake, Shooter shooter, Arm arm) {
        return Commands.waitUntil(
                () -> arm.isAtAngle(ArmConstants.SCORE_ANGLE_DEGREES) && shooter.isAtSpeed())
            .withTimeout(ShooterConstants.SPIN_UP_TIMEOUT_SECONDS)
            .andThen(intake.feedCommand().withTimeout(ShooterConstants.FEED_SECONDS))
            .deadlineFor(shooter.spinCommand(), arm.goToAngleCommand(ArmConstants.SCORE_ANGLE_DEGREES))
            .withName("ShootWithArm");
      }

      private ScoringCommands() {}
    }
    ```

    `deadlineFor` 可以同時放好幾個 Command：等待和送料的期間，Shooter 一直轉、手臂一直停在發射角度。
