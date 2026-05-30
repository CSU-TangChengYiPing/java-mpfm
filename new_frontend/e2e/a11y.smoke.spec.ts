import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

async function assertNoSeriousA11yViolations(page: Page, pageUrl: string): Promise<void> {
  await page.goto(pageUrl);
  await expect(page.locator("body")).toBeVisible();
  const scanResult = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa"])
    .analyze();
  const seriousOrCritical = scanResult.violations.filter((item) => item.impact === "serious" || item.impact === "critical");
  expect(
    seriousOrCritical,
    `页面 ${pageUrl} 存在严重可访问性问题: ${seriousOrCritical.map((v) => v.id).join(", ")}`
  ).toEqual([]);
}

test("public auth pages should pass a11y smoke checks", async ({ page }) => {
  await assertNoSeriousA11yViolations(page, "/login");
  await assertNoSeriousA11yViolations(page, "/register");
});
