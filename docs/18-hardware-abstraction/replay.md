# Log replay

--8<-- "alpha-warning.md"

## 這頁學什麼

- Log replay 在做什麼：同樣的輸入，用新的程式重新算一次輸出
- 重播一份 log、在 AdvantageScope 比較結果
- 改程式之後重播，看到哪些東西會變、哪些不會
- 哪些寫法會讓重播失準

## 觀念

### 重播在做什麼

比賽第三場，機器人在自動階段撞牆了。log 裡有那 15 秒的**所有輸入**：每個模組的位置、速度、角度，IMU，Driver Station。重播就是：

1. 把**同一份輸入**，一圈一圈餵給程式
2. 程式照常計算 Odometry、Setpoint Generator、PathPlanner 的輸出
3. 把新算出的輸出存到新的 log

如果程式沒改，新的輸出和比賽時**完全一樣**。如果你改了程式（例如修了一個 bug），就能看到「當時如果是新的程式，會算出什麼」。

### 會變的和不會變的

重播時，機器人實際怎麼動是**已經發生的事**，記錄在輸入裡，不會改變。會變的只有程式算出的東西：

| | 改了程式之後重播 |
| --- | --- |
| 模組的位置、速度、角度（輸入） | **不會變**：那是當時量到的 |
| Pose（由輸入算出的輸出） | 改了 Odometry 的程式才會變 |
| 目標速度、目標角度（輸出） | **會變**：這就是「新的程式會怎麼做」 |

所以重播最適合回答的問題是：**「同樣的情況下，新的程式會下什麼指令？」** 例如：Pose 算錯是不是 Odometry 的 bug？修了之後 Pose 對不對？Setpoint Generator 的參數改了，指令會變得怎樣？

它不能回答「機器人會不會因此就不撞牆」，因為那需要知道新的指令會讓機器人怎麼動，而重播只有當時的資料。

## 步驟

### 1. 準備一份 log

最簡單的是用模擬產生一份：用 `SIM` 模式啟動模擬，開一陣子車再關掉，`logs` 資料夾裡就會有一份 `.wpilog`。真的機器人的 log 在機器人上，用 AdvantageScope 的 **File → Download Logs** 下載。

### 2. 切換到重播模式

把 `Constants` 的 `SIM_MODE` 改成 `REPLAY`：

```java
public static final Mode SIM_MODE = Mode.REPLAY;
```

### 3. 告訴程式要重播哪一份 log

三種方法，AdvantageKit 會依序找：

1. 環境變數 `AKIT_LOG_PATH`
2. **AdvantageScope 目前打開的 log**：在 AdvantageScope 打開那份 log 就好，最方便
3. 都沒有的話，程式會在終端機上問你檔案的路徑

### 4. 執行

選 **WPILib: Simulate Robot Code**，**不要勾任何擴充**（包括 Sim GUI）。程式會用最快的速度跑完整份 log，結束時自動關閉，並在原本的 log 旁邊產生一份 `_sim` 結尾的新 log。如果 AdvantageScope 開著，新的 log 會自動在 AdvantageScope 裡打開。

### 5. 比較

新的 log 裡有兩份輸出：

- **`RealOutputs`**：當時（原本的 log）算出的輸出
- **`ReplayOutputs`**：這次重播用新的程式算出的輸出

在 AdvantageScope 把兩個 `Drivetrain/Pose` 放在同一個 2D Field，或把兩個目標值放在同一張折線圖，就能看出差別。

!!! tip "小技巧：`replayWatch`"
    在終端機執行 `./gradlew replayWatch`，它會盯著你的程式碼，**每次存檔就自動重播一次**。改一行程式、存檔、看 AdvantageScope，很適合一邊修 bug 一邊看結果。

## 範例：用模擬的 log 實測

用模擬讓機器人照一段腳本移動約 6 秒：往前、一邊往左一邊旋轉、往後一邊反向旋轉，記錄成 log，再用 `REPLAY` 模式重播。

### 程式沒改：完全一樣

每 50 圈（1 秒）比較一次 Pose：

| 時間點 | 原本（模擬） | 重播 |
| --- | --- | --- |
| 第 50 圈 | x = 1.524548, y = 0.000019, 0.000000 度 | x = 1.524548, y = 0.000019, 0.000000 度 |
| 第 100 圈 | x = 2.906239, y = 0.567379, 16.191885 度 | x = 2.906239, y = 0.567379, 16.191885 度 |
| 第 150 圈 | x = 1.524050, y = 1.968781, 74.149698 度 | x = 1.524050, y = 1.968781, 74.149698 度 |
| 第 250 圈 | x = 0.045176, y = 1.858880, −74.662472 度 | x = 0.045176, y = 1.858880, −74.662472 度 |

到小數點後六位都一樣：重播的結果是**確定的 (Deterministic)**。

