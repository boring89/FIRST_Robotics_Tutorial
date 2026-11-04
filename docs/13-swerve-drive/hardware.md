# 硬體與常數

--8<-- "alpha-warning.md"

## 這頁學什麼

- 一個 Swerve 模組裡有哪些零件、各自負責什麼
- 齒輪比和單位換算：馬達圈數、輪子圈數、公尺
- 規劃 8 顆馬達和 4 個 CANcoder 的 CAN ID
- 用 Phoenix Tuner X 量測 CANcoder 的零點
- 把所有參數集中在 `Constants.java`

## 觀念

### 一個模組裡有什麼

```text
          ┌─────────────┐
          │  SparkMax   │──► NEO（轉向馬達）
          │  TalonFX    │──► Kraken X60（驅動馬達）
          └──────┬──────┘
                 │  轉向軸
          ┌──────┴──────┐
          │  CANcoder   │  ← 裝在轉向軸上，量輪子指向哪裡
          └──────┬──────┘
               ┌─┴─┐
               │ 輪 │
               └───┘
```

| 零件 | 做什麼 | 量到什麼 |
| --- | --- | --- |
| **Drive motor（驅動馬達）**：Kraken X60 + TalonFX | 讓輪子往前或往後滾 | 內建編碼器：輪子滾了幾圈、多快 |
| **Steer motor（轉向馬達）**：NEO + SparkMax | 讓整個輪子轉向 | 內建編碼器：**相對式**，開機時是 0 |
| **CANcoder** | 不驅動任何東西 | **絕對式**：輪子指向的角度，開機就知道 |

為什麼轉向需要兩個編碼器？NEO 的內建編碼器是相對式的（[第 6 章](../06-motors-sensors/encoders.md)），開機時一律從 0 開始算，它不知道輪子開機時指向哪裡。CANcoder 是絕對式的，開機就知道正確的角度。這一章先只用 CANcoder；[第 14 章](../14-swerve-tuning/onboard-pid.md) 會用 CANcoder 校正 NEO 的編碼器，讓 SparkMax 自己跑轉向的 PID。

### 齒輪比

Swerve 模組通常是買現成的（例如 SDS MK4i、WCP X2i、Thrifty Swerve），**齒輪比要查廠商的規格表**。這幾章用的例子是 SDS MK4i：

| | 齒輪比 | 意思 |
| --- | --- | --- |
| 驅動（L2） | 6.75 : 1 | 馬達轉 6.75 圈，輪子滾 1 圈 |
| 轉向 | 150/7 : 1（約 21.43 : 1） | 馬達轉 21.43 圈，輪子轉向 1 圈 |

輪子直徑 4 英寸（0.1016 公尺），所以輪子滾 1 圈走 π × 0.1016 ≈ 0.319 公尺。

**單位換算**的路線：

```text
驅動：馬達圈數 ÷ 6.75 → 輪子圈數 × 0.319 → 公尺
轉向：馬達圈數 ÷ 21.43 → 輪子轉了幾圈（1 圈 = 360 度）
```

