# 讓馬達轉起來

--8<-- "alpha-warning.md"

## 這頁學什麼

- 在程式裡建立馬達控制器物件
- 用 `setThrottle()` 設定馬達輸出
- 機器人停用時，馬達會發生什麼事
- 第一次讓馬達轉之前的安全檢查

## 觀念

### 建立馬達控制器物件

程式要控制一顆馬達，先要建立一個代表它的**物件**。建立時要告訴程式三件事：

1. **它接在哪一條 CAN 匯流排**：用 `CANPort`，例如 `CANPort.CAN_S0`（見 [韌體與 CAN 設定](../03-setup/firmware-can.md#程式裡怎麼指定-can-匯流排)）
2. **它的 CAN ID**
3. **它是什麼裝置**：用哪個類別就代表哪一種

=== "REV"

    ```java
    private final SparkMax motor = new SparkMax(CANPort.CAN_S0, 10, MotorType.kBrushless);
    ```

    - `SparkMax`：SPARK MAX 控制器。用 SPARK Flex 的話改成 `SparkFlex`，參數一樣
    - `CANPort.CAN_S0`：接在 Systemcore 的 CAN 0
    - `10`：CAN ID
    - `MotorType.kBrushless`：接的是無刷馬達（NEO 系列）

=== "CTRE"

    ```java
    private final TalonFX motor = new TalonFX(1, new CANBus(CANPort.CAN_S0));
    ```

    - `TalonFX`：Kraken X60、Kraken X44、Falcon 500 內建的控制器
    - `1`：CAN ID
    - `new CANBus(CANPort.CAN_S0)`：接在 Systemcore 的 CAN 0

    注意 CTRE 的參數順序：**ID 在前，匯流排在後**，和 REV 相反。

### 設定輸出：`setThrottle()`

```java
motor.setThrottle(0.2);
```

`setThrottle()` 設定馬達的輸出比例，範圍是 **-1.0 到 1.0**：

| 值 | 意思 |
| --- | --- |
| `1.0` | 正轉全速 |
| `0.2` | 正轉 20% |
| `0.0` | 停止 |
| `-0.5` | 反轉 50% |

設定之後，馬達會**一直維持這個輸出**，直到你設定新的值，或是機器人被停用。

!!! info "2027 新改動：`set()` 改名為 `setThrottle()`"
    2026 以前設定馬達輸出用的是 `motor.set(0.2)`。2027 的 WPILib 把它改名為 `setThrottle()`，REVLib 和 Phoenix 6 也跟著改了。舊的範例程式要改過才能用。

!!! tip "小技巧：CTRE 的另一種寫法"
    Phoenix 6 慣用的寫法是送出「控制請求 (Control request)」，例如 `motor.setControl(new DutyCycleOut(0.2))`，效果和 `setThrottle(0.2)` 一樣。之後要做速度、位置控制時都是這種寫法，[第 6 章](../06-motors-sensors/index.md) 會介紹。這章先用兩家都一樣的 `setThrottle()`。

### 停用時會發生什麼事

機器人被 Disable 時，**系統會自動關掉所有馬達的輸出**，不管你的程式最後設定了什麼。這是 FRC 最重要的安全機制，所以按下 Disable（或緊急停止）一定能讓機器人停下來。

但是重新 Enable 的時候，如果你的程式沒有重新設定，馬達可能會**繼續用停用前的值**。所以好習慣是：在 `disabledInit()` 裡主動把馬達設為停止。

## 範例

進入 Teleop 模式後，讓馬達以 20% 的速度轉動：

=== "REV"

    ```java title="src/main/java/first/robot/Robot.java"
    package first.robot;

    import com.revrobotics.spark.SparkLowLevel.MotorType;
    import com.revrobotics.spark.SparkMax;
    import org.wpilib.framework.TimedRobot;
    import org.wpilib.hardware.bus.CANPort;

    public class Robot extends TimedRobot {
      private final SparkMax motor = new SparkMax(CANPort.CAN_S0, 10, MotorType.kBrushless);

      @Override
      public void teleopPeriodic() {
        motor.setThrottle(0.2);
      }

      @Override
      public void disabledInit() {
        motor.stopMotor();
      }
    }
    ```

=== "CTRE"

    ```java title="src/main/java/first/robot/Robot.java"
    package first.robot;

    import com.ctre.phoenix6.CANBus;
    import com.ctre.phoenix6.hardware.TalonFX;
    import org.wpilib.framework.TimedRobot;
    import org.wpilib.hardware.bus.CANPort;

    public class Robot extends TimedRobot {
      private final TalonFX motor = new TalonFX(1, new CANBus(CANPort.CAN_S0));

      @Override
      public void teleopPeriodic() {
        motor.setThrottle(0.2);
      }

      @Override
      public void disabledInit() {
        motor.stopMotor();
      }
    }
    ```

記得把 `CANPort` 和 CAN ID 改成你實際接線的值。部署的方法見 [部署與除錯](deploy-debug.md)。

### 第一次測試前的檢查清單

- [ ] 馬達已經固定好，不會撞到東西
- [ ] 程式裡的 `CANPort`、CAN ID 和實際接線一樣
- [ ] 輸出先設**小一點**（0.1～0.2），確認方向對了再加大
- [ ] 手放在 Disable 的位置，隨時可以停下來
- [ ] 喊「Enable！」，確認周圍的人都知道

## 常見錯誤

!!! warning "注意：REV 的馬達類型不要設錯"
    無刷馬達（NEO、NEO Vortex、NEO 550）一定要用 `MotorType.kBrushless`。把無刷馬達設成 `kBrushed` 可能會**損壞馬達或控制器**。

!!! bug "常見錯誤：馬達完全不動"
    依序檢查：

    1. 機器人有沒有 Enable？是不是在 Teleop 模式？
    2. 控制器的指示燈有沒有亮？有沒有電？
    3. 程式裡的 `CANPort`、CAN ID 對不對？用 REV Hardware Client 2 / Phoenix Tuner X 確認
    4. 終端機有沒有出現找不到裝置的錯誤訊息？
    5. 韌體和 vendordep 的版本對不對？

!!! bug "常見錯誤：馬達轉的方向和預期相反"
    正值代表哪個方向，取決於馬達的安裝方式。可以先在程式裡加負號測試，之後在 [第 6 章](../06-motors-sensors/motor-config.md) 學怎麼用設定反轉馬達方向，讓程式比較好讀。

## 小練習

1. 執行範例，確認馬達在 Teleop 時轉動、Disable 時停止。
2. 把輸出改成 `-0.2`，觀察方向的變化。
3. 修改程式，讓馬達在 Teleop 時轉、在 Autonomous 時以反方向轉。
4. 想一想：如果把 `motor.setThrottle(0.2)` 放在 `teleopInit()` 而不是 `teleopPeriodic()`，馬達會轉嗎？為什麼？

??? question "第 2 題參考答案"
    馬達會往**反方向**轉，速度一樣是 20%。正負號只決定方向。

??? question "第 3 題參考答案"
    以 REV 為例（CTRE 只有建立馬達的那一行不一樣）：

    ```java title="src/main/java/first/robot/Robot.java"
    package first.robot;

    import com.revrobotics.spark.SparkLowLevel.MotorType;
    import com.revrobotics.spark.SparkMax;
    import org.wpilib.framework.TimedRobot;
    import org.wpilib.hardware.bus.CANPort;

    public class Robot extends TimedRobot {
      private final SparkMax motor = new SparkMax(CANPort.CAN_S0, 10, MotorType.kBrushless);

      @Override
      public void teleopPeriodic() {
        motor.setThrottle(0.2);
      }

      @Override
      public void autonomousPeriodic() {
        motor.setThrottle(-0.2);
      }

      @Override
      public void disabledInit() {
        motor.stopMotor();
      }
    }
    ```

??? question "第 4 題參考答案"
    會轉。`setThrottle()` 設定之後，馬達會一直維持這個輸出，所以只在進入 Teleop 時設定一次也會轉。不過實際的機器人程式通常要根據手把或感測器隨時調整輸出，所以會放在 `Periodic` 裡每次重新設定。
