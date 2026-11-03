package first.robot.subsystems;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.config.RobotConfig;
import com.pathplanner.lib.controllers.PPLTVController;
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
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchState;
import org.wpilib.hardware.bus.CANPort;
import org.wpilib.hardware.imu.OnboardIMU;
import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;
import org.wpilib.math.controller.SimpleMotorFeedforward;
import org.wpilib.math.estimator.DifferentialDrivePoseEstimator;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.DifferentialDriveKinematics;
import org.wpilib.math.kinematics.DifferentialDriveWheelVelocities;
import org.wpilib.math.linalg.Matrix;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N3;
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
  private final DifferentialDriveKinematics kinematics =
      new DifferentialDriveKinematics(DrivetrainConstants.TRACK_WIDTH_METERS);
  private final SimpleMotorFeedforward feedforward =
      new SimpleMotorFeedforward(DrivetrainConstants.KS, DrivetrainConstants.KV);
  private final DifferentialDrivePoseEstimator poseEstimator;

  private double startDistance = 0.0;

  public Drivetrain() {
    configure(leftLeader, baseConfig());
    configure(rightLeader, baseConfig().inverted(true));
    configure(leftFollower, baseConfig().follow(leftLeader));
    configure(rightFollower, baseConfig().follow(rightLeader));

    poseEstimator = new DifferentialDrivePoseEstimator(
        getHeading(), getLeftDistanceMeters(), getRightDistanceMeters(), new Pose2d());
    configurePathPlanner();
  }

  private static SparkMaxConfig baseConfig() {
    SparkMaxConfig config = new SparkMaxConfig();
    config.idleMode(IdleMode.kBrake).smartCurrentLimit(DrivetrainConstants.CURRENT_LIMIT);
    return config;
  }

  private static void configure(SparkMax motor, SparkBaseConfig config) {
    motor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
  }

  private void configurePathPlanner() {
    RobotConfig config;
    try {
      config = RobotConfig.fromGUISettings();
    } catch (Exception e) {
      System.err.println("無法讀取 PathPlanner 的機器人設定：" + e.getMessage());
      return;
    }
    AutoBuilder.configure(
        this::getPose,
        this::resetPose,
        this::getRobotRelativeVelocities,
        (velocities, feedforwards) -> driveRobotRelative(velocities),
        new PPLTVController(0.02),
        config,
        () -> MatchState.getAlliance().orElse(Alliance.BLUE) == Alliance.RED,
        this);
  }

  // ---------- 單位換算 ----------

  private static double rotationsToMeters(double motorRotations) {
    return motorRotations / DrivetrainConstants.GEAR_RATIO
        * Math.PI * DrivetrainConstants.WHEEL_DIAMETER_METERS;
  }

  private static double rpmToMetersPerSecond(double motorRpm) {
    return rotationsToMeters(motorRpm / 60.0);
  }

  // ---------- 感測器 ----------

  public double getLeftDistanceMeters() {
    return rotationsToMeters(leftEncoder.getPosition().get(0.0));
  }

  public double getRightDistanceMeters() {
    return rotationsToMeters(rightEncoder.getPosition().get(0.0));
  }

  public double getDistanceMeters() {
    return (getLeftDistanceMeters() + getRightDistanceMeters()) / 2;
  }

  /** 機器人的朝向，逆時針為正。 */
  public Rotation2d getHeading() {
    return imu.getRotation2d();
  }

  public Pose2d getPose() {
    return poseEstimator.getEstimatedPosition();
  }

  public void resetPose(Pose2d pose) {
    poseEstimator.resetPose(pose);
  }

  /** 加入視覺算出的位置。 */
  public void addVisionMeasurement(Pose2d pose, double timestampSeconds, Matrix<N3, N1> stdDevs) {
    poseEstimator.addVisionMeasurement(pose, timestampSeconds, stdDevs);
  }

  public ChassisVelocities getRobotRelativeVelocities() {
    return kinematics.toChassisVelocities(
        new DifferentialDriveWheelVelocities(
            rpmToMetersPerSecond(leftEncoder.getVelocity().get(0.0)),
            rpmToMetersPerSecond(rightEncoder.getVelocity().get(0.0))));
  }

  // ---------- 控制 ----------

  public void arcadeDrive(double forward, double rotation) {
    drive.arcadeDrive(forward, rotation, false);
  }

  /** 用指定的速度移動（PathPlanner 使用）。 */
  public void driveRobotRelative(ChassisVelocities velocities) {
    DifferentialDriveWheelVelocities wheels = kinematics.toWheelVelocities(velocities);
    leftLeader.setVoltage(feedforward.calculate(wheels.left));
    rightLeader.setVoltage(feedforward.calculate(wheels.right));
    drive.feed();   // 告訴 Motor Safety 底盤有在被控制
  }

  // ---------- Command ----------

  /** 用 Arcade drive 開車：forward 正值往前，rotation 正值往左轉。 */
  public Command arcadeDriveCommand(DoubleSupplier forward, DoubleSupplier rotation) {
    return run(() -> drive.arcadeDrive(forward.getAsDouble(), rotation.getAsDouble()))
        .withName("ArcadeDrive");
  }

  /** 往前（正）或往後（負）走指定的距離，單位：公尺。 */
  public Command driveDistanceCommand(double meters) {
    double direction = Math.signum(meters);
    return runOnce(() -> startDistance = getDistanceMeters())
        .andThen(run(() -> arcadeDrive(0.4 * direction, 0.0)))
        .until(() -> Math.abs(getDistanceMeters() - startDistance) >= Math.abs(meters))
        .finallyDo(() -> arcadeDrive(0.0, 0.0))
        .withName("DriveDistance");
  }

  public Command resetHeadingCommand() {
    return runOnce(imu::resetYaw).ignoringDisable(true).withName("ResetHeading");
  }

  @Override
  public void periodic() {
    poseEstimator.update(getHeading(), getLeftDistanceMeters(), getRightDistanceMeters());
    Telemetry.log("Drivetrain/Pose", getPose());
    Telemetry.log("Drivetrain/HeadingDegrees", getHeading().getDegrees());
  }
}
