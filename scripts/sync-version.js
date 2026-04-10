#!/usr/bin/env node
/**
 * sync-version.js
 * Читает APP_VERSION и APP_VERSION_CODE из gradle.properties
 * и обновляет:
 *   - desktop/package.json        → "version"
 *   - app/src/main/assets/js/core.js → строку с '4.x.xx' (fallback-версия)
 *
 * Запуск: node scripts/sync-version.js
 * Запускается автоматически через GitHub Actions перед сборкой.
 */

const fs   = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');

// ── Читаем gradle.properties ──────────────────────────────────────────
const propsPath = path.join(ROOT, 'gradle.properties');
const propsText = fs.readFileSync(propsPath, 'utf8');

function prop(name) {
  const m = propsText.match(new RegExp(`^${name}=(.+)$`, 'm'));
  if (!m) throw new Error(`Свойство ${name} не найдено в gradle.properties`);
  return m[1].trim();
}

const VERSION      = prop('APP_VERSION');
const VERSION_CODE = prop('APP_VERSION_CODE');

console.log(`📦 Синхронизирую версию: ${VERSION} (code ${VERSION_CODE})`);

// ── desktop/package.json ──────────────────────────────────────────────
const pkgPath = path.join(ROOT, 'desktop', 'package.json');
const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
pkg.version = VERSION;
fs.writeFileSync(pkgPath, JSON.stringify(pkg, null, 2) + '\n');
console.log(`  ✅ desktop/package.json → ${VERSION}`);

// ── core.js (fallback-версия) ─────────────────────────────────────────
const corePath = path.join(ROOT, 'app', 'src', 'main', 'assets', 'js', 'core.js');
let core = fs.readFileSync(corePath, 'utf8');

// Заменяем строку вида: : '4.x.xx';
const corePatched = core.replace(
  /(window\.Android\.getAppVersion\(\)\s*\)\s*\n\s*: ')[\d.]+(')/,
  `$1${VERSION}$2`
);

if (corePatched === core) {
  console.warn('  ⚠️  core.js: паттерн fallback-версии не найден, пропускаю');
} else {
  fs.writeFileSync(corePath, corePatched);
  console.log(`  ✅ core.js (APP_VERSION fallback) → ${VERSION}`);
}

console.log('✅ Версия синхронизирована!');
