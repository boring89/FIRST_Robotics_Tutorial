package first.robot;

public final class Constants {
  public static final class OperatorConstants {
    public static final int DRIVER_CONTROLLER_PORT = 0;
  }

  public static final class DrivetrainConstants {
    public static final int LEFT_LEADER_ID = 1;
    public static final int LEFT_FOLLOWER_ID = 2;
    public static final int RIGHT_LEADER_ID = 3;
    public static final int RIGHT_FOLLOWER_ID = 4;
    public static final int PIGEON_ID = 41;
    public static final int CURRENT_LIMIT = 50;
    public static final double GEAR_RATIO = 8.45;
    public static final double WHEEL_DIAMETER_METERS = 0.1524;
  }

  public static final class IntakeConstants {
    public static final int MOTOR_ID = 10;
    public static final int BEAM_BREAK_CHANNEL = 0;
    public static final double INTAKE_THROTTLE = 0.8;
    public static final double EJECT_THROTTLE = -0.5;
  }

  public static final class ShooterConstants {
    public static final int MOTOR_ID = 20;
    public static final int CURRENT_LIMIT = 40;
    public static final double SHOOT_THROTTLE = 0.9;
    public static final double MIN_SHOOT_RPM = 4000;
    public static final double SPIN_UP_SECONDS = 1.0;
    public static final double SPIN_UP_TIMEOUT_SECONDS = 2.0;
    public static final double FEED_SECONDS = 0.5;
  }

  public static final class ArmConstants {
    public static final int MOTOR_ID = 30;
    public static final int CANCODER_ID = 40;
    public static final int CURRENT_LIMIT = 40;
    public static final double ABSOLUTE_OFFSET_ROTATIONS = 0.0;
    public static final double STOWED_ANGLE_DEGREES = -30.0;
    public static final int BOTTOM_LIMIT_CHANNEL = 1;
  }

  private Constants() {}
}
