package first.robot.subsystems;

import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import first.robot.Constants.IntakeConstants;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.hardware.bus.CANPort;
import org.wpilib.hardware.discrete.DigitalInput;
import org.wpilib.telemetry.Telemetry;

public class Intake extends SubsystemBase {
  private final SparkMax motor =
      new SparkMax(CANPort.CAN_S1, IntakeConstants.MOTOR_ID, MotorType.kBrushless);
  private final DigitalInput beamBreak = new DigitalInput(IntakeConstants.BEAM_BREAK_CHANNEL);

  public void setThrottle(double throttle) {
    motor.setThrottle(throttle);
  }

  public void stop() {
    motor.stopMotor();
  }

  public boolean hasGamePiece() {
    return !beamBreak.get();
  }

  public Command intakeCommand() {
    return startEnd(() -> setThrottle(IntakeConstants.INTAKE_THROTTLE), this::stop)
        .until(this::hasGamePiece)
        .withName("Intake");
  }

  public Command ejectCommand() {
    return startEnd(() -> setThrottle(IntakeConstants.EJECT_THROTTLE), this::stop)
        .withName("Eject");
  }

  public Command feedCommand() {
    return startEnd(() -> setThrottle(IntakeConstants.INTAKE_THROTTLE), this::stop)
        .withName("Feed");
  }

  public Command stopCommand() {
    return run(this::stop).withName("Stop");
  }

  @Override
  public void periodic() {
    Telemetry.log("Intake/HasGamePiece", hasGamePiece());
  }
}
