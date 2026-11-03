package first.robot.subsystems;

import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import first.robot.Constants.ShooterConstants;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.hardware.bus.CANPort;

public class Shooter extends SubsystemBase {
  private final SparkFlex motor =
      new SparkFlex(CANPort.CAN_S1, ShooterConstants.MOTOR_ID, MotorType.kBrushless);

  public Command spinCommand() {
    return startEnd(() -> motor.setThrottle(ShooterConstants.SHOOT_THROTTLE), motor::stopMotor)
        .withName("Spin");
  }
}
