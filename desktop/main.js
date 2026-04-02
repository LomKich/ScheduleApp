const { app, BrowserWindow, shell, dialog, ipcMain, Notification, protocol, net, globalShortcut } = require('electron');
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');
const os = require('os');
const { execFile, spawn } = require('child_process');

let mainWindow;

// ── Путь к общим assets (работает и в dev-режиме, и в собранном .exe) ────────
// В dev:       desktop/../app/src/main/assets
// В packaged:  resources/app/src/main/assets  (через extraResources)
const ASSETS_DIR = app.isPackaged
  ? path.join(process.resourcesPath, 'app', 'src', 'main', 'assets')
  : path.join(__dirname, '..', 'app', 'src', 'main', 'assets');

// ══════════════════════════════════════════════════════════════════
// ── ROTATING FILE LOGGER ──────────────────────────────────────────
// Хранит максимум 4 файла в папке logs/ рядом с приложением.
// Имена: app_YYYYMMDD_HHMMSS.log  (каждый запуск = новый файл)
// ══════════════════════════════════════════════════════════════════
const LOG_DIR   = path.join(__dirname, 'logs');
const LOG_MAX   = 4;
let   _logFile  = null;

function _setupLogger() {
  try {
    if (!fs.existsSync(LOG_DIR)) fs.mkdirSync(LOG_DIR, { recursive: true });

    // Имя текущего файла — по дате запуска
    const now  = new Date();
    const pad  = n => String(n).padStart(2, '0');
    const name = `app_${now.getFullYear()}${pad(now.getMonth()+1)}${pad(now.getDate())}`
               + `_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}.log`;
    _logFile = path.join(LOG_DIR, name);

    // Ротация: удаляем старые если > MAX
    const files = fs.readdirSync(LOG_DIR)
      .filter(f => f.startsWith('app_') && f.endsWith('.log'))
      .map(f => ({ name: f, mtime: fs.statSync(path.join(LOG_DIR, f)).mtimeMs }))
      .sort((a, b) => a.mtime - b.mtime); // старые в начале
    while (files.length >= LOG_MAX) {
      const old = files.shift();
      try { fs.unlinkSync(path.join(LOG_DIR, old.name)); } catch(_) {}
    }

    sLog('info', `Logger started → ${name}`);
  } catch(e) {
    console.error('[Logger] setup failed:', e.message);
  }
}

function sLog(level, ...args) {
  const line = `[${new Date().toISOString()}] [${level.toUpperCase()}] ${args.join(' ')}\n`;
  if (['error','warn','info'].includes(level)) console.log(line.trimEnd());
  if (_logFile) {
    try { fs.appendFileSync(_logFile, line); } catch(_) {}
  }
}

// ══════════════════════════════════════════════════════════════════
// ── OTA Auto-Update via GitHub Releases ───────────────────────────
// ══════════════════════════════════════════════════════════════════
const GITHUB_OWNER  = 'LomKich';          // ← ваш GitHub username
const GITHUB_REPO   = 'ScheduleApp';      // ← репозиторий
const UPDATE_INTERVAL_MS = 60 * 60 * 1000; // проверять каждый час

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    const opts = {
      hostname: 'api.github.com',
      path: url,
      method: 'GET',
      headers: {
        'User-Agent': `ScheduleApp-Desktop/${app.getVersion()}`,
        'Accept': 'application/vnd.github.v3+json',
      },
    };
    const req = https.request(opts, (res) => {
      let data = '';
      res.on('data', chunk => (data += chunk));
      res.on('end', () => {
        try { resolve(JSON.parse(data)); }
        catch (e) { reject(e); }
      });
    });
    req.on('error', reject);
    req.end();
  });
}

function semverGt(a, b) {
  // returns true if version string a > b  (e.g. "1.2.3" > "1.2.0")
  const pa = a.replace(/^v/, '').split('.').map(Number);
  const pb = b.replace(/^v/, '').split('.').map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const na = pa[i] || 0, nb = pb[i] || 0;
    if (na > nb) return true;
    if (na < nb) return false;
  }
  return false;
}

