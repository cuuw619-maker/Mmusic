package com.example.plugin

enum class PluginCategory(val displayName: String) {
    PLAYER("Плеер (Player)"),
    QUEUE("Очередь (Queue)"),
    LIBRARY("Медиатека (Library)"),
    PLAYLISTS("Плейлисты (Playlists)"),
    PLAYBACK("Воспроизведение и эффекты (Playback)"),
    MEDIA("Медиаданные (Media)"),
    NOTIFICATION("Уведомления (Notification)"),
    UI("Интерфейс (UI)"),
    SETTINGS("Настройки (Settings)"),
    EVENTS("События (Events)"),
    STORAGE("Хранилище (Storage)"),
    NETWORK("Сеть (Network)")
}

enum class PluginCapability(
    val category: PluginCategory,
    val title: String,
    val description: String
) {
    // PLAYER
    READ_PLAYBACK_STATE(PluginCategory.PLAYER, "Чтение состояния плеера", "Получение текущего статуса воспроизведения (играет, пауза, позиция)"),
    CONTROL_PLAYER(PluginCategory.PLAYER, "Полное управление плеером", "Доступ ко всем базовым командам плеера"),
    PLAY(PluginCategory.PLAYER, "Запуск воспроизведения", "Команда play()"),
    PAUSE(PluginCategory.PLAYER, "Приостановка воспроизведения", "Команда pause()"),
    STOP(PluginCategory.PLAYER, "Остановка воспроизведения", "Команда stop()"),
    NEXT(PluginCategory.PLAYER, "Следующий трек", "Переход к следующей композиции"),
    PREVIOUS(PluginCategory.PLAYER, "Предыдущий трек", "Переход к предыдущей композиции"),
    SEEK(PluginCategory.PLAYER, "Перемотка", "Изменение текущей позиции воспроизведения"),
    CHANGE_SPEED(PluginCategory.PLAYER, "Изменение скорости", "Управление темпом воспроизведения (0.5x - 2.0x)"),
    CHANGE_PITCH(PluginCategory.PLAYER, "Изменение высоты тона", "Тональность аудио (Pitch shift)"),

    // QUEUE
    READ_QUEUE(PluginCategory.QUEUE, "Чтение очереди", "Доступ к списку треков в очереди воспроизведения"),
    MODIFY_QUEUE(PluginCategory.QUEUE, "Управление очередью", "Полное изменение порядка и состава очереди"),
    ADD_TO_QUEUE(PluginCategory.QUEUE, "Добавление в очередь", "Добавление новых треков в конец или начало очереди"),
    REMOVE_FROM_QUEUE(PluginCategory.QUEUE, "Удаление из очереди", "Исключение трека из текущей очереди"),
    REORDER_QUEUE(PluginCategory.QUEUE, "Сортировка очереди", "Перемещение позиций треков внутри очереди"),
    CLEAR_QUEUE(PluginCategory.QUEUE, "Очистка очереди", "Сброс всей текущей очереди воспроизведения"),

    // LIBRARY
    READ_LIBRARY(PluginCategory.LIBRARY, "Чтение медиатеки", "Доступ к списку всех локальных аудиофайлов"),
    READ_METADATA(PluginCategory.LIBRARY, "Чтение метаданных", "Теги треков, названия, альбомы, исполнители, битрейт"),
    READ_ALBUMS(PluginCategory.LIBRARY, "Чтение альбомов", "Доступ к списку альбомов и треков внутри них"),
    READ_ARTISTS(PluginCategory.LIBRARY, "Чтение исполнителей", "Доступ к дискографии и спискам исполнителей"),
    READ_GENRES(PluginCategory.LIBRARY, "Чтение жанров", "Категоризация по музыкальным жанрам"),
    READ_FOLDERS(PluginCategory.LIBRARY, "Чтение папок", "Просмотр файловой структуры папок с музыкой"),
    READ_FAVORITES(PluginCategory.LIBRARY, "Чтение избранного", "Список треков, добавленных в избранное"),
    MODIFY_FAVORITES(PluginCategory.LIBRARY, "Изменение избранного", "Добавление и удаление песен из избранного"),

    // PLAYLISTS
    READ_PLAYLISTS(PluginCategory.PLAYLISTS, "Чтение плейлистов", "Доступ ко всем пользовательским плейлистам"),
    CREATE_PLAYLISTS(PluginCategory.PLAYLISTS, "Создание плейлистов", "Возможность создавать новые плейлисты"),
    MODIFY_PLAYLISTS(PluginCategory.PLAYLISTS, "Изменение плейлистов", "Редактирование названий и состава плейлистов"),
    DELETE_PLAYLISTS(PluginCategory.PLAYLISTS, "Удаление плейлистов", "Удаление существующих плейлистов"),
    ADD_TO_PLAYLISTS(PluginCategory.PLAYLISTS, "Добавление треков в плейлист", "Добавление треков в любой плейлист"),
    REMOVE_FROM_PLAYLISTS(PluginCategory.PLAYLISTS, "Удаление треков из плейлиста", "Исключение треков из выбранного плейлиста"),

    // PLAYBACK
    CONTROL_REPEAT(PluginCategory.PLAYBACK, "Режим повтора", "Переключение между повтором трека, списка и выключением"),
    CONTROL_SHUFFLE(PluginCategory.PLAYBACK, "Случайный порядок", "Включение и отключение случайного порядка (Shuffle)"),
    CONTROL_CROSSFADE(PluginCategory.PLAYBACK, "Кроссфейд", "Плавное микширование перехода между треками"),
    CONTROL_SLEEP_TIMER(PluginCategory.PLAYBACK, "Таймер сна", "Установка и отмена таймера выключения музыки"),
    CONTROL_EQUALIZER(PluginCategory.PLAYBACK, "Эквалайзер", "Управление полосами эквалайзера и пресетами"),
    CONTROL_AUDIO_EFFECTS(PluginCategory.PLAYBACK, "Аудиоэффекты", "Bass Boost, Virtualizer и Loudness Enhancer"),
    CONTROL_SPEED(PluginCategory.PLAYBACK, "Скорость воспроизведения", "Управление темпом аудио"),
    CONTROL_PITCH(PluginCategory.PLAYBACK, "Высота тона (Pitch)", "Транспонирование тональности"),

    // MEDIA
    READ_AUDIO_FILES(PluginCategory.MEDIA, "Доступ к аудиофайлам", "Прямое чтение файлов композиций на устройстве"),
    READ_COVER_ART(PluginCategory.MEDIA, "Обложки альбомов", "Доступ к графическим обложкам треков"),
    READ_TAGS(PluginCategory.MEDIA, "ID3 теги", "Парсинг расширенных аудиотегов"),
    READ_BITRATE(PluginCategory.MEDIA, "Битрейт и кодек", "Информация о формате аудио и битрейте"),
    READ_DURATION(PluginCategory.MEDIA, "Длительность трека", "Точная длина композиций в миллисекундах"),
    READ_FILE_PATH(PluginCategory.MEDIA, "Путь к файлу", "Абсолютный путь к файлу на накопителе"),
    READ_FILE_SIZE(PluginCategory.MEDIA, "Размер файла", "Размер аудиофайла в байтах"),

    // NOTIFICATION
    READ_NOTIFICATION_STATE(PluginCategory.NOTIFICATION, "Статус уведомления", "Чтение текущего состояния системного медиа-уведомления"),
    ADD_NOTIFICATION_ACTION(PluginCategory.NOTIFICATION, "Действие в уведомлении", "Добавление кастомных кнопок в шторку уведомлений"),
    MODIFY_PLUGIN_NOTIFICATION_ACTIONS(PluginCategory.NOTIFICATION, "Управление кнопками уведомления", "Изменение добавленных кнопок управления в уведомлении"),

    // UI
    UI_EXTENSION(PluginCategory.UI, "Расширение интерфейса", "Добавление кнопок, карточек и диалогов в UI"),
    ADD_MENU_ITEM(PluginCategory.UI, "Пункты меню", "Добавление пунктов в контекстные меню плеера"),
    ADD_ACTION(PluginCategory.UI, "Быстрые действия", "Регистрация кнопок действий в экранах приложения"),
    ADD_SETTINGS_SCREEN(PluginCategory.UI, "Экран настроек плагина", "Индивидуальный экран параметров плагина"),
    ADD_PLAYER_ACTION(PluginCategory.UI, "Кнопки в плеере", "Кастомные действия на экране Now Playing"),
    ADD_LIBRARY_ACTION(PluginCategory.UI, "Кнопки в медиатеке", "Действия на экране медиатеки"),
    ADD_QUEUE_ACTION(PluginCategory.UI, "Кнопки в очереди", "Действия в шторке очереди треков"),
    ADD_CONTEXT_MENU(PluginCategory.UI, "Контекстное меню трека", "Кастомные команды при нажатии на трек"),
    ADD_HOME_WIDGET(PluginCategory.UI, "Виджет на главном экране", "Информационный блок на главном экране"),
    ADD_PLAYER_OVERLAY(PluginCategory.UI, "Оверлей плеера", "Отображение графических элементов поверх обложки"),

    // SETTINGS
    READ_SETTINGS(PluginCategory.SETTINGS, "Чтение настроек плеера", "Доступ к общим параметрам плеера"),
    MODIFY_SETTINGS(PluginCategory.SETTINGS, "Изменение настроек плеера", "Изменение глобальных настроек плеера"),
    STORE_PLUGIN_SETTINGS(PluginCategory.SETTINGS, "Хранение настроек плагина", "Сохранение собственных данных конфигурации плагина"),

    // EVENTS
    RECEIVE_PLAYBACK_EVENTS(PluginCategory.EVENTS, "События воспроизведения", "Подписка на паузу, запуск, стоп и завершение трека"),
    RECEIVE_TRACK_EVENTS(PluginCategory.EVENTS, "События смены трека", "Получение уведомлений при переключении композиции"),
    RECEIVE_QUEUE_EVENTS(PluginCategory.EVENTS, "События очереди", "Оповещение об изменениях состава очереди"),
    RECEIVE_LIBRARY_EVENTS(PluginCategory.EVENTS, "События медиатеки", "Оповещение о сканировании или обновлении медиатеки"),
    RECEIVE_PLAYLIST_EVENTS(PluginCategory.EVENTS, "События плейлистов", "Оповещение об изменениях в плейлистах"),
    RECEIVE_SETTINGS_EVENTS(PluginCategory.EVENTS, "События настроек", "Оповещение об изменении настроек приложения"),

    // NETWORK
    NETWORK_ACCESS(PluginCategory.NETWORK, "Доступ к сети", "Разрешение на сетевые запросы при явной необходимости"),

    // STORAGE
    READ_PLUGIN_STORAGE(PluginCategory.STORAGE, "Чтение хранилища плагина", "Доступ к собственной изолированной директории данных"),
    WRITE_PLUGIN_STORAGE(PluginCategory.STORAGE, "Запись в хранилище плагина", "Сохранение файлов и кэша в изолированную директорию")
}
