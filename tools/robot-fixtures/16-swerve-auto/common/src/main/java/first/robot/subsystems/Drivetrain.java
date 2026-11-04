package first.robot.subsystems;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.config.PIDConstants;
import com.pathplanner.lib.config.RobotConfig;
import com.pathplanner.lib.controllers.PPHolonomicDriveController;
import first.robot.Constants.AutoConstants;
import first.robot.Constants.SwerveConstants;
import java.util.function.DoubleSupplier;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.driverstation.Alliance;
import org.wpilib.driverstation.MatchState;
import org.wpilib.hardware.imu.OnboardIMU;
import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;
import org.wpilib.math.estimator.SwerveDrivePoseEstimator;
import org.wpilib.math.geometry.Pose2d;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.SwerveDriveKinematics;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
import org.wpilib.math.linalg.Matrix;
import org.wpilib.math.numbers.N1;
import org.wpilib.math.numbers.N3;
import org.wpilib.simulation.OnboardIMUSim;
import org.wpilib.telemetry.Telemetry;

public class Drivetrain extends SubsystemBase {
  // 順序固定是左前、右前、左後、右後，Kinematics 也用同樣的順序
  private final SwerveModule[] modules = {
    new SwerveModule(SwerveConstants.FRONT_LEFT),
    new SwerveModule(SwerveConstants.FRONT_RIGHT),
    new SwerveModule(SwerveConstants.BACK_LEFT),
    new SwerveModule(SwerveConstants.BACK_RIGHT),
  };

  private final SwerveDriveKinematics kinematics =
      new SwerveDriveKinematics(
          SwerveConstants.FRONT_LEFT.location(),
          SwerveConstants.FRONT_RIGHT.location(),
          SwerveConstants.BACK_LEFT.location(),
          SwerveConstants.BACK_RIGHT.location());

  private final OnboardIMU imu = new OnboardIMU(MountOrientation.FLAT);
  private double simYawRadians = 0.0;

  private final SwerveDrivePoseEstimator poseEstimator;

