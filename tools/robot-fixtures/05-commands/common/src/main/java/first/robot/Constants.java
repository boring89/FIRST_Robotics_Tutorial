package first.robot;

public final class Constants {
  public static final class OperatorConstants {
    public static final int DRIVER_CONTROLLER_PORT = 0;
  }

  public static final class IntakeConstants {
    public static final int MOTOR_ID = 10;
    public static final int BEAM_BREAK_CHANNEL = 0;
    public static final double INTAKE_THROTTLE = 0.8;
    public static final double EJECT_THROTTLE = -0.5;
  }

  public static final class ShooterConstants {
    public static final int MOTOR_ID = 20;
    public static final double SHOOT_THROTTLE = 0.9;
    public static final double SPIN_UP_SECONDS = 1.0;
    public static final double FEED_SECONDS = 0.5;
  }

  private Constants() {}
}
