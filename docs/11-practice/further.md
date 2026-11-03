# 延伸學習

這一節的內容不是必讀，第一次讀可以先跳過。

## AdvantageKit

**AdvantageKit** 是 FRC 6328 Mechanical Advantage 開發的記錄框架，比 `DataLogManager` 更進一步：它記錄機器人程式的**所有輸入**（感測器、手把、相機），之後可以在電腦上**重新執行一次程式**，用完全相同的輸入重現比賽中發生的事，甚至修改程式後看看結果會不會不同。這叫 **Log replay**。

代價是程式的架構要配合它設計（每個 Subsystem 拆成「讀取硬體的 IO 層」和「邏輯層」）。2027 alpha 7 的 vendordep 清單裡已經有 AdvantageKit 的 alpha 版本，有興趣的話可以參考它的官方文件。

## 單元測試

WPILib 的專案已經設定好 **JUnit**，可以為程式寫**單元測試 (Unit test)**：不需要機器人，在電腦上自動檢查程式的邏輯對不對。適合測試的東西：

- 單位換算（齒輪比、公尺和圈數）
- 自動程式的組合有沒有用到正確的 Subsystem
- 狀態機的轉換邏輯

```java
@Test
void rotationsToMetersIsCorrect() {
  // 馬達轉 GEAR_RATIO 圈 = 輪子轉 1 圈 = 輪子周長
  double meters = Drivetrain.rotationsToMeters(DrivetrainConstants.GEAR_RATIO);
  assertEquals(Math.PI * DrivetrainConstants.WHEEL_DIAMETER_METERS, meters, 1e-9);
}
```

測試放在 `src/test/java/` 裡，用 `./gradlew test` 或 VS Code 的測試面板執行。（要測試 `rotationsToMeters` 的話，要把它從 `private` 改成 package-private 或 `public`）

## Code review

在 Pull request 上請隊友看過程式碼再合併，叫做 **Code review**。它的好處不只是抓錯誤：

- 至少有**兩個人**看得懂每一段程式，負責的人不在時也有人能修
- 新手透過 review 學習學長姐的寫法，學長姐透過回答問題整理自己的觀念

Review 時可以問的問題：

- 這段程式在 Command 被中斷時會怎樣？馬達會停嗎？
- 數字有沒有放在 `Constants`？單位清楚嗎？
- 有沒有在機器人上測試過？

## Epilogue

WPILib 內建的 **Epilogue** 可以用 `@Logged` 標註，自動把 Subsystem 的欄位和方法回傳值送到 Telemetry，不用手動寫一堆 `Telemetry.log()`。2027 alpha 裡仍然有 Epilogue，但它和新的 Telemetry API 搭配的方式，以 WPILib 文件的最新說明為準。

## 持續學習

- **看強隊的程式碼**：很多強隊在 GitHub 公開整個賽季的程式。讀完這份教學後，你應該已經看得懂大部分的結構了
- **Chief Delphi**：遇到問題時搜尋，常常能找到別隊遇過一樣的狀況
- **WPILib 文件**：2027 正式版發布後，官方文件會更完整，也會有 Commands v3、OpModeRobot 等新功能的說明
- **回頭修改這份教學**：你學到的東西、踩過的坑，寫成新的內容幫助下一屆的學弟妹（見[寫作規範](../appendix/style-guide.md)）
