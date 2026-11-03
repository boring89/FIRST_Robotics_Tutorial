# SysId

--8<-- "alpha-warning.md"

## 這頁學什麼

- SysId 在做什麼、為什麼比手動調 Feedforward 準
- 在 Subsystem 加上 SysId 的測試 Command
- 執行測試、分析資料，得到 kS、kV、kA

## 觀念

### 用量測代替猜測

[Feedforward](feedforward.md) 的 kS、kV、kA 可以手動慢慢調，但更好的方法是**直接量測**。**SysId（System Identification，系統識別）** 是 WPILib 的工具：讓機構照著固定的方式動，記錄電壓、位置、速度，再用數學算出 Feedforward 的參數。

### 四個測試

SysId 會跑四個測試：

| 測試 | 做什麼 | 量到什麼 |
| --- | --- | --- |
| **Quasistatic（準靜態）**，正向 | 電壓**很慢地**從 0 往上增加 | kS、kV |
| **Quasistatic**，反向 | 同上，反方向 | kS、kV |
| **Dynamic（動態）**，正向 | 電壓**突然**跳到一個固定值 | kA |
| **Dynamic**，反向 | 同上，反方向 | kA |

每個測試都要**按住按鈕才會執行**，放開就停止。測試時機構會一直加速，**一定要在機構撞到底之前放開**。

!!! warning "注意：測試空間"
    - Shooter、Intake 這種可以一直轉的機構最安全
    - 手臂、升降機構有行程限制，Quasistatic 測試前要確認電壓增加的速度夠慢，在撞到底之前放開
    - 底盤要在**很大的空曠場地**測試，機器人會一直加速往前衝

## 範例

