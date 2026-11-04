# Swerve 入門

--8<-- "alpha-warning.md"

## 這頁學什麼

- Swerve 模組的構造和運作原理
- `ChassisVelocities`：用「機器人要怎麼動」描述底盤的速度
- Kinematics（運動學）：從機器人的速度算出每個模組的速度和角度
- Robot-relative 和 Field-relative（場地座標）的差別
- 寫 Swerve 程式有哪些現成的工具

## 觀念

### Swerve 模組

Swerve 底盤通常有 4 個 **Swerve module（模組）**，裝在機器人的四個角落。每個模組有：

| 元件 | 用途 |
| --- | --- |
| **Drive motor（驅動馬達）** | 讓輪子往前或往後滾 |
| **Steer motor（轉向馬達）**，也叫 Turn / Azimuth | 讓整個輪子轉向 |
| **Absolute encoder（絕對式編碼器）** | 知道輪子目前指向哪裡（例如 CANcoder） |

四個輪子都能各自轉到任何方向，所以機器人可以**往任何方向移動，同時自轉**。

### `ChassisVelocities`：機器人要怎麼動

描述 Swerve 底盤的移動，不是直接說「每個輪子要怎麼轉」，而是說**整台機器人要怎麼動**：

```java
new ChassisVelocities(vx, vy, omega)
```

| 參數 | 意思 | 單位 |
| --- | --- | --- |
| `vx` | 往前的速度 | 公尺 / 秒 |
| `vy` | 往左的速度 | 公尺 / 秒 |
| `omega` | 逆時針旋轉的速度 | 弧度 / 秒 |

