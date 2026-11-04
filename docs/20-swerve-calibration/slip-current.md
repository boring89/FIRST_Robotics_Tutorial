# 打滑電流

--8<-- "alpha-warning.md"

## 這頁學什麼

- 打滑電流是什麼，為什麼電流限制要照它設定
- 頂著牆慢慢加電壓，找出輪子開始打滑的電流
- 從打滑電流算出輪子和地毯的摩擦係數

## 觀念

### 電流就是推力

馬達的**扭力和定子電流成正比**（扭力 = Kt × 電流，Kraken X60 的 Kt 約 0.0194 N·m / A）。經過齒輪和輪子，扭力變成輪子推地面的力：

**一個輪子的推力 = Kt × 電流 × 齒輪比 ÷ 輪子半徑**

但輪子能推的力有上限：**摩擦力 = 摩擦係數 × 壓在輪子上的重量**。推力超過摩擦力，輪子就會**打滑**：輪子轉了，機器人沒有跟著走那麼遠。剛好開始打滑時的電流，就是**打滑電流 (Slip current)**。

### 為什麼電流限制要照它設定

| 電流限制 | 結果 |
| --- | --- |
| 比打滑電流**高** | 加速、被推的時候輪子打滑：Odometry 以為走得比實際遠、輪子磨損、推不動對手 |
| 比打滑電流**低很多** | 不打滑，但馬達的力沒有用完，加速比較慢 |
| **接近**打滑電流 | 用上所有的抓地力，又不打滑 |

[第 13 章](../13-swerve-drive/hardware.md) 的電流限制寫 60 A，[第 17 章](../17-swerve-advanced/setpoint-generator.md) 的摩擦係數猜 1.2。用上面的公式算，一台 50 公斤、摩擦係數 1.2 的機器人，打滑電流大約是 **57 A**，比 60 A 還低一點。但實際的值要看你們的輪子、地毯和機器人的重量，所以要量。

### 頂著牆量

機器人的正面頂著一面牆，輪子朝前，慢慢增加電壓：

1. 一開始輪子推不動牆，也推不動機器人，**輪子不轉**，電流隨著電壓一直上升
2. 電流大到推力超過摩擦力的那一刻，**輪子開始打滑**，輪子的速度突然變大
3. 記下這一刻之前最大的電流，就是打滑電流

因為機器人一直頂著牆，輪子只要開始轉，就一定是打滑。

## 範例

### SwerveModule 和 Drivetrain：直接給電壓、讀電流

`SwerveModule` 加上一個 `VoltageOut` 請求和三個方法（`import com.ctre.phoenix6.controls.VoltageOut` 和 `com.ctre.phoenix6.configs.CurrentLimitsConfigs`）：

```java
private final VoltageOut voltageRequest = new VoltageOut(0);

/** 轉向固定在 angle，驅動馬達直接給電壓（不經過 PID 和 Feedforward）。量測時用。 */
public void runCharacterization(double volts, Rotation2d angle) {
  steerController.setSetpoint(
      angle.getRotations() * SwerveConstants.STEER_GEAR_RATIO, ControlType.kPosition);
  driveMotor.setControl(voltageRequest.withOutput(volts));
}

/** 驅動馬達的定子電流，單位：安培。 */
public double getDriveCurrentAmps() {
  return driveMotor.getStatorCurrent().getValueAsDouble();
}

/** 改變驅動馬達的定子電流限制。量打滑電流時暫時放寬用。 */
public void setDriveCurrentLimit(double amps) {
  driveMotor.getConfigurator().apply(
      new CurrentLimitsConfigs()
          .withStatorCurrentLimit(amps)
          .withStatorCurrentLimitEnable(true));
}
```

`runCharacterization` 下一頁的 SysId 也會用到。

`Drivetrain` 加上：

