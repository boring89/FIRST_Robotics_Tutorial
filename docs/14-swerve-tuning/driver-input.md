# 駕駛手感

--8<-- "alpha-warning.md"

## 這頁學什麼

- 輸入曲線：讓搖桿推一點點時更好微調
- 慢速模式：精準對位
- X 字鎖定：讓機器人很難被推走
- `discretize`：修正一邊走一邊轉時的偏移

## 觀念

### 輸入曲線

[第 13 章](../13-swerve-drive/drivetrain.md) 的搖桿是**線性**的：推 50% 就是半速。但駕駛大部分的時間需要的是**小幅度的精準控制**，全速只在長距離移動時才用。

把搖桿的值**平方**（保留正負號），小的輸入會變得更小，大的輸入不太變：

| 搖桿 | 線性 | 平方 |
| --- | --- | --- |
| 0.2 | 20% | 4% |
| 0.5 | 50% | 25% |
| 0.8 | 80% | 64% |
| 1.0 | 100% | 100% |

`MathUtil.copyDirectionPow(value, 2)` 就是「取平方、保留正負號」。和 Deadband 一起用：

```java
/** 搖桿中間 10% 不動作，其他部分取平方：推一點點時更好微調。 */
private static double shape(double input) {
  return MathUtil.copyDirectionPow(MathUtil.applyDeadband(input, 0.1), 2);
}
```

`applyDeadband` 不只是把 −0.1～0.1 變成 0，還會把剩下的範圍**重新拉回 0～1**：搖桿推到 0.1 時輸出 0，推到底時還是 1，中間不會突然跳一下。

### X 字鎖定

比賽中被對手推的時候，四個輪子都朝前，機器人很容易被往前後推。把輪子轉成 **X 字形**，不管從哪個方向推，都有輪子是橫的，很難推動：

```text
   左前 ╲  ╱ 右前
          ×
   左後 ╱  ╲ 右後
```

### `discretize`：一邊走一邊轉

Kinematics 算的是「這一瞬間」的速度。但程式每 20 ms 才更新一次，這 20 ms 裡機器人一直用同樣的速度，而機器人自己在轉，原本「往前」的速度會慢慢變成「往旁邊」，所以一邊平移一邊旋轉時，機器人會往旋轉的方向偏。

**`velocities.discretize(0.02)`** 把「這 20 ms 內一邊走一邊轉」考慮進去，算出一個修正過的速度。

在[第 15 章](../15-swerve-sim/index.md) 的模擬裡，讓機器人以 1 m/s 往前、同時以 2 rad/s 旋轉 2 秒：

| | 往前走的距離 | 往旁邊偏的距離 |
| --- | --- | --- |
| 沒有 `discretize` | 1.90 m | 0.32 m |
| 有 `discretize` | 1.90 m | 0.28 m |

`discretize` 修正了一部分，剩下的偏移主要是**轉向跟不上**造成的：機器人一直在轉，每個輪子的目標角度也一直在變，轉向總是慢一點。[延伸學習](further.md) 有進一步的補償方法。

## 範例

### Drivetrain

在 `driveRobotRelative()` 的最前面加上 `discretize`：

```java
public void driveRobotRelative(ChassisVelocities velocities) {
  // 修正「一邊走一邊轉」時往旁邊偏的問題
  velocities = velocities.discretize(0.02);
  SwerveModuleVelocity[] targets = kinematics.toSwerveModuleVelocities(velocities);
  // ...後面不變...
}
```

加上 X 字鎖定：

```java
/** 把四個輪子轉成 X 字形，讓機器人很難被推動。 */
public void lock() {
  modules[0].setTarget(new SwerveModuleVelocity(0.0, Rotation2d.fromDegrees(45)));   // 左前
  modules[1].setTarget(new SwerveModuleVelocity(0.0, Rotation2d.fromDegrees(-45)));  // 右前
  modules[2].setTarget(new SwerveModuleVelocity(0.0, Rotation2d.fromDegrees(-45)));  // 左後
  modules[3].setTarget(new SwerveModuleVelocity(0.0, Rotation2d.fromDegrees(45)));   // 右後
}

/** 按住時把輪子轉成 X 字形。 */
public Command lockCommand() {
  return run(this::lock).withName("SwerveLock");
}
```

