# 寫作規範

這頁給**撰寫和維護這份教學的人**看，讓每一章的風格保持一致。

## 章節結構

每一章是一個資料夾，裡面固定有：

| 檔案 | 用途 |
| --- | --- |
| `index.md` | 章節首頁：這章學什麼、適合誰、需要先讀哪幾章 |
| 各小節 `.md` | 正文 |
| `further.md` | **延伸學習**：進階主題、外部資源，第一次讀可以跳過 |

每個小節頁面依序包含：

1. **這頁學什麼**：2～4 點，讀完能做到什麼
2. **觀念**：先講為什麼，再講怎麼做
3. **範例**：程式碼，或是硬體章節的「動手看看」
4. **常見錯誤**：新手最常踩的坑
5. **小練習**：讀者可以自己試的題目

## 專有名詞

- 第一次出現時寫成 **English（中文）**，例如 Subsystem（子系統）。之後直接用英文。
- 程式裡的名稱（類別、方法）一律用 `程式碼格式`，不翻譯，例如 `Mechanism`。
- 新的名詞要同步加到 [名詞對照表](glossary.md)。

## 提示框

| 寫法 | 用途 |
| --- | --- |
| `!!! tip "小技巧"` | 好用但不是必要的做法 |
| `!!! warning "注意"` | 會弄壞東西或造成危險的事 |
| `!!! bug "常見錯誤"` | 新手常犯的錯 |
| `!!! info "2027 新改動"` | 和 2026 以前不一樣的地方 |
| `??? question "想一想"` | 可收合的思考題，答案放在裡面 |

## REV / CTRE 分頁

需要分廠商寫的內容，用分頁並排。分頁標題固定寫 `REV` 和 `CTRE`，讀者切換一次後，整個網站的分頁都會同步切換。

```markdown
=== "REV"

    REV 的內容

=== "CTRE"

    CTRE 的內容
```

## 照片

硬體章節的照片放在 `docs/assets/photos/`，用 JPG。

