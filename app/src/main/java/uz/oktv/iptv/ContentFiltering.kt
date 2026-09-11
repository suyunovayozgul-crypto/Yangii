package uz.oktv.iptv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberFilteredChannels(
    selectedCategory: String,
    activeChannels: List<M3UChannel>,
    favorites: List<Int>,
    contentMode: AppContentMode,
    currentLang: AppLang,
    vodSearchQuery: String,
    selectedVodGenre: String,
    selectedVodYear: String
): List<M3UChannel> {

    return remember(
        selectedCategory,
        activeChannels,
        favorites,
        contentMode,
        currentLang,
        vodSearchQuery,
        selectedVodGenre,
        selectedVodYear
    ) {
        val allLabel = Strings.get("all", currentLang)

        if (contentMode == AppContentMode.VOD) {
            var list = activeChannels

            if (
                !selectedVodGenre.equals("Все", ignoreCase = true) &&
                !selectedVodGenre.equals("Все жанры", ignoreCase = true)
            ) {
                list = list.filter {
                    it.group.trim().equals(
                        selectedVodGenre.trim(),
                        ignoreCase = true
                    )
                }
            }

            if (!selectedVodYear.equals("Все", ignoreCase = true)) {
                list = list.filter {
                    it.name.contains(selectedVodYear, ignoreCase = true)
                }
            }

            if (vodSearchQuery.length >= 2) {
                val q = cleanTitleForSearch(vodSearchQuery)

                list = list.filter {
                    cleanTitleForSearch(it.name).contains(
                        q,
                        ignoreCase = true
                    )
                }
            }

            list
        } else {
            when {
                selectedCategory.equals(allLabel, ignoreCase = true) ||
                    selectedCategory.equals("Все", ignoreCase = true) -> {
                    activeChannels
                }

                selectedCategory.contains("Избранное", ignoreCase = true) ||
                    selectedCategory.contains("Favorites", ignoreCase = true) -> {
                    activeChannels
                        .filter { favorites.contains(it.id) }
                        .sortedBy { favorites.indexOf(it.id) }
                }

                else -> {
                    activeChannels.filter {
                        it.group.trim().equals(
                            selectedCategory.trim(),
                            ignoreCase = true
                        )
                    }
                }
            }
        }
    }
}
