# 馬達控制器設定

--8<-- "alpha-warning.md"

## 這頁學什麼

- 用設定讓馬達反轉，而不是在程式裡到處加負號
- Brake 和 Coast 兩種 Idle mode 的差別
- 為什麼一定要設定電流限制
- REV 和 CTRE 的設定寫法

## 觀念

### 為什麼要設定

馬達控制器出廠時是一組預設值，但每個機構需要的設定不一樣：

| 設定 | 用途 |
| --- | --- |
| **Inverted（反轉）** | 讓「正值」對應到機構你想要的方向 |
| **Idle mode（閒置模式）** | 輸出為 0 時，馬達要煞住還是自由轉動 |
| **Current limit（電流限制）** | 限制馬達最多能吃多少電流 |

### Inverted（反轉）

馬達裝在機構左邊或右邊，同樣的正值轉出來的方向就會相反。與其在程式裡每個地方加負號，不如在設定裡把馬達設成反轉，程式裡就可以統一用「正值 = 往前 / 往上 / 吸入」。

### Idle mode：Brake 和 Coast

輸出為 0 時：

| 模式 | 行為 | 適合 |
| --- | --- | --- |
| **Brake（煞車）** | 馬達會抵抗轉動，機構很快停下來 | 手臂、升降機構、底盤（停下來不會滑） |
| **Coast（滑行）** | 馬達自由轉動，機構慢慢停下來 | Shooter 的飛輪（突然煞住會傷機構） |

!!! warning "注意：Brake 不是鎖住"
    Brake 模式只是讓馬達「比較難轉」，不能完全撐住重物。手臂在 Brake 模式下還是可能因為重力慢慢往下掉，要撐住手臂需要[第 8 章](../08-control/feedforward.md)的 Feedforward。

### Current limit（電流限制）

馬達卡住（例如 Intake 夾到東西、底盤推牆）時，電流會暴增，可能：

- 讓電池電壓瞬間下降，整台機器人進入 [Brownout](../02-hardware/further.md#電壓不足-brownout)
- 燒掉馬達
- 跳掉斷路器

設定電流限制，馬達最多只會用到限制的電流。常見的起始值：

| 機構 | 建議起始值 |
| --- | --- |
| 小型機構（NEO 550 等） | 20～30 A |
| Intake、Shooter | 40 A |
| 底盤 | 40～60 A |

實際數值要依照機構測試調整。

### 設定只要做一次

設定應該在 Subsystem 的**建構子**裡做一次，不要放在 `periodic()` 或 Command 裡重複送出。設定會透過 CAN 傳到控制器，重複送出會佔用 CAN 頻寬，寫入控制器的記憶體也有次數限制。

## 範例

幫 Shooter 加上設定：方向不反轉、Coast 模式、40 A 電流限制。電流限制的常數加在 `Constants.java` 的 `ShooterConstants` 裡：

```java
public static final int CURRENT_LIMIT = 40;
```

=== "REV"

    ```java title="src/main/java/first/robot/subsystems/Shooter.java"
    package first.robot.subsystems;

    import com.revrobotics.PersistMode;
    import com.revrobotics.ResetMode;
    import com.revrobotics.spark.SparkFlex;
    import com.revrobotics.spark.SparkLowLevel.MotorType;
    import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
    import com.revrobotics.spark.config.SparkFlexConfig;
    import first.robot.Constants.ShooterConstants;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.bus.CANPort;

    public class Shooter extends SubsystemBase {
      private final SparkFlex motor =
          new SparkFlex(CANPort.CAN_S1, ShooterConstants.MOTOR_ID, MotorType.kBrushless);

      public Shooter() {
        SparkFlexConfig config = new SparkFlexConfig();
        config
            .inverted(false)
            .idleMode(IdleMode.kCoast)
            .smartCurrentLimit(ShooterConstants.CURRENT_LIMIT);
        motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
      }

      public Command spinCommand() {
        return startEnd(() -> motor.setThrottle(ShooterConstants.SHOOT_THROTTLE), motor::stopMotor)
            .withName("Spin");
      }
    }
    ```

    `configure()` 的兩個參數：

    - `ResetMode.kResetSafeParameters`：先把設定重設成預設值，再套用這次的設定。這樣控制器裡不會殘留以前的設定
    - `PersistMode.kPersistParameters`：把設定存進控制器，斷電後也會保留

    SPARK MAX 用 `SparkMaxConfig`，SPARK Flex 用 `SparkFlexConfig`，寫法一樣。

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

      public Command spinCommand() {
        return startEnd(() -> motor.setThrottle(ShooterConstants.SHOOT_THROTTLE), motor::stopMotor)
            .withName("Spin");
      }
    }
    ```

    - `InvertedValue`：`CounterClockwise_Positive`（從馬達軸的正面看，逆時針為正）或 `Clockwise_Positive`
    - `NeutralModeValue`：CTRE 把 Idle mode 叫做 **Neutral mode**
    - `new TalonFXConfiguration()` 建立的是一份完整的設定，`apply()` 會把**所有**設定都送出去，沒有寫到的項目會變回預設值

    CTRE 有兩種電流限制：**Stator current**（流過馬達的電流，限制扭力和發熱）和 **Supply current**（從電池抽的電流，防止 Brownout）。新手先設 Stator 就好，[延伸學習](further.md) 有更多說明。

## 常見錯誤

!!! bug "常見錯誤：在 `periodic()` 裡送出設定"
    每 20 ms 送一次設定，會塞滿 CAN 匯流排，而且 REV 的 `kPersistParameters` 每次都會寫入控制器的快閃記憶體。設定只在建構子裡做一次。

!!! bug "常見錯誤：同一個機構的兩顆馬達方向設錯"
    兩顆馬達一起帶動同一根軸時（例如底盤一邊兩顆），如果一顆正轉、一顆反轉，兩顆馬達會互相抵抗，電流暴增、馬達發燙。**先用很小的輸出測試每顆馬達的方向**，確認一致後再一起測試。

!!! bug "常見錯誤：Shooter 用 Brake 模式"
    飛輪轉得很快、慣性很大，突然煞住會對齒輪和皮帶造成很大的衝擊。Shooter 用 Coast。

## 小練習

1. 幫第 5 章的 Intake 加上設定：Brake 模式、30 A 電流限制。
2. 用 REV Hardware Client 2 或 Phoenix Tuner X 確認設定真的有寫進控制器。
3. 把 Intake 改成 Coast 模式，比較吸入停止時遊戲物件的行為有什麼不同。

??? question "第 1 題參考答案"
    在 `Intake` 的建構子裡加上設定：

    === "REV"

        ```java
        public Intake() {
          SparkMaxConfig config = new SparkMaxConfig();
          config.idleMode(IdleMode.kBrake).smartCurrentLimit(30);
          motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
        }
        ```

    === "CTRE"

        ```java
        public Intake() {
          TalonFXConfiguration config = new TalonFXConfiguration();
          config.MotorOutput.NeutralMode = NeutralModeValue.Brake;
          config.CurrentLimits.StatorCurrentLimit = 30;
          config.CurrentLimits.StatorCurrentLimitEnable = true;
          motor.getConfigurator().apply(config);
        }
        ```

    30 也可以放進 `IntakeConstants`，例如 `CURRENT_LIMIT = 30`。

??? question "第 3 題參考答案"
    Coast 模式下，馬達停止後滾輪還會繼續轉一下，遊戲物件可能被**多吸進去一點**，或是在 Intake 裡滑動。Brake 模式會讓滾輪很快停下，物件停在感測器觸發的位置附近，比較容易控制。