為 [Feedforward 那頁的 Shooter](feedforward.md#範例) 加上 SysId。用 WPILib 的 **`SysIdRoutine`**，需要告訴它兩件事：

1. **怎麼用指定的電壓驅動馬達**
2. **怎麼記錄目前的電壓、位置、速度**

在 `Shooter` 加上：

=== "REV"

    ```java
    private final SysIdRoutine sysIdRoutine =
        new SysIdRoutine(
            new SysIdRoutine.Config(),
            new SysIdRoutine.Mechanism(
                motor::setVoltage,
                log -> log.motor("shooter")
                    .voltage(Volts.of(motor.getAppliedOutput().get(0.0) * motor.getBusVoltage().get(0.0)))
                    .angularPosition(Rotations.of(encoder.getPosition().get(0.0)))
                    .angularVelocity(RPM.of(getRpm())),
                this));

    public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {
      return sysIdRoutine.quasistatic(direction);
    }

    public Command sysIdDynamic(SysIdRoutine.Direction direction) {
      return sysIdRoutine.dynamic(direction);
    }
    ```

    REV 沒有直接回報馬達電壓，用「輸出比例 × 電池電壓」算出來。

=== "CTRE"

    ```java
    private final VoltageOut voltageRequest = new VoltageOut(0);

    private final SysIdRoutine sysIdRoutine =
        new SysIdRoutine(
            new SysIdRoutine.Config(),
            new SysIdRoutine.Mechanism(
                volts -> motor.setControl(voltageRequest.withOutput(volts)),
                log -> log.motor("shooter")
                    .voltage(motor.getMotorVoltage().getValue())
                    .angularPosition(motor.getPosition().getValue())
                    .angularVelocity(motor.getVelocity().getValue()),
                this));

    public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {
      return sysIdRoutine.quasistatic(direction);
    }

    public Command sysIdDynamic(SysIdRoutine.Direction direction) {
      return sysIdRoutine.dynamic(direction);
    }
    ```

    Phoenix 6 的 `getValue()` 回傳的是**帶單位**的值（例如 `Voltage`、`Angle`），可以直接交給 SysId。

`Volts`、`Rotations`、`RPM` 來自 WPILib 的[單位函式庫](../06-motors-sensors/further.md#單位函式庫)，要加上 `import static org.wpilib.units.Units.*;`。

??? example "完整的 Shooter.java"

    === "REV"

        ```java title="src/main/java/first/robot/subsystems/Shooter.java"
        package first.robot.subsystems;

        import static org.wpilib.units.Units.RPM;
        import static org.wpilib.units.Units.Rotations;
        import static org.wpilib.units.Units.Volts;

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
        import org.wpilib.command2.sysid.SysIdRoutine;
        import org.wpilib.hardware.bus.CANPort;
        import org.wpilib.telemetry.Telemetry;

        public class Shooter extends SubsystemBase {
          private final SparkFlex motor =
              new SparkFlex(CANPort.CAN_S1, ShooterConstants.MOTOR_ID, MotorType.kBrushless);
          private final RelativeEncoder encoder = motor.getEncoder();
          private final SparkClosedLoopController controller = motor.getClosedLoopController();

          private final SysIdRoutine sysIdRoutine =
              new SysIdRoutine(
                  new SysIdRoutine.Config(),
                  new SysIdRoutine.Mechanism(
                      motor::setVoltage,
                      log -> log.motor("shooter")
                          .voltage(Volts.of(motor.getAppliedOutput().get(0.0) * motor.getBusVoltage().get(0.0)))
                          .angularPosition(Rotations.of(encoder.getPosition().get(0.0)))
                          .angularVelocity(RPM.of(getRpm())),
                      this));

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

          public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {
            return sysIdRoutine.quasistatic(direction);
          }

          public Command sysIdDynamic(SysIdRoutine.Direction direction) {
            return sysIdRoutine.dynamic(direction);
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
        import com.ctre.phoenix6.controls.VelocityVoltage;
        import com.ctre.phoenix6.controls.VoltageOut;
        import com.ctre.phoenix6.hardware.TalonFX;
        import com.ctre.phoenix6.signals.InvertedValue;
        import com.ctre.phoenix6.signals.NeutralModeValue;
        import first.robot.Constants.ShooterConstants;
        import org.wpilib.command2.Command;
        import org.wpilib.command2.SubsystemBase;
        import org.wpilib.command2.sysid.SysIdRoutine;
        import org.wpilib.hardware.bus.CANPort;
        import org.wpilib.telemetry.Telemetry;

        public class Shooter extends SubsystemBase {
          private final TalonFX motor = new TalonFX(ShooterConstants.MOTOR_ID, new CANBus(CANPort.CAN_S1));
          private final VelocityVoltage velocityRequest = new VelocityVoltage(0);
          private final VoltageOut voltageRequest = new VoltageOut(0);

          private final SysIdRoutine sysIdRoutine =
              new SysIdRoutine(
                  new SysIdRoutine.Config(),
                  new SysIdRoutine.Mechanism(
                      volts -> motor.setControl(voltageRequest.withOutput(volts)),
                      log -> log.motor("shooter")
                          .voltage(motor.getMotorVoltage().getValue())
                          .angularPosition(motor.getPosition().getValue())
                          .angularVelocity(motor.getVelocity().getValue()),
                      this));

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

          public Command sysIdQuasistatic(SysIdRoutine.Direction direction) {
            return sysIdRoutine.quasistatic(direction);
          }

          public Command sysIdDynamic(SysIdRoutine.Direction direction) {
            return sysIdRoutine.dynamic(direction);
          }

          @Override
          public void periodic() {
            Telemetry.log("Shooter/RPM", getRpm());
            Telemetry.log("Shooter/AtSpeed", isAtSpeed());
          }
        }
        ```

在 `RobotContainer` 用第二支手把（編號 1）綁定四個測試，避免比賽時誤觸：

```java
private final CommandGamepad tester = new CommandGamepad(1);

// 在 configureBindings() 裡
tester.faceUp().whileTrue(shooter.sysIdQuasistatic(SysIdRoutine.Direction.FORWARD));
tester.faceDown().whileTrue(shooter.sysIdQuasistatic(SysIdRoutine.Direction.REVERSE));
tester.faceRight().whileTrue(shooter.sysIdDynamic(SysIdRoutine.Direction.FORWARD));
tester.faceLeft().whileTrue(shooter.sysIdDynamic(SysIdRoutine.Direction.REVERSE));
```

!!! info "2027 新改動"
    `SysIdRoutine.Direction` 的值在 2027 改成全大寫的 `FORWARD`、`REVERSE`（2026 以前是 `kForward`、`kReverse`）。

## 執行測試和分析

1. 部署程式，Enable（Teleop 模式）
2. 依序**按住**四個按鈕，每個測試大約 5～10 秒，或在機構快到極限前放開
3. 四個測試都做完後 Disable
4. 用 **Data Log Tool**（**WPILib: Start Tool**）把 Systemcore 上的 log 檔下載到電腦
5. 打開 **SysId** 工具（同樣用 **WPILib: Start Tool**），載入 log 檔
6. 選擇機構 `shooter`、分析類型 **Simple**（Shooter、Intake 這類機構；手臂選 **Arm**，升降機構選 **Elevator**）
7. SysId 會算出 kS、kV、kA，並顯示資料和模型吻合的程度

把算出來的值填回 `Constants.java`。注意**單位**：SysId 的結果是依照你記錄的單位，要換算成控制器的單位（REV 用 RPM，CTRE 用每秒轉數）。

!!! tip "小技巧：CTRE 的 SignalLogger"
    CTRE 的 Phoenix 6 有自己的記錄工具 **SignalLogger**，可以記錄更高頻率的資料，在 Phoenix Tuner X 裡轉換成 SysId 能讀的格式。進階使用者可以參考 CTRE 文件的 SysId 章節。

## 常見錯誤

!!! bug "常見錯誤：沒有按住，測試只跑了一瞬間"
    SysId 的測試要**按住**按鈕才會持續執行（所以用 `whileTrue`）。用 `onTrue` 綁定的話，測試不會停，非常危險。

!!! bug "常見錯誤：記錄的單位和控制器的單位不一致"
    記錄時用了「手臂角度」，控制器卻用「馬達圈數」，算出來的 kV 會差一個齒輪比。記錄的位置和速度要和控制器使用的回授感測器一致。

## 小練習

1. 為 Shooter 跑完四個 SysId 測試，比較 SysId 算出的 kV 和[Feedforward](feedforward.md)那頁手動調出來的值。
2. 用 SysId 的結果更新 `Constants.java`，觀察 Shooter 的轉速曲線有沒有變得更穩定。
3. 想一想：手臂的 SysId 測試，為什麼要選 **Arm** 分析類型，而不是 **Simple**？（提示：重力）
