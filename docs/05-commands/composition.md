# 組合 Command

--8<-- "alpha-warning.md"

## 這頁學什麼

- 依序執行：`andThen`
- 同時執行：`alongWith`、`raceWith`、`deadlineFor`
- 加上結束條件：`withTimeout`、`until`
- 組合之後的 Requirements
- 把常用的組合寫成 factory

## 觀念

### 為什麼要組合

「發射」這個動作其實是好幾個小動作組成的：

1. Shooter 開始轉
2. 等 1 秒，讓 Shooter 轉到夠快
3. Intake 把物件送進 Shooter，持續 0.5 秒
4. 全部停止

每個小動作都已經是一個 Command 了（`shooter.spinCommand()`、`intake.feedCommand()`），我們只需要把它們**組合**起來。組合出來的結果也是一個 Command，可以綁在按鈕上，也可以再跟別的 Command 組合。

### 依序執行

```java
a.andThen(b)            // a 結束後執行 b
a.andThen(b, c)         // a → b → c
Commands.sequence(a, b, c)   // 同上
```

### 同時執行

三種同時執行的方式，差別在**什麼時候結束**：

| 寫法 | 什麼時候結束 | 例子 |
| --- | --- | --- |
| `a.alongWith(b)` | **全部**都結束 | 手臂和升降機構同時移動到定位 |
| `a.raceWith(b)` | **任何一個**結束（其他的被中斷） | 吸入，或是 3 秒到了 |
| `a.deadlineFor(b)` | **`a` 結束**時（`b` 被中斷） | Shooter 在整個送料過程中持續轉 |

以 `a` 需要 2 秒、`b` 需要 3 秒為例（`█` 是執行中，`✕` 是被中斷）：

```text
                    時間（秒）
                    0    1    2    3
a.alongWith(b)  a   ██████████
                b   ███████████████      → 3 秒時結束（等兩個都做完）

a.raceWith(b)   a   ██████████
                b   ██████████✕          → 2 秒時結束（a 先做完，b 被中斷）

a.deadlineFor(b) a  ██████████
                 b  ██████████✕          → 2 秒時結束（a 是 deadline）
```

`raceWith` 和 `deadlineFor` 在這個例子裡結果一樣，差別在 `b` 比較快結束的時候：`raceWith` 會跟著結束，`deadlineFor` 則會繼續等 `a`。

### 加上結束條件

| 修飾 | 意思 |
| --- | --- |
| `cmd.withTimeout(秒數)` | 時間到就中斷 |
| `cmd.until(條件)` | 條件成立就中斷 |
| `cmd.onlyWhile(條件)` | 條件不成立就中斷 |
| `cmd.unless(條件)` | 開始時條件成立的話，直接不執行 |
| `cmd.repeatedly()` | 結束後重新開始，一直重複 |
| `cmd.finallyDo(動作)` | 結束時（不管怎麼結束）額外做一件事 |

這些修飾其實也是組合：`cmd.withTimeout(1.0)` 等於 `cmd.raceWith(Commands.waitSeconds(1.0))`。

### 組合之後的 Requirements

組合出來的 Command，Requirements 是**所有成員的 Requirements 加起來**，而且從整個組合**開始**就佔用、到**結束**才釋放。

以「發射」為例：它需要 Shooter 和 Intake。就算 Intake 要等 1 秒後才開始送料，這 1 秒裡 Intake 也已經被佔用了，Intake 的 default command 不會執行，其他需要 Intake 的 Command 也會中斷發射。

## 範例

### Shooter Subsystem

`Shooter` 只有一個 Command：執行期間轉動、結束時停止。

=== "REV"

    ```java title="src/main/java/first/robot/subsystems/Shooter.java"
    package first.robot.subsystems;

    import com.revrobotics.spark.SparkFlex;
    import com.revrobotics.spark.SparkLowLevel.MotorType;
    import first.robot.Constants.ShooterConstants;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.bus.CANPort;

    public class Shooter extends SubsystemBase {
      private final SparkFlex motor =
          new SparkFlex(CANPort.CAN_S1, ShooterConstants.MOTOR_ID, MotorType.kBrushless);

      public Command spinCommand() {
        return startEnd(() -> motor.setThrottle(ShooterConstants.SHOOT_THROTTLE), motor::stopMotor)
            .withName("Spin");
      }
    }
    ```

=== "CTRE"

    ```java title="src/main/java/first/robot/subsystems/Shooter.java"
    package first.robot.subsystems;

    import com.ctre.phoenix6.CANBus;
    import com.ctre.phoenix6.hardware.TalonFX;
    import first.robot.Constants.ShooterConstants;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.hardware.bus.CANPort;

    public class Shooter extends SubsystemBase {
      private final TalonFX motor = new TalonFX(ShooterConstants.MOTOR_ID, new CANBus(CANPort.CAN_S1));

      public Command spinCommand() {
        return startEnd(() -> motor.setThrottle(ShooterConstants.SHOOT_THROTTLE), motor::stopMotor)
            .withName("Spin");
      }
    }
    ```

