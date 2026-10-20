import { test, expect } from "@playwright/test";
import path from "node:path";
import { readFile } from "node:fs/promises";

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

  const fileBytes = await readFile(
    path.resolve("public/templates/timetable-template.docx"),
  );
  const transfer = await page.evaluateHandle((base64) => {
    const bytes = Uint8Array.from(atob(base64), (character) =>
      character.charCodeAt(0),
    );
    const data = new DataTransfer();
    data.items.add(
      new File([bytes], "timetable-template.docx", {
        type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      }),
    );
    return data;
  }, fileBytes.toString("base64"));
  const dropzone = page.locator(".timetable-dropzone");
  await dropzone.dispatchEvent("dragover", { dataTransfer: transfer });
  await expect(dropzone).toHaveClass(/is-dragging/);
  await dropzone.dispatchEvent("drop", { dataTransfer: transfer });
  await expect(
    dropzone.getByText("timetable-template.docx", { exact: true }),
  ).toBeVisible();
  await transfer.dispose();
  await page
    .getByLabel("Replacement scope")
    .fill(`Browser validity review ${Date.now()}`);
  await page.getByLabel("Effective from").fill("2026-07-13");
  const uploaded = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/timetables") &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Upload timetable" }).click();
  const response = await uploaded;
  expect(response.ok()).toBeTruthy();
  const version = await response.json();
  expect(version.filename).toBe("timetable-template.docx");
  expect(version.valid_until).toBeNull();
  expect(version.publication_windows).toEqual([]);
  await expect(page.locator(".version-heading")).toContainText(
    "From 2026-07-13 · no end date",
  );
  await expect(
    page.getByRole("button", { name: "Remove from schedule" }),
  ).toHaveCount(0);
});

test("deleting an uploaded timetable moves its source into hidden history", async ({
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
    .setInputFiles(path.resolve("public/templates/timetable-template.docx"));
  const scope = `Browser deletion review ${Date.now()}`;
  await page.getByLabel("Replacement scope").fill(scope);
  await page.getByLabel("Effective from").fill("2026-07-13");
  const uploaded = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/timetables") &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Upload timetable" }).click();
  const uploadResponse = await uploaded;
  expect(uploadResponse.ok()).toBeTruthy();
  const version = await uploadResponse.json();
  await page.getByRole("button", { name: "All timetables" }).click();
  const card = page.locator(".timetable-file-card").filter({ hasText: scope });
  await card
    .getByRole("button", {
      name: "Delete timetable-template.docx",
      exact: true,
    })
    .click();
  await expect(
    card.getByText(/Its active classes and room mappings will be removed/),
  ).toBeVisible();
  const deleted = page.waitForResponse(
    (response) =>
      response.url().includes(`/api/timetables/${version.id}?`) &&
      response.request().method() === "DELETE",
  );
  await card
    .getByRole("button", { name: "Delete timetable", exact: true })
    .click();
  const deleteResponse = await deleted;
  expect(deleteResponse.ok()).toBeTruthy();
  expect((await deleteResponse.json()).archived_at).toBeTruthy();
  await expect(
    page.locator(".timetable-current").getByText(scope, { exact: true }),
  ).toHaveCount(0);
  const history = page.locator("details.timetable-disclosure");
  await expect(history).not.toHaveAttribute("open", "");
  await page.getByText(/^Logs and history ·/).click();
  const historicalCard = history
    .locator(".timetable-file-card")
    .filter({ hasText: scope });
  await expect(
    historicalCard.getByText("Deleted", { exact: true }),
  ).toBeVisible();
  await historicalCard
    .getByRole("button", { name: /View room mappings/ })
    .click();
  await expect(
    page.getByText(/It no longer supplies active classes or room mappings/),
  ).toBeVisible();
  await page.getByText("Advanced session editor", { exact: true }).click();
  const source = await page.request.get(`/api/timetables/${version.id}/source`);
  expect(source.ok()).toBeTruthy();
  expect(source.headers()["content-type"]).toContain(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  );
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
  await page.getByRole("button", { name: "Upload timetable" }).click();
  const response = await uploaded;
  expect(response.ok()).toBeTruthy();
  const version = await response.json();
  expect(version.sessions.length).toBeGreaterThan(50);
  await expect(
    page.getByRole("heading", { name: "Check room mappings", exact: true }),
  ).toBeVisible();
  const matchedRooms = new Set(
    version.sessions
      .map((session: { room_id: string | null }) => session.room_id)
      .filter(Boolean),
  );
  await expect(page.locator(".mapping-card")).toHaveCount(matchedRooms.size);
  await expect(page.locator(".review-table")).not.toBeVisible();
  await page.getByText("Advanced session editor", { exact: true }).click();
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
    page.locator('section[aria-label="Timetable review issues"]'),
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
    .locator(".timetable-file-card")
    .filter({ hasText: version.scope_key })
    .getByRole("button", { name: /View room mappings/ })
    .click();
  await page.getByText("Advanced session editor", { exact: true }).click();
  await expect(
    page.getByLabel("Subject for row 1", { exact: true }),
  ).toBeVisible();
  await expect(page.locator(".review-table tbody tr")).toHaveCount(25);
});
