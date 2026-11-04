# 變數與型別

## 這頁學什麼

- 宣告變數、修改變數的值
- 四種最常用的型別：`int`、`double`、`boolean`、`String`
- 用 `final` 宣告常數
- 避開整數除法的陷阱

## 觀念

### 變數是貼了標籤的盒子

**Variable（變數）** 用來存放資料。每個變數有一個名字，還有一個 **Type（型別）**，決定盒子裡能放什麼。

```java
int canId = 5;
```

這行的意思是：建立一個叫 `canId` 的盒子，裡面只能放整數 (`int`)，現在放進 `5`。

變數的值可以改：

```java
int canId = 5;
canId = 7;        // 改成 7，前面不用再寫 int
```

`//` 後面是 **Comment（註解）**，給人看的說明，程式執行時會忽略。

### 四種常用型別

| 型別 | 存放什麼 | 例子 |
| --- | --- | --- |
| `int` | 整數 | CAN ID、按鈕編號、計數 |
| `double` | 小數 | 馬達速度、角度、距離 |
| `boolean` | `true` 或 `false` | 感測器有沒有觸發、機構是否到位 |
| `String` | 文字 | 名稱、要印出的訊息 |

```java
int intakeCanId = 10;
double intakeSpeed = 0.8;          // 80% 的速度
boolean hasGamePiece = false;      // 還沒拿到遊戲物件
String robotName = "Bumblebee";    // 文字要用雙引號
```

### 計算

```java
double wheelDiameter = 4.0;                     // 輪子直徑，英寸
double circumference = Math.PI * wheelDiameter; // 圓周長
double distance = circumference * 3;            // 輪子轉 3 圈走的距離
```

常用的運算子：`+` 加、`-` 減、`*` 乘、`/` 除、`%` 取餘數。`Math` 裡有很多現成的數學工具，例如 `Math.PI`、`Math.abs()`（絕對值）、`Math.sqrt()`（平方根）。

### 常數：不會變的值

有些值一旦決定就不該改，例如馬達的 CAN ID、機構的齒輪比。在前面加上 `final`，它就變成 **Constant（常數）**，之後如果不小心改到它，程式會無法編譯。

```java
final int INTAKE_CAN_ID = 10;
final double DRIVE_GEAR_RATIO = 6.75;

INTAKE_CAN_ID = 11;   // 編譯錯誤！final 的值不能改
```

**命名慣例**：

- 一般變數用 **camelCase**（第一個字小寫，之後每個字開頭大寫）：`intakeSpeed`、`hasGamePiece`
- 常數用 **UPPER_SNAKE_CASE**（全大寫，用底線分隔）：`INTAKE_CAN_ID`

!!! info "2027 新改動"
    WPILib 2027 把函式庫裡的常數和列舉值也統一改成全大寫，和 Java 的慣例一致。

## 範例

計算機器人輪子轉一圈前進多少公尺：

```java title="WheelDistance.java"
void main() {
    final double WHEEL_DIAMETER_METERS = 0.1016;  // 4 英寸輪
    final double GEAR_RATIO = 6.75;               // 馬達轉 6.75 圈，輪子轉 1 圈

    double wheelCircumference = Math.PI * WHEEL_DIAMETER_METERS;
    double metersPerMotorRotation = wheelCircumference / GEAR_RATIO;

    IO.println("輪子周長：" + wheelCircumference + " 公尺");
    IO.println("馬達每轉一圈前進：" + metersPerMotorRotation + " 公尺");
}
```

`"文字" + 數字` 會把數字接到文字後面，變成一串新的文字。

## 常見錯誤

!!! bug "常見錯誤：整數除法"
    兩個 `int` 相除，結果還是 `int`，**小數部分會直接被丟掉**：

    ```java
    double ratio = 1 / 12;     // 結果是 0.0，不是 0.0833…！
    double ratio2 = 1.0 / 12;  // 0.08333…，正確
    ```

    `1 / 12` 是兩個整數相除，先算出 `0`，再存進 `double` 變成 `0.0`。寫齒輪比、換算係數時特別容易犯這個錯，機構會完全不動或動得很奇怪。**只要有小數，就至少把一個數字寫成 `1.0` 這種形式。**

!!! bug "常見錯誤：拼錯大小寫"
    Java 分大小寫。`intakeSpeed` 和 `IntakeSpeed` 是兩個不同的變數。

!!! bug "常見錯誤：忘記分號"
    每個敘述最後都要加 `;`。編譯器的錯誤訊息常常指在**下一行**，看到奇怪的錯誤先檢查上一行有沒有分號。

## 小練習

1. 宣告一個常數 `SHOOTER_CAN_ID`，值是 `15`，再宣告一個變數 `shooterSpeed`，值是 `0.65`，把兩個值都印出來。
2. 輪子直徑 6 英寸，換算成公尺（1 英寸 = 0.0254 公尺），再算出輪子轉一圈走幾公尺。
3. 不執行程式，猜猜看下面印出什麼，再執行確認：

    ```java
    IO.println(7 / 2);
    IO.println(7 / 2.0);
    IO.println(7 % 2);
    ```

??? question "第 1 題參考答案"
    ```java title="ShooterConstants.java"
    void main() {
        final int SHOOTER_CAN_ID = 15;
        double shooterSpeed = 0.65;

        IO.println("Shooter CAN ID：" + SHOOTER_CAN_ID);
        IO.println("Shooter 速度：" + shooterSpeed);
    }
    ```

??? question "第 2 題參考答案"
    ```java title="SixInchWheel.java"
    void main() {
        final double INCH_TO_METERS = 0.0254;
        double diameterMeters = 6 * INCH_TO_METERS;            // 0.1524 公尺
        double metersPerRotation = Math.PI * diameterMeters;   // 約 0.479 公尺

        IO.println("直徑：" + diameterMeters + " 公尺");
        IO.println("轉一圈：" + metersPerRotation + " 公尺");
    }
    ```

    6 英寸 = 0.1524 公尺，轉一圈大約走 0.479 公尺。

??? question "第 3 題答案"
    `3`、`3.5`、`1`。第一行是整數除法，小數被丟掉；第二行有一個小數，結果是小數；第三行 `%` 是取餘數，7 除以 2 餘 1。