// Находим нужный asset для текущей платформы
function findAssetForPlatform(assets) {
  const plat = process.platform; // 'win32' | 'darwin' | 'linux'
  const arch  = process.arch;    // 'x64' | 'arm64'
  const exts = plat === 'win32' ? ['.exe', '.msi'] :
               plat === 'darwin' ? ['.dmg'] :
               ['.AppImage', '.deb', '.rpm'];
  for (const ext of exts) {
    const found = assets.find(a =>
      a.name.toLowerCase().endsWith(ext) &&
      (arch === 'arm64' ? a.name.includes('arm64') || a.name.includes('aarch64') : !a.name.includes('arm64'))
    );
    if (found) return found;
  }
  return assets[0] || null;
}

async function checkForUpdates(silent = false) {
  try {
    const release = await fetchJson(`/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/latest`);
    if (!release || !release.tag_name) { if (!silent) showNoUpdate(); return; }

    const latestVer = release.tag_name.replace(/^v/, '');
    const currentVer = app.getVersion();

    if (!semverGt(latestVer, currentVer)) {
      if (!silent) showNoUpdate(currentVer);
      return;
    }

    // Нашли новую версию!
    const asset   = findAssetForPlatform(release.assets || []);
    const body    = (release.body || '').slice(0, 600);
    const btnLabel = asset ? 'Скачать и установить' : 'Открыть страницу релиза';

    const { response } = await dialog.showMessageBox(mainWindow, {
      type: 'info',
      title: '🔄 Доступно обновление',
      message: `Новая версия: v${latestVer}\nТекущая: v${currentVer}`,
      detail: body || 'Нажмите «Скачать и установить» чтобы обновиться.',
      buttons: [btnLabel, 'Позже'],
      defaultId: 0,
      cancelId: 1,
    });

    if (response === 1) return;

    if (!asset) {
      shell.openExternal(release.html_url);
      return;
    }

    // Скачиваем установщик
    await downloadAndInstall(asset, release.html_url);

  } catch (err) {
    if (!silent) {
      dialog.showMessageBox(mainWindow, {
        type: 'error',
        title: 'Ошибка проверки обновлений',
        message: 'Не удалось подключиться к GitHub: ' + err.message,
        buttons: ['OK'],
      });
    }
  }
}

function showNoUpdate(ver) {
  dialog.showMessageBox(mainWindow, {
    type: 'info',
    title: 'Обновлений нет',
    message: `У вас последняя версия${ver ? ` (v${ver})` : ''}.`,
    buttons: ['OK'],
  });
}

function downloadAndInstall(asset, fallbackUrl) {
  return new Promise(async (resolve) => {
    const tmpDir   = os.tmpdir();
    const destPath = path.join(tmpDir, asset.name);

    // Прогресс-диалог через уведомление
    if (Notification.isSupported()) {
      new Notification({ title: '⬇️ Загрузка обновления', body: asset.name }).show();
    }

    // Если файл уже скачан — проверяем размер (старый кэш мог быть битым или от другой версии)
    if (fs.existsSync(destPath)) {
      const stat = fs.statSync(destPath);
      const expectedSize = asset.size || 0;
      if (expectedSize > 0 && Math.abs(stat.size - expectedSize) < 1024) {
        sLog('info', 'Installer already cached, reusing:', destPath);
        launchInstaller(destPath, fallbackUrl);
        resolve();
        return;
      } else {
        // Размер не совпадает — удаляем старый и качаем заново
        sLog('info', 'Cached installer size mismatch, re-downloading');
        try { fs.unlinkSync(destPath); } catch(_) {}
      }
    }

    try {
      await downloadFile(asset.browser_download_url, destPath);
      launchInstaller(destPath, fallbackUrl);
    } catch (err) {
      dialog.showMessageBox(mainWindow, {
        type: 'error',
        title: 'Ошибка загрузки',
        message: 'Не удалось скачать установщик. Открываем страницу для ручной загрузки.',
        buttons: ['OK'],
      });
      shell.openExternal(fallbackUrl);
    }
    resolve();
  });
}

function downloadFile(url, dest) {
  return new Promise((resolve, reject) => {
    // Поддержка редиректов (GitHub CDN)
    function get(u, redirects = 0) {
      if (redirects > 5) return reject(new Error('Too many redirects'));
      const lib = u.startsWith('https') ? https : http;
      lib.get(u, { headers: { 'User-Agent': `ScheduleApp-Desktop/${app.getVersion()}` } }, (res) => {
        if (res.statusCode === 301 || res.statusCode === 302 || res.statusCode === 307) {
          return get(res.headers.location, redirects + 1);
        }
        if (res.statusCode !== 200) return reject(new Error('HTTP ' + res.statusCode));
        const out = fs.createWriteStream(dest);
        res.pipe(out);
        out.on('finish', () => out.close(resolve));
        out.on('error', reject);
      }).on('error', reject);
    }
    get(url);
  });
}

