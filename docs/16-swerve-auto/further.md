# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 自動找路

PathPlanner 除了跟著畫好的路徑走，還可以**即時規劃**一條路徑到指定位置，並避開場地上的障礙物：

```java
Command goToScoring = AutoBuilder.pathfindToPose(
    new Pose2d(14.0, 5.5, Rotation2d.fromDegrees(180)),   // 目標位置
    new PathConstraints(3.0, 3.0, Math.toRadians(540), Math.toRadians(720)));  // 速度和加速度限制
```

`PathConstraints` 的四個值是最高速度（m/s）、最高加速度（m/s²）、最高旋轉速度（rad/s）、最高旋轉加速度（rad/s²）。綁在按鈕上，駕駛按一下，機器人就自己開到得分位置。它依賴準確的 Pose，所以一定要有[視覺](vision.md) 修正位置。

## 對準到指定位置

最後幾公分的精準對位，用 PathPlanner 規劃路徑反而太慢。可以直接用三個 PID：X、Y 和朝向各一個，目標是指定的 `Pose2d`：

```java
PIDController xController = new PIDController(3.0, 0.0, 0.0);
PIDController yController = new PIDController(3.0, 0.0, 0.0);
PIDController thetaController = new PIDController(4.0, 0.0, 0.0);
thetaController.enableContinuousInput(-Math.PI, Math.PI);

// 每 20 ms
Pose2d pose = drivetrain.getPose();
drivetrain.driveFieldRelative(new ChassisVelocities(
    xController.calculate(pose.getX(), target.getX()),
    yController.calculate(pose.getY(), target.getY()),
    thetaController.calculate(pose.getRotation().getRadians(), target.getRotation().getRadians())));
```

這裡用 **`driveFieldRelative`**（場地座標），不是 `driveDriverRelative`：目標位置是場地座標，不管哪個聯盟，X 和 Y 的方向都一樣。

## 偵測打滑和碰撞

Odometry 假設輪子沒有打滑。被撞的時候，可以比較「Kinematics 算出的速度」和「IMU 的加速度」，或是比較四個模組的速度是否一致，判斷 Odometry 現在可不可信，必要時把視覺的標準差調小，讓 Pose estimator 更相信視覺。

## Choreo

**Choreo** 是另一個路徑規劃工具，它用**最佳化**算出在機器人物理限制下最快的路徑（PathPlanner 是你畫出路徑的形狀，再規劃速度）。PathPlannerLib 可以直接讀取 Choreo 的路徑。使用前先確認它是否已經支援 2027。

## 進一步閱讀

- [PathPlanner 文件](https://pathplanner.dev/home.html)：Holonomic 設定、Pathfinding、Choreo 整合
- [WPILib 文件：Swerve Drive Odometry](https://docs.wpilib.org/en/latest/docs/software/kinematics-and-odometry/swerve-drive-odometry.html)：Swerve 的 Odometry（部分內容可能還是 2026 的類別名稱）
