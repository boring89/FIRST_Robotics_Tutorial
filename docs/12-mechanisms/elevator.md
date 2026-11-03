# 升降機構 Elevator

升降機構 (Elevator) 和手臂很像，都是位置控制，但有一個重要的差別：**重力永遠一樣大**。手臂需要的重力補償是 `kG × cos(角度)`，升降機構只需要一個固定的 kG。

- **硬體**：兩顆 NEO，10:1 減速，1 英寸的捲軸，滑車重 6 公斤，行程 1.2 公尺
- **控制**：馬達控制器上跑 Motion Profile + PID + kV，再加上固定的 kG（CTRE 的 `GravityType = Elevator_Static`，或 REV 的 `kG`）
- **Command**：`faceUp` 升到 1.1 公尺、`faceRight` 升到 0.6 公尺、`faceDown` 降到底；推右搖桿切換成手動控制

<div data-sim="elevator"></div>
<script type="module" src="../../assets/sim/elevator.js"></script>

## 試試看

### 1. 只有 P

按 **只有 P**、**Enable**，再按 `faceRight`。滑車會停在比 0.6 公尺**低**大約 3 公分的地方（看左上角的高度）：和手臂一樣，P 一定要有誤差才會有輸出，撐住重力的電壓只能靠誤差換來。

和手臂不同的是：不管停在哪個高度，誤差都差不多大，因為重力不會隨高度改變。

### 2. 加上 kG

按 **P + kG**，再按 `faceRight`。kG 剛好撐住重力，滑車停得很準。

試著把 kG 調成 0.2、0.35、0.5，看看滑車在 Disable 之前和之後的變化：kG 太小會往下掉，太大會往上飄。

### 3. 載著遊戲物件

在 **P + kG** 的設定下，滑車停在 0.6 公尺時打開 **載著遊戲物件（+2 kg）**。滑車變重了，原本剛好的 kG 現在不夠，P 必須補上差額，所以滑車會停得**低大約 1 公分**。

這說明了兩件事：

- Feedforward 是**預測**，預測錯了（重量變了），就要靠 PID 修正
- 真實的機器人上，拿著和沒拿遊戲物件的重量不同。差很多的話，可以準備兩組參數（CTRE 和 REV 都支援多個 Slot），依照狀態切換

### 4. Motion Profile

按 **完整**，在 `faceDown` 和 `faceUp` 之間切換，觀察電壓曲線：加速時電壓升高、等速時穩定、減速時降低。把**巡航速度**調到 1.5 m/s、**加速度**調到 10 m/s²：動作快很多，但電壓會頂到 12 V 的上限，再快就沒有餘裕了。調回 1 m/s、3 m/s²，動作平順很多，對機構和上面的遊戲物件也比較溫和。

### 5. Disable

滑車停在 1.1 公尺時按 **Disable**：Brake 模式下滑車會慢慢滑下來。關掉「停用時 Brake 模式」再試一次，滑車會直接掉到底。

## 對照程式碼

| 模擬器 | 程式碼（CTRE） |
| --- | --- |
| kG（固定） | `Slot0.kG`、`Slot0.GravityType = GravityTypeValue.Elevator_Static` |
| kP、kD、kV | `Slot0.kP`、`Slot0.kD`、`Slot0.kV` |
| Motion Profile | `MotionMagic.MotionMagicCruiseVelocity`、`MotionMagicAcceleration` |
| `faceUp` → `ElevatorTo(1.10 m)` | `driver.faceUp().onTrue(elevator.goToHeightCommand(1.1))` |

和第 8 章的手臂不同，升降機構的 kG 是固定值，可以直接設定在控制器上，不需要機器人程式每 20 ms 計算 `cos(角度)` 再送過去。

模擬器的參數單位：kP 是「伏特 / 公尺」、kV 是「伏特 / (公尺 / 秒)」。真實的控制器用的是馬達或感測器的圈數，要再乘上換算係數。
