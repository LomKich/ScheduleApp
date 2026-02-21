#!/usr/bin/env python3
"""
Скрипт для установки иконки приложения перед компиляцией.

Использование:
    python3 set_icon.py icon.png

Требования:
    pip install Pillow

Что делает:
    - Берёт любое PNG/JPG изображение (лучше квадратное, мин. 512x512)
    - Нарезает его во все нужные размеры для Android
    - Кладёт файлы в правильные папки mipmap-*
    - Поддерживает как обычную (квадрат), так и круглую иконку
"""

import sys
import os
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Установи Pillow: pip install Pillow")
    sys.exit(1)

# Размеры иконок для каждой папки mipmap
SIZES = {
    'mipmap-mdpi':    48,
    'mipmap-hdpi':    72,
    'mipmap-xhdpi':   96,
    'mipmap-xxhdpi':  144,
    'mipmap-xxxhdpi': 192,
}

RES_DIR = Path(__file__).parent / 'app' / 'src' / 'main' / 'res'


def make_square(img):
    """Обрезает изображение до квадрата по центру."""
    w, h = img.size
    size = min(w, h)
    left = (w - size) // 2
    top = (h - size) // 2
    return img.crop((left, top, left + size, top + size))


def make_round(img, size):
    """Создаёт круглую версию иконки с прозрачным фоном."""
    img = img.resize((size, size), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (size, size), 0)
    from PIL import ImageDraw
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size, size), fill=255)
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    result.paste(img, mask=mask)
    return result


def set_icon(source_path: str):
    source = Path(source_path)
    if not source.exists():
        print(f"Файл не найден: {source_path}")
        sys.exit(1)

    print(f"Загружаю: {source}")
    img = Image.open(source).convert("RGBA")

    # Делаем квадратным
    img = make_square(img)
    w, h = img.size
    print(f"Исходный размер: {w}x{h} → обрезано до {min(w,h)}x{min(w,h)}")

    if min(w, h) < 192:
        print(f"ПРЕДУПРЕЖДЕНИЕ: рекомендуется минимум 512x512, у тебя {w}x{h}")

    count = 0
    for folder, size in SIZES.items():
        dir_path = RES_DIR / folder
        dir_path.mkdir(parents=True, exist_ok=True)

        # Обычная квадратная иконка
        square = img.resize((size, size), Image.LANCZOS)
        out = dir_path / 'ic_launcher.png'
        square.save(out, 'PNG', optimize=True)

        # Круглая иконка
        round_img = make_round(img, size)
        out_round = dir_path / 'ic_launcher_round.png'
        round_img.save(out_round, 'PNG', optimize=True)

        print(f"  ✅ {folder}: {size}x{size}px")
        count += 2

    print(f"\nГотово! Создано {count} файлов.")
    print("Теперь можно компилировать APK.")


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Использование: python3 set_icon.py <путь_к_иконке.png>")
        print("Пример:        python3 set_icon.py my_icon.png")
        sys.exit(1)

    set_icon(sys.argv[1])