function launchInstaller(filePath, fallbackUrl) {
  dialog.showMessageBox(mainWindow, {
    type: 'info',
    title: '✅ Загрузка завершена',
    message: 'Установщик скачан. Приложение закроется для установки обновления.',
    buttons: ['Установить сейчас'],
    defaultId: 0,
  }).then(() => {
    try {
      if (process.platform === 'win32') {
        // shell.openPath надёжнее spawn — работает с пробелами в пути и UAC
        shell.openPath(filePath);
        // Удаляем temp-файл через PowerShell с задержкой (нельзя удалить запущенный .exe сразу)
        // NSIS-установщик копирует себя во временную папку системы, поэтому удаление безопасно
        const cleanupCmd = `Start-Sleep -Seconds 15; Remove-Item -Force -LiteralPath '${filePath.replace(/'/g, "''")}' -ErrorAction SilentlyContinue`;
        spawn('powershell', ['-NonInteractive', '-Command', cleanupCmd],
          { detached: true, stdio: 'ignore' }).unref();
      } else if (process.platform === 'darwin') {
        shell.openPath(filePath);
        // Удаляем dmg через 15 секунд
        setTimeout(() => {
          try { fs.unlinkSync(filePath); } catch(_) {}
        }, 15000);
      } else {
        // Linux: делаем исполняемым, запускаем, удаляем через 30 сек
        fs.chmodSync(filePath, 0o755);
        spawn(filePath, [], { detached: true, stdio: 'ignore' }).unref();
        setTimeout(() => {
          try { fs.unlinkSync(filePath); } catch(_) {}
        }, 30000);
      }
    } catch (err) {
      sLog('error', 'launchInstaller spawn failed:', err.message);
      // Fallback: открываем папку с установщиком чтобы пользователь запустил вручную
      shell.showItemInFolder(filePath);
      return;
    }
    // Даём установщику 600мс инициализироваться до того как Electron завершится
    setTimeout(() => app.quit(), 600);
  });
}

// ── Register custom protocol schemes before app is ready ─────────────────────
// We intercept https://sounds.local/* → assets/sounds/*
// and https://dice.local/*  → assets/dice/*
// This mirrors what Android does with shouldInterceptRequest.
protocol.registerSchemesAsPrivileged([
  { scheme: 'https', privileges: { standard: true, secure: true, supportFetchAPI: true } },
]);

function createWindow() {
  // ── 4:3 portrait window sizing ──────────────────────────────────────────
  // Вычисляем размер окна под доступную площадь экрана, соблюдая соотношение 3:4
  const { screen } = require('electron');
  const { width: sw, height: sh } = screen.getPrimaryDisplay().workAreaSize;
  // Для мобильного контента — portrait: ширина × 4/3 = высота
  const WIN_H = Math.min(Math.round(sh * 0.92), 900);
  const WIN_W = Math.round(WIN_H * 3 / 4); // соотношение 3:4 (portrait 4:3)

  mainWindow = new BrowserWindow({
    width: WIN_W,
    height: WIN_H,
    minWidth: 300,
    minHeight: 400,
    title: 'ScheduleApp',
    backgroundColor: '#0d0d0d',
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'default',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      webSecurity: false,           // allow loading local assets + supabase
      allowRunningInsecureContent: true,
      // Allow media (camera/mic) for voice/video recording
      experimentalFeatures: true,
    },
    show: false,
  });

  mainWindow.loadFile(path.join(ASSETS_DIR, 'index.html'));

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

  // Open external links in browser, not in Electron
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  // Grant media permissions (camera, mic)
  mainWindow.webContents.session.setPermissionRequestHandler((wc, perm, cb) => {
    const allowed = ['media', 'camera', 'microphone', 'notifications', 'clipboard-read'];
    cb(allowed.includes(perm));
  });
}

