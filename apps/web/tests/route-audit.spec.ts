import { test, expect, type Page, type TestInfo } from "@playwright/test";

const password = process.env.QUICKSLOT_TEST_PASSWORD || "QuickSlot-demo-2026!";

async function login(page: Page, role: string) {
  await page.context().clearCookies();
  await page.goto("/login");
  await page.getByLabel("Email address").fill(`${role}@demo.quickslot.test`);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL("/");
}

async function inspectPage(page: Page, info: TestInfo, name: string) {
  await page.locator("main").waitFor();
  await page.mouse.wheel(0, -10000);
  await page.screenshot({ path: info.outputPath(`${name}-top.png`) });
  const height = await page
    .locator("body")
    .evaluate((element) => element.scrollHeight);
  const viewport = page.viewportSize()?.height || 800;
  for (let offset = viewport; offset < height; offset += viewport) {
    await page.mouse.wheel(0, viewport);
  }
  await page.locator("footer").scrollIntoViewIfNeeded();
  await page.screenshot({ path: info.outputPath(`${name}-bottom.png`) });
  await expect(page.locator("footer")).toBeInViewport();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
}

test("all roles can complete their real request and administration screens", async ({
  page,
  request,
}, info) => {
  const rooms = (await (await request.get("/api/rooms")).json()) as {
    id: string;
    code: string;
  }[];
  const room = rooms[0];
  expect(room).toBeTruthy();
  await page.goto("/");
  await expect(page.getByText("About these results")).toBeVisible();
  await inspectPage(page, info, "finder");
  await page.goto(
    `/rooms/${room.id}?date=2027-01-12&start_time=12:00&end_time=13:00`,
  );
  await expect(
    page.getByRole("heading", { name: `Room ${room.code}.` }),
  ).toBeVisible();
  await inspectPage(page, info, "room-detail");
  await page.goto("/login");
  await inspectPage(page, info, "login");

  await login(page, "faculty");
  await page.goto(
    `/requests/new?room_id=${room.id}&date=2027-01-12&start_time=12:00&end_time=13:00`,
  );
  await expect(
    page.getByRole("button", { name: "Send request" }),
  ).toBeVisible();
  await inspectPage(page, info, "request-form");
  const purpose = `Browser workflow verification ${Date.now()}`;
  await page.getByRole("combobox", { name: "Booking reason" }).click();
  await page.getByRole("option", { name: "Other", exact: true }).click();
  await page.getByLabel("What is the room for?").fill(purpose);
  await page.getByLabel("Expected attendance").fill("5");
  await page.getByRole("button", { name: "Send request" }).click();
  await expect(page).toHaveURL(/\/requests\?created=1/);
  await expect(page.getByRole("heading", { name: purpose })).toBeVisible();
  await inspectPage(page, info, "faculty-requests");
  await page.goto("/teachers");
  await expect(
    page.getByRole("heading", { name: "Faculty timetable" }),
  ).toBeVisible();
  await inspectPage(page, info, "teacher-schedules");

  await login(page, "club");
  await page.goto("/requests");
  await expect(
    page.getByRole("heading", { name: "My requests." }),
  ).toBeVisible();
  await inspectPage(page, info, "club-requests");

  await login(page, "coordinator");
  await page.goto("/approvals");
  const coordinatorCard = page
    .locator(".request-card")
    .filter({ hasText: purpose });
  await expect(coordinatorCard).toBeVisible();
  await inspectPage(page, info, "coordinator-queue");
  await coordinatorCard
    .getByRole("button", { name: "Approve", exact: true })
    .click();
  await coordinatorCard
    .getByRole("button", { name: "Confirm decision" })
    .click();
  await expect(page.getByRole("alert")).toContainText(/coverage|capacity/i);
  await coordinatorCard.getByRole("button", { name: "Keep reviewing" }).click();
  await coordinatorCard.getByRole("button", { name: "Forward to HOD" }).click();
  await coordinatorCard
    .getByLabel("Reason (required)")
    .fill("Test request needs department review.");
  await coordinatorCard
    .getByRole("button", { name: "Confirm decision" })
    .click();
  await expect(
    coordinatorCard.getByText("With HOD", { exact: true }),
  ).toBeVisible();

  await login(page, "hod");
  await page.goto("/approvals");
  const hodCard = page.locator(".request-card").filter({ hasText: purpose });
  await expect(hodCard).toBeVisible();
  await inspectPage(page, info, "hod-queue");
  await hodCard.getByRole("button", { name: "Reject", exact: true }).click();
  await hodCard
    .getByLabel("Reason (required)")
    .fill("Browser verification completed; no booking needed.");
  await hodCard.getByRole("button", { name: "Confirm decision" }).click();
  await expect(hodCard.getByText("Rejected", { exact: true })).toBeVisible();

  await login(page, "admin");
  await page.goto("/admin/timetables");
  await expect(
    page.getByRole("heading", { name: "Timetables.", exact: true }),
  ).toBeVisible();
  await expect(page.locator(".timetable-file-card").first()).toBeVisible();
  await inspectPage(page, info, "timetable-register");
  await page.goto("/admin/rooms");
  await expect(
    page.getByRole("heading", { name: "Room register." }),
  ).toBeVisible();
  await inspectPage(page, info, "room-register");
  await page.getByRole("button", { name: "Add a room", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Add a classroom or lecture theatre" }),
  ).toBeVisible();
  await inspectPage(page, info, "room-form");
});
