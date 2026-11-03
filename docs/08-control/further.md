# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 在 Systemcore 上做 Motion Profile

控制器內建的 MAXMotion / Motion Magic 只能用接在控制器上的感測器。需要其他感測器時（例如用 IMU 轉向、或控制器不支援的機構），可以用 WPILib 的 **`ProfiledPIDController`**：它結合了 `PIDController` 和 `TrapezoidProfile`，在 Systemcore 上規劃曲線並計算 PID。

```java
ProfiledPIDController controller = new ProfiledPIDController(
    kP, kI, kD,
    new TrapezoidProfile.Constraints(最大速度, 最大加速度));

controller.setGoal(目標位置);
double output = controller.calculate(目前位置);   // 每 20 ms 呼叫一次
boolean done = controller.atGoal();
```

和 `PIDController` 的差別是：`setGoal` 設定的是**最終目標**，控制器每次迴圈會沿著曲線往前推一點，PID 追的是曲線上的**中間目標** (`getSetpoint()`)。

## Feedforward 搭配 Motion Profile

Motion Profile 不只告訴你每個時間點的位置，還有**速度**。把曲線上的速度交給 Feedforward，就能預先給出需要的電壓：

```java
double output = controller.calculate(目前位置)
    + feedforward.calculate(controller.getSetpoint().velocity);
```

這是「Feedforward 負責大部分、PID 負責修正」最完整的形式。MAXMotion 和 Motion Magic 在控制器內部做的就是這件事。

## 為什麼 I 通常不用

I 會把**過去**的誤差累積起來。機構卡住（例如手臂被擋住）時，誤差一直存在，I 會越累積越大；擋住的東西一移開，累積的 I 會讓機構猛然衝出去，這叫 **Integral windup（積分飽和）**。

大部分的「差一點點到不了」，其實是缺少 Feedforward（kS 或 kG）。先把 Feedforward 調好，通常就不需要 I。

## Controls Engineering

想深入了解控制理論的話：

- [Controls Engineering in FRC](https://github.com/calcmogul/controls-engineering-in-frc)（Tyler Veness）：FRC 控制理論的經典教材，從 PID 一路講到 State-space control（狀態空間控制）
- [WPILib 文件：Advanced Controls](https://docs.wpilib.org/en/latest/docs/software/advanced-controls/index.html)：PID、Feedforward、Motion Profile、State-space 的官方說明，還有互動式的模擬可以玩

**State-space control（狀態空間控制）** 用矩陣描述整個系統，可以設計出比 PID 更好的控制器（例如 LQR），也可以結合多個感測器的資料（Kalman filter）。這是大學程度的內容，有興趣的同學可以挑戰。
