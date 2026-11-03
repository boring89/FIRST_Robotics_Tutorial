# 名詞對照表

教學裡出現的專有名詞都會整理在這裡，依英文字母排序。

| English | 中文 | 說明 |
| --- | --- | --- |
| A301 | A301 致動器 | 2027 新的 FIRST 致動器，接在 Motioncore 的 Device port |
| Absolute encoder | 絕對式編碼器 | 斷電後仍然知道目前角度的編碼器 |
| Actuator | 致動器 | 會讓機器人動起來的元件，例如馬達、氣壓缸 |
| Beam break | 光遮斷感測器 | 光線被擋住時觸發，常用來偵測遊戲物件 |
| Brake / Coast | 煞車 / 滑行 | 馬達輸出為 0 時，要抵抗轉動還是自由轉動 |
| Brushless motor | 無刷馬達 | 沒有碳刷的馬達，效率高，需要搭配無刷馬達控制器 |
| CAN (Controller Area Network) | CAN 匯流排 | 讓控制器和各種裝置互相傳資料的通訊線路 |
| CAN ID | CAN 編號 | 每個 CAN 裝置在同一條匯流排上的唯一編號 |
| Circuit breaker | 斷路器 | 電流過大時自動斷電，保護線路 |
| Command | Command（指令） | 機器人要執行的一個動作，例如「Intake 吸入」 |
| Command factory | 指令工廠方法 | 回傳 Command 的方法，例如 `intake.intakeCommand()` |
| Commands v2 / v3 | Commands v2 / v3 | WPILib 的兩代 Command-based 框架，本教學以 v2 為主 |
| Coprocessor | 協同處理器 | 額外的小電腦，通常拿來跑視覺 |
| Coroutine | 協程 | 可以暫停、之後再繼續執行的程式，Commands v3 用它來寫 Command |
| Current limit | 電流限制 | 限制馬達最多能使用的電流，保護馬達和電池 |
| Deadband | 死區 | 小於某個值就當作 0，用來忽略搖桿放開時的微小讀值 |
| Decorator | 修飾 | 加在 Command 後面改變行為的方法，例如 `withTimeout()`、`until()` |
| Default command | 預設指令 | Subsystem 沒有被其他 Command 使用時執行的 Command |
| Deploy | 部署 | 把編譯好的程式傳到 Systemcore 上執行 |
| Driver Station | Driver Station（操控站） | 駕駛用來控制機器人的電腦程式 |
| Encoder | 編碼器 | 量測轉動角度和速度的感測器 |
| Firmware | 韌體 | 裝置本身內建的程式 |
| Follower | 跟隨者 | 自動跟著另一顆馬達（Leader）動作的馬達 |
| Gamepad | 手把 | Xbox、PS4 / PS5 手把等 |
| Heading | 朝向 | 機器人面向場地的哪個方向 |
| Idle mode | 閒置模式 | 馬達輸出為 0 時的行為，分成 Brake 和 Coast |
| IMU (Inertial Measurement Unit) | 慣性測量單元 | 量測轉向和加速度，常用來知道機器人朝哪個方向 |
| Interrupt | 中斷 | Command 在自己結束之前被停止，例如被需要同一個 Subsystem 的 Command 搶走 |
| Inverted | 反轉 | 讓馬達的正方向反過來的設定 |
| Limit switch | 極限開關 | 機構碰到時觸發的開關，用來限制行程 |
| Mechanism | Mechanism（機構） | Commands v3 裡代表一個機構的物件，對應 v2 的 Subsystem |
| Motioncore | Motioncore | 2027 的 CAN 與電源分配裝置 |
| Motor controller | 馬達控制器 | 接收控制器的指令，決定要送多少電給馬達 |
| Requirements | 需求 | Command 會使用哪些 Subsystem，Scheduler 用它避免衝突 |
| RPM (Rotations Per Minute) | 每分鐘轉數 | 轉速的單位，REV 的編碼器速度使用這個單位 |
| Scheduler | 排程器 | 每 20 ms 檢查 Trigger、執行 Command 的 WPILib 元件 |
| Simulation | 模擬 | 在電腦上執行機器人程式，不需要真的機器人 |
| Smart IO | Smart IO | Systemcore 上可以用程式切換功能的 I/O 埠 |
| Stack trace | 堆疊追蹤 | 程式出錯時印出的訊息，指出錯誤發生在哪一行 |
| State machine | 狀態機 | 用「狀態」和「狀態之間的轉換」描述機構行為的方法 |
| Subsystem | Subsystem（子系統） | Commands v2 裡代表一個機構的類別 |
| Systemcore | Systemcore | 2027 的機器人主控制器，取代 roboRIO |
| Telemetry | 遙測 | 把機器人的資料傳回電腦顯示或記錄 |
| Trigger | Trigger（觸發條件） | 條件成立時啟動 Command，例如按下按鈕 |
| Vendor dependency (vendordep) | 廠商函式庫 | 廠商提供的程式庫，例如 REVLib、Phoenix 6 |
| Yaw | 偏航角 | 繞著垂直軸轉動的角度，也就是機器人的朝向 |
| Zero offset | 零點偏移 | 讓絕對式編碼器在指定位置讀到 0 的設定 |
