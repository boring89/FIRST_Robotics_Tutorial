package first.robot;

import first.robot.Constants.OperatorConstants;
import first.robot.commands.TurnToAngle;
import first.robot.subsystems.Arm;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import first.robot.subsystems.Superstructure;
import first.robot.subsystems.Superstructure.Goal;
import first.robot.subsystems.Superstructure.State;
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
  private final Superstructure superstructure = new Superstructure(intake, arm, shooter);

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
    // Intake、Arm、Shooter 交給 Superstructure 控制，不設 default command
  }

  private void configureBindings() {
    driver.faceDown().whileTrue(superstructure.goalCommand(Goal.INTAKE));
    driver.rightBumper().whileTrue(superstructure.goalCommand(Goal.SCORE));
    driver.faceRight().whileTrue(superstructure.goalCommand(Goal.EJECT));

    driver.start().onTrue(drivetrain.resetHeadingCommand());
    driver.dpadUp().onTrue(new TurnToAngle(drivetrain, 0));
    driver.dpadLeft().onTrue(new TurnToAngle(drivetrain, 90));
    driver.dpadDown().onTrue(new TurnToAngle(drivetrain, 180));
    driver.dpadRight().onTrue(new TurnToAngle(drivetrain, -90));
    driver.leftBumper().whileTrue(
        drivetrain.arcadeDriveCommand(
            () -> -driver.getLeftY() * 0.3, () -> -driver.getRightX() * 0.3));

    new Trigger(() -> superstructure.getState() == State.HOLDING)
        .onTrue(Commands.print("拿到遊戲物件！"));
  }

  public Command getAutonomousCommand() {
    // 預載的遊戲物件：發射完（回到 EMPTY）才結束
    return superstructure.goalCommand(Goal.SCORE)
        .until(() -> superstructure.getState() == State.EMPTY)
        .withTimeout(3.0)
        .withName("ScorePreload");
  }
}
