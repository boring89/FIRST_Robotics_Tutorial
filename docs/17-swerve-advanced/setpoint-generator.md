# Swerve Setpoint Generator

--8<-- "alpha-warning.md"

## 這頁學什麼

- 急轉方向時，四個模組為什麼會互相拉扯
- 254 的 Setpoint Generator 在做什麼：把整台機器人的變化**一起**縮小到做得到的範圍
- 用 PathPlannerLib 的 `SwerveSetpointGenerator`
- 在程式裡建立 `RobotConfig`

## 觀念

### 問題：目標是「瞬間」的

[第 13 章的 `driveRobotRelative()`](../13-swerve-drive/drivetrain.md) 每次都直接把搖桿的目標交給 Kinematics。機器人以 3 m/s 往前開時，駕駛突然把搖桿推往左邊，Kinematics 算出的目標是「四個輪子立刻朝左、3 m/s」。但實際上：

- **轉向要時間**：輪子要轉 90 度，大約 0.1～0.2 秒
- **加速有極限**：馬達的扭力、輪子和地毯的摩擦力都有上限，速度不能瞬間改變
- **四個模組的反應不一樣**：有的輪子 `optimize` 之後要反轉，要先減速到 0 再加速；有的只要轉向

這段時間裡，四個輪子各做各的，方向和速度互相不一致，輪子會打滑、互相拉扯，機器人甚至會往錯的方向滑一下。[第 15 章的模擬](../15-swerve-sim/module-sim.md#模擬的限制) 看到的朝向偏移就是這個原因。

### 254 的做法

FRC 254 隊在 2022 賽季機器人的公開程式碼（[FRC-2022-Public](https://github.com/Team254/FRC-2022-Public)）裡，公開了他們的 **[Swerve Setpoint Generator](https://github.com/Team254/FRC-2022-Public/blob/main/src/main/java/com/team254/lib/swerve/SwerveSetpointGenerator.java)**，PathPlannerLib 的版本就是根據它改寫的。它的想法是：

> 不要直接跳到目標。每 20 ms 只往目標前進「四個模組都做得到」的那一段。

```text
上一次的速度 ───────────────────────────► 目標速度
             └── 這 20 ms 只走到這裡 ──┘
                 （s 倍，0 ≤ s ≤ 1）
```

每次迴圈，它找出一個比例 s：

1. **轉向的限制**：每個輪子這 20 ms 最多只能轉「最高轉向速度 × 0.02 秒」那麼多
2. **加速的限制**：每個輪子的速度變化不能超過馬達扭力和摩擦力能做到的
3. 取四個模組裡**最嚴格**的那個 s，**整台機器人一起**往目標前進 s 倍

254 的原始版本用**固定**的最高加速度和最高轉向速度當作限制，用 Regula falsi（試位法）這種求根的方法，找出每個模組剛好碰到限制的 s。PathPlannerLib 的版本把加速度的限制改成用**馬達扭力和輪子的摩擦力**計算，所以需要知道機器人的重量和馬達型號。

因為四個模組用的是同一個 s，它們的變化永遠是一致的：機器人整體的運動是「上一次的速度」和「目標」之間的某個點，所以不會互相拉扯。它也會判斷輪子什麼時候要反轉，並且自己處理 [`discretize`](../14-swerve-tuning/driver-input.md#discretize一邊走一邊轉) 和[限速](../13-swerve-drive/drivetrain.md#desaturate超速時等比例縮小)。

### 它需要知道什麼

Setpoint Generator 要知道機器人的物理參數，才能算出「做得到」的範圍。PathPlannerLib 用和 [PathPlanner 的 Robot Config](../16-swerve-auto/pathplanner.md#swerve-的-robot-config) 一樣的 **`RobotConfig`**：

| 參數 | 用途 |
| --- | --- |
| 機器人重量、轉動慣量 | 同樣的力，越重加速越慢 |
| 輪子半徑、齒輪比、驅動馬達、電流限制 | 算出每個輪子最多能出多少力 |
| 輪子的摩擦係數 | 超過摩擦力，輪子就會打滑 |
| 模組的位置 | Kinematics |
| **最高轉向速度** | 每 20 ms 輪子最多能轉多少（另外傳入） |

機器人移動時還有一個限制：輪子轉向太快，機器人會像急轉彎的車子一樣**側滑**。Generator 用摩擦係數和重量算出「不會側滑的最大轉向」，速度越快越小。以 2.3 m/s 計算，大約是每 20 ms 轉 5.9 度，比轉向馬達的極限（約每 20 ms 25 度）還嚴格。所以**移動中真正起作用的通常是摩擦力的限制**，最高轉向速度主要影響原地轉向的時候。

最高轉向速度可以用馬達規格估：NEO 空轉約 5676 RPM，經過 21.43 : 1 的減速，輪子每秒最多轉約 4.4 圈（約 27.7 rad/s）。打八折留一點餘裕。

!!! warning "注意：PathPlannerLib 2027 alpha 的已知問題"
    PathPlannerLib 2027.0.0-alpha-4.1 的 `SwerveSetpointGenerator` 是從 2026 版本改過來的。2026 的 `optimize()` 和 `desaturateWheelSpeeds()` 會**直接修改**傳入的物件，2027 改成**回傳新的物件**（[第 14 章](../14-swerve-tuning/optimize.md#這兩個方法都會回傳新的物件)），但它的程式裡有兩個地方沒有接住回傳值。

    在模擬裡測到的影響是：輪子的速度可能**略為超過** `MAX_SPEED`（最高約 4.66 m/s，接近馬達的真正極速），其他行為都正常。撰寫本章時（2026 年 11 月），PathPlanner 的 2027 分支還沒有修正，已回報在 [PathPlanner issue #1185](https://github.com/mjansen4857/pathplanner/issues/1185)。更新 PathPlannerLib 之前，可以先看這個 issue 有沒有關閉。

## 範例

### Constants

在 `SwerveConstants` 加上 Setpoint Generator 需要的參數（`DCMotor` 在 `org.wpilib.math.system.DCMotor`）：

```java
// ---------- Setpoint generator ----------
public static final double ROBOT_MASS_KG = 50.0;
/** 機器人的轉動慣量，單位：kg·m²。 */
public static final double ROBOT_MOI = 6.0;
/** 輪子和地毯的摩擦係數。 */
public static final double WHEEL_COF = 1.2;
/** 輪子轉向的最高速度，單位：弧度 / 秒。NEO 空轉轉速 ÷ 齒輪比，打八折。 */
public static final double MAX_STEER_VELOCITY =
    DCMotor.getNEO(1).freeSpeed / STEER_GEAR_RATIO * 0.8;
```

`ROBOT_MOI` 可以先用長方體的公式估：質量 × (長² + 寬²) ÷ 12。50 公斤、0.8 × 0.8 公尺的機器人大約是 5.3，再加上集中在外圍的保險桿，取 6.0。這些值**要和 PathPlanner 應用程式的 Settings 一樣**。

### Drivetrain

用 `Constants` 建立 `RobotConfig`，不用讀 PathPlanner 的設定檔，在模擬和還沒設定 PathPlanner 時也能用：

```java
/** 用 Constants 建立的機器人設定，給 Setpoint generator 用。 */
private static final RobotConfig ROBOT_CONFIG =
    new RobotConfig(
        SwerveConstants.ROBOT_MASS_KG,
        SwerveConstants.ROBOT_MOI,
        new ModuleConfig(
            SwerveConstants.WHEEL_DIAMETER_METERS / 2,
            SwerveConstants.MAX_SPEED,
            SwerveConstants.WHEEL_COF,
            DCMotor.getKrakenX60(1),
            SwerveConstants.DRIVE_GEAR_RATIO,
            SwerveConstants.DRIVE_CURRENT_LIMIT,
            1),
        SwerveConstants.FRONT_LEFT.location(),
        SwerveConstants.FRONT_RIGHT.location(),
        SwerveConstants.BACK_LEFT.location(),
        SwerveConstants.BACK_RIGHT.location());

private final SwerveSetpointGenerator setpointGenerator =
    new SwerveSetpointGenerator(ROBOT_CONFIG, SwerveConstants.MAX_STEER_VELOCITY);
private SwerveSetpoint previousSetpoint;
```

`ModuleConfig` 的最後一個參數 `1` 是每個模組有幾顆驅動馬達。

`driveRobotRelative()` 改成交給 Setpoint Generator。**原本的 `discretize` 和 `desaturateWheelVelocities` 都拿掉**，它會自己處理：

```java
public void driveRobotRelative(ChassisVelocities velocities) {
  // Setpoint generator 會自己 discretize 和限速，這裡不用再做
  previousSetpoint = setpointGenerator.generateSetpoint(previousSetpoint, velocities, 0.02);
  SwerveModuleVelocity[] targets = previousSetpoint.moduleStates();
  for (int i = 0; i < modules.length; i++) {
    modules[i].setTarget(targets[i]);
  }
  Telemetry.log("Drivetrain/TargetVelocities", targets);
}
```

`generateSetpoint` 收的是**上一次的 setpoint**，不是量到的速度。它假設機器人有照著上一次的 setpoint 動，從那裡往目標前進一步。

所以停下來之後，要讓它從**實際的**狀態重新開始，不然下一次啟動時，它會以為機器人還在用停止前的速度移動：

```java
/** 從目前實際的速度重新開始，例如停止之後。 */
private void resetSetpoint() {
  previousSetpoint =
      new SwerveSetpoint(
          getRobotRelativeVelocities(),
          getModuleVelocities(),
          DriveFeedforwards.zeros(modules.length));
}
```

在建構子建立 Pose estimator 之後呼叫一次 `resetSetpoint()`，`stop()` 的最後也呼叫一次。開車的 Command 結束時（包括機器人停用時被取消）都會呼叫 `stop()`，所以每次重新啟用都會從實際的狀態開始。

完整的 `Drivetrain` 在[下一頁](acceleration-ff.md#drivetrain)，同時加上了加速度 Feedforward。

### 在模擬裡比較

以 3 m/s 往前開 1 秒，再突然改成往左 3 m/s（場地座標的速度，每 0.1 秒記錄一次）：

| 改方向後 | 第 16 章：vx, vy | 第 16 章：朝向 | Setpoint Generator：vx, vy | Setpoint Generator：朝向 |
| --- | --- | --- | --- | --- |
| 0.1 秒 | 1.17, **−1.14** | −3.3 度 | 2.70, 0.00 | −0.1 度 |
| 0.2 秒 | −0.06, −0.66 | −14.1 度 | 1.93, 0.46 | −0.1 度 |
| 0.5 秒 | 0.12, 1.22 | −49.4 度 | 0.00, 2.91 | 0.0 度 |
| 1.0 秒 | 0.00, 3.05 | −63.2 度 | 0.00, 3.05 | 0.0 度 |

- **第 16 章**：改方向的瞬間，機器人往**右**滑（vy 是負的），朝向一路偏到 −63 度
- **Setpoint Generator**：往前的速度平順地降下來，往左的速度同時升上去，朝向幾乎不變

模擬把四個模組當成各自獨立的，所以第 16 章那一欄的偏移比真的機器人誇張（[模擬的限制](../15-swerve-sim/module-sim.md#模擬的限制)）。但「四個模組互相拉扯」這件事在真的機器人上一樣會發生，只是表現成輪子打滑和機器人晃動。

## 常見錯誤

!!! bug "常見錯誤：傳入量到的速度"
    寫成 `generateSetpoint(目前量到的狀態, velocities, 0.02)`：量到的速度有雜訊、也有延遲，每次都從量到的值出發，限制會失準，機器人反應變慢。照 PathPlannerLib 的說明，**傳入上一次的 setpoint**，只在停止後重新開始。

!!! bug "常見錯誤：還保留 `discretize`"
    Setpoint Generator 裡面已經做了 `discretize`，外面再做一次，同樣的修正就做了兩次。

!!! bug "常見錯誤：`MAX_STEER_VELOCITY` 設得比實際快"
    Generator 以為輪子轉得很快，給的目標角度變化太大，轉向跟不上，又回到互相拉扯的情況。寧可設慢一點。

## 小練習

1. 把 `MAX_STEER_VELOCITY` 改成原本的一半，在模擬裡重做「往前改往左」的測試，機器人的反應有什麼不同？
2. 把 `WHEEL_COF` 改成 0.5（很滑的地面），從 0 加速到全速要多久？為什麼？
3. 為什麼停止之後要呼叫 `resetSetpoint()`？如果不呼叫，機器人被推了一段距離之後重新啟用，會發生什麼事？

??? question "第 2 題參考答案"
    摩擦係數變小，輪子能推動地面的最大力變小（最大力 ≈ 摩擦係數 × 重量）。Generator 會把加速度限制在不會打滑的範圍，所以加速會**慢很多**。這也是它的好處之一：在打滑之前就先限制，Odometry 也比較準。

??? question "第 3 題參考答案"
    Generator 以為機器人還在用「上一次的 setpoint」的速度和角度移動。如果停止前輪子朝左、速度 2 m/s，重新啟用時它會從「朝左、2 m/s」開始慢慢減速，即使機器人其實是靜止的，而且輪子可能已經被推到別的角度。這段時間機器人會先往左衝一下。從實際的狀態重新開始，就不會有這個問題。
