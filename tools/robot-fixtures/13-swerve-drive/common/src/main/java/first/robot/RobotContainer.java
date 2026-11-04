package first.robot;

import first.robot.Constants.OperatorConstants;
import first.robot.subsystems.Drivetrain;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.button.CommandGamepad;
import org.wpilib.math.util.MathUtil;

public class RobotContainer {
  private final Drivetrain drivetrain = new Drivetrain();

  private final CommandGamepad driver = new CommandGamepad(OperatorConstants.DRIVER_CONTROLLER_PORT);

  public RobotContainer() {
    configureDefaultCommands();
    configureBindings();
  }

  /** 搖桿放開時不一定剛好是 0，中間 10% 當作 0。 */
  private static double deadband(double input) {
    return MathUtil.applyDeadband(input, 0.1);
  }

  private void configureDefaultCommands() {
    // 搖桿往前推是負值、往右推是正值，所以三個都要加負號
    drivetrain.setDefaultCommand(
        drivetrain.driveCommand(
            () -> deadband(-driver.getLeftY()),
            () -> deadband(-driver.getLeftX()),
            () -> deadband(-driver.getRightX())));
  }

  private void configureBindings() {
    driver.start().onTrue(drivetrain.resetHeadingCommand());
  }

  public Command getAutonomousCommand() {
    return Commands.none();
  }
}
