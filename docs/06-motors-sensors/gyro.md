# 陀螺儀與 IMU

--8<-- "alpha-warning.md"

## 這頁學什麼

- 讀取機器人的 Heading（朝向）
- 設定 Systemcore 內建 IMU 的安裝方向
- 使用 CTRE 的 Pigeon 2
- `Rotation2d` 和角度的正負方向

## 觀念

### Heading（朝向）

**IMU（慣性測量單元）** 可以量測很多東西，但 FRC 最常用的是 **Yaw（偏航角）**，也就是機器人**面向場地的哪個方向**，通常稱為 **Heading**。

它會用在：

- Swerve 的場地座標控制：推搖桿往前，機器人就往場地的前方走，不管機器人面向哪裡（[第 7 章](../07-drivetrain/swerve.md)）
- 自動階段：知道機器人轉了幾度（[第 9 章](../09-autonomous/odometry.md)）

### 角度的方向

WPILib 的慣例是**逆時針 (Counterclockwise, CCW) 為正**：從上往下看機器人，往左轉角度增加，往右轉角度減少。這和數學課的角度方向一樣。

### `Rotation2d`

WPILib 用 **`Rotation2d`** 類別表示一個平面上的角度，它同時記得角度的各種表示方式：

```java
Rotation2d heading = Rotation2d.fromDegrees(90);

heading.getDegrees();     // 90.0
heading.getRadians();     // 1.5707...（π/2）
heading.getRotations();   // 0.25
```

`Rotation2d` 是不可變 (Immutable) 的物件，「轉動」它會得到一個新的物件，例如 `heading.plus(Rotation2d.fromDegrees(30))`。

!!! warning "2027 新改動：`getDegrees()` 的範圍改變了"
    WPILib 2027 起，`Rotation2d` 的 `getDegrees()`、`getRadians()`、`getRotations()` 會把角度**限制在一圈以內**，例如 `getDegrees()` 的範圍是 -180 到 180。機器人轉了 370 度，讀到的會是 10 度。

    這是一個**不會有編譯錯誤**的改動。舊程式如果依賴「累積的總角度」（例如計算轉了幾圈），在 2027 會悄悄算錯。

### Systemcore 內建 IMU 和 Pigeon 2

| | Systemcore 內建 IMU | CTRE Pigeon 2 |
| --- | --- | --- |
| 需要另外買 | 不用 | 要 |
| 接線 | 不用 | 接在 CAN 上 |
| 安裝位置 | 跟著 Systemcore | 可以裝在機器人的中心、震動小的地方 |
| 程式類別 | `OnboardIMU`（WPILib） | `Pigeon2`（Phoenix 6） |

內建 IMU 的好處是不用另外接線。但 Systemcore 的安裝位置是電控組依照空間決定的，不一定是最理想的位置。兩種都能用，看隊上的配置。

### 內建 IMU 的安裝方向

IMU 要知道「哪個方向是上」，才能正確算出 Yaw。建立 `OnboardIMU` 時，要告訴它 Systemcore 是怎麼安裝的：

| `MountOrientation` | Systemcore 的安裝方式 |
| --- | --- |
| `FLAT` | 平放，和地面平行 |
| `LANDSCAPE` | 直立，**長邊**和地面平行 |
| `PORTRAIT` | 直立，**短邊**和地面平行 |

## 範例

在底盤 Subsystem 裡放 IMU。這一章先只有 IMU，[第 7 章](../07-drivetrain/index.md) 再加上馬達。Pigeon 2 的 CAN ID 加在 `Constants.java`：

```java
public static final class DrivetrainConstants {
  public static final int PIGEON_ID = 41;
}
```

=== "Systemcore 內建 IMU"

    ```java title="src/main/java/first/robot/subsystems/Drivetrain.java"
    package first.robot.subsystems;

    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.imu.OnboardIMU;
    import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;
    import org.wpilib.math.geometry.Rotation2d;
    import org.wpilib.telemetry.Telemetry;

    public class Drivetrain extends SubsystemBase {
      private final OnboardIMU imu = new OnboardIMU(MountOrientation.FLAT);

      /** 機器人的朝向，逆時針為正。 */
      public Rotation2d getHeading() {
        return imu.getRotation2d();
      }

      /** 把目前的朝向設為 0 度。 */
      public Command resetHeadingCommand() {
        return runOnce(imu::resetYaw).ignoringDisable(true).withName("ResetHeading");
      }

      @Override
      public void periodic() {
        Telemetry.log("Drivetrain/HeadingDegrees", getHeading().getDegrees());
      }
    }
    ```

=== "CTRE Pigeon 2"

    ```java title="src/main/java/first/robot/subsystems/Drivetrain.java"
    package first.robot.subsystems;

    import com.ctre.phoenix6.CANBus;
    import com.ctre.phoenix6.hardware.Pigeon2;
    import first.robot.Constants.DrivetrainConstants;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.math.geometry.Rotation2d;
    import org.wpilib.telemetry.Telemetry;

    public class Drivetrain extends SubsystemBase {
      private final Pigeon2 pigeon = new Pigeon2(DrivetrainConstants.PIGEON_ID, new CANBus(CANPort.CAN_S0));

      /** 機器人的朝向，逆時針為正。 */
      public Rotation2d getHeading() {
        return pigeon.getRotation2d();
      }

      /** 把目前的朝向設為 0 度。 */
      public Command resetHeadingCommand() {
        return runOnce(pigeon::reset).ignoringDisable(true).withName("ResetHeading");
      }

      @Override
      public void periodic() {
        Telemetry.log("Drivetrain/HeadingDegrees", getHeading().getDegrees());
      }
    }
    ```

    `pigeon.getYaw()` 也可以讀到角度（單位是度，而且**不會**限制在一圈以內），但轉成 `Rotation2d` 後一樣會被限制在 -180 到 180 度。

`ignoringDisable(true)` 讓這個 Command 在機器人停用時也能執行。重設朝向不會讓機器人動，所以在 Disabled 時執行是安全的。

在 `RobotContainer` 裡綁定一個按鈕，讓駕駛可以在比賽中重設朝向：

```java
private final Drivetrain drivetrain = new Drivetrain();

// 在 configureBindings() 裡
driver.start().onTrue(drivetrain.resetHeadingCommand());
```

## 常見錯誤

!!! bug "常見錯誤：安裝方向設錯"
    Systemcore 直立安裝，程式裡卻寫 `FLAT`，機器人原地轉圈時 Heading 幾乎不會變，或是變化方向很奇怪。原地慢慢轉一圈，看 `Drivetrain/HeadingDegrees` 有沒有從 0 變到 ±180 再回到 0。

!!! bug "常見錯誤：開機時機器人在動"
    IMU 開機時會校正，如果這時候機器人在移動或被搬動，Heading 可能會慢慢飄移。開機後讓機器人靜止幾秒。

!!! bug "常見錯誤：以為往右轉角度會增加"
    WPILib 是逆時針為正，往右轉角度會**減少**。如果你的計算結果方向相反，先檢查這裡。

## 小練習

1. 部署範例，在 Elastic 上觀察 `Drivetrain/HeadingDegrees`。用手把機器人往左轉 90 度，讀到的是 90 還是 -90？
2. 繼續往左轉超過 180 度，觀察數值的變化。
3. 寫一個方法 `public boolean isFacingForward()`：朝向在 0 度 ±10 度以內時回傳 `true`。注意 -5 度和 355 度要怎麼處理？（提示：`getHeading().getDegrees()` 已經限制在 -180～180 度了）