```java
/** 四個輪子都朝正前方，驅動馬達給同樣的電壓。 */
public void runDriveCharacterization(double volts) {
  for (SwerveModule module : modules) {
    module.runCharacterization(volts, Rotation2d.ZERO);
  }
}

/** 四顆驅動馬達的平均定子電流，單位：安培。 */
public double getAverageDriveCurrentAmps() {
  double sum = 0.0;
  for (SwerveModule module : modules) {
    sum += module.getDriveCurrentAmps();
  }
  return sum / modules.length;
}

public void setDriveCurrentLimit(double amps) {
  for (SwerveModule module : modules) {
    module.setDriveCurrentLimit(amps);
  }
}
```

### SlipCurrentTest

```java title="src/main/java/first/robot/commands/SlipCurrentTest.java"
package first.robot.commands;

import first.robot.Constants.SwerveConstants;
import first.robot.subsystems.Drivetrain;
import org.wpilib.command2.Command;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
import org.wpilib.math.system.DCMotor;
import org.wpilib.system.Timer;
import org.wpilib.telemetry.Telemetry;

/** 機器人頂著牆，慢慢加大驅動馬達的電壓，輪子開始打滑時記錄電流。 */
public class SlipCurrentTest extends Command {
  /** 電壓每秒增加多少，單位：伏特。 */
  private static final double RAMP_VOLTS_PER_SECOND = 0.5;
  /** 最多加到幾伏特。到了還沒打滑就停止。 */
  private static final double MAX_VOLTS = 6.0;
  /** 輪子速度超過這個值（公尺 / 秒）就算打滑：機器人頂著牆，輪子本來不該轉。 */
  private static final double SLIP_VELOCITY = 0.2;
  /** 測試時暫時放寬的電流限制，單位：安培。 */
  private static final double TEST_CURRENT_LIMIT = 120;

  private final Drivetrain drivetrain;
  private final Timer timer = new Timer();
  private double maxCurrentAmps;
  private boolean slipped;

  public SlipCurrentTest(Drivetrain drivetrain) {
    this.drivetrain = drivetrain;
    addRequirements(drivetrain);
  }

  @Override
  public void initialize() {
    drivetrain.setDriveCurrentLimit(TEST_CURRENT_LIMIT);
    timer.restart();
    maxCurrentAmps = 0.0;
    slipped = false;
  }

  @Override
  public void execute() {
    double volts = timer.get() * RAMP_VOLTS_PER_SECOND;
    drivetrain.runDriveCharacterization(volts);

    double current = drivetrain.getAverageDriveCurrentAmps();
    maxCurrentAmps = Math.max(maxCurrentAmps, current);

    double velocitySum = 0.0;
    for (SwerveModuleVelocity module : drivetrain.getModuleVelocities()) {
      velocitySum += Math.abs(module.velocity);
    }
    if (velocitySum / 4 > SLIP_VELOCITY) {
      slipped = true;
    }

    Telemetry.log("SlipTest/Volts", volts);
    Telemetry.log("SlipTest/CurrentAmps", current);
    Telemetry.log("SlipTest/WheelVelocity", velocitySum / 4);
  }

  @Override
  public boolean isFinished() {
    return slipped || timer.get() * RAMP_VOLTS_PER_SECOND > MAX_VOLTS;
  }

  @Override
  public void end(boolean interrupted) {
    drivetrain.stop();
    // 一定要改回原本的電流限制
    drivetrain.setDriveCurrentLimit(SwerveConstants.DRIVE_CURRENT_LIMIT);

    if (!slipped) {
      System.out.println("沒有偵測到打滑：確認機器人有頂著牆，或電壓上限太低");
      return;
    }
    // 四個輪子的推力 = 摩擦力上限：4 × 馬達扭力 × 齒輪比 ÷ 輪子半徑 = 摩擦係數 × 機器人重量
    double motorTorque = DCMotor.getKrakenX60(1).Kt * maxCurrentAmps;
    double wheelForce = motorTorque * SwerveConstants.DRIVE_GEAR_RATIO / (SwerveConstants.WHEEL_DIAMETER_METERS / 2);
    double cof = 4 * wheelForce / (SwerveConstants.ROBOT_MASS_KG * 9.81);
    System.out.printf("打滑電流 %.1f A，估計的摩擦係數 %.2f%n", maxCurrentAmps, cof);
  }
}
```

