package first.robot.commands;

import first.robot.Constants.SwerveConstants;
import first.robot.subsystems.Drivetrain;
import org.wpilib.command2.Command;
import org.wpilib.math.filter.SlewRateLimiter;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.telemetry.Telemetry;

/** 原地慢慢旋轉，比較 IMU 轉過的角度和輪子轉過的角度，算出輪子真正的半徑。 */
public class WheelRadiusCharacterization extends Command {
  /** 旋轉速度，單位：弧度 / 秒。轉慢一點，輪子比較不會打滑。 */
  private static final double OMEGA = 1.0;
  /** 旋轉速度每秒最多增加多少，單位：弧度 / 秒²。 */
  private static final double RAMP_RATE = 0.5;
  /** 模組到機器人中心的距離，單位：公尺。原地旋轉時，每個輪子都繞著這個半徑的圓走。 */
  private static final double DRIVE_BASE_RADIUS =
      Math.hypot(SwerveConstants.TRACK_WIDTH_METERS / 2, SwerveConstants.WHEEL_BASE_METERS / 2);

  private final Drivetrain drivetrain;
  private final SlewRateLimiter omegaLimiter = new SlewRateLimiter(RAMP_RATE);

  private double[] startWheelRadians;
  private Rotation2d lastHeading;
  private double gyroRadians;
  private double radiusMeters;

  public WheelRadiusCharacterization(Drivetrain drivetrain) {
    this.drivetrain = drivetrain;
    addRequirements(drivetrain);
  }

  @Override
  public void initialize() {
    omegaLimiter.reset(0.0);
    startWheelRadians = drivetrain.getWheelRadians();
    lastHeading = drivetrain.getHeading();
    gyroRadians = 0.0;
    radiusMeters = 0.0;
  }

  @Override
  public void execute() {
    drivetrain.driveRobotRelative(new ChassisVelocities(0.0, 0.0, omegaLimiter.calculate(OMEGA)));

    // IMU：把每 20 ms 轉過的角度累加起來（不受 -180～180 度的限制）
    Rotation2d heading = drivetrain.getHeading();
    gyroRadians += heading.minus(lastHeading).getRadians();
    lastHeading = heading;

    // 輪子：四個輪子從開始到現在轉過的角度，取平均
    double[] wheelRadians = drivetrain.getWheelRadians();
    double wheelSum = 0.0;
    for (int i = 0; i < wheelRadians.length; i++) {
      wheelSum += Math.abs(wheelRadians[i] - startWheelRadians[i]);
    }
    double averageWheelRadians = wheelSum / wheelRadians.length;

    // 機器人轉了 gyroRadians，每個輪子走了 gyroRadians × DRIVE_BASE_RADIUS 公尺。
    // 輪子轉了 averageWheelRadians，所以半徑 = 走的距離 ÷ 轉的角度
    if (averageWheelRadians > 0.0) {
      radiusMeters = Math.abs(gyroRadians) * DRIVE_BASE_RADIUS / averageWheelRadians;
    }

    Telemetry.log("WheelRadius/GyroDegrees", Math.toDegrees(gyroRadians));
    Telemetry.log("WheelRadius/WheelDegrees", Math.toDegrees(averageWheelRadians));
    Telemetry.log("WheelRadius/RadiusMeters", radiusMeters);
  }

  @Override
  public void end(boolean interrupted) {
    drivetrain.stop();
    System.out.printf(
        "轉了 %.0f 度，輪子半徑 %.4f 公尺（直徑 %.4f 公尺）%n",
        Math.toDegrees(gyroRadians), radiusMeters, radiusMeters * 2);
  }
}
