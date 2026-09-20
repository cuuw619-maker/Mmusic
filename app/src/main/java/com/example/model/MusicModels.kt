package com.example.model

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val contentUriString: String,
    val albumArtUriString: String? = null,
    val dataPath: String = "",
    val mimeType: String = "audio/*",
    val dateAdded: Long = 0L,
    val size: Long = 0L,
    val folderName: String = "",
    val genre: String = "Unknown",
    val year: Int = 0,
    val bitrateKbps: Int = 0,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L
) {
    val durationFormatted: String
        get() = formatDuration(durationMs)

    val format: String
        get() {
            val extension = dataPath.substringAfterLast('.', "").uppercase()
            if (extension.isNotEmpty() && extension.length <= 4) return extension
            return when {
                mimeType.contains("flac", ignoreCase = true) -> "FLAC"
                mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "M4A"
                mimeType.contains("aac", ignoreCase = true) -> "AAC"
                mimeType.contains("wav", ignoreCase = true) -> "WAV"
                mimeType.contains("ogg", ignoreCase = true) -> "OGG"
                mimeType.contains("opus", ignoreCase = true) -> "OPUS"
                else -> "MP3"
            }
        }

    val sizeFormatted: String
        get() {
            if (size <= 0) return ""
            val mb = size / (1024.0 * 1024.0)
            return "%.1f MB".format(mb)
        }

    val qualityBadge: String
        get() {
            val sb = StringBuilder(format)
            if (bitrateKbps > 0) {
                sb.append(" • ").append(bitrateKbps).append(" kbps")
            }
            return sb.toString()
        }

    companion object {
        fun formatDuration(durationMs: Long): String {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val albumArtUriString: String? = null,
    val year: Int = 0
)

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int
)

data class Genre(
    val name: String,
    val songCount: Int
)

data class Folder(
    val path: String,
    val name: String,
    val songCount: Int
)

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val songCount: Int = 0,
    val firstSongAlbumArt: String? = null
)

enum class SortOrder(val displayName: String) {
    TITLE_ASC("Название (А → Я)"),
    TITLE_DESC("Название (Я → А)"),
    ARTIST_ASC("Исполнитель (А → Я)"),
    ARTIST_DESC("Исполнитель (Я → А)"),
    ALBUM_ASC("Альбом (А → Я)"),
    ALBUM_DESC("Альбом (Я → А)"),
    DATE_ADDED_DESC("Дата добавления (новые → старые)"),
    DATE_ADDED_ASC("Дата добавления (старые → новые)"),
    DURATION_DESC("Продолжительность (длинные → короткие)"),
    DURATION_ASC("Продолжительность (короткие → длинные)"),
    PLAY_COUNT_DESC("Количество воспроизведений (по убыванию)"),
    YEAR_DESC("Год (новые → старые)"),
    YEAR_ASC("Год (старые → новые)")
}

enum class RepeatMode(val value: Int, val label: String) {
    OFF(0, "Без повтора"),
    ALL(1, "Повторять все"),
    ONE(2, "Повторять один")
}

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Song> = emptyList(),
    val currentQueueIndex: Int = -1,
    val errorMessage: String? = null
)

data class EqualizerBand(
    val bandIndex: Short,
    val centerFreqHz: Int,
    val minLevelMilliBels: Short,
    val maxLevelMilliBels: Short,
    val currentLevelMilliBels: Short
)

data class EqualizerState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    val bassBoostStrength: Short = 0,
    val virtualizerStrength: Short = 0,
    val loudnessEnhancerStrength: Int = 0,
    val presets: List<String> = emptyList(),
    val currentPresetIndex: Short = -1
)

enum class SleepTimerOption(val minutes: Int, val label: String) {
    OFF(0, "Выключен"),
    MIN_15(15, "15 минут"),
    MIN_30(30, "30 минут"),
    MIN_45(45, "45 минут"),
    MIN_60(60, "60 минут"),
    MIN_90(90, "90 минут"),
    END_OF_TRACK(-1, "До конца текущего трека")
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val totalMinutes: Int = 0,
    val secondsRemaining: Int = 0,
    val stopAtEndOfTrack: Boolean = false
) {
    val formattedRemaining: String
        get() {
            if (!isActive) return ""
            if (stopAtEndOfTrack) return "До конца трека"
            val m = secondsRemaining / 60
            val s = secondsRemaining % 60
            return "%02d:%02d".format(m, s)
        }
}

enum class SearchCategory(val title: String) {
    ALL("Все"),
    SONGS("Треки"),
    ALBUMS("Альбомы"),
    ARTISTS("Исполнители"),
    PLAYLISTS("Плейлисты")
}

data class SearchResult(
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList()
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}
