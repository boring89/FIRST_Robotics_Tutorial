# 部署與除錯

--8<-- "alpha-warning.md"

## 這頁學什麼

- 把程式部署到 Systemcore
- 用 Driver Station 啟用機器人
- 看程式印出的訊息和錯誤
- 用 Telemetry 即時顯示資料

## 步驟

### 1. 連上機器人

1. 接上電池，打開主斷路器
2. 用 USB-C、網路線，或 Systemcore 的 Wi-Fi 熱點連線（見 [廠商工具](../03-setup/vendor-tools.md#systemcore-設定網頁)）
3. 確認 Systemcore 設定網頁裡的隊號，和專案 `.wpilib/wpilib_preferences.json` 裡的隊號**一樣**

### 2. 部署程式

在 VS Code 按 ++shift+f5++，或用 Command Palette 選 **WPILib: Deploy Robot Code**。

Gradle 會先編譯，再把程式傳到 Systemcore。成功時終端機最後會出現：

```text
BUILD SUCCESSFUL
```

**Deploy（部署）** 之後，Systemcore 會自動重新啟動機器人程式。之後每次開機，Systemcore 都會執行最後一次部署的程式，不用每次都重新部署。

### 3. 用 Driver Station 啟用

1. 打開 FIRST Driver Station，設定和 Systemcore 一樣的隊號
2. 等它顯示已經連上機器人、而且有偵測到機器人程式
3. 選擇模式（例如 Teleop）
4. 確認周圍安全，喊「Enable！」，按下 **Enable**

### 4. 看輸出訊息

`System.out.println()` 印出的訊息和錯誤訊息，可以用 Command Palette 的 **WPILib: Start RioLog** 查看。RioLog 會在 VS Code 裡開一個視窗，即時顯示機器人程式的輸出。

## 觀念

### 讀懂錯誤訊息

程式在執行時出錯，會印出一段 **Stack trace（堆疊追蹤）**，然後機器人程式會重新啟動。內容類似這樣：

```text
Unhandled exception: java.lang.NullPointerException: Cannot invoke "first.robot.Intake.run()" because "this.intake" is null
    at first.robot.Robot.teleopPeriodic(Robot.java:27)
    at org.wpilib.framework.IterativeRobotBase.loopFunc(...)
    ...
```

讀法：

1. **第一行**是錯誤的種類和原因：`NullPointerException`，因為 `intake` 是 `null`（還記得 [類別與物件](../01-java/classes.md#常見錯誤) 的「忘記 `new`」嗎？）
2. **往下找第一個 `first.robot` 開頭的行**，那是你自己寫的程式：`Robot.java` 的第 27 行
3. `org.wpilib` 開頭的行是 WPILib 內部的程式，通常不用看

### Telemetry：即時看到程式裡的數值

用 `println` 印數值有個問題：每 20 ms 印一次，訊息會刷得太快，根本看不清楚。**Telemetry（遙測）** 會把數值傳到電腦上的 Dashboard（儀表板），只顯示最新的值。

```java
Telemetry.log("Motor/Throttle", throttle);
Telemetry.log("Gamepad/FaceDown", gamepad.getFaceDownButton());
```

- 第一個參數是**名稱**，用 `/` 分組，例如 `Motor/Throttle`
- 第二個參數是**數值**，可以是數字、`boolean`、文字

把 `Telemetry.log()` 放在 `robotPeriodic()` 裡，資料就會一直更新。在電腦上打開 **Elastic**（用 **WPILib: Start Tool** 打開），設定隊號連上機器人，就能看到這些數值，也能拖曳成圖表。

!!! info "2027 新改動"
    2026 以前常用 `SmartDashboard.putNumber()` 顯示數值。2027 移除了 SmartDashboard，改用 `Telemetry.log()`。資料一樣透過 NetworkTables 傳送，放在 `/Telemetry` 底下，Elastic 和 AdvantageScope 都能讀取。

## 範例

在上一頁的程式加上 Telemetry 和按鈕訊息：

=== "REV"

    ```java title="src/main/java/first/robot/Robot.java"
    package first.robot;

    import com.revrobotics.spark.SparkLowLevel.MotorType;
    import com.revrobotics.spark.SparkMax;
    import org.wpilib.driverstation.Gamepad;
    import org.wpilib.framework.TimedRobot;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.telemetry.Telemetry;

    public class Robot extends TimedRobot {
      private static final double MAX_THROTTLE = 0.5;

      private final SparkMax motor = new SparkMax(CANPort.CAN_S0, 10, MotorType.kBrushless);
      private final Gamepad gamepad = new Gamepad(0);
      private double throttle = 0.0;

      @Override
      public void robotPeriodic() {
        Telemetry.log("Motor/Throttle", throttle);
        Telemetry.log("Gamepad/LeftY", gamepad.getLeftY());
      }

      @Override
      public void teleopPeriodic() {
        throttle = -gamepad.getLeftY() * MAX_THROTTLE;
        motor.setThrottle(throttle);

        if (gamepad.getFaceDownButtonPressed()) {
          System.out.println("faceDown 被按下，目前輸出：" + throttle);
        }
      }

      @Override
      public void disabledInit() {
        throttle = 0.0;
        motor.stopMotor();
      }
    }
    ```

=== "CTRE"

    ```java title="src/main/java/first/robot/Robot.java"
    package first.robot;

    import com.ctre.phoenix6.CANBus;
    import com.ctre.phoenix6.hardware.TalonFX;
    import org.wpilib.driverstation.Gamepad;
    import org.wpilib.framework.TimedRobot;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.telemetry.Telemetry;

    public class Robot extends TimedRobot {
      private static final double MAX_THROTTLE = 0.5;

      private final TalonFX motor = new TalonFX(1, new CANBus(CANPort.CAN_S0));
      private final Gamepad gamepad = new Gamepad(0);
      private double throttle = 0.0;

      @Override
      public void robotPeriodic() {
        Telemetry.log("Motor/Throttle", throttle);
        Telemetry.log("Gamepad/LeftY", gamepad.getLeftY());
      }

      @Override
      public void teleopPeriodic() {
        throttle = -gamepad.getLeftY() * MAX_THROTTLE;
        motor.setThrottle(throttle);

        if (gamepad.getFaceDownButtonPressed()) {
          System.out.println("faceDown 被按下，目前輸出：" + throttle);
        }
      }

      @Override
      public void disabledInit() {
        throttle = 0.0;
        motor.stopMotor();
      }
    }
    ```

## 常見錯誤

!!! bug "常見錯誤：部署時找不到機器人"
    錯誤訊息類似「找不到 Systemcore」或連線逾時。檢查：

    1. 電腦有沒有連上 Systemcore（USB、網路線或 Wi-Fi）
    2. 專案的隊號和 Systemcore 設定的隊號是否一樣
    3. 用瀏覽器打開 `http://robot.local`，打得開代表網路是通的

!!! bug "常見錯誤：部署成功，但 Driver Station 顯示沒有程式"
    程式一啟動就當掉了。打開 RioLog 看錯誤訊息，最常見的是建構子或欄位初始化時出錯，例如 CAN 裝置設定錯誤。

!!! bug "常見錯誤：程式一直重新啟動"
    每次重啟前都會印出一段 stack trace。找到**第一段**錯誤訊息（不是最後一段），照上面的讀法找到出錯的那一行。

## 小練習

1. 部署範例程式，打開 RioLog，按下 `faceDown` 按鈕，確認訊息有印出來。
2. 打開 Elastic，連上機器人，找到 `Motor/Throttle`，推動搖桿看數值變化。
3. 在 Telemetry 加上馬達控制器回報的資料。（提示：REV 用 `motor.getAppliedOutput().get()`；CTRE 用 `motor.getDutyCycle().getValueAsDouble()`）
4. 故意在 `teleopPeriodic()` 裡寫一個會出錯的程式（例如呼叫一個 `null` 物件的方法），部署後在 RioLog 找到錯誤訊息，指出是哪一行。
