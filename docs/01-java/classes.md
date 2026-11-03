# 類別與物件

## 這頁學什麼

- **Class（類別）** 和 **Object（物件）** 的關係
- 欄位、建構子、方法
- `private`、`public` 與封裝
- 為什麼 FRC 程式用類別來代表機構

## 觀念

### 類別是設計圖，物件是做出來的東西

機器人上有兩個 Intake，構造一樣，只是接的馬達不同。我們可以先寫一張「Intake 的設計圖」，再照著設計圖做出兩個 Intake。

- **Class（類別）**：設計圖，描述這種東西**有什麼資料**、**能做什麼事**
- **Object（物件）**：照設計圖做出來的實體，也叫 **Instance（實例）**

```java
class Intake {
    // 欄位 (Field)：每個 Intake 自己的資料
    int canId;
    double speed;

    // 方法 (Method)：Intake 能做的事
    void run() {
        speed = 0.8;
        IO.println("Intake " + canId + " 吸入，速度 " + speed);
    }

    void stop() {
        speed = 0.0;
        IO.println("Intake " + canId + " 停止");
    }
}
```

用 `new` 照設計圖做出物件，用 `.` 存取它的欄位和方法：

```java
Intake frontIntake = new Intake();
frontIntake.canId = 10;
frontIntake.run();      // Intake 10 吸入，速度 0.8
```

### Constructor（建構子）

上面每次建立 Intake 都要另外設定 `canId`，很容易忘記。**Constructor（建構子）** 是建立物件時**一定會執行**的特殊方法，可以強制呼叫的人一開始就給好必要的資料：

```java
class Intake {
    int canId;

    Intake(int canId) {          // 建構子：名字和類別相同，沒有回傳型別
        this.canId = canId;
    }
}

Intake frontIntake = new Intake(10);   // 建立時就要給 CAN ID
```

`this.canId` 指的是**這個物件的欄位**，`canId` 指的是建構子的參數。兩個名字一樣時，要用 `this.` 區分。

### 封裝：`private` 和 `public`

如果誰都能直接改 `speed`，有人可能寫 `intake.speed = 5.0`，超出馬達能接受的範圍。把欄位設成 **`private`（私有）**，只有類別自己能改；外面要透過 **`public`（公開）** 的方法操作，方法裡就能檢查資料對不對。

這種「把資料藏起來，只開放安全的操作方式」的做法叫 **Encapsulation（封裝）**。

```java
class Intake {
    private final int canId;
    private double speed = 0.0;

    public Intake(int canId) {
        this.canId = canId;
    }

    public void setSpeed(double speed) {
        // 外面不能直接改 speed，一定要經過這裡的檢查
        this.speed = Math.max(-1.0, Math.min(1.0, speed));
    }

    public double getSpeed() {
        return speed;
    }
}
```

`canId` 加上了 `final`：建立之後就不能再改，因為馬達的 CAN ID 在執行中不會變。

### 為什麼 FRC 用類別代表機構

FRC 程式的組織方式就是**一個機構一個類別**：`Drivetrain`（底盤）、`Intake`、`Arm`（手臂）、`Shooter`（發射器）。每個類別管好自己的馬達和感測器，對外只提供「吸入」、「停止」、「轉到某個角度」這類操作。[第 5 章](../05-commands/subsystem.md) 的 **Subsystem（子系統）** 就是這個概念。

## 範例

```java title="IntakeDemo.java"
class Intake {
    private final int canId;
    private double speed = 0.0;
    private boolean hasGamePiece = false;

    public Intake(int canId) {
        this.canId = canId;
    }

    public void run() {
        if (hasGamePiece) {
            IO.println("Intake " + canId + "：已經有東西了，不吸");
            return;
        }
        speed = 0.8;
        IO.println("Intake " + canId + "：吸入中");
    }

    public void stop() {
        speed = 0.0;
        IO.println("Intake " + canId + "：停止");
    }

    public void sensorTriggered() {
        hasGamePiece = true;
        stop();
    }

    public double getSpeed() {
        return speed;
    }
}

void main() {
    Intake front = new Intake(10);
    Intake back = new Intake(11);

    front.run();
    back.run();
    front.sensorTriggered();   // 前 Intake 拿到東西，自動停止
    front.run();               // 已經有東西了，不會再吸

    IO.println("前：" + front.getSpeed() + "，後：" + back.getSpeed());
}
```

`front` 和 `back` 是同一張設計圖做出來的兩個物件，各自有自己的 `speed` 和 `hasGamePiece`，互不影響。

## 常見錯誤

!!! bug "常見錯誤：忘記 `new`"
    `Intake front;` 只是宣告了一個「可以放 Intake 的盒子」，裡面是空的 (`null`)。還沒 `new` 就呼叫 `front.run()`，會出現 **`NullPointerException`**，這是 FRC 程式最常見的當機原因之一。

!!! bug "常見錯誤：建構子裡忘了 `this.`"
    ```java
    public Intake(int canId) {
        canId = canId;   // ❌ 把參數指定給參數自己，欄位沒被設定
    }
    ```

!!! bug "常見錯誤：同一個裝置 `new` 兩次"
    在 FRC 程式裡，一個實體的馬達只能對應**一個**物件。在兩個地方各自 `new` 了同一個 CAN ID 的馬達，會造成衝突或錯誤。需要共用時，把同一個物件傳過去。

## 小練習

1. 寫一個 `Shooter` 類別：建構子接收 CAN ID，有 `spinUp()`（速度設為 1.0）、`stop()` 兩個方法，和一個 `isReady()` 方法，速度大於 0.9 時回傳 `true`。
2. 在 `main` 裡建立一個 `Shooter`，依序呼叫 `spinUp()`、印出 `isReady()`、呼叫 `stop()`、再印出 `isReady()`。
3. 想一想：`Shooter` 的速度欄位為什麼應該是 `private`？

??? question "第 3 題參考答案"
    如果速度是 `public`，任何地方的程式都能直接改它，可能設成超出範圍的值，或是在不該發射的時候改動它。設成 `private`，所有修改都必須經過 `Shooter` 自己的方法，檢查和限制只要寫在一個地方。
