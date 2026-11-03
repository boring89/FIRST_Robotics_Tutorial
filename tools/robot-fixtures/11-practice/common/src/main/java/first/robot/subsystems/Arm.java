package first.robot.subsystems;

import com.revrobotics.AbsoluteEncoder;
import com.revrobotics.PersistMode;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.ClosedLoopSlot;
import com.revrobotics.spark.FeedbackSensor;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkClosedLoopController.ArbFFUnits;
import com.revrobotics.spark.SparkLowLevel.ControlType;
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
import org.wpilib.math.controller.ArmFeedforward;
import org.wpilib.telemetry.Telemetry;

public class Arm extends SubsystemBase {
  private final SparkMax motor =
      new SparkMax(CANPort.CAN_S2, ArmConstants.MOTOR_ID, MotorType.kBrushless);
  private final AbsoluteEncoder absoluteEncoder = motor.getAbsoluteEncoder();
  private final SparkClosedLoopController controller = motor.getClosedLoopController();
  private final DigitalInput bottomLimit = new DigitalInput(ArmConstants.BOTTOM_LIMIT_CHANNEL);
  private final ArmFeedforward gravity = new ArmFeedforward(0.0, ArmConstants.KG, 0.0);

  public Arm() {
    SparkMaxConfig config = new SparkMaxConfig();
    config.idleMode(IdleMode.kBrake).smartCurrentLimit(ArmConstants.CURRENT_LIMIT);
    config.absoluteEncoder.zeroOffset(ArmConstants.ABSOLUTE_OFFSET_ROTATIONS);
    config.closedLoop
        .feedbackSensor(FeedbackSensor.kAbsoluteEncoder)
        .p(ArmConstants.KP)
        .d(ArmConstants.KD);
    config.closedLoop.feedForward.kV(ArmConstants.KV);
    config.closedLoop.maxMotion
        .cruiseVelocity(ArmConstants.CRUISE_VELOCITY)
        .maxAcceleration(ArmConstants.MAX_ACCELERATION);
    motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
  }

  /** 手臂角度，單位：度，水平為 0。Through Bore Encoder 裝在手臂的轉軸上，零點設在收起的位置。 */
  public double getAngleDegrees() {
    return absoluteEncoder.getPosition().get(0.0) * 360.0 + ArmConstants.STOWED_ANGLE_DEGREES;
  }

  public boolean isAtBottom() {
    return !bottomLimit.get();
  }

  public boolean isAtAngle(double degrees) {
    return Math.abs(getAngleDegrees() - degrees) < ArmConstants.TOLERANCE_DEGREES;
  }

  /** 角度（水平為 0）換算成編碼器的圈數（收起為 0）。 */
  private static double toEncoderRotations(double degrees) {
    return (degrees - ArmConstants.STOWED_ANGLE_DEGREES) / 360.0;
  }

  /** 撐住手臂需要的電壓，跟著目前的角度變化。 */
  private double gravityVolts() {
    return gravity.calculate(Math.toRadians(getAngleDegrees()), 0.0);
  }

  /** 用 MAXMotion 轉到指定角度，並一直停在那裡。 */
  public Command goToAngleCommand(double degrees) {
    double target = Math.clamp(degrees, ArmConstants.STOWED_ANGLE_DEGREES, ArmConstants.MAX_ANGLE_DEGREES);
    return run(() -> controller.setSetpoint(
            toEncoderRotations(target),
            ControlType.kMAXMotionPositionControl,
            ClosedLoopSlot.kSlot0,
            gravityVolts(),
            ArbFFUnits.kVoltage))
        .withName("ArmToAngle");
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