方向的定義見 [章節首頁的座標說明](index.md#座標與方向)。

!!! info "2027 新改動：名稱改了"
    WPILib 2027 把 Kinematics 相關的類別改名了：

    | 2026 以前 | 2027 |
    | --- | --- |
    | `ChassisSpeeds` | `ChassisVelocities` |
    | `SwerveModuleState` | `SwerveModuleVelocity` |
    | `kinematics.toSwerveModuleStates(...)` | `kinematics.toSwerveModuleVelocities(...)` |
    | `desaturateWheelSpeeds(...)` | `desaturateWheelVelocities(...)` |
    | `ChassisSpeeds.fromFieldRelativeSpeeds(...)`（更舊的寫法）或 `speeds.toRobotRelative(heading)` | `velocities.toRobotRelative(heading)` |

    網路上大部分的 Swerve 範例都是舊名稱，抄的時候要改。

### Kinematics（運動學）

**`SwerveDriveKinematics`** 知道四個模組裝在機器人的哪裡，它可以把「機器人要怎麼動」換算成「每個模組的速度和角度」（`SwerveModuleVelocity`）。

為了理解它在算什麼，看看下面這段程式。它不會控制任何馬達，只是把幾種移動方式換算出來印出來：

```java title="src/main/java/first/robot/SwerveKinematicsDemo.java"
package first.robot;

import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.SwerveDriveKinematics;
import org.wpilib.math.kinematics.SwerveModuleVelocity;

/** 用來理解 Swerve Kinematics 的範例，不會控制任何馬達。 */
public final class SwerveKinematicsDemo {
  // 四個模組相對於機器人中心的位置，單位：公尺（X 往前、Y 往左）
  private static final SwerveDriveKinematics KINEMATICS =
      new SwerveDriveKinematics(
          new Translation2d(0.3, 0.3), // 左前
          new Translation2d(0.3, -0.3), // 右前
          new Translation2d(-0.3, 0.3), // 左後
          new Translation2d(-0.3, -0.3)); // 右後

  private static final String[] NAMES = {"左前", "右前", "左後", "右後"};

  public static void print(String label, ChassisVelocities velocities) {
    SwerveModuleVelocity[] modules = KINEMATICS.toSwerveModuleVelocities(velocities);
    System.out.println(label);
    for (int i = 0; i < modules.length; i++) {
      System.out.printf(
          "  %s：速度 %.2f m/s，角度 %4.0f 度%n",
          NAMES[i], modules[i].velocity, modules[i].angle.getDegrees());
    }
  }

  public static void main(String[] args) {
    print("往前 1 m/s", new ChassisVelocities(1.0, 0.0, 0.0));
    print("往左 1 m/s", new ChassisVelocities(0.0, 1.0, 0.0));
    print("原地逆時針轉 1 rad/s", new ChassisVelocities(0.0, 0.0, 1.0));

    // 駕駛想讓機器人往「場地的前方」走，但機器人現在面向左方（90 度）
    ChassisVelocities fieldRelative = new ChassisVelocities(1.0, 0.0, 0.0);
    ChassisVelocities robotRelative = fieldRelative.toRobotRelative(Rotation2d.fromDegrees(90));
    print("場地座標往前 1 m/s，機器人面向 90 度", robotRelative);
  }

  private SwerveKinematicsDemo() {}
}
```

執行結果：

```text
往前 1 m/s
  左前：速度 1.00 m/s，角度    0 度
  右前：速度 1.00 m/s，角度    0 度
  左後：速度 1.00 m/s，角度    0 度
  右後：速度 1.00 m/s，角度    0 度
往左 1 m/s
  左前：速度 1.00 m/s，角度   90 度
  右前：速度 1.00 m/s，角度   90 度
  左後：速度 1.00 m/s，角度   90 度
  右後：速度 1.00 m/s，角度   90 度
原地逆時針轉 1 rad/s
  左前：速度 0.42 m/s，角度  135 度
  右前：速度 0.42 m/s，角度   45 度
  左後：速度 0.42 m/s，角度 -135 度
  右後：速度 0.42 m/s，角度  -45 度
場地座標往前 1 m/s，機器人面向 90 度
  左前：速度 1.00 m/s，角度  -90 度
  右前：速度 1.00 m/s，角度  -90 度
  左後：速度 1.00 m/s，角度  -90 度
  右後：速度 1.00 m/s，角度  -90 度
```

看懂這四個結果：

1. **往前**：四個輪子都朝前（0 度）
2. **往左**：四個輪子都朝左（90 度），機器人直接橫移
3. **原地轉**：每個輪子都轉到和「機器人中心到模組的連線」垂直的方向，四個輪子排成一個圓。模組離中心約 0.42 公尺，轉速 1 rad/s，所以輪速是 0.42 m/s
4. **場地座標**：機器人面向左方時，「場地的前方」是機器人的**右方**，所以輪子都朝 -90 度

### Robot-relative 和 Field-relative

- **Robot-relative（機器人座標）**：「前」是機器人的前方。機器人轉了 180 度之後，推搖桿往前，機器人會往駕駛的方向衝過來
- **Field-relative（場地座標）**：「前」是場地的前方（遠離駕駛的方向）。不管機器人面向哪裡，推搖桿往前，機器人就往前

大部分駕駛都偏好**場地座標**。做法就是上面範例的最後一個：把搖桿的輸入當作場地座標的速度，用 IMU 的 [Heading](../06-motors-sensors/gyro.md) 換算成機器人座標，再交給 Kinematics。這也是為什麼 IMU 對 Swerve 這麼重要。

### 實際控制模組時還要做的事

拿到每個模組的 `SwerveModuleVelocity` 之後，真正的 Swerve 程式還要：

1. **Optimize（最佳化）**：如果輪子要轉超過 90 度，不如反過來轉、輪子倒著滾，比較快。用 `velocity.optimize(目前角度)` 處理
2. **Desaturate（限速）**：同時往前又自轉時，某些輪子算出來的速度可能超過馬達的極限。用 `SwerveDriveKinematics.desaturateWheelVelocities(...)` 等比例縮小
3. **控制轉向馬達**到指定角度：需要[第 8 章](../08-control/pid.md)的 PID
4. **控制驅動馬達**到指定速度：需要第 8 章的 PID + Feedforward

這些加起來是一個不小的工程，所以第一次做 Swerve，**強烈建議用現成的方案**。

## 現成的 Swerve 方案

| 方案 | 適合 | 說明 |
| --- | --- | --- |
| **CTRE Swerve Generator** | 全部使用 CTRE 硬體（Kraken + CANcoder + Pigeon 2） | 在 Phoenix Tuner X 裡輸入模組的參數，自動產生整套 Swerve 程式 |
| **YAGSL**（Yet Another Generic Swerve Library） | 混用不同廠商的硬體 | 用 JSON 檔描述硬體，支援 REV、CTRE 等各家的馬達和編碼器 |
| **廠商範例程式** | 使用特定廠商的 Swerve 模組 | 例如 REV 的 MAXSwerve 模組有官方範例程式 |

!!! info "2027 alpha 的支援狀況"
    YAGSL 在 2027 alpha 7 的 vendordep 清單裡已經有 **Commands v2** 和 **Commands v3** 兩種版本。CTRE Swerve Generator 和各廠商範例在 2027 alpha 的支援程度可能還在更新，使用前先確認版本。

用現成方案時，最重要的工作是**量測和設定正確的參數**：模組的位置、齒輪比、輪子直徑、編碼器的零點、馬達方向。參數錯了，Swerve 會亂轉。

想知道這些方案裡面在做什麼，或是硬體組合沒有現成方案（例如 TalonFX 驅動、SparkMax 轉向），[第 13 章到第 16 章](../13-swerve-drive/index.md) 會帶你從零寫一套 Swerve。

## 常見錯誤

!!! bug "常見錯誤：用了舊的類別名稱"
    `ChassisSpeeds`、`SwerveModuleState` 在 2027 已經改名，會出現 `cannot find symbol`。對照上面的表格修改。

!!! bug "常見錯誤：模組位置的 Y 方向弄反"
    WPILib 的 Y 軸是**往左**為正。把右邊的模組寫成正的 Y，原地轉圈時輪子的方向會全錯，機器人會一邊轉一邊亂跑。

!!! bug "常見錯誤：角速度用了度"
    `ChassisVelocities` 的 `omega` 單位是**弧度 / 秒**。寫成 `90`（想要每秒 90 度），機器人會以每秒約 5000 度的速度狂轉（如果馬達跟得上的話）。用 `Math.toRadians(90)` 換算。

## 小練習

1. 不看執行結果，先猜猜看 `new ChassisVelocities(1.0, 1.0, 0.0)` 時四個輪子的角度和速度，再改範例程式驗證。
2. 機器人面向 180 度（背對場地前方）時，場地座標往前 1 m/s，四個輪子應該指向幾度？
3. 量量看隊上 Swerve 底盤（或去年的機器人）四個模組到機器人中心的距離，寫出 `SwerveDriveKinematics` 的四個 `Translation2d`。

??? question "第 1 題參考答案"
    四個輪子都指向 **45 度**（往前又往左），速度都是 **1.41 m/s**（√(1² + 1²)）。機器人往左前方斜著走，不會轉向。

??? question "第 2 題參考答案"
    機器人背對場地前方時，「場地的前方」是機器人的**正後方**。四個輪子指向 **180 度**、速度 1 m/s。

    實際控制時，`optimize` 會發現「轉 180 度」不如「不轉、輪子倒著滾」，所以輪子可能維持 0 度，速度變成 -1 m/s，結果一樣。
