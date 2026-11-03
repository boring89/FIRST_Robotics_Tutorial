package first.robot.commands;

import first.robot.Constants.ShooterConstants;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;

public final class ScoringCommands {
  public static Command shoot(Intake intake, Shooter shooter) {
    return Commands.waitSeconds(ShooterConstants.SPIN_UP_SECONDS)
        .andThen(intake.feedCommand().withTimeout(ShooterConstants.FEED_SECONDS))
        .deadlineFor(shooter.spinCommand())
        .withName("Shoot");
  }

  private ScoringCommands() {}
}
