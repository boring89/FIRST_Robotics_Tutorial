package first.robot.subsystems;

import com.limelightvision.Limelight;
import com.limelightvision.PoseEstimate;
import com.limelightvision.PoseEstimateType;
import first.robot.Constants.VisionConstants;
import org.wpilib.command2.SubsystemBase;
import org.wpilib.telemetry.Telemetry;

public class Vision extends SubsystemBase {
  private final Limelight limelight = new Limelight(VisionConstants.LIMELIGHT_NAME);
  private final Drivetrain drivetrain;

  public Vision(Drivetrain drivetrain) {
    this.drivetrain = drivetrain;
  }

  public boolean hasTarget() {
    return limelight.hasTarget();
  }

  /** 目標的水平角度，目標在右邊為正。 */
  public double getTXDegrees() {
    return limelight.getTXDegrees();
  }

  @Override
  public void periodic() {
    // MegaTag2 需要知道機器人目前在場地上的朝向
    Limelight.setSharedRobotOrientation(drivetrain.getPose().getRotation().getDegrees());

    PoseEstimate estimate = limelight.getPoseEstimate(PoseEstimateType.MT2_WPIBLUE);
    if (estimate.isValid()) {
      drivetrain.addVisionMeasurement(estimate.pose, estimate.timestampSeconds, estimate.stdDevs);
      Telemetry.log("Vision/Pose", estimate.pose);
    }

    Telemetry.log("Vision/HasTarget", hasTarget());
    Telemetry.log("Vision/TXDegrees", getTXDegrees());
  }
}
