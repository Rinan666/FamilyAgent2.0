import { expect, test } from '@playwright/test';

const registrationInviteCode = 'DEV-FAMILY-LOCAL';
const password = 'E2ePass123!';

type SaveDraftOptions = {
  source: string;
  tool: 'PERSONAL_MEMORY' | 'FAMILY_MEMORY';
  title: string;
  content: string;
  visibility: 'PRIVATE' | 'FAMILY_VISIBLE';
};

async function register(page: import('@playwright/test').Page, username: string, nickname: string) {
  await page.goto('/register');
  await page.locator('#register-username').fill(username);
  await page.locator('#register-invite-code').fill(registrationInviteCode);
  await page.locator('#register-nickname').fill(nickname);
  await page.locator('#register-password').fill(password);
  await page.getByTestId('register-submit').click();
  await expect(page).toHaveURL(/\/dashboard\/agent$/);
}

async function login(page: import('@playwright/test').Page, username: string) {
  await page.goto('/login');
  await page.locator('#login-username').fill(username);
  await page.locator('#login-password').fill(password);
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/dashboard\/agent$/);
}

async function clearSession(page: import('@playwright/test').Page) {
  await page.evaluate(() => localStorage.clear());
}

async function createFamily(page: import('@playwright/test').Page, familyName: string) {
  await page.goto('/dashboard/family');
  await page.getByTestId('family-create-open').first().click();
  await expect(page.getByTestId('family-create-form')).toBeVisible();
  await page.locator('input[name="newFamilyName"]').fill(familyName);
  await page.getByTestId('family-create-submit').click();
  await expect(page.getByText(familyName, { exact: true })).toBeVisible();
  return (await page.getByTestId('family-invite-code').first().innerText()).trim();
}

async function saveAgentDraft(
  page: import('@playwright/test').Page,
  options: SaveDraftOptions,
) {
  await page.goto('/dashboard/agent');
  await page.getByPlaceholder('发消息或按住说话').fill(options.source);
  await page.getByRole('button', { name: '发送消息' }).click();

  const draft = page.getByTestId('save-draft-card');
  await expect(draft).toBeVisible();
  await draft.getByLabel('保存类型').selectOption(options.tool);
  await draft.getByLabel('标题').fill(options.title);
  await draft.getByLabel('保存内容').fill(options.content);
  await draft.getByLabel('可见范围').selectOption(options.visibility);
  await draft.getByRole('button', { name: '确认保存' }).click();
  await expect(draft).not.toBeVisible();
}

test('register, login, create a family, and join it as a second member', async ({ page }) => {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 10_000)}`;
  const ownerUsername = `owner-${suffix}`;
  const memberUsername = `member-${suffix}`;
  const familyName = `E2E Family ${suffix}`;

  await register(page, ownerUsername, 'E2E Owner');
  await clearSession(page);
  await login(page, ownerUsername);

  const familyInviteCode = await createFamily(page, familyName);
  expect(familyInviteCode).toMatch(/^[A-Z0-9]{8}$/);

  await clearSession(page);
  await register(page, memberUsername, 'E2E Member');
  await page.goto('/dashboard/family');
  await page.getByTestId('family-join-open').click();
  await page.locator('input[name="inviteCode"]').fill(familyInviteCode);
  await page.getByTestId('family-join-submit').click();

  await expect(page.getByText(familyName, { exact: true })).toBeVisible();
});

test('generate an AI save draft, edit it, confirm it, and open the saved personal memory', async ({ page }) => {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 10_000)}`;
  const username = `memory-${suffix}`;
  const familyName = `E2E Memory Family ${suffix}`;
  const editedTitle = `E2E edited ${suffix}`.slice(0, 24);
  const editedContent = `E2E edited personal memory ${suffix}`;

  await register(page, username, 'E2E Memory Owner');
  await createFamily(page, familyName);
  await saveAgentDraft(page, {
    source: `保存到记忆库：E2E source memory ${suffix}`,
    tool: 'PERSONAL_MEMORY',
    title: editedTitle,
    content: editedContent,
    visibility: 'PRIVATE',
  });
  await page.getByRole('link', { name: '打开' }).last().click();
  await expect(page).toHaveURL(/\/dashboard\/memory-library\?library=personal/);
  await expect(page.getByText(editedContent, { exact: true }).last()).toBeVisible();
});

test('keep private personal memory hidden while sharing family memory with another member', async ({ page }) => {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 10_000)}`;
  const ownerUsername = `visibility-owner-${suffix}`;
  const memberUsername = `visibility-member-${suffix}`;
  const familyName = `E2E Visibility Family ${suffix}`;
  const privateContent = `E2E private personal memory ${suffix}`;
  const familyContent = `E2E family-visible memory ${suffix}`;

  await register(page, ownerUsername, 'Visibility Owner');
  const familyInviteCode = await createFamily(page, familyName);
  await saveAgentDraft(page, {
    source: `保存到记忆库：${privateContent}`,
    tool: 'PERSONAL_MEMORY',
    title: 'Private E2E memory',
    content: privateContent,
    visibility: 'PRIVATE',
  });
  await saveAgentDraft(page, {
    source: `保存到记忆库：${familyContent}`,
    tool: 'FAMILY_MEMORY',
    title: 'Family E2E memory',
    content: familyContent,
    visibility: 'FAMILY_VISIBLE',
  });

  await clearSession(page);
  await register(page, memberUsername, 'Visibility Member');
  await page.goto('/dashboard/family');
  await page.getByTestId('family-join-open').click();
  await page.locator('input[name="inviteCode"]').fill(familyInviteCode);
  await page.getByTestId('family-join-submit').click();
  await expect(page.getByText(familyName, { exact: true })).toBeVisible();

  await page.goto('/dashboard/memory-library?library=personal');
  await expect(page.getByText(privateContent, { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '家族记忆库' }).click();
  await expect(page.getByText(familyContent, { exact: true })).toBeVisible();
  await expect(page.getByText(privateContent, { exact: true })).toHaveCount(0);
});
