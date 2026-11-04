package first.robot;

import com.ctre.phoenix6.signals.InvertedValue;
import org.wpilib.math.geometry.Translation2d;

public final class Constants {
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

    // ---------- 轉向馬達（SparkMax）----------
    public static final boolean STEER_INVERTED = true;
    public static final int STEER_CURRENT_LIMIT = 30;
    // SparkMax 內建 PID 的單位：誤差 1 圈（馬達的圈數）輸出多少比例（-1～1）
    public static final double STEER_KP = 0.3;
    public static final double STEER_KD = 0.0;

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
