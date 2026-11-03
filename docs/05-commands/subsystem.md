# Subsystem

--8<-- "alpha-warning.md"

## 這頁學什麼

- 用 `SubsystemBase` 寫一個 Subsystem
- Subsystem 應該公開什麼、隱藏什麼
- `periodic()` 適合放什麼
- Default command（預設指令）

## 觀念

### `SubsystemBase`

每個 Subsystem 都是一個繼承 **`SubsystemBase`** 的類別，習慣放在 `subsystems/` 資料夾：

```java
public class Intake extends SubsystemBase {
  // 馬達、感測器
  // 控制方法
  // Command factory
}
```

繼承 `SubsystemBase` 之後，建立物件時它會**自動向 Scheduler 註冊**，Scheduler 就會每 20 ms 呼叫它的 `periodic()`。

### Subsystem 裡放什麼

用 [類別與物件](../01-java/classes.md#封裝private-和-public) 學過的封裝觀念來設計：

| 內容 | 存取 | 例子 |
| --- | --- | --- |
| 馬達、感測器物件 | `private` | `motor`、`beamBreak` |
| 簡單的控制方法 | `public` | `setThrottle()`、`stop()` |
| 狀態查詢 | `public` | `hasGamePiece()` |
| **Command factory** | `public` | `intakeCommand()`、`ejectCommand()` |

**Command factory（指令工廠方法）** 是一個會回傳 `Command` 的方法。別的地方需要「Intake 吸入」這個動作時，呼叫 `intake.intakeCommand()` 就能拿到一個 Command，不需要知道裡面怎麼做。下一頁會詳細介紹怎麼寫。

### `periodic()`

`periodic()` 每 20 ms 會被呼叫一次，**不管有沒有 Command 在使用這個 Subsystem**。適合放：

- 更新 Telemetry
- 讀取感測器、更新狀態

不適合放控制馬達的程式，那是 Command 的工作。

### Default command（預設指令）

當沒有任何 Command 在使用某個 Subsystem 時，Scheduler 會執行它的 **Default command**。最常見的用法：

- Intake 的 default command：停止馬達
- 底盤的 default command：用搖桿開車（[第 7 章](../07-drivetrain/index.md)）

設定方式（通常在 `RobotContainer` 裡設定）：

```java
intake.setDefaultCommand(intake.stopCommand());
```

Default command **不應該自己結束**，否則 Scheduler 每次迴圈都會重新啟動它。所以 `stopCommand()` 是用 `run()` 寫的，會一直持續。

## 範例

一個完整的 Intake Subsystem，放在 `subsystems/Intake.java`。用到的常數放在 `Constants.java`，見 [RobotContainer](robot-container.md#constants)。

=== "REV"

    ```java title="src/main/java/first/robot/subsystems/Intake.java"
    package first.robot.subsystems;

    import com.revrobotics.spark.SparkLowLevel.MotorType;
    import com.revrobotics.spark.SparkMax;
    import first.robot.Constants.IntakeConstants;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.hardware.discrete.DigitalInput;
    import org.wpilib.telemetry.Telemetry;

    public class Intake extends SubsystemBase {
      private final SparkMax motor =
          new SparkMax(CANPort.CAN_S1, IntakeConstants.MOTOR_ID, MotorType.kBrushless);
      private final DigitalInput beamBreak = new DigitalInput(IntakeConstants.BEAM_BREAK_CHANNEL);

      public void setThrottle(double throttle) {
        motor.setThrottle(throttle);
      }

      public void stop() {
        motor.stopMotor();
      }

      /** 光被擋住時代表有遊戲物件。實際讀值要看感測器的接法，必要時拿掉 ! 號。 */
      public boolean hasGamePiece() {
        return !beamBreak.get();
      }

      /** 吸入，拿到遊戲物件時自動停止。 */
      public Command intakeCommand() {
        return startEnd(() -> setThrottle(IntakeConstants.INTAKE_THROTTLE), this::stop)
            .until(this::hasGamePiece)
            .withName("Intake");
      }

      /** 把遊戲物件吐出來。 */
      public Command ejectCommand() {
        return startEnd(() -> setThrottle(IntakeConstants.EJECT_THROTTLE), this::stop)
            .withName("Eject");
      }

      /** 把遊戲物件送進 Shooter，不管感測器。 */
      public Command feedCommand() {
        return startEnd(() -> setThrottle(IntakeConstants.INTAKE_THROTTLE), this::stop)
            .withName("Feed");
      }

      /** 停止，當作 default command 使用。 */
      public Command stopCommand() {
        return run(this::stop).withName("Stop");
      }

      @Override
      public void periodic() {
        Telemetry.log("Intake/HasGamePiece", hasGamePiece());
      }
    }
    ```

=== "CTRE"

    ```java title="src/main/java/first/robot/subsystems/Intake.java"
    package first.robot.subsystems;

    import com.ctre.phoenix6.CANBus;
    import com.ctre.phoenix6.hardware.TalonFX;
    import first.robot.Constants.IntakeConstants;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.hardware.discrete.DigitalInput;
    import org.wpilib.telemetry.Telemetry;

    public class Intake extends SubsystemBase {
      private final TalonFX motor = new TalonFX(IntakeConstants.MOTOR_ID, new CANBus(CANPort.CAN_S1));
      private final DigitalInput beamBreak = new DigitalInput(IntakeConstants.BEAM_BREAK_CHANNEL);

      public void setThrottle(double throttle) {
        motor.setThrottle(throttle);
      }

      public void stop() {
        motor.stopMotor();
      }

      /** 光被擋住時代表有遊戲物件。實際讀值要看感測器的接法，必要時拿掉 ! 號。 */
      public boolean hasGamePiece() {
        return !beamBreak.get();
      }

      /** 吸入，拿到遊戲物件時自動停止。 */
      public Command intakeCommand() {
        return startEnd(() -> setThrottle(IntakeConstants.INTAKE_THROTTLE), this::stop)
            .until(this::hasGamePiece)
            .withName("Intake");
      }

      /** 把遊戲物件吐出來。 */
      public Command ejectCommand() {
        return startEnd(() -> setThrottle(IntakeConstants.EJECT_THROTTLE), this::stop)
            .withName("Eject");
      }

      /** 把遊戲物件送進 Shooter，不管感測器。 */
      public Command feedCommand() {
        return startEnd(() -> setThrottle(IntakeConstants.INTAKE_THROTTLE), this::stop)
            .withName("Feed");
      }

      /** 停止，當作 default command 使用。 */
      public Command stopCommand() {
        return run(this::stop).withName("Stop");
      }

      @Override
      public void periodic() {
        Telemetry.log("Intake/HasGamePiece", hasGamePiece());
      }
    }
    ```

兩個版本只有建立馬達的那幾行不一樣。**這就是 Subsystem 的好處**：換廠商時只要改這個檔案，其他用到 Intake 的程式完全不用動。

`DigitalInput` 讀的是 Systemcore 的 [Smart IO](../02-hardware/sensors.md#數位感測器) 連接埠，參數是 Smart IO 的編號。

## 常見錯誤

!!! bug "常見錯誤：同一個 Subsystem `new` 了兩次"
    在兩個地方各自 `new Intake()`，就會有兩個物件都想控制同一顆馬達。每個 Subsystem 只在 `RobotContainer` 裡建立**一次**，需要的地方把這個物件傳過去。

!!! bug "常見錯誤：在 `periodic()` 裡控制馬達"
    在 `periodic()` 裡寫 `motor.setThrottle(...)`，不管 Command 想做什麼，馬達每 20 ms 都會被改回來。控制馬達交給 Command。

!!! bug "常見錯誤：Default command 沒有使用這個 Subsystem"
    Default command 一定要把這個 Subsystem 列為 Requirement，否則 `setDefaultCommand()` 會丟出錯誤。用 Subsystem 自己的 `run()`、`startEnd()` 等方法建立的 Command，會自動加上 Requirement。

## 小練習

1. 照著範例在你的專案裡建立 `Intake.java`，並在 `Constants.java` 加上需要的常數。
2. 在 `Intake` 加一個方法 `public boolean isRunning()`，馬達有在轉時回傳 `true`，並在 `periodic()` 裡用 Telemetry 顯示它。（提示：在 `setThrottle()` 和 `stop()` 裡記錄目前的輸出）
3. 參考 `Intake`，寫一個 `Shooter` Subsystem，有一個 `spinCommand()`：Command 執行時以 `SHOOT_THROTTLE` 轉動，結束時停止。（答案在 [組合 Command](composition.md)）
