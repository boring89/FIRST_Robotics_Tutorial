# 第 10 章 視覺

--8<-- "alpha-warning.md"

[第 9 章的 Odometry](../09-autonomous/odometry.md) 有一個根本的問題：它是靠累加算出位置的，輪子一打滑、被撞一下，誤差就會累積，而且無法自己修正。**視覺 (Vision)** 讓機器人可以「看」場地上的標記，隨時知道自己真正的位置。

## 這章學什麼

- 視覺在 FRC 能做什麼
- AprilTag 是什麼，怎麼用它對準目標、修正機器人的位置
- Limelight 和 PhotonVision 的差別
- 寫一個 Vision Subsystem，把視覺的位置資料加進 Pose estimation（姿態估計）

**需要先讀**：[第 8 章 PID](../08-control/pid.md)、[第 9 章 Odometry](../09-autonomous/odometry.md)

## 視覺在 FRC 能做什麼

| 用途 | 做法 | 例子 |
| --- | --- | --- |
| **對準目標** | 看目標在畫面裡偏左還是偏右，轉向讓它置中 | 發射前自動對準得分目標 |
| **修正位置** | 看到場地上已知位置的 AprilTag，反推機器人在場上的位置 | 讓 Odometry 不再漂移，PathPlanner 走得更準 |
| **偵測遊戲物件** | 用神經網路辨識畫面中的遊戲物件 | 自動追著遊戲物件去吸 |

這章著重前兩個，第三個在[延伸學習](further.md)。

## 視覺的硬體

視覺需要大量運算，通常不在機器人程式裡做，而是交給專門的硬體，算好之後透過 NetworkTables 把結果傳給機器人程式：

```mermaid
flowchart LR
    CAM["相機"] --> CP["視覺處理<br/>（Limelight / 協同處理器）"]
    CP -- "NetworkTables：<br/>目標角度、機器人位置" --> SC["Systemcore<br/>你的機器人程式"]
```

| 方案 | 硬體 | 特色 |
| --- | --- | --- |
| **Limelight** | 相機和處理器做在一起的產品 | 設定簡單，網頁介面操作，大部分隊伍使用 |
| **PhotonVision** | 開源軟體，裝在 Orange Pi 等協同處理器上，搭配一般 USB 相機 | 便宜、彈性大，可以接多個相機 |

!!! info "2027 新改動"
    Systemcore 由 Limelight 開發。LimelightLib 2027 版也支援直接在 **Systemcore 上**執行視覺處理，相機接在 Systemcore 的 USB 埠上（程式裡用 `Limelight.SYSTEMCORE_USB0` 等名稱）。實際的效能和比賽規定，以 2027 賽季公布的資訊為準。

## 本章內容

| 小節 | 內容 |
| --- | --- |
| [AprilTag](apriltag.md) | AprilTag 是什麼、對準目標、Pose estimation 的觀念 |
| [Limelight 與 PhotonVision](limelight-photonvision.md) | 設定 Limelight、寫 Vision Subsystem、修正底盤的位置 |
| [延伸學習](further.md) | 物件偵測、多個相機、過濾不可靠的資料 |
