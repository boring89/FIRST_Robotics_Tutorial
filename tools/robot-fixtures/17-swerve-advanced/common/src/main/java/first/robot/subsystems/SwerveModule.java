package first.robot.subsystems;

import com.ctre.phoenix6.CANBus;
import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.controls.VelocityVoltage;
import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.signals.NeutralModeValue;
import com.ctre.phoenix6.signals.SensorDirectionValue;
import com.revrobotics.PersistMode;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.FeedbackSensor;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkLowLevel.ControlType;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import first.robot.Constants.ModuleConstants;
import first.robot.Constants.SwerveConstants;
import org.wpilib.framework.RobotBase;
import org.wpilib.hardware.bus.CANPort;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.SwerveModulePosition;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
import org.wpilib.telemetry.Telemetry;

/** 一個 Swerve 模組：TalonFX 驅動輪子，SparkMax 轉向，CANcoder 量輪子指向的角度。 */
public class SwerveModule {
  private final String name;
  private final TalonFX driveMotor;
  private final SparkMax steerMotor;
  private final CANcoder encoder;

  private final RelativeEncoder steerEncoder;
  private final SparkClosedLoopController steerController;
  private final VelocityVoltage velocityRequest = new VelocityVoltage(0);
  private SwerveModuleVelocity lastTarget = new SwerveModuleVelocity();

  /** 只有在模擬時才會建立，實際的機器人上是 null。 */
  private final ModuleSim sim;

  public SwerveModule(ModuleConstants constants) {
    name = constants.name();
    driveMotor = new TalonFX(constants.driveId(), new CANBus(CANPort.CAN_S0));
    steerMotor = new SparkMax(CANPort.CAN_S0, constants.steerId(), MotorType.kBrushless);
    encoder = new CANcoder(constants.encoderId(), new CANBus(CANPort.CAN_S0));
    steerEncoder = steerMotor.getEncoder();
    steerController = steerMotor.getClosedLoopController();

    configureDriveMotor();
    configureSteerMotor();
    configureEncoder(constants.encoderOffsetRotations());

    // 要在 syncSteerEncoder() 之前建立，模擬的 CANcoder 才會有讀值
    sim = RobotBase.isSimulation()
        ? new ModuleSim(driveMotor, steerMotor, encoder, constants.encoderOffsetRotations())
        : null;
    syncSteerEncoder();
  }

  private void configureDriveMotor() {
    TalonFXConfiguration config = new TalonFXConfiguration();
    config.MotorOutput.Inverted = SwerveConstants.DRIVE_INVERTED;
    config.MotorOutput.NeutralMode = NeutralModeValue.Brake;
    config.CurrentLimits.StatorCurrentLimit = SwerveConstants.DRIVE_CURRENT_LIMIT;
    config.CurrentLimits.StatorCurrentLimitEnable = true;
    // 讓位置和速度的單位變成「輪子的圈數」，不是馬達的圈數
    config.Feedback.SensorToMechanismRatio = SwerveConstants.DRIVE_GEAR_RATIO;
    config.Slot0.kS = SwerveConstants.DRIVE_KS;
    config.Slot0.kV = SwerveConstants.DRIVE_KV;
    config.Slot0.kP = SwerveConstants.DRIVE_KP;
    config.Slot0.kA = SwerveConstants.DRIVE_KA;
    driveMotor.getConfigurator().apply(config);
  }

