# 做法一：組合 Command

--8<-- "alpha-warning.md"

## 這頁學什麼

- 把「一個意圖」寫成一個 Command 組合，Requirements 一次包含所有用到的機構
- 用 `waitUntil` 保證條件、用 `deadlineFor` 讓機構在等待時保持位置
- `withInterruptBehavior`：不讓其他按鈕在動作做到一半時搶走機構
- 這個做法在什麼時候開始變得難寫

## 觀念

### 一個意圖，一個 Command

[上一頁](problem.md) 的問題，很多都可以用[第 5 章的組合 Command](../05-commands/composition.md) 解決。重點是：**把駕駛的一個意圖，寫成一個 Command**，裡面包含這個意圖需要的所有機構。

| 意圖 | Command 要做的事 | Requirements |
| --- | --- | --- |
| 吸入 | 手臂收起 → 到位後 Intake 吸 → 拿到物件就結束 | Intake、Arm |
| 發射 | 手臂轉到發射角度、飛輪加速 → 兩個都到了才送料 → 送料 0.5 秒後結束 | Intake、Arm、Shooter |

組合 Command 的 Requirements 是**裡面所有 Command 的 Requirements 加起來**。所以發射的 Command 一開始執行，Intake、Arm、Shooter 三個都歸它管，右搖桿的手動控制、`faceLeft` 的收手臂都不能在中途插進來亂動。

### 用 `waitUntil` 保證條件

```java
Commands.waitUntil(() -> arm.isAtAngle(SCORE_ANGLE_DEGREES) && shooter.isAtSpeed())
    .andThen(intake.feedCommand().withTimeout(FEED_SECONDS))
```

`waitUntil` 會一直等到條件成立才結束，接著才送料。**沒有時間限制**：和第 8 章的 `shootWithArm()` 不同，條件沒成立就絕對不會送料。

### 用 `deadlineFor` 保持位置

等待和送料的時候，手臂要一直停在發射角度、飛輪要一直轉：

```java
.deadlineFor(arm.goToAngleCommand(SCORE_ANGLE_DEGREES), shooter.spinCommand())
```

`deadlineFor` 讓前面的 Command（等待 + 送料）當**主角**：主角結束，`deadlineFor` 裡的 Command 一起被停下來。`goToAngleCommand` 和 `spinCommand` 本身不會結束，剛好適合放在這裡。

### 不讓其他按鈕插隊：`withInterruptBehavior`

預設情況下，新的 Command 需要同一個 Subsystem 時，會**中斷**正在執行的 Command（`InterruptionBehavior.CANCEL_SELF`）。發射到一半時按 `faceRight` 吐出，吐出的 Command 會搶走 Intake，把發射中斷掉。

改成 `CANCEL_INCOMING`，正在執行的 Command 就不會被搶，**新來的 Command 直接不執行**：

```java
.withInterruptBehavior(InterruptionBehavior.CANCEL_INCOMING)
```

### 手臂自己收回來

給手臂一個 Default command：沒有其他 Command 用到手臂的時候，就回到收起的位置。

```java
arm.setDefaultCommand(arm.goToAngleCommand(ArmConstants.STOWED_ANGLE_DEGREES));
```

發射的 Command 結束後，Arm 沒有人用了，Scheduler 就會執行 Default command，手臂自動收起。駕駛不用再按 `faceLeft`。

## 範例

### ScoringCommands

