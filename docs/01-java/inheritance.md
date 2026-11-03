# 繼承與介面

## 這頁學什麼

- 用 `extends` 繼承另一個類別
- `@Override` 覆寫方法
- 用 `interface` 定義「能做什麼」
- 看懂 WPILib 裡 `extends SubsystemBase` 這類寫法

## 觀念

### Inheritance（繼承）：在別人的基礎上擴充

WPILib 已經寫好了很多類別，你不用從零開始。**Inheritance（繼承）** 讓你的類別直接拿到另一個類別的所有功能，再加上自己的部分。

```java
class RobotPart {
    private final String name;

    RobotPart(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }

    void periodic() {
        // 預設什麼都不做
    }
}

class Arm extends RobotPart {
    private double angle = 0.0;

    Arm() {
        super("Arm");           // 呼叫父類別的建構子
    }

    @Override
    void periodic() {
        IO.println(getName() + " 角度：" + angle);
    }
}
```

- `class Arm extends RobotPart`：`Arm` 繼承 `RobotPart`。`RobotPart` 叫 **Superclass（父類別）**，`Arm` 叫 **Subclass（子類別）**
- `super("Arm")`：呼叫父類別的建構子，必須寫在建構子的第一行
- `Arm` 沒有自己寫 `getName()`，但可以直接用，因為繼承來了

### `@Override`：改寫父類別的方法

父類別的 `periodic()` 什麼都不做。`Arm` 想要每次都印出角度，就自己寫一個同名的方法，這叫 **Override（覆寫）**。

`@Override` 是 **Annotation（標註）**，告訴編譯器「我是故意要覆寫父類別的方法」。如果你拼錯了方法名稱（例如寫成 `periodc()`），編譯器會報錯，幫你抓到錯誤。**覆寫時一定要加 `@Override`。**

### 在 FRC 裡看到的繼承

第 5 章你會寫出這樣的程式：

```java
public class Intake extends SubsystemBase {
    @Override
    public void periodic() {
        // 每 20 ms 會被呼叫一次
    }
}
```

`SubsystemBase` 是 WPILib 寫好的類別，它已經處理好「向排程器註冊」等雜事。你只要繼承它，再覆寫 `periodic()` 放自己的邏輯。

### Interface（介面）：約定「能做什麼」

**Interface（介面）** 只規定「要有哪些方法」，不管怎麼做。類別用 `implements` 表示「我遵守這個約定」，就必須把這些方法寫出來。

用 REV 和 CTRE 的馬達來舉例。兩家的程式寫法不同，但都能「設定速度」：

```java
interface SimpleMotor {
    void set(double speed);
}

class RevMotor implements SimpleMotor {
    @Override
    public void set(double speed) {
        IO.println("REV 馬達設定為 " + speed);
    }
}

class CtreMotor implements SimpleMotor {
    @Override
    public void set(double speed) {
        IO.println("CTRE 馬達設定為 " + speed);
    }
}
```

好處是：寫 Intake 的人只需要知道「這是一個 `SimpleMotor`」，不用管是哪一家：

```java
class Intake {
    private final SimpleMotor motor;

    Intake(SimpleMotor motor) {
        this.motor = motor;
    }

    void run() {
        motor.set(0.8);
    }
}
```

`Intake` 可以搭配任何一種馬達，換廠商時 `Intake` 的程式完全不用改。這種「同一個型別、不同的實作」叫 **Polymorphism（多型）**。

### 繼承和介面的差別

| | `extends`（繼承） | `implements`（介面） |
| --- | --- | --- |
| 意思 | 「我**是**一種…」，並拿到它的程式碼 | 「我**能**做…」，自己寫出做法 |
| 數量 | 只能繼承**一個**類別 | 可以實作**很多個**介面 |
| FRC 例子 | `class Intake extends SubsystemBase` | `SubsystemBase` 本身實作了 `Subsystem` 介面 |

## 範例

```java title="MotorInterface.java"
interface SimpleMotor {
    void set(double speed);
}

class RevMotor implements SimpleMotor {
    private final int canId;

    RevMotor(int canId) {
        this.canId = canId;
    }

    @Override
    public void set(double speed) {
        IO.println("[REV #" + canId + "] " + speed);
    }
}

class CtreMotor implements SimpleMotor {
    private final int canId;

    CtreMotor(int canId) {
        this.canId = canId;
    }

    @Override
    public void set(double speed) {
        IO.println("[CTRE #" + canId + "] " + speed);
    }
}

class Intake {
    private final SimpleMotor motor;

    Intake(SimpleMotor motor) {
        this.motor = motor;
    }

    void run() {
        motor.set(0.8);
    }

    void stop() {
        motor.set(0.0);
    }
}

void main() {
    Intake revIntake = new Intake(new RevMotor(10));
    Intake ctreIntake = new Intake(new CtreMotor(20));

    revIntake.run();
    ctreIntake.run();
    revIntake.stop();
}
```

## 常見錯誤

!!! bug "常見錯誤：沒加 `@Override`，方法名稱又拼錯"
    寫成 `public void periodc()` 不會有任何錯誤，但 WPILib 永遠不會呼叫它，你的程式就「好像沒有執行」。加上 `@Override`，編譯器就會立刻告訴你哪裡錯了。

!!! bug "常見錯誤：實作介面時忘了 `public`"
    介面裡的方法都是 `public` 的，實作時也必須寫 `public`，否則編譯錯誤。

!!! bug "常見錯誤：子類別建構子沒呼叫 `super(...)`"
    父類別的建構子需要參數時，子類別的建構子第一行一定要用 `super(...)` 把參數傳上去。

## 小練習

1. 寫一個 `Shooter extends RobotPart`，覆寫 `periodic()`，印出「Shooter 運轉中」。
2. 在 `MotorInterface.java` 加入第三種馬達 `FakeMotor`，`set()` 時什麼都不印，只把速度存起來，再加一個 `getLastSpeed()` 方法。這種「假的」馬達在寫測試時很有用。
3. 想一想：為什麼 `Intake` 的建構子參數型別要寫 `SimpleMotor`，而不是 `RevMotor`？

??? question "第 3 題參考答案"
    寫 `SimpleMotor` 的話，任何實作 `SimpleMotor` 的馬達都能傳進來，`Intake` 不必知道是哪一家。如果寫死 `RevMotor`，換成 CTRE 馬達時就得修改 `Intake` 的程式。
