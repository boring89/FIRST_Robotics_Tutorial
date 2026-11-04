package first.robot.subsystems;

import org.littletonrobotics.junction.Logger;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.math.kinematics.SwerveModuleVelocity;

/** 一個 Swerve 模組的邏輯。硬體的部分交給 ModuleIO，這裡不知道底下是真的馬達、模擬，還是 log 重播。 */
public class SwerveModule {
  private final ModuleIO io;
  private final ModuleIOInputsAutoLogged inputs = new ModuleIOInputsAutoLogged();
  private final String name;

  public SwerveModule(ModuleIO io, String name) {
    this.io = io;
    this.name = name;
  }

  /** 每 20 ms 呼叫一次：讀取硬體，並記錄到 log。要在使用任何讀值之前呼叫。 */
  public void periodic() {
    io.updateInputs(inputs);
    Logger.processInputs("Drivetrain/Module/" + name, inputs);
  }

  // ---------- 感測器 ----------

  /** 輪子指向的角度，逆時針為正，0 度是機器人的正前方。 */
  public Rotation2d getAngle() {
    return inputs.steerAngle;
  }

  /** 目前的速度和角度。 */
  public SwerveModuleVelocity getVelocity() {
    return new SwerveModuleVelocity(inputs.driveVelocityMetersPerSec, getAngle());
  }

  /** 目前走過的距離和角度，給 Odometry 用。 */
  public SwerveModulePosition getPosition() {
    return new SwerveModulePosition(inputs.drivePositionMeters, getAngle());
  }

  // ---------- 控制 ----------

  /** 讓模組轉到指定的角度，並用指定的速度滾動。 */
  public void setTarget(SwerveModuleVelocity target) {
    setTarget(target, 0.0);
  }

  /**
   * 讓模組轉到指定的角度，用指定的速度滾動，並告訴驅動馬達預期的加速度。
   *
   * @param accelerationMetersPerSecSq 輪子的加速度，單位：公尺 / 秒²
   */
  public void setTarget(SwerveModuleVelocity target, double accelerationMetersPerSecSq) {
    Rotation2d current = getAngle();
    // 要轉超過 90 度時，改成反方向轉、輪子倒著滾
    SwerveModuleVelocity optimized = target.optimize(current);
    if (!optimized.angle.equals(target.angle)) {
      // 輪子倒著滾，加速度的方向也要反過來
      accelerationMetersPerSecSq = -accelerationMetersPerSecSq;
    }
    // 輪子還沒轉到位時先放慢，避免往錯的方向推
    target = optimized.cosineScale(current);

    io.setSteerAngle(target.angle);
    io.setDriveVelocity(target.velocity, accelerationMetersPerSecSq);
    Logger.recordOutput("Drivetrain/Module/" + name + "/Target", target);
  }

  public void stop() {
    io.stop();
  }
}
