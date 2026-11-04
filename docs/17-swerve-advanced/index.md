# 第 17 章 Swerve 進階

--8<-- "alpha-warning.md"

[第 13～16 章](../13-swerve-drive/index.md) 的 Swerve 已經能開、能跑自動程式。這一章參考 FRC 強隊 **254（The Cheesy Poofs）** 等隊伍公開的做法，再往上加三個優化，讓 Swerve 在急轉方向、急加速、被撞的時候都更穩。

## 這章學什麼

- **Swerve Setpoint Generator**：讓四個模組的變化永遠「做得到」，急轉方向時不會互相拉扯
- **加速度 Feedforward**：把預期的加速度告訴 TalonFX，加速時速度跟得更緊
- **保持朝向**：放開右搖桿時自動鎖住朝向；按十字鍵一鍵轉到指定方向

**需要先讀**：[第 13～16 章](../13-swerve-drive/index.md)。這一章的程式是在[第 16 章](../16-swerve-auto/index.md) 最後的版本上修改

## 在模擬裡看到的差別

在[第 15 章](../15-swerve-sim/index.md) 的模擬裡測試（數字都是實測）：

| 測試 | 改進前 | 改進後 |
| --- | --- | --- |
| 以 3 m/s 往前開，突然改往左：朝向偏了多少 | −63 度（第 16 章） | 約 0 度（Setpoint Generator） |
| 同上：改方向後 0.1 秒，有沒有往錯的方向滑 | 有，往右 1.14 m/s（第 16 章） | 沒有（Setpoint Generator） |
| 從 0 加速到 4 m/s：速度平均落後多少 | 0.37 m/s（沒有 kA） | 0.16 m/s（加速度 Feedforward） |
| 放開右搖桿後，一邊平移一邊保持朝向 | 不會鎖定 | 保持在 ±2 度以內（保持朝向） |

## 本章內容

| 小節 | 內容 |
| --- | --- |
| [Swerve Setpoint Generator](setpoint-generator.md) | 為什麼需要、它在做什麼、用 PathPlannerLib 的版本 |
| [加速度 Feedforward](acceleration-ff.md) | kA、`withAcceleration()`、完整的 `SwerveModule` 和 `Drivetrain` |
| [保持朝向](heading-lock.md) | Heading lock、一鍵轉向、`TeleopDrive` |
| [延伸學習](further.md) | 高頻率 Odometry、打滑偵測、254 的原始版本 |
