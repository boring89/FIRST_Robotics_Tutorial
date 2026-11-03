package first.robot.commands;

import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import org.wpilib.command2.Command;

public final class Autos {
  /** 往前開 2 秒，離開起始區。 */
  public static Command driveForwardTimed(Drivetrain drivetrain) {
    return drivetrain.arcadeDriveCommand(() -> 0.4, () -> 0.0)
        .withTimeout(2.0)
        .withName("DriveForwardTimed");
  }

  /** 發射預載的遊戲物件，往後走 1.5 公尺。 */
  public static Command shootThenBackUp(Drivetrain drivetrain, Intake intake, Shooter shooter) {
    return ScoringCommands.shoot(intake, shooter)
        .andThen(drivetrain.driveDistanceCommand(-1.5))
        .withName("ShootThenBackUp");
  }

  /** 發射、後退、轉身，一邊往前走一邊吸下一個遊戲物件。 */
  public static Command shootAndPickUp(Drivetrain drivetrain, Intake intake, Shooter shooter) {
    return ScoringCommands.shoot(intake, shooter)
        .andThen(drivetrain.driveDistanceCommand(-1.5))
        .andThen(new TurnToAngle(drivetrain, 180))
        .andThen(drivetrain.driveDistanceCommand(1.0).deadlineFor(intake.intakeCommand()))
        .withName("ShootAndPickUp");
  }

  private Autos() {}
}
