package first.robot.subsystems;

import com.revrobotics.PersistMode;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import first.robot.Constants.DrivetrainConstants;
import java.util.function.DoubleSupplier;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.drive.DifferentialDrive;
import org.wpilib.hardware.bus.CANPort;
import org.wpilib.hardware.imu.OnboardIMU;
import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.telemetry.Telemetry;

public class Drivetrain extends SubsystemBase {
  private final SparkMax leftLeader =
      new SparkMax(CANPort.CAN_S0, DrivetrainConstants.LEFT_LEADER_ID, MotorType.kBrushless);
  private final SparkMax leftFollower =
      new SparkMax(CANPort.CAN_S0, DrivetrainConstants.LEFT_FOLLOWER_ID, MotorType.kBrushless);
  private final SparkMax rightLeader =
      new SparkMax(CANPort.CAN_S0, DrivetrainConstants.RIGHT_LEADER_ID, MotorType.kBrushless);
  private final SparkMax rightFollower =
      new SparkMax(CANPort.CAN_S0, DrivetrainConstants.RIGHT_FOLLOWER_ID, MotorType.kBrushless);

  private final RelativeEncoder leftEncoder = leftLeader.getEncoder();
  private final RelativeEncoder rightEncoder = rightLeader.getEncoder();
  private final OnboardIMU imu = new OnboardIMU(MountOrientation.FLAT);

  private final DifferentialDrive drive =
      new DifferentialDrive(leftLeader::setThrottle, rightLeader::setThrottle);

  public Drivetrain() {
    configure(leftLeader, baseConfig());
    configure(rightLeader, baseConfig().inverted(true));
    configure(leftFollower, baseConfig().follow(leftLeader));
    configure(rightFollower, baseConfig().follow(rightLeader));
  }

  private static SparkMaxConfig baseConfig() {
    SparkMaxConfig config = new SparkMaxConfig();
    config.idleMode(IdleMode.kBrake).smartCurrentLimit(DrivetrainConstants.CURRENT_LIMIT);
    return config;
  }

  private static void configure(SparkMax motor, SparkBaseConfig config) {
    motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
  }

  /** 左右輪平均走了多遠，單位：公尺。 */
  public double getDistanceMeters() {
    double rotations = (leftEncoder.getPosition().get(0.0) + rightEncoder.getPosition().get(0.0)) / 2;
    return rotations / DrivetrainConstants.GEAR_RATIO * Math.PI * DrivetrainConstants.WHEEL_DIAMETER_METERS;
  }

  public void resetEncoders() {
    leftEncoder.setPosition(0);
    rightEncoder.setPosition(0);
  }

  /** 機器人的朝向，逆時針為正。 */
  public Rotation2d getHeading() {
    return imu.getRotation2d();
  }

  /** 用 Arcade drive 開車：forward 正值往前，rotation 正值往左轉。 */
  public Command arcadeDriveCommand(DoubleSupplier forward, DoubleSupplier rotation) {
    return run(() -> drive.arcadeDrive(forward.getAsDouble(), rotation.getAsDouble()))
        .withName("ArcadeDrive");
  }

  public Command resetHeadingCommand() {
    return runOnce(imu::resetYaw).ignoringDisable(true).withName("ResetHeading");
  }

  @Override
  public void periodic() {
    Telemetry.log("Drivetrain/DistanceMeters", getDistanceMeters());
    Telemetry.log("Drivetrain/HeadingDegrees", getHeading().getDegrees());
  }
}
