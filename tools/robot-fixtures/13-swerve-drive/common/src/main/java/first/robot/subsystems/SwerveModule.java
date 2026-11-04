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
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import first.robot.Constants.ModuleConstants;
import first.robot.Constants.SwerveConstants;
import org.wpilib.hardware.bus.CANPort;
import org.wpilib.math.controller.PIDController;
import org.wpilib.math.geometry.Rotation2d;
import org.wpilib.math.kinematics.SwerveModuleVelocity;

/** 一個 Swerve 模組：TalonFX 驅動輪子，SparkMax 轉向，CANcoder 量輪子指向的角度。 */
public class SwerveModule {
  private final TalonFX driveMotor;
  private final SparkMax steerMotor;
  private final CANcoder encoder;

  private final VelocityVoltage velocityRequest = new VelocityVoltage(0);
  private final PIDController steerController =
      new PIDController(SwerveConstants.STEER_KP, 0.0, SwerveConstants.STEER_KD);

  public SwerveModule(ModuleConstants constants) {
    driveMotor = new TalonFX(constants.driveId(), new CANBus(CANPort.CAN_S0));
    steerMotor = new SparkMax(CANPort.CAN_S0, constants.steerId(), MotorType.kBrushless);
    encoder = new CANcoder(constants.encoderId(), new CANBus(CANPort.CAN_S0));

    configureDriveMotor();
    configureSteerMotor();
    configureEncoder(constants.encoderOffsetRotations());

    // 角度是一個圓：-180 度和 180 度是同一個方向
    steerController.enableContinuousInput(-Math.PI, Math.PI);
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
    driveMotor.getConfigurator().apply(config);
  }

  private void configureSteerMotor() {
    SparkMaxConfig config = new SparkMaxConfig();
    config
        .idleMode(IdleMode.kBrake)
        .smartCurrentLimit(SwerveConstants.STEER_CURRENT_LIMIT)
        .inverted(SwerveConstants.STEER_INVERTED);
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

  // ---------- 控制 ----------

  /** 讓模組轉到指定的角度，並用指定的速度滾動。 */
  public void setTarget(SwerveModuleVelocity target) {
    double steerVolts = steerController.calculate(getAngle().getRadians(), target.angle.getRadians());
    steerMotor.setVoltage(steerVolts);

    double wheelRps = target.velocity / SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
    driveMotor.setControl(velocityRequest.withVelocity(wheelRps));
  }

  public void stop() {
    driveMotor.stopMotor();
    steerMotor.stopMotor();
  }
}
