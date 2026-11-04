# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## 用模擬寫自動測試

每次改程式都要開模擬、接手把、開 AdvantageScope 看，很花時間。可以把「開模擬、讓機器人動、檢查結果」寫成 **JUnit 測試**，用 `./gradlew test` 自動執行。

這份教學在寫[模擬 Swerve 模組](module-sim.md) 時，就是用類似下面的測試印出「跑起來的樣子」那些數字的：

```java
package first.robot;

import first.robot.subsystems.Drivetrain;
import org.junit.jupiter.api.Test;
import org.wpilib.hardware.hal.HAL;
import org.wpilib.math.kinematics.ChassisVelocities;
import org.wpilib.math.kinematics.SwerveModuleVelocity;
import org.wpilib.simulation.DriverStationSim;

class SwerveSimTest {
  /** 用同一個速度跑 n 次迴圈（每次 20 ms）。 */
  static void step(Drivetrain drivetrain, ChassisVelocities velocities, int n) throws Exception {
    for (int i = 0; i < n; i++) {
      DriverStationSim.notifyNewData();
      drivetrain.driveRobotRelative(velocities);
      drivetrain.periodic();
      drivetrain.simulationPeriodic();
      Thread.sleep(20);   // CTRE 的模擬用真實的時間，要真的等
    }
  }

  @Test
  void strafesLeft() throws Exception {
    HAL.initialize();
    DriverStationSim.setEnabled(true);
    DriverStationSim.notifyNewData();
    Drivetrain drivetrain = new Drivetrain();

    step(drivetrain, new ChassisVelocities(0.0, 2.0, 0.0), 50);   // 往左 1 秒

    for (SwerveModuleVelocity module : drivetrain.getModuleVelocities()) {
      System.out.printf("%.2f m/s, %.1f 度%n", module.velocity, module.angle.getDegrees());
    }
  }
}
```

放在 `src/test/java/first/robot/SwerveSimTest.java`。幾個要注意的地方：

- `build.gradle` 裡的 `includeDesktopSupport` 要改成 `true`，測試才能載入廠商函式庫在電腦上的版本
- 第一次執行需要網路，Gradle 會下載廠商函式庫的電腦版
- `DriverStationSim.setEnabled(true)`：模擬的馬達控制器只有在機器人啟用時才會輸出
- 這裡只是把結果印出來。正式的測試應該用 `assertEquals` 檢查角度和速度在合理的範圍內，測試失敗時 `./gradlew test` 就會報錯

## 更完整的物理模擬

[模擬的限制](module-sim.md#模擬的限制) 提到，這一章的模型把四個模組當成互相獨立的，沒有機器人整體的物理，也沒有碰撞。

**maple-sim** 是 FRC 社群開發的物理模擬函式庫，會模擬整台機器人的質量、輪子的摩擦力、和場地牆壁或遊戲物件的碰撞。想讓模擬更接近真實，例如測試被推撞時的表現、自動階段撞牆的情況，可以參考它的文件。使用前先確認它是否已經支援 WPILib 2027。

## 模擬電池電壓

這一章一直假設電池是 12 V（`RobotController.getBatteryVoltage()` 在模擬裡預設是 12 V）。真正的機器人在八顆馬達同時加速時，電池電壓會掉下來。WPILib 的 **`BatterySim`** 可以用所有馬達的電流估計電池電壓：

```java
// 在 Drivetrain 的 simulationPeriodic() 裡，currents 是八顆馬達的電流（安培）
RoboRioSim.setVInVoltage(BatterySim.calculateDefaultBatteryLoadedVoltage(currents));
```

馬達的電流可以從 `DCMotorSim.getCurrentDraw()` 取得。2027 的 Systemcore 模擬仍然沿用 `RoboRioSim` 這個類別名稱。

## 進一步閱讀

- [WPILib 文件：Robot Simulation](https://docs.wpilib.org/en/latest/docs/software/wpilib-tools/robot-simulation/introduction.html)：Sim GUI、物理模擬類別的說明（部分內容可能還是 2026 的寫法）
- [AdvantageScope 文件：Swerve](https://docs.advantagescope.org/tab-reference/swerve)：Swerve 分頁的所有功能
- [maple-sim](https://github.com/Shenzhen-Robotics-Alliance/maple-sim)：FRC 的物理模擬函式庫
