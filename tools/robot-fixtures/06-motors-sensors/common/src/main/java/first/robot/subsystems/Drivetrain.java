package first.robot.subsystems;

import org.wpilib.command2.Command;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.hardware.imu.OnboardIMU;
import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.telemetry.Telemetry;

public class Drivetrain extends SubsystemBase {
  private final OnboardIMU imu = new OnboardIMU(MountOrientation.FLAT);

  /** 機器人的朝向，逆時針為正。 */
  public Rotation2d getHeading() {
    return imu.getRotation2d();
  }

  /** 把目前的朝向設為 0 度。 */
  public Command resetHeadingCommand() {
    return runOnce(imu::resetYaw).ignoringDisable(true).withName("ResetHeading");
  }

  @Override
  public void periodic() {
    Telemetry.log("Drivetrain/HeadingDegrees", getHeading().getDegrees());
  }
}
