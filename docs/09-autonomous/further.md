# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 自動程式的設計建議

- **每個步驟都要有逾時保護**：感測器壞了、被別的機器人擋住時，自動程式不能卡在某一步不動，浪費整個自動階段
- **準備好幾個版本**：不同起始位置、配合不同的聯盟隊友。比賽前和隊友討論誰負責哪一條路線，避免撞在一起
- **最簡單的版本要最穩**：一個「只離開起始區」的版本，通常就能拿到分數，可以當作預設選項
- **練習場就要測紅藍兩邊**：很多隊伍到了比賽才發現紅色聯盟的路徑是錯的

## Swerve 搭配 PathPlanner

Swerve 底盤使用 PathPlanner 時，`AutoBuilder.configure` 的控制器改用 **`PPHolonomicDriveController`**，需要兩組 PID 參數：一組修正位置（平移），一組修正朝向（旋轉）：

```java
new PPHolonomicDriveController(
    new PIDConstants(5.0, 0.0, 0.0),   // 平移
    new PIDConstants(5.0, 0.0, 0.0))   // 旋轉
```

`driveRobotRelative` 則是把 `ChassisVelocities` 交給 [Swerve 的 Kinematics](../07-drivetrain/swerve.md)，換算成四個模組的速度和角度。用 [CTRE Swerve Generator 或 YAGSL](../07-drivetrain/swerve.md#現成的-swerve-方案) 的話，它們通常已經提供了和 PathPlanner 整合的方法。

## 即時路徑規劃 (Pathfinding)

PathPlanner 除了照著事先畫好的路徑走，還可以**即時規劃**：給一個目標位置，PathPlannerLib 會自己找一條避開場地障礙物的路徑。

```java
Command goToScoring = AutoBuilder.pathfindToPose(
    new Pose2d(2.0, 5.5, Rotation2d.fromDegrees(180)),
    new PathConstraints(3.0, 3.0, Math.toRadians(540), Math.toRadians(720)),
    0.0);   // 到達時的速度
```

這在手動階段也很好用：按一個按鈕，機器人自己開到得分位置。不過它非常依賴 Odometry 的準確度，通常要搭配[第 10 章](../10-vision/index.md) 的視覺定位。

## Choreo

**Choreo** 是另一個路徑規劃工具。和 PathPlanner 不同的是，它會用數學最佳化算出**在機器人物理限制下最快**的軌跡，適合追求極限速度的自動程式。2027 alpha 7 的 vendordep 清單裡有 **ChoreoLib**。

| | PathPlanner | Choreo |
| --- | --- | --- |
| 路徑怎麼來 | 自己拖曳控制點畫出來 | 給定經過的點，工具算出最快的軌跡 |
| 上手難度 | 容易 | 需要準確的機器人參數 |
| 即時路徑規劃 | 有 | 沒有 |

新手建議先用 PathPlanner，熟悉之後再試試 Choreo。PathPlannerLib 也可以讀取 Choreo 產生的軌跡。
