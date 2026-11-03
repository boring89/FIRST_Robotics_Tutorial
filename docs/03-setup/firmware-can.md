# 韌體與 CAN 設定

--8<-- "alpha-warning.md"

## 這頁學什麼

- 更新 Systemcore 的映像檔
- 更新馬達控制器的韌體
- 規劃和設定 CAN ID
- 知道程式裡怎麼指定 CAN 匯流排

## 觀念

### 映像檔和韌體

- **Image（映像檔）**：Systemcore 的作業系統，類似電腦的 Windows 或 macOS
- **Firmware（韌體）**：馬達控制器、感測器內建的程式

這兩樣東西都要和 WPILib、vendordep 的版本對應（見 [版本對照](index.md#版本對照)）。版本不對的時候，最常見的症狀是：程式部署成功，但裝置一直報錯、馬達不動，或 Driver Station 連不上。

!!! warning "注意：比賽期間不要隨便更新"
    更新映像檔或韌體都可能帶來新的問題。賽季開始後，除非有必要的修正，否則**不要在比賽前一天更新**。每次更新都記錄下來：更新了什麼、從哪個版本到哪個版本。

### CAN ID 的規劃

每個 CAN 裝置都要有一個 ID，**同一條匯流排上、同一種裝置的 ID 不能重複**。新裝置出廠時的 ID 通常都一樣，所以裝上機器人之前要先改。

建議在隊上訂一套規則，例如：

| ID 範圍 | 用途 |
| --- | --- |
| 1～9 | 底盤 |
| 10～19 | Intake |
| 20～29 | 手臂、升降機構 |
| 30～39 | 發射器 |
| 40～49 | 感測器（CANcoder、IMU 等） |

有規則的好處是：看到 ID 就知道是哪個機構，除錯時很快就能找到裝置。

### 程式裡怎麼指定 CAN 匯流排

Systemcore 有 5 條 CAN 匯流排，Motioncore 有 20 個 Device port，所以程式除了 CAN ID，還要指定**哪一條匯流排**。2027 的 WPILib 用 `CANPort` 這個 enum 表示：

| 硬體上的位置 | 系統裡的名稱 | 程式裡的寫法 |
| --- | --- | --- |
| Systemcore CAN 0～4 | `can_s0`～`can_s4` | `CANPort.CAN_S0`～`CANPort.CAN_S4` |
| Motioncore Device port 0～19 | `can_d0`～`can_d19` | `CANPort.CAN_D0`～`CANPort.CAN_D19` |

實際建立馬達物件的程式會在 [第 4 章 讓馬達轉起來](../04-robot-basics/first-motor.md) 介紹。

!!! info "2027 新改動"
    2026 以前只有一條 CAN 匯流排，建立裝置時只要給 CAN ID。2027 起 REVLib、Phoenix 6 和 WPILib 的 CAN 裝置都要另外指定 `CANPort`。

## 步驟

### 更新 Systemcore 映像檔

映像檔 image 12 以上可以直接用網頁更新：

1. 到 [systemcore-os-public](https://github.com/LimelightVision/systemcore-os-public) 下載最新的 `.llupdate` 檔。**alpha 版和 beta 版硬體的檔案不同**，選對你手上的版本
2. 連上 Systemcore，打開 `http://robot.local/configure`
3. 捲到最下面，選擇剛剛下載的檔案
4. 選擇更新時要保留哪些資料（建議全部保留）
5. 等待更新完成，**過程中不要斷電**

映像檔比 image 12 舊的話，要用 **Limelight Hardware Manager** 的 Flash 模式更新，步驟見 [SystemcoreTesting](https://github.com/wpilibsuite/SystemcoreTesting)。

### 更新韌體、設定 CAN ID

=== "REV"

    1. 用 USB 線把 SPARK MAX / SPARK Flex 接到電腦（或透過 Systemcore 上的 RHC2 從 CAN 掃描）
    2. 打開 **REV Hardware Client 2**，等它偵測到裝置
    3. 更新韌體到 **27.0.0**（SPARK MAX、SPARK Flex）
    4. 在裝置設定裡修改 **CAN ID**，儲存
    5. 在裝置上貼標籤，寫上 CAN ID

    其他 REV 裝置（Power Distribution Hub、Pneumatic Hub 等）目前使用 2026 的韌體 v26.1.3。

    **A301** 接在 Motioncore 上時，REVLib 預設會自動偵測它的 ID，通常不需要手動設定。

=== "CTRE"

    1. 把裝置接上 CAN，確認 Systemcore（或 CANivore）已經開機
    2. 打開 **Phoenix Tuner X**，連線到機器人
    3. 在 **Devices** 頁面找到裝置，更新韌體到 **26.70.x**
    4. 修改裝置的 **ID**，儲存
    5. 在裝置上貼標籤，寫上 CAN ID

    同時有好幾個 ID 一樣的新裝置時，**一次只接一個**，改好 ID 再接下一個，比較不會搞混。

## 常見錯誤

!!! bug "常見錯誤：新裝置全部同一個 ID"
    新買的馬達控制器出廠 ID 都一樣。一次全部接上機器人，Tuner X / RHC2 只會看到其中一個，或是看到的裝置一直跳來跳去。**一次接一個，改完 ID 再接下一個。**

!!! bug "常見錯誤：程式裡的 `CANPort` 和實際接線不一樣"
    程式寫 `CANPort.CAN_S0`，裝置卻接在 Systemcore 的 CAN 1，程式會一直報「找不到裝置」。CAN 表裡要同時記錄匯流排和 ID。

!!! bug "常見錯誤：更新映像檔時斷電"
    更新到一半斷電，Systemcore 可能開不了機，只能用 Flash 模式重灌。更新時接上充飽的電池，或用穩定的電源。

## 小練習

1. 為隊上的機器人訂一套 CAN ID 規則，寫成表格。
2. 把 [馬達與馬達控制器](../02-hardware/motors.md) 小練習裡做的 CAN 表，加上「程式裡的寫法」一欄（例如 `CANPort.CAN_S0`）。
3. 如果有實體裝置，用 RHC2 或 Tuner X 查出它目前的韌體版本和 CAN ID。
