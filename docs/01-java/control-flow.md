# 條件與迴圈

## 這頁學什麼

- 用 `if` / `else` 依照條件做不同的事
- 比較運算子和邏輯運算子
- 用 `for`、`while` 重複執行
- 為什麼機器人程式幾乎不自己寫無窮迴圈

## 觀念

### `if`：依條件做不同的事

```java
if (條件) {
    // 條件成立時執行
} else {
    // 條件不成立時執行
}
```

條件是一個 `boolean`。例如：手臂角度超過 90 度就停下來。

```java
double armAngle = 95.0;

if (armAngle > 90.0) {
    IO.println("手臂到頂了，停止馬達");
} else {
    IO.println("繼續往上");
}
```

需要判斷很多種情況時，可以用 `else if` 串起來，程式會**由上往下**檢查，執行第一個成立的區塊：

```java
if (speed > 1.0) {
    speed = 1.0;
} else if (speed < -1.0) {
    speed = -1.0;
}
```

### 比較運算子

| 運算子 | 意思 |
| --- | --- |
| `==` | 等於 |
| `!=` | 不等於 |
| `>`、`<` | 大於、小於 |
| `>=`、`<=` | 大於等於、小於等於 |

### 邏輯運算子

| 運算子 | 意思 | 例子 |
| --- | --- | --- |
| `&&` | 而且（兩個都成立） | `hasGamePiece && atTarget` |
| <code>&#124;&#124;</code> | 或者（一個成立就好） | <code>atTopLimit &#124;&#124; atBottomLimit</code> |
| `!` | 不是（反過來） | `!hasGamePiece` |

```java
boolean hasGamePiece = true;
boolean shooterReady = false;

if (hasGamePiece && shooterReady) {
    IO.println("發射！");
}
```

### `for`：重複固定次數

```java
for (int i = 0; i < 4; i++) {
    IO.println("設定第 " + i + " 顆底盤馬達");
}
```

括號裡的三個部分：`int i = 0` 從 0 開始；`i < 4` 只要成立就繼續；`i++` 每跑完一次 `i` 加 1。所以會印出 0、1、2、3，共 4 次。

### `while`：條件成立就一直重複

```java
int battery = 100;
while (battery > 20) {
    battery = battery - 30;
    IO.println("電量剩下 " + battery);
}
```

### 為什麼機器人程式不寫無窮迴圈

初學者常想這樣寫：「一直讀搖桿，一直設定馬達」

```java
// ❌ 不要在機器人程式這樣寫
while (true) {
    double speed = readJoystick();
    setMotor(speed);
}
```

在機器人程式裡，**WPILib 已經幫你寫好了這個迴圈**。它大約每 20 毫秒會呼叫一次你寫的 `periodic()` 方法，你只要寫「這一次要做什麼」就好。

```mermaid
flowchart LR
    A["WPILib 的主迴圈"] --> B["呼叫你的 periodic()"]
    B --> C["處理 Driver Station 的資料<br/>更新 Command 等"]
    C -- "約 20 ms 後" --> A
```

如果你在 `periodic()` 裡自己寫了 `while (true)`，WPILib 的迴圈就卡住了：收不到 Driver Station 的停用指令，其他機構也不會更新。這非常危險，[第 4 章](../04-robot-basics/lifecycle.md) 會詳細說明。

## 範例

把馬達速度限制在 -1.0 到 1.0 之間，並在接近極限時印出警告：

```java title="ClampSpeed.java"
void main() {
    double[] requests = {0.5, 1.3, -2.0, 0.95};

    for (int i = 0; i < requests.length; i++) {
        double speed = requests[i];

        if (speed > 1.0) {
            speed = 1.0;
        } else if (speed < -1.0) {
            speed = -1.0;
        }

        if (Math.abs(speed) > 0.9) {
            IO.println("第 " + i + " 筆：" + speed + "（接近全速）");
        } else {
            IO.println("第 " + i + " 筆：" + speed);
        }
    }
}
```

`double[]` 是 **Array（陣列）**，一次存很多個 `double`。`requests[i]` 取出第 `i` 個（從 0 開始數），`requests.length` 是總共有幾個。

## 常見錯誤

!!! bug "常見錯誤：`=` 和 `==` 搞混"
    `=` 是**指定**（把值放進變數），`==` 是**比較**（兩邊是否相等）。`if (count = 3)` 在 Java 會編譯錯誤，看到錯誤訊息時記得檢查這裡。

!!! bug "常見錯誤：用 `==` 比較小數"
    感測器讀到的角度幾乎不可能**剛好**等於 `90.0`，可能是 `89.9997`。不要寫 `if (angle == 90.0)`，改成判斷誤差夠小：

    ```java
    if (Math.abs(angle - 90.0) < 1.0) {
        // 在 90 度 ± 1 度以內，視為到位
    }
    ```

!!! bug "常見錯誤：用 `==` 比較 `String`"
    比較兩段文字是否相同要用 `.equals()`：`name.equals("Bumblebee")`。用 `==` 有時候會得到錯誤的結果。

## 小練習

1. 寫一段程式：如果 `hasGamePiece` 是 `true` 就印出「Intake 停止」，否則印出「Intake 吸入中」。
2. 用 `for` 迴圈印出 CAN ID 1 到 8。
3. 有一個手臂，角度小於 0 或大於 120 都是危險的。寫一個條件判斷，危險時印出「停止！」。

??? question "第 1 題參考答案"
    ```java title="IntakeStatus.java"
    void main() {
        boolean hasGamePiece = true;

        if (hasGamePiece) {
            IO.println("Intake 停止");
        } else {
            IO.println("Intake 吸入中");
        }
    }
    ```

    把 `hasGamePiece` 改成 `false` 再執行一次，確認兩種情況都對。

??? question "第 2 題參考答案"
    ```java title="CanIds.java"
    void main() {
        for (int id = 1; id <= 8; id++) {
            IO.println("CAN ID " + id);
        }
    }
    ```

    注意條件是 `id <= 8`（小於**等於**），寫成 `id < 8` 只會印到 7。

??? question "第 3 題參考答案"
    ```java
    if (armAngle < 0 || armAngle > 120) {
        IO.println("停止！");
    }
    ```
