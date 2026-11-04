# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 轉向的 SysId

[第 14 章的延伸學習](../14-swerve-tuning/further.md#轉向的-feedforward) 提到轉向也可以加 Feedforward。kS、kV 一樣可以用 SysId 量：驅動馬達停止，只對轉向馬達給電壓。

```java
/** 驅動馬達停止，轉向馬達直接給電壓。量轉向的 Feedforward 時用。 */
public void runSteerCharacterization(double volts) {
  driveMotor.stopMotor();
  steerMotor.setVoltage(volts);
}

/** 記錄轉向馬達：REVLib 2027 沒有換算係數，單位是馬達的圈數和 RPM。 */
public void logSteerSysId(SysIdRoutineLog log) {
  log.motor("steer-" + name)
      .voltage(Volts.of(steerMotor.getAppliedOutput().get(0.0) * steerMotor.getBusVoltage().get(0.0)))
      .angularPosition(Rotations.of(steerEncoder.getPosition().get(0.0)))
      .angularVelocity(RPM.of(steerEncoder.getVelocity().get(0.0)));
}
```

轉向可以一直轉，不會跑出場地，所以可以**架高**測試，也可以用比較高的電壓。不過轉向有 CANcoder 的線，一直轉下去線會被扯到：每個測試幾秒就放開，或確認你們的模組可以無限旋轉。

## 量轉動慣量

[第 17 章](../17-swerve-advanced/setpoint-generator.md) 的 `ROBOT_MOI = 6.0` 是估計的。**Moment of inertia（轉動慣量）**也可以用 SysId 量：再跑一次驅動的 SysId，但這次四個輪子都轉到**繞著中心旋轉**的方向，機器人會原地旋轉。

```java
private static final Translation2d[] MODULE_LOCATIONS = {
  SwerveConstants.FRONT_LEFT.location(),
  SwerveConstants.FRONT_RIGHT.location(),
  SwerveConstants.BACK_LEFT.location(),
  SwerveConstants.BACK_RIGHT.location(),
};

/** 四個輪子轉到和旋轉方向相切的角度，驅動馬達給同樣的電壓：機器人會原地逆時針旋轉。 */
public void runRotationCharacterization(double volts) {
  for (int i = 0; i < modules.length; i++) {
    // 輪子朝向「位置的方向 + 90 度」，就是繞著中心逆時針轉的方向
    Rotation2d tangent = MODULE_LOCATIONS[i].getAngle().orElseThrow().plus(Rotation2d.fromDegrees(90));
    modules[i].runCharacterization(volts, tangent);
  }
}
```

用它建立第二個 `SysIdRoutine`（記錄的方式和直線的一樣，用 `logSysId`），跑完之後得到「旋轉時」的 kA。兩個 kA 的單位都是輪子的圈數，可以直接比較：

- 直線時，四個輪子推的是整台機器人的**質量** m
- 旋轉時，四個輪子在距離中心 R 的地方推，推的是**轉動慣量** I

推導下來，**I = m × R² × (旋轉的 kA ÷ 直線的 kA)**，R 是模組到中心的距離（這一章是 0.409 公尺）。例如 m = 50 公斤，直線的 kA = 0.047，旋轉的 kA = 0.035：I = 50 × 0.409² × (0.035 ÷ 0.047) ≈ 6.2 kg·m²。

[PathPlanner 的文件](https://pathplanner.dev/robot-config.html) 也有用 SysId 算轉動慣量的公式，它用的單位不同（旋轉的 kA 用機器人的角度、直線的 kA 用公尺），寫法看起來不一樣，原理是相同的。

這一節沒有在模擬上驗證：[第 15 章的模擬](../15-swerve-sim/module-sim.md#模擬的限制) 把四個模組當成互相獨立的，沒有整台機器人的轉動慣量，量不出有意義的值。

## 用第 18 章的架構

用了[第 18 章的硬體抽象化](../18-hardware-abstraction/index.md)，量測用的方法要經過 `ModuleIO`：

| 這一章的寫法 | 第 18 章的寫法 |
| --- | --- |
| `driveMotor.setControl(voltageRequest...)` | `ModuleIO` 加上 `setDriveVoltage(double volts)`，`ModuleIOReal` 實作 |
| `driveMotor.getPosition()` × 2π | `inputs.drivePositionMeters ÷ 輪子半徑`：除以同一個半徑，剛好換回輪子轉的弧度 |
| `driveMotor.getStatorCurrent()` | `inputs.driveCurrentAmps`（已經在輸入資料裡） |
| `setDriveCurrentLimit()` | `ModuleIO` 加上 `setDriveCurrentLimit(double amps)` |

SysId 的記錄方式也不一樣。AdvantageKit 已經記錄了所有的輸入，只要再記錄**目前在跑哪一個測試**：

```java
new SysIdRoutine.Config(
    Volts.of(1).per(Second), Volts.of(2), Seconds.of(5),
    state -> Logger.recordOutput("Drivetrain/SysIdState", state.toString()))
```

`Mechanism` 的記錄參數傳 `null`。AdvantageKit 的 log 檔**不能直接**給 SysId 工具：要先用 AdvantageScope 打開，**File → Export Data**，格式選 WPILOG、時間選 **AdvantageKit Cycles**，再把匯出的檔案交給 SysId（[AdvantageKit 文件：SysId Compatibility](https://docs.advantagekit.org/data-flow/sysid-compatibility/)）。

## 用 CTRE 的工具量

CTRE 的 Phoenix Tuner X 有 **Swerve Project Generator**，產生的 Swerve 程式裡有 SysId 的 Command，用 **SignalLogger** 記錄更高頻率的資料（[第 8 章](../08-control/sysid.md#執行測試和分析) 提過）。用全 CTRE 的 Swerve 時可以參考它的做法（2027 alpha 的支援情況以 CTRE 的文件為準）。這一章用的是 WPILib 的 `SysIdRoutine`，TalonFX 和 SparkMax 混用時比較方便。

## 進一步閱讀

- [WPILib 文件：SysId](https://docs.wpilib.org/en/latest/docs/software/advanced-controls/system-identification/index.html)：測試和分析的完整說明
- [AdvantageKit 的 Swerve 範本](https://github.com/Mechanical-Advantage/AdvantageKit/tree/main/template_projects/sources/spark_swerve)：`DriveCommands` 裡有 Feedforward 和輪子半徑的量測
- [PathPlanner 文件：Robot Config](https://pathplanner.dev/robot-config.html)：每個機器人設定的意思和量法
