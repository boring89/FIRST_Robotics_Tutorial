package first.robot.subsystems;

import first.robot.Constants.ArmConstants;
import first.robot.Constants.IntakeConstants;
import first.robot.Constants.ShooterConstants;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.system.Timer;
import org.wpilib.telemetry.Telemetry;

/** 協調 Intake、Arm、Shooter 的狀態機。 */
public class Superstructure extends SubsystemBase {
  /** 想要的狀態：駕駛（或自動程式）希望機器人做什麼。 */
  public enum Goal {
    IDLE,
    INTAKE,
    SCORE,
    EJECT
  }

  /** 目前的狀態：機器人實際上正在做什麼。 */
  public enum State {
    EMPTY,
    INTAKING,
    HOLDING,
    PREPARING,
    SHOOTING,
    EJECTING
  }

  /** 判斷要不要轉換狀態時，需要知道的感測器資料。 */
  public record Inputs(
      boolean hasGamePiece,
      boolean armAtStow,
      boolean armAtScore,
      boolean shooterAtSpeed,
      double secondsInState) {}

  private final Intake intake;
  private final Arm arm;
  private final Shooter shooter;

  private Goal goal = Goal.IDLE;
  private State state = State.EMPTY;
  private final Timer stateTimer = new Timer();

  public Superstructure(Intake intake, Arm arm, Shooter shooter) {
    this.intake = intake;
    this.arm = arm;
    this.shooter = shooter;
    stateTimer.start();
  }

  /** 狀態轉換：只看目前的狀態、想要的狀態和感測器，不碰任何硬體。 */
  public static State nextState(State state, Goal goal, Inputs in) {
    return switch (state) {
      case EMPTY -> {
        if (in.hasGamePiece()) yield State.HOLDING;
        if (goal == Goal.INTAKE && in.armAtStow()) yield State.INTAKING;
        if (goal == Goal.EJECT) yield State.EJECTING;
        yield State.EMPTY;
      }
      case INTAKING -> {
        if (in.hasGamePiece()) yield State.HOLDING;
        if (goal != Goal.INTAKE) yield State.EMPTY;
        yield State.INTAKING;
      }
      case HOLDING -> {
        if (!in.hasGamePiece()) yield State.EMPTY;
        if (goal == Goal.SCORE) yield State.PREPARING;
        if (goal == Goal.EJECT) yield State.EJECTING;
        yield State.HOLDING;
      }
      case PREPARING -> {
        if (!in.hasGamePiece()) yield State.EMPTY;
        if (goal != Goal.SCORE) yield State.HOLDING;
        if (in.armAtScore() && in.shooterAtSpeed()) yield State.SHOOTING;
        yield State.PREPARING;
      }
      // 開始送料之後就一定做完，放開按鈕也不會停在一半
      case SHOOTING -> in.secondsInState() >= ShooterConstants.FEED_SECONDS ? State.EMPTY : State.SHOOTING;
      case EJECTING -> {
        if (goal != Goal.EJECT) yield in.hasGamePiece() ? State.HOLDING : State.EMPTY;
        yield State.EJECTING;
      }
    };
  }

  /** 每個狀態下，三個機構各自要做什麼。 */
  private void applyOutputs() {
    switch (state) {
      case EMPTY, HOLDING -> {
        arm.setTargetAngle(ArmConstants.STOWED_ANGLE_DEGREES);
        intake.stop();
        shooter.stop();
      }
      case INTAKING -> {
        arm.setTargetAngle(ArmConstants.STOWED_ANGLE_DEGREES);
        intake.setThrottle(IntakeConstants.INTAKE_THROTTLE);
        shooter.stop();
      }
      case PREPARING -> {
        arm.setTargetAngle(ArmConstants.SCORE_ANGLE_DEGREES);
        intake.stop();
        shooter.spin();
      }
      case SHOOTING -> {
        arm.setTargetAngle(ArmConstants.SCORE_ANGLE_DEGREES);
        intake.setThrottle(IntakeConstants.INTAKE_THROTTLE);
        shooter.spin();
      }
      case EJECTING -> {
        arm.setTargetAngle(ArmConstants.STOWED_ANGLE_DEGREES);
        intake.setThrottle(IntakeConstants.EJECT_THROTTLE);
        shooter.stop();
      }
    }
  }

  @Override
  public void periodic() {
    Inputs inputs = new Inputs(
        intake.hasGamePiece(),
        arm.isAtAngle(ArmConstants.STOWED_ANGLE_DEGREES),
        arm.isAtAngle(ArmConstants.SCORE_ANGLE_DEGREES),
        shooter.isAtSpeed(),
        stateTimer.get());

    State next = nextState(state, goal, inputs);
    if (next != state) {
      state = next;
      stateTimer.reset();
    }
    applyOutputs();

    Telemetry.log("Superstructure/Goal", goal.name());
    Telemetry.log("Superstructure/State", state.name());
  }

  public State getState() {
    return state;
  }

  /** 執行期間把想要的狀態設成 wanted，結束時回到 IDLE。 */
  public Command goalCommand(Goal wanted) {
    return startEnd(() -> goal = wanted, () -> goal = Goal.IDLE).withName("Goal " + wanted);
  }
}
