# Feedforward

--8<-- "alpha-warning.md"

## 這頁學什麼

- 為什麼只用 PID 不夠
- Feedforward 的四個參數：kS、kV、kA、kG
- 用 Feedforward + PID 讓 Shooter 維持指定轉速

## 觀念

### 只用 PID 的問題

PID 是**等誤差出現了才反應**。以 Shooter 為例，要維持 4000 RPM：

- 一開始誤差很大，P 讓馬達出力，轉速上升
- 越接近 4000 RPM，誤差越小，P 的出力也越小
- 但是要**維持** 4000 RPM 本來就需要一定的電壓！誤差變成 0 時 P 的出力也是 0，轉速就會掉下來

結果是轉速永遠停在比 4000 低一點的地方。

### Feedforward：先算好需要多少電壓

**Feedforward（前饋）** 的想法是：我們大概知道「要轉 4000 RPM，需要多少電壓」，那就**直接給這個電壓**，PID 只負責修正剩下的一點點誤差。

```text
總輸出 = Feedforward（預測需要的電壓）+ PID（修正誤差）
```

Feedforward 用一個簡單的物理模型預測需要的電壓：

| 參數 | 意思 | 單位（以 CTRE 速度控制為例） |
| --- | --- | --- |
| **kS**（Static） | 克服摩擦力、讓機構開始動所需的電壓 | 伏特 (V) |
| **kV**（Velocity） | 每增加一單位速度，需要多加的電壓 | V / (每秒轉數) |
| **kA**（Acceleration） | 每增加一單位加速度，需要多加的電壓 | V / (每秒轉數²) |
| **kG**（Gravity） | 撐住重力所需的電壓（手臂、升降機構才需要） | V |

速度控制的 Feedforward：

```text
電壓 = kS × sign(速度) + kV × 速度 + kA × 加速度
```

`sign(速度)` 是速度的正負號（+1 或 -1），因為摩擦力總是跟運動方向相反。

### kV 的估計方法

kV 可以先用馬達規格估一個起始值：

```text
kV ≈ 12 V ÷ 馬達空轉最高速度
```

例如 Kraken X60 的空轉速度約每秒 100 轉，kV ≈ 12 ÷ 100 = 0.12 V/(每秒轉數)。實際的值會因為機構的摩擦和負載而不同，用 [SysId](sysid.md) 量測最準。

### 手臂和升降機構：kG

- **升降機構 (Elevator)**：重力永遠一樣大，需要固定的 kG
- **手臂 (Arm)**：重力造成的力矩和角度有關。手臂**水平時最大**，**垂直時為 0**，所以撐住手臂需要的電壓是 `kG × cos(角度)`

