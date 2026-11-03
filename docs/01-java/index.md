# 第 1 章 Java 速成

FRC 的機器人程式用 **Java** 撰寫。這章教你寫 FRC 程式需要的 Java 基礎，範例都盡量用機器人相關的情境。

## 這章學什麼

- 用變數存資料、用條件和迴圈控制流程
- 把程式拆成方法和類別
- 看懂繼承、介面和 Lambda，這些是 WPILib 大量使用的寫法

**需要先讀**：不用。

!!! tip "可以跳過這章嗎？"
    如果你已經會寫 Java，或是學過其他程式語言（Python、C++ 等），而且看得懂下面這段程式在做什麼，可以只看每頁的「常見錯誤」，但 [Lambda](lambda.md) 那頁建議完整讀。

    ```java
    interface Motor {
        void set(double speed);
    }

    class Intake {
        private final Motor motor;

        Intake(Motor motor) {
            this.motor = motor;
        }

        Runnable runCommand() {
            return () -> motor.set(0.8);
        }
    }
    ```

## 怎麼練習

這章的範例都不需要機器人，在電腦上就能執行。選一種方式：

=== "線上執行（最簡單）"

    打開 [Java Playground](https://dev.java/playground/)，把範例貼上去，按 **Run** 就能執行。不用安裝任何東西。

=== "在自己的電腦執行"

    裝好 WPILib（[第 3 章](../03-setup/install-wpilib.md) 會教）之後，電腦上就有 Java 了。把範例存成 `.java` 檔，在終端機執行：

    ```bash
    java Hello.java
    ```

    WPILib 附的 Java 不一定在系統路徑上，可以用 WPILib VS Code 內建的終端機執行。

試試看你的第一個程式：

```java title="Hello.java"
void main() {
    IO.println("Hello, FRC!");
}
```

執行後應該會看到：

```text
Hello, FRC!
```

!!! info "關於 `void main()`"
    WPILib 2027 使用 **Java 25**。Java 25 可以用上面這種簡短的寫法直接開始寫程式，不用先寫一大堆樣板。舊的教材常看到的 `public static void main(String[] args)` 也還是可以用，意思一樣。機器人專案的程式架構由 WPILib 範本產生，到時候不需要自己寫 `main`。

## 本章內容

| 小節 | 內容 |
| --- | --- |
| [變數與型別](variables.md) | 用變數存資料、四種常用型別、常數 |
| [條件與迴圈](control-flow.md) | `if`、`for`、`while`，以及為什麼機器人程式不寫無窮迴圈 |
| [方法](methods.md) | 把程式碼包成可以重複使用的方法 |
| [類別與物件](classes.md) | 用類別描述一個機構 |
| [繼承與介面](inheritance.md) | `extends`、`implements`、`@Override` |
| [Lambda](lambda.md) | 把「要做的事」當成資料傳遞 |
| [延伸學習](further.md) | `enum`、`record`、集合，以及推薦教材 |
