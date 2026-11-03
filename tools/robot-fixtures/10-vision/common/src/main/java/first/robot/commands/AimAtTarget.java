package first.robot.commands;

import first.robot.Constants.VisionConstants;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Vision;
import java.util.function.DoubleSupplier;
import org.wpilib.command2.Command;
import org.wpilib.math.controller.PIDController;

/** 一邊讓駕駛控制前進後退，一邊自動轉向對準目標。 */
public class AimAtTarget extends Command {
  private final Drivetrain drivetrain;
  private final Vision vision;
  private final DoubleSupplier forward;
  private final PIDController controller = new PIDController(VisionConstants.AIM_KP, 0.0, 0.0);

  public AimAtTarget(Drivetrain drivetrain, Vision vision, DoubleSupplier forward) {
    this.drivetrain = drivetrain;
    this.vision = vision;
    this.forward = forward;
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
    double rotation = 0.0;
    if (vision.hasTarget()) {
      rotation = Math.clamp(controller.calculate(vision.getTXDegrees()), -0.5, 0.5);
    }
    drivetrain.arcadeDrive(forward.getAsDouble(), rotation);
  }

  @Override
  public void end(boolean interrupted) {
    drivetrain.arcadeDrive(0.0, 0.0);
  }
}
