package first.robot.commands;

import first.robot.Constants.ArmConstants;
import first.robot.Constants.ShooterConstants;
import first.robot.subsystems.Arm;
import first.robot.subsystems.Intake;
import first.robot.subsystems.Shooter;
import org.wpilib.command2.Command;
import org.wpilib.command2.Command.InterruptionBehavior;
import org.wpilib.command2.Commands;

public final class ScoringCommands {
  /** 手臂收起之後才開始吸，拿到遊戲物件為止。 */
  public static Command intake(Intake intake, Arm arm) {
    return Commands.waitUntil(() -> arm.isAtAngle(ArmConstants.STOWED_ANGLE_DEGREES))
        .andThen(intake.intakeCommand())
        .deadlineFor(arm.goToAngleCommand(ArmConstants.STOWED_ANGLE_DEGREES))
        .withName("IntakeSequence");
  }

  /** 手臂轉到發射角度、飛輪轉到夠快，兩個都到了才送料。 */
  public static Command score(Intake intake, Arm arm, Shooter shooter) {
    return Commands.waitUntil(
            () -> arm.isAtAngle(ArmConstants.SCORE_ANGLE_DEGREES) && shooter.isAtSpeed())
        .andThen(intake.feedCommand().withTimeout(ShooterConstants.FEED_SECONDS))
        .deadlineFor(
            arm.goToAngleCommand(ArmConstants.SCORE_ANGLE_DEGREES), shooter.spinCommand())
        .withInterruptBehavior(InterruptionBehavior.CANCEL_INCOMING)
        .withName("ScoreSequence");
  }

  private ScoringCommands() {}
}
