# 模擬 Swerve 模組

--8<-- "alpha-warning.md"

## 這頁學什麼

- 用 `DCMotorSim` 描述「電壓 → 轉速、位置」
- TalonFX、SparkMax、CANcoder 各自的模擬寫法
- 用模組的速度模擬 IMU
- 模擬的限制

## 觀念

### `DCMotorSim`：馬達的物理模型

**`DCMotorSim`** 知道一顆馬達接上齒輪和負載之後，給它多少電壓會轉多快。它需要三個資訊：

| 參數 | 意思 | 這裡的值 |
| --- | --- | --- |
| 馬達 | 哪一種馬達，幾顆 | `DCMotor.getKrakenX60(1)`、`DCMotor.getNEO(1)` |
| 齒輪比 | 馬達轉幾圈，輸出轉一圈 | `DRIVE_GEAR_RATIO`、`STEER_GEAR_RATIO` |
| 轉動慣量 (Moment of inertia) | 輸出端有多「重」、多難轉動，單位 kg·m² | 驅動 0.03、轉向 0.004 |

每 20 ms 呼叫一次：

```java
physics.setInputVoltage(電壓);
physics.update(0.02);
physics.getAngularPosition();   // 輸出端的位置，單位：弧度
physics.getAngularVelocity();   // 輸出端的速度，單位：弧度 / 秒
```

建立 `DCMotorSim` 要先用 `Models` 建立一個 **Plant（受控體）** 模型。WPILib 2027 用 `Models.singleJointedArmFromPhysicalConstants()` 建立「馬達 + 齒輪 + 轉動慣量」的模型。名字裡有「arm」，但這個模型**沒有重力**，數學上和任何一個用馬達轉動的機構都一樣，所以輪子也可以用它。

### 轉動慣量怎麼估

- **轉向**：只要轉動輪子和模組的轉向部分，很輕。0.004 kg·m² 是常見的估計值
- **驅動**：輪子要推動**四分之一台機器人**。假設機器人 50 公斤，每個輪子負責 12.5 公斤，輪子半徑 0.0508 公尺：12.5 × 0.0508² ≈ 0.03 kg·m²

這些值不需要很精確，模擬只是要讓機器人的反應「大概合理」。

### 三種廠商裝置的模擬

| 裝置 | 取得模擬狀態 | 要告訴它什麼 | 從它讀什麼 |
| --- | --- | --- | --- |
| TalonFX | `driveMotor.getSimState()` | 電池電壓、**轉子**的位置和速度 | 馬達的輸出電壓 `getMotorVoltage()` |
| SparkMax | `new SparkMaxSim(steerMotor, DCMotor.getNEO(1))` | 馬達的轉速（RPM）、電池電壓 | 輸出比例 `getAppliedOutput()` |
| CANcoder | `encoder.getSimState()` | 磁鐵的位置（扣掉零點之前） | （不用讀） |

注意單位：

- TalonFX 要的是**轉子**（馬達）的圈數，不是輪子的。它會自己套用 `SensorToMechanismRatio` 換算成輪子的圈數
- SparkMax 要的是**馬達**的 RPM
- CANcoder 的 `setRawPosition()` 是**還沒加上零點**的值。我們希望加上零點之後讀到的是輪子的角度，所以要傳「輪子角度 − 零點」

### IMU 怎麼模擬

模擬裡沒有真正的機器人在轉，IMU 不知道機器人的朝向。我們用四個模組**實際的**速度，透過 Kinematics 算出機器人轉得多快，再一直累加：

```text
朝向 += omega × 0.02 秒
```

再用 `OnboardIMUSim.setYaw()` 告訴模擬的 IMU。之後 `imu.getRotation2d()` 就會讀到這個朝向。

## 範例

### ModuleSim