- **暫時放寬電流限制**：原本 60 A 的限制如果比打滑電流低，電流到 60 A 就上不去，永遠量不到打滑。`end()` 裡一定改回原本的值，就算中途放開按鈕也一樣
- **每秒加 0.5 V**：輪子不轉的時候，電流 ≈ 電壓 ÷ 馬達的電阻（Kraken X60 約 0.033 Ω），所以 2 V 就有大約 60 A。加得慢，才量得到打滑那一刻的電流
- **6 V 還沒打滑就停**：大約 12 秒。沒有頂著牆、或抓地力大到量不到時，不會一直加下去
- **最後算出摩擦係數**：四個輪子的推力加起來 = 摩擦係數 × 機器人的重量

綁在測試手把上：

```java
tester.rightBumper().whileTrue(new SlipCurrentTest(drivetrain));
```

## 步驟

1. 機器人放在地毯上，**正面的保險桿**貼著一面穩固的牆（場地的牆、或固定好的厚木板）。正面要和牆平行
2. 啟用，**按住**測試手把的 `rightBumper`
3. 電壓慢慢增加，幾秒後輪子開始打滑，Command 自己結束。終端機會印出類似這樣的結果：`打滑電流 57.3 A，估計的摩擦係數 1.20`
4. 等 10 秒讓馬達冷卻，再做兩次，取平均

在 AdvantageScope 把 `SlipTest/CurrentAmps` 和 `SlipTest/WheelVelocity` 放在同一張折線圖，可以看到電流一直上升，輪子速度在某一刻突然跳起來。

!!! warning "注意：大電流"
    測試時四顆驅動馬達的定子電流可能接近 120 A。時間很短（幾秒），而且電壓很低，電池實際送出的電流沒有這麼大，但還是要用**充飽的電池**，連續測試之間讓馬達休息一下。

!!! info "尚未在模擬上驗證"
    模擬裡沒有牆，也沒有輪子和地毯的摩擦，輪子一給電壓就轉起來，量不到有意義的值。這個 Command 只在模擬上確認過會自己結束、會把電流限制改回來，**實際的打滑電流要在機器人上量**。

## 常見錯誤

!!! bug "常見錯誤：沒頂著牆"
    機器人和牆之間有空隙，一給電壓機器人就往前走，輪子馬上「轉起來」，Command 以為打滑了，印出一個很小的電流（例如 2 A）。開始前確認保險桿貼緊牆。

!!! bug "常見錯誤：用 Supply current"
    Phoenix Tuner X 也看得到 **Supply current**（電池送進控制器的電流）。扭力和**定子電流 (Stator current)** 成正比，打滑電流指的是定子電流，電流限制也要設定在定子電流上（`StatorCurrentLimit`）。

!!! bug "常見錯誤：機器人斜著頂"
    只有一邊的保險桿貼著牆，機器人會被推得轉一下，輪子看起來像打滑。正面要完整貼著牆。

## 小練習

1. 用公式驗算：機器人 50 公斤、量到打滑電流 50 A，摩擦係數是多少？
2. 機器人加上了 10 公斤的機構，打滑電流會變大還是變小？要重新量嗎？
3. 為什麼測試時四個輪子都要朝正前方，不能是 X 字形？

??? question "第 1 題參考答案"
    推力 = 4 × 0.0194 × 50 × 6.75 ÷ 0.0508 ≈ 515 N。重量 = 50 × 9.81 ≈ 491 N。摩擦係數 ≈ 515 ÷ 491 ≈ 1.05。

??? question "第 2 題參考答案"
    變大：摩擦力 = 摩擦係數 × 重量，重量變大，輪子能推的力就變大，打滑需要更大的電流。摩擦係數本身不變（它只和輪子、地毯有關），所以可以用公式從新的重量算出新的打滑電流，但最準的還是重新量一次。

??? question "第 3 題參考答案"
    X 字形時，輪子推的方向和牆不垂直，推力有一部分會讓機器人往旁邊滑，或是四個輪子互相抵消。朝正前方時，四個輪子的推力都直接頂著牆，公式裡的「4 × 推力」才成立。
