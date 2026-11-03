# 飛輪 Shooter

這個模擬器的 Shooter 和[第 6 章](../06-motors-sensors/encoders.md#shooter轉速夠快才發射)、[第 8 章 Feedforward](../08-control/feedforward.md#範例) 的範例一樣：

- **硬體**：一顆 NEO Vortex 直接帶動飛輪，加上一個把遊戲物件送進飛輪的 Intake（Feeder）
- **電池**：模擬電池的內阻，馬達吃大電流時電池電壓會下降
- **控制**：可以切換**開迴路**（`setThrottle(0.64)`）或**速度控制**（kS + kV + kP，目標 4000 RPM）
- **Command**：`rightBumper` 執行 `ScoringCommands.shoot()`，整個過程中 Shooter 持續轉動：
    - 速度控制時：等到轉速到了（最多 2 秒），再送料 0.5 秒（[第 6 章](../06-motors-sensors/encoders.md#shooter轉速夠快才發射)的寫法）
    - 開迴路時：先轉 1 秒，再送料 0.5 秒（[第 5 章](../05-commands/composition.md#發射的組合)的寫法）

<div data-sim="flywheel"></div>
<script type="module" src="../../assets/sim/flywheel.js"></script>

遊戲物件的落點取決於**發射當下的轉速**。綠色的圓圈是目標，轉速在 4000 RPM 附近時才會打中（落點變綠色），太快或太慢都會打歪（變紅色）。

## 試試看

### 1. 發射流程

1. 按 **Enable**，再按 `rightBumper`
2. 看右下角的 Command Scheduler：`Shoot` 依序執行 `WaitUntil(isAtSpeed)` → `Feed`（開迴路時是 `Wait(1s)` → `Feed`）
3. 打完之後按 `faceDown` 裝填下一顆

注意轉速曲線：遊戲物件被飛輪擠出去的那一瞬間，**轉速會掉下來**，因為飛輪把能量交給了遊戲物件。

### 2. 開迴路的問題：電池

1. 按 **開迴路**（輸出固定 0.64，先轉 1 秒再送料）
2. 把 **電池電量** 設成 12.6 V，發射幾顆：轉速大約 3970 RPM，打得中
3. 把電池電量調到 11.8 V，再發射幾顆：轉速只剩 3700 RPM 左右，打不到

電池電量低時，同樣的輸出比例只能給出比較低的電壓，轉 1 秒之後的轉速比較慢，遊戲物件就打不到目標。這就是[第 4 章](../04-robot-basics/first-motor.md)的 `setThrottle()` 在比賽中不可靠的原因：一場比賽下來電池電壓會一直下降。

### 3. 速度控制：只有 Feedforward

按 **只有 FF**（kP = 0）。這次控制器直接用 `kS + kV × 目標轉速` 算出需要的**電壓**，不管電池電量多少，給的都是同樣的電壓，所以轉速很穩定。

把 **kV** 調大或調小一點，觀察轉速會停在比目標高還是低的地方。這就是[第 8 章](../08-control/feedforward.md#調整的順序)說的「先調 Feedforward」。

### 4. 加上 P

按 **FF + P**，連續發射幾顆，比較和「只有 FF」的差別：發射後轉速下降時，P 會根據誤差多給一些電壓，讓轉速**更快恢復**。

把 kP 調很大（例如 0.01），轉速在目標附近會開始抖動。

### 5. 只轉不發射

按住 `faceUp`：只有 Shooter 在轉（`whileTrue`），放開就停止。這在比賽中很常用：先把 Shooter 轉起來，等對準了再發射，就不用等轉速。

## 對照程式碼

| 模擬器 | 程式碼 |
| --- | --- |
| `rightBumper` → `Shoot` | `driver.rightBumper().onTrue(ScoringCommands.shoot(intake, shooter))` |
| `WaitUntil(isAtSpeed)`，最多 2 秒 | `Commands.waitUntil(shooter::isAtSpeed).withTimeout(SPIN_UP_TIMEOUT_SECONDS)` |
| `Feed` 0.5 秒 | `intake.feedCommand().withTimeout(FEED_SECONDS)` |
| 整個過程 Shooter 持續轉 | `.deadlineFor(shooter.spinCommand())` |
| 速度控制的 kS、kV、kP | REV：`config.closedLoop.feedForward.kS(...).kV(...)`、`config.closedLoop.p(...)` |
| 開迴路 0.64，轉 1 秒再送料 | `motor.setThrottle(0.64)`、`Commands.waitSeconds(1.0)`（第 5 章） |

模擬器的參數單位和第 8 章 REV 版本一樣：kV 是「伏特 / RPM」，kP 是「伏特 / RPM 誤差」。
