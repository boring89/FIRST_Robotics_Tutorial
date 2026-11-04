# 用 AdvantageScope 看模擬

--8<-- "alpha-warning.md"

## 這頁學什麼

- 把 AdvantageScope 連到模擬
- 在 Swerve 分頁看四個輪子的目標和實際值
- 用模擬檢查 `optimize`、`cosineScale` 和 X 字鎖定
- 用折線圖看轉向的反應

## 觀念

### 連線到模擬

模擬的程式一樣會用 NetworkTables 送出 Telemetry，只是位址從機器人變成**自己的電腦**。

1. 啟動模擬（[模擬怎麼運作](sim-basics.md)）
2. 打開 **AdvantageScope**
3. 選 **File → Connect to Simulator**

之後的操作和連到真的機器人一樣（[第一次上電](../13-swerve-drive/first-drive.md#在-advantagescope-看四個輪子)）。

### 為什麼先在模擬裡看

真的機器人上，轉向弄錯可能會讓輪子狂轉、機器人亂衝。模擬裡什麼都不會壞，可以放心地：

- 故意把 `STEER_INVERTED` 改錯，看看「轉向方向反了」長什麼樣子
- 故意拿掉 `optimize`，看輪子繞遠路
- 改模組的順序，看機器人旋轉時亂掉的樣子

**先在模擬裡看過錯誤的樣子**，真的機器人出問題時就認得出來。

## 步驟

### 1. Swerve 分頁

1. 新增一個 **Swerve** 分頁
2. 把 `Telemetry/Drivetrain/ModuleVelocities` 和 `Telemetry/Drivetrain/TargetVelocities` 拖到 Sources
3. 把 `Telemetry/Drivetrain/HeadingDegrees` 拖到 Rotation
4. 在 Sim GUI 啟用 Teleop，用手把開車

推左搖桿往不同方向，兩組箭頭應該幾乎重疊。

### 2. 看 `optimize` 的效果

1. 推左搖桿往**前**，停一下
2. 快速改推往**後**

四個箭頭的**方向不變**，只是速度從正的變成負的（箭頭反過來），輪子沒有轉 180 度。這就是 `optimize`。

### 3. 看 `cosineScale` 的效果

1. 推左搖桿往前，然後快速改往左
2. 加一個折線圖，畫 `Swerve/FrontLeft/TargetVelocity` 和 `Swerve/FrontLeft/TargetAngleDegrees`

改變方向的瞬間，目標速度會先**掉下來**，等角度轉到位再回到原本的速度。

### 4. 看 X 字鎖定

按住 X 鍵，四個箭頭轉成 X 字形，速度都是 0。

### 5. 看轉向的反應

開一個折線圖，畫 `Swerve/FrontLeft/AngleDegrees` 和 `Swerve/FrontLeft/TargetAngleDegrees`。快速改變方向，看實際角度多快追上目標。這和[第 14 章調整參數](../14-swerve-tuning/tuning.md#1-轉向的-p) 的方法一樣，可以先在模擬裡練習怎麼看這張圖。

!!! info "機器人在場地上的位置"
    這一章還沒有 Odometry，所以只能看到機器人的朝向和輪子，看不到機器人在場地上移動。[第 16 章](../16-swerve-auto/odometry.md) 加上 Odometry 之後，就能在 2D / 3D 場地上看到模擬的機器人跑來跑去。

## 常見錯誤

!!! bug "常見錯誤：AdvantageScope 連到機器人的位址"
    模擬時要用 **Connect to Simulator**，不是 **Connect to Robot**。連到機器人的位址會一直顯示連線中。

!!! bug "常見錯誤：箭頭不動"
    確認 Sim GUI 的 Robot State 是 **Teleoperated**，而且手把有拖到 Joystick[0]。在 Disabled 時，馬達不會有輸出，輪子不會動。

## 小練習

1. 照「為什麼先在模擬裡看」的三個例子，各改一次程式，在 AdvantageScope 觀察，記錄每一種錯誤長什麼樣子。
2. 用折線圖量量看：模擬裡，輪子從 0 度轉到 90 度要多久？把 `STEER_KP` 改成兩倍再量一次。
3. 推左搖桿往前、同時推右搖桿旋轉，觀察四個輪子的箭頭。為什麼四個輪子的速度不一樣？

??? question "第 3 題參考答案"
    一邊往前一邊旋轉時，每個輪子的速度是「往前的速度」加上「旋轉造成的速度」。旋轉造成的速度和機器人中心到模組的連線垂直：逆時針轉時，右邊的模組被往前推、左邊的模組被往後推。所以右邊的輪子比較快、左邊比較慢，方向也稍微不同。
