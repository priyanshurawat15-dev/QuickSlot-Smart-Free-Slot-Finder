import { expect, test } from "@playwright/test";

test("faculty lookup is hidden publicly and denied to student accounts", async ({page}) => {
  await page.goto("/");
  await expect(page.getByRole("link", {name:"Faculty timetable", exact:true})).toHaveCount(0);
  await page.goto("/teachers");
  await expect(page).toHaveURL(/\/login\?returnTo=/);
  await page.getByLabel("Email address").fill("student@demo.quickslot.test");
  await page.getByLabel("Password", {exact:true}).fill(process.env.QUICKSLOT_TEST_PASSWORD || "QuickSlot-demo-2026!");
  await page.getByRole("button", {name:"Sign in",exact:true}).click();
  await expect(page.getByText("Your account cannot access this page.",{exact:false})).toBeVisible();
  await expect(page.getByRole("link", {name:"Faculty timetable",exact:true})).toHaveCount(0);
  expect((await page.request.get("/api/timetables/teachers")).status()).toBe(403);
});