  public Drivetrain() {
    poseEstimator =
        new SwerveDrivePoseEstimator(kinematics, getHeading(), getModulePositions(), Pose2d.ZERO);
    configurePathPlanner();
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
        new PPHolonomicDriveController(
            new PIDConstants(AutoConstants.TRANSLATION_KP, 0.0, 0.0),
            new PIDConstants(AutoConstants.ROTATION_KP, 0.0, 0.0)),
        config,
        this::isRedAlliance,
        this);
  }

  // ---------- 感測器 ----------

  /** IMU 讀到的朝向，逆時針為正。只給 Odometry 用，其他地方用 getPose().getRotation()。 */
  public Rotation2d getHeading() {
    return imu.getRotation2d();
  }

  /** 四個模組目前的速度和角度。 */
  public SwerveModuleVelocity[] getModuleVelocities() {
    SwerveModuleVelocity[] velocities = new SwerveModuleVelocity[modules.length];
    for (int i = 0; i < modules.length; i++) {
      velocities[i] = modules[i].getVelocity();
    }
    return velocities;
  }

  /** 四個模組走過的距離和角度。 */
  public SwerveModulePosition[] getModulePositions() {
    SwerveModulePosition[] positions = new SwerveModulePosition[modules.length];
    for (int i = 0; i < modules.length; i++) {
      positions[i] = modules[i].getPosition();
    }
    return positions;
  }

  /** 機器人在場地上的位置和朝向。 */
  public Pose2d getPose() {
    return poseEstimator.getEstimatedPosition();
  }

  public void resetPose(Pose2d pose) {
    poseEstimator.resetPose(pose);
  }

  /** 加入一筆視覺算出的位置。 */
  public void addVisionMeasurement(Pose2d pose, double timestampSeconds, Matrix<N3, N1> stdDevs) {
    poseEstimator.addVisionMeasurement(pose, timestampSeconds, stdDevs);
  }

  /** 機器人座標的速度（PathPlanner 使用）。 */
  public ChassisVelocities getRobotRelativeVelocities() {
    return kinematics.toChassisVelocities(getModuleVelocities());
  }

  public boolean isRedAlliance() {
    return MatchState.getAlliance().orElse(Alliance.BLUE) == Alliance.RED;
  }

  // ---------- 控制 ----------

  /** 用機器人座標的速度移動：vx 往前、vy 往左、omega 逆時針。 */
  public void driveRobotRelative(ChassisVelocities velocities) {
    // 修正「一邊走一邊轉」時往旁邊偏的問題
    velocities = velocities.discretize(0.02);
    SwerveModuleVelocity[] targets = kinematics.toSwerveModuleVelocities(velocities);
    targets = SwerveDriveKinematics.desaturateWheelVelocities(targets, SwerveConstants.MAX_SPEED);
    for (int i = 0; i < modules.length; i++) {
      modules[i].setTarget(targets[i]);
    }
    Telemetry.log("Drivetrain/TargetVelocities", targets);
  }

  /** 用場地座標的速度移動：vx 是往紅色聯盟的方向（+X）。 */
  public void driveFieldRelative(ChassisVelocities velocities) {
    driveRobotRelative(velocities.toRobotRelative(getPose().getRotation()));
  }

  /** 用駕駛的視角移動：vx 是遠離駕駛的方向。紅色聯盟的駕駛站在場地的另一端，所以方向相反。 */
  public void driveDriverRelative(ChassisVelocities velocities) {
    if (isRedAlliance()) {
      velocities = new ChassisVelocities(-velocities.vx, -velocities.vy, velocities.omega);
    }
    driveFieldRelative(velocities);
  }

  public void stop() {
    for (SwerveModule module : modules) {
      module.stop();
    }
  }

  /** 把四個輪子轉成 X 字形，讓機器人很難被推動。 */
  public void lock() {
    modules[0].setTarget(new SwerveModuleVelocity(0.0, Rotation2d.fromDegrees(45)));   // 左前
    modules[1].setTarget(new SwerveModuleVelocity(0.0, Rotation2d.fromDegrees(-45)));  // 右前
    modules[2].setTarget(new SwerveModuleVelocity(0.0, Rotation2d.fromDegrees(-45)));  // 左後
    modules[3].setTarget(new SwerveModuleVelocity(0.0, Rotation2d.fromDegrees(45)));   // 右後
  }

  // ---------- Command ----------

  /**
   * 用搖桿開車（駕駛視角）。三個輸入的範圍都是 -1～1。
   *
   * @param forward 遠離駕駛
   * @param left 往駕駛的左邊
   * @param rotation 逆時針旋轉
   */
  public Command driveCommand(DoubleSupplier forward, DoubleSupplier left, DoubleSupplier rotation) {
    return run(() -> driveDriverRelative(
            new ChassisVelocities(
                forward.getAsDouble() * SwerveConstants.MAX_SPEED,
                left.getAsDouble() * SwerveConstants.MAX_SPEED,
                rotation.getAsDouble() * SwerveConstants.MAX_ANGULAR_SPEED)))
        .finallyDo(this::stop)
        .withName("SwerveDrive");
  }

  /** 按住時把輪子轉成 X 字形。 */
  public Command lockCommand() {
    return run(this::lock).withName("SwerveLock");
  }

  /** 駕駛讓機器人面向遠離自己的方向，再按這個按鈕：重設朝向，位置不變。 */
  public Command resetHeadingCommand() {
    return runOnce(() -> {
          Rotation2d awayFromDriver = isRedAlliance() ? Rotation2d.PI : Rotation2d.ZERO;
          resetPose(new Pose2d(getPose().getTranslation(), awayFromDriver));
        })
        .ignoringDisable(true)
        .withName("ResetHeading");
  }

  @Override
  public void periodic() {
    for (SwerveModule module : modules) {
      module.log();
    }
    poseEstimator.update(getHeading(), getModulePositions());
    Telemetry.log("Drivetrain/Pose", getPose());
    Telemetry.log("Drivetrain/ModuleVelocities", getModuleVelocities());
    Telemetry.log("Drivetrain/HeadingDegrees", getPose().getRotation().getDegrees());
  }

  @Override
  public void simulationPeriodic() {
    for (SwerveModule module : modules) {
      module.simulationPeriodic();
    }
    // 模擬的 IMU：用四個模組實際的速度，算出機器人轉得多快，再累加成朝向
    ChassisVelocities actual = kinematics.toChassisVelocities(getModuleVelocities());
    simYawRadians += actual.omega * 0.02;
    OnboardIMUSim.setYaw(simYawRadians);
  }
}
