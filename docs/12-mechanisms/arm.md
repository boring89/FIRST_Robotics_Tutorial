# 手臂 Arm

這個模擬器的手臂和[第 8 章 Motion Profile](../08-control/motion-profile.md) 的範例一樣：

- **硬體**：一顆 NEO 馬達，60:1 減速，手臂長 0.6 公尺、重 4 公斤
- **角度**：水平為 0 度，收起時是 -30 度，最高 110 度（兩端都有硬體擋塊）
- **控制**：馬達控制器上跑 Motion Profile + PID + kV，機器人程式每 20 ms 送出重力補償 `kG × cos(角度)`
- **Command**：`faceUp` 轉到 90 度、`faceRight` 轉到 0 度（水平）、`faceLeft` 收起；推右搖桿時切換成手動控制

<div data-sim="arm"></div>
<script type="module" src="../../assets/sim/arm.js"></script>

橘色是手臂的實際位置，半透明的藍色是 Motion Profile 規劃的「現在應該在哪裡」。

## 試試看

### 1. 沒有 Enable 會怎樣

先**不要**按 Enable，按 `faceUp`。什麼都不會發生：機器人停用時，Scheduler 不會啟動任何 Command，馬達也不會有輸出。

### 2. 只有 P 的問題

1. 按 **只有 P**，再按 **Enable**
2. 按 `faceRight`（水平 0 度），觀察手臂停在哪裡

手臂會停在比 0 度**低**十幾度的地方。手臂水平時重力最大，要撐住它需要一定的電壓，而 P 的輸出 = kP × 誤差，**一定要有誤差才會有輸出**。這就是[第 8 章 Feedforward](../08-control/feedforward.md#只用-pid-的問題) 講的問題。

再按 `faceUp`（90 度）：手臂接近垂直時重力很小，所以這次停得比較準。重力造成的誤差和角度有關，這正是手臂需要 `kG × cos(角度)` 的原因。

再按 `faceLeft` 收起，然後按 `faceUp`：沒有 Motion Profile 時，目標一下子跳過去，馬達電壓直接衝到上限，手臂猛然甩上去。看看電壓曲線。

### 3. 加上重力補償

按 **P + kG**，再按 `faceRight`。這次手臂停在水平的位置：kG 負責撐住重力，P 只需要修正剩下的一點誤差。觀察電壓曲線中綠色的「重力補償」：手臂越接近水平，需要的電壓越大；越接近垂直，需要的電壓越小。

試著把 **kG** 調到 0、0.5、1.5，看看手臂在水平位置時會往下掉、停得剛好，還是往上飄。

### 4. 加上 Motion Profile

按 **完整**，在 `faceLeft` 和 `faceUp` 之間切換。手臂會平順地加速、等速、減速，藍色的 Profile 目標畫出一個梯形的速度曲線。

- 把**巡航速度**和**加速度**調大，手臂動得更快、更猛
- 把它們調小，手臂動得很慢但很穩
- 調太大時，馬達的電壓會頂到 12 V 的上限，手臂跟不上 Profile（橘線落後藍線）

### 5. 被撞一下

手臂停在水平（`faceRight`）時，按 **往下推手臂一下**。PID 會把手臂拉回來。把 **kD** 設成 0 再試一次，比較兩者的差別：沒有 D 時，回到目標的過程會有比較多的晃動。

### 6. Command 的中斷

1. 按 `faceUp`，手臂還在往上的時候，馬上按 `faceLeft`
2. 看右下角的 **Command Scheduler**：`ArmToAngle(90°)` 被**中斷**，`ArmToAngle(-30°)` **開始**

這就是[第 5 章 Requirements](../05-commands/why.md#requirements一個機構同時只能做一件事)：兩個 Command 都需要 Arm，新的會中斷舊的。

再試試看：手臂停在 90 度時，推一下**右搖桿 Y**。Trigger 偵測到搖桿推超過 0.2，啟動 `ArmManual`，中斷 `ArmToAngle`。放開搖桿後，手臂會慢慢往下掉，因為手動控制在搖桿為 0 時輸出 0 V，Brake 模式只能讓它掉得慢一點，撐不住重力。

### 7. Disable 時的 Brake 和 Coast

手臂停在水平（`faceRight`）時按 **Disable**：手臂會慢慢往下掉（Brake 模式的馬達會抵抗轉動，但撐不住）。把「停用時 Brake 模式」關掉再試一次，這次手臂會直接摔下去。這就是[第 6 章](../06-motors-sensors/motor-config.md#idle-modebrake-和-coast)說手臂要用 Brake 的原因。

## 對照程式碼

模擬器的控制方式對應到[第 8 章的 Arm](../08-control/motion-profile.md#範例)：

| 模擬器 | 程式碼 |
| --- | --- |
| `faceUp` → `ArmToAngle(90°)` | `driver.faceUp().onTrue(arm.goToAngleCommand(90))` |
| Motion Profile（巡航速度、加速度） | `MotionMagicCruiseVelocity`、`MotionMagicAcceleration`（REV：`maxMotion`） |
| kP、kD、kV | `Slot0.kP`、`Slot0.kD`、`Slot0.kV` |
| 重力補償 | `ArmFeedforward` 算出 `kG × cos(角度)`，用 `withFeedForward()` 送給控制器 |
| 推右搖桿 → `ArmManual` | `new Trigger(() -> Math.abs(driver.getRightY()) > 0.2).onTrue(arm.manualCommand(...))` |

模擬器的參數單位和第 8 章 CTRE 版本一樣：kP 是「伏特 / 手臂圈數」、kV 是「伏特 / (手臂圈數 / 秒)」、速度是「手臂圈數 / 秒」。