### 改了程式：指令變了，Pose 沒變

重播時把[第 17 章](../17-swerve-advanced/setpoint-generator.md) 的輪子摩擦係數 `WHEEL_COF` 從 1.2 改成 0.6（假裝場地比較滑），看「往前改成往左」那一刻，左前模組的目標：

| 時間點 | 原本（1.2） | 重播時改成 0.6 |
| --- | --- | --- |
| 第 81 圈 | 1.41 m/s、49.0 度 | 1.52 m/s、22.5 度 |
| 第 84 圈 | 1.65 m/s、75.7 度 | 1.42 m/s、35.5 度 |
| 第 90 圈 | 2.31 m/s、96.7 度 | 1.47 m/s、62.4 度 |
| Pose | 第 100、150 圈都和原本一樣 | 第 100、150 圈都和原本一樣 |

摩擦係數變小，Setpoint Generator 讓輪子**轉得比較慢**（避免側滑），這是新程式「會下的指令」。Pose 沒有變，因為它是從當時量到的輸入算出來的。

## 哪些寫法會讓重播失準

重播要準，**程式用到的每一個外部資料都要經過輸入**。這幾種情況會讓重播算出不一樣的結果：

| 寫法 | 為什麼 | 怎麼改 |
| --- | --- | --- |
| 邏輯裡直接讀感測器 | 重播時硬體是空的，讀不到當時的值 | 經過 IO 的 `updateInputs` |
| 讀電池電壓（`RobotController.getBatteryVoltage()`） | 重播時讀到的是電腦上的預設值 | 放進某個 IO 的輸入，或用固定值。所以 `Drivetrain` 的 Setpoint Generator 固定用 12 V |
| 直接讀 NetworkTables（例如 `Vision` 讀 Limelight） | 重播時沒有 Limelight | 做一個 `VisionIO`（[延伸學習](further.md#視覺的硬體抽象化)） |
| Dashboard 的選單（`Selectable`） | 重播時讀不到當時選了哪一個 | 用 AdvantageKit 的 `LoggedNetworkChooser`（[延伸學習](further.md#可以重播的自動程式選單)） |
| 用電腦的時間（`System.currentTimeMillis()`）、亂數 | 每次執行都不一樣 | 用 WPILib 的 `Timer`，AdvantageKit 會讓它在重播時回到 log 的時間 |

這一章的 `Vision` 還是直接讀 Limelight，所以**有看到 AprilTag 的 log** 重播時，視覺修正不會被重播，Pose 會和原本不一樣。模擬裡沒有 Limelight，所以上面的測試不受影響。

## 常見錯誤

!!! bug "常見錯誤：重播時勾了 Sim GUI"
    重播需要所有 HAL 的模擬擴充都關閉。勾了 Sim GUI 會讓重播的時間和 Driver Station 狀態錯亂，結果和原本不一樣。

!!! bug "常見錯誤：忘了把 `SIM_MODE` 改回來"
    重播完沒有把 `SIM_MODE` 改回 `SIM`，下次開模擬時，程式會去找 log 檔，而不是跑模擬。部署到機器人上則不受影響（一定是 `REAL`）。

!!! bug "常見錯誤：以為重播能模擬「新的程式會讓機器人怎麼動」"
    重播用的是當時量到的輸入。改了 PID 之後重播，目標值會變，但模組的速度、角度還是當時的值，機器人不會「照新的指令動」。想看新的程式會讓機器人怎麼動，要用 `SIM` 模式模擬。

## 小練習

1. 照上面的步驟，用模擬產生一份 log，不改程式重播一次，在 AdvantageScope 確認 `RealOutputs/Drivetrain/Pose` 和 `ReplayOutputs/Drivetrain/Pose` 完全重疊。
2. 重播時把 `HEADING_KP` 改成兩倍，在 AdvantageScope 找出目標值的差別（提示：要在 log 裡有「放開右搖桿」的時候才會看到差別）。
3. 為什麼重播時把 `WHEEL_DIAMETER_METERS` 改大，Pose 不會變？如果想用重播檢查輪徑設錯的影響，`ModuleIOInputs` 要記錄什麼？

??? question "第 3 題參考答案"
    `ModuleIOInputs` 記錄的是**換算好的公尺**（`drivePositionMeters`）。換算發生在 `ModuleIOReal.updateInputs()` 裡，重播時 IO 是空的，不會執行換算，log 裡的公尺直接被讀回來。所以改輪徑不影響重播。

    想用重播檢查輪徑，輸入要記錄**沒有換算的原始值**（例如輪子的圈數 `driveRotations`），換算改到邏輯那一邊做。這也是很多隊伍在 IO 裡只記錄原始值的原因：取捨在於「換廠商時要改哪裡」和「重播時能改什麼」。
