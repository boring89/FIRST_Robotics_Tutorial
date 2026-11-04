# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 用 PathConstraints 限制手動駕駛

`generateSetpoint` 有一個多收 `PathConstraints` 的版本，可以在機器人本身的極限之外，**再**加上自己的限制：

```java
// 例如：手上有遊戲物件時，限制加速度，避免物件甩出去
PathConstraints gentle = new PathConstraints(3.0, 2.0, Math.toRadians(360), Math.toRadians(360));
previousSetpoint = setpointGenerator.generateSetpoint(previousSetpoint, velocities, gentle, 0.02);
```

四個值是最高速度（m/s）、最高加速度（m/s²）、最高旋轉速度和旋轉加速度（rad/s、rad/s²）。可以依照機器人的狀態切換，例如升降機構升高時，降低加速度避免翻車。

## 高頻率 Odometry

這幾章的 Odometry 每 20 ms 更新一次。高速轉彎時，20 ms 裡輪子的角度和速度變化很大，Odometry 會有誤差。強隊的做法是：

- 讓 TalonFX 和 CANcoder 用更高的頻率（例如 250 Hz）送位置和角度
- 在另一個執行緒裡用 `BaseStatusSignal.waitForAll()` 等新的資料，每收到一次就更新一次 Odometry
- 主程式讀 Pose 時加鎖，避免兩個執行緒同時存取

這一套組合剛好適合高頻率 Odometry：Odometry 需要的**距離**來自 TalonFX、**角度**來自 CANcoder，兩個都是 CTRE 的裝置，不需要 SparkMax 的資料。CTRE 的 Swerve API 就是這樣做的，可以參考它的原始碼。

## 打滑偵測

Setpoint Generator 會盡量避免打滑，但被撞的時候還是會滑。可以比較：

- 四個模組量到的速度，用 Kinematics 算回機器人的速度之後，再算回每個模組「應該」的速度，和量到的差多少
- 差很多的時候，代表有輪子在打滑，這時候的 Odometry 不可信，可以把視覺的標準差調小，讓 Pose estimator 更相信 AprilTag

## 254 的原始版本

PathPlannerLib 的 `SwerveSetpointGenerator` 改寫自 254 隊 2022 賽季的版本，原始程式碼在 [FRC-2022-Public](https://github.com/Team254/FRC-2022-Public/blob/main/src/main/java/com/team254/lib/swerve/SwerveSetpointGenerator.java)（MIT 授權）。和 PathPlannerLib 的版本對照著讀，可以看到：

- `KinematicLimits`：最高速度、最高加速度、最高轉向速度三個限制
- `findSteeringMaxS`、`findDriveMaxS`：用求根的方法找出每個模組的 s
- 同一個資料夾裡的 `HeadingLockedFieldRelativeController`：場地座標控制加上保持朝向，就是[保持朝向](heading-lock.md) 那一頁的做法

254 大部分賽季結束後，都會在 GitHub 的 **Team254** 組織公開機器人的完整程式碼（例如 FRC-2024-Public、FRC-2025-Public），裡面還有很多值得參考的做法。程式碼很多，可以先從底盤的部分開始看。

## 再往下

[第 18 章 硬體抽象化與 AdvantageKit](../18-hardware-abstraction/index.md) 把這一章的 Swerve 改寫成硬體和邏輯分開的架構，並用 AdvantageKit 重播比賽的 log。

## 進一步閱讀

- [PathPlanner 文件](https://pathplanner.dev/home.html)：Swerve Setpoint Generator 的用法
- [CTRE 文件：Swerve](https://v6.docs.ctr-electronics.com/en/latest/docs/api-reference/mechanisms/swerve/swerve-overview.html)：高頻率 Odometry 的做法
