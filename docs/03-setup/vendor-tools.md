# 廠商工具

--8<-- "alpha-warning.md"

## 這頁學什麼

- 用 Systemcore 的設定網頁設定隊號、安裝套件
- 安裝 REV Hardware Client 2 或 Phoenix Tuner X
- 知道 WPILib 附了哪些工具程式

## 觀念

除了寫程式的 VS Code，程式組還會用到幾種工具：

| 工具 | 用途 | 什麼時候用 |
| --- | --- | --- |
| **Systemcore 設定網頁** | 設定隊號、更新映像檔、安裝套件 | 拿到新的 Systemcore、更新系統時 |
| **REV Hardware Client 2** | 設定 REV 裝置、更新韌體、測試馬達 | 用 REV 裝置時 |
| **Phoenix Tuner X** | 設定 CTRE 裝置、更新韌體、測試馬達 | 用 CTRE 裝置時 |
| **WPILib 附的工具** | 看資料、看 log、調參數 | 寫程式、除錯時 |

## 步驟

### Systemcore 設定網頁

1. 打開 Systemcore 的電源
2. 用 USB-C、網路線，或連上 Systemcore 的 Wi-Fi 熱點
    - 熱點名稱是 `SYSTEMCORE` 或 `SYSTEMCORE-` 加上一串亂碼（image 17 以後）
    - 密碼顯示在 Systemcore 的螢幕上
3. 用瀏覽器打開 `http://robot.local`

**設定隊號**：在設定頁面輸入隊號，按紅色的 **Change Team Number** 按鈕。這一步一定要做，否則 VS Code 部署程式時找不到機器人。Driver Station 裡也要設定同一個隊號。

**安裝套件**：首頁的 **Add Package** 可以安裝額外的軟體套件（`.ipk` 檔），例如 Elastic、AdvantageScope、REV Hardware Client 2、CANivore 的支援套件。

!!! tip "小技巧：`robot.local` 打不開"
    有些網路環境不支援 `.local` 名稱。可以直接用 IP 位址連線：

    | 連線方式 | IP 位址 |
    | --- | --- |
    | Wi-Fi 熱點 | `172.30.0.1` |
    | USB（Windows） | `172.26.0.1` |
    | USB（macOS / Linux） | `172.27.0.1` |
    | 網路線 | 顯示在 Systemcore 的螢幕上 |

### 廠商工具

=== "REV"

    **REV Hardware Client 2 (RHC2)** 可以掃描 REV 裝置、設定 CAN ID、更新韌體，還能直接讓馬達轉起來測試。

    - **電腦版**：從 [REV Hardware Client 2 下載頁](https://rhc2.revrobotics.com/download/download.html) 下載安裝
    - **Systemcore 版**：也可以把 RHC2 的 `.ipk` 套件裝在 Systemcore 上，用 Systemcore 設定網頁的 **Add Package** 安裝，啟動需要一分鐘左右

    !!! info "2027 新改動"
        2027 的 REV 韌體（包含 A301）都是透過 **REV Hardware Client 2** 更新，不是 2026 以前的第一代 REV Hardware Client。

=== "CTRE"

    **Phoenix Tuner X** 可以掃描 CTRE 裝置、設定 CAN ID、更新韌體、畫即時圖表。

    2027 alpha 需要 **Phoenix Tuner X Preview** 版本（2026.10.x 以上），才能搭配 Phoenix 6 26.70.0-alpha-2：

    - **Windows**：從 Microsoft Store 安裝 Preview 版，可以和正式版同時存在
    - **macOS / iOS**：透過 TestFlight 安裝

    使用 **CANivore**（CTRE 的 USB 轉 CAN FD 裝置）的話，要在 Systemcore 上安裝對應的 CANivore 套件。依照 Systemcore 的映像檔版本選擇正確的 `.ipk`，下載連結在 [SystemcoreTesting 的 CTRE 頁面](https://github.com/wpilibsuite/SystemcoreTesting/blob/main/CTR-Phoenix.md)。

### WPILib 附的工具

安裝 WPILib 時已經一起裝好了，可以用 Command Palette 的 **WPILib: Start Tool** 打開：

| 工具 | 用途 | 本教學在哪裡用到 |
| --- | --- | --- |
| **Elastic** | Dashboard（儀表板）：比賽時顯示機器人的資料、選擇自動程式 | [第 4 章](../04-robot-basics/deploy-debug.md) |
| **AdvantageScope** | 看即時資料和 log，回放比賽 | [第 11 章](../11-practice/logging.md) |
| **Glass** | 看資料、模擬時操作機器人 | [第 4 章](../04-robot-basics/further.md) |
| **SysId** | 量測機構的特性，用來調控制參數 | [第 8 章](../08-control/sysid.md) |
| **Data Log Tool** | 從 Systemcore 下載 log 檔 | [第 11 章](../11-practice/logging.md) |

!!! info "2027 新改動：SmartDashboard 和 Shuffleboard 移除了"
    2026 以前常用的 SmartDashboard 和 Shuffleboard 在 2027 已經移除。顯示資料改用 **Elastic** 或 **AdvantageScope**，程式裡則改用新的 Telemetry API。

## 常見錯誤

!!! bug "常見錯誤：工具版本和韌體版本對不上"
    舊版的 Phoenix Tuner X 或 REV Hardware Client 看不到、或無法正確設定 2027 韌體的裝置。工具也要跟著 alpha 版本更新。

!!! bug "常見錯誤：同時開好幾個工具搶同一個裝置"
    RHC2 或 Tuner X 正在控制馬達時，機器人程式送出的指令可能會互相衝突。測試完記得關掉工具的控制功能，再 Enable 機器人程式。

## 小練習

1. 連上 Systemcore 的設定網頁，找到隊號設定和 **Add Package** 的位置。
2. 依照隊上使用的廠商，安裝 REV Hardware Client 2 或 Phoenix Tuner X。
3. 用 **WPILib: Start Tool** 打開 Elastic 和 AdvantageScope，看看它們長什麼樣子。
