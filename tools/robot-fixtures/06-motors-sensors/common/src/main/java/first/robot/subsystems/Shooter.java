package first.robot.subsystems;

import com.revrobotics.PersistMode;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkFlexConfig;
import first.robot.Constants.ShooterConstants;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.hardware.bus.CANPort;
import org.wpilib.telemetry.Telemetry;

public class Shooter extends SubsystemBase {
  private final SparkFlex motor =
      new SparkFlex(CANPort.CAN_S1, ShooterConstants.MOTOR_ID, MotorType.kBrushless);
  private final RelativeEncoder encoder = motor.getEncoder();

  public Shooter() {
    SparkFlexConfig config = new SparkFlexConfig();
    config
        .inverted(false)
        .idleMode(IdleMode.kCoast)
        .smartCurrentLimit(ShooterConstants.CURRENT_LIMIT);
    motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
  }

  /** 飛輪轉速，單位 RPM（飛輪直接裝在馬達上，齒輪比 1:1）。 */
  public double getRpm() {
    return encoder.getVelocity().get(0.0);
  }

  public boolean isAtSpeed() {
    return getRpm() >= ShooterConstants.MIN_SHOOT_RPM;
  }

  public Command spinCommand() {
    return startEnd(() -> motor.setThrottle(ShooterConstants.SHOOT_THROTTLE), motor::stopMotor)
        .withName("Spin");
  }

  @Override
  public void periodic() {
    Telemetry.log("Shooter/RPM", getRpm());
    Telemetry.log("Shooter/AtSpeed", isAtSpeed());
  }
}