```java title="src/main/java/first/robot/subsystems/ModuleSim.java"
package first.robot.subsystems;

import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.sim.CANcoderSimState;
import com.ctre.phoenix6.sim.TalonFXSimState;
import com.revrobotics.sim.SparkMaxSim;
import com.revrobotics.spark.SparkMax;
import first.robot.Constants.SwerveConstants;
import org.wpilib.math.system.DCMotor;
import org.wpilib.math.system.Models;
import org.wpilib.simulation.DCMotorSim;
import org.wpilib.system.RobotController;

/** 模擬一個 Swerve 模組：用馬達模型算出輪子怎麼轉，再告訴 TalonFX、SparkMax、CANcoder。 */
public class ModuleSim {
  // 轉動慣量，單位：kg·m²。驅動輪要推動四分之一台機器人，所以比轉向大很多
  private static final double DRIVE_INERTIA = 0.03;
  private static final double STEER_INERTIA = 0.004;

  private final DCMotor driveGearbox = DCMotor.getKrakenX60(1);
  private final DCMotor steerGearbox = DCMotor.getNEO(1);

  private final DCMotorSim drivePhysics =
      new DCMotorSim(
          Models.singleJointedArmFromPhysicalConstants(
              driveGearbox, DRIVE_INERTIA, SwerveConstants.DRIVE_GEAR_RATIO),
          driveGearbox);
  private final DCMotorSim steerPhysics =
      new DCMotorSim(
          Models.singleJointedArmFromPhysicalConstants(
              steerGearbox, STEER_INERTIA, SwerveConstants.STEER_GEAR_RATIO),
          steerGearbox);

  private final TalonFXSimState driveSim;
  private final SparkMaxSim steerSim;
  private final CANcoderSimState encoderSim;
  private final double encoderOffsetRotations;

  public ModuleSim(TalonFX driveMotor, SparkMax steerMotor, CANcoder encoder, double encoderOffsetRotations) {
    driveSim = driveMotor.getSimState();
    steerSim = new SparkMaxSim(steerMotor, steerGearbox);
    encoderSim = encoder.getSimState();
    this.encoderOffsetRotations = encoderOffsetRotations;
    updateEncoder();
  }

  /** 每 20 ms 呼叫一次。 */
  public void update(double dt) {
    double battery = RobotController.getBatteryVoltage();

    // 驅動：TalonFX 輸出的電壓 → 馬達模型 → 把轉子的位置和速度告訴 TalonFX
    driveSim.setSupplyVoltage(battery);
    drivePhysics.setInputVoltage(driveSim.getMotorVoltage());
    drivePhysics.update(dt);
    double driveRotorRotations =
        drivePhysics.getAngularPosition() / (2 * Math.PI) * SwerveConstants.DRIVE_GEAR_RATIO;
    double driveRotorRps =
        drivePhysics.getAngularVelocity() / (2 * Math.PI) * SwerveConstants.DRIVE_GEAR_RATIO;
    driveSim.setRawRotorPosition(driveRotorRotations);
    driveSim.setRotorVelocity(driveRotorRps);

    // 轉向：SparkMax 輸出的比例 × 電池電壓 → 馬達模型 → 把馬達轉速告訴 SparkMax
    steerPhysics.setInputVoltage(steerSim.getAppliedOutput() * battery);
    steerPhysics.update(dt);
    double steerMotorRpm =
        steerPhysics.getAngularVelocity() / (2 * Math.PI) * 60 * SwerveConstants.STEER_GEAR_RATIO;
    steerSim.iterate(steerMotorRpm, battery, dt);

    updateEncoder();
  }

  /** CANcoder 裝在轉向軸上，讀到的就是輪子的角度（再扣掉零點）。 */
  private void updateEncoder() {
    double moduleRotations = steerPhysics.getAngularPosition() / (2 * Math.PI);
    encoderSim.setRawPosition(moduleRotations - encoderOffsetRotations);
  }
}
```

`update()` 每次做三件事：

1. **驅動**：TalonFX 決定輸出多少電壓 → 物理模型算出輪子轉了多少 → 換算成**轉子**的圈數交回給 TalonFX
2. **轉向**：SparkMax 的輸出比例乘上電池電壓 → 物理模型 → 換算成**馬達**的 RPM 交回給 SparkMax。`iterate()` 會執行 SparkMax 韌體的 PID，所以[第 14 章](../14-swerve-tuning/onboard-pid.md) 的轉向 PID 在模擬裡也會跑
3. **CANcoder**：輪子的角度扣掉零點，交給模擬的 CANcoder

建構子的最後也呼叫一次 `updateEncoder()`，讓 CANcoder 一開始就有讀值。

### SwerveModule

加上一個 `ModuleSim` 欄位，只在模擬時建立：

```java
/** 只有在模擬時才會建立，實際的機器人上是 null。 */
private final ModuleSim sim;
```

在建構子裡，**`syncSteerEncoder()` 之前**建立它：

```java
configureEncoder(constants.encoderOffsetRotations());

// 要在 syncSteerEncoder() 之前建立，模擬的 CANcoder 才會有讀值
sim = RobotBase.isSimulation()
    ? new ModuleSim(driveMotor, steerMotor, encoder, constants.encoderOffsetRotations())
    : null;
syncSteerEncoder();
```

`條件 ? A : B` 是 Java 的**三元運算子**：條件成立時是 A，否則是 B。`sim` 是 `final`，只能設定一次，所以用這個寫法一次決定它的值。

加上給 `Drivetrain` 呼叫的方法：

```java
/** 只在模擬時呼叫：更新馬達模型。 */
public void simulationPeriodic() {
  sim.update(0.02);
}
```

`RobotBase` 在 `org.wpilib.framework.RobotBase`。

### Drivetrain

加上模擬的朝向和 `simulationPeriodic()`：

```java
private double simYawRadians = 0.0;

@Override
public void simulationPeriodic() {
  for (SwerveModule module : modules) {
    module.simulationPeriodic();
  }
  // 模擬的 IMU：用四個模組實際的速度，算出機器人轉得多快，再累加成朝向
  ChassisVelocities actual = kinematics.toChassisVelocities(getModuleVelocities());
  simYawRadians += actual.omega * 0.02;
  OnboardIMUSim.setYaw(simYawRadians);
}
```

