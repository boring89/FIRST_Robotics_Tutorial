# PID

--8<-- "alpha-warning.md"

## 這頁學什麼

- P、I、D 三個項目各自在做什麼
- 用 WPILib 的 `PIDController` 寫一個 Command：底盤轉到指定角度
- 手動調 PID 的步驟
- 在馬達控制器上跑 PID 的方式

## 觀念

### PID 是什麼

**PID** 是最常見的 Closed loop 控制方法，輸出由三個部分加起來：

```text
輸出 = kP × 誤差  +  kI × 誤差的累積  +  kD × 誤差的變化速度
```

| 項目 | 看什麼 | 效果 | 比喻 |
| --- | --- | --- | --- |
| **P**（Proportional，比例） | **現在**的誤差 | 離目標越遠，出力越大 | 離目的地越遠，油門踩越深 |
| **I**（Integral，積分） | **過去**累積的誤差 | 誤差一直消不掉時，慢慢加大出力 | 一直差一點點到不了，就越踩越用力 |
| **D**（Derivative，微分） | 誤差**變化**的速度 | 接近目標時減速，防止衝過頭 | 快到的時候提早放油門 |

`kP`、`kI`、`kD` 是三個要調整的參數 (Gain)。

### 只用 P 會怎樣

只有 P 的時候：

- `kP` 太小：機構動得很慢，可能停在離目標還有一段距離的地方（出力太小，推不動）
- `kP` 剛好：快速接近目標，稍微超過一點點後停下
- `kP` 太大：衝過頭、再衝回來，來回**震盪 (Oscillation)**

加上 D 可以抑制震盪；FRC 大部分的機構用 **P + D**（或 P + Feedforward）就夠了，**I 通常設為 0**，因為它很容易讓系統不穩定。

### 用 `PIDController` 轉到指定角度

WPILib 的 `PIDController` 在 Systemcore 上計算 PID。底盤轉向需要用 IMU 的角度，只能在 Systemcore 上算：

```java
PIDController controller = new PIDController(kP, kI, kD);
controller.setSetpoint(90);                    // 目標：90 度
double output = controller.calculate(目前角度);  // 每 20 ms 呼叫一次，算出輸出
```

兩個重要的設定：

- `enableContinuousInput(-180, 180)`：告訴控制器角度是**繞一圈會回來**的。目前 170 度、目標 -170 度時，控制器會知道只要再轉 20 度，而不是反方向轉 340 度
- `setTolerance(2)`：誤差在 ±2 度以內就算到了，`atSetpoint()` 會回傳 `true`

## 範例

