# 測試狀態機

--8<-- "alpha-warning.md"

## 這頁學什麼

- 用 **JUnit** 寫自動測試，檢查 `nextState()` 的每一種轉換
- 在 VS Code 和終端機執行測試
- 改程式之後，用測試確認沒有改壞別的地方

## 觀念

### 為什麼要測試狀態機

[狀態機那頁](state-machine.md#畫好之後檢查每一格) 的表格有 24 格，再加上感測器的各種組合。每次改程式（例如加一個 `PREPARE` Goal），都要重新確認**每一格**還是對的。上機器人一格一格試，要擺好手臂、塞物件、按按鈕，一次就要好幾分鐘，而且很容易漏掉。

**Unit test（單元測試）** 是一段檢查程式的程式：給 `nextState()` 指定的參數，檢查它回傳的狀態對不對。[上一頁](superstructure.md#狀態轉換不碰硬體) 把 `nextState()` 寫成不碰硬體的 `static` 方法，就是為了這一刻：測試不需要機器人、不需要模擬馬達，幾秒鐘就能跑完所有情況。

### JUnit

WPILib 的專案範本已經設定好 **JUnit**（Java 最常用的測試工具）。測試放在 `src/test/java/` 底下，和 `src/main/java/` 的資料夾結構對應：

| 程式 | 測試 |
| --- | --- |
| `src/main/java/first/robot/subsystems/Superstructure.java` | `src/test/java/first/robot/subsystems/SuperstructureTest.java` |

一個測試就是一個加上 **`@Test`** 的方法，裡面用 **`assertEquals(預期的值, 實際的值)`** 檢查結果。值不一樣，這個測試就**失敗**，並告訴你預期是什麼、實際算出什麼。

## 範例

```java title="src/test/java/first/robot/subsystems/SuperstructureTest.java"
package first.robot.subsystems;

import static org.junit.jupiter.api.Assertions.assertEquals;

import first.robot.subsystems.Superstructure.Goal;
import first.robot.subsystems.Superstructure.Inputs;
import first.robot.subsystems.Superstructure.State;
import org.junit.jupiter.api.Test;

class SuperstructureTest {
  // 常用的幾種感測器情況
  private static final Inputs EMPTY_ARM_UP = new Inputs(false, false, true, false, 0.0);
  private static final Inputs EMPTY_ARM_DOWN = new Inputs(false, true, false, false, 0.0);
  private static final Inputs HOLDING_ARM_DOWN = new Inputs(true, true, false, false, 0.0);
  private static final Inputs ARM_UP_SLOW_SHOOTER = new Inputs(true, false, true, false, 0.0);
  private static final Inputs READY_TO_SHOOT = new Inputs(true, false, true, true, 0.0);

  @Test
  void intakeWaitsForArmToStow() {
    assertEquals(State.EMPTY, Superstructure.nextState(State.EMPTY, Goal.INTAKE, EMPTY_ARM_UP));
    assertEquals(State.INTAKING, Superstructure.nextState(State.EMPTY, Goal.INTAKE, EMPTY_ARM_DOWN));
  }

  @Test
  void gamePieceDetectedMeansHolding() {
    assertEquals(State.HOLDING, Superstructure.nextState(State.INTAKING, Goal.INTAKE, HOLDING_ARM_DOWN));
  }

  @Test
  void releasingIntakeButtonStopsIntaking() {
    assertEquals(State.EMPTY, Superstructure.nextState(State.INTAKING, Goal.IDLE, EMPTY_ARM_DOWN));
  }

  @Test
  void shootsOnlyWhenArmAndShooterAreReady() {
    assertEquals(State.PREPARING, Superstructure.nextState(State.HOLDING, Goal.SCORE, HOLDING_ARM_DOWN));
    assertEquals(State.PREPARING, Superstructure.nextState(State.PREPARING, Goal.SCORE, ARM_UP_SLOW_SHOOTER));
    assertEquals(State.SHOOTING, Superstructure.nextState(State.PREPARING, Goal.SCORE, READY_TO_SHOOT));
  }

  @Test
  void releasingScoreButtonBeforeShootingKeepsGamePiece() {
    assertEquals(State.HOLDING, Superstructure.nextState(State.PREPARING, Goal.IDLE, ARM_UP_SLOW_SHOOTER));
  }

  @Test
  void shootingFinishesEvenIfButtonIsReleased() {
    Inputs justStarted = new Inputs(false, false, true, true, 0.1);
    Inputs done = new Inputs(false, false, true, true, 0.5);
    assertEquals(State.SHOOTING, Superstructure.nextState(State.SHOOTING, Goal.IDLE, justStarted));
    assertEquals(State.EMPTY, Superstructure.nextState(State.SHOOTING, Goal.IDLE, done));
  }

  @Test
  void everyStateHandlesEveryGoal() {
    // 不管什麼組合都不能丟出例外，而且一定要回傳一個狀態
    for (State state : State.values()) {
      for (Goal goal : Goal.values()) {
        for (int bits = 0; bits < 16; bits++) {
          Inputs in = new Inputs((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, 0.0);
          Superstructure.nextState(state, goal, in);
        }
      }
    }
  }
}
```

- **每個測試只檢查一件事**，方法名稱就是這件事的說明（`intakeWaitsForArmToStow`：吸入要等手臂收起）。測試失敗時，看名稱就知道哪一條規則壞了
- 上面的 `EMPTY_ARM_UP` 這些常數是常用的感測器組合，讓每個測試更好讀。`Inputs` 的五個值依序是：有沒有物件、手臂在收起位置、手臂在發射角度、飛輪到速、進入這個狀態幾秒
- `shootingFinishesEvenIfButtonIsReleased` 用 `secondsInState` 模擬時間：不用真的等 0.5 秒
- `everyStateHandlesEveryGoal` 把所有狀態、所有 Goal、所有感測器組合（6 × 4 × 16 = 384 種）都跑一次。它不檢查結果對不對，只確保**沒有任何組合會讓程式出錯**

測試和 `Superstructure` 放在同一個 package（`first.robot.subsystems`），寫起來比較簡潔。

## 執行測試

=== "VS Code"

    按 ++ctrl+shift+p++，選 **WPILib: Test Robot Code**。結果會顯示在終端機，失敗的測試會列出名稱和出錯的行號。

=== "終端機"

    在專案資料夾執行：

    ```bash
    ./gradlew test
    ```

    全部通過時會顯示 `BUILD SUCCESSFUL`。詳細的報告在 `build/reports/tests/test/index.html`，用瀏覽器打開，裡面有每個測試失敗時預期和實際的值。

!!! warning "注意：部署不會自動跑測試"
    **WPILib: Build Robot Code**（`./gradlew build`）會執行測試，測試失敗就算編譯失敗。但 **WPILib: Deploy Robot Code** 不會先跑測試，測試沒通過的程式一樣會被部署上去。養成習慣：**部署前先 Build 一次**，特別是在比賽 pit 裡急著改程式的時候。

### 測試抓到錯誤的樣子

假設有人為了讓吸入反應快一點，把 `EMPTY` 的條件改成不等手臂：

```java
if (goal == Goal.INTAKE) yield State.INTAKING;   // 拿掉了 && in.armAtStow()
```

執行測試：

```text
SuperstructureTest > intakeWaitsForArmToStow() FAILED
    org.opentest4j.AssertionFailedError at SuperstructureTest.java:20

7 tests completed, 1 failed
```

打開測試報告，會看到這一行的訊息是 `expected: <EMPTY> but was: <INTAKING>`：手臂還在上面（`EMPTY_ARM_UP`）時，預期停在 `EMPTY`，結果卻變成 `INTAKING`。這個錯誤在機器人上很難發現：大部分時候手臂本來就在下面，只有偶爾在發射後馬上按吸入才會出現。

## 常見錯誤

!!! bug "常見錯誤：測試放在 `src/main/java`"
    測試檔案放錯資料夾，`import org.junit` 會找不到，或是測試被編譯進機器人程式。測試一定放在 `src/test/java/`。

!!! bug "常見錯誤：測試裡建立 `Superstructure` 物件"
    `new Superstructure(new Intake(), new Arm(), new Shooter())` 會建立真的馬達控制器物件，在電腦上執行需要廠商函式庫的電腦版和模擬環境，測試會變慢、甚至失敗。`nextState()` 是 `static`，直接呼叫 `Superstructure.nextState(...)` 就好，不用建立物件。

!!! bug "常見錯誤：改程式時順手改測試讓它通過"
    測試失敗時，先想清楚是**程式錯了**還是**規則改了**。規則真的改了（例如駕駛想要新的行為），才改測試；否則就是程式改壞了，要修的是程式。

## 小練習

1. 寫一個測試：在 `HOLDING` 時物件不見了，要回到 `EMPTY`。
2. 寫一個測試：在 `EJECTING` 放開按鈕時，如果物件還在，要回到 `HOLDING`。
3. 加了 `PREPARE` Goal（[上一頁的小練習](superstructure.md#小練習)）之後，要加哪些測試？

??? question "第 1、2 題參考答案"
    ```java
    @Test
    void losingGamePieceWhileHoldingMeansEmpty() {
      assertEquals(State.EMPTY, Superstructure.nextState(State.HOLDING, Goal.IDLE, EMPTY_ARM_DOWN));
    }

    @Test
    void releasingEjectWithGamePieceMeansHolding() {
      assertEquals(State.HOLDING, Superstructure.nextState(State.EJECTING, Goal.IDLE, HOLDING_ARM_DOWN));
    }
    ```

??? question "第 3 題參考答案"
    至少要檢查新的規則和「沒有改壞舊的規則」：

    - `HOLDING` + `PREPARE` → `PREPARING`
    - `PREPARING` + `PREPARE`，手臂和飛輪都到了 → 還是 `PREPARING`（不送料）
    - `PREPARING` + `SCORE`，手臂和飛輪都到了 → `SHOOTING`（舊的規則，應該還是通過）
    - `PREPARING` + `IDLE` → `HOLDING`