這就是為什麼[第 6 章](../06-motors-sensors/encoders.md#absolute-encoder絕對式編碼器)要把角度換算成「水平為 0 度」。手臂的 Feedforward 在下一頁 [Motion Profile](motion-profile.md) 的範例裡。

## 範例

讓 Shooter 用控制器上的速度控制，維持在 4000 RPM。在 `Constants.java` 的 `ShooterConstants` 加上（數值是起始值，要實際調整）：

=== "REV"

    ```java
    public static final double TARGET_RPM = 4000;
    public static final double RPM_TOLERANCE = 150;
    public static final double KP = 0.0002;
    public static final double KS = 0.1;     // 伏特
    public static final double KV = 0.0018;  // 伏特 / RPM（≈ 12 V ÷ NEO Vortex 空轉約 6700 RPM）
    ```

=== "CTRE"

    ```java
    public static final double TARGET_RPM = 4000;
    public static final double RPM_TOLERANCE = 150;
    public static final double KP = 0.1;     // 伏特 / (每秒轉數)
    public static final double KS = 0.1;     // 伏特
    public static final double KV = 0.12;    // 伏特 / (每秒轉數)（≈ 12 V ÷ Kraken X60 空轉約每秒 100 轉）
    ```

完整的 Shooter：

=== "REV"

    ```java title="src/main/java/first/robot/subsystems/Shooter.java"
    package first.robot.subsystems;

    import com.revrobotics.PersistMode;
    import com.revrobotics.RelativeEncoder;
    import com.revrobotics.ResetMode;
    import com.revrobotics.spark.SparkClosedLoopController;
    import com.revrobotics.spark.SparkFlex;
    import com.revrobotics.spark.SparkLowLevel.ControlType;
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
      private final SparkClosedLoopController controller = motor.getClosedLoopController();

      public Shooter() {
        SparkFlexConfig config = new SparkFlexConfig();
        config
            .inverted(false)
            .idleMode(IdleMode.kCoast)
            .smartCurrentLimit(ShooterConstants.CURRENT_LIMIT);
        config.closedLoop.p(ShooterConstants.KP);
        config.closedLoop.feedForward.kS(ShooterConstants.KS).kV(ShooterConstants.KV);
        motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
      }

      /** 飛輪轉速，單位 RPM。 */
      public double getRpm() {
        return encoder.getVelocity().get(0.0);
      }

      public boolean isAtSpeed() {
        return Math.abs(getRpm() - ShooterConstants.TARGET_RPM) < ShooterConstants.RPM_TOLERANCE;
      }

      /** 用速度控制維持在 TARGET_RPM，結束時停止。 */
      public Command spinCommand() {
        return startEnd(
                () -> controller.setSetpoint(ShooterConstants.TARGET_RPM, ControlType.kVelocity),
                motor::stopMotor)
            .withName("Spin");
      }

      @Override
      public void periodic() {
        Telemetry.log("Shooter/RPM", getRpm());
        Telemetry.log("Shooter/AtSpeed", isAtSpeed());
      }
    }
    ```

    REVLib 的 Feedforward 設定在 `config.closedLoop.feedForward` 裡，單位是**伏特**；速度的單位是控制器的 RPM。

=== "CTRE"

    ```java title="src/main/java/first/robot/subsystems/Shooter.java"
    package first.robot.subsystems;

    import com.ctre.phoenix6.CANBus;
    import com.ctre.phoenix6.configs.TalonFXConfiguration;
    import com.ctre.phoenix6.controls.VelocityVoltage;
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
      private final VelocityVoltage velocityRequest = new VelocityVoltage(0);

      public Shooter() {
        TalonFXConfiguration config = new TalonFXConfiguration();
        config.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;
        config.MotorOutput.NeutralMode = NeutralModeValue.Coast;
        config.CurrentLimits.StatorCurrentLimit = ShooterConstants.CURRENT_LIMIT;
        config.CurrentLimits.StatorCurrentLimitEnable = true;
        config.Slot0.kP = ShooterConstants.KP;
        config.Slot0.kS = ShooterConstants.KS;
        config.Slot0.kV = ShooterConstants.KV;
        motor.getConfigurator().apply(config);
      }

      /** 飛輪轉速，單位 RPM。 */
      public double getRpm() {
        return motor.getVelocity().getValueAsDouble() * 60.0;
      }

      public boolean isAtSpeed() {
        return Math.abs(getRpm() - ShooterConstants.TARGET_RPM) < ShooterConstants.RPM_TOLERANCE;
      }

      /** 用速度控制維持在 TARGET_RPM，結束時停止。 */
      public Command spinCommand() {
        return startEnd(
                () -> motor.setControl(velocityRequest.withVelocity(ShooterConstants.TARGET_RPM / 60.0)),
                motor::stopMotor)
            .withName("Spin");
      }

      @Override
      public void periodic() {
        Telemetry.log("Shooter/RPM", getRpm());
        Telemetry.log("Shooter/AtSpeed", isAtSpeed());
      }
    }
    ```

    `VelocityVoltage` 是「用電壓做速度控制」的請求，Feedforward 和 PID 的單位都是伏特。請求物件可以重複使用，`withVelocity()` 會修改它的目標值，不用每次 `new`。

[第 6 章的 `ScoringCommands.shoot()`](../06-motors-sensors/encoders.md#shooter轉速夠快才發射) 用的是 `shooter::isAtSpeed`，所以不用修改，發射時就會等到轉速穩定在 4000 RPM 附近才送料。

### 調整的順序

1. **先調 Feedforward，`KP` 設為 0**：只靠 kS、kV，看轉速能不能接近 4000 RPM
    - 轉速太低：kV 加大；太高：kV 減小
2. **再加 P**：讓 P 修正剩下的誤差，並在發射時（物件壓到飛輪、轉速下降）更快恢復
3. 在 AdvantageScope 畫出 `Shooter/RPM`，觀察發射前後的曲線

## 常見錯誤

!!! bug "常見錯誤：Feedforward 的單位用錯"
    REV 的速度單位是 RPM，CTRE 是每秒轉數，kV 的數值會差 60 倍。從別隊的程式抄參數時，一定要確認單位和廠商。

!!! bug "常見錯誤：只調 P，把 P 調得很大"
    沒有 Feedforward 時，為了接近目標只能把 P 一直加大，結果系統開始震盪。轉速類的控制，**Feedforward 才是主角**，P 只是修正。

!!! bug "常見錯誤：每次都 `new` 控制請求"
    CTRE 的 `new VelocityVoltage(...)` 放在每 20 ms 執行的地方，會一直產生新物件。把請求存成欄位，用 `withVelocity()` 修改目標值。

## 小練習

1. 把 `KP` 設成 0，調整 `KV`，讓 Shooter 只靠 Feedforward 就能接近 4000 RPM。記下最後的 `KV`，和用馬達規格估的值比較。
2. 加上 `KP`，觀察發射遊戲物件時轉速下降多少、多久恢復。
3. 修改 `spinCommand()`，讓它接收一個目標轉速參數，近距離和遠距離發射可以用不同轉速。
