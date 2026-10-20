import { test, expect } from "@playwright/test";
import path from "node:path";

test("administrator uploads a timetable without inventing an end date", async ({
  page,
}) => {
  await page.goto("/login");
  await page.getByLabel("Email address").fill("admin@demo.quickslot.test");
  await page
    .getByLabel("Password", { exact: true })
    .fill(process.env.QUICKSLOT_TEST_PASSWORD || "QuickSlot-demo-2026!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL("/");
  await page.goto("/admin/timetables");
  await expect(page.getByLabel("Valid until", { exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Set an end date")).not.toBeChecked();
  await page.getByLabel("Set an end date").check();
  await expect(page.getByLabel("Valid until", { exact: true })).toBeVisible();
  await page.getByLabel("Set an end date").uncheck();

  await page
    .getByLabel("PDF or editable Word timetable")
    .setInputFiles(path.resolve("public/templates/timetable-template.docx"));
  await page
    .getByLabel("Replacement scope")
    .fill(`Browser validity review ${Date.now()}`);
  await page.getByLabel("Effective from").fill("2026-07-13");
  const uploaded = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/timetables") &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Upload draft" }).click();
  const response = await uploaded;
  expect(response.ok()).toBeTruthy();
  const version = await response.json();
  expect(version.valid_until).toBeNull();
  expect(version.publication_windows).toEqual([]);
  await expect(page.locator(".version-heading")).toContainText(
    "From 2026-07-13 · no end date",
  );
  await expect(
    page.getByRole("button", { name: "Remove from schedule" }),
  ).toHaveCount(0);
});

test("PDF review paginates without losing edits on other pages", async ({
  page,
}) => {
  await page.goto("/login");
  await page.getByLabel("Email address").fill("admin@demo.quickslot.test");
  await page
    .getByLabel("Password", { exact: true })
    .fill(process.env.QUICKSLOT_TEST_PASSWORD || "QuickSlot-demo-2026!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL("/");
  await page.goto("/admin/timetables");
  await page
    .getByLabel("PDF or editable Word timetable")
    .setInputFiles(
      path.resolve("../api/src/main/resources/timetables/B.Tech-CSE-III.pdf"),
    );
  await page
    .getByLabel("Replacement scope")
    .fill(`Browser pagination review ${Date.now()}`);
  await page.getByLabel("Effective from").fill("2026-07-13");
  const uploaded = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/timetables") &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Upload draft" }).click();
  const response = await uploaded;
  expect(response.ok()).toBeTruthy();
  const version = await response.json();
  expect(version.sessions.length).toBeGreaterThan(50);
  await expect(page.locator(".review-table tbody tr")).toHaveCount(25);
  await expect(
    page.getByRole("button", { name: "Previous sessions" }),
  ).toBeDisabled();
  await page.getByRole("button", { name: "Next sessions" }).click();
  const subject = page.getByLabel("Subject for row 26", { exact: true });
  await expect(subject).toBeVisible();
  const editedSubject = `${await subject.inputValue()} review check`;
  await subject.fill(editedSubject);
  await expect(
    page.getByRole("region", { name: "Timetable review issues" }),
  ).toHaveCount(1);
  expect(
    await page.evaluate(
      () =>
        document.documentElement.scrollWidth <=
        document.documentElement.clientWidth,
    ),
  ).toBeTruthy();
  await page.getByRole("button", { name: "Previous sessions" }).click();
  await expect(
    page.getByLabel("Subject for row 26", { exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Next sessions" }).click();
  await expect(subject).toHaveValue(editedSubject);
  await page.getByRole("combobox", { name: "Sessions per page" }).click();
  await page
    .getByRole("option", { name: "50 sessions per page", exact: true })
    .click();
  await expect(page.locator(".review-table tbody tr")).toHaveCount(50);
  await expect(
    page.getByRole("button", { name: "Previous sessions" }),
  ).toBeDisabled();
  await expect(subject).toHaveValue(editedSubject);
  await page.getByRole("button", { name: "All timetables" }).click();
  await page
    .locator(".version-row")
    .filter({ hasText: version.scope_key })
    .click();
  await expect(
    page.getByLabel("Subject for row 1", { exact: true }),
  ).toBeVisible();
  await expect(page.locator(".review-table tbody tr")).toHaveCount(25);
});
