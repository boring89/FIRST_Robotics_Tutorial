# 差速底盤

--8<-- "alpha-warning.md"

## 這頁學什麼

- 差速底盤怎麼前進和轉彎
- Arcade drive 和 Tank drive 兩種操控方式
- 用 `DifferentialDrive` 寫底盤 Subsystem
- 用 default command 讓駕駛用搖桿開車

## 觀念

### 差速底盤怎麼動

差速底盤左右各有一組輪子（通常每邊 2～3 個輪子，用 2 顆馬達帶動）：

| 左邊 | 右邊 | 機器人 |
| --- | --- | --- |
| 往前 | 往前 | 前進 |
| 往後 | 往後 | 後退 |
| 往前 | 往後 | 原地向右轉 |
| 往前（快） | 往前（慢） | 邊前進邊向右轉 |

左右兩邊的馬達通常是**面對面**裝的，所以要讓機器人前進，左右馬達其實要往相反的方向轉。我們會把右邊的馬達設成[反轉](../06-motors-sensors/motor-config.md#inverted反轉)，讓程式裡「正值 = 往前」對兩邊都成立。

### 兩種操控方式

| 方式 | 搖桿 | 特色 |
| --- | --- | --- |
| **Arcade drive** | 左搖桿上下控制前進後退，右搖桿左右控制轉彎 | 直覺，大部分駕駛喜歡這種 |
| **Tank drive** | 左搖桿控制左邊輪子，右搖桿控制右邊輪子 | 像開坦克，操作精細但比較難上手 |

### `DifferentialDrive`

WPILib 的 `DifferentialDrive` 類別幫你把「前進速度 + 轉彎速度」換算成「左邊輸出 + 右邊輸出」：

```java
private final DifferentialDrive drive =
    new DifferentialDrive(leftLeader::setThrottle, rightLeader::setThrottle);

drive.arcadeDrive(forward, rotation);   // forward、rotation 都是 -1.0 ～ 1.0
drive.tankDrive(left, right);
```

- 建構子接收兩個 **method reference**：「怎麼設定左邊的輸出」和「怎麼設定右邊的輸出」。這是 [Lambda](../01-java/lambda.md) 那頁學過的寫法，REV 和 CTRE 的馬達都能這樣用
- `arcadeDrive` 的 `rotation` **逆時針為正**：正值往左轉
- 預設會把輸入**平方**（保留正負號），讓搖桿推一點點時更好微調

!!! info "Motor Safety"
    `DifferentialDrive` 內建 **Motor Safety（馬達安全機制）**：如果超過 0.1 秒沒有呼叫 `arcadeDrive` 或 `tankDrive`，它會自動停止馬達，並印出警告。所以要讓 default command **每次迴圈都呼叫**它。

### Follower

每邊的兩顆馬達都帶動同一組輪子，所以第二顆設成第一顆的 [Follower](../06-motors-sensors/further.md#follower讓兩顆馬達一起動)，程式只需要控制每邊的 **Leader**。

## 範例

在 `Constants.java` 的 `DrivetrainConstants` 加上：

```java
public static final class DrivetrainConstants {
  public static final int LEFT_LEADER_ID = 1;
  public static final int LEFT_FOLLOWER_ID = 2;
  public static final int RIGHT_LEADER_ID = 3;
  public static final int RIGHT_FOLLOWER_ID = 4;
  public static final int PIGEON_ID = 41;
  public static final int CURRENT_LIMIT = 50;
  public static final double GEAR_RATIO = 8.45;              // 馬達轉 8.45 圈，輪子轉 1 圈
  public static final double WHEEL_DIAMETER_METERS = 0.1524; // 6 英寸輪
}
```

把[第 6 章的 Drivetrain](../06-motors-sensors/gyro.md#範例)加上馬達：

=== "REV"

    ```java title="src/main/java/first/robot/subsystems/Drivetrain.java"
    package first.robot.subsystems;

    import com.revrobotics.PersistMode;
    import com.revrobotics.RelativeEncoder;
    import com.revrobotics.ResetMode;
    import com.revrobotics.spark.SparkLowLevel.MotorType;
    import com.revrobotics.spark.SparkMax;
    import com.revrobotics.spark.config.SparkBaseConfig;
    import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
    import com.revrobotics.spark.config.SparkMaxConfig;
    import first.robot.Constants.DrivetrainConstants;
    import java.util.function.DoubleSupplier;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.drive.DifferentialDrive;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.hardware.imu.OnboardIMU;
    import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;
    import org.wpilib.math.geometry.Rotation2d;
    import org.wpilib.telemetry.Telemetry;

    public class Drivetrain extends SubsystemBase {
      private final SparkMax leftLeader =
          new SparkMax(CANPort.CAN_S0, DrivetrainConstants.LEFT_LEADER_ID, MotorType.kBrushless);
      private final SparkMax leftFollower =
          new SparkMax(CANPort.CAN_S0, DrivetrainConstants.LEFT_FOLLOWER_ID, MotorType.kBrushless);
      private final SparkMax rightLeader =
          new SparkMax(CANPort.CAN_S0, DrivetrainConstants.RIGHT_LEADER_ID, MotorType.kBrushless);
      private final SparkMax rightFollower =
          new SparkMax(CANPort.CAN_S0, DrivetrainConstants.RIGHT_FOLLOWER_ID, MotorType.kBrushless);

      private final RelativeEncoder leftEncoder = leftLeader.getEncoder();
      private final RelativeEncoder rightEncoder = rightLeader.getEncoder();
      private final OnboardIMU imu = new OnboardIMU(MountOrientation.FLAT);

      private final DifferentialDrive drive =
          new DifferentialDrive(leftLeader::setThrottle, rightLeader::setThrottle);

      public Drivetrain() {
        configure(leftLeader, baseConfig());
        configure(rightLeader, baseConfig().inverted(true));
        configure(leftFollower, baseConfig().follow(leftLeader));
        configure(rightFollower, baseConfig().follow(rightLeader));
      }

      private static SparkMaxConfig baseConfig() {
        SparkMaxConfig config = new SparkMaxConfig();
        config.idleMode(IdleMode.kBrake).smartCurrentLimit(DrivetrainConstants.CURRENT_LIMIT);
        return config;
      }

      private static void configure(SparkMax motor, SparkBaseConfig config) {
        motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
      }

      /** 左右輪平均走了多遠，單位：公尺。 */
      public double getDistanceMeters() {
        double rotations = (leftEncoder.getPosition().get(0.0) + rightEncoder.getPosition().get(0.0)) / 2;
        return rotations / DrivetrainConstants.GEAR_RATIO * Math.PI * DrivetrainConstants.WHEEL_DIAMETER_METERS;
      }

      public void resetEncoders() {
        leftEncoder.setPosition(0);
        rightEncoder.setPosition(0);
      }

      /** 機器人的朝向，逆時針為正。 */
      public Rotation2d getHeading() {
        return imu.getRotation2d();
      }

      /** 用 Arcade drive 開車：forward 正值往前，rotation 正值往左轉。 */
      public Command arcadeDriveCommand(DoubleSupplier forward, DoubleSupplier rotation) {
        return run(() -> drive.arcadeDrive(forward.getAsDouble(), rotation.getAsDouble()))
            .withName("ArcadeDrive");
      }

      public Command resetHeadingCommand() {
        return runOnce(imu::resetYaw).ignoringDisable(true).withName("ResetHeading");
      }

      @Override
      public void periodic() {
        Telemetry.log("Drivetrain/DistanceMeters", getDistanceMeters());
        Telemetry.log("Drivetrain/HeadingDegrees", getHeading().getDegrees());
      }
    }
    ```

    `follow(leftLeader)` 讓 follower 跟著 leader，方向和 leader 相同。每邊的兩顆馬達裝的方向一樣，所以不用另外反轉。

=== "CTRE"

    ```java title="src/main/java/first/robot/subsystems/Drivetrain.java"
    package first.robot.subsystems;

    import com.ctre.phoenix6.CANBus;
    import com.ctre.phoenix6.configs.TalonFXConfiguration;
    import com.ctre.phoenix6.controls.Follower;
    import com.ctre.phoenix6.hardware.TalonFX;
    import com.ctre.phoenix6.signals.InvertedValue;
    import com.ctre.phoenix6.signals.MotorAlignmentValue;
    import com.ctre.phoenix6.signals.NeutralModeValue;
    import first.robot.Constants.DrivetrainConstants;
    import java.util.function.DoubleSupplier;
    import org.wpilib.command2.Command;
    import org.wpilib.command2.SubsystemBase;
    import org.wpilib.drive.DifferentialDrive;
    import org.wpilib.hardware.bus.CANPort;
    import org.wpilib.hardware.imu.OnboardIMU;
    import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;
    import org.wpilib.math.geometry.Rotation2d;
    import org.wpilib.telemetry.Telemetry;

    public class Drivetrain extends SubsystemBase {
      private final CANBus bus = new CANBus(CANPort.CAN_S0);
      private final TalonFX leftLeader = new TalonFX(DrivetrainConstants.LEFT_LEADER_ID, bus);
      private final TalonFX leftFollower = new TalonFX(DrivetrainConstants.LEFT_FOLLOWER_ID, bus);
      private final TalonFX rightLeader = new TalonFX(DrivetrainConstants.RIGHT_LEADER_ID, bus);
      private final TalonFX rightFollower = new TalonFX(DrivetrainConstants.RIGHT_FOLLOWER_ID, bus);

      private final OnboardIMU imu = new OnboardIMU(MountOrientation.FLAT);

      private final DifferentialDrive drive =
          new DifferentialDrive(leftLeader::setThrottle, rightLeader::setThrottle);

      public Drivetrain() {
        TalonFXConfiguration config = new TalonFXConfiguration();
        config.MotorOutput.NeutralMode = NeutralModeValue.Brake;
        config.CurrentLimits.StatorCurrentLimit = DrivetrainConstants.CURRENT_LIMIT;
        config.CurrentLimits.StatorCurrentLimitEnable = true;

        config.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;
        leftLeader.getConfigurator().apply(config);
        leftFollower.getConfigurator().apply(config);

        config.MotorOutput.Inverted = InvertedValue.Clockwise_Positive;   // 右邊反轉
        rightLeader.getConfigurator().apply(config);
        rightFollower.getConfigurator().apply(config);

        leftFollower.setControl(new Follower(leftLeader.getDeviceID(), MotorAlignmentValue.Aligned));
        rightFollower.setControl(new Follower(rightLeader.getDeviceID(), MotorAlignmentValue.Aligned));
      }

      /** 左右輪平均走了多遠，單位：公尺。 */
      public double getDistanceMeters() {
        double rotations =
            (leftLeader.getPosition().getValueAsDouble() + rightLeader.getPosition().getValueAsDouble()) / 2;
        return rotations / DrivetrainConstants.GEAR_RATIO * Math.PI * DrivetrainConstants.WHEEL_DIAMETER_METERS;
      }

      public void resetEncoders() {
        leftLeader.setPosition(0);
        rightLeader.setPosition(0);
      }

      /** 機器人的朝向，逆時針為正。 */
      public Rotation2d getHeading() {
        return imu.getRotation2d();
      }

      /** 用 Arcade drive 開車：forward 正值往前，rotation 正值往左轉。 */
      public Command arcadeDriveCommand(DoubleSupplier forward, DoubleSupplier rotation) {
        return run(() -> drive.arcadeDrive(forward.getAsDouble(), rotation.getAsDouble()))
            .withName("ArcadeDrive");
      }

      public Command resetHeadingCommand() {
        return runOnce(imu::resetYaw).ignoringDisable(true).withName("ResetHeading");
      }

      @Override
      public void periodic() {
        Telemetry.log("Drivetrain/DistanceMeters", getDistanceMeters());
        Telemetry.log("Drivetrain/HeadingDegrees", getHeading().getDegrees());
      }
    }
    ```

    `MotorAlignmentValue.Aligned` 讓 follower 和 leader 往同一個方向轉。Follower 自己的 `Inverted` 設定在跟隨時不會用到，這裡一起設定只是讓四顆馬達的設定一致。

兩個版本的 IMU 都用 Systemcore 內建的；用 Pigeon 2 的話，照 [第 6 章](../06-motors-sensors/gyro.md#範例) 的寫法替換。

在 `RobotContainer` 設定 default command：

```java
private final Drivetrain drivetrain = new Drivetrain();

// 在 configureDefaultCommands() 裡
drivetrain.setDefaultCommand(
    drivetrain.arcadeDriveCommand(() -> -driver.getLeftY(), () -> -driver.getRightX()));
```

兩個負號的原因：

- `-driver.getLeftY()`：搖桿往前推是負值（[讀取手把輸入](../04-robot-basics/gamepad.md#讀搖桿和板機)），要變成正值才是前進
- `-driver.getRightX()`：搖桿往右推是正值，但 `arcadeDrive` 的旋轉是**逆時針（往左）為正**，所以往右推要變成負值

### 第一次測試底盤

!!! warning "注意：底盤一定要架高測試"
    第一次測試底盤程式時，**把機器人架在箱子或支架上，讓輪子離地**。確認每個輪子的轉向都正確後，才放到地上。底盤馬達的力量很大，方向錯誤的機器人在地上會直接衝出去。

1. 架高機器人，Enable，輕推左搖桿往前：**所有輪子都應該往前轉**
2. 輕推右搖桿往右：左邊輪子往前、右邊輪子往後
3. 有輪子方向不對：回到設定檢查 `inverted` / `Inverted`
4. 確認都正確後放到地上，用低速測試

## 常見錯誤

!!! bug "常見錯誤：機器人只會原地打轉"
    推搖桿往前，機器人卻原地轉圈：左右其中一邊的方向反了。檢查右邊有沒有設定反轉，或是左邊被誤設成反轉。

!!! bug "常見錯誤：Follower 和 Leader 互相抵抗"
    同一邊的兩顆馬達方向不一致，會互相抵抗，聽起來有怪聲、馬達很快發燙、電流很高。架高測試時，摸摸看（小心！）或用 Telemetry 看兩顆馬達的電流。

!!! bug "常見錯誤：出現「Output not updated often enough」警告"
    這是 Motor Safety 的警告：`arcadeDrive` 沒有每次迴圈都被呼叫。確認底盤有 default command，而且 Command 是用 `run()`（每次迴圈都執行）寫的，不是 `runOnce()`。

## 小練習

1. 照著範例寫出 Drivetrain，架高測試每個輪子的方向。
2. 改成 Tank drive：寫一個 `tankDriveCommand(DoubleSupplier left, DoubleSupplier right)`，讓駕駛試試兩種操控方式，問問看他們喜歡哪一種。
3. 推著機器人往前走 1 公尺（用捲尺量），看 `Drivetrain/DistanceMeters` 是不是接近 1。差很多的話，檢查齒輪比和輪子直徑。
