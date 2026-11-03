# Limelight 與 PhotonVision

--8<-- "alpha-warning.md"

## 這頁學什麼

- 比較 Limelight 和 PhotonVision
- 設定 Limelight、加入 LimelightLib
- 寫一個 Vision Subsystem
- 用視覺對準目標
- 把視覺的位置資料加進底盤的 Pose estimator

## 觀念

### 兩種方案比較

| | Limelight | PhotonVision |
| --- | --- | --- |
| 硬體 | Limelight 相機（相機和處理器一體） | 協同處理器（Orange Pi 等）+ USB 相機 |
| 價格 | 較高 | 較低 |
| 設定 | 網頁介面，簡單 | 網頁介面，選項多 |
| 多相機 | 每個相機一台 Limelight | 一個協同處理器可接多個相機 |
| 程式函式庫 | **LimelightLib** | **PhotonLib** |

兩者的觀念和用法很像：設定好 AprilTag 的 Pipeline（處理流程），在程式裡讀取 tx 或機器人的位置。這頁的程式以 Limelight 為例。

!!! info "PhotonVision 的 2027 支援狀況"
    撰寫本章時（2026 年 11 月），PhotonVision 最新的 2027 版本是 2027.0.0-alpha-2，還沒有出現在 WPILib 2027 alpha 7 的 vendordep 清單裡。使用 PhotonVision 之前，先到 [PhotonVision 的 GitHub](https://github.com/PhotonVision/photonvision/releases) 確認有沒有支援你使用的 WPILib 版本。

### 設定 Limelight

1. 把 Limelight 接上機器人的網路（或接在 Systemcore 的 USB 埠上），用瀏覽器打開它的網頁介面
2. 更新到支援 2027 的 Limelight OS 版本
3. 新增一個 **AprilTag** Pipeline
4. 在 **3D** 設定裡：
    - 輸入相機在機器人上的**安裝位置**（相對於機器人中心，往前、往旁邊、往上幾公尺）和**角度**
    - 上傳比賽的**場地配置 (Field map)**
5. 在網頁介面上確認看得到 AprilTag，而且算出的機器人位置合理

### 加入 LimelightLib

2027 使用新版的 **LimelightLib 2**（舊的 `LimelightHelpers.java` 不適用）。用 **WPILib: Manage Vendor Libraries** → **Install new libraries (online)**，貼上：

```text
https://limelightvision.github.io/limelightlib-public/LimelightLib-alpha7.json
```

這是 WPILib 2027 alpha 7 用的版本。換成其他 alpha 版本時，到 [SystemcoreTesting](https://github.com/wpilibsuite/SystemcoreTesting) 找對應的網址。

### `Limelight` 類別

```java
Limelight limelight = new Limelight("limelight");   // 名稱和 Limelight 網頁介面上設定的一樣

limelight.hasTarget();        // 有沒有看到目標
limelight.getTXDegrees();     // 水平角度，目標在右邊為正
limelight.getPoseEstimate(PoseEstimateType.MT2_WPIBLUE);   // MegaTag2 算出的機器人位置
```

`MT2_WPIBLUE` 代表用 MegaTag2、以**藍色聯盟為原點**的座標，和 [WPILib 的場地座標](../09-autonomous/odometry.md#場地座標)一致，紅色聯盟也用這個。

## 範例

### Vision Subsystem

在 `Constants.java` 加上：

```java
public static final class VisionConstants {
  public static final String LIMELIGHT_NAME = "limelight";
  public static final double AIM_KP = 0.02;           // 每 1 度 tx，輸出 0.02
  public static final double AIM_TOLERANCE_DEGREES = 1.0;
}
```

Vision 會用到底盤的位置（MegaTag2 需要朝向），也會把算出的位置交給底盤，所以建構子接收 `Drivetrain`：

```java title="src/main/java/first/robot/subsystems/Vision.java"
package first.robot.subsystems;

import com.limelightvision.Limelight;
import com.limelightvision.PoseEstimate;
import com.limelightvision.PoseEstimateType;
import first.robot.Constants.VisionConstants;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.telemetry.Telemetry;

public class Vision extends SubsystemBase {
  private final Limelight limelight = new Limelight(VisionConstants.LIMELIGHT_NAME);
  private final Drivetrain drivetrain;

  public Vision(Drivetrain drivetrain) {
    this.drivetrain = drivetrain;
  }

  public boolean hasTarget() {
    return limelight.hasTarget();
  }

  /** 目標的水平角度，目標在右邊為正。 */
  public double getTXDegrees() {
    return limelight.getTXDegrees();
  }

  @Override
  public void periodic() {
    // MegaTag2 需要知道機器人目前在場地上的朝向
    Limelight.setSharedRobotOrientation(drivetrain.getPose().getRotation().getDegrees());

    PoseEstimate estimate = limelight.getPoseEstimate(PoseEstimateType.MT2_WPIBLUE);
    if (estimate.isValid()) {
      drivetrain.addVisionMeasurement(estimate.pose, estimate.timestampSeconds, estimate.stdDevs);
      Telemetry.log("Vision/Pose", estimate.pose);
    }

    Telemetry.log("Vision/HasTarget", hasTarget());
    Telemetry.log("Vision/TXDegrees", getTXDegrees());
  }
}
```

- `Limelight.setSharedRobotOrientation(...)`：把朝向送給**所有** Limelight。只有一台 Limelight 時也用這個
- `estimate.isValid()`：有看到場地上的 AprilTag、而且沒有被 LimelightLib 判定為不可靠時才是 `true`
- `estimate.stdDevs`：LimelightLib 依照看到幾個 AprilTag、距離多遠算出的標準差，直接交給 Pose estimator

### 把 Odometry 換成 Pose estimator

在[第 9 章的 Drivetrain](../09-autonomous/pathplanner.md#範例) 裡，把 `DifferentialDriveOdometry` 換成 **`DifferentialDrivePoseEstimator`**。用法幾乎一樣，多了 `addVisionMeasurement`：

```java
// 欄位：把 DifferentialDriveOdometry 換掉
private final DifferentialDrivePoseEstimator poseEstimator;

// 建構子
poseEstimator = new DifferentialDrivePoseEstimator(
    getHeading(), getLeftDistanceMeters(), getRightDistanceMeters(), new Pose2d());

public Pose2d getPose() {
  return poseEstimator.getEstimatedPosition();
}

public void resetPose(Pose2d pose) {
  poseEstimator.resetPose(pose);
}

/** 加入視覺算出的位置。 */
public void addVisionMeasurement(Pose2d pose, double timestampSeconds, Matrix<N3, N1> stdDevs) {
  poseEstimator.addVisionMeasurement(pose, timestampSeconds, stdDevs);
}

// periodic()
poseEstimator.update(getHeading(), getLeftDistanceMeters(), getRightDistanceMeters());
```

需要的 `import`：`org.wpilib.math.estimator.DifferentialDrivePoseEstimator`、`org.wpilib.math.linalg.Matrix`、`org.wpilib.math.numbers.N1`、`org.wpilib.math.numbers.N3`。

`getPose()` 的名字沒變，所以 **PathPlanner 不用改任何程式**，就會自動用上視覺修正後的位置。

### 對準目標

按住按鈕時，駕駛控制前進後退，轉向交給視覺自動對準：

```java title="src/main/java/first/robot/commands/AimAtTarget.java"
package first.robot.commands;

import first.robot.Constants.VisionConstants;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Vision;
import java.util.function.DoubleSupplier;
import org.wpilib.command2.Command;
import org.wpilib.math.controller.PIDController;

/** 一邊讓駕駛控制前進後退，一邊自動轉向對準目標。 */
public class AimAtTarget extends Command {
  private final Drivetrain drivetrain;
  private final Vision vision;
  private final DoubleSupplier forward;
  private final PIDController controller = new PIDController(VisionConstants.AIM_KP, 0.0, 0.0);

  public AimAtTarget(Drivetrain drivetrain, Vision vision, DoubleSupplier forward) {
    this.drivetrain = drivetrain;
    this.vision = vision;
    this.forward = forward;
    controller.setSetpoint(0.0);
    controller.setTolerance(VisionConstants.AIM_TOLERANCE_DEGREES);
    addRequirements(drivetrain);   // Vision 只是讀資料，不需要佔用
  }

  @Override
  public void initialize() {
    controller.reset();
  }

  @Override
  public void execute() {
    double rotation = 0.0;
    if (vision.hasTarget()) {
      rotation = Math.clamp(controller.calculate(vision.getTXDegrees()), -0.5, 0.5);
    }
    drivetrain.arcadeDrive(forward.getAsDouble(), rotation);
  }

  @Override
  public void end(boolean interrupted) {
    drivetrain.arcadeDrive(0.0, 0.0);
  }
}
```

在 `RobotContainer` 建立 Vision（要寫在 `drivetrain` **下面**，因為它需要 `drivetrain`）並綁定按鈕：

```java
private final Drivetrain drivetrain = new Drivetrain();
private final Vision vision = new Vision(drivetrain);

// 在 configureBindings() 裡：按住右板機時自動對準
driver.rightTrigger().whileTrue(new AimAtTarget(drivetrain, vision, () -> -driver.getLeftY()));
```

## 常見錯誤

!!! bug "常見錯誤：Vision 在 Drivetrain 之前建立"
    `RobotContainer` 的欄位是由上往下建立的。`vision` 寫在 `drivetrain` 上面，建立 Vision 時 `drivetrain` 還是 `null`，程式一啟動就會 `NullPointerException`（見[第 5 章](../05-commands/robot-container.md#常見錯誤)）。

!!! bug "常見錯誤：Limelight 名稱不一致"
    程式裡的 `"limelight"` 要和 Limelight 網頁介面設定的名稱一模一樣。名稱不對的話，`hasTarget()` 永遠是 `false`，也不會有錯誤訊息。

!!! bug "常見錯誤：Pose 在紅色聯盟時錯了"
    使用 `MT2_WPIRED` 這類以紅色聯盟為原點的座標，和 WPILib、PathPlanner 的座標系統不一致。**一律用 `WPIBLUE`**，PathPlanner 會處理翻轉。

## 小練習

1. 設定好 Limelight，在 AdvantageScope 的場地圖上同時顯示 `Telemetry/Drivetrain/Pose` 和 `Telemetry/Vision/Pose`，推著機器人走，比較兩者的差別。
2. 用力推機器人讓輪子打滑，觀察加了視覺之後，底盤的位置會不會自己修正回來。
3. 修改 `AimAtTarget`：沒有看到目標時，讓駕駛可以用右搖桿手動轉向。
