package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Standard vector icon registry for consistent, scalable Material 3 Expressive iconography.
 */
object AppIcons {
    val Home: ImageVector = Icons.Rounded.Home
    val Search: ImageVector = Icons.Rounded.Search
    val Library: ImageVector = Icons.Rounded.MusicNote
    val Playlist: ImageVector = Icons.AutoMirrored.Rounded.QueueMusic
    val PlaylistAdd: ImageVector = Icons.AutoMirrored.Rounded.PlaylistAdd
    val Settings: ImageVector = Icons.Rounded.Settings

    val Play: ImageVector = Icons.Rounded.PlayArrow
    val Pause: ImageVector = Icons.Rounded.Pause
    val Previous: ImageVector = Icons.Rounded.SkipPrevious
    val Next: ImageVector = Icons.Rounded.SkipNext
    val Shuffle: ImageVector = Icons.Rounded.Shuffle
    val Repeat: ImageVector = Icons.Rounded.Repeat
    val RepeatOne: ImageVector = Icons.Rounded.RepeatOne

    val Rewind: ImageVector = Icons.Rounded.Replay
    val Forward: ImageVector = Icons.Rounded.Refresh

    val FavoriteFilled: ImageVector = Icons.Rounded.Favorite
    val FavoriteOutlined: ImageVector = Icons.Rounded.FavoriteBorder

    val Queue: ImageVector = Icons.AutoMirrored.Rounded.QueueMusic
    val Equalizer: ImageVector = Icons.Rounded.Equalizer
    val Speed: ImageVector = Icons.Rounded.Speed
    val Pitch: ImageVector = Icons.Rounded.GraphicEq
    val SleepTimer: ImageVector = Icons.Rounded.Bedtime
    val Tune: ImageVector = Icons.Rounded.Tune

    val Download: ImageVector = Icons.Rounded.Download
    val Share: ImageVector = Icons.Rounded.Share
    val More: ImageVector = Icons.Rounded.MoreVert
    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val Close: ImageVector = Icons.Rounded.Close
    val Volume: ImageVector = Icons.AutoMirrored.Rounded.VolumeUp
}
