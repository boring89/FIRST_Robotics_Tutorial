package first.robot.commands;

import first.robot.Constants.SwerveConstants;
import first.robot.Constants.VisionConstants;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Vision;
import java.util.function.DoubleSupplier;
import org.wpilib.command2.Command;
import org.wpilib.math.controller.PIDController;
import org.wpilib.math.kinematics.ChassisVelocities;

/** 駕駛控制移動的方向，機器人自動轉向對準目標。 */
public class AimAtTarget extends Command {
  private final Drivetrain drivetrain;
  private final Vision vision;
  private final DoubleSupplier forward;
  private final DoubleSupplier left;
  private final PIDController controller = new PIDController(VisionConstants.AIM_KP, 0.0, 0.0);

  public AimAtTarget(Drivetrain drivetrain, Vision vision, DoubleSupplier forward, DoubleSupplier left) {
    this.drivetrain = drivetrain;
    this.vision = vision;
    this.forward = forward;
    this.left = left;
    controller.setSetpoint(0.0);
    controller.setTolerance(VisionConstants.AIM_TOLERANCE_DEGREES);
    addRequirements(drivetrain);   // Vision 只是讀資料，不需要佔用
  }

  @Override
  public void initialize() {
    controller.reset();
  }

  @Override
  public void execute() {
    double omega = 0.0;
    if (vision.hasTarget()) {
      // 目標在右邊（tx 為正）時，PID 輸出負值，機器人順時針轉
      double limit = SwerveConstants.MAX_ANGULAR_SPEED * 0.5;
      omega = Math.clamp(controller.calculate(vision.getTXDegrees()), -limit, limit);
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
