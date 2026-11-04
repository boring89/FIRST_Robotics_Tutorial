# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 靜止時重新校正轉向編碼器

[SparkMax 轉向 PID](onboard-pid.md) 只在開機時用 CANcoder 校正一次。齒輪間隙、劇烈碰撞可能讓 NEO 的編碼器和真正的角度慢慢偏離。

可以在機器人**停用**、輪子沒在動的時候重新校正：

```java
// 在 Drivetrain 裡
public Command syncSteerEncodersCommand() {
  return runOnce(() -> {
        for (SwerveModule module : modules) {
          module.syncSteerEncoder();
        }
      })
      .ignoringDisable(true)
      .withName("SyncSteerEncoders");
}
```

```java
// 在 RobotContainer 的 configureBindings() 裡：每次停用時重新校正一次
// （import org.wpilib.command2.button.RobotModeTriggers）
RobotModeTriggers.disabled().onTrue(drivetrain.syncSteerEncodersCommand());
```

也可以在 `SwerveModule` 裡比較 CANcoder 和 NEO 編碼器的角度，差超過幾度時才校正。

## 斜移補償

[駕駛手感](driver-input.md) 看到，一邊平移一邊旋轉時，`discretize` 只修正了一部分的偏移。剩下的主要來自轉向的延遲：機器人一直在轉，輪子的目標角度一直在變，轉向總是慢半拍。

常見的補償方法是把平移的方向**預先多轉一點**：

```java
/** 用場地座標的速度移動，並預先補償轉向的延遲。 */
public void driveFieldRelative(ChassisVelocities velocities) {
  // 預估轉向的延遲（秒），要實際測試調整
  double lookahead = 0.05;
  Rotation2d heading = getHeading().plus(Rotation2d.fromRadians(velocities.omega * lookahead));
  driveRobotRelative(velocities.toRobotRelative(heading));
}
```

在[第 15 章](../15-swerve-sim/index.md) 的模擬裡，用[駕駛手感](driver-input.md#discretize一邊走一邊轉) 同樣的測試（1 m/s 往前、2 rad/s 旋轉 2 秒）：

| `lookahead` | 往旁邊偏的距離 |
| --- | --- |
| 0（只有 `discretize`） | 0.28 m |
| 0.05 秒 | 0.09 m |
| 0.1 秒 | −0.10 m（補償過頭，往另一邊偏） |

`lookahead` 太大會往反方向偏。真正的機器人和模擬不一樣，要在場地上邊開邊調。

## 轉向的 Feedforward

轉向也可以加 Feedforward：轉向馬達有摩擦力（kS），轉得快時也需要電壓（kV）。SparkMax 的 `config.closedLoop.feedForward` 可以設定 kS、kV，用法見[第 8 章](../08-control/feedforward.md)。對大部分的模組來說，好好調 P 就夠了，加 kS 可以改善「差一點點轉不到」的情況。

## 更高頻率的 Odometry

這幾章的 Odometry（[第 16 章](../16-swerve-auto/odometry.md)）每 20 ms 更新一次。Phoenix 6 可以讓 TalonFX 和 CANcoder 用更高的頻率送資料，並加上時間戳記，在另一個執行緒裡用 250 Hz 更新 Odometry，CTRE 的 Swerve API 就是這樣做的。這需要多執行緒的知識，有興趣可以參考 CTRE 的文件。

## 進一步閱讀

- [WPILib 文件：Swerve Drive Kinematics](https://docs.wpilib.org/en/latest/docs/software/kinematics-and-odometry/swerve-drive-kinematics.html)：Optimize、Cosine 補償、Discretize（部分內容可能還是 2026 的類別名稱）
- [CTRE 文件：Swerve](https://v6.docs.ctr-electronics.com/en/latest/docs/api-reference/mechanisms/swerve/swerve-overview.html)：高頻率 Odometry 的做法