### 發射的組合

需要好幾個 Subsystem 的組合，習慣放在 `commands/` 資料夾的一個 factory 類別裡：

```java title="src/main/java/first/robot/commands/ScoringCommands.java"
package first.robot.commands;

import first.robot.Constants.ShooterConstants;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;

public final class ScoringCommands {
  /** Shooter 轉 1 秒後，Intake 送料 0.5 秒。整個過程中 Shooter 持續轉動。 */
  public static Command shoot(Intake intake, Shooter shooter) {
    return Commands.waitSeconds(ShooterConstants.SPIN_UP_SECONDS)
        .andThen(intake.feedCommand().withTimeout(ShooterConstants.FEED_SECONDS))
        .deadlineFor(shooter.spinCommand())
        .withName("Shoot");
  }

  private ScoringCommands() {}
}
```

由內往外讀：

1. `Commands.waitSeconds(1.0).andThen(intake.feedCommand().withTimeout(0.5))`：等 1 秒，再送料 0.5 秒。這是 **deadline**，總共 1.5 秒
2. `.deadlineFor(shooter.spinCommand())`：deadline 執行期間，Shooter 一直轉；deadline 結束時，Shooter 的 Command 被中斷，`end` 讓馬達停止
3. `.withName("Shoot")`：取名字

`private ScoringCommands() {}` 讓這個類別不能被 `new`，因為它只是用來放 `static` 方法的工具類別。

## 常見錯誤

!!! bug "常見錯誤：同一個 Command 物件用在兩個地方"
    ```java
    Command feed = intake.feedCommand();
    Command a = feed.withTimeout(0.5);
    Command b = feed.withTimeout(1.0);   // ❌ 執行時出錯
    ```

    一個 Command 物件被放進組合之後，就**不能再放進別的組合，也不能單獨執行**，否則會出現類似 `Commands that have been composed may not be added to another composition or scheduled individually` 的錯誤。需要的時候，呼叫 factory 取得新的 Command。

!!! bug "常見錯誤：`alongWith` 搭配不會結束的 Command"
    `alongWith` 要等全部結束，如果其中一個是 `spinCommand()` 這種不會自己結束的 Command，整個組合就永遠不會結束。這種情況要用 `deadlineFor` 或 `raceWith`。

!!! bug "常見錯誤：組合裡兩個成員需要同一個 Subsystem"
    `intake.intakeCommand().alongWith(intake.ejectCommand())` 會出錯：同時執行的成員不能共用 Subsystem。依序執行（`andThen`）就沒有這個問題。

## 小練習

1. 寫一個 `ScoringCommands.intakeThenShoot(intake, shooter)`：先吸入直到拿到物件（最多 3 秒），再發射。
2. 修改 `shoot()`：如果 Intake 裡沒有遊戲物件，就直接不執行。（提示：`unless`）
3. 想一想：發射時把 Shooter 轉 1 秒的做法有什麼缺點？如果有編碼器可以量轉速，可以怎麼改？（[第 6 章](../06-motors-sensors/encoders.md) 會學到）

??? question "第 1 題參考答案"
    ```java
    public static Command intakeThenShoot(Intake intake, Shooter shooter) {
      return intake.intakeCommand()
          .withTimeout(3.0)
          .andThen(shoot(intake, shooter))
          .withName("IntakeThenShoot");
    }
    ```

??? question "第 2 題參考答案"
    ```java
    public static Command shoot(Intake intake, Shooter shooter) {
      return Commands.waitSeconds(ShooterConstants.SPIN_UP_SECONDS)
          .andThen(intake.feedCommand().withTimeout(ShooterConstants.FEED_SECONDS))
          .deadlineFor(shooter.spinCommand())
          .unless(() -> !intake.hasGamePiece())
          .withName("Shoot");
    }
    ```

    `unless` 在 Command **開始的那一刻**檢查條件：條件成立就直接結束，不會執行。

??? question "第 3 題參考答案"
    缺點：轉 1 秒後的轉速會隨著**電池電量**、飛輪的磨損而改變，電量低時轉速不夠，遊戲物件就打不遠。另外如果轉速其實 0.6 秒就到了，剩下的 0.4 秒都是浪費。

    有編碼器的話，改成「**等到轉速夠快**再送料」：把 `Commands.waitSeconds(...)` 換成 `Commands.waitUntil(shooter::isAtSpeed)`，再加上逾時保護。[第 6 章](../06-motors-sensors/encoders.md#shooter轉速夠快才發射)就是這樣改的。
