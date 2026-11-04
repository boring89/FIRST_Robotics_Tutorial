# 第 15 章 Swerve 模擬

--8<-- "alpha-warning.md"

機器人只有一台，程式組卻常常要等機構組做完才能測試。**模擬 (Simulation)** 讓你在電腦上執行**真正的機器人程式**：同樣的 `Drivetrain`、同樣的 Command、同樣的手把綁定，只是馬達和感測器換成了電腦算出來的模型。

這一章讓[第 13、14 章](../13-swerve-drive/index.md) 的 Swerve 可以在模擬裡開。[第 16 章](../16-swerve-auto/index.md) 的 Odometry 和 PathPlanner 也都能在模擬裡測試。

!!! info "和第 12 章的模擬器不一樣"
    [第 12 章](../12-mechanisms/index.md) 的模擬器是用 JavaScript 重寫的網頁，用來建立直覺。這一章是 **WPILib 的模擬**：在電腦上執行你寫的 Java 程式，程式碼和部署到機器人上的完全一樣。

## 這章學什麼

- WPILib 模擬怎麼運作：`simulationPeriodic()`、廠商的模擬狀態、物理模型
- 用 `DCMotorSim` 模擬驅動和轉向馬達
- 模擬 TalonFX、SparkMax、CANcoder 和內建 IMU
- 在 Sim GUI 用手把開車，在 AdvantageScope 看四個輪子
- 模擬的限制：哪些結果可以相信、哪些不行

**需要先讀**：[第 14 章 Swerve 優化與調整](../14-swerve-tuning/index.md)

## 本章內容

| 小節 | 內容 |
| --- | --- |
| [模擬怎麼運作](sim-basics.md) | 啟動模擬、Sim GUI、程式怎麼知道自己在模擬 |
| [模擬 Swerve 模組](module-sim.md) | `ModuleSim`：馬達模型和廠商的模擬狀態 |
| [用 AdvantageScope 看模擬](advantagescope.md) | 開車、看輪子、比較目標和實際值 |
| [延伸學習](further.md) | 用模擬寫自動測試、更完整的物理模擬 |
