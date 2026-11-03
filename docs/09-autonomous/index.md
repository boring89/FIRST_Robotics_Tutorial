# 第 9 章 自動階段

--8<-- "alpha-warning.md"

**Autonomous period（自動階段）** 是比賽一開始的十幾秒，機器人完全照著程式自己動，駕駛不能操控。這段時間的得分通常比較高，強隊和一般隊伍的差距，常常就在自動階段。

## 這章學什麼

- 用 `Selectable` 讓駕駛在比賽前選擇要執行哪一個自動程式
- 從最簡單的「用時間控制」開始，逐步進步到「用編碼器控制」
- 用 Odometry（里程計）讓機器人知道自己在場上的哪裡
- 用 PathPlanner 畫路徑，讓機器人沿著路徑走

**需要先讀**：[第 5 章 組合 Command](../05-commands/composition.md)、[第 7 章 差速底盤](../07-drivetrain/differential.md)、[第 8 章 PID](../08-control/pid.md)

## 自動程式就是一個 Command

在 Command-based 架構裡，**整個自動程式就是一個（組合起來的）Command**。範本的 `Robot.java` 已經寫好了：

- 進入 Autonomous 時，呼叫 `robotContainer.getAutonomousCommand()`，拿到 Command 並啟動
- 離開 Autonomous 時，取消這個 Command

所以寫自動程式，就是用[第 5 章](../05-commands/composition.md)學的 `andThen`、`alongWith`、`withTimeout` 等方式，把各個機構的 Command 組合起來。

## 循序漸進

| 方法 | 怎麼知道走了多遠 | 準確度 | 本章 |
| --- | --- | --- | --- |
| **用時間** | 不知道，只知道過了幾秒 | 差，受電池電量影響 | [用時間控制](timed.md) |
| **用編碼器** | 量輪子轉了幾圈 | 直線還不錯，轉彎會累積誤差 | [用編碼器控制](encoder-based.md) |
| **Odometry** | 結合編碼器和 IMU，算出在場上的座標 | 好，但時間久了會漂移 | [Odometry](odometry.md) |
| **PathPlanner** | 用 Odometry 跟著畫好的路徑走 | 好，路徑可以是曲線 | [PathPlanner](pathplanner.md) |
| **加上視覺** | 用 AprilTag 修正 Odometry 的漂移 | 最好 | [第 10 章](../10-vision/index.md) |

新手隊伍第一場比賽，**能穩定執行的簡單自動程式**比複雜但不穩定的好得多。先求有、再求好。

## 選擇自動程式：`Selectable`

比賽前，機器人的起始位置、聯盟隊友的策略都不一樣，所以通常會準備好幾個自動程式，在 Dashboard 上選擇要執行哪一個。2027 用 **`Selectable`**：

```java
private final Selectable<Command> autoChooser = new Selectable<>();

// 在 RobotContainer 的建構子裡
autoChooser.addDefault("什麼都不做", Commands.none());
autoChooser.add("往前開 2 秒", Autos.driveForwardTimed(drivetrain));
Tunables.publish("Auto Chooser", autoChooser);

// getAutonomousCommand()
return autoChooser.getSelected();
```

在 **Elastic** 上加入 `Auto Chooser` 這個元件，就能用下拉選單選擇。

!!! info "2027 新改動"
    2026 以前用的是 `SendableChooser` 搭配 `SmartDashboard.putData()`。2027 改成 `Selectable` 搭配 `Tunables.publish()`，而且需要 Elastic alpha 9 以上的版本才能顯示。

!!! tip "小技巧：預設選項要安全"
    `addDefault` 設定的是沒有選擇時的預設值。建議設成一個**不會出錯**的自動程式（例如什麼都不做，或只是往前開出起始區），萬一忘記選，機器人也不會做出危險的動作。

## 本章內容

| 小節 | 內容 |
| --- | --- |
| [用時間控制](timed.md) | 最簡單的自動程式、`Autos` 類別 |
| [用編碼器控制](encoder-based.md) | 走指定距離、轉指定角度 |
| [Odometry](odometry.md) | `Pose2d`、讓機器人知道自己在哪裡 |
| [PathPlanner](pathplanner.md) | 畫路徑、Named commands、自動程式選單 |
| [延伸學習](further.md) | Choreo、即時路徑規劃、Swerve 搭配 PathPlanner |
