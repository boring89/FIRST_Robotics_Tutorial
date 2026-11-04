# 第 16 章 Swerve 自動階段

--8<-- "alpha-warning.md"

前三章的 Swerve 已經可以開、調好了，也能在模擬裡測試。這一章讓它**知道自己在場地上的哪裡**，然後用 PathPlanner 自己跑路徑，最後用 AprilTag 修正位置、自動對準目標。

和[第 9 章](../09-autonomous/index.md)、[第 10 章](../10-vision/index.md) 的差速底盤比較，觀念完全一樣，只是換成 Swerve 的類別：

| | 差速底盤（第 9、10 章） | Swerve（這一章） |
| --- | --- | --- |
| Odometry 需要的 | 左右輪的距離 | 四個模組的距離和角度（`SwerveModulePosition`） |
| Pose estimator | `DifferentialDrivePoseEstimator` | `SwerveDrivePoseEstimator` |
| PathPlanner 的控制器 | `PPLTVController` | `PPHolonomicDriveController` |
| 對準目標時能不能移動 | 只能前進後退 | 可以往任何方向移動 |

## 這章學什麼

- 用 `SwerveModulePosition` 和 `SwerveDrivePoseEstimator` 計算機器人的位置
- 駕駛視角：紅色聯盟的駕駛看到的「前方」是相反的
- 設定 PathPlanner 的 Holonomic 模式，在模擬裡跑自動程式
- 用 Limelight 修正位置，一邊移動一邊對準目標

**需要先讀**：[第 9 章 Odometry 和 PathPlanner](../09-autonomous/odometry.md)、[第 10 章 視覺](../10-vision/index.md)、[第 15 章 Swerve 模擬](../15-swerve-sim/index.md)

## 本章內容

| 小節 | 內容 |
| --- | --- |
| [Swerve Odometry](odometry.md) | 模組的位置、Pose estimator、駕駛視角 |
| [PathPlanner](pathplanner.md) | Holonomic 設定、`AutoBuilder`、在模擬裡跑路徑 |
| [視覺](vision.md) | 用 AprilTag 修正位置、一邊移動一邊對準目標 |
| [延伸學習](further.md) | 自動找路、對準到指定位置 |