速度是 0，所以只有轉向在動。左前和右後指向 45 度，右前和左後指向 −45 度，四個輪子都指向（或背對）機器人中心。

### RobotContainer

```java title="src/main/java/first/robot/RobotContainer.java"
package first.robot;

import first.robot.Constants.OperatorConstants;
import first.robot.subsystems.Drivetrain;
import org.wpilib.command2.Command;
import org.wpilib.command2.Commands;
import org.wpilib.command2.button.CommandGamepad;
import org.wpilib.math.util.MathUtil;

public class RobotContainer {
  private final Drivetrain drivetrain = new Drivetrain();

  private final CommandGamepad driver = new CommandGamepad(OperatorConstants.DRIVER_CONTROLLER_PORT);

  public RobotContainer() {
    configureDefaultCommands();
    configureBindings();
  }

  /** 搖桿中間 10% 不動作，其他部分取平方：推一點點時更好微調。 */
  private static double shape(double input) {
    return MathUtil.copyDirectionPow(MathUtil.applyDeadband(input, 0.1), 2);
  }

  private void configureDefaultCommands() {
    drivetrain.setDefaultCommand(
        drivetrain.driveCommand(
            () -> shape(-driver.getLeftY()),
            () -> shape(-driver.getLeftX()),
            () -> shape(-driver.getRightX())));
  }

  private void configureBindings() {
    driver.start().onTrue(drivetrain.resetHeadingCommand());
    driver.faceLeft().whileTrue(drivetrain.lockCommand());
    // 按住左肩鍵時，速度降到 30%
    driver.leftBumper().whileTrue(
        drivetrain.driveCommand(
            () -> shape(-driver.getLeftY()) * 0.3,
            () -> shape(-driver.getLeftX()) * 0.3,
            () -> shape(-driver.getRightX()) * 0.3));
  }

  public Command getAutonomousCommand() {
    return Commands.none();
  }
}
```

- 三個搖桿都經過 `shape()`
- **X 鍵**（`faceLeft()`）按住時鎖定
- **左肩鍵**按住時速度降到 30%。它和 default command 一樣是 `driveCommand`，按住時會中斷 default command，放開後 default command 自動回來（[第 7 章延伸學習](../07-drivetrain/further.md)）

## 常見錯誤

!!! bug "常見錯誤：先平方再 Deadband"
    `applyDeadband(copyDirectionPow(x, 2), 0.1)`：平方之後 0.3 變成 0.09，被當成死區。搖桿要推超過 0.32 才會動，駕駛會覺得搖桿「很鈍」。**先 Deadband 再平方**。

!!! bug "常見錯誤：X 字鎖定時角度寫成一樣"
    四個輪子都寫 45 度，輪子是平行的，只是斜著放，一推就滑走了。要兩個 45 度、兩個 −45 度。

## 小練習

1. 把 `shape()` 改成三次方（`copyDirectionPow(..., 3)`），給駕駛試開，比較平方和三次方的手感。
2. 加一個按鈕：按住時用**機器人座標**開車（[第 13 章 Drivetrain 小練習](../13-swerve-drive/drivetrain.md#小練習)），並套用 `shape()`。
3. 在比賽的哪些時候會用到 X 字鎖定？哪些時候不應該用？

??? question "第 3 題參考答案"
    **適合用**：在得分位置被對手推、需要守住位置的時候；比賽最後幾秒停在指定區域時。

    **不適合用**：需要移動的時候。鎖定時輪子是斜的，鬆開按鈕後要先轉回來才能走，會慢一點。另外鎖定時驅動馬達沒有出力，只靠輪子的摩擦力撐住，被很大的力量推還是會滑。