  private void configureSteerMotor() {
    SparkMaxConfig config = new SparkMaxConfig();
    config
        .idleMode(IdleMode.kBrake)
        .smartCurrentLimit(SwerveConstants.STEER_CURRENT_LIMIT)
        .inverted(SwerveConstants.STEER_INVERTED);
    config.closedLoop
        .feedbackSensor(FeedbackSensor.kPrimaryEncoder)
        .p(SwerveConstants.STEER_KP)
        .d(SwerveConstants.STEER_KD)
        // 輪子轉一圈 = 馬達轉 STEER_GEAR_RATIO 圈，超過範圍就從另一端繞回來
        .positionWrappingEnabled(true)
        .positionWrappingInputRange(
            -SwerveConstants.STEER_GEAR_RATIO / 2, SwerveConstants.STEER_GEAR_RATIO / 2);
    steerMotor.configure(config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
  }

  private void configureEncoder(double offsetRotations) {
    CANcoderConfiguration config = new CANcoderConfiguration();
    config.MagnetSensor.MagnetOffset = offsetRotations;
    config.MagnetSensor.SensorDirection = SensorDirectionValue.CounterClockwise_Positive;
    // 讀值範圍 -0.5～0.5 圈，也就是 -180～180 度
    config.MagnetSensor.AbsoluteSensorDiscontinuityPoint = 0.5;
    encoder.getConfigurator().apply(config);
  }

  /** 用 CANcoder 的絕對角度，設定 NEO 內建編碼器的位置。 */
  public void syncSteerEncoder() {
    double moduleRotations = encoder.getAbsolutePosition().waitForUpdate(0.1).getValueAsDouble();
    steerEncoder.setPosition(moduleRotations * SwerveConstants.STEER_GEAR_RATIO);
  }

  // ---------- 感測器 ----------

  /** 輪子指向的角度，逆時針為正，0 度是機器人的正前方。 */
  public Rotation2d getAngle() {
    return Rotation2d.fromRotations(encoder.getAbsolutePosition().getValueAsDouble());
  }

  /** 輪子的速度，單位：公尺 / 秒。 */
  public double getVelocityMetersPerSecond() {
    return driveMotor.getVelocity().getValueAsDouble() * SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
  }

  /** 目前的速度和角度。 */
  public SwerveModuleVelocity getVelocity() {
    return new SwerveModuleVelocity(getVelocityMetersPerSecond(), getAngle());
  }

  /** 輪子總共滾了多遠，單位：公尺。 */
  public double getDistanceMeters() {
    return driveMotor.getPosition().getValueAsDouble() * SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
  }

  /** 目前走過的距離和角度，給 Odometry 用。 */
  public SwerveModulePosition getPosition() {
    return new SwerveModulePosition(getDistanceMeters(), getAngle());
  }

  // ---------- 控制 ----------

  /** 讓模組轉到指定的角度，並用指定的速度滾動。 */
  public void setTarget(SwerveModuleVelocity target) {
    setTarget(target, 0.0);
  }

  /**
   * 讓模組轉到指定的角度，用指定的速度滾動，並告訴 TalonFX 預期的加速度。
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
    target = optimized;
    // 輪子還沒轉到位時先放慢，避免往錯的方向推
    target = target.cosineScale(current);
    lastTarget = target;

    steerController.setSetpoint(
        target.angle.getRotations() * SwerveConstants.STEER_GEAR_RATIO, ControlType.kPosition);

    double wheelRps = target.velocity / SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
    double wheelRpsPerSec = accelerationMetersPerSecSq / SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
    driveMotor.setControl(velocityRequest.withVelocity(wheelRps).withAcceleration(wheelRpsPerSec));
  }

  public void stop() {
    driveMotor.stopMotor();
    steerMotor.stopMotor();
  }

  /** 把目標和實際的角度、速度送到 Telemetry，調參數時用。 */
  public void log() {
    Telemetry.log("Swerve/" + name + "/AngleDegrees", getAngle().getDegrees());
    Telemetry.log("Swerve/" + name + "/TargetAngleDegrees", lastTarget.angle.getDegrees());
    Telemetry.log("Swerve/" + name + "/Velocity", getVelocityMetersPerSecond());
    Telemetry.log("Swerve/" + name + "/TargetVelocity", lastTarget.velocity);
  }

  /** 只在模擬時呼叫：更新馬達模型。 */
  public void simulationPeriodic() {
    sim.update(0.02);
  }
}
