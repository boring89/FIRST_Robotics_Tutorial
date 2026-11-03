# 底盤 Drivetrain

這個模擬器是一台差速底盤，在一個 8 × 4 公尺的練習場上：

- **硬體**：四顆 NEO（每邊兩顆），8.45:1 減速，6 英寸輪，機器人重 50 公斤
- **物理**：輪子和地面之間有**抓地力上限**，加速太猛時輪子會打滑
- **感測器**：每邊的編碼器量的是**輪子**轉了多少；IMU 量的是機器人的朝向
- **Odometry**：橘色是機器人**實際**的位置，半透明的藍色是 Odometry **估計**的位置
- **Command**：default command 是 Arcade drive；`leftBumper` 慢速；十字鍵用 PID 轉到指定方向；還有一個「正方形」自動程式

<div data-sim="drivetrain"></div>
<script type="module" src="../../assets/sim/drivetrain.js"></script>

**開車**：先按 **Enable**，點一下 3D 畫面，用鍵盤的 ++w++ ++s++ 前進後退、++a++ ++d++ 轉彎，按住 ++shift++ 是慢速模式。也可以拖曳面板上的搖桿滑桿。

## 試試看

### 1. Arcade drive

開車繞場地一圈。注意左右輪速的曲線：轉彎時兩邊的速度不一樣，原地轉時一正一負，這就是[第 7 章](../07-drivetrain/differential.md#差速底盤怎麼動)的差速原理。

關掉「搖桿輸入平方」再開一次，比較低速時微調的手感。

### 2. TurnToAngle

按十字鍵讓機器人轉到 0、90、180、-90 度。這是[第 8 章的 `TurnToAngle`](../08-control/pid.md#範例)：用 IMU 的角度做 PID。

- 把**轉向 kP** 調小（0.004）：轉得很慢，而且停的位置差一點
- 調大（0.04）：轉過頭再轉回來，來回震盪
- 加一點 **kD**：震盪變少

從 170 度按 `dpadDown`（180 度）時，機器人只轉 10 度，而不是反方向轉 350 度，這是 `enableContinuousInput(-180, 180)` 的效果。

### 3. Odometry 的漂移

1. 按 **重設位置**，讓實際和估計的位置重疊
2. 正常開一圈：藍色的估計位置會跟著橘色的機器人走，誤差很小
3. 按 **從側面撞一下**：機器人被往旁邊推，但**差速底盤的編碼器量不到側向的移動**，估計的位置不會動，誤差一下子變大
4. 全速起步、急停幾次：右上角出現「⚠ 輪子打滑」時，輪子轉得比機器人實際移動的快，編碼器**多算**了距離，誤差又變大了

這就是[第 9 章](../09-autonomous/odometry.md#odometry里程計)說的：Odometry 是累加出來的，誤差只會越來越大，不會自己修正。

### 4. AprilTag 修正

打開 **AprilTag 視覺修正**，再撞幾次。當機器人面向場地牆上的 AprilTag（白色方塊）、而且距離夠近時，估計的位置會被慢慢拉回實際的位置。轉過頭背對所有 AprilTag 時，修正就停止了。

這就是[第 10 章](../10-vision/apriltag.md#用法二修正機器人的位置)的 Pose estimation：平常靠 Odometry，看到 AprilTag 時用視覺修正。

### 5. 自動程式：正方形

按 **重設位置**，再按 **自動：正方形**。機器人會依序執行 `DriveDistance(1.5 m)` → `TurnToAngle(90°)` × 4，畫出一個正方形，Command Scheduler 會顯示目前在第幾步。

- 自動程式執行到一半時推一下搖桿：**什麼都不會發生**，因為 default command 只在沒有其他 Command 使用底盤時才執行
- 跑完之後看「誤差」：大約 0.2 公尺。`DriveDistance` 一結束就馬上開始 `TurnToAngle`，機器人還在往前衝的時候一邊的輪子就反轉，那一瞬間輪子會打滑（注意 ⚠ 的提示），誤差就這樣慢慢累積
- 把**自動速度**調到 1.0 再跑一次：起步和換方向時打滑得更嚴重，編碼器多算了距離，最後的誤差會到 1 公尺左右，正方形完全歪掉

這說明了為什麼自動程式要**適度控制速度**、在步驟之間讓機器人穩定下來，以及為什麼強隊都會用視覺修正位置。

## 對照程式碼

| 模擬器 | 程式碼 |
| --- | --- |
| Arcade drive（default command） | `drivetrain.setDefaultCommand(drivetrain.arcadeDriveCommand(...))`（[第 7 章](../07-drivetrain/differential.md#範例)） |
| `leftBumper` 慢速 | `driver.leftBumper().whileTrue(drivetrain.arcadeDriveCommand(... * 0.3 ...))`（[第 7 章延伸學習](../07-drivetrain/further.md#慢速模式)） |
| 十字鍵 → `TurnToAngle` | `driver.dpadUp().onTrue(new TurnToAngle(drivetrain, 0))`（[第 8 章](../08-control/pid.md#範例)） |
| `DriveDistance(1.5 m)` | `drivetrain.driveDistanceCommand(1.5)`（[第 9 章](../09-autonomous/encoder-based.md#走到指定距離為止)） |
| 藍色的估計位置 | `DifferentialDriveOdometry` / `DifferentialDrivePoseEstimator` |
| AprilTag 修正 | `poseEstimator.addVisionMeasurement(...)`（[第 10 章](../10-vision/limelight-photonvision.md#把-odometry-換成-pose-estimator)） |
