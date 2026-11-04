# FRC 程式新手教學

這是一份寫給 **FIRST Robotics Competition (FRC)** 新手的程式教學，使用 **Java** 和 **WPILib 2027**。就算你從來沒寫過程式，也可以從頭跟著做；已經會寫程式的人，可以直接跳到 FRC 的部分。

--8<-- "alpha-warning.md"

## 該從哪裡開始？

<div class="grid cards" markdown>

-   :material-sprout: **完全沒寫過程式**

    ---

    從 [第 0 章 開始之前](00-intro/index.md) 開始，依序讀 [第 1 章 Java 速成](01-java/index.md)。

-   :material-code-braces: **會寫一點程式**

    ---

    先讀 [第 2 章 FRC 基本元件認識](02-hardware/index.md)，再從 [第 3 章 環境建置](03-setup/index.md) 開始動手。

-   :material-robot-industrial: **用過 2026 以前的 WPILib**

    ---

    2027 有很多不相容的改動（package 改名、Systemcore、SmartDashboard 移除），建議先讀 [第 2 章](02-hardware/index.md) 和 [第 5 章](05-commands/index.md)。

</div>

## 章節一覽

| 章節 | 內容 |
| --- | --- |
| [第 0 章 開始之前](00-intro/index.md) | FRC 程式組在做什麼、這份教學怎麼讀 |
| [第 1 章 Java 速成](01-java/index.md) | 寫 FRC 程式需要的 Java 基礎（有基礎可跳過） |
| [第 2 章 FRC 基本元件認識](02-hardware/index.md) | Systemcore、Motioncore、馬達、感測器、Driver Station |
| [第 3 章 環境建置](03-setup/index.md) | 安裝 WPILib、建立專案、設定廠商工具 |
| [第 4 章 機器人程式基礎](04-robot-basics/index.md) | 程式的生命週期、讓馬達轉、讀手把 |
| [第 5 章 Command-based 架構](05-commands/index.md) | 用 Subsystem 和 Command 組織程式（Commands v2） |
| [第 6 章 馬達與感測器](06-motors-sensors/index.md) | REV / CTRE 馬達控制器、編碼器、IMU |
| [第 7 章 底盤](07-drivetrain/index.md) | 差速底盤、Swerve 入門 |
| [第 8 章 控制入門](08-control/index.md) | PID、Feedforward、Motion Profile、SysId |
| [第 9 章 自動階段](09-autonomous/index.md) | 從時間控制到 PathPlanner |
| [第 10 章 視覺](10-vision/index.md) | AprilTag、Limelight、PhotonVision |
| [第 11 章 實戰](11-practice/index.md) | Git 分工、看 log、賽場檢查清單 |
| [第 12 章 機構範例](12-mechanisms/index.md) | 在網頁上玩的 3D 機構模擬器 |
| [第 13 章 從零打造 Swerve](13-swerve-drive/index.md) | TalonFX + SparkMax + CANcoder，自己寫一套 Swerve |
| [第 14 章 Swerve 優化與調整](14-swerve-tuning/index.md) | Optimize、SparkMax 轉向 PID、駕駛手感、調參數 |
| [第 15 章 Swerve 模擬](15-swerve-sim/index.md) | 在電腦上模擬馬達和感測器，不用機器人也能測試 |
| [第 16 章 Swerve 自動階段](16-swerve-auto/index.md) | Odometry、PathPlanner、視覺修正位置 |
| [第 17 章 Swerve 進階](17-swerve-advanced/index.md) | 254 的 Setpoint Generator、加速度 Feedforward、保持朝向 |
| [第 18 章 硬體抽象化與 AdvantageKit](18-hardware-abstraction/index.md) | `ModuleIO` 架構、AdvantageKit、Log replay |
| [第 19 章 Superstructure 與狀態機](19-superstructure/index.md) | 協調 Intake、Arm、Shooter，用 JUnit 測試狀態機 |

每一章最後都有一節 **延伸學習**，放進階主題和外部資源，第一次讀可以先跳過。

## 一起改進這份教學

看到錯字、過時的內容，或有地方看不懂，可以到 [GitHub](https://github.com/boring89/FIRST_Robotics_Tutorial) 開 Issue，或直接點每頁右上角的編輯按鈕送 Pull request。
