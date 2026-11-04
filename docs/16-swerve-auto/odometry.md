# Swerve Odometry

--8<-- "alpha-warning.md"

## 這頁學什麼

- `SwerveModulePosition`：模組走了多遠、指向哪裡
- 用 `SwerveDrivePoseEstimator` 計算機器人在場地上的位置
- 場地座標和駕駛視角：紅色聯盟的「前方」是 −X
- 重設朝向時，不要重設 IMU

## 觀念

### `SwerveModulePosition`

[第 9 章](../09-autonomous/odometry.md) 的差速底盤用左右輪走了多遠來算位置。Swerve 的每個輪子方向都不一樣，所以每個模組要提供**兩個**資訊：

```java
new SwerveModulePosition(走了幾公尺, 輪子的角度)
```

Kinematics 用四個模組「這 20 ms 多走了多少、往哪個方向」，算出機器人移動了多少、轉了多少，再一直累加。

### 為什麼直接用 Pose estimator

第 9 章先用 `DifferentialDriveOdometry`，到[第 10 章](../10-vision/apriltag.md) 才換成 Pose estimator。這一章直接用 **`SwerveDrivePoseEstimator`**：沒有視覺資料時，它就和 Odometry 一模一樣；[視覺](vision.md) 那一頁加上 AprilTag 時，不用再改。

### 場地座標和駕駛視角

加上 Odometry 之後，`getPose().getRotation()` 就是機器人在**場地座標**的朝向：0 度是面向紅色聯盟（+X），和[第 9 章](../09-autonomous/odometry.md#場地座標) 一樣。

但駕駛站在場地的兩端：

| 聯盟 | 駕駛站在 | 駕駛的「前方」 |
| --- | --- | --- |
| 藍色 | X = 0 那一端 | 場地的 +X |
| 紅色 | 場地的另一端 | 場地的 **−X** |

所以把搖桿換成場地座標的速度時，紅色聯盟要把 `vx`、`vy` 都**反過來**。旋轉的方向不用反：從上往下看，逆時針就是逆時針。

```java
public void driveDriverRelative(ChassisVelocities velocities) {
  if (isRedAlliance()) {
    velocities = new ChassisVelocities(-velocities.vx, -velocities.vy, velocities.omega);
  }
  driveFieldRelative(velocities);
}
```

### 重設朝向：改 Pose，不要改 IMU

[第 13 章](../13-swerve-drive/drivetrain.md) 的 Start 鍵會重設 IMU。現在 Odometry 用 IMU 的**變化量**算位置，中途重設 IMU 會讓 Odometry 以為機器人突然轉了一大圈（[第 9 章的常見錯誤](../09-autonomous/odometry.md#常見錯誤)）。

改成重設 **Pose 的朝向**，位置不變：

```java
Rotation2d awayFromDriver = isRedAlliance() ? Rotation2d.PI : Rotation2d.ZERO;
resetPose(new Pose2d(getPose().getTranslation(), awayFromDriver));
```

`Rotation2d.PI` 是 180 度。從這裡開始，`getHeading()`（IMU 的讀值）只給 Odometry 用，其他地方一律用 `getPose().getRotation()`。

## 範例

### SwerveModule

加上走過的距離和 `getPosition()`：

```java
/** 輪子總共滾了多遠，單位：公尺。 */
public double getDistanceMeters() {
  return driveMotor.getPosition().getValueAsDouble() * SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
}

/** 目前走過的距離和角度，給 Odometry 用。 */
public SwerveModulePosition getPosition() {
  return new SwerveModulePosition(getDistanceMeters(), getAngle());
}
```

因為設了 `SensorToMechanismRatio`，`getPosition()` 讀到的是輪子的圈數，乘上周長就是公尺。

### Drivetrain

加上 Pose estimator：

```java
private final SwerveDrivePoseEstimator poseEstimator;

public Drivetrain() {
  poseEstimator =
      new SwerveDrivePoseEstimator(kinematics, getHeading(), getModulePositions(), Pose2d.ZERO);
}

/** 四個模組走過的距離和角度。 */
public SwerveModulePosition[] getModulePositions() {
  SwerveModulePosition[] positions = new SwerveModulePosition[modules.length];
  for (int i = 0; i < modules.length; i++) {
    positions[i] = modules[i].getPosition();
  }
  return positions;
}

/** 機器人在場地上的位置和朝向。 */
public Pose2d getPose() {
  return poseEstimator.getEstimatedPosition();
}

public void resetPose(Pose2d pose) {
  poseEstimator.resetPose(pose);
}
```

場地座標改用 Pose 的朝向：

```java
/** 用場地座標的速度移動：vx 是往紅色聯盟的方向（+X）。 */
public void driveFieldRelative(ChassisVelocities velocities) {
  driveRobotRelative(velocities.toRobotRelative(getPose().getRotation()));
}
```

`driveCommand` 改成呼叫 `driveDriverRelative`，`periodic()` 每次更新 Pose estimator：

```java
@Override
public void periodic() {
  poseEstimator.update(getHeading(), getModulePositions());
  // ...原本的 Telemetry...
  Telemetry.log("Drivetrain/Pose", getPose());
}
```

完整的 `Drivetrain` 在[下一頁](pathplanner.md#drivetrain)。

### 在模擬裡看機器人跑

1. 啟動模擬，AdvantageScope 選 **Connect to Simulator**
2. 新增一個 **2D Field** 分頁（或 **3D Field**），把 `Telemetry/Drivetrain/Pose` 拖到場地上
3. 在 Swerve 分頁也可以把 `Pose` 拖到 Rotation，代替 `HeadingDegrees`

在模擬裡用場地座標以 2 m/s 往 +X 開 1 秒再停下，Pose 會停在 **X ≈ 2.05 公尺**：開了 1 秒，加上停下來時多滑的一小段。

## 常見錯誤

!!! bug "常見錯誤：紅色聯盟時搖桿方向全反"
    在紅色聯盟測試時，推搖桿往前，機器人往駕駛衝過來：`driveCommand` 還是呼叫 `driveFieldRelative`，沒有改成 `driveDriverRelative`。練習時在 Driver Station（或 Sim GUI）切換聯盟顏色，兩邊都要測。

!!! bug "常見錯誤：Odometry 的模組順序和 Kinematics 不一樣"
    `getModulePositions()` 的順序一定要和 `SwerveDriveKinematics` 一樣（左前、右前、左後、右後）。順序錯了，機器人直線前進時 Pose 會在場地上轉圈。

!!! bug "常見錯誤：用目標值算 Odometry"
    `getPosition()` 要用**量到的**距離和 CANcoder 的角度，不能用 `setTarget` 收到的目標。目標是「想要的」，Odometry 要的是「實際發生的」。

## 小練習

1. 在模擬裡讓機器人走一個 2 公尺 × 2 公尺的正方形，回到起點時 Pose 差多少？和[第 9 章小練習](../09-autonomous/odometry.md#小練習) 的差速底盤比較。
2. 在 Sim GUI 把聯盟改成紅色，推搖桿往前，模擬的機器人在場地上往哪個方向走？
3. 為什麼旋轉的方向不用因為紅色聯盟而反過來？

??? question "第 2 題參考答案"
    往場地的 **−X** 方向走，也就是從紅色聯盟的駕駛站往藍色聯盟的方向，對紅色聯盟的駕駛來說就是「遠離自己」。

??? question "第 3 題參考答案"
    紅色聯盟的駕駛只是站在場地的另一端，從上往下看，「逆時針」對兩邊的駕駛都是同一個方向。只有前後左右是相對於駕駛站的位置，所以只有 `vx`、`vy` 要反過來。
