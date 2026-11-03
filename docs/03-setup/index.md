# 第 3 章 環境建置

--8<-- "alpha-warning.md"

這章要把寫機器人程式需要的工具都裝好，並且建立你的第一個機器人專案。做完之後，你的電腦就能編譯機器人程式、把程式部署到 Systemcore 上。

## 這章學什麼

- 安裝 WPILib 和 FIRST Driver Station
- 用範本建立一個 Command-based 專案，看懂專案裡每個檔案的用途
- 加入 REV / CTRE 的廠商函式庫
- 使用廠商工具設定 CAN ID、更新韌體
- 讓所有軟體和韌體的版本互相對得上

**需要先讀**：[第 2 章 FRC 基本元件認識](../02-hardware/index.md)，至少要知道 Systemcore、CAN ID 是什麼。

## 你需要準備

| 項目 | 說明 |
| --- | --- |
| 電腦 | Windows 11、macOS 15 以上，或 Ubuntu 26.04 / Debian 13（都要 64 位元） |
| 硬碟空間 | 至少預留 10 GB |
| 網路 | 安裝時、第一次加入廠商函式庫時需要 |
| 系統管理員權限 | 不一定需要，但有的話比較不會遇到問題 |
| Systemcore（選用） | 只有部署程式那一步需要，其他步驟沒有機器人也能做 |

!!! warning "注意：Windows 10"
    Microsoft 在 2025 年 10 月停止支援 Windows 10，WPILib 2027 之後的版本可能無法在 Windows 10 上執行。隊上的筆電還是 Windows 10 的話，建議先升級。

## 版本對照

2027 還在 alpha 測試階段，**每一樣東西的版本都要互相對應**，否則會出現奇怪的問題。這是撰寫本章時（2026 年 11 月）使用的組合：

| 項目 | 版本 |
| --- | --- |
| WPILib | 2027.0.0-alpha-7（alpha-8 相容） |
| Systemcore 映像檔 | Image 14 以上 |
| FIRST Driver Station | Alpha 7 以上 |
| REVLib | 2027.0.0-alpha-7 以上 |
| Phoenix 6 | 26.70.0-alpha-2 |
| SPARK MAX / SPARK Flex 韌體 | 27.0.0 |
| CTRE 裝置韌體 | 26.70.x |

最新的對照表在 [SystemcoreTesting](https://github.com/wpilibsuite/SystemcoreTesting) 的首頁。換新的 alpha 版本前，先確認這張表。

## 本章內容

| 小節 | 內容 |
| --- | --- |
| [安裝 WPILib](install-wpilib.md) | 安裝 WPILib、WPILib VS Code、FIRST Driver Station |
| [建立第一個專案](first-project.md) | 用範本建立專案、專案結構、加入廠商函式庫 |
| [廠商工具](vendor-tools.md) | Systemcore 設定網頁、REV Hardware Client 2、Phoenix Tuner X |
| [韌體與 CAN 設定](firmware-can.md) | 更新映像檔和韌體、設定 CAN ID |
| [延伸學習](further.md) | 模擬、命令列、升級舊專案、離線安裝 |
