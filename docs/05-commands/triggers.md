# Trigger 與按鍵綁定

--8<-- "alpha-warning.md"

## 這頁學什麼

- 用 `CommandGamepad` 取得按鈕的 Trigger
- `onTrue`、`whileTrue`、`toggleOnTrue` 等綁定方式的差別
- 用感測器的狀態當 Trigger
- 組合多個 Trigger

## 觀念

### Trigger 是什麼

**Trigger（觸發條件）** 代表一個會變成 `true` 或 `false` 的條件，例如「`faceDown` 按鈕有沒有被按下」。把 Command 綁到 Trigger 上，Scheduler 就會在條件變化時啟動或停止 Command。

### `CommandGamepad`

第 4 章用 `Gamepad` 讀按鈕；Command-based 改用 **`CommandGamepad`**，它的每個按鈕方法都會回傳一個 `Trigger`：

```java
private final CommandGamepad driver = new CommandGamepad(0);

driver.faceDown()       // faceDown 按鈕的 Trigger
driver.rightBumper()    // 右肩鍵的 Trigger
driver.rightTrigger()   // 右板機按下超過一半的 Trigger
driver.dpadUp()         // 十字鍵上的 Trigger
```

讀搖桿數值的方法和 `Gamepad` 一樣：`driver.getLeftY()` 等。

### 綁定方式

| 方法 | 行為 | 常見用途 |
| --- | --- | --- |
| `onTrue(cmd)` | 條件**變成** `true` 時，啟動 `cmd` | 按一下就執行整套動作，例如發射 |
| `whileTrue(cmd)` | 條件**變成** `true` 時啟動，**變回** `false` 時取消 | 按住才動，例如按住吸入 |
| `toggleOnTrue(cmd)` | 每次條件**變成** `true`，在啟動和取消之間切換 | 按一下開、再按一下關 |
| `onFalse(cmd)` | 條件**變成** `false` 時，啟動 `cmd` | 放開按鈕時做某件事 |
| `whileFalse(cmd)` | 條件是 `false` 的期間執行 | 較少用 |

注意 **`onTrue` 只負責啟動**，按鈕放開不會停止 Command；Command 要自己結束（或被中斷）。**`whileTrue` 會在放開時取消** Command，觸發它的 `end(true)`。

```java
driver.faceDown().whileTrue(intake.intakeCommand());      // 按住吸入，放開停止
driver.faceRight().whileTrue(intake.ejectCommand());      // 按住吐出
driver.rightBumper().onTrue(ScoringCommands.shoot(intake, shooter));  // 按一下就完成整套發射
```

### 用感測器當 Trigger

Trigger 不一定是按鈕。任何回傳 `boolean` 的方法都能變成 Trigger：

```java
new Trigger(intake::hasGamePiece)
    .onTrue(Commands.print("拿到遊戲物件！"));
```

`intake::hasGamePiece` 是 [method reference](../01-java/lambda.md#method-reference方法參考)，型別是 `BooleanSupplier`。Scheduler 每 20 ms 會呼叫它一次，檢查有沒有從 `false` 變成 `true`。

### 組合 Trigger

| 方法 | 意思 |
| --- | --- |
| `a.and(b)` | `a` 和 `b` 都成立 |
| `a.or(b)` | `a` 或 `b` 其中一個成立 |
| `a.negate()` | `a` 不成立 |
| `a.debounce(秒數)` | `a` 持續成立超過指定秒數才算成立，用來過濾雜訊 |

```java
// 按住 rightBumper，而且有遊戲物件時，才能發射
driver.rightBumper()
    .and(intake::hasGamePiece)
    .onTrue(ScoringCommands.shoot(intake, shooter));
```

### 綁定要寫在哪裡

所有的綁定都寫在 `RobotContainer` 的 `configureBindings()` 裡，**只在程式啟動時執行一次**。綁好之後 Scheduler 會一直檢查，不需要放在 `Periodic` 裡。完整的寫法見 [RobotContainer](robot-container.md)。

## 常見錯誤

!!! bug "常見錯誤：用 `onTrue` 綁一個不會結束的 Command"
    ```java
    driver.faceRight().onTrue(intake.ejectCommand());   // ❌ 按一下就一直吐，停不下來
    ```

    `ejectCommand()` 不會自己結束，`onTrue` 也不會在放開時取消它。按住才動的動作要用 `whileTrue`。

!!! bug "常見錯誤：在 `Periodic` 裡綁定按鍵"
    每 20 ms 綁一次，同一個按鈕會被綁上幾千個 Command。綁定只在 `configureBindings()` 裡做一次。

!!! bug "常見錯誤：兩個按鈕綁到同一個 Command 物件"
    ```java
    Command intakeCmd = intake.intakeCommand();
    driver.faceDown().whileTrue(intakeCmd);
    driver.leftBumper().whileTrue(intakeCmd);   // 可以，但要小心
    ```

    直接綁定可以共用同一個物件，但如果這個 Command 也被放進[組合](composition.md)裡，就不能再單獨綁定或執行。最保險的做法是**每次都呼叫 factory 取得新的 Command**：`intake.intakeCommand()`。

## 小練習

1. 把 `faceDown` 綁定為按住吸入、`faceRight` 綁定為按住吐出，部署並測試。
2. 把吸入改成 `toggleOnTrue`，比較兩種操作感覺有什麼不同。哪一種比較適合比賽？為什麼？
3. 加一個 Trigger：拿到遊戲物件時，讓手把震動 0.5 秒。（提示：`driver.getHID().setRumble(...)`，可以用 `startEnd` 搭配 `withTimeout`；查查看 `setRumble` 需要什麼參數）

??? question "第 2 題參考答案"
    - **`whileTrue`**（按住吸入）：放開就停，駕駛不用記住現在是開還是關，比較直覺
    - **`toggleOnTrue`**（按一下開、再按一下關）：駕駛的手指可以休息，但容易忘記現在的狀態

    吸入通常用 `whileTrue` 比較適合：搭配「拿到物件自動停止」，駕駛只要按住到拿到為止。`toggleOnTrue` 比較適合長時間持續的動作，例如比賽中一直讓 Shooter 預轉。

??? question "第 3 題參考答案"
    ```java
    new Trigger(intake::hasGamePiece).onTrue(
        Commands.startEnd(
                () -> driver.getHID().setRumble(RumbleType.LEFT_RUMBLE, 1.0),
                () -> driver.getHID().setRumble(RumbleType.LEFT_RUMBLE, 0.0))
            .withTimeout(0.5));
    ```

    `RumbleType` 要 `import org.wpilib.driverstation.GenericHID.RumbleType;`。`setRumble` 的第二個參數是強度（0.0～1.0）。這個 Command 不需要任何 Subsystem，所以不會中斷其他 Command。
