# 第 14 章 Swerve 優化與調整

--8<-- "alpha-warning.md"

[第 13 章](../13-swerve-drive/index.md) 的 Swerve 已經可以開了，但還有很多地方可以更好：輪子有時候會繞遠路轉 180 度、轉向還沒到位就開始全速滾、搖桿的手感很「衝」、參數也只是估計值。這一章一個一個改進。

## 這章學什麼

- **Optimize**：輪子要轉超過 90 度時，改成反方向轉、倒著滾
- **Cosine 補償**：轉向還沒到位時先放慢
- 把轉向的 PID 從 Systemcore 搬到 SparkMax 上，用 CANcoder 校正 NEO 的編碼器
- 駕駛手感：Deadband、輸入曲線、慢速模式、X 字鎖定
- 照順序調整轉向和驅動的參數

**需要先讀**：[第 13 章 從零打造 Swerve](../13-swerve-drive/index.md)

## 改了什麼

| 小節 | 改動的檔案 | 效果 |
| --- | --- | --- |
| [Optimize 與 Cosine 補償](optimize.md) | `SwerveModule.setTarget()` | 輪子最多只轉 90 度，轉向時不會亂推 |
| [SparkMax 轉向 PID](onboard-pid.md) | `SwerveModule`、`Constants` | 轉向的 PID 每 1 ms 算一次，反應更快 |
| [駕駛手感](driver-input.md) | `RobotContainer`、`Drivetrain` | 搖桿更好微調、可以慢速和鎖定 |
| [調整參數](tuning.md) | `Constants` | 轉向不抖、速度準確 |
| [延伸學習](further.md) | | 重新校正編碼器、斜移補償、SysId |
