# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。等你開始寫機器人程式、在別人的程式碼裡看到這些寫法時，再回來看。

## 推薦教材

- [dev.java Learn](https://dev.java/learn/)：Java 官方的學習網站，從基礎到進階都有
- [Java Playground](https://dev.java/playground/)：線上執行 Java，不用安裝

## 進階語法

### `enum`：列舉

一個變數只會是**幾種固定值的其中一種**時，用 `enum`。例如機器人的狀態：

```java title="EnumDemo.java"
enum ArmPosition {
    STOWED,
    INTAKE,
    SCORE_HIGH
}

double targetAngle(ArmPosition position) {
    return switch (position) {
        case STOWED -> 0.0;
        case INTAKE -> 15.0;
        case SCORE_HIGH -> 110.0;
    };
}

void main() {
    ArmPosition pos = ArmPosition.SCORE_HIGH;
    IO.println(pos + " 的角度：" + targetAngle(pos));
}
```

比起用 `int` 代表狀態（0 是收起、1 是吸入…），`enum` 不會打錯，而且 `switch` 漏寫某個情況時編譯器會提醒你。

### `record`：簡單的資料類別

只是用來裝資料的類別，可以用 `record` 一行寫完：

```java
record ShooterSetpoint(double rpm, double hoodAngle) {}

ShooterSetpoint close = new ShooterSetpoint(3000, 20);
IO.println(close.rpm());     // 3000.0
```

`record` 會自動產生建構子、讀取方法、`equals` 等。WPILib 裡也有很多類似的「不可變資料」類別，例如 `Pose2d`（位置與朝向）。

### 集合：`ArrayList` 和 `List`

陣列的長度建立後就不能變。需要能增減的清單時，用 `ArrayList`：

```java
List<String> log = new ArrayList<>();
log.add("Intake 開始");
log.add("拿到物件");
IO.println(log.size());      // 2
```

`<String>` 是 **Generics（泛型）**，表示這個清單裡只能放 `String`。

### Exception（例外）

程式執行時發生錯誤（例如 `NullPointerException`），Java 會丟出 **Exception（例外）**。在機器人程式裡，沒有被處理的例外會讓程式當掉重啟，機器人在那幾秒內完全不能動。第 11 章 [常見錯誤排查](../11-practice/troubleshooting.md) 會教你怎麼從錯誤訊息找到出問題的那一行。

### `static`

本章大部分的方法都屬於某個物件，要先 `new` 才能用。加上 `static` 的方法或欄位屬於**類別本身**，不需要物件就能用，例如 `Math.abs()`、`MathUtil.applyDeadband()`。FRC 專案裡的 `Constants` 類別也常用 `static final` 存放常數。

## 想一想

??? question "為什麼 WPILib 的 `Pose2d` 等類別都設計成不可變 (Immutable)？"
    不可變的物件建立之後內容就不會改變。機器人程式裡，同一個位置資料可能同時被底盤、視覺、自動階段的程式拿去用。如果其中一個地方偷偷改了它，其他地方會拿到錯誤的資料，而且很難查出是誰改的。不可變的物件就沒有這個問題：要「改」只能產生一個新的物件。
