import os

file_path = "app/src/main/java/uz/oktv/iptv/MainActivity.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Проверим, есть ли уже функция, чтобы не дублировать
if "fun FullscreenGestureControls" in content:
    print("Функция FullscreenGestureControls уже добавлена.")
else:
    print("Документ готов к патчу. Укажи точный маркер или давай вынесем блок вручную.")

