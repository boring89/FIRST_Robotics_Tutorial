# 第 19 章 Superstructure 與狀態機

--8<-- "alpha-warning.md"

到[第 8 章](../08-control/index.md) 為止，Intake、Arm、Shooter 都能各自動得很好：Intake 吸到物件會停、手臂用 Motion Profile 轉到指定角度、Shooter 維持在 4000 RPM。但比賽用的機器人不是三個各自獨立的機構，而是**一台機器**：手臂收起來 Intake 才碰得到地面，手臂轉到定位、飛輪轉到夠快才能發射。

這一章學怎麼把好幾個機構**協調**起來。很多隊伍把底盤以外、會互相影響的機構合稱 **Superstructure（上層結構）**，這一章也用這個名字。

## 這章學什麼

- 各個機構「各做各的」會出什麼問題
- 做法一：用 Command 組合加上條件，把好幾個機構串成一個動作
- **State machine（狀態機）** 的觀念：狀態、轉換、「想要的狀態」和「目前的狀態」
- 做法二：寫一個 `Superstructure` Subsystem，用狀態機協調 Intake、Arm、Shooter
- 用 JUnit 測試狀態機，不需要機器人

**需要先讀**：[第 5 章 Command-based 架構](../05-commands/index.md)、[第 8 章 Motion Profile](../08-control/motion-profile.md)。這一章的程式是在第 8 章最後的版本上修改，Java 的 `enum`、`record` 在[第 1 章的延伸學習](../01-java/further.md)。

## 這章的機器人

沿用第 8 章的 Intake、Arm、Shooter，並假設它們的位置是這樣：

| 動作 | 需要的條件 |
| --- | --- |
| 吸入 | 手臂在**收起**的位置（`STOWED_ANGLE_DEGREES`），Intake 才碰得到地面 |
| 發射 | 手臂在**發射角度**（`SCORE_ANGLE_DEGREES`），而且 Shooter 轉速到了 |
| 吐出 | 隨時都可以（例如吸到兩個、或吸歪了） |

你們的機器人條件一定不一樣，但方法是相同的：**先列出每個動作需要什麼條件**，再決定程式怎麼保證這些條件。

## 本章內容

| 小節 | 內容 |
| --- | --- |
| [各做各的問題](problem.md) | 手臂還沒到就發射、按鈕互相搶機構、駕駛要記順序 |
| [做法一：組合 Command](commands.md) | 用 `waitUntil`、`deadlineFor` 串成一個動作，以及這個做法的極限 |
| [狀態機](state-machine.md) | 狀態、轉換、狀態圖，「想要的」和「目前的」狀態 |
| [做法二：Superstructure](superstructure.md) | 用狀態機協調三個機構的 `Superstructure` Subsystem |
| [測試狀態機](testing.md) | 用 JUnit 自動檢查每一種狀態轉換 |
| [延伸學習](further.md) | 自動程式怎麼用、碰撞保護、Commands v3 的 `StateMachine` |
