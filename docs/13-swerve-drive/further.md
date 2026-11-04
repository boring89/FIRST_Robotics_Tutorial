# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 全 CTRE 或全 REV 時哪裡不一樣

這幾章的程式是混搭的，但架構和其他組合完全一樣：`SwerveModule` 管三個硬體，`Drivetrain` 管 Kinematics 和 IMU。換硬體時只要改 `SwerveModule`。

| 組合 | 轉向的做法 | 現成方案 |
| --- | --- | --- |
| **全 CTRE**（TalonFX + TalonFX + CANcoder） | TalonFX 可以直接用 CANcoder 當回授（`FeedbackSensorSourceValue.FusedCANcoder` 或 `RemoteCANcoder`），不需要校正內建編碼器 | Phoenix Tuner X 的 **Swerve Generator** |
| **全 REV**（SparkMax/SparkFlex + Through Bore Encoder） | 絕對式編碼器接在 SPARK 的資料埠上，SPARK 直接用它跑轉向 PID | REV 的 **MAXSwerve** 範例程式 |
| **混搭**（這幾章） | CANcoder 是 CTRE 的，SparkMax 讀不到它，只能在 Systemcore 上跑 PID，或用 CANcoder 校正 NEO 的編碼器（[第 14 章](../14-swerve-tuning/onboard-pid.md)） | 沒有，要自己寫（或用 YAGSL） |

## CAN 匯流排的分配

這幾章把 12 個裝置都接在 `CAN_S0`。每個裝置每秒都要送很多次資料，一條匯流排上的裝置越多，**CAN 使用率**越高；使用率太高時，資料會延遲或遺失。

Systemcore 有 5 條 CAN 匯流排，可以把底盤分開：

```java
// 例如：左邊的兩個模組接 CAN_S0，右邊的兩個接 CAN_S1
public record ModuleConstants(
    String name, CANPort bus, int driveId, int steerId, int encoderId,
    double encoderOffsetRotations, Translation2d location) {}
```

在 `ModuleConstants` 加上 `CANPort`，`SwerveModule` 建立裝置時改用 `constants.bus()` 就好。Driver Station 和 Phoenix Tuner X 都可以看到每條匯流排的使用率。

## 用 Pigeon 2 代替內建 IMU

Systemcore 的安裝位置不一定理想（例如離機器人中心很遠、裝在會震動的地方）。改用 CTRE 的 Pigeon 2 時，只要改 `Drivetrain` 的 IMU 部分，寫法見[第 6 章](../06-motors-sensors/gyro.md)。其他程式都透過 `getHeading()` 讀朝向，不用修改。

## 進一步閱讀

- [CTRE 文件：Swerve](https://v6.docs.ctr-electronics.com/en/latest/docs/api-reference/mechanisms/swerve/swerve-overview.html)：CTRE 的 Swerve API 和 Swerve Generator
- [REV 文件](https://docs.revrobotics.com/)：REVLib 和 MAXSwerve 模組的說明
- [YAGSL](https://docs.yagsl.com/)：支援混用各家硬體的 Swerve 函式庫，看看它的 JSON 設定檔，和這章的 `Constants.java` 比較