- **自己拍，或用開放授權的圖**：網路圖片只能用 CC BY、CC BY-SA、CC0、公有領域的，例如 [WPILib 文件](https://github.com/wpilibsuite/frc-docs)（CC BY 4.0）和 [Wikimedia Commons](https://commons.wikimedia.org/)。廠商網站的圖、CC BY-NC-ND（例如 CTRE 的文件）都不能用。**附上來源不代表可以用**，要先確認授權
- **圖片來源**：用了網路圖片，要在那一章「延伸學習」最後的**圖片來源**表格寫出原始檔案、作者、授權、做了什麼修改。之後換成自己拍的照片時，記得把那一列刪掉
- **人物**：照片裡有隊員的臉，要先問過本人（未成年要家長同意）；拍硬體時盡量不要拍到人
- **格式**：JPG、橫式 3:2、長邊 1600 px 左右、一張不超過 400 KB
- **拍法**：背景乾淨、光線充足，主角放在畫面中間，說明文字提到的東西都要看得到

在頁面裡放照片的寫法：

```markdown
<figure markdown="span">
  ![裝在機器人上的 Limelight](../assets/photos/limelight-mounted.jpg){ width="1600" height="1067" }
  <figcaption>照片下方的說明</figcaption>
</figure>
```

### 還沒有照片的地方

這些地方找不到開放授權的圖，需要隊上自己拍：

| 頁面 | 放在哪一段 | 要拍什麼 |
| --- | --- | --- |
| [第 2 章 FRC 基本元件認識](../02-hardware/index.md) | 控制系統全貌 | 電控板俯視全景：電池、主斷路器、Systemcore、Motioncore、馬達控制器 |
| [電源與 Motioncore](../02-hardware/power.md) | Motioncore | 接好線的 Motioncore，看得到 Device port |
| [感測器](../02-hardware/sensors.md) | Encoder（REV 分頁） | REV Through Bore Encoder |
| [Driver Station 與手把](../02-hardware/driver-station.md) | Driver Station | 駕駛站：筆電和手把 |
| [韌體與 CAN 設定](../03-setup/firmware-can.md) | 更新韌體、設定 CAN ID | 貼了 CAN ID 標籤的馬達控制器 |
| [第 4 章 機器人程式基礎](../04-robot-basics/index.md) | 需要的硬體 | 測試板：Systemcore 和一組馬達固定在板子上 |
| [編碼器](../06-motors-sensors/encoders.md) | Absolute encoder | 裝在手臂轉軸上的絕對式編碼器 |
| [陀螺儀與 IMU](../06-motors-sensors/gyro.md) | 內建 IMU 的安裝方向 | 裝在機器人上的 Systemcore，看得出安裝方向 |
| [數位感測器](../06-motors-sensors/digital-sensors.md) | 用極限開關保護機構 | 裝在升降機構或手臂底部的極限開關 |
| [差速底盤](../07-drivetrain/differential.md) | 差速底盤怎麼動 | 差速底盤翻過來的底部：左右兩邊的輪子和馬達 |
| [第 10 章 視覺](../10-vision/index.md) | 視覺的硬體 | Limelight 相機 |
| [Limelight 與 PhotonVision](../10-vision/limelight-photonvision.md) | 設定 Limelight | 裝在機器人上的 Limelight，看得出安裝位置和角度 |

## 程式碼範例

- 可以直接執行的完整範例，在程式碼區塊加上檔名標題：`` ```java title="IntakeDemo.java" ``
- 機器人專案裡的完整檔案，標題寫專案內的路徑：`` ```java title="src/main/java/first/robot/Robot.java" ``。JUnit 測試的標題寫 `src/test/java/...`，檢查時會真的執行測試，不只是編譯
- 只是片段、不能單獨執行的程式碼，不要加標題
- 機器人程式使用 WPILib 範本的風格：縮排 2 個空格、用 `System.out.println()`
- 送出前用 WPILib 附的 JDK 檢查所有完整範例（安裝資料夾見 [安裝 WPILib](../03-setup/install-wpilib.md)）。最後一個參數是用來編譯機器人程式的專案（用 Command v2 Robot 範本建立），要先加好 REVLib、Phoenix 6、PathplannerLib、LimelightLib，並在有網路時編譯過一次：

```bash
python3 tools/check_java.py docs <WPILib 安裝資料夾>/jdk/bin/java /tmp/java-check <機器人專案>
```

- 範例用到其他頁面的類別（例如 `RobotContainer` 用到 `Intake`）時，把那些類別的 REV 版本放在 `tools/robot-fixtures/<章節資料夾>/common/`。某一頁需要不同版本時，放在 `tools/robot-fixtures/<章節資料夾>/<頁面檔名>/`，會蓋過 `common/` 的同名檔案。fixture 也可以放 `build.gradle` 和 `vendordeps/`，例如第 18 章帶了自己的 AdvantageKit vendordep 和修改過的 `build.gradle`，第一次檢查時要在有網路的情況下編譯，讓 Gradle 下載 AdvantageKit

## 自動檢查

每個 Pull request 都會在 GitHub Actions 上自動執行兩項檢查（`.github/workflows/check.yml`），沒通過時 PR 上會顯示紅色的 ✗：

| 檢查 | 內容 |
| --- | --- |
| **Build site** | `mkdocs build --strict`：壞掉的連結、找不到的錨點、格式錯誤都會失敗 |
| **Run Java examples** | 用 Java 25 執行所有標了檔名的獨立範例（例如第 1 章的 `Hello.java`） |

機器人專案的範例（標題是 `src/main/java/...` 的程式碼）需要 WPILib 2027 的開發環境，CI 上會跳過，送出前請在自己的電腦上用上面的指令檢查。

## 版本說明

內容和 2027 alpha 的 API 或硬體規格有關的頁面，在標題下方加上：

```markdown
--8<-- "alpha-warning.md"
```

不確定、還沒有官方資料的內容，要明確寫出「尚未確認」，不要猜。
