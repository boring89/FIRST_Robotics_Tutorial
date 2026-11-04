package first.robot;

import com.ctre.phoenix6.signals.InvertedValue;
import org.wpilib.framework.RobotBase;
import org.wpilib.math.geometry.Translation2d;
import org.wpilib.math.system.DCMotor;

public final class Constants {
  /** AdvantageKit 的執行模式。 */
  public enum Mode {
    /** 真的機器人。 */
    REAL,
    /** 在電腦上模擬。 */
    SIM,
    /** 在電腦上重播 log 檔。 */
    REPLAY
  }

  /** 在電腦上執行時要用哪一種模式：模擬（SIM）還是重播（REPLAY）。 */
  public static final Mode SIM_MODE = Mode.SIM;
  /** 現在的模式：部署到機器人上時一定是 REAL。 */
  public static final Mode CURRENT_MODE = RobotBase.isReal() ? Mode.REAL : SIM_MODE;

  public static final class OperatorConstants {
    public static final int DRIVER_CONTROLLER_PORT = 0;
  }

  /**
   * 一個 Swerve 模組的設定。
   *
   * @param name 名稱，用在 Telemetry
   * @param driveId 驅動馬達（TalonFX）的 CAN ID
   * @param steerId 轉向馬達（SparkMax）的 CAN ID
   * @param encoderId CANcoder 的 CAN ID
   * @param encoderOffsetRotations CANcoder 的零點，單位：圈
   * @param location 模組相對於機器人中心的位置，單位：公尺（X 往前、Y 往左）
   */
  public record ModuleConstants(
      String name,
      int driveId,
      int steerId,
      int encoderId,
      double encoderOffsetRotations,
      Translation2d location) {}

  public static final class SwerveConstants {
    // ---------- 尺寸 ----------
    /** 左右兩個模組中心的距離，單位：公尺。 */
    public static final double TRACK_WIDTH_METERS = 0.578;
    /** 前後兩個模組中心的距離，單位：公尺。 */
    public static final double WHEEL_BASE_METERS = 0.578;
    public static final double WHEEL_DIAMETER_METERS = 0.1016; // 4 英寸
    public static final double WHEEL_CIRCUMFERENCE_METERS = Math.PI * WHEEL_DIAMETER_METERS;

    // ---------- 齒輪比（馬達轉幾圈，輪子轉一圈）----------
    public static final double DRIVE_GEAR_RATIO = 6.75;
    public static final double STEER_GEAR_RATIO = 150.0 / 7.0;

    // ---------- 速度上限 ----------
    /** 底盤的最高速度，單位：公尺 / 秒。 */
    public static final double MAX_SPEED = 4.5;
    /** 底盤的最高旋轉速度，單位：弧度 / 秒。 */
    public static final double MAX_ANGULAR_SPEED =
        MAX_SPEED / Math.hypot(TRACK_WIDTH_METERS / 2, WHEEL_BASE_METERS / 2);

    // ---------- 驅動馬達（TalonFX）----------
    public static final InvertedValue DRIVE_INVERTED = InvertedValue.CounterClockwise_Positive;
    public static final double DRIVE_CURRENT_LIMIT = 60;
    public static final double DRIVE_KS = 0.2; // 伏特
    public static final double DRIVE_KV = 0.8; // 伏特 / (輪子每秒轉數)
    public static final double DRIVE_KP = 0.1;
    public static final double DRIVE_KA = 0.05; // 伏特 / (輪子每秒轉數 / 秒)

    // ---------- 轉向馬達（SparkMax）----------
    public static final boolean STEER_INVERTED = true;
    public static final int STEER_CURRENT_LIMIT = 30;
    // SparkMax 內建 PID 的單位：誤差 1 圈（馬達的圈數）輸出多少比例（-1～1）
    public static final double STEER_KP = 0.3;
    public static final double STEER_KD = 0.0;

    // ---------- Setpoint generator ----------
    public static final double ROBOT_MASS_KG = 50.0;
    /** 機器人的轉動慣量，單位：kg·m²。 */
    public static final double ROBOT_MOI = 6.0;
    /** 輪子和地毯的摩擦係數。 */
    public static final double WHEEL_COF = 1.2;
    /** 輪子轉向的最高速度，單位：弧度 / 秒。NEO 空轉轉速 ÷ 齒輪比，打八折。 */
    public static final double MAX_STEER_VELOCITY =
        DCMotor.getNEO(1).freeSpeed / STEER_GEAR_RATIO * 0.8;

    // ---------- 保持朝向 ----------
    /** 朝向差 1 弧度時，轉多快（弧度 / 秒）。 */
    public static final double HEADING_KP = 5.0;
    /** 機器人轉得比這個慢（弧度 / 秒），才開始鎖定朝向。 */
    public static final double HEADING_LOCK_SPEED = 0.3;

    // ---------- 四個模組 ----------
    public static final ModuleConstants FRONT_LEFT =
        new ModuleConstants(
            "FrontLeft", 1, 2, 41, -0.254,
            new Translation2d(WHEEL_BASE_METERS / 2, TRACK_WIDTH_METERS / 2));
    public static final ModuleConstants FRONT_RIGHT =
        new ModuleConstants(
            "FrontRight", 3, 4, 42, 0.137,
            new Translation2d(WHEEL_BASE_METERS / 2, -TRACK_WIDTH_METERS / 2));
    public static final ModuleConstants BACK_LEFT =
        new ModuleConstants(
            "BackLeft", 5, 6, 43, 0.412,
            new Translation2d(-WHEEL_BASE_METERS / 2, TRACK_WIDTH_METERS / 2));
    public static final ModuleConstants BACK_RIGHT =
        new ModuleConstants(
            "BackRight", 7, 8, 44, -0.081,
            new Translation2d(-WHEEL_BASE_METERS / 2, -TRACK_WIDTH_METERS / 2));
  }

  public static final class AutoConstants {
    /** PathPlanner 修正位置誤差的 P：差 1 公尺時，多給 5 公尺 / 秒。 */
    public static final double TRANSLATION_KP = 5.0;
    /** PathPlanner 修正朝向誤差的 P：差 1 弧度時，多給 5 弧度 / 秒。 */
    public static final double ROTATION_KP = 5.0;
  }

  public static final class VisionConstants {
    public static final String LIMELIGHT_NAME = "limelight";
    /** 對準目標的 P：差 1 度時，轉 0.1 弧度 / 秒。 */
    public static final double AIM_KP = 0.1;
    public static final double AIM_TOLERANCE_DEGREES = 1.0;
  }

  private Constants() {}
}
