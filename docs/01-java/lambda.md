# Lambda

## 這頁學什麼

- 看懂、寫出 **Lambda expression（Lambda 運算式）**
- **Method reference（方法參考）** 的寫法
- `Runnable`、`DoubleSupplier`、`BooleanSupplier` 這幾個 FRC 最常用的介面
- 分清楚「傳一個值」和「傳一個取得值的方法」

!!! tip "這頁很重要"
    Command-based 程式裡到處都是 Lambda。這頁如果看不懂，第 5 章會很辛苦。建議每個範例都自己執行一次。

## 觀念

### 把「要做的事」當成資料

到目前為止，我們傳給方法的都是**資料**：數字、文字、物件。但有時候我們想傳的是**一件事**：

> 「按下 A 鍵的時候，**讓 Intake 吸入**。」

「讓 Intake 吸入」不是一個數字，而是一段要執行的程式碼，而且不是現在執行，是**按下按鍵的時候**才執行。

**Lambda** 就是把一小段程式碼包起來，當成資料傳來傳去的寫法：

```java
() -> intake.run()
```

讀作：「一件不需要參數 (`()`)、要做的事是 `intake.run()` 的事情」。寫下這行的時候 `intake.run()` **不會執行**，要等到有人「呼叫」這個 Lambda 才會執行。

### Lambda 的寫法

```text
(參數) -> 要做的事
```

| 寫法 | 意思 |
| --- | --- |
| `() -> intake.run()` | 沒有參數，執行 `intake.run()` |
| `() -> 0.5` | 沒有參數，回傳 `0.5` |
| `x -> x * 2` | 一個參數 `x`，回傳 `x * 2` |
| `(a, b) -> a + b` | 兩個參數，回傳相加的結果 |
| `() -> { a(); b(); }` | 要做好幾件事時用大括號包起來 |

### Lambda 的型別：Functional interface

Lambda 也需要型別。它的型別是一個**只有一個方法的介面**，叫 **Functional interface（函式型介面）**。Java 內建了很多，FRC 最常用的是這幾個：

| 介面 | 方法 | 意思 | FRC 用途 |
| --- | --- | --- | --- |
| `Runnable` | `void run()` | 做一件事 | Command 要執行的動作 |
| `DoubleSupplier` | `double getAsDouble()` | 提供一個小數 | 搖桿數值、感測器讀值 |
| `BooleanSupplier` | `boolean getAsBoolean()` | 提供 `true` / `false` | 感測器是否觸發、是否到位 |
| `Supplier<T>` | `T get()` | 提供一個物件 | 提供目前的位置等 |

```java
Runnable sayHi = () -> IO.println("Hi");
sayHi.run();                  // 這時候才印出 Hi

DoubleSupplier half = () -> 0.5;
double value = half.getAsDouble();   // 0.5
```

### Method reference（方法參考）

如果 Lambda 只是呼叫一個現成的方法，可以寫得更短：

```java
Runnable a = () -> intake.stop();    // Lambda
Runnable b = intake::stop;           // Method reference，意思完全一樣
```

`intake::stop` 讀作：「`intake` 的 `stop` 方法」。注意**沒有括號**，因為不是現在呼叫它。

### 在 FRC 裡長什麼樣子

第 5 章你會看到這樣的程式（先看懂結構就好）：

```java
controller.a().whileTrue(intake.run(() -> intake.setSpeed(0.8)));
```

由內往外讀：

1. `() -> intake.setSpeed(0.8)`：一件事——把 Intake 速度設為 0.8
2. `intake.run(...)`：把這件事包成一個 **Command（指令）**，Command 執行期間會一直重複做這件事
3. `controller.a().whileTrue(...)`：A 鍵按住的時候，執行這個 Command

如果沒有 Lambda，就沒辦法把「設定速度」這件事交給按鍵，讓它在**之後**、**每次按下時**才執行。

## 範例

用純 Java 模擬「按鍵綁定」，看看 Lambda 什麼時候被執行：

