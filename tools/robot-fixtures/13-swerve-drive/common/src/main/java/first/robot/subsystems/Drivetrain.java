package first.robot.subsystems;

import first.robot.Constants.SwerveConstants;
import java.util.function.DoubleSupplier;
import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.hardware.imu.OnboardIMU;
import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.SwerveDriveKinematics;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
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

  // ---------- 感測器 ----------

  /** 機器人的朝向，逆時針為正。 */
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

  // ---------- 控制 ----------

  /** 用機器人座標的速度移動：vx 往前、vy 往左、omega 逆時針。 */
  public void driveRobotRelative(ChassisVelocities velocities) {
    SwerveModuleVelocity[] targets = kinematics.toSwerveModuleVelocities(velocities);
    targets = SwerveDriveKinematics.desaturateWheelVelocities(targets, SwerveConstants.MAX_SPEED);
    for (int i = 0; i < modules.length; i++) {
      modules[i].setTarget(targets[i]);
    }
    Telemetry.log("Drivetrain/TargetVelocities", targets);
  }

  /** 用場地座標的速度移動：不管機器人面向哪裡，vx 都是往場地的前方。 */
  public void driveFieldRelative(ChassisVelocities velocities) {
    driveRobotRelative(velocities.toRobotRelative(getHeading()));
  }

  public void stop() {
    for (SwerveModule module : modules) {
      module.stop();
    }
  }

  // ---------- Command ----------

  /**
   * 用搖桿開車（場地座標）。三個輸入的範圍都是 -1～1。
   *
   * @param forward 往前
   * @param left 往左
   * @param rotation 逆時針旋轉
   */
  public Command driveCommand(DoubleSupplier forward, DoubleSupplier left, DoubleSupplier rotation) {
    return run(() -> driveFieldRelative(
            new ChassisVelocities(
                forward.getAsDouble() * SwerveConstants.MAX_SPEED,
                left.getAsDouble() * SwerveConstants.MAX_SPEED,
                rotation.getAsDouble() * SwerveConstants.MAX_ANGULAR_SPEED)))
        .finallyDo(this::stop)
        .withName("SwerveDrive");
  }

  /** 把目前的朝向設為 0 度：駕駛讓機器人面向遠離自己的方向，再按這個按鈕。 */
  public Command resetHeadingCommand() {
    return runOnce(imu::resetYaw).ignoringDisable(true).withName("ResetHeading");
  }

  @Override
  public void periodic() {
    Telemetry.log("Drivetrain/ModuleVelocities", getModuleVelocities());
    Telemetry.log("Drivetrain/HeadingDegrees", getHeading().getDegrees());
  }
}
