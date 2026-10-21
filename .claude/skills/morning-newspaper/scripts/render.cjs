#!/usr/bin/env node
// Render a filled newspaper HTML file to a Letter-size PDF with Playwright's Chromium.
// Usage: node render.cjs <input.html> <output.pdf>
const { existsSync } = require('node:fs');
const { resolve, join } = require('node:path');
const { pathToFileURL } = require('node:url');
const { execSync } = require('node:child_process');

function loadPlaywright() {
  try {
    return require('playwright');
  } catch {
    // Fall back to a global install (npm i -g playwright).
    return require(join(execSync('npm root -g').toString().trim(), 'playwright'));
  }
}

async function main() {
  const [input, output] = process.argv.slice(2);
  if (!input || !output) {
    console.error('Usage: node render.cjs <input.html> <output.pdf>');
    process.exit(1);
  }
  const { chromium } = loadPlaywright();
  const executablePath = process.env.CHROMIUM_PATH
    || (existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined);
  const browser = await chromium.launch(executablePath ? { executablePath } : {});
  try {
    const page = await browser.newPage();
    await page.goto(pathToFileURL(resolve(input)).href, { waitUntil: 'load' });
    const pdf = await page.pdf({ path: output, format: 'Letter', printBackground: true, preferCSSPageSize: true });
    const pages = (pdf.toString('latin1').match(/\/Type\s*\/Page[^s]/g) || []).length;
    console.log(`Wrote ${output} (${pages} page${pages === 1 ? '' : 's'})`);
  } finally {
    await browser.close();
  }
}

main().catch((err) => { console.error(err); process.exit(1); });