先在[第 7 章的 Drivetrain](../07-drivetrain/differential.md#範例) 加一個讓其他 Command 直接開車的方法。第三個參數 `false` 是關掉輸入的平方，讓 PID 的輸出直接作用：

```java
public void arcadeDrive(double forward, double rotation) {
  drive.arcadeDrive(forward, rotation, false);
}
```

在 `Constants.java` 的 `DrivetrainConstants` 加上：

```java
public static final double TURN_KP = 0.01;                 // 每 1 度誤差，輸出 0.01
public static final double TURN_KD = 0.0;
public static final double TURN_TOLERANCE_DEGREES = 2.0;
public static final double TURN_MAX_OUTPUT = 0.5;
```

寫一個 `TurnToAngle` Command：

```java title="src/main/java/first/robot/commands/TurnToAngle.java"
package first.robot.commands;

import first.robot.Constants.DrivetrainConstants;
import first.robot.subsystems.Drivetrain;
import org.wpilib.command2.Command;
import org.wpilib.math.controller.PIDController;

/** 原地轉到指定的朝向（場地座標，逆時針為正）。 */
public class TurnToAngle extends Command {
  private final Drivetrain drivetrain;
  private final PIDController controller =
      new PIDController(DrivetrainConstants.TURN_KP, 0.0, DrivetrainConstants.TURN_KD);

  public TurnToAngle(Drivetrain drivetrain, double targetDegrees) {
    this.drivetrain = drivetrain;
    controller.setSetpoint(targetDegrees);
    controller.enableContinuousInput(-180, 180);
    controller.setTolerance(DrivetrainConstants.TURN_TOLERANCE_DEGREES);
    addRequirements(drivetrain);
  }

  @Override
  public void initialize() {
    controller.reset();
  }

  @Override
  public void execute() {
    double output = controller.calculate(drivetrain.getHeading().getDegrees());
    output = Math.clamp(output, -DrivetrainConstants.TURN_MAX_OUTPUT, DrivetrainConstants.TURN_MAX_OUTPUT);
    drivetrain.arcadeDrive(0.0, output);
  }

  @Override
  public boolean isFinished() {
    return controller.atSetpoint();
  }

  @Override
  public void end(boolean interrupted) {
    drivetrain.arcadeDrive(0.0, 0.0);
  }
}
```

- `controller.reset()`：清掉上一次執行留下的狀態（D 和 I 會用到過去的誤差）
- `Math.clamp(...)`：限制輸出的範圍，避免誤差很大時全速旋轉

在 `RobotContainer` 綁定按鈕，例如按十字鍵的上 / 左 / 下 / 右轉到四個方向：

```java
driver.dpadUp().onTrue(new TurnToAngle(drivetrain, 0));
driver.dpadLeft().onTrue(new TurnToAngle(drivetrain, 90));
driver.dpadDown().onTrue(new TurnToAngle(drivetrain, 180));
driver.dpadRight().onTrue(new TurnToAngle(drivetrain, -90));
```

## 手動調 PID 的步驟

1. **`kI`、`kD` 設為 0，`kP` 設一個很小的值**（例如上面的 0.01），按下按鈕看機器人怎麼轉
2. **慢慢加大 `kP`**（每次 ×2 左右），直到機器人能快速轉到目標附近，而且開始有一點點來回震盪
3. **加一點 `kD`** 抑制震盪。D 太大時機器人會抖動、發出怪聲
4. 如果最後總是差一點點到不了：先檢查是不是摩擦力太大（[Feedforward](feedforward.md) 的 kS 可以解決），最後才考慮加很小的 `kI`

!!! tip "小技巧：用 Telemetry 看曲線"
    把目標和實際值都用 `Telemetry.log()` 送出，在 AdvantageScope 或 Elastic 畫成同一張圖。看著曲線調參數，比用眼睛看機器人準確很多。

## 在馬達控制器上跑 PID

單一馬達的速度或位置控制，可以把 PID 交給馬達控制器。做法是：在設定裡寫好參數，然後送出**目標值**而不是輸出比例：

=== "REV"

    ```java
    // 設定（在建構子裡）
    config.closedLoop.p(0.0002).d(0.0);

    // 送出目標：維持 4000 RPM
    motor.getClosedLoopController().setSetpoint(4000, ControlType.kVelocity);
    ```

=== "CTRE"

    ```java
    // 設定（在建構子裡）
    config.Slot0.kP = 0.1;
    config.Slot0.kD = 0.0;

    // 送出目標：維持 4000 RPM（Phoenix 6 用每秒轉數）
    motor.setControl(new VelocityVoltage(4000 / 60.0));
    ```

送出目標之後，控制器會自己每 1 ms 計算一次 PID，直到收到新的指令。完整的寫法在下一頁 [Feedforward](feedforward.md) 的範例。

!!! info "注意單位"
    控制器上的 PID，誤差的單位是**控制器的單位**：REV 是馬達的圈數 / RPM，CTRE 是圈數 / 每秒轉數。同樣的 `kP` 在兩家的效果完全不同，網路上的參數不能直接抄。

## 常見錯誤

!!! bug "常見錯誤：角度控制沒有開 `enableContinuousInput`"
    從 170 度轉到 -170 度，機器人會反方向轉了一大圈。角度類的控制都要開。

!!! bug "常見錯誤：PID 方向反了"
    輸出為正時機構卻往「誤差變大」的方向動，機構會全速衝走、停不下來。第一次測試時 `kP` 設很小，確認機構會往目標移動；方向反了就檢查馬達的反轉設定或感測器的方向。

!!! bug "常見錯誤：每次執行都 `new` 一個 PIDController，或忘了 `reset()`"
    在 `execute()` 裡建立新的 PIDController，D 永遠沒有作用；重複使用同一個 Command 卻不 `reset()`，D 會用到上一次留下的誤差，開始時會突然抖一下。

## 小練習

1. 照著步驟調出你們底盤的 `TURN_KP`，記錄每次調整的值和結果。
2. 在 `TurnToAngle` 的 `execute()` 裡用 Telemetry 送出目標角度和目前角度，在 AdvantageScope 看轉向的曲線。
3. 想一想：為什麼 `TurnToAngle` 適合在 Systemcore 上算，而 Shooter 的轉速適合在馬達控制器上算？
