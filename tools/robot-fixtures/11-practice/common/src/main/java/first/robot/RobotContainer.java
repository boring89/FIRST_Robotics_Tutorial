package first.robot;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.auto.NamedCommands;
import first.robot.Constants.ArmConstants;
import first.robot.Constants.OperatorConstants;
import first.robot.commands.AimAtTarget;
import first.robot.commands.Autos;
import first.robot.commands.ScoringCommands;
import first.robot.commands.TurnToAngle;
import first.robot.subsystems.Arm;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import first.robot.subsystems.Vision;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.button.CommandGamepad;
import org.wpilib.command2.button.Trigger;
import org.wpilib.math.filter.SlewRateLimiter;
import org.wpilib.tunable.Selectable;
import org.wpilib.tunable.Tunables;

public class RobotContainer {
  private final Drivetrain drivetrain = new Drivetrain();
  private final Vision vision = new Vision(drivetrain);
  private final Intake intake = new Intake();
  private final Shooter shooter = new Shooter();
  private final Arm arm = new Arm();

  private final CommandGamepad driver = new CommandGamepad(OperatorConstants.DRIVER_CONTROLLER_PORT);
  private final SlewRateLimiter forwardLimiter = new SlewRateLimiter(3.0);

  private final Selectable<Command> autoChooser;

  public RobotContainer() {
    // Named commands 一定要在建立自動程式之前註冊
    registerNamedCommands();
    autoChooser = buildAutoChooser();
    Tunables.publish("Auto Chooser", autoChooser);

    configureDefaultCommands();
    configureBindings();
  }

  private void registerNamedCommands() {
    NamedCommands.registerCommand("Shoot", ScoringCommands.shoot(intake, shooter));
    NamedCommands.registerCommand("Intake", intake.intakeCommand().withTimeout(3.0));
  }

  private Selectable<Command> buildAutoChooser() {
    Selectable<Command> chooser;
    if (AutoBuilder.isConfigured()) {
      chooser = AutoBuilder.buildAutoChooser();   // 包含所有 PathPlanner 的自動程式
    } else {
      chooser = new Selectable<>();
      chooser.addDefault("什麼都不做", Commands.none());
    }
    chooser.add("往前開 2 秒（時間）", Autos.driveForwardTimed(drivetrain));
    chooser.add("發射後後退（編碼器）", Autos.shootThenBackUp(drivetrain, intake, shooter));
    chooser.add("發射並撿下一個（編碼器）", Autos.shootAndPickUp(drivetrain, intake, shooter));
    return chooser;
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
    driver.rightTrigger().whileTrue(new AimAtTarget(drivetrain, vision, () -> -driver.getLeftY()));
    driver.leftBumper().whileTrue(
        drivetrain.arcadeDriveCommand(
            () -> -driver.getLeftY() * 0.3, () -> -driver.getRightX() * 0.3));

    driver.dpadUp().onTrue(new TurnToAngle(drivetrain, 0));
    driver.dpadLeft().onTrue(new TurnToAngle(drivetrain, 90));
    driver.dpadDown().onTrue(new TurnToAngle(drivetrain, 180));
    driver.dpadRight().onTrue(new TurnToAngle(drivetrain, -90));

    driver.faceUp().onTrue(arm.goToAngleCommand(ArmConstants.SCORE_ANGLE_DEGREES));
    driver.faceLeft().onTrue(arm.goToAngleCommand(ArmConstants.STOWED_ANGLE_DEGREES));
    new Trigger(() -> Math.abs(driver.getRightY()) > 0.2)
        .onTrue(arm.manualCommand(() -> -driver.getRightY() * 0.3));

    new Trigger(intake::hasGamePiece)
        .debounce(0.1)
        .onTrue(Commands.print("拿到遊戲物件！"));
  }

  public Command getAutonomousCommand() {
    return autoChooser.getSelected();
  }
}
