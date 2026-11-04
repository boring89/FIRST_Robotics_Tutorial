package first.robot.subsystems;

import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.sim.CANcoderSimState;
import com.ctre.phoenix6.sim.TalonFXSimState;
import com.revrobotics.sim.SparkMaxSim;
import com.revrobotics.spark.SparkMax;
import first.robot.Constants.SwerveConstants;
import org.wpilib.math.system.DCMotor;
import org.wpilib.math.system.Models;
import org.wpilib.simulation.DCMotorSim;
import org.wpilib.system.RobotController;

/** 模擬一個 Swerve 模組：用馬達模型算出輪子怎麼轉，再告訴 TalonFX、SparkMax、CANcoder。 */
public class ModuleSim {
  // 轉動慣量，單位：kg·m²。驅動輪要推動四分之一台機器人，所以比轉向大很多
  private static final double DRIVE_INERTIA = 0.03;
  private static final double STEER_INERTIA = 0.004;

  private final DCMotor driveGearbox = DCMotor.getKrakenX60(1);
  private final DCMotor steerGearbox = DCMotor.getNEO(1);

  private final DCMotorSim drivePhysics =
      new DCMotorSim(
          Models.singleJointedArmFromPhysicalConstants(
              driveGearbox, DRIVE_INERTIA, SwerveConstants.DRIVE_GEAR_RATIO),
          driveGearbox);
  private final DCMotorSim steerPhysics =
      new DCMotorSim(
          Models.singleJointedArmFromPhysicalConstants(
              steerGearbox, STEER_INERTIA, SwerveConstants.STEER_GEAR_RATIO),
          steerGearbox);

  private final TalonFXSimState driveSim;
  private final SparkMaxSim steerSim;
  private final CANcoderSimState encoderSim;
  private final double encoderOffsetRotations;

  public ModuleSim(TalonFX driveMotor, SparkMax steerMotor, CANcoder encoder, double encoderOffsetRotations) {
    driveSim = driveMotor.getSimState();
    steerSim = new SparkMaxSim(steerMotor, steerGearbox);
    encoderSim = encoder.getSimState();
    this.encoderOffsetRotations = encoderOffsetRotations;
    updateEncoder();
  }

  /** 每 20 ms 呼叫一次。 */
  public void update(double dt) {
    double battery = RobotController.getBatteryVoltage();

    // 驅動：TalonFX 輸出的電壓 → 馬達模型 → 把轉子的位置和速度告訴 TalonFX
    driveSim.setSupplyVoltage(battery);
    drivePhysics.setInputVoltage(driveSim.getMotorVoltage());
    drivePhysics.update(dt);
    double driveRotorRotations =
        drivePhysics.getAngularPosition() / (2 * Math.PI) * SwerveConstants.DRIVE_GEAR_RATIO;
    double driveRotorRps =
        drivePhysics.getAngularVelocity() / (2 * Math.PI) * SwerveConstants.DRIVE_GEAR_RATIO;
    driveSim.setRawRotorPosition(driveRotorRotations);
    driveSim.setRotorVelocity(driveRotorRps);

    // 轉向：SparkMax 輸出的比例 × 電池電壓 → 馬達模型 → 把馬達轉速告訴 SparkMax
    steerPhysics.setInputVoltage(steerSim.getAppliedOutput() * battery);
    steerPhysics.update(dt);
    double steerMotorRpm =
        steerPhysics.getAngularVelocity() / (2 * Math.PI) * 60 * SwerveConstants.STEER_GEAR_RATIO;
    steerSim.iterate(steerMotorRpm, battery, dt);

    updateEncoder();
  }

  /** CANcoder 裝在轉向軸上，讀到的就是輪子的角度（再扣掉零點）。 */
  private void updateEncoder() {
    double moduleRotations = steerPhysics.getAngularPosition() / (2 * Math.PI);
    encoderSim.setRawPosition(moduleRotations - encoderOffsetRotations);
  }
}
