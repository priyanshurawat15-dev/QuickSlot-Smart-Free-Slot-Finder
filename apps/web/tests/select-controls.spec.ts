import { expect, test, type Page } from "@playwright/test";

async function signIn(page: Page, role: string, returnTo: string) {
  await page.goto(`/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.getByLabel("Email address").fill(`${role}@demo.quickslot.test`);
  await page
    .getByLabel("Password", { exact: true })
    .fill(process.env.QUICKSLOT_TEST_PASSWORD || "QuickSlot-demo-2026!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(returnTo.replaceAll("/", "\\/")));
}

test("room filters and hour choices support keyboard, touch and reset", async ({
  page,
}) => {
  await page.goto("/");
  const slot = page.getByRole("combobox", { name: "Time slot", exact: true });
  await expect(slot).toHaveText("10–11");
  await slot.focus();
  await slot.press("ArrowDown");
  await expect(page.getByRole("listbox")).toBeVisible();
  await expect(
    page.getByRole("option", { name: "10–11", exact: true }),
  ).toBeFocused();
  await page.keyboard.press("ArrowDown");
  await page.keyboard.press("Enter");
  await expect(slot).toHaveText("11–12");
  await expect(slot).toBeFocused();
  await slot.click();
  await page.getByRole("option", { name: "14–15", exact: true }).click();
  await expect(slot).toHaveText("14–15");
  await page.getByRole("button", { name: "Find a room", exact: true }).click();
  await expect(page).toHaveURL(/start_time=14%3A00&end_time=15%3A00/);

  const type = page.getByRole("combobox", { name: "Room type", exact: true });
  await type.click();
  await page.keyboard.press("l");
  await page.keyboard.press("Enter");
  await expect(type).toHaveText("Lecture theatre");
  await expect(page.locator(".room-card")).toHaveCount(4);
  await type.click();
  await page
    .getByRole("option", { name: "Classrooms & LTs", exact: true })
    .click();
  await expect(type).toHaveText("Classrooms & LTs");
  await expect(page.locator(".room-card")).toHaveCount(12);
  await type.click();
  await expect(
    page.getByRole("option", { name: "Classrooms & LTs", exact: true }),
  ).toHaveAttribute("aria-selected", "true");
  await page.keyboard.press("Escape");
  await expect(type).toBeFocused();
});

test("a required room stays empty until selected", async ({
  page,
  request,
}) => {
  await signIn(page, "faculty", "/requests/new");
  const purpose = `Dropdown selection verification ${Date.now()}`;
  await page.getByLabel("Date", { exact: true }).fill("2027-01-12");
  await page.getByRole("combobox", { name: "Booking reason" }).click();
  await page.getByRole("option", { name: "Other", exact: true }).click();
  await page.getByLabel("What is the room for?").fill(purpose);
  await page.getByLabel("Expected attendance").fill("10");
  let submitted = false;
  page.on("request", (outgoing) => {
    if (
      new URL(outgoing.url()).pathname === "/api/requests" &&
      outgoing.method() === "POST"
    )
      submitted = true;
  });
  await page.getByRole("button", { name: "Send request", exact: true }).click();
  const room = page.getByRole("combobox", { name: "Room", exact: true });
  await expect(room).toHaveAttribute("aria-required", "true");
  await expect(room).toHaveAttribute("aria-invalid", "true");
  await expect(room).toBeFocused();
  expect(
    await page
      .locator(".request-sheet select[required]")
      .first()
      .evaluate(
        (element) => (element as HTMLSelectElement).validity.valueMissing,
      ),
  ).toBe(true);
  expect(submitted).toBe(false);

  const rooms = await (await request.get("/api/rooms")).json();
  await room.click();
  await page
    .getByRole("option", {
      name: `${rooms[0].code} (${rooms[0].kind === "LT" ? "LT" : "CR"})`,
      exact: true,
    })
    .click();
  await page.getByRole("button", { name: "Send request", exact: true }).click();
  await expect(page).toHaveURL(/\/requests\?created=1/);
  await expect(
    page.getByRole("heading", { name: purpose, exact: true }),
  ).toBeVisible();
  expect(submitted).toBe(true);
});

test("room type dropdown preserves its named form value", async ({ page }) => {
  await signIn(page, "admin", "/admin/rooms");
  await page.getByRole("button", { name: "Add a room", exact: true }).click();
  const type = page.getByRole("combobox", { name: "Type", exact: true });
  await type.click();
  await page
    .getByRole("option", { name: "Lecture theatre", exact: true })
    .click();
  await expect(type).toHaveText("Lecture theatre");
  expect(
    await page
      .locator(".inventory-form")
      .evaluate((form) => new FormData(form as HTMLFormElement).get("kind")),
  ).toBe("LT");
});
