package first.robot.subsystems;

import org.wpilib.hardware.imu.OnboardIMU;
import org.wpilib.hardware.imu.OnboardIMU.MountOrientation;

/** Systemcore 內建的 IMU。 */
public class GyroIOOnboard implements GyroIO {
  private final OnboardIMU imu = new OnboardIMU(MountOrientation.FLAT);

  @Override
  public void updateInputs(GyroIOInputs inputs) {
    inputs.connected = true;
    inputs.yaw = imu.getRotation2d();
    inputs.yawVelocityRadPerSec = imu.getGyroRateZ();
  }
}
