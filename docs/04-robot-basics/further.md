# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## Simulation（模擬）

沒有機器人的時候，也可以在電腦上執行機器人程式：

1. 用 Command Palette 選 **WPILib: Simulate Robot Code**
2. 選擇要使用的模擬擴充 (Extension)，勾選 **Sim GUI**
3. 程式會在你的電腦上啟動，並打開模擬介面

在模擬介面裡，你可以：

- 切換 Disabled / Autonomous / Teleop 模式
- 把電腦上的手把（或鍵盤）指定成程式裡的手把
- 看 NetworkTables 裡的 Telemetry 資料

!!! info "2027 alpha 的限制"
    WPILib 2027 alpha 目前把模擬用的 Driver Station 擴充改成不執行任何動作，官方表示完整的模擬 Driver Station 支援會在之後的版本加入。如果模擬介面裡切換模式、手把沒有作用，就是這個原因。

模擬可以測試程式的**邏輯**：按鍵有沒有綁對、模式切換時有沒有印出正確的訊息。它沒辦法告訴你真實的馬達會怎麼動，除非你另外寫了物理模型。

!!! tip "小技巧"
    先在模擬裡確認程式不會一啟動就當掉，再部署到真的機器人上，可以省下很多時間。

## OpModeRobot

2027 新增了 **OpModeRobot** 範本，寫法和 FTC 的 OpMode 類似：每一種自動程式或手動程式都是一個獨立的類別，在 Driver Station 上選擇要執行哪一個。

這份教學使用 `TimedRobot` 和 Command-based，因為目前大部分 FRC 的資源和範例都是這種架構。OpModeRobot 的官方文件還在撰寫中，有興趣的話可以建立一個 **OpMode Robot** 範本的專案，讀讀看裡面的註解。

## `addPeriodic`：用不同的頻率執行

`TimedRobot` 預設每 20 ms 執行一次。如果某件事需要更頻繁（或更不頻繁）地執行，可以在建構子裡用 `addPeriodic()` 加上另一個週期：

```java
public Robot() {
  // 每 5 ms 執行一次 fastUpdate()
  addPeriodic(this::fastUpdate, 0.005);
}

private void fastUpdate() {
  // 需要快速更新的程式
}
```

`this::fastUpdate` 是 [Lambda](../01-java/lambda.md) 那頁學過的 method reference。大部分情況用不到這個功能，真的需要時要注意：這段程式也不能卡住。

## 為什麼是 20 ms？

20 ms（每秒 50 次）是速度和負擔之間的平衡：

- Driver Station 大約每 20 ms 傳一次手把資料給機器人，迴圈跑得再快，手把資料也不會更新得更快
- 對大部分機構來說，每秒調整 50 次已經夠快了
- 需要更快速控制的部分（例如馬達的 PID），通常直接在馬達控制器內部執行，控制器內部的速度比 20 ms 快得多

[第 8 章](../08-control/index.md) 會再詳細介紹這個設計。
