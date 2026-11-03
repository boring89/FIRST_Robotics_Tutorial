package first.robot.commands;

import first.robot.Constants.ShooterConstants;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;

public final class ScoringCommands {
  /** Shooter 轉到夠快（最多等 2 秒）後，Intake 送料 0.5 秒。 */
  public static Command shoot(Intake intake, Shooter shooter) {
    return Commands.waitUntil(shooter::isAtSpeed)
        .withTimeout(ShooterConstants.SPIN_UP_TIMEOUT_SECONDS)
        .andThen(intake.feedCommand().withTimeout(ShooterConstants.FEED_SECONDS))
        .deadlineFor(shooter.spinCommand())
        .withName("Shoot");
  }

  private ScoringCommands() {}
}
