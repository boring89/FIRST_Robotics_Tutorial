# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 慢速模式

需要精準對位時（例如靠近得分位置），駕駛常常希望底盤變慢。可以綁一個按鈕，按住時用比較慢的速度開車：

```java
// 在 configureBindings() 裡：按住左肩鍵時，速度降到 30%
driver.leftBumper().whileTrue(
    drivetrain.arcadeDriveCommand(
        () -> -driver.getLeftY() * 0.3, () -> -driver.getRightX() * 0.3));
```

按住左肩鍵時，這個 Command 會中斷 default command；放開後 default command 自動回來。這就是 [Requirements](../05-commands/why.md#requirements一個機構同時只能做一件事) 和 default command 合作的方式。

## 限制加速度：`SlewRateLimiter`

搖桿從 0 突然推到底，底盤會瞬間全速，機器人可能翹頭、打滑，或是讓上面的機構晃動。**`SlewRateLimiter`** 限制數值每秒最多能變化多少，讓加速比較平順：

```java
// 每秒最多變化 3.0，也就是從 0 加速到全速至少要 1/3 秒
private final SlewRateLimiter forwardLimiter = new SlewRateLimiter(3.0);

// 在 configureDefaultCommands() 裡
drivetrain.setDefaultCommand(
    drivetrain.arcadeDriveCommand(
        () -> forwardLimiter.calculate(-driver.getLeftY()), () -> -driver.getRightX()));
```

`SlewRateLimiter` 有記憶，每個要限制的數值要用**自己的**一個 `SlewRateLimiter` 物件。

## Curvature drive

`DifferentialDrive` 還有第三種操控方式 **Curvature drive**（也叫 Cheesy drive）：右搖桿控制的是**轉彎的弧度**，而不是轉彎的速度。高速時轉彎比較穩，賽車遊戲的手感：

```java
drive.curvatureDrive(forward, rotation, allowTurnInPlace);
```

`allowTurnInPlace` 為 `true` 時可以原地轉，通常綁一個按鈕切換。

## Swerve 的場地座標控制

[Swerve 入門](swerve.md#robot-relative-和-field-relative) 介紹了場地座標的觀念，實際的 default command 大概長這樣（以 `ChassisVelocities` 為例，`MAX_SPEED`、`MAX_ANGULAR_SPEED` 是底盤的最高速度）：

```java
ChassisVelocities fieldRelative = new ChassisVelocities(
    -driver.getLeftY() * MAX_SPEED,          // 往前
    -driver.getLeftX() * MAX_SPEED,          // 往左（搖桿往右是正，所以加負號）
    -driver.getRightX() * MAX_ANGULAR_SPEED); // 逆時針
ChassisVelocities robotRelative = fieldRelative.toRobotRelative(getHeading());
```

注意**紅藍聯盟**的場地方向是相反的：在紅色聯盟那一邊，「遠離駕駛的方向」在場地座標裡其實是往後。比賽用的程式要根據聯盟顏色調整，[第 9 章](../09-autonomous/odometry.md) 會再介紹。

## 進一步閱讀

- [WPILib 文件：Drive classes](https://docs.wpilib.org/en/latest/docs/software/hardware-apis/motors/wpi-drive-classes.html)：`DifferentialDrive` 等類別的說明
- [WPILib 文件：Kinematics and Odometry](https://docs.wpilib.org/en/latest/docs/software/kinematics-and-odometry/index.html)：Swerve、差速、Mecanum 的 Kinematics（部分內容可能還是 2026 的類別名稱）

## 圖片來源

這一章用到的網路圖片，依照各自的授權條款使用。

| 圖片 | 原始檔案 | 作者 | 授權 | 修改 |
| --- | --- | --- | --- | --- |
| [Swerve 底盤](swerve.md#swerve-模組) | [WCPSwerveX2i.png](https://github.com/wpilibsuite/frc-docs/blob/main/source/docs/software/hardware-apis/motors/images/WCPSwerveX2i.png) | [WPILib frc-docs](https://github.com/wpilibsuite/frc-docs) 貢獻者 | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.zh-hant) | 轉成 JPG |
