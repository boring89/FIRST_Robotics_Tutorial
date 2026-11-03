# 第 7 章 底盤

--8<-- "alpha-warning.md"

底盤 (Drivetrain) 是機器人最重要的機構：不管其他機構做得多好，機器人到不了該去的地方就什麼都做不了。這章介紹 FRC 最常見的兩種底盤，並把它們寫成 Subsystem。

## 這章學什麼

- 常見底盤種類的優缺點
- 寫一個差速底盤的 Subsystem，用 Arcade drive 操控
- Swerve 底盤的運作原理和 Kinematics（運動學）
- 寫 Swerve 程式時有哪些現成的工具可以用

**需要先讀**：[第 5 章 Command-based 架構](../05-commands/index.md)、[第 6 章 馬達與感測器](../06-motors-sensors/index.md)

## 常見的底盤種類

| 種類 | 怎麼動 | 優點 | 缺點 |
| --- | --- | --- | --- |
| **Differential（差速）**，也叫 Tank drive | 左右兩邊的輪子各自控制，靠速度差轉彎 | 機構簡單、便宜、耐撞、程式簡單 | 不能橫移 |
| **Swerve（全向轉向）** | 每個輪子都能獨立轉向和驅動 | 可以往任何方向移動，同時自轉 | 機構複雜、貴、程式比較難 |
| **Mecanum（麥克納姆輪）** | 特殊滾輪讓機器人可以橫移 | 可以橫移，機構比 Swerve 簡單 | 推力差、容易被推動 |

近年來的 FRC 比賽，強隊幾乎都用 Swerve。但對新手隊伍來說，**差速底盤是學習的最好起點**：程式簡單、機構穩定，先把差速底盤做好，再挑戰 Swerve。

## 座標與方向

WPILib 描述機器人的移動時，統一使用這套座標：

```text
          +X（前）
            ▲
            │
 +Y（左） ◄──┼──
            │
         機器人中心

旋轉：從上往下看，逆時針 (CCW) 為正
```

- **X**：往機器人的**前方**為正
- **Y**：往機器人的**左方**為正
- **旋轉**：**逆時針**為正（和 [IMU](../06-motors-sensors/gyro.md#角度的方向) 一樣）

## 本章內容

| 小節 | 內容 |
| --- | --- |
| [差速底盤](differential.md) | 用 `DifferentialDrive` 寫差速底盤、Arcade drive 和 Tank drive |
| [Swerve 入門](swerve.md) | Swerve 的原理、Kinematics、現成方案 |
| [延伸學習](further.md) | 場地座標、加速度限制、慢速模式 |
