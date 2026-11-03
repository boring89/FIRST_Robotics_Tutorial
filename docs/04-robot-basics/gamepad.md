# 讀取手把輸入

--8<-- "alpha-warning.md"

## 這頁學什麼

- 建立手把物件、讀取搖桿和按鈕
- 為什麼搖桿往上推是負數
- Deadband（死區）的預設值
- 用手把控制馬達

## 觀念

### 建立手把物件

```java
private final Gamepad gamepad = new Gamepad(0);
```

`0` 是手把在 Driver Station 裡的編號 (Port)。第一支手把是 0，第二支是 1，依此類推。

2027 的 WPILib 有兩種常用的手把類別：

| 類別 | 按鈕名稱 | 適合 |
| --- | --- | --- |
| `Gamepad` | 用位置命名：`faceDown`、`faceRight`… | 任何手把都通用。**本教學使用這個** |
| `XboxController` | Xbox 的名稱：A、B、X、Y | 只用 Xbox 手把 |

WPILib 的範本也是用 `Gamepad`，所以這份教學都用它。按鈕位置的對照表在 [Driver Station 與手把](../02-hardware/driver-station.md#手把)。

### 讀搖桿和板機

| 方法 | 讀什麼 | 範圍 |
| --- | --- | --- |
| `getLeftX()`、`getLeftY()` | 左搖桿的 X、Y 軸 | -1.0 ～ 1.0 |
| `getRightX()`、`getRightY()` | 右搖桿的 X、Y 軸 | -1.0 ～ 1.0 |
| `getLeftTrigger()`、`getRightTrigger()` | 左、右板機 | 0.0 ～ 1.0 |

方向的規則：**X 軸往右是正，Y 軸往後（往自己的方向拉）是正**。所以搖桿**往前推是負數**。想要「往前推就前進」，要加一個負號：

```java
double forward = -gamepad.getLeftY();
```

### Deadband（死區）已經內建

2027 的 `Gamepad` **預設就有死區**：搖桿是 0.1、板機是 0.01。也就是搖桿推不到 10% 時，讀到的值會是 0。需要調整時：

```java
gamepad.setLeftYDeadband(0.05);
```

### 讀按鈕

每個按鈕都有三種讀法：

| 方法 | 什麼時候是 `true` | 適合 |
| --- | --- | --- |
| `getFaceDownButton()` | 按住的**每一次**迴圈 | 按住才動，例如按住吸入 |
| `getFaceDownButtonPressed()` | 按下的**那一次**迴圈 | 按一下就做一件事，例如切換模式 |
| `getFaceDownButtonReleased()` | 放開的**那一次**迴圈 | 放開時做一件事 |

其他按鈕的命名方式一樣，例如 `getFaceRightButton()`、`getLeftBumperButton()`。完整的方法清單可以在 VS Code 裡打 `gamepad.` 之後看自動完成的提示。

## 範例

用左搖桿控制馬達，按住下方按鈕 (`faceDown`) 時以固定速度轉：

=== "REV"

    ```java title="src/main/java/first/robot/Robot.java"
    package first.robot;

    import com.revrobotics.spark.SparkLowLevel.MotorType;
    import com.revrobotics.spark.SparkMax;
    import org.wpilib.driverstation.Gamepad;
    import org.wpilib.framework.TimedRobot;
    import org.wpilib.hardware.bus.CANPort;

    public class Robot extends TimedRobot {
      private static final double MAX_THROTTLE = 0.5;

      private final SparkMax motor = new SparkMax(CANPort.CAN_S0, 10, MotorType.kBrushless);
      private final Gamepad gamepad = new Gamepad(0);

      @Override
      public void teleopPeriodic() {
        double throttle;
        if (gamepad.getFaceDownButton()) {
          throttle = 0.3;
        } else {
          throttle = -gamepad.getLeftY() * MAX_THROTTLE;
        }
        motor.setThrottle(throttle);
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
    import org.wpilib.driverstation.Gamepad;
    import org.wpilib.framework.TimedRobot;
    import org.wpilib.hardware.bus.CANPort;

    public class Robot extends TimedRobot {
      private static final double MAX_THROTTLE = 0.5;

      private final TalonFX motor = new TalonFX(1, new CANBus(CANPort.CAN_S0));
      private final Gamepad gamepad = new Gamepad(0);

      @Override
      public void teleopPeriodic() {
        double throttle;
        if (gamepad.getFaceDownButton()) {
          throttle = 0.3;
        } else {
          throttle = -gamepad.getLeftY() * MAX_THROTTLE;
        }
        motor.setThrottle(throttle);
      }

      @Override
      public void disabledInit() {
        motor.stopMotor();
      }
    }
    ```

`MAX_THROTTLE` 把搖桿推到底時的輸出限制在 50%，第一次測試時比較安全。確認沒問題之後再調高。

## 常見錯誤

!!! bug "常見錯誤：往前推，馬達卻反轉"
    忘了在 `getLeftY()` 前面加負號。

!!! bug "常見錯誤：用 `getXxxButton()` 做「按一下切換」"
    ```java
    if (gamepad.getFaceRightButton()) {
      intakeOn = !intakeOn;   // ❌ 按住的每 20 ms 都會切換一次
    }
    ```

    按一下大約會持續好幾次迴圈，狀態會切換好幾次，結果看起來是隨機的。「按一下就做一次」要用 `getFaceRightButtonPressed()`。

!!! bug "常見錯誤：手把編號不對"
    Driver Station 裡的手把順序和程式裡的編號不一樣，就會讀到另一支手把，或什麼都讀不到。在 Driver Station 確認手把的編號。

## 小練習

1. 執行範例，確認搖桿推越多、馬達轉越快，放開時停止。
2. 改成用右板機控制馬達正轉、左板機控制反轉。（提示：`getRightTrigger() - getLeftTrigger()`）
3. 加一個「按一下切換」的功能：按一下 `faceRight` 讓馬達以 0.3 持續轉動，再按一下停止。

??? question "第 3 題參考答案"
    ```java
    private boolean spinning = false;

    @Override
    public void teleopPeriodic() {
      if (gamepad.getFaceRightButtonPressed()) {
        spinning = !spinning;
      }
      motor.setThrottle(spinning ? 0.3 : 0.0);
    }
    ```

    `條件 ? A : B` 叫 **Ternary operator（三元運算子）**：條件成立時是 A，否則是 B。
