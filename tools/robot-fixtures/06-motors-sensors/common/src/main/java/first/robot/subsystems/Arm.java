package first.robot.subsystems;

import com.revrobotics.AbsoluteEncoder;
import com.revrobotics.PersistMode;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import first.robot.Constants.ArmConstants;
import java.util.function.DoubleSupplier;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.hardware.bus.CANPort;
import org.wpilib.hardware.discrete.DigitalInput;
import org.wpilib.telemetry.Telemetry;

public class Arm extends SubsystemBase {
  private final SparkMax motor =
      new SparkMax(CANPort.CAN_S2, ArmConstants.MOTOR_ID, MotorType.kBrushless);
  private final AbsoluteEncoder absoluteEncoder = motor.getAbsoluteEncoder();
  private final DigitalInput bottomLimit = new DigitalInput(ArmConstants.BOTTOM_LIMIT_CHANNEL);

  public Arm() {
    SparkMaxConfig config = new SparkMaxConfig();
    config.idleMode(IdleMode.kBrake).smartCurrentLimit(ArmConstants.CURRENT_LIMIT);
    config.absoluteEncoder.zeroOffset(ArmConstants.ABSOLUTE_OFFSET_ROTATIONS);
    motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
  }

  /** 手臂角度，單位：度。Through Bore Encoder 裝在手臂的轉軸上。 */
  public double getAngleDegrees() {
    return absoluteEncoder.getPosition().get(0.0) * 360.0;
  }

  /** 手臂是否碰到底部的極限開關。實際讀值要看接法，必要時拿掉 ! 號。 */
  public boolean isAtBottom() {
    return !bottomLimit.get();
  }

  public void setThrottle(double throttle) {
    if (isAtBottom() && throttle < 0) {
      throttle = 0;
    }
    motor.setThrottle(throttle);
  }

  public void stop() {
    motor.stopMotor();
  }

  public Command manualCommand(DoubleSupplier throttle) {
    return runEnd(() -> setThrottle(throttle.getAsDouble()), this::stop).withName("ArmManual");
  }

  @Override
  public void periodic() {
    Telemetry.log("Arm/AngleDegrees", getAngleDegrees());
    Telemetry.log("Arm/AtBottom", isAtBottom());
  }
}