CTRE 可以在設定裡用 **`SensorToMechanismRatio`** 告訴 TalonFX 齒輪比，之後 `getPosition()`、`getVelocity()` 讀到的就直接是**輪子**的圈數，速度控制的目標值也用輪子的圈數。REVLib 2027 沒有這個功能，SparkMax 讀到的一律是**馬達**的圈數（見[第 6 章](../06-motors-sensors/encoders.md#單位換算)）。

### 最高速度

Kraken X60 的空轉轉速大約是 6000 RPM（每秒 100 圈）。經過 6.75 : 1 的減速，輪子每秒約 14.8 圈，也就是每秒約 4.7 公尺。這是**理論上限**，實際上有摩擦和負重，所以程式裡的 `MAX_SPEED` 設低一點（4.5 m/s）。

機器人原地轉圈時，輪子繞著機器人中心跑。模組離中心的距離是 √(0.289² + 0.289²) ≈ 0.409 公尺，所以最高旋轉速度是 4.5 ÷ 0.409 ≈ 11 弧度 / 秒（每秒約 1.75 圈）。

### 估計驅動的 Feedforward

[第 8 章](../08-control/feedforward.md) 的 kV 是「每多 1 單位的速度，要多給幾伏特」。用馬達規格可以先估一個值：

```text
12 V 時輪子每秒轉 14.8 圈 → kV ≈ 12 ÷ 14.8 ≈ 0.81 V / (輪子每秒轉數)
```

這只是起點，[第 14 章](../14-swerve-tuning/tuning.md) 會實際調整。

### CAN ID 的規劃

照[第 3 章](../03-setup/firmware-can.md#can-id-的規劃) 的規則，底盤用 1～9、感測器用 40～49：

| 模組 | 驅動 TalonFX | 轉向 SparkMax | CANcoder |
| --- | --- | --- | --- |
| 左前 (Front left) | 1 | 2 | 41 |
| 右前 (Front right) | 3 | 4 | 42 |
| 左後 (Back left) | 5 | 6 | 43 |
| 右後 (Back right) | 7 | 8 | 44 |

TalonFX 和 SparkMax 是不同廠商的裝置，就算 ID 一樣也不會衝突，但**每個 ID 只用一次**比較不會搞混。這幾章所有裝置都接在 `CAN_S0`。

### 模組的位置

`SwerveDriveKinematics` 需要知道每個模組在機器人的哪裡（[第 7 章](../07-drivetrain/swerve.md#kinematics運動學)）。量**模組轉向軸的中心**之間的距離：

- **Track width**：左右兩個模組的距離
- **Wheel base**：前後兩個模組的距離

| 模組 | X（往前為正） | Y（往左為正） |
| --- | --- | --- |
| 左前 | +wheel base / 2 | +track width / 2 |
| 右前 | +wheel base / 2 | −track width / 2 |
| 左後 | −wheel base / 2 | +track width / 2 |
| 右後 | −wheel base / 2 | −track width / 2 |

### CANcoder 的零點

CANcoder 裝上去的角度是隨機的，所以要告訴它：「輪子朝正前方時，你讀到的應該是 0」。這個值叫 **Magnet offset（磁鐵偏移）**。

## 步驟

### 量測 CANcoder 的零點

1. 打開 **Phoenix Tuner X**，連上機器人
2. 選一個 CANcoder，在 **Config** 頁面把 **Magnet Offset** 設為 0，按 **Apply**
3. 用手把四個輪子都轉到**正前方**。用一根長直尺靠著同一側的兩個輪子，確認它們平行
4. 每個模組的**斜齒輪**都朝向同一側（例如都朝機器人的左邊）。朝向哪一側都可以，但四個要一樣，不然有的輪子會倒著滾
5. 在 **Plot** 或裝置頁面讀 **Absolute Position**（單位：圈）
6. **零點 = −讀到的值**。例如讀到 0.254，零點就是 −0.254
7. 四個 CANcoder 都量完，填進 `Constants.java`

!!! warning "注意：不要按 Tuner X 的 Zero 按鈕就結束"
    Tuner X 有直接歸零的按鈕，但我們的程式每次開機都會用 `Constants.java` 裡的零點**覆蓋** CANcoder 的設定。只在 Tuner X 歸零、沒有改程式，重新開機後零點就會被蓋回去。

## 範例

把所有參數集中在 `Constants.java`。每個模組的設定用 **`record`** 包在一起：`record` 是 Java 16 起的語法，用一行宣告一個「只裝資料」的類別，Java 會自動產生建構子和讀取的方法（例如 `driveId()`）。

```java title="src/main/java/first/robot/Constants.java"
package first.robot;

import com.ctre.phoenix6.signals.InvertedValue;
import org.wpilib.math.geometry.Translation2d;

public final class Constants {
  public static final class OperatorConstants {
    public static final int DRIVER_CONTROLLER_PORT = 0;
  }

  /**
   * 一個 Swerve 模組的設定。
   *
   * @param name 名稱，用在 Telemetry
   * @param driveId 驅動馬達（TalonFX）的 CAN ID
   * @param steerId 轉向馬達（SparkMax）的 CAN ID
   * @param encoderId CANcoder 的 CAN ID
   * @param encoderOffsetRotations CANcoder 的零點，單位：圈
   * @param location 模組相對於機器人中心的位置，單位：公尺（X 往前、Y 往左）
   */
  public record ModuleConstants(
      String name,
      int driveId,
      int steerId,
      int encoderId,
      double encoderOffsetRotations,
      Translation2d location) {}

  public static final class SwerveConstants {
    // ---------- 尺寸 ----------
    /** 左右兩個模組中心的距離，單位：公尺。 */
    public static final double TRACK_WIDTH_METERS = 0.578;
    /** 前後兩個模組中心的距離，單位：公尺。 */
    public static final double WHEEL_BASE_METERS = 0.578;
    public static final double WHEEL_DIAMETER_METERS = 0.1016; // 4 英寸
    public static final double WHEEL_CIRCUMFERENCE_METERS = Math.PI * WHEEL_DIAMETER_METERS;

    // ---------- 齒輪比（馬達轉幾圈，輪子轉一圈）----------
    public static final double DRIVE_GEAR_RATIO = 6.75;
    public static final double STEER_GEAR_RATIO = 150.0 / 7.0;

    // ---------- 速度上限 ----------
    /** 底盤的最高速度，單位：公尺 / 秒。 */
    public static final double MAX_SPEED = 4.5;
    /** 底盤的最高旋轉速度，單位：弧度 / 秒。 */
    public static final double MAX_ANGULAR_SPEED =
        MAX_SPEED / Math.hypot(TRACK_WIDTH_METERS / 2, WHEEL_BASE_METERS / 2);

    // ---------- 驅動馬達（TalonFX）----------
    public static final InvertedValue DRIVE_INVERTED = InvertedValue.CounterClockwise_Positive;
    public static final double DRIVE_CURRENT_LIMIT = 60;
    public static final double DRIVE_KS = 0.2; // 伏特
    public static final double DRIVE_KV = 0.8; // 伏特 / (輪子每秒轉數)
    public static final double DRIVE_KP = 0.1;

    // ---------- 轉向馬達（SparkMax）----------
    public static final boolean STEER_INVERTED = true;
    public static final int STEER_CURRENT_LIMIT = 30;
    public static final double STEER_KP = 6.0; // 伏特 / 弧度
    public static final double STEER_KD = 0.0;

    // ---------- 四個模組 ----------
    public static final ModuleConstants FRONT_LEFT =
        new ModuleConstants(
            "FrontLeft", 1, 2, 41, -0.254,
            new Translation2d(WHEEL_BASE_METERS / 2, TRACK_WIDTH_METERS / 2));
    public static final ModuleConstants FRONT_RIGHT =
        new ModuleConstants(
            "FrontRight", 3, 4, 42, 0.137,
            new Translation2d(WHEEL_BASE_METERS / 2, -TRACK_WIDTH_METERS / 2));
    public static final ModuleConstants BACK_LEFT =
        new ModuleConstants(
            "BackLeft", 5, 6, 43, 0.412,
            new Translation2d(-WHEEL_BASE_METERS / 2, TRACK_WIDTH_METERS / 2));
    public static final ModuleConstants BACK_RIGHT =
        new ModuleConstants(
            "BackRight", 7, 8, 44, -0.081,
            new Translation2d(-WHEEL_BASE_METERS / 2, -TRACK_WIDTH_METERS / 2));
  }

  private Constants() {}
}
```

- 四個零點（`-0.254`、`0.137`…）只是範例，**一定要換成你們量到的值**
- `DRIVE_INVERTED`、`STEER_INVERTED`：馬達往哪個方向轉算「正」。不同型號的模組不一樣（例如 MK4i 的轉向馬達要反轉），先照廠商文件填，[第一次上電](first-drive.md) 時再確認
- `MAX_ANGULAR_SPEED` 用公式算，改了尺寸或最高速度會自動跟著變

## 常見錯誤

!!! bug "常見錯誤：齒輪比抄錯"
    同一款模組有好幾種驅動齒輪比（例如 MK4i 有 L1、L2、L3）。抄錯的話，機器人實際的速度和程式以為的不一樣，Odometry 算出來的距離也會錯。**看模組上的齒輪或訂單確認**，不要只看網路上的範例。

!!! bug "常見錯誤：量零點時斜齒輪朝向不一致"
    四個輪子都朝前，但有一個的斜齒輪朝向另一側：那個輪子其實轉了 180 度。往前開時，它會往反方向滾，機器人歪斜或在原地抖動。

!!! bug "常見錯誤：零點的正負號弄反"
    零點是**讀到的值的負數**。填成正的，輪子朝前時會讀到兩倍的值，轉向全錯。

## 小練習

1. 如果你們的模組是 L3 齒輪比（6.12 : 1），理論最高速度大約是多少 m/s？`DRIVE_KV` 大約要改成多少？
2. 機器人的模組是長方形排列：左右距離 0.5 公尺、前後距離 0.6 公尺。寫出四個模組的 `Translation2d`。
3. 為什麼 CANcoder 要裝在**轉向軸**上，而不是裝在轉向馬達上？

??? question "第 1 題參考答案"
    輪子每秒最多轉 100 ÷ 6.12 ≈ 16.3 圈，乘上周長 0.319 公尺，大約 **5.2 m/s**。

    `DRIVE_KV` ≈ 12 ÷ 16.3 ≈ **0.74** V / (輪子每秒轉數)。齒輪比越小，輪子轉得越快，同樣的速度需要的電壓越少，kV 就越小。

??? question "第 2 題參考答案"
    `TRACK_WIDTH_METERS = 0.5`、`WHEEL_BASE_METERS = 0.6`：

    ```java
    new Translation2d(0.3, 0.25)    // 左前
    new Translation2d(0.3, -0.25)   // 右前
    new Translation2d(-0.3, 0.25)   // 左後
    new Translation2d(-0.3, -0.25)  // 右後
    ```

    X 是前後，用 wheel base 的一半；Y 是左右，用 track width 的一半。

??? question "第 3 題參考答案"
    轉向馬達和輪子之間有 21.43 : 1 的減速。裝在馬達上的話，馬達轉一圈只代表輪子轉了約 17 度，讀值一圈就會繞回來，沒辦法知道輪子真正的方向。裝在轉向軸上，**CANcoder 轉一圈就是輪子轉一圈**，讀到的就是輪子的角度。
