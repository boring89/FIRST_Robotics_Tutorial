# 數位感測器

--8<-- "alpha-warning.md"

## 這頁學什麼

- 用 Smart IO 讀取極限開關
- 用極限開關保護手臂，避免轉過頭
- 接在馬達控制器上的極限開關
- 用 Trigger 的 `debounce` 過濾雜訊

## 觀念

### 數位感測器只有兩種狀態

**Limit switch（極限開關）**、**Beam break（光遮斷感測器）** 這類感測器只會回報 `true` 或 `false`。在 Systemcore 上，它們接在 **Smart IO** 連接埠，用 `DigitalInput` 讀取：

```java
private final DigitalInput bottomLimit = new DigitalInput(1);   // Smart IO 1

boolean value = bottomLimit.get();
```

第 5 章的 Intake 已經用 `DigitalInput` 讀過光遮斷感測器了。

### 讀到 `true` 代表什麼？

`get()` 回傳的是**訊號線上的電壓高低**，不是「有沒有被按下」。按下時是 `true` 還是 `false`，取決於感測器的種類和接法：

- 常開 (Normally Open, NO) 或常閉 (Normally Closed, NC) 的開關
- Systemcore 的版本：[alpha 版和 beta 版的 IO 腳位接法不同](../02-hardware/sensors.md#數位感測器)

所以**不要猜，實際測試**：在 Telemetry 顯示 `get()` 的值，按下和放開各看一次，再決定要不要加 `!`。把判斷包成有意義的方法，例如 `isAtBottom()`，其他地方就不用管接法。

### 用極限開關保護機構

手臂轉到底時，如果馬達還在往下推，會撞壞機構或燒掉馬達。在 Subsystem 的 `setThrottle()` 裡檢查極限開關：碰到底部時，就不允許繼續往下。

```java
public void setThrottle(double throttle) {
  if (isAtBottom() && throttle < 0) {
    throttle = 0;   // 已經到底，不能再往下
  }
  motor.setThrottle(throttle);
}
```

注意只擋住「往下」的方向，往上還是可以動，否則手臂碰到開關後就再也離不開了。

因為所有控制手臂的 Command 都會經過 `setThrottle()`，保護只要寫在這一個地方。這就是[封裝](../01-java/classes.md#封裝private-和-public)的好處。

### 接在馬達控制器上的極限開關

極限開關也可以直接接在馬達控制器上，控制器碰到開關時會**自己停止**那個方向的輸出，反應比經過程式更快：

=== "REV"

    SPARK MAX / SPARK Flex 有正向 (Forward) 和反向 (Reverse) 兩個極限開關輸入：

    ```java
    boolean atBottom = motor.getReverseLimitSwitch().isPressed().get(false);
    ```

    控制器的行為（要不要自動停止、開關是常開還是常閉）用設定裡的 `config.limitSwitch` 調整。

=== "CTRE"

    Talon FX 的極限開關接在資料埠上：

    ```java
    boolean atBottom = motor.getReverseLimit().getValue() == ReverseLimitValue.ClosedToGround;
    ```

    控制器的行為用設定裡的 `config.HardwareLimitSwitch` 調整。

### `debounce`：過濾雜訊

機械開關在按下的瞬間可能會快速地開、關跳動好幾次（稱為 **Bounce（彈跳）**），光遮斷感測器也可能被晃動的物件短暫遮住。用 Trigger 的 `debounce()`，條件要**持續**一段時間才算成立：

```java
new Trigger(intake::hasGamePiece)
    .debounce(0.1)   // 持續 0.1 秒才算真的拿到
    .onTrue(Commands.print("拿到遊戲物件！"));
```

## 範例

在 [上一頁的 Arm](encoders.md#arm用絕對式編碼器讀角度) 加上底部的極限開關。新增的部分：

```java
private final DigitalInput bottomLimit = new DigitalInput(ArmConstants.BOTTOM_LIMIT_CHANNEL);

/** 手臂是否碰到底部的極限開關。實際讀值要看接法，必要時拿掉 ! 號。 */
public boolean isAtBottom() {
  return !bottomLimit.get();
}

public void setThrottle(double throttle) {
  if (isAtBottom() && throttle < 0) {
    throttle = 0;
  }
  motor.setThrottle(throttle);
}
```

??? example "完整的 Arm.java"

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
        import org.wpilib.hardware.discrete.DigitalInput;
        import org.wpilib.telemetry.Telemetry;

        public class Arm extends SubsystemBase {
          private final SparkMax motor =
              new SparkMax(CANPort.CAN_S2, ArmConstants.MOTOR_ID, MotorType.kBrushless);
          private final AbsoluteEncoder absoluteEncoder = motor.getAbsoluteEncoder();
          private final DigitalInput bottomLimit = new DigitalInput(ArmConstants.BOTTOM_LIMIT_CHANNEL);

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

          /** 手臂是否碰到底部的極限開關。實際讀值要看接法，必要時拿掉 ! 號。 */
          public boolean isAtBottom() {
            return !bottomLimit.get();
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
        import org.wpilib.hardware.discrete.DigitalInput;
        import org.wpilib.telemetry.Telemetry;

        public class Arm extends SubsystemBase {
          private final CANBus bus = new CANBus(CANPort.CAN_S2);
          private final TalonFX motor = new TalonFX(ArmConstants.MOTOR_ID, bus);
          private final CANcoder cancoder = new CANcoder(ArmConstants.CANCODER_ID, bus);
          private final DigitalInput bottomLimit = new DigitalInput(ArmConstants.BOTTOM_LIMIT_CHANNEL);

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

          /** 手臂是否碰到底部的極限開關。實際讀值要看接法，必要時拿掉 ! 號。 */
          public boolean isAtBottom() {
            return !bottomLimit.get();
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

## 常見錯誤

!!! bug "常見錯誤：極限開關的方向判斷反了"
    `isAtBottom()` 的 `!` 寫反，手臂在正常位置時程式以為已經到底，**往下就完全不能動**；或是真的到底了程式卻不知道。一定要先在 Telemetry 確認讀值。

!!! bug "常見錯誤：連往上也一起擋住"
    寫成 `if (isAtBottom()) throttle = 0;`，手臂碰到開關後就再也動不了。只擋住會讓情況更糟的方向。

!!! bug "常見錯誤：只靠程式保護，沒有機構限位"
    程式和感測器都可能出錯（線鬆了、開關壞了）。極限開關是**多一層**保護，機構上的硬體限位（擋塊）還是要有。

## 小練習

1. 在 Elastic 上觀察 `Arm/AtBottom`，用手按下極限開關，確認 `isAtBottom()` 的方向正確。
2. 手臂碰到底部時，把手臂的相對式編碼器歸零。（提示：在 `periodic()` 裡檢查 `isAtBottom()`）想一想：既然已經有絕對式編碼器了，什麼情況下還需要這樣做？
3. 加上**軟體限位**：手臂角度超過 110 度時，不允許再往上。

??? question "第 2 題參考答案"
    在 `periodic()` 裡加上：

    === "REV"

        ```java
        if (isAtBottom()) {
          motor.getEncoder().setPosition(0);
        }
        ```

    === "CTRE"

        ```java
        if (isAtBottom()) {
          motor.setPosition(0);
        }
        ```

    有絕對式編碼器的手臂其實不太需要這樣做。這個技巧（叫做 **Homing（歸零）**）主要用在**只有相對式編碼器**的機構，例如升降機構只有馬達內建的編碼器時：開機後先讓機構慢慢往下，碰到極限開關就把編碼器歸零，之後的位置才準。

??? question "第 3 題參考答案"
    在 `ArmConstants` 加上 `MAX_ANGLE_DEGREES = 110.0`，修改 `setThrottle()`：

    ```java
    public void setThrottle(double throttle) {
      if (isAtBottom() && throttle < 0) {
        throttle = 0;
      }
      if (getAngleDegrees() >= ArmConstants.MAX_ANGLE_DEGREES && throttle > 0) {
        throttle = 0;   // 已經到最高點，不能再往上
      }
      motor.setThrottle(throttle);
    }
    ```

    和極限開關一樣，只擋住會讓情況更糟的方向（往上），往下還是可以動。
