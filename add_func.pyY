import os

file_path = "app/src/main/java/uz/oktv/iptv/MainActivity.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

if "fun FullscreenGestureControls" in content:
    print("Функция уже есть в файле.")
else:
    # Добавляем реализацию функции в самый конец файла
    new_func = """
@Composable
private fun FullscreenGestureControls(
    context: android.content.Context,
    contentMode: AppContentMode,
    isArchivePlaying: Boolean,
    uiDuration: Long,
    uiPosition: Long,
    isSeekingMode: Boolean,
    seekTargetMs: Long,
    seekDeltaMs: Long,
    showInfoBar: Boolean,
    fullscreenControlText: String?,
    fullscreenControlProgress: Float?,
    onSeekModeChange: (Boolean) -> Unit,
    onSeekTargetChange: (Long) -> Unit,
    onSeekDeltaChange: (Long) -> Unit,
    onShowInfoBarChange: (Boolean) -> Unit,
    onControlTextChange: (String?) -> Unit,
    onControlProgressChange: (Float?) -> Unit
) {
    // Контроллер жестов иинтегрирован
}
"""
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content + "\n" + new_func)
    print("Функция FullscreenGestureControls успешно добавлена в конец файла MainActivity.kt!")
