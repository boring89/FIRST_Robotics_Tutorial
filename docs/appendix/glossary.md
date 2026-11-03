# 名詞對照表

教學裡出現的專有名詞都會整理在這裡，依英文字母排序。

| English | 中文 | 說明 |
| --- | --- | --- |
| A301 | A301 致動器 | 2027 新的 FIRST 致動器，接在 Motioncore 的 Device port |
| Absolute encoder | 絕對式編碼器 | 斷電後仍然知道目前角度的編碼器 |
| Actuator | 致動器 | 會讓機器人動起來的元件，例如馬達、氣壓缸 |
| Alliance | 聯盟 | 每場比賽的紅、藍兩方，各由 3 支隊伍組成 |
| AprilTag | AprilTag | 場地上有編號的黑白方塊圖案，相機看到它就能算出位置 |
| Arbitrary feedforward | 額外前饋 | 自己算好、加在馬達控制器輸出上的電壓，例如手臂的重力補償 |
| Arcade drive | 單搖桿式操控 | 一個軸控制前進後退、另一個軸控制轉彎的底盤操控方式 |
| Autonomous period | 自動階段 | 比賽一開始機器人照程式自己動的時間 |
| Beam break | 光遮斷感測器 | 光線被擋住時觸發，常用來偵測遊戲物件 |
| Brake / Coast | 煞車 / 滑行 | 馬達輸出為 0 時，要抵抗轉動還是自由轉動 |
| Brushless motor | 無刷馬達 | 沒有碳刷的馬達，效率高，需要搭配無刷馬達控制器 |
| CAN (Controller Area Network) | CAN 匯流排 | 讓控制器和各種裝置互相傳資料的通訊線路 |
| CAN ID | CAN 編號 | 每個 CAN 裝置在同一條匯流排上的唯一編號 |
| Circuit breaker | 斷路器 | 電流過大時自動斷電，保護線路 |
| Closed loop / Open loop | 閉迴路 / 開迴路 | 是否根據感測器量到的結果修正輸出 |
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
| Differential drive | 差速底盤 | 左右兩邊輪子各自控制，靠速度差轉彎的底盤，也叫 Tank drive |
| Driver Station | Driver Station（操控站） | 駕駛用來控制機器人的電腦程式 |
| Encoder | 編碼器 | 量測轉動角度和速度的感測器 |
| Error | 誤差 | 目標值減去實際值 |
| Event marker | 事件標記 | PathPlanner 路徑上的一個點，機器人經過時觸發 Named command |
| Feedforward | 前饋 | 用物理模型預先算出需要的電壓（kS、kV、kA、kG） |
| Field layout | 場地配置 | 場地上每個 AprilTag 的位置資料 |
| Field-relative | 場地座標 | 以場地為準的方向，推搖桿往前，機器人就往場地前方走 |
| Firmware | 韌體 | 裝置本身內建的程式 |
| Follower | 跟隨者 | 自動跟著另一顆馬達（Leader）動作的馬達 |
| Gain | 增益（參數） | 控制器的參數，例如 kP、kV |
| Gamepad | 手把 | Xbox、PS4 / PS5 手把等 |
| Heading | 朝向 | 機器人面向場地的哪個方向 |
| Idle mode | 閒置模式 | 馬達輸出為 0 時的行為，分成 Brake 和 Coast |
| IMU (Inertial Measurement Unit) | 慣性測量單元 | 量測轉向和加速度，常用來知道機器人朝哪個方向 |
| Integral windup | 積分飽和 | PID 的 I 項累積過大，造成機構猛然衝出去 |
| Interrupt | 中斷 | Command 在自己結束之前被停止，例如被需要同一個 Subsystem 的 Command 搶走 |
| Inverted | 反轉 | 讓馬達的正方向反過來的設定 |
| Kinematics | 運動學 | 機器人整體速度和各個輪子速度之間的換算 |
| Limit switch | 極限開關 | 機構碰到時觸發的開關，用來限制行程 |
| MAXMotion | MAXMotion | REV 馬達控制器內建的 Motion Profile 功能 |
| Mechanism | Mechanism（機構） | Commands v3 裡代表一個機構的物件，對應 v2 的 Subsystem |
| MegaTag | MegaTag | Limelight 用 AprilTag 計算機器人位置的方法，MegaTag2 會用到機器人的朝向 |
| Motion Magic | Motion Magic | CTRE 馬達控制器內建的 Motion Profile 功能 |
| Motion Profile | 運動曲線 | 事先規劃好每個時間點的位置和速度，讓機構平順地移動 |
| Motioncore | Motioncore | 2027 的 CAN 與電源分配裝置 |
| Motor controller | 馬達控制器 | 接收控制器的指令，決定要送多少電給馬達 |
| Motor Safety | 馬達安全機制 | 太久沒有收到新指令時自動停止馬達的機制 |
| Named command | 具名指令 | 在 PathPlanner 裡用名字指定、在程式裡註冊的 Command |
| Object detection | 物件偵測 | 用神經網路辨識畫面中的遊戲物件 |
| Odometry | 里程計 | 用編碼器和 IMU 累加計算機器人在場上的位置 |
| Oscillation | 震盪 | 機構在目標附近來回擺動，通常是 kP 太大 |
| Pathfinding | 即時路徑規劃 | 給定目標位置，自動找出避開障礙物的路徑 |
| PathPlanner | PathPlanner | FRC 常用的路徑規劃工具，包含應用程式和 PathPlannerLib |
| PID | PID 控制 | 根據誤差的比例、累積、變化速度計算輸出的控制方法 |
| Pipeline | 處理流程 | 視覺系統中一組影像處理的設定，例如 AprilTag 偵測 |
| Pose estimation | 姿態估計 | 結合 Odometry 和視覺等多種來源，估計機器人的位置 |
| Pose2d | 位姿 | 機器人在場上的 X、Y 座標和朝向 |
| Requirements | 需求 | Command 會使用哪些 Subsystem，Scheduler 用它避免衝突 |
| Robot-relative | 機器人座標 | 以機器人自己為準的方向，「前」是機器人面向的方向 |
| RPM (Rotations Per Minute) | 每分鐘轉數 | 轉速的單位，REV 的編碼器速度使用這個單位 |
| Scheduler | 排程器 | 每 20 ms 檢查 Trigger、執行 Command 的 WPILib 元件 |
| Selectable | Selectable（選單） | 2027 的 Dashboard 選單，用來選擇自動程式，取代 SendableChooser |
| Setpoint | 目標值 | 控制器要達到的值，例如 4000 RPM、90 度 |
| Simulation | 模擬 | 在電腦上執行機器人程式，不需要真的機器人 |
| Smart IO | Smart IO | Systemcore 上可以用程式切換功能的 I/O 埠 |
| Stack trace | 堆疊追蹤 | 程式出錯時印出的訊息，指出錯誤發生在哪一行 |
| Standard deviation | 標準差 | 在 Pose estimation 中代表一筆資料有多不可信，越大越不可信 |
| State machine | 狀態機 | 用「狀態」和「狀態之間的轉換」描述機構行為的方法 |
| Subsystem | Subsystem（子系統） | Commands v2 裡代表一個機構的類別 |
| Swerve drive | 全向轉向底盤 | 每個輪子都能獨立轉向和驅動的底盤 |
| Swerve module | Swerve 模組 | Swerve 底盤的一個角落，包含驅動馬達、轉向馬達和絕對式編碼器 |
| SysId | 系統識別 | 量測機構特性、算出 Feedforward 參數的 WPILib 工具 |
| Systemcore | Systemcore | 2027 的機器人主控制器，取代 roboRIO |
| Tank drive | 坦克式操控 | 左右搖桿分別控制左右兩邊輪子的底盤操控方式 |
| Telemetry | 遙測 | 把機器人的資料傳回電腦顯示或記錄 |
| Timestamp | 時間戳記 | 資料是在什麼時間點量到的 |
| Trapezoid profile | 梯形速度曲線 | 先加速、等速巡航、再減速的 Motion Profile |
| Trigger | Trigger（觸發條件） | 條件成立時啟動 Command，例如按下按鈕 |
| tx / ty | 水平 / 垂直角度 | 目標相對於相機畫面中央的角度 |
| Vendor dependency (vendordep) | 廠商函式庫 | 廠商提供的程式庫，例如 REVLib、Phoenix 6 |
| Yaw | 偏航角 | 繞著垂直軸轉動的角度，也就是機器人的朝向 |
| Zero offset | 零點偏移 | 讓絕對式編碼器在指定位置讀到 0 的設定 |
