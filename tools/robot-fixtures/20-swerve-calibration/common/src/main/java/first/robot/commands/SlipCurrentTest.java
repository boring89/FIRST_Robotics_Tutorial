package first.robot.commands;

import first.robot.Constants.SwerveConstants;
import first.robot.subsystems.Drivetrain;
import org.wpilib.command2.Command;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
import org.wpilib.math.system.DCMotor;
import org.wpilib.system.Timer;
import org.wpilib.telemetry.Telemetry;

/** 機器人頂著牆，慢慢加大驅動馬達的電壓，輪子開始打滑時記錄電流。 */
public class SlipCurrentTest extends Command {
  /** 電壓每秒增加多少，單位：伏特。 */
  private static final double RAMP_VOLTS_PER_SECOND = 0.5;
  /** 最多加到幾伏特。到了還沒打滑就停止。 */
  private static final double MAX_VOLTS = 6.0;
  /** 輪子速度超過這個值（公尺 / 秒）就算打滑：機器人頂著牆，輪子本來不該轉。 */
  private static final double SLIP_VELOCITY = 0.2;
  /** 測試時暫時放寬的電流限制，單位：安培。 */
  private static final double TEST_CURRENT_LIMIT = 120;

  private final Drivetrain drivetrain;
  private final Timer timer = new Timer();
  private double maxCurrentAmps;
  private boolean slipped;

  public SlipCurrentTest(Drivetrain drivetrain) {
    this.drivetrain = drivetrain;
    addRequirements(drivetrain);
  }

  @Override
  public void initialize() {
    drivetrain.setDriveCurrentLimit(TEST_CURRENT_LIMIT);
    timer.restart();
    maxCurrentAmps = 0.0;
    slipped = false;
  }

  @Override
  public void execute() {
    double volts = timer.get() * RAMP_VOLTS_PER_SECOND;
    drivetrain.runDriveCharacterization(volts);

    double current = drivetrain.getAverageDriveCurrentAmps();
    maxCurrentAmps = Math.max(maxCurrentAmps, current);

    double velocitySum = 0.0;
    for (SwerveModuleVelocity module : drivetrain.getModuleVelocities()) {
      velocitySum += Math.abs(module.velocity);
    }
    if (velocitySum / 4 > SLIP_VELOCITY) {
      slipped = true;
    }

    Telemetry.log("SlipTest/Volts", volts);
    Telemetry.log("SlipTest/CurrentAmps", current);
    Telemetry.log("SlipTest/WheelVelocity", velocitySum / 4);
  }

  @Override
  public boolean isFinished() {
    return slipped || timer.get() * RAMP_VOLTS_PER_SECOND > MAX_VOLTS;
  }

  @Override
  public void end(boolean interrupted) {
    drivetrain.stop();
    // 一定要改回原本的電流限制
    drivetrain.setDriveCurrentLimit(SwerveConstants.DRIVE_CURRENT_LIMIT);

    if (!slipped) {
      System.out.println("沒有偵測到打滑：確認機器人有頂著牆，或電壓上限太低");
      return;
    }
    // 四個輪子的推力 = 摩擦力上限：4 × 馬達扭力 × 齒輪比 ÷ 輪子半徑 = 摩擦係數 × 機器人重量
    double motorTorque = DCMotor.getKrakenX60(1).Kt * maxCurrentAmps;
    double wheelForce = motorTorque * SwerveConstants.DRIVE_GEAR_RATIO / (SwerveConstants.WHEEL_DIAMETER_METERS / 2);
    double cof = 4 * wheelForce / (SwerveConstants.ROBOT_MASS_KG * 9.81);
    System.out.printf("打滑電流 %.1f A，估計的摩擦係數 %.2f%n", maxCurrentAmps, cof);
  }
}
