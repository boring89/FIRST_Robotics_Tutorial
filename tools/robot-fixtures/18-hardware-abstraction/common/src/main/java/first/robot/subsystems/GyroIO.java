package first.robot.subsystems;

import org.littletonrobotics.junction.AutoLog;
import org.wpilib.math.geometry.Rotation2d;

/** 量機器人朝向的硬體。 */
public interface GyroIO {
  @AutoLog
  class GyroIOInputs {
    /** 沒有 IMU 時是 false，Drivetrain 會改用模組的資料估計朝向。 */
    public boolean connected = false;
    public Rotation2d yaw = Rotation2d.ZERO;
    public double yawVelocityRadPerSec = 0.0;
  }

  default void updateInputs(GyroIOInputs inputs) {}
}
