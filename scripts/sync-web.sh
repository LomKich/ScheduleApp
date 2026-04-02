#!/usr/bin/env bash
# scripts/sync-web.sh
# Локальная сборка веб-версии из Android-ресурсов.
# Запускай перед `wrangler dev` или `wrangler deploy`.
#
# Использование:
#   bash scripts/sync-web.sh          # сборка
#   bash scripts/sync-web.sh --watch  # сборка + слежение за изменениями

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$REPO_ROOT/app/src/main/assets"
DIST="$REPO_ROOT/web/_dist"
WATCH_MODE=false

[ "$1" = "--watch" ] && WATCH_MODE=true

build() {
  echo "🔨 Building web dist from Android assets..."
  rm -rf "$DIST"
  mkdir -p "$DIST/js"

  # ── index.html ──────────────────────────────────────────────────
  # Убираем desktop-patch.js (Electron-only), добавляем SW-регистрацию
  sed '/desktop-patch\.js/d' "$ASSETS/index.html" \
    | sed 's|</body>|<script>if("serviceWorker"in navigator)navigator.serviceWorker.register("/sw.js");<\/script>\n</body>|' \
    | sed 's|</head>|<link rel="manifest" href="/manifest.json">\n</head>|' \
    > "$DIST/index.html"

  # ── JS файлы ────────────────────────────────────────────────────
  for f in "$ASSETS/js"/*.js; do
    fname=$(basename "$f")
    [ "$fname" = "desktop-patch.js" ] && continue  # Electron-only, пропускаем
    cp "$f" "$DIST/js/$fname"
  done

  # ── Веб-специфичные файлы ────────────────────────────────────────
  cp "$REPO_ROOT/web/sw.js" "$DIST/sw.js"

  # ── PWA Manifest ────────────────────────────────────────────────
  cat > "$DIST/manifest.json" << 'EOF'
{
  "name": "ScheduleApp",
  "short_name": "Расписание",
  "description": "Расписание занятий",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#1c1c1e",
  "theme_color": "#e87722",
  "icons": [
    { "src": "/icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "/icon-512.png", "sizes": "512x512", "type": "image/png" }
  ]
}
EOF

  echo "✅ Done: $(ls "$DIST/js" | wc -l) JS files, $(wc -c < "$DIST/index.html") bytes HTML"
  echo "   → $DIST"
}

build

if $WATCH_MODE; then
  echo ""
  echo "👀 Watching for changes in $ASSETS ..."
  echo "   Press Ctrl+C to stop"
  echo ""

  # Используем inotifywait если есть, иначе fswatch (macOS)
  if command -v inotifywait &>/dev/null; then
    while inotifywait -r -e modify,create,delete "$ASSETS/js" "$ASSETS/index.html" -q; do
      echo "🔄 Change detected, rebuilding..."
      build
    done
  elif command -v fswatch &>/dev/null; then
    fswatch -o "$ASSETS/js" "$ASSETS/index.html" | while read; do
      echo "🔄 Change detected, rebuilding..."
      build
    done
  else
    echo "⚠️  Install inotifywait (Linux) или fswatch (macOS) для watch-режима"
    echo "   Linux:  sudo apt install inotify-tools"
    echo "   macOS:  brew install fswatch"
  fi
fi
