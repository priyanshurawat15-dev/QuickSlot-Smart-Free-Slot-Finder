import { test, expect, type Page } from "@playwright/test";

const password = process.env.QUICKSLOT_TEST_PASSWORD || "QuickSlot-demo-2026!";

async function signIn(page: Page, email: string) {
  await page.goto("/login?returnTo=%2Fclubs%2Fcodev");
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page).toHaveURL(/\/clubs\/codev$/);
}

test("Co-Dev profile uses source contacts and has a readable mobile layout", async ({
  page,
}) => {
  await page.goto("/clubs");
  await expect(
    page.getByRole("heading", { name: "Clubs", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("img", { name: "Co-Dev Club logo" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "View club" }).click();
  await expect(
    page.getByRole("heading", { name: "Co-Dev Club", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "codev@gehu.ac.in" }),
  ).toHaveAttribute("href", "mailto:codev@gehu.ac.in");
  await expect(
    page.getByRole("link", { name: "Instagram", exact: true }),
  ).toHaveAttribute("href", "https://www.instagram.com/codev.gehu");
  await expect(
    page.getByRole("img", { name: "Co-Dev Club logo" }),
  ).toBeVisible();
  const faculty = page.getByRole("region", {
    name: "Faculty coordinator",
    exact: true,
  });
  await expect(
    faculty.getByRole("heading", { name: "Mr. Sushant Chamoli" }),
  ).toBeVisible();
  await expect(
    faculty.getByText("Faculty Mentor", { exact: true }),
  ).toBeVisible();
  const coreTeam = page.getByRole("region", { name: "Core team", exact: true });
  await expect(coreTeam.locator(".club-leader")).toHaveCount(15);
  await expect(coreTeam.getByText("Mr. Sushant Chamoli")).toHaveCount(0);
  await expect(
    page.getByRole("link", { name: "Sign in to join" }),
  ).toBeVisible();
  await page.locator("footer").scrollIntoViewIfNeeded();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
});

test("student membership is verified by the club head and never enables room booking", async ({
  browser,
}, testInfo) => {
  const baseURL = process.env.QUICKSLOT_WEB_URL || "http://127.0.0.1:5173";
  const { viewport, isMobile, hasTouch, deviceScaleFactor, userAgent } =
    testInfo.project.use;
  const contextOptions = {
    baseURL,
    viewport,
    isMobile,
    hasTouch,
    deviceScaleFactor,
    userAgent,
  };
  const studentContext = await browser.newContext(contextOptions);
  const reviewerContext = await browser.newContext(contextOptions);
  const student = await studentContext.newPage();
  const reviewer = await reviewerContext.newPage();
  try {
    await signIn(student, "student@demo.quickslot.test");
    await expect(
      student.locator(".club-membership-panel .membership-badge"),
    ).toBeVisible();
    const apply = student.getByRole("button", {
      name: /^(Apply to join|Apply again)$/,
    });
    if (await apply.isVisible()) await apply.click();
    await expect(
      student.locator(".club-membership-panel .membership-badge"),
    ).toHaveText(/Awaiting verification|Verified member/);
    await signIn(reviewer, "club@demo.quickslot.test");
    await expect(
      reviewer.getByText("Club reviewer", { exact: true }),
    ).toBeVisible();
    await expect(
      reviewer.getByRole("heading", { name: "Membership applications" }),
    ).toBeVisible();
    const application = reviewer.locator(".club-application").filter({
      has: reviewer.getByRole("heading", { name: "STUDENT", exact: true }),
    });
    await expect(application).toHaveCount(1);
    const verify = application.getByRole("button", {
      name: "Verify",
      exact: true,
    });
    if (await verify.isVisible()) {
      await verify.click();
      await application
        .getByRole("button", { name: "Confirm verification" })
        .click();
    }
    await expect(application.locator(".membership-badge")).toHaveText(
      "Verified member",
    );
    await student.reload();
    await expect(
      student.locator(".club-membership-panel .membership-badge"),
    ).toHaveText("Verified member");
    await student.goto("/requests/new");
    await expect(
      student.getByText("Your account cannot access this page.", {
        exact: false,
      }),
    ).toBeVisible();
    await expect(
      student.getByRole("button", { name: "Send request" }),
    ).toHaveCount(0);
  } finally {
    await studentContext.close();
    await reviewerContext.close();
  }
});
