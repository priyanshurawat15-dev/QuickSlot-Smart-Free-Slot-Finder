import { expect, test } from "@playwright/test";

test("unverified schedules have no public verification badge or false free label", async ({ page, request }) => {
  const query = "date=2027-01-12&start_time=10:00&end_time=11:00";
  const response = await request.get(`/api/availability?${query}`);
  expect(response.ok()).toBeTruthy();
  const data = await response.json() as { rooms: { id: string; state: string }[] };
  const room = data.rooms.find((item) => item.state === "UNKNOWN");
  test.skip(!room, "This dataset has no room with an unverified schedule.");
  await page.goto(`/?${query}`);
  const card = page.locator(`.room-card[href*="/rooms/${room!.id}?"]`);
  await expect(card).toBeVisible();
  await expect(card.locator(".status")).toHaveCount(0);
  await expect(card).not.toHaveClass(/room-card-available/);
  await expect(page.getByText("Needs verification", { exact: true })).toHaveCount(0);
});

test("requesting a room as a guest opens sign-in and preserves room and hours", async ({ page, request }) => {
  const response = await request.get("/api/rooms");
  expect(response.ok()).toBeTruthy();
  const rooms = await response.json() as { id: string; code: string }[];
  const room = rooms[0];
  expect(room).toBeTruthy();
  await page.goto(`/rooms/${room.id}?date=2027-01-12&start_time=10:00&end_time=11:00`);
  await page.getByRole("link", { name: "Request this room" }).click();
  await expect(page).toHaveURL(/\/login\?returnTo=/);
  const destination = new URL(page.url()).searchParams.get("returnTo");
  expect(destination).toContain(`room_id=${room.id}`);
  expect(destination).toContain("start_time=10:00&end_time=11:00");
  await page.getByLabel("Email address").fill("faculty@demo.quickslot.test");
  await page.getByLabel("Password", { exact: true }).fill(process.env.QUICKSLOT_TEST_PASSWORD || "QuickSlot-demo-2026!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL(/\/requests\/new\?/);
  await expect(page.getByRole("combobox", { name: "Room", exact: true })).toContainText(room.code);
  await expect(page.getByRole("combobox", { name: "Time slot", exact: true })).toHaveText(/10–11/);
});
