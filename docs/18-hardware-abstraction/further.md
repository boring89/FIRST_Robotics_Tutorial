# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 視覺的硬體抽象化

[第 16 章的 `Vision`](../16-swerve-auto/vision.md) 直接讀 Limelight，重播時讀不到。照同樣的方法做一個 `VisionIO`：

```java
public interface VisionIO {
  @AutoLog
  class VisionIOInputs {
    public boolean hasTarget = false;
    public double txDegrees = 0.0;
    /** 這一圈有沒有新的、有效的位置估計。 */
    public boolean poseValid = false;
    public Pose2d pose = Pose2d.ZERO;
    public double timestampSeconds = 0.0;
    /** X、Y、朝向的標準差。 */
    public double[] stdDevs = new double[] {0.0, 0.0, 0.0};
  }

  default void updateInputs(VisionIOInputs inputs) {}

  /** MegaTag2 需要知道機器人的朝向（這是輸出，送給相機）。 */
  default void setRobotOrientation(double degrees) {}
}
```

`VisionIOLimelight` 把第 16 章 `Vision.periodic()` 裡讀 Limelight 的部分搬進 `updateInputs()`，`Vision` Subsystem 只剩下「有效的話就交給 `Drivetrain.addVisionMeasurement()`」。標準差在 IO 裡轉成 `double[]`，`Vision` 再用 `VecBuilder.fill(...)` 轉回 `Matrix`。模擬和重播時用空的 `new VisionIO() {}`。

## 可以重播的自動程式選單

WPILib 的 `Selectable` 選單在重播時讀不到當時選了哪一個。AdvantageKit 的 **`LoggedNetworkChooser`** 可以把它包起來，把選擇記錄成輸入：

```java
private final LoggedNetworkChooser<Command> autoChooser =
    new LoggedNetworkChooser<>("Auto Chooser", buildAutoChooser());

public Command getAutonomousCommand() {
  return autoChooser.get();
}
```

它會自己發布到 NetworkTables，所以原本的 `Tunables.publish(...)` 不用再寫。`LoggedNetworkNumber`、`LoggedNetworkBoolean` 則可以用在比賽中可以調整的數值（例如 PID 參數），重播時也會讀回當時的值。

## WPILib 的 Epilogue

WPILib 2027 內建了一個比較輕量的 log 工具 **Epilogue**：在類別或欄位上加 `@Logged` 註解，就會自動把數值記錄到 log。它比 AdvantageKit 簡單，不需要硬體抽象化，但**不支援重播**。如果隊上只想要「自動記錄很多資料」，Epilogue 就夠了；想要重播，才需要 AdvantageKit 和硬體抽象化。

## 用假的 IO 寫測試

有了硬體抽象化，寫 JUnit 測試時可以傳入自己寫的「假 IO」，直接指定感測器的讀值，測試邏輯對不對：

```java
/** 角度永遠是 170 度的假模組，用來測試 optimize。 */
class FakeModuleIO implements ModuleIO {
  Rotation2d lastSteerTarget;

  @Override
  public void updateInputs(ModuleIOInputs inputs) {
    inputs.steerAngle = Rotation2d.fromDegrees(170);
  }

  @Override
  public void setSteerAngle(Rotation2d angle) {
    lastSteerTarget = angle;
  }
}
```

給它一個 −170 度的目標，檢查 `lastSteerTarget` 是不是 −170 度（只轉 20 度），而不是被 `optimize` 翻成 10 度。不需要模擬馬達，也不用等真實的時間，測試很快就跑完。

## 進一步閱讀

- [AdvantageKit 文件](https://docs.advantagekit.org/)：完整的說明，包括 IO 架構的建議、Swerve 範本、高頻率 Odometry
- [AdvantageKit 的 Releases](https://github.com/Mechanical-Advantage/AdvantageKit/releases)：2027 alpha 的版本和範本專案
- [FRC 6328 的程式碼](https://github.com/Mechanical-Advantage)：AdvantageKit 作者的機器人程式，IO 架構的實際例子
