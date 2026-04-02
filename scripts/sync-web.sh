#!/usr/bin/env bash
# scripts/sync-web.sh
# ─────────────────────────────────────────────────────────────────────
# Локальная сборка веб-версии из Android-ресурсов.
# Запускай когда хочешь посмотреть как выглядит веб локально.
#
# Использование:
#   bash scripts/sync-web.sh            # собрать один раз
#   bash scripts/sync-web.sh --serve    # собрать + запустить локальный сервер
#   bash scripts/sync-web.sh --watch    # пересобирать при изменениях
# ─────────────────────────────────────────────────────────────────────

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$REPO_ROOT/app/src/main/assets"
DIST="$REPO_ROOT/_site"
SERVE=false
WATCH=false

for arg in "$@"; do
  [ "$arg" = "--serve" ] && SERVE=true
  [ "$arg" = "--watch" ] && WATCH=true
done

build() {
  echo "🔨 Сборка веб-версии..."
  rm -rf "$DIST"
  mkdir -p "$DIST/js"

  # index.html — убираем desktop-patch.js, добавляем SW и manifest
  sed '/desktop-patch\.js/d' "$ASSETS/index.html" \
    | sed 's|</body>|<script>if("serviceWorker"in navigator)navigator.serviceWorker.register("/sw.js");<\/script>\n</body>|' \
    | sed 's|</head>|<link rel="manifest" href="/manifest.json">\n</head>|' \
    > "$DIST/index.html"

  # JS — всё кроме desktop-patch.js
  for f in "$ASSETS/js"/*.js; do
    fname=$(basename "$f")
    [ "$fname" = "desktop-patch.js" ] && continue
    cp "$f" "$DIST/js/$fname"
  done

  # Service Worker
  cp "$REPO_ROOT/web/sw.js" "$DIST/sw.js"

  # PWA Manifest
  cat > "$DIST/manifest.json" << 'EOF'
{
  "name": "ScheduleApp",
  "short_name": "Расписание",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#1c1c1e",
  "theme_color": "#e87722"
}
EOF

  echo "✅ Готово: $(ls "$DIST/js" | wc -l) JS файлов → $DIST"
}

build

if $SERVE; then
  echo ""
  echo "🌐 Запускаем локальный сервер на http://localhost:8080"
  echo "   Ctrl+C чтобы остановить"
  echo ""
  # Python есть на любом Mac/Linux
  if command -v python3 &>/dev/null; then
    cd "$DIST" && python3 -m http.server 8080
  else
    echo "❌ Нужен python3. Установи: sudo apt install python3"
  fi
fi

if $WATCH && ! $SERVE; then
  echo ""
  echo "👀 Слежу за изменениями в $ASSETS ..."
  echo "   Ctrl+C чтобы остановить"
  if command -v inotifywait &>/dev/null; then
    while inotifywait -r -e modify,create,delete "$ASSETS/js" "$ASSETS/index.html" -q; do
      echo "🔄 Изменение detected, пересобираю..."
      build
    done
  elif command -v fswatch &>/dev/null; then
    fswatch -o "$ASSETS/js" "$ASSETS/index.html" | while read; do
      echo "🔄 Изменение detected, пересобираю..."
      build
    done
  else
    echo "⚠️  Установи: sudo apt install inotify-tools  (Linux)"
    echo "              brew install fswatch              (macOS)"
  fi
fi
