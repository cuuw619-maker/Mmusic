package com.example.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Album
import com.example.model.Artist
import com.example.model.Folder
import com.example.model.Genre
import com.example.model.Song
import com.example.model.SortOrder
import com.example.ui.components.AlbumCard
import com.example.ui.components.ArtistListItem
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FolderListItem
import com.example.ui.components.SongListItem
import com.example.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val songs by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentSort by viewModel.sortOrder.collectAsStateWithLifecycle()

    val selectedAlbum by viewModel.selectedAlbum.collectAsStateWithLifecycle()
    val selectedArtist by viewModel.selectedArtist.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Песни", "Альбомы", "Исполнители", "Папки", "Жанры")
    var showSortMenu by remember { mutableStateOf(false) }

    // Detail Drill-down handling
    if (selectedAlbum != null) {
        val album = selectedAlbum!!
        val albumSongs = viewModel.repository.getSongsForAlbum(album.id)
        DetailListScreen(
            title = album.title,
            subtitle = "${album.artist} • ${albumSongs.size} треков",
            songs = albumSongs,
            playbackState = playbackState,
            onBack = { viewModel.selectAlbum(null) },
            onPlaySong = { song -> viewModel.playSong(song, albumSongs) },
            onPlayAll = { viewModel.playQueue(albumSongs, 0) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) },
            onAddToQueue = { viewModel.addToQueue(it) }
        )
        return
    }

    if (selectedArtist != null) {
        val artist = selectedArtist!!
        val artistSongs = viewModel.repository.getSongsForArtist(artist.name)
        DetailListScreen(
            title = artist.name,
            subtitle = "${artistSongs.size} треков",
            songs = artistSongs,
            playbackState = playbackState,
            onBack = { viewModel.selectArtist(null) },
            onPlaySong = { song -> viewModel.playSong(song, artistSongs) },
            onPlayAll = { viewModel.playQueue(artistSongs, 0) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) },
            onAddToQueue = { viewModel.addToQueue(it) }
        )
        return
    }

    if (selectedFolder != null) {
        val folder = selectedFolder!!
        val folderSongs = viewModel.repository.getSongsForFolder(folder.name)
        DetailListScreen(
            title = folder.name,
            subtitle = "${folderSongs.size} файлов • ${folder.path}",
            songs = folderSongs,
            playbackState = playbackState,
            onBack = { viewModel.selectFolder(null) },
            onPlaySong = { song -> viewModel.playSong(song, folderSongs) },
            onPlayAll = { viewModel.playQueue(folderSongs, 0) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) },
            onAddToQueue = { viewModel.addToQueue(it) }
        )
        return
    }

    if (selectedGenre != null) {
        val genre = selectedGenre!!
        val genreSongs = viewModel.repository.getSongsForGenre(genre.name)
        DetailListScreen(
            title = genre.name,
            subtitle = "${genreSongs.size} треков",
            songs = genreSongs,
            playbackState = playbackState,
            onBack = { viewModel.selectGenre(null) },
            onPlaySong = { song -> viewModel.playSong(song, genreSongs) },
            onPlayAll = { viewModel.playQueue(genreSongs, 0) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) },
            onAddToQueue = { viewModel.addToQueue(it) }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        // Search Bar & Sort Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Поиск трека, альбома, артиста...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Поиск",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("library_search_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Sort Menu Button
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.testTag("library_sort_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sort,
                        contentDescription = "Сортировка",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortOrder.values().forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = order.displayName,
                                    fontWeight = if (currentSort == order) FontWeight.Bold else FontWeight.Normal,
                                    color = if (currentSort == order) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                viewModel.setSortOrder(order)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Tabs: Songs, Albums, Artists, Folders, Genres
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                )
            }
        }

        // Content for selected Tab
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> SongsTab(
                    songs = songs,
                    playbackState = playbackState,
                    onPlaySong = { song -> viewModel.playSong(song, songs) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onAddToPlaylist = { viewModel.setSongToAddToPlaylist(it) },
                    onAddToQueue = { viewModel.addToQueue(it) }
                )
                1 -> AlbumsTab(
                    albums = albums,
                    onSelectAlbum = { viewModel.selectAlbum(it) }
                )
                2 -> ArtistsTab(
                    artists = artists,
                    onSelectArtist = { viewModel.selectArtist(it) }
                )
                3 -> FoldersTab(
                    folders = folders,
                    onSelectFolder = { viewModel.selectFolder(it) }
                )
                4 -> GenresTab(
                    genres = genres,
                    onSelectGenre = { viewModel.selectGenre(it) }
                )
            }
        }
    }
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    playbackState: com.example.model.PlaybackState,
    onPlaySong: (Song) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyStateView(
            icon = Icons.Rounded.MusicNote,
            title = "Песни не найдены",
            subtitle = "Попробуйте изменить запрос поиска"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isPlaying = playbackState.currentSong?.id == song.id && playbackState.isPlaying,
                    isCurrent = playbackState.currentSong?.id == song.id,
                    onClick = { onPlaySong(song) },
                    onToggleFavorite = { onToggleFavorite(song.id) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: List<Album>,
    onSelectAlbum: (Album) -> Unit
) {
    if (albums.isEmpty()) {
        EmptyStateView(
            icon = Icons.Rounded.Album,
            title = "Альбомы не найдены",
            subtitle = "В медиатеке нет альбомов"
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(
                    album = album,
                    onClick = { onSelectAlbum(album) }
                )
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    artists: List<Artist>,
    onSelectArtist: (Artist) -> Unit
) {
    if (artists.isEmpty()) {
        EmptyStateView(
            icon = Icons.Rounded.Person,
            title = "Исполнители не найдены",
            subtitle = "В медиатеке нет исполнителей"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
        ) {
            items(artists, key = { it.name }) { artist ->
                ArtistListItem(
                    artist = artist,
                    onClick = { onSelectArtist(artist) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun FoldersTab(
    folders: List<Folder>,
    onSelectFolder: (Folder) -> Unit
) {
    if (folders.isEmpty()) {
        EmptyStateView(
            icon = Icons.Rounded.Folder,
            title = "Папки не найдены",
            subtitle = "В медиатеке нет папок с аудио"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
        ) {
            items(folders, key = { it.path + it.name }) { folder ->
                FolderListItem(
                    folder = folder,
                    onClick = { onSelectFolder(folder) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun GenresTab(
    genres: List<Genre>,
    onSelectGenre: (Genre) -> Unit
) {
    if (genres.isEmpty()) {
        EmptyStateView(
            icon = Icons.Rounded.MusicNote,
            title = "Жанры не найдены",
            subtitle = "В медиатеке нет жанров"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
        ) {
            items(genres, key = { it.name }) { genre ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable { onSelectGenre(genre) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = genre.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "${genre.songCount} треков",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailListScreen(
    title: String,
    subtitle: String,
    songs: List<Song>,
    playbackState: com.example.model.PlaybackState,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit
) {
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Назад"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (songs.isNotEmpty()) {
                IconButton(onClick = onPlayAll) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Воспроизвести всё",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isPlaying = playbackState.currentSong?.id == song.id && playbackState.isPlaying,
                    isCurrent = playbackState.currentSong?.id == song.id,
                    onClick = { onPlaySong(song) },
                    onToggleFavorite = { onToggleFavorite(song.id) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}