`OnboardIMUSim` 在 `org.wpilib.simulation.OnboardIMUSim`。

### 跑起來的樣子

用程式讓模擬的機器人依序做幾個動作，印出四個模組的速度和角度（左前、右前、左後、右後）：

```text
往前 2 m/s 1 秒      [2.06 m/s 0.0°] [2.06 m/s 0.0°] [2.06 m/s -0.1°] [2.06 m/s 0.1°]
改成往左 0.2 秒後    [1.83 m/s 89.3°] [1.83 m/s 89.3°] [0.83 m/s -89.4°] [1.83 m/s 89.4°]
往左 2 m/s 1 秒      [2.06 m/s 90.0°] [2.06 m/s 90.0°] [-2.06 m/s -90.1°] [2.06 m/s 90.1°]
原地轉 2 rad/s       [0.88 m/s 135.0°] [0.88 m/s 45.0°] [0.88 m/s -135.1°] [-0.88 m/s 135.1°]
```

- 改成往左之後 0.2 秒，四個輪子都轉到 90 度附近了：SparkMax 的轉向 PID 在模擬裡也在運作
- 左後的模組因為 `optimize`，轉到 −90 度、倒著滾（[第 14 章](../14-swerve-tuning/optimize.md)）
- 原地轉時，四個輪子排成一個圓。右後的 −0.88 m/s、135 度，和 0.88 m/s、−45 度是同一件事

## 模擬的限制

模擬很好用，但它不是真的機器人。這個簡單的模型有幾個已知的限制：

!!! warning "注意：模擬裡沒有摩擦力"
    上面往前 2 m/s 時，實際速度是 **2.06 m/s**，比目標快一點。因為真正的機器人有摩擦力，`DRIVE_KS` 是用來克服摩擦力的電壓；模擬裡沒有摩擦力，多出來的 kS 就讓輪子轉得比較快。**在模擬裡調出來的 Feedforward 不能直接用在真的機器人上**。

!!! warning "注意：四個模組是各自模擬的"
    這個模型把四個模組當成**互相獨立**的四個輪子，沒有「它們裝在同一台機器人上」的限制。真正的機器人是一個整體，四個輪子的速度方向不一致時，輪子會打滑，機器人不會亂轉。

    模擬裡，突然從往前改成往左時，左後的模組要先把速度從 +2 降到 0、再加速到 −2（倒著滾），這段時間它和其他三個模組不一致。Kinematics 會把這個不一致算成**機器人在旋轉**，所以模擬的朝向會偏掉：在上面的測試裡，往前改往左之後，朝向偏了 **20 多度**。

    這是模擬的誤差，不是程式的錯。想要更真實的模擬，可以用[延伸學習](further.md) 介紹的物理模擬函式庫。

!!! info "模擬裡可以相信的東西"
    - 方向對不對：推搖桿往前，模擬的機器人往前走
    - 程式的邏輯：Command 有沒有在對的時候啟動、`optimize` 有沒有作用、Odometry 有沒有在更新
    - 大概的反應：轉向大約多快到位、加速大約要多久

    不能相信的：精確的 PID、Feedforward 參數，和任何跟摩擦力、打滑、碰撞有關的結果。

## 常見錯誤

!!! bug "常見錯誤：交給 TalonFX 輪子的圈數"
    `setRawRotorPosition()` 要的是**轉子**的圈數。交給它輪子的圈數，TalonFX 會再除以 6.75，模擬的機器人只走了 1/6.75 的距離。

!!! bug "常見錯誤：CANcoder 忘了扣掉零點"
    `setRawPosition()` 直接傳輪子的角度，CANcoder 會再加上零點，讀到的角度就偏了 `encoderOffsetRotations` 那麼多。每個模組的零點不一樣，四個輪子會指向不同的方向。

!!! bug "常見錯誤：在 `syncSteerEncoder()` 之後才建立 `ModuleSim`"
    校正的時候模擬的 CANcoder 還沒有讀值，讀到 0（加上零點之後也不是輪子真正的角度），NEO 的編碼器從一開始就偏了。

## 小練習

1. 把 `DRIVE_INERTIA` 改成 0.3（十倍），模擬裡加速的感覺有什麼不同？
2. 在模擬裡把 `STEER_KP` 改成 3.0，看看轉向會發生什麼事。
3. 為什麼 IMU 的模擬要用模組**實際的**速度，而不是 `driveRobotRelative` 收到的目標速度？

??? question "第 1 題參考答案"
    轉動慣量越大，同樣的電壓要花更久才能加速到目標速度，模擬的機器人會像是「很重」，起步和停下來都慢很多。這相當於機器人重了十倍。

??? question "第 3 題參考答案"
    真正的 IMU 量到的是機器人**實際**的轉動，不是程式想要的轉動。用目標速度的話，就算轉向 PID 很差、輪子根本沒轉到位，模擬的朝向還是完美的，模擬就沒辦法幫你發現問題。
