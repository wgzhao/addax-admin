import { test, expect } from '@playwright/test'

test('table search should return expected source table', async ({ page }) => {
  await page.goto('/')

  const tableMenuLink = page.getByRole('link', { name: '采集表管理' })
  if (await tableMenuLink.count()) {
    await tableMenuLink.first().click()
  } else {
    await page.getByRole('button', { name: '采集表管理' }).click()
  }

  await expect(page).toHaveURL(/\/table$/)

  await page.getByRole('textbox', { name: '关键字' }).fill('odsrk.sscf_move_record')
  await page.getByRole('button', { name: '查询' }).click()

  const rows = page.locator('table tbody tr')
  await expect(rows).toHaveCount(1, { timeout: 20_000 })

  const rowCount = await rows.count()
  const rowText = (await rows.first().innerText()).replace(/\s+/g, ' ').trim()
  console.log(`[table-search] rowCount=${rowCount}`)
  console.log(`[table-search] firstRow=${rowText}`)
  await test.info().attach('table-search-result', {
    body: `rowCount=${rowCount}\nfirstRow=${rowText}`,
    contentType: 'text/plain'
  })

  await expect(rows.first()).toContainText('csyy_system.sscf_move_record')
})
