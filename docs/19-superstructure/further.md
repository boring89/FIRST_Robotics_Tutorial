# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 在 PathPlanner 的自動程式裡用

[第 9 章](../09-autonomous/pathplanner.md) 用 `NamedCommands` 讓 PathPlanner 的路徑在指定的地方執行 Command。用了 `Superstructure` 之後，註冊的是「設定 Goal，直到到達某個狀態」：

```java
NamedCommands.registerCommand("Intake",
    superstructure.goalCommand(Goal.INTAKE)
        .until(() -> superstructure.getState() == State.HOLDING));
NamedCommands.registerCommand("Score",
    superstructure.goalCommand(Goal.SCORE)
        .until(() -> superstructure.getState() == State.EMPTY));
```

和[做法二的 `getAutonomousCommand()`](superstructure.md#robotcontainer) 一樣，自動程式和駕駛用的是**同一套**狀態轉換規則。手動時測試過「手臂沒到位不會送料」，自動時就一定也不會。

`Intake` 最好再加上 `withTimeout(...)`：路徑走到了卻沒吸到物件時，不要讓整個自動程式停在這裡等。

## 碰撞保護：升降機構加手臂

很多機器人的手臂裝在升降機構上。升降機構在最低點時，手臂往外轉可能會撞到底盤或保險桿。這種「A 沒到某個位置之前，B 不能動」的規則叫 **Interlock（互鎖）**。

狀態機很適合處理互鎖：在輸出的地方加上條件，不用改轉換規則。

```java
case PREPARING -> {
  elevator.setTargetHeight(ElevatorConstants.SCORE_HEIGHT_METERS);
  // 升降機構還沒升到安全高度之前，手臂留在收起的位置
  if (elevator.getHeightMeters() > ElevatorConstants.SAFE_HEIGHT_METERS) {
    arm.setTargetAngle(ArmConstants.SCORE_ANGLE_DEGREES);
  } else {
    arm.setTargetAngle(ArmConstants.STOWED_ANGLE_DEGREES);
  }
  ...
}
```

收回來的時候順序反過來：手臂先收起，**手臂到位之後**升降機構才往下。收回的狀態也要加上同樣的條件，否則手臂還在外面，升降機構就先降下來了。

規則很多的時候，可以把「目前位置安不安全」寫成一個獨立的方法，所有狀態的輸出都經過它，就不會漏掉某一個狀態。

## Commands v3 的 `StateMachine`

[第 5 章的延伸學習](../05-commands/further.md#commands-v3) 介紹過 WPILib 2027 新的 Commands v3。v3 內建一個**宣告式**的狀態機 `StateMachine`：

- `addState(command)`：每個狀態對應一個 Command，進入狀態時執行
- `state1.switchTo(state2).when(條件)`：條件成立時，取消目前狀態的 Command，換到另一個狀態
- `switchTo(...).whenComplete()`：目前狀態的 Command 自己結束時才換
- `setInitialState(state)`：狀態機從哪個狀態開始

和這一章的寫法比，v3 的 `StateMachine` 把「每個狀態做什麼」交給 Command，轉換規則用宣告的方式寫。這一章的寫法則是把轉換規則寫成普通的 `switch`，好處是可以像[測試狀態機](testing.md) 那樣，不靠任何框架直接測試。隊上用的是 v2 的話，照這一章的寫法就可以；之後換到 v3，狀態圖和轉換表都還能沿用。

## 用狀態控制 LED 和 Dashboard

`superstructure.getState()` 是駕駛最想知道的資訊：「我拿到了沒？準備好了沒？」很多隊伍用 LED 燈條顯示狀態，例如 `EMPTY` 熄燈、`HOLDING` 綠色、`PREPARING` 閃爍、`SHOOTING` 白色。駕駛不用看 Dashboard，看機器人就知道。

做法和[做法二小練習第 1 題](superstructure.md#小練習) 的震動一樣：用 `Trigger` 偵測狀態，或在 LED 的 Subsystem 裡每 20 ms 讀一次 `getState()`。

## 其他隊伍的寫法

這一章的兩種做法，強隊都有人用：

- **254（2024）** 偏向做法一：把每個動作寫成 Command factory，例如 [`SuperstructureFactory`](https://github.com/Team254/FRC-2024-Public/blob/main/src/main/java/com/team254/frc2024/command_factories/SuperstructureFactory.java)，再組合起來
- **6328（2024）** 偏向做法二：[`Superstructure`](https://github.com/Mechanical-Advantage/RobotCode2024Public/blob/main/src/main/java/org/littletonrobotics/frc2024/subsystems/superstructure/Superstructure.java) 有 `desiredGoal`（想要的）和 `currentGoal`（目前的），每 20 ms 依照目前的 Goal 設定每個機構

讀這些程式時，先找出它們的「狀態」和「轉換」在哪裡，再對照這一章的寫法。它們的機器人和程式都比這一章複雜很多，不用每一行都看懂。

## 進一步閱讀

- [WPILib 文件：Command-Based Programming](https://docs.wpilib.org/en/latest/docs/software/commandbased/index.html)：組合 Command、Interrupt behavior 的官方說明（部分內容可能還是 2026 的寫法）
- [WPILib 文件：Unit Testing](https://docs.wpilib.org/en/latest/docs/software/wpilib-tools/robot-simulation/unit-testing.html)：在機器人專案裡寫 JUnit 測試