把[第 8 章小練習](../08-control/motion-profile.md#小練習) 的 `shootWithArm()` 改寫成 `score()`，再加上吸入的 `intake()`：

```java title="src/main/java/first/robot/commands/ScoringCommands.java"
package first.robot.commands;

import first.robot.Constants.ArmConstants;
import first.robot.Constants.ShooterConstants;
import first.robot.subsystems.Arm;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import org.wpilib.command2.Command;
import org.wpilib.command2.Command.InterruptionBehavior;
import org.wpilib.command2.Commands;

public final class ScoringCommands {
  /** 手臂收起之後才開始吸，拿到遊戲物件為止。 */
  public static Command intake(Intake intake, Arm arm) {
    return Commands.waitUntil(() -> arm.isAtAngle(ArmConstants.STOWED_ANGLE_DEGREES))
        .andThen(intake.intakeCommand())
        .deadlineFor(arm.goToAngleCommand(ArmConstants.STOWED_ANGLE_DEGREES))
        .withName("IntakeSequence");
  }

  /** 手臂轉到發射角度、飛輪轉到夠快，兩個都到了才送料。 */
  public static Command score(Intake intake, Arm arm, Shooter shooter) {
    return Commands.waitUntil(
            () -> arm.isAtAngle(ArmConstants.SCORE_ANGLE_DEGREES) && shooter.isAtSpeed())
        .andThen(intake.feedCommand().withTimeout(ShooterConstants.FEED_SECONDS))
        .deadlineFor(
            arm.goToAngleCommand(ArmConstants.SCORE_ANGLE_DEGREES), shooter.spinCommand())
        .withInterruptBehavior(InterruptionBehavior.CANCEL_INCOMING)
        .withName("ScoreSequence");
  }

  private ScoringCommands() {}
}
```

- `intake()`：先等手臂收起（`deadlineFor` 讓手臂在這段時間一直往收起的位置轉），再執行 `intakeCommand()`。`intakeCommand()` 拿到物件就會結束，整個組合也就結束
- `score()`：拿掉了第 8 章的 `withTimeout`，條件沒成立就一直等
- `InterruptionBehavior` 是 `Command` 裡的 enum，要 `import org.wpilib.command2.Command.InterruptionBehavior`

### RobotContainer

```java title="src/main/java/first/robot/RobotContainer.java"
package first.robot;

import first.robot.Constants.ArmConstants;
import first.robot.Constants.OperatorConstants;
import first.robot.commands.ScoringCommands;
import first.robot.commands.TurnToAngle;
import first.robot.subsystems.Arm;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import org.wpilib.command2.Command;
import org.wpilib.command2.button.CommandGamepad;
import org.wpilib.math.filter.SlewRateLimiter;

public class RobotContainer {
  private final Drivetrain drivetrain = new Drivetrain();
  private final Intake intake = new Intake();
  private final Shooter shooter = new Shooter();
  private final Arm arm = new Arm();

  private final CommandGamepad driver = new CommandGamepad(OperatorConstants.DRIVER_CONTROLLER_PORT);
  private final SlewRateLimiter forwardLimiter = new SlewRateLimiter(3.0);

  public RobotContainer() {
    configureDefaultCommands();
    configureBindings();
  }

  private void configureDefaultCommands() {
    drivetrain.setDefaultCommand(
        drivetrain.arcadeDriveCommand(
            () -> forwardLimiter.calculate(-driver.getLeftY()), () -> -driver.getRightX()));
    intake.setDefaultCommand(intake.stopCommand());
    // 沒有其他 Command 用到手臂時，手臂回到收起的位置
    arm.setDefaultCommand(arm.goToAngleCommand(ArmConstants.STOWED_ANGLE_DEGREES));
  }

  private void configureBindings() {
    driver.faceDown().whileTrue(ScoringCommands.intake(intake, arm));
    driver.faceRight().whileTrue(intake.ejectCommand());
    driver.rightBumper()
        .and(intake::hasGamePiece)
        .onTrue(ScoringCommands.score(intake, arm, shooter));

    driver.start().onTrue(drivetrain.resetHeadingCommand());
    driver.dpadUp().onTrue(new TurnToAngle(drivetrain, 0));
    driver.dpadLeft().onTrue(new TurnToAngle(drivetrain, 90));
    driver.dpadDown().onTrue(new TurnToAngle(drivetrain, 180));
    driver.dpadRight().onTrue(new TurnToAngle(drivetrain, -90));
    driver.leftBumper().whileTrue(
        drivetrain.arcadeDriveCommand(
            () -> -driver.getLeftY() * 0.3, () -> -driver.getRightX() * 0.3));
  }

  public Command getAutonomousCommand() {
    return ScoringCommands.score(intake, arm, shooter).withTimeout(3.0).withName("ScorePreload");
  }
}
```

和第 8 章比，少了 `faceUp`、`faceLeft` 和右搖桿的手臂按鈕：手臂的位置現在完全由 Command 決定。駕駛只剩三個按鈕：`faceDown` 吸入、`rightBumper` 發射、`faceRight` 吐出。

自動階段的 `withTimeout(3.0)` 和第 8 章的不一樣：它加在**整個** `score()` 外面，時間到了是把整個 Command **取消**，不會送料。

## 這個做法的極限

這樣寫已經比第 8 章好很多。但多寫幾個動作、多上場幾次，就會遇到這些問題：

### 按下去就停不下來

`score()` 是 `CANCEL_INCOMING`，又沒有時間限制。如果手臂被卡住、到不了發射角度，`score()` 會一直等下去，而且**其他按鈕都搶不走機構**：Intake、Arm、Shooter 全部被鎖住，直到手臂到位為止。

想改成「放開 `rightBumper` 就取消」，把 `onTrue` 換成 `whileTrue`：

```java
driver.rightBumper()
    .and(intake::hasGamePiece)
    .whileTrue(ScoringCommands.score(intake, arm, shooter));
```

結果發射**每一次都會失敗**：送料時物件離開光遮斷感測器，`hasGamePiece()` 變成 `false`，整個 Trigger 變成 `false`，`whileTrue` 就把送到一半的 `score()` 取消了。要修好，就得把條件拆開、在 Command 裡面另外判斷，程式越來越難看懂。

### 看不出現在在哪一步

Dashboard 上只看得到「`ScoreSequence` 正在執行」。它現在是在等手臂、等飛輪，還是在送料？從外面看不出來，除錯時只能猜。

### 動作越多，組合越多

每多一個動作，就要想一次它和**所有**其他動作同時發生時會怎樣：

- 吸入到一半就按住 `rightBumper`？這時還沒拿到物件，`.and(hasGamePiece)` 擋住 `score()`，吸入繼續。吸到物件的那一刻，Trigger 從 `false` 變成 `true`，`score()` **自動開始**。這是你要的行為嗎？要仔細讀過每一個 Trigger 才推得出來
- 發射前想先把手臂舉起來等？（要再寫一個「只轉手臂和飛輪、不送料」的 Command，還要處理它和 `score()` 的銜接）
- 物件在等手臂的時候被撞掉了？（`score()` 不管物件還在不在，手臂和飛輪到位後照樣送料，對著空氣送 0.5 秒）

這些情況單獨看都能處理，但它們藏在一堆 `.until()`、`.and()`、`InterruptionBehavior` 裡，很難一眼看出機器人在每種情況下會做什麼。

**當你發現自己一直在問「如果在 A 的時候發生 B，會怎樣？」，就是該畫狀態圖的時候了。**[下一頁](state-machine.md) 介紹狀態機。

## 常見錯誤

!!! bug "常見錯誤：用 `alongWith` 代替 `deadlineFor`"
    `waitUntil(...).andThen(feed).alongWith(arm.goToAngleCommand(...))`：`alongWith` 要等**所有** Command 都結束，但 `goToAngleCommand` 永遠不會結束，整個組合也就永遠不會結束，手臂一直停在發射角度，Default command 也不會接手。

!!! bug "常見錯誤：`CANCEL_INCOMING` 又沒有出口"
    不讓別人中斷，又沒有時間限制或其他結束條件，Command 卡住時就沒有任何按鈕能救。至少要確定它**一定會結束**，或是駕駛有辦法取消（例如 `whileTrue` 放開按鈕）。

## 小練習

1. `intake()` 為什麼不用 `CANCEL_INCOMING`？想想吸入到一半時，駕駛想按哪些按鈕。
2. 加一個 `prepare()`：手臂轉到發射角度、飛輪加速，但**不送料**，按住 `leftTrigger` 時執行。放開後手臂會怎樣？
3. 發射時物件在等待階段掉了（被撞掉），`score()` 會怎樣？要怎麼改？

??? question "第 1 題參考答案"
    吸入的時候，駕駛常常需要「改變主意」：吸歪了想吐出（`faceRight`），或物件被別人搶走、想直接去發射別的東西。用預設的 `CANCEL_SELF`，這些按鈕都可以直接中斷吸入。吸入本身沒有「做到一半很危險」的步驟，不需要保護。

??? question "第 2 題參考答案"
    ```java
    public static Command prepare(Arm arm, Shooter shooter) {
      return Commands.parallel(
              arm.goToAngleCommand(ArmConstants.SCORE_ANGLE_DEGREES), shooter.spinCommand())
          .withName("Prepare");
    }
    ```

    ```java
    driver.leftTrigger().whileTrue(ScoringCommands.prepare(arm, shooter));
    ```

    放開後 `prepare()` 被取消，Arm 沒人用了，Default command 讓手臂收起來。問題是：按住 `leftTrigger` 的同時按 `rightBumper`，`score()` 會中斷 `prepare()`，手臂和飛輪會重新開始一次 `goToAngleCommand` 和 `spinCommand`。這裡剛好沒有影響（目標一樣）。但 `score()` 結束後，就算還按著 `leftTrigger`，`prepare()` 也不會重新開始（`whileTrue` 只在按下的那一刻排程），手臂會收起來。每多一個動作，就要多想一次這種銜接。

??? question "第 3 題參考答案"
    `waitUntil` 只等手臂和飛輪，不管物件還在不在，所以會照常送料，對著空氣送 0.5 秒才結束。

    直覺的改法是在整個 `score()` 外面加 `.until(() -> !intake.hasGamePiece())`，但這樣送料時物件離開感測器也會觸發，和上面 `whileTrue` 的問題一樣，每次都送到一半就停。

    比較好的改法是讓**送料那一步**在開始前檢查一次：

    ```java
    .andThen(intake.feedCommand().withTimeout(ShooterConstants.FEED_SECONDS).onlyIf(intake::hasGamePiece))
    ```

    `onlyIf` 是在**輪到這一步的那一刻**判斷一次：物件還在就送料，不在就直接跳過。送料開始之後條件變成 `false`，也不會中斷。

    這正是狀態機要解決的問題：**同一個感測器的變化，在不同的階段代表不同的意思**。等待時物件不見了是「掉了」，送料時物件不見了是「射出去了」。