app.whenReady().then(() => {
  _setupLogger();
  sLog('info', 'App starting, version ' + (app.getVersion ? app.getVersion() : '?'));
  // ── Intercept https://sounds.local/* and https://dice.local/* ──────────────
  const assetsDir = ASSETS_DIR;
  const { session } = require('electron');

  session.defaultSession.webRequest.onBeforeRequest(
    { urls: ['https://sounds.local/*', 'https://dice.local/*'] },
    (details, callback) => {
      const url = new URL(details.url);
      let localPath;
      if (url.hostname === 'sounds.local') {
        localPath = path.join(assetsDir, 'sounds', decodeURIComponent(url.pathname.slice(1)));
      } else if (url.hostname === 'dice.local') {
        localPath = path.join(assetsDir, 'dice', decodeURIComponent(url.pathname.slice(1)));
      }
      if (localPath && fs.existsSync(localPath)) {
        callback({ redirectURL: `file://${localPath.replace(/\\/g, '/')}` });
      } else {
        callback({});
      }
    }
  );

  createWindow();

  // ── Хоткей для секретной консоли CMD: Ctrl+Shift+C / Cmd+Shift+C ──────────
  globalShortcut.register('CommandOrControl+Shift+C', () => {
    if (mainWindow) {
      mainWindow.webContents
        .executeJavaScript('if(typeof cmdOpen==="function")cmdOpen()')
        .catch(() => {});
    }
  });

  // ── Авто-проверка обновлений при старте (через 5 сек после запуска) ──
  setTimeout(() => checkForUpdates(true), 5000);
  // Повторная проверка каждый час
  setInterval(() => checkForUpdates(true), UPDATE_INTERVAL_MS);

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('will-quit', () => {
  globalShortcut.unregisterAll();
});

// ─── IPC handlers (called from preload.js) ────────────────────────────────────

// Open URL in default browser
ipcMain.handle('open-url', async (_, url) => {
  shell.openExternal(url);
});

// Show OS notification
ipcMain.handle('show-notification', async (_, title, body) => {
  if (Notification.isSupported()) {
    new Notification({ title, body }).show();
  }
});

// File picker for background image
ipcMain.handle('pick-image', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Выберите изображение',
    filters: [{ name: 'Images', extensions: ['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp'] }],
    properties: ['openFile'],
  });
  if (result.canceled || result.filePaths.length === 0) return null;
  const data = fs.readFileSync(result.filePaths[0]);
  const ext = path.extname(result.filePaths[0]).slice(1).toLowerCase();
  const mime = ext === 'jpg' ? 'image/jpeg' : `image/${ext}`;
  return `data:${mime};base64,${data.toString('base64')}`;
});

// File picker for uploads (any file)
ipcMain.handle('pick-file', async (_, accept) => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Выберите файл',
    properties: ['openFile'],
  });
  if (result.canceled || result.filePaths.length === 0) return null;
  const filePath = result.filePaths[0];
  const data = fs.readFileSync(filePath);
  const name = path.basename(filePath);
  return {
    name,
    base64: data.toString('base64'),
    size: data.length,
    path: filePath,
  };
});

// File picker for video avatar
ipcMain.handle('pick-video', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Выберите видео для аватарки',
    filters: [{ name: 'Video', extensions: ['mp4', 'webm', 'mov', 'avi', 'mkv'] }],
    properties: ['openFile'],
  });
  if (result.canceled || result.filePaths.length === 0) return null;
  const filePath = result.filePaths[0];
  const stat = fs.statSync(filePath);
  if (stat.size > 20 * 1024 * 1024) return { error: 'too_large' };
  const data = fs.readFileSync(filePath);
  const ext = path.extname(filePath).slice(1).toLowerCase();
  const mime = ext === 'mov' ? 'video/mp4' : `video/${ext}`;
  return `data:${mime};base64,${data.toString('base64')}`;
});

// Save file to Downloads
ipcMain.handle('save-file', async (_, { name, base64, mime }) => {
  const downloads = app.getPath('downloads');
  const filePath = path.join(downloads, name);
  const buf = Buffer.from(base64, 'base64');
  fs.writeFileSync(filePath, buf);
  shell.showItemInFolder(filePath);
  return filePath;
});

// Native HTTP fetch (bypass CORS)
ipcMain.handle('native-fetch', async (_, url, options) => {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const lib = parsed.protocol === 'https:' ? https : http;
    const opts = {
      hostname: parsed.hostname,
      port: parsed.port || (parsed.protocol === 'https:' ? 443 : 80),
      path: parsed.pathname + parsed.search,
      method: options?.method || 'GET',
      headers: options?.headers || {},
    };
    const req = lib.request(opts, (res) => {
      let data = '';
      res.on('data', (chunk) => (data += chunk));
      res.on('end', () => resolve({ status: res.statusCode, body: data, headers: res.headers }));
    });
    req.on('error', reject);
    if (options?.body) req.write(options.body);
    req.end();
  });
});

