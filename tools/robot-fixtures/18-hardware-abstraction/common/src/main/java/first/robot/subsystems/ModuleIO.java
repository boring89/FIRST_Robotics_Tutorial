package first.robot.subsystems;

import org.littletonrobotics.junction.AutoLog;
import org.wpilib.math.geometry.Rotation2d;

/** 一個 Swerve 模組的硬體：只負責讀感測器、送指令，不做任何判斷。 */
public interface ModuleIO {
  /** 每 20 ms 從硬體讀到的所有資料。 */
  @AutoLog
  class ModuleIOInputs {
    public double drivePositionMeters = 0.0;
    public double driveVelocityMetersPerSec = 0.0;
    public double driveAppliedVolts = 0.0;
    public double driveCurrentAmps = 0.0;
    /** CANcoder 量到的輪子角度。 */
    public Rotation2d steerAngle = Rotation2d.ZERO;
    public double steerCurrentAmps = 0.0;
  }

  /** 讀取硬體，把結果寫進 inputs。 */
  default void updateInputs(ModuleIOInputs inputs) {}

  /** 驅動輪的目標速度（公尺 / 秒）和加速度（公尺 / 秒²）。 */
  default void setDriveVelocity(double metersPerSec, double accelerationMetersPerSecSq) {}

  /** 輪子要轉到的角度。 */
  default void setSteerAngle(Rotation2d angle) {}

  default void stop() {}
}
