package first.robot;

import com.pathplanner.lib.auto.AutoBuilder;
import first.robot.Constants.OperatorConstants;
import first.robot.Constants.SwerveConstants;
import first.robot.commands.AimAtTarget;
import first.robot.commands.TeleopDrive;
import first.robot.subsystems.Drivetrain;
import first.robot.subsystems.GyroIO;
import first.robot.subsystems.GyroIOOnboard;
import first.robot.subsystems.ModuleIO;
import first.robot.subsystems.ModuleIOReal;
import first.robot.subsystems.ModuleIOSim;
import first.robot.subsystems.Vision;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.button.CommandGamepad;
import org.wpilib.math.util.MathUtil;
import org.wpilib.tunable.Selectable;
import org.wpilib.tunable.Tunables;

public class RobotContainer {
  private final Drivetrain drivetrain = createDrivetrain();
  private final Vision vision = new Vision(drivetrain);

  private final CommandGamepad driver = new CommandGamepad(OperatorConstants.DRIVER_CONTROLLER_PORT);

  private final TeleopDrive teleopDrive =
      new TeleopDrive(
          drivetrain,
          () -> shape(-driver.getLeftY()) * speedScale(),
          () -> shape(-driver.getLeftX()) * speedScale(),
          () -> shape(-driver.getRightX()) * speedScale());

  private final Selectable<Command> autoChooser;

  public RobotContainer() {
    autoChooser = buildAutoChooser();
    Tunables.publish("Auto Chooser", autoChooser);

    configureDefaultCommands();
    configureBindings();
  }

  /** 依照執行模式，決定 Drivetrain 底下接的是哪一種硬體。 */
  private static Drivetrain createDrivetrain() {
    return switch (Constants.CURRENT_MODE) {
      case REAL -> new Drivetrain(
          new GyroIOOnboard(),
          new ModuleIOReal(SwerveConstants.FRONT_LEFT),
          new ModuleIOReal(SwerveConstants.FRONT_RIGHT),
          new ModuleIOReal(SwerveConstants.BACK_LEFT),
          new ModuleIOReal(SwerveConstants.BACK_RIGHT));
      case SIM -> new Drivetrain(
          new GyroIO() {},   // 模擬裡沒有 IMU，Drivetrain 會用模組的資料估計朝向
          new ModuleIOSim(SwerveConstants.FRONT_LEFT),
          new ModuleIOSim(SwerveConstants.FRONT_RIGHT),
          new ModuleIOSim(SwerveConstants.BACK_LEFT),
          new ModuleIOSim(SwerveConstants.BACK_RIGHT));
      case REPLAY -> new Drivetrain(
          // 重播時不碰任何硬體，所有讀值都從 log 檔來
          new GyroIO() {},
          new ModuleIO() {},
          new ModuleIO() {},
          new ModuleIO() {},
          new ModuleIO() {});
    };
  }

  private Selectable<Command> buildAutoChooser() {
    if (AutoBuilder.isConfigured()) {
      return AutoBuilder.buildAutoChooser();   // 包含所有 PathPlanner 的自動程式
    }
    Selectable<Command> chooser = new Selectable<>();
    chooser.addDefault("什麼都不做", Commands.none());
    return chooser;
  }

  /** 搖桿中間 10% 不動作，其他部分取平方：推一點點時更好微調。 */
  private static double shape(double input) {
    return MathUtil.copyDirectionPow(MathUtil.applyDeadband(input, 0.1), 2);
  }

  /** 按住左肩鍵時，速度降到 30%。 */
  private double speedScale() {
    return driver.leftBumper().getAsBoolean() ? 0.3 : 1.0;
  }

  private void configureDefaultCommands() {
    drivetrain.setDefaultCommand(teleopDrive);
  }

  private void configureBindings() {
    driver.start().onTrue(drivetrain.resetHeadingCommand());
    driver.faceLeft().whileTrue(drivetrain.lockCommand());
    // 按住右扳機時，一邊移動一邊對準目標
    driver.rightTrigger().whileTrue(
        new AimAtTarget(
            drivetrain, vision, () -> shape(-driver.getLeftY()), () -> shape(-driver.getLeftX())));

    // 十字鍵：轉到駕駛視角的 0、90、180、-90 度
    driver.dpadUp().onTrue(Commands.runOnce(() -> teleopDrive.snapTo(0)));
    driver.dpadLeft().onTrue(Commands.runOnce(() -> teleopDrive.snapTo(90)));
    driver.dpadDown().onTrue(Commands.runOnce(() -> teleopDrive.snapTo(180)));
    driver.dpadRight().onTrue(Commands.runOnce(() -> teleopDrive.snapTo(-90)));
  }

  public Command getAutonomousCommand() {
    return autoChooser.getSelected();
  }
}