// Get app version
ipcMain.handle('get-version', async () => {
  return app.getVersion();
});

// Manual update check (called from renderer via preload)
ipcMain.handle('check-updates', async () => {
  await checkForUpdates(false);
});

// Clear cache
ipcMain.handle('clear-cache', async () => {
  await mainWindow.webContents.session.clearCache();
});

// Reload webview to a URL
ipcMain.handle('load-url', async (_, url) => {
  mainWindow.webContents.loadURL(url);
});

// Write log from renderer
ipcMain.handle('write-log', async (_, level, msg) => {
  sLog(level || 'info', '[renderer]', msg);
});

// Typed file picker: accepts filter array [{name, extensions}] or accept string
ipcMain.handle('pick-file-typed', async (_, opts) => {
  const { title = 'Выберите файл', filters, multiple = false } = opts || {};
  const result = await dialog.showOpenDialog(mainWindow, {
    title,
    filters: filters || [{ name: 'All Files', extensions: ['*'] }],
    properties: multiple ? ['openFile', 'multiSelections'] : ['openFile'],
  });
  if (result.canceled || !result.filePaths.length) return null;

  const results = [];
  for (const filePath of result.filePaths) {
    const data = fs.readFileSync(filePath);
    const name = path.basename(filePath);
    const ext  = path.extname(filePath).slice(1).toLowerCase();
    // Detect MIME
    const MIME_MAP = {
      jpg:'image/jpeg', jpeg:'image/jpeg', png:'image/png', gif:'image/gif',
      webp:'image/webp', bmp:'image/bmp', svg:'image/svg+xml',
      mp4:'video/mp4', webm:'video/webm', mov:'video/quicktime', avi:'video/x-msvideo',
      mkv:'video/x-matroska',
      mp3:'audio/mpeg', ogg:'audio/ogg', wav:'audio/wav', flac:'audio/flac',
      aac:'audio/aac', m4a:'audio/m4a', opus:'audio/opus', wma:'audio/x-ms-wma',
      pdf:'application/pdf', zip:'application/zip', rar:'application/x-rar-compressed',
      doc:'application/msword', docx:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      xls:'application/vnd.ms-excel', xlsx:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      txt:'text/plain',
    };
    const mime = MIME_MAP[ext] || 'application/octet-stream';
    results.push({ name, base64: data.toString('base64'), size: data.length, mime, path: filePath });
  }
  sLog('info', `pick-file-typed: ${results.map(r=>r.name).join(', ')}`);
  return multiple ? results : results[0];
});

// ── Upload file to catbox.moe CDN (used by nativeUploadFileAsync) ────────────
ipcMain.handle('upload-to-catbox', async (_, { base64, fileName, mimeType }) => {
  const buf = Buffer.from(base64, 'base64');
  const boundary = '----ScheduleAppUpload' + Date.now().toString(36);

  // Build multipart/form-data body manually
  const reqtypePart = Buffer.from(
    `--${boundary}\r\n` +
    `Content-Disposition: form-data; name="reqtype"\r\n\r\n` +
    `fileupload\r\n`
  );
  const fileHeader = Buffer.from(
    `--${boundary}\r\n` +
    `Content-Disposition: form-data; name="fileToUpload"; filename="${fileName}"\r\n` +
    `Content-Type: ${mimeType}\r\n\r\n`
  );
  const fileFooter = Buffer.from(`\r\n--${boundary}--\r\n`);

  const body = Buffer.concat([reqtypePart, fileHeader, buf, fileFooter]);

  return new Promise((resolve, reject) => {
    const opts = {
      hostname: 'catbox.moe',
      path: '/user.php',
      method: 'POST',
      headers: {
        'Content-Type': `multipart/form-data; boundary=${boundary}`,
        'Content-Length': body.length,
        'User-Agent': `ScheduleApp-Desktop/${app.getVersion()}`,
      },
    };
    const req = https.request(opts, (res) => {
      let data = '';
      res.on('data', chunk => (data += chunk));
      res.on('end', () => {
        const url = data.trim();
        if (url.startsWith('https://') || url.startsWith('http://')) {
          resolve(url);
        } else {
          reject(new Error('Catbox upload failed: ' + url.substring(0, 120)));
        }
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
});
