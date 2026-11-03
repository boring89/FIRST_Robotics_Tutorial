# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## Follower：讓兩顆馬達一起動

很多機構用兩顆馬達一起出力（例如底盤一邊兩顆、雙馬達的手臂）。與其在程式裡對兩顆馬達下一樣的指令，可以把第二顆設成 **Follower（跟隨者）**，讓它自動跟著第一顆（**Leader**）動：

=== "REV"

    ```java
    SparkMaxConfig followerConfig = new SparkMaxConfig();
    followerConfig.follow(leader, true);   // true：方向和 leader 相反
    follower.configure(followerConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    ```

=== "CTRE"

    ```java
    follower.setControl(new Follower(leader.getDeviceID(), MotorAlignmentValue.Opposed));
    ```

    `MotorAlignmentValue.Opposed` 代表方向和 leader 相反，同方向用 `Aligned`。

之後只要控制 leader，follower 就會一起動。兩顆馬達裝的方向相反時（很常見），記得設定反向，否則兩顆會互相抵抗。

## 訊號的更新頻率

馬達控制器會定期透過 CAN 回報資料（位置、速度、電流、溫度……）。每種資料都佔用 CAN 頻寬，回報越頻繁，匯流排越忙。

- **REV**：用設定裡的 `config.signals` 調整每種資料的回報週期
- **CTRE**：用 `signal.setUpdateFrequency(...)` 調整，或用 `motor.optimizeBusUtilization()` 把沒用到的訊號頻率降到最低

當機器人上的 CAN 裝置很多、CAN 使用率偏高時，再來調整。2027 的 Systemcore 有 5 條 CAN 匯流排，把裝置分散到不同匯流排也是好方法。

## Signal 的有效性

REVLib 2027 的讀值包在 `Signal` 裡，Phoenix 6 的讀值包在 `StatusSignal` 裡，兩者都能告訴你**這個值是不是最新的、有沒有錯誤**：

=== "REV"

    ```java
    Signal<Double> velocity = encoder.getVelocity();
    if (velocity.isValid()) {
      double rpm = velocity.get();
    }
    ```

=== "CTRE"

    ```java
    StatusSignal<AngularVelocity> velocity = motor.getVelocity();
    if (velocity.getStatus().isOK()) {
      double rps = velocity.getValueAsDouble();
    }
    ```

馬達控制器斷線（CAN 線鬆了）時，讀值會變成無效。重要的判斷（例如「手臂到位了沒」）如果用到無效的讀值，可能會做出危險的動作。比賽用的程式可以在讀值無效時讓機構停下來，並用 Telemetry 或 `Alert` 提醒駕駛。

## Stator current 和 Supply current

CTRE 有兩種電流限制，REV 的 `smartCurrentLimit` 比較接近 Stator current：

| | Stator current | Supply current |
| --- | --- | --- |
| 量的是 | 流過馬達線圈的電流 | 從電池流進控制器的電流 |
| 限制它可以 | 限制扭力、防止馬達過熱 | 防止電池電壓掉太多（Brownout） |
| 什麼時候比較大 | 低速、高扭力（例如推牆） | 高速、高功率 |

兩種都設定是比較完整的做法。詳細的建議值可以參考 [CTRE 的文件](https://v6.docs.ctr-electronics.com/)。

## 單位函式庫

WPILib 有一套 **Units（單位）** 函式庫，可以讓程式裡的數字帶著單位，例如 `Meters.of(1.5)`、`RotationsPerSecond.of(50)`，避免把公尺和英寸搞混。Phoenix 6 的 `StatusSignal` 也會回傳帶單位的值（`getValue()`），例如 `AngularVelocity`。

這份教學為了讓新手容易讀，大部分使用 `double` 搭配清楚的變數名稱（例如 `getAngleDegrees()`）。熟悉之後，可以試試看用單位函式庫改寫。
