package first.robot;

import com.pathplanner.lib.auto.AutoBuilder;
import first.robot.Constants.OperatorConstants;
import first.robot.commands.AimAtTarget;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Vision;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.button.CommandGamepad;
import org.wpilib.math.util.MathUtil;
import org.wpilib.tunable.Selectable;
import org.wpilib.tunable.Tunables;

public class RobotContainer {
  private final Drivetrain drivetrain = new Drivetrain();
  private final Vision vision = new Vision(drivetrain);

  private final CommandGamepad driver = new CommandGamepad(OperatorConstants.DRIVER_CONTROLLER_PORT);

  private final Selectable<Command> autoChooser;

  public RobotContainer() {
    autoChooser = buildAutoChooser();
    Tunables.publish("Auto Chooser", autoChooser);

    configureDefaultCommands();
    configureBindings();
  }

  private Selectable<Command> buildAutoChooser() {
    if (AutoBuilder.isConfigured()) {
      return AutoBuilder.buildAutoChooser();   // 包含所有 PathPlanner 的自動程式
    }
    Selectable<Command> chooser = new Selectable<>();
    chooser.addDefault("什麼都不做", Commands.none());
    return chooser;
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
    // 按住右扳機時，一邊移動一邊對準目標
    driver.rightTrigger().whileTrue(
        new AimAtTarget(
            drivetrain, vision, () -> shape(-driver.getLeftY()), () -> shape(-driver.getLeftX())));
  }

  public Command getAutonomousCommand() {
    return autoChooser.getSelected();
  }
}
