# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 過濾不可靠的視覺資料

LimelightLib 的 `isValid()` 已經做了基本的檢查，但比賽中還會遇到更多狀況。常見的額外過濾條件：

- **機器人轉得太快時不採用**：高速旋轉時影像會模糊，MegaTag2 用的朝向也可能來不及更新
- **算出的位置在場地外面時不採用**：例如 X 是負的，或超出場地的長寬
- **和目前的位置差太多時不採用**：例如一下子差了 3 公尺，比較可能是誤判而不是機器人真的瞬間移動

```java
// 機器人每秒轉超過 360 度時，不採用這筆視覺資料
boolean spinningTooFast = Math.abs(drivetrain.getRobotRelativeVelocities().omega) > Math.toRadians(360);
if (estimate.isValid() && !spinningTooFast) {
  drivetrain.addVisionMeasurement(estimate.pose, estimate.timestampSeconds, estimate.stdDevs);
}
```

## 調整標準差

Pose estimator 怎麼在 Odometry 和視覺之間取捨，取決於兩邊的標準差：

- **視覺的標準差越小**：越相信視覺，位置會很快被拉到視覺的結果，但也比較容易跳動
- **視覺的標準差越大**：越相信 Odometry，位置比較平滑，但修正得比較慢

LimelightLib 會根據看到的 AprilTag 數量和距離算出標準差。如果覺得位置跳動太大，可以把它乘上一個係數（例如 ×2）再交給 Pose estimator。

## 多個相機

一台相機的視野有限，強隊常常在機器人的前、後、側面都裝相機，讓機器人幾乎隨時都看得到 AprilTag。做法是每台相機一個 `Limelight` 物件（不同名稱），每一台的結果都呼叫 `addVisionMeasurement`。`Limelight.setSharedRobotOrientation()` 一次就能把朝向送給所有的 Limelight。

## 物件偵測 (Object detection)

除了 AprilTag，Limelight 和 PhotonVision 都可以跑**神經網路**，辨識畫面中的遊戲物件。用法和對準 AprilTag 很像：讀取物件的 tx，讓機器人轉向物件、往前開，搭配 Intake 自動吸入。

神經網路模型需要針對每年的遊戲物件訓練。賽季開始後，Limelight 和社群通常會釋出訓練好的模型。

## 進一步閱讀

- [Limelight 官方文件](https://docs.limelightvision.io/)：Pipeline 設定、MegaTag、物件偵測的詳細說明
- [PhotonVision 官方文件](https://docs.photonvision.org/)：協同處理器的安裝、相機校正
- [WPILib 文件：Pose Estimators](https://docs.wpilib.org/en/latest/docs/software/advanced-controls/state-space/state-space-pose-estimators.html)：Pose estimator 的原理
