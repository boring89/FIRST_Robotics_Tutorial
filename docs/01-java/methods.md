# 方法

## 這頁學什麼

- 定義和呼叫 **Method（方法）**
- 參數和回傳值
- 什麼時候該把程式碼抽成方法

## 觀念

### 把一段程式碼取個名字

上一頁限制馬達速度的程式，如果每個機構都要用，複製貼上很多次很麻煩，而且要修改時得每個地方都改。**Method（方法）** 讓你把一段程式碼包起來、取個名字，之後只要叫它的名字就能重複使用。

```java
double clamp(double value, double min, double max) {
    if (value > max) {
        return max;
    }
    if (value < min) {
        return min;
    }
    return value;
}
```

拆開來看：

```text
double     clamp   (double value, double min, double max)
───┬──     ──┬──    ─────────────────┬─────────────────
回傳型別    方法名稱            參數（Parameter）
```

- **Parameter（參數）**：呼叫方法時要給的資料
- **Return type（回傳型別）**：方法算完之後交回來的資料型別
- **`return`**：交回結果，並立刻結束這個方法

呼叫方法：

```java
double speed = clamp(1.3, -1.0, 1.0);   // speed 是 1.0
```

### 不回傳東西的方法：`void`

有些方法只負責做事，不用交回結果，回傳型別就寫 `void`：

```java
void printStatus(String name, double speed) {
    IO.println(name + " 速度：" + speed);
}
```

### 什麼時候該寫成方法

- **同樣的程式碼出現兩次以上**：抽成方法，之後只要改一個地方
- **一段程式碼在做一件有名字的事**：例如「套用死區」、「換算成公尺」，取個好名字能讓程式更好讀
- **方法太長**：一個方法最好只做一件事。超過一個螢幕就考慮拆開

## 範例

**Deadband（死區）**：搖桿放開時讀到的值通常不是剛好 0，所以小於某個值就當作 0。這在 FRC 程式裡非常常見：

```java title="Deadband.java"
double applyDeadband(double value, double deadband) {
    if (Math.abs(value) < deadband) {
        return 0.0;
    }
    return value;
}

double inchesToMeters(double inches) {
    return inches * 0.0254;
}

void main() {
    IO.println(applyDeadband(0.03, 0.05));   // 0.0
    IO.println(applyDeadband(0.60, 0.05));   // 0.6
    IO.println(applyDeadband(-0.8, 0.05));   // -0.8

    IO.println(inchesToMeters(4.0) + " 公尺");
}
```

!!! tip "小技巧：WPILib 已經有很多現成的方法"
    死區、限制範圍這種常用功能，WPILib 都有現成的（例如 `MathUtil.applyDeadband()`、`MathUtil.clamp()`），寫機器人程式時直接用就好。這裡自己寫一次，是為了理解方法怎麼運作。

## 常見錯誤

!!! bug "常見錯誤：忘記 `return`"
    回傳型別不是 `void` 的方法，**每一條路徑**都必須 `return` 一個值，否則編譯錯誤：

    ```java
    double sign(double x) {
        if (x > 0) {
            return 1.0;
        }
        // ❌ x <= 0 時沒有 return
    }
    ```

!!! bug "常見錯誤：參數順序弄錯"
    `clamp(value, min, max)` 寫成 `clamp(min, max, value)` 不會有編譯錯誤，因為型別都是 `double`，但結果會錯。呼叫方法時，對照一下參數的意思。

!!! bug "常見錯誤：以為方法裡的變數外面也看得到"
    在方法裡宣告的變數，方法結束後就消失了，方法外面看不到它。需要結果就用 `return` 交回來。

## 小練習

1. 寫一個方法 `double degreesToRadians(double degrees)`，把角度換算成弧度（弧度 = 角度 × π ÷ 180）。
2. 寫一個方法 `boolean isArmSafe(double angle)`，角度在 0 到 120 之間回傳 `true`，否則回傳 `false`。
3. 改寫上一頁的 `ClampSpeed.java`，把限制速度的部分改成呼叫 `clamp` 方法。

??? question "第 1 題參考答案"
    ```java title="DegreesToRadians.java"
    double degreesToRadians(double degrees) {
        return degrees * Math.PI / 180;
    }

    void main() {
        IO.println(degreesToRadians(180));   // 3.14159…（π）
        IO.println(degreesToRadians(90));    // 1.5707…（π/2）
    }
    ```

    Java 其實也有內建的 `Math.toRadians()`，結果一樣。

??? question "第 2 題參考答案"
    ```java
    boolean isArmSafe(double angle) {
        return angle >= 0 && angle <= 120;
    }
    ```

    條件本身就是 `boolean`，可以直接 `return`，不需要寫 `if (...) return true; else return false;`。

??? question "第 3 題參考答案"
    ```java title="ClampSpeedWithMethod.java"
    double clamp(double value, double min, double max) {
        if (value > max) {
            return max;
        }
        if (value < min) {
            return min;
        }
        return value;
    }

    void main() {
        double[] requests = {0.5, 1.3, -2.0, 0.95};

        for (int i = 0; i < requests.length; i++) {
            double speed = clamp(requests[i], -1.0, 1.0);

            if (Math.abs(speed) > 0.9) {
                IO.println("第 " + i + " 筆：" + speed + "（接近全速）");
            } else {
                IO.println("第 " + i + " 筆：" + speed);
            }
        }
    }
    ```

    原本的 `if / else if` 變成一行 `clamp(...)`，迴圈裡的程式短很多，也更容易看出它在做什麼。
