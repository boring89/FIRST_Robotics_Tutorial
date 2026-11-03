# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。等你想深入了解硬體、或是要跟電控組一起接線時再回來看。

## 官方資料

- [SystemcoreTesting](https://github.com/wpilibsuite/SystemcoreTesting)：Systemcore 和 Motioncore 的 alpha / beta 測試 repo，裡面有規格書 (Specification)、線材說明、已知問題清單
- [WPILib 2027 新改動 (New for 2027)](https://docs.wpilib.org/en/latest/docs/yearly-overview/yearly-changelog.html)：2027 所有軟硬體改動的完整清單
- [WPILib 官方文件 (latest)](https://docs.wpilib.org/en/latest/)：2027 版的 WPILib 文件，仍在持續更新中

## 廠商文件

=== "REV"

    - [REV Robotics 官方文件](https://docs.revrobotics.com/)：SPARK MAX、SPARK Flex、NEO 系列的規格與接線說明

=== "CTRE"

    - [CTRE 官方文件](https://v6.docs.ctr-electronics.com/)：Phoenix 6、Talon FX、CANcoder、Pigeon 2 的說明

## 進階主題

### CAN 匯流排的頻寬

每個 CAN 裝置都會定期回報資料（狀態、位置、速度…）。回報頻率越高，占用的頻寬越多。裝置一多，匯流排使用率 (CAN utilization) 太高就會開始掉資料。Phoenix 6 和 REVLib 都可以調整每種資料的回報頻率，把不需要的資料調低或關掉。

想一想：底盤馬達的速度資料和 Intake 馬達的溫度資料，哪個需要比較高的回報頻率？

### 電壓不足 (Brownout)

電池電壓掉得太低時，控制系統會進入 **Brownout（電壓不足）** 保護，先關掉馬達輸出，避免主控制器當機重開。比賽中機器人突然「軟掉」幾秒，常常就是這個原因。之後在 [第 11 章 用 AdvantageScope 看 log](../11-practice/logging.md) 會教你怎麼從 log 找出 Brownout。

### 接線規則

FRC 對機器人的電路有詳細的規定，例如：每種元件要用多大的斷路器、電線要多粗、哪些地方要用什麼顏色的線。這些規定寫在每年的 **賽季手冊 (Game Manual)** 的機器人規則 (Robot rules) 章節。2027 的規則公布後，這節會整理程式組需要知道的部分。
