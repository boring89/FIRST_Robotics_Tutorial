package first.robot.commands;

import first.robot.Constants.SwerveConstants;
import first.robot.subsystems.Drivetrain;
import java.util.function.DoubleSupplier;
import org.wpilib.command2.Command;
import org.wpilib.math.controller.PIDController;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.ChassisVelocities;

/** 駕駛用的 Command：放開右搖桿時，自動保持機器人的朝向。 */
public class TeleopDrive extends Command {
  private final Drivetrain drivetrain;
  private final DoubleSupplier forward;
  private final DoubleSupplier left;
  private final DoubleSupplier rotation;
  private final PIDController headingController =
      new PIDController(SwerveConstants.HEADING_KP, 0.0, 0.0);

  /** 要保持的朝向（場地座標）。null 代表目前沒有鎖定。 */
  private Rotation2d targetHeading = null;

  /** 三個輸入的範圍都是 -1～1，和 Drivetrain.driveCommand 一樣。 */
  public TeleopDrive(
      Drivetrain drivetrain, DoubleSupplier forward, DoubleSupplier left, DoubleSupplier rotation) {
    this.drivetrain = drivetrain;
    this.forward = forward;
    this.left = left;
    this.rotation = rotation;
    headingController.enableContinuousInput(-Math.PI, Math.PI);
    addRequirements(drivetrain);
  }

  /**
   * 讓機器人轉到指定的朝向，之後一直保持。
   *
   * @param degrees 駕駛視角的角度：0 是遠離駕駛，90 是駕駛的左邊
   */
  public void snapTo(double degrees) {
    Rotation2d heading = Rotation2d.fromDegrees(degrees);
    targetHeading = drivetrain.isRedAlliance() ? heading.plus(Rotation2d.PI) : heading;
  }

  @Override
  public void initialize() {
    targetHeading = null;
  }

  @Override
  public void execute() {
    Rotation2d current = drivetrain.getPose().getRotation();
    double input = rotation.getAsDouble();
    double omega;

    if (input != 0.0) {
      // 駕駛在轉：照搖桿轉，取消鎖定
      targetHeading = null;
      omega = input * SwerveConstants.MAX_ANGULAR_SPEED;
    } else {
      // 駕駛放開右搖桿：等機器人轉慢下來，再鎖定當時的朝向
      double measuredOmega = drivetrain.getRobotRelativeVelocities().omega;
      if (targetHeading == null && Math.abs(measuredOmega) < SwerveConstants.HEADING_LOCK_SPEED) {
        targetHeading = current;
        headingController.reset();
      }
      omega = targetHeading == null
          ? 0.0
          : headingController.calculate(current.getRadians(), targetHeading.getRadians());
    }

    drivetrain.driveDriverRelative(
        new ChassisVelocities(
            forward.getAsDouble() * SwerveConstants.MAX_SPEED,
            left.getAsDouble() * SwerveConstants.MAX_SPEED,
            omega));
  }

  @Override
  public void end(boolean interrupted) {
    drivetrain.stop();
  }
}
