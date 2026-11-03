package first.robot.commands;

import first.robot.Constants.DrivetrainConstants;
import first.robot.subsystems.Drivetrain;
import org.wpilib.command2.Command;
import org.wpilib.math.controller.PIDController;

/** 原地轉到指定的朝向（場地座標，逆時針為正）。 */
public class TurnToAngle extends Command {
  private final Drivetrain drivetrain;
  private final PIDController controller =
      new PIDController(DrivetrainConstants.TURN_KP, 0.0, DrivetrainConstants.TURN_KD);

  public TurnToAngle(Drivetrain drivetrain, double targetDegrees) {
    this.drivetrain = drivetrain;
    controller.setSetpoint(targetDegrees);
    controller.enableContinuousInput(-180, 180);
    controller.setTolerance(DrivetrainConstants.TURN_TOLERANCE_DEGREES);
    addRequirements(drivetrain);
  }

  @Override
  public void initialize() {
    controller.reset();
  }

  @Override
  public void execute() {
    double output = controller.calculate(drivetrain.getHeading().getDegrees());
    output = Math.clamp(output, -DrivetrainConstants.TURN_MAX_OUTPUT, DrivetrainConstants.TURN_MAX_OUTPUT);
    drivetrain.arcadeDrive(0.0, output);
  }

  @Override
  public boolean isFinished() {
    return controller.atSetpoint();
  }

  @Override
  public void end(boolean interrupted) {
    drivetrain.arcadeDrive(0.0, 0.0);
  }
}
