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

  /** 搖桿中間 10% 不動作，其他部分取平方：推一點點時更好微調。 */
  private static double shape(double input) {
    return MathUtil.copyDirectionPow(MathUtil.applyDeadband(input, 0.1), 2);
  }

  private void configureDefaultCommands() {
    drivetrain.setDefaultCommand(
        drivetrain.driveCommand(
            () -> shape(-driver.getLeftY()),
            () -> shape(-driver.getLeftX()),
            () -> shape(-driver.getRightX())));
  }

  private void configureBindings() {
    driver.start().onTrue(drivetrain.resetHeadingCommand());
    driver.faceLeft().whileTrue(drivetrain.lockCommand());
    // 按住左肩鍵時，速度降到 30%
    driver.leftBumper().whileTrue(
        drivetrain.driveCommand(
            () -> shape(-driver.getLeftY()) * 0.3,
            () -> shape(-driver.getLeftX()) * 0.3,
            () -> shape(-driver.getRightX()) * 0.3));
  }

  public Command getAutonomousCommand() {
    return Commands.none();
  }
}
