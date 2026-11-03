# 第 6 章 馬達與感測器

--8<-- "alpha-warning.md"

第 5 章的 Subsystem 只會「讓馬達轉」和「讀一個光遮斷感測器」。這章要讓機構變得更聰明：設定好馬達控制器，讀編碼器知道轉了多少、轉多快，用 IMU 知道機器人面向哪裡，用極限開關保護機構。

## 這章學什麼

- 設定馬達的方向、Idle mode（Brake / Coast）、電流限制
- 讀取編碼器的位置和速度，並換算成有意義的單位
- 用絕對式編碼器讀取手臂角度
- 讀取 Systemcore 內建 IMU 或 Pigeon 2 的朝向
- 用極限開關保護機構

**需要先讀**：[第 2 章 感測器](../02-hardware/sensors.md)、[第 5 章 Command-based 架構](../05-commands/index.md)

## REV 和 CTRE 的差異

這章的每個範例都有 REV 和 CTRE 兩種寫法。兩家的觀念一樣，但設定方式和單位不同，先有個印象：

| | REV（REVLib） | CTRE（Phoenix 6） |
| --- | --- | --- |
| 設定方式 | 建立 `SparkMaxConfig` 物件，用 `configure()` 送出 | 建立 `TalonFXConfiguration` 物件，用 `getConfigurator().apply()` 送出 |
| 位置單位 | 馬達轉的圈數 (rotations) | 馬達轉的圈數 (rotations) |
| 速度單位 | **RPM**（每分鐘轉數） | **rotations per second**（每秒轉數） |
| 讀值的寫法 | `encoder.getPosition().get(0.0)` | `motor.getPosition().getValueAsDouble()` |

!!! warning "注意：速度單位不一樣"
    REV 的速度是**每分鐘**轉數，CTRE 是**每秒**轉數，差了 60 倍。從網路上抄範例時，一定要確認單位。

## 這章的範例

延續第 5 章的機器人，並新增一個機構：

- **Shooter**：加上設定和編碼器，判斷轉速夠不夠快再發射
- **Arm**（手臂，新的）：用絕對式編碼器讀角度，用極限開關防止轉過頭
- **Drivetrain**（底盤）：先只放 IMU，第 7 章再加上馬達

## 本章內容

| 小節 | 內容 |
| --- | --- |
| [馬達控制器設定](motor-config.md) | 反轉、Idle mode、電流限制 |
| [編碼器](encoders.md) | 讀位置和速度、單位換算、絕對式編碼器 |
| [陀螺儀與 IMU](gyro.md) | 讀取機器人朝向 |
| [數位感測器](digital-sensors.md) | 極限開關、光遮斷感測器、軟體限位 |
| [延伸學習](further.md) | Follower、訊號更新頻率、Signal 的有效性 |
