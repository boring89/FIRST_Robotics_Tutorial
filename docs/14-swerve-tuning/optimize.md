# Optimize 與 Cosine 補償

--8<-- "alpha-warning.md"

## 這頁學什麼

- 為什麼輪子永遠不需要轉超過 90 度
- `optimize()`：反方向轉、輪子倒著滾
- `cosineScale()`：轉向還沒到位時先放慢
- 這兩個方法會回傳新的物件

## 觀念

### 輪子永遠不需要轉超過 90 度

輪子指向 0 度、往前滾，和指向 180 度、往後滾，**推機器人的方向是一樣的**。所以當目標角度和目前角度差超過 90 度時，不如：

1. 把目標角度**加 180 度**（轉比較少）
2. 把目標速度**變成負的**（輪子倒著滾）

```text
目前 0 度，目標 135 度、速度 2 m/s
       ↓ optimize
目標 −45 度、速度 −2 m/s       轉 45 度就好，不用轉 135 度
```

這叫 **Optimize（最佳化）**。WPILib 的 `SwerveModuleVelocity` 已經有這個方法：

```java
target = target.optimize(getAngle());
```

[第 7 章的小練習](../07-drivetrain/swerve.md#小練習) 也提到過：機器人背對前方時，`optimize` 會讓輪子不轉、改成倒著滾。

### 還沒轉到位就全速滾

輪子從 0 度轉到 90 度大約要 0.1～0.2 秒。這段時間裡，驅動馬達已經用全速在滾，但輪子還指向錯的方向，機器人會先往錯的方向被推一下。

**Cosine compensation（餘弦補償）** 的想法是：輪子和目標方向差了 θ 度，它在目標方向上的推力只有 cos θ。所以乾脆把速度乘上 cos θ：

| 角度誤差 | cos θ | 速度變成 |
| --- | --- | --- |
| 0 度 | 1.0 | 100% |
| 30 度 | 0.87 | 87% |
| 60 度 | 0.5 | 50% |
| 90 度 | 0 | 0%，先轉好再滾 |

```java
target = target.cosineScale(getAngle());
```

`optimize` 之後角度誤差最多 90 度，所以 cos θ 不會是負的。**一定要先 `optimize` 再 `cosineScale`**。

### 這兩個方法都會回傳新的物件

`optimize()` 和 `cosineScale()` 不會修改原本的 `target`，而是**回傳一個新的** `SwerveModuleVelocity`。一定要用 `target = ...` 接住回傳值。

## 範例

修改[第 13 章 `SwerveModule`](../13-swerve-drive/module.md) 的 `setTarget()`：

```java
/** 讓模組轉到指定的角度，並用指定的速度滾動。 */
public void setTarget(SwerveModuleVelocity target) {
  Rotation2d current = getAngle();
  // 要轉超過 90 度時，改成反方向轉、輪子倒著滾
  target = target.optimize(current);
  // 輪子還沒轉到位時先放慢，避免往錯的方向推
  target = target.cosineScale(current);

  double steerVolts = steerController.calculate(current.getRadians(), target.angle.getRadians());
  steerMotor.setVoltage(steerVolts);

  double wheelRps = target.velocity / SwerveConstants.WHEEL_CIRCUMFERENCE_METERS;
  driveMotor.setControl(velocityRequest.withVelocity(wheelRps));
}
```

`getAngle()` 只讀一次，存在 `current` 裡，三個地方都用同一個值。

### 在模擬裡看到的效果

[第 15 章](../15-swerve-sim/index.md) 的模擬裡，讓機器人以 2 m/s 往前開，再突然改成往左開。加上 `optimize` 之後，左後的模組從 0 度轉到 **−90 度、速度 −2 m/s**，而不是轉到 90 度：因為它剛好在 −0.1 度，轉到 90 度要 90.1 度，超過 90 度了。其他三個模組在 0.0 或 +0.1 度，轉到 90 度剛好不超過 90 度，就正常轉過去。四個輪子都只轉了 90 度左右，0.2 秒內就到位。

## 常見錯誤

!!! bug "常見錯誤：只寫 `target.optimize(current);`"
    沒有接住回傳值，`target` 還是原本的目標，`optimize` 完全沒有作用。2026 以前 `SwerveModuleState.optimize()` 是**直接修改**物件的，從舊程式改過來時特別容易出錯。

!!! bug "常見錯誤：先 `cosineScale` 再 `optimize`"
    角度差超過 90 度時，cos θ 是負的，`cosineScale` 會先把速度變成負的，接著 `optimize` 又把它反過來，輪子就往錯的方向滾了。

!!! bug "常見錯誤：Odometry 用了目標速度"
    `optimize` 之後的目標速度可能是負的，角度也加了 180 度。計算機器人實際怎麼動時，一定要用**量到的**速度和角度（`getVelocity()`），不要用目標值。

## 小練習

1. 目前角度 30 度，目標角度 −100 度、速度 3 m/s。`optimize` 之後是多少？再 `cosineScale` 之後呢？
2. 把 `cosineScale` 那行註解掉，在模擬或架高的機器人上快速左右推搖桿，和有 `cosineScale` 的時候比較。
3. 為什麼 `optimize` 不會讓輪子**累積**轉很多圈？（提示：角度的範圍）

??? question "第 1 題參考答案"
    30 度到 −100 度差了 130 度，超過 90 度，所以 `optimize` 把目標改成 −100 + 180 = **80 度**、速度 **−3 m/s**。80 和 30 差 50 度。

    `cosineScale`：cos 50° ≈ 0.64，速度變成 −3 × 0.64 ≈ **−1.93 m/s**。

??? question "第 3 題參考答案"
    `Rotation2d` 的角度永遠在 −180～180 度之間，`optimize` 加減 180 度之後也會限制在這個範圍裡。轉向的 PID 又開了 Continuous input，永遠走比較近的那一邊。所以輪子每次最多只轉 90 度，不會因為一直繞同一個方向而累積好幾圈。