```java title="ButtonDemo.java"
class Button {
    private Runnable action = () -> {};

    void onPress(Runnable action) {
        this.action = action;
    }

    void press() {
        IO.println("（按下按鍵）");
        action.run();
    }
}

class Intake {
    void run() {
        IO.println("Intake 吸入");
    }

    void stop() {
        IO.println("Intake 停止");
    }
}

void main() {
    Intake intake = new Intake();
    Button a = new Button();
    Button b = new Button();

    a.onPress(() -> intake.run());   // 綁定：現在還不會執行
    b.onPress(intake::stop);         // 用 method reference 綁定

    IO.println("綁定完成");
    a.press();
    b.press();
    a.press();
}
```

執行結果：

```text
綁定完成
（按下按鍵）
Intake 吸入
（按下按鍵）
Intake 停止
（按下按鍵）
Intake 吸入
```

「綁定完成」先印出來，證明綁定的時候 Lambda 並沒有執行。

## 常見錯誤

這是 FRC 新手**最常犯**的錯誤之一，一定要看懂。

!!! bug "常見錯誤：傳了「值」，而不是「取得值的方法」"
    假設底盤有一個方法，接收一個 `DoubleSupplier` 當作速度來源，執行時會一直呼叫它取得最新的搖桿值：

    ```java
    // ✅ 正確：傳入 Lambda，每次執行都會重新讀搖桿
    drive.driveWith(() -> controller.getLeftY());
    ```

    如果你有另一個版本是接收 `double`：

    ```java
    // ❌ 錯誤：getLeftY() 現在就被呼叫了，只讀一次
    drive.driveWith(controller.getLeftY());
    ```

    第二種寫法在**程式啟動的那一刻**就讀了搖桿（通常是 0），之後永遠用這個值。結果就是：程式沒有錯誤，但推搖桿機器人完全不動。

    下面的範例可以實際看到差別：

    ```java title="SupplierDemo.java"
    double joystick = 0.0;   // 模擬搖桿目前的值

    void printEachLoop(DoubleSupplier source) {
        for (int i = 0; i < 3; i++) {
            joystick = joystick + 0.25;             // 模擬駕駛推搖桿
            IO.println("讀到：" + source.getAsDouble());
        }
    }

    void main() {
        IO.println("傳 Lambda：");
        printEachLoop(() -> joystick);              // 每次都重新讀

        joystick = 0.0;
        double snapshot = joystick;
        IO.println("傳固定的值：");
        printEachLoop(() -> snapshot);              // 永遠是同一個值
    }
    ```

!!! bug "常見錯誤：Method reference 多寫了括號"
    `intake::stop()` 是語法錯誤，要寫 `intake::stop`。

!!! bug "常見錯誤：在 Lambda 裡修改外面的區域變數"
    Lambda 裡可以**讀**外面的區域變數，但不能**修改**它，而且那個變數之後也不能再被改（Java 稱為 *effectively final*）。需要在 Lambda 裡改的狀態，放在物件的欄位裡。

## 小練習

1. 宣告一個 `Runnable`，執行時印出「Shooter 發射」，然後呼叫它兩次。
2. 宣告一個 `BooleanSupplier isTooHot`，當變數 `temperature` 大於 80 時回傳 `true`。（提示：`temperature` 要是欄位，不能是 `main` 裡的區域變數。）
3. 在 `ButtonDemo.java` 加一個按鍵 `x`，按下時**同時**讓 Intake 停止並印出「全部停止」。
4. 把下面的 Lambda 改成 method reference：`() -> shooter.spinUp()`。

??? question "第 1 題參考答案"
    ```java title="RunnableDemo.java"
    void main() {
        Runnable fire = () -> IO.println("Shooter 發射");
        fire.run();
        fire.run();
    }
    ```

??? question "第 2 題參考答案"
    ```java title="TooHotDemo.java"
    double temperature = 25.0;   // 欄位：Lambda 執行時讀到的是最新的值

    void main() {
        BooleanSupplier isTooHot = () -> temperature > 80;

        IO.println(isTooHot.getAsBoolean());   // false
        temperature = 85.0;
        IO.println(isTooHot.getAsBoolean());   // true
    }
    ```

    `temperature` 改變之後，同一個 `isTooHot` 回傳的結果也跟著變，因為 Lambda 每次被呼叫時才去讀 `temperature`。

??? question "第 3、4 題參考答案"
    第 3 題：

    ```java
    x.onPress(() -> {
        intake.stop();
        IO.println("全部停止");
    });
    ```

    第 4 題：`shooter::spinUp`
