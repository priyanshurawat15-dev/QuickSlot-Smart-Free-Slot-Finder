import { test, expect } from "@playwright/test";

const password = process.env.QUICKSLOT_TEST_PASSWORD || "QuickSlot-demo-2026!";

async function signIn(page: import("@playwright/test").Page, email: string) {
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}

test("public search uses the API and exposes its coverage limits", async ({
  page,
}) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Room availability", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("About these results")).toBeVisible();
  await expect(
    page.getByText(/Check the room in person before settling in/),
  ).toBeVisible();
  await expect(page.locator(".room-row").first()).toBeVisible();
  await expect(page.locator(".user-nav")).toHaveCount(0);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
});

test("faculty sign-in preserves the selected room and interval", async ({
  page,
  request,
}) => {
  const response = await request.get("/api/rooms");
  expect(response.ok()).toBeTruthy();
  const rooms = (await response.json()) as {
    id: string;
    code: string;
    kind: string;
  }[];
  expect(rooms.length).toBeGreaterThan(0);
  const date = "2027-01-12";
  await page.goto(
    `/requests/new?room_id=${rooms[0].id}&date=${date}&start_time=12:00&end_time=13:00`,
  );
  await expect(page).toHaveURL(/\/login\?returnTo=/);
  await signIn(page, "faculty@demo.quickslot.test");
  await expect(page).toHaveURL(/\/requests\/new\?/);
  await expect(
    page.getByRole("combobox", { name: "Room", exact: true }),
  ).toContainText(`${rooms[0].code} (${rooms[0].kind === "LT" ? "LT" : "CR"})`);
  await expect(page.getByLabel("Date", { exact: true })).toHaveValue(date);
  await expect(
    page.getByRole("combobox", { name: "Time slot", exact: true }),
  ).toHaveText("12–13");
});

test("student request access follows verified club membership", async ({
  page,
}) => {
  await page.goto("/login?returnTo=%2Frequests%2Fnew");
  await signIn(page, "student@demo.quickslot.test");
  await expect(page).toHaveURL(/\/requests\/new$/);
  const response = await page.request.get("/api/requests/eligibility");
  expect(response.ok()).toBeTruthy();
  const eligibility = await response.json();
  if (eligibility.can_request) {
    await expect(
      page.getByRole("combobox", { name: "Request for club" }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Send request" }),
    ).toBeVisible();
  } else {
    await expect(
      page.getByText("Join a club and have your membership verified", {
        exact: false,
      }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Send request" }),
    ).toHaveCount(0);
  }
});
