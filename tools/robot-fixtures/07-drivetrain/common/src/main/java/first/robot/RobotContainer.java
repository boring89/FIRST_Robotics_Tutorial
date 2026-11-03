package first.robot;

import first.robot.Constants.OperatorConstants;
import first.robot.commands.ScoringCommands;
import first.robot.subsystems.Arm;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.button.CommandGamepad;
import org.wpilib.command2.button.Trigger;
import org.wpilib.math.filter.SlewRateLimiter;

public class RobotContainer {
  private final Drivetrain drivetrain = new Drivetrain();
  private final Intake intake = new Intake();
  private final Shooter shooter = new Shooter();
  private final Arm arm = new Arm();

  private final CommandGamepad driver = new CommandGamepad(OperatorConstants.DRIVER_CONTROLLER_PORT);
  private final SlewRateLimiter forwardLimiter = new SlewRateLimiter(3.0);

  public RobotContainer() {
    configureDefaultCommands();
    configureBindings();
  }

  private void configureDefaultCommands() {
    drivetrain.setDefaultCommand(
        drivetrain.arcadeDriveCommand(
            () -> forwardLimiter.calculate(-driver.getLeftY()), () -> -driver.getRightX()));
    intake.setDefaultCommand(intake.stopCommand());
    arm.setDefaultCommand(arm.manualCommand(() -> -driver.getRightY() * 0.3));
  }

  private void configureBindings() {
    driver.faceDown().whileTrue(intake.intakeCommand());
    driver.faceRight().whileTrue(intake.ejectCommand());
    driver.rightBumper()
        .and(intake::hasGamePiece)
        .onTrue(ScoringCommands.shoot(intake, shooter));
    driver.start().onTrue(drivetrain.resetHeadingCommand());
    driver.leftBumper().whileTrue(
        drivetrain.arcadeDriveCommand(
            () -> -driver.getLeftY() * 0.3, () -> -driver.getRightX() * 0.3));

    new Trigger(intake::hasGamePiece)
        .debounce(0.1)
        .onTrue(Commands.print("拿到遊戲物件！"));
  }

  public Command getAutonomousCommand() {
    return ScoringCommands.shoot(intake, shooter)
        .andThen(intake.intakeCommand().withTimeout(3.0))
        .withName("ShootThenIntake");
  }
}
