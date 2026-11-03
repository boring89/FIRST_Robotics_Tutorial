# 安裝 WPILib

--8<-- "alpha-warning.md"

## 這頁學什麼

- WPILib 是什麼、安裝了哪些東西
- 在自己的作業系統上安裝 WPILib
- 安裝 FIRST Driver Station
- 打開正確的 VS Code

## 觀念

### WPILib 是什麼

**WPILib** 是 FRC 官方的程式函式庫和開發工具組。你寫的程式會呼叫 WPILib 來讀手把、控制馬達、和 Driver Station 溝通。安裝程式會一次裝好所有需要的東西：

| 內容 | 用途 |
| --- | --- |
| **WPILib VS Code** | 寫程式用的編輯器，已經裝好 WPILib 擴充套件 |
| **JDK** | Java 開發工具（2027 是 Java 25），不會影響電腦上其他的 Java |
| **Gradle** | 編譯程式、部署程式的工具 |
| **WPILib 函式庫** | 離線版本，不用網路也能編譯 |
| **工具程式** | AdvantageScope、Elastic、Glass、SysId 等 |
| **離線文件** | WPILib 文件和 API 參考 |

**每一個版本都裝在自己的資料夾裡**，所以 2026 和 2027 可以同時存在，alpha 版之間也不會互相覆蓋：

| 作業系統 | 安裝位置 |
| --- | --- |
| Windows | `C:\Users\Public\wpilib\2027_alpha7` |
| macOS | `~/wpilib/2027_alpha7` |
| Linux | `~/.local/share/wpilib/2027_alpha7` |

## 步驟

### 1. 下載安裝程式

到 [WPILib 的 GitHub Releases](https://github.com/wpilibsuite/allwpilib/releases) 頁面，找到最新的 2027 版本（目前是 alpha）。安裝程式的下載連結寫在**版本說明的文字裡**，下載符合你電腦的檔案（以 alpha-7 為例）：

| 作業系統 | 檔案 |
| --- | --- |
| Windows | `WPILib_Windows-x64-2027.0.0-alpha-7.iso` |
| macOS（Apple 晶片） | `WPILib_macOS-Arm64-2027.0.0-alpha-7.dmg` |
| macOS（Intel） | `WPILib_macOS-Intel-2027.0.0-alpha-7.dmg` |
| Linux | `WPILib_Linux-x64-2027.0.0-alpha-7.tar.gz` |

檔案大約 2 GB 以上，建議用穩定的網路下載。

### 2. 執行安裝程式

=== "Windows"

    1. 對 `.iso` 檔按右鍵 → **掛接 (Mount)**
    2. 執行裡面的 `WPILibInstaller.exe`
    3. 如果跳出 SmartScreen 警告，按 **其他資訊** → **仍要執行**

=== "macOS"

    1. 打開 `.dmg` 檔
    2. 執行 **WPILibInstaller**，跳出警告時按 **打開**
    3. 安裝前，先退出舊的 WPILibInstaller 映像檔

=== "Linux"

    在終端機執行（雙擊執行檔在 Ubuntu 上沒有作用）：

    ```bash
    tar -xf WPILib_Linux-x64-2027.0.0-alpha-7.tar.gz
    cd WPILib_Linux-x64-2027.0.0-alpha-7
    ./WPILibInstaller
    ```

### 3. 選擇安裝選項

1. 按 **Start**
2. 選 **Everything**（完整安裝）。另一個選項 **Tools Only** 不包含 VS Code，寫程式的電腦不要選它
3. Windows 會問要裝給誰：**Install for this User** 不需要管理員權限；**Install for all Users** 需要，但電腦上每個帳號都能用
4. VS Code 的部分選 **Download for this computer only**
5. 等它下載、安裝完成，按 **Finish**

### 4. 打開 WPILib VS Code

安裝完成後，桌面會出現一個 **2027_alpha7 WPILib VS Code** 的捷徑（macOS 和 Linux 不會建立桌面捷徑，要到安裝資料夾裡找）。

!!! warning "注意：一定要開 WPILib 的 VS Code"
    如果你電腦上本來就有裝 VS Code，**不要用那一個**。WPILib 安裝的是另一份獨立的 VS Code，裡面已經設定好 Java 和 WPILib 擴充套件。用錯 VS Code，會出現找不到 WPILib 指令或 Java 版本不對的問題。

打開後，按 ++ctrl+shift+p++（macOS 是 ++cmd+shift+p++）打開 **Command Palette（命令面板）**，輸入 `WPILib`，應該會看到一串 WPILib 開頭的指令。看得到就代表安裝成功了。

!!! tip "小技巧：右上角的 WPILib 圖示"
    編輯器右上角有一個 WPILib 的紅色 W 圖示，點它也能打開 WPILib 指令清單，不用記快捷鍵。

### 5. 安裝 FIRST Driver Station

2027 使用新版的 **FIRST Driver Station**，要另外安裝：

1. 到 [FirstDriverStation-Public](https://github.com/wpilibsuite/FirstDriverStation-Public) 下載最新版本
2. 確認版本是 **Alpha 7 以上**（Systemcore image 14 以上需要）
3. 依照該頁面的說明安裝

!!! info "2027 新改動"
    2026 以前的 NI Driver Station 只能在 Windows 上執行，也不支援 Systemcore。新版 FIRST Driver Station 支援多種作業系統，但**只支援 Systemcore**，不能用來控制 roboRIO。

## 常見錯誤

!!! bug "常見錯誤：Linux 上 VS Code 打不開"
    Ubuntu 預設的安全設定會擋住 VS Code。把安裝資料夾裡 `wpilibcode/AppArmor` 的設定檔複製到 `/etc/apparmor.d/`，再重新載入 AppArmor：

    ```bash
    sudo cp ~/.local/share/wpilib/2027_alpha7/wpilibcode/AppArmor/* /etc/apparmor.d/
    sudo systemctl reload apparmor
    ```

!!! bug "常見錯誤：裝到一半失敗，或防毒軟體刪掉檔案"
    學校電腦的防毒軟體有時會擋住安裝程式或刪掉 Gradle 的檔案。可以請管理員把 WPILib 的安裝資料夾加入例外，再重新安裝一次。

!!! bug "常見錯誤：開了舊年度的 VS Code"
    電腦上同時有 2026 和 2027 的 WPILib 時，桌面會有兩個捷徑。開錯年度，建立的專案就會是舊版。看捷徑名稱上的年度。

## 小練習

1. 安裝完成後，打開 WPILib VS Code 的 Command Palette，找出這三個指令：`WPILib: Create a new project`、`WPILib: Deploy Robot Code`、`WPILib: Manage Vendor Libraries`。
2. 找到你電腦上的 WPILib 安裝資料夾，看看 `tools` 資料夾裡有哪些工具程式。
