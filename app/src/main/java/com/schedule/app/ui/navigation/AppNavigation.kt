package com.schedule.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.schedule.app.ui.components.*
import com.schedule.app.ui.screens.*
import com.schedule.app.ui.theme.LocalTheme

// ─────────────────────────────────────────────────────────────────────────────
// Маршруты
// ─────────────────────────────────────────────────────────────────────────────
sealed class Screen {
    object Home        : Screen()
    object Groups      : Screen()
    object Schedule    : Screen()
    object Bells       : Screen()
    object Settings    : Screen()
    object Themes      : Screen()
    object Homework    : Screen()
    object Profile     : Screen()
    object Login       : Screen()
    object ProfileEdit : Screen()
    object Friends     : Screen()
    object Leaderboard : Screen()
}

// ─────────────────────────────────────────────────────────────────────────────
// AppScreen — корневой контейнер со стековой навигацией
// Анимация: translateX(100%) → 0  (как в оригинальном .screen)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppScreen(
    // ── Schedule state ────────────────────────────────────────────────────────
    files: List<ScheduleFile>,
    selectedFile: ScheduleFile?,
    groups: List<String>,
    selectedGroup: String?,
    scheduleDays: List<ScheduleDay>,
    hwItems: List<HomeworkItem>,
    bellSchedules: List<BellSchedule>,
    yandexUrl: String,
    isLoading: Boolean,
    loadProgress: Float,
    statusText: String,
    isMuted: Boolean,
    isTeacher: Boolean,
    isGlassMode: Boolean,
    currentThemeId: String,
    appVersion: String,
    // ── Social state ──────────────────────────────────────────────────────────
    userProfile: UserProfile?,
    friends: List<FriendEntry>,
    leaderboard: List<LeaderboardEntry>,
    p2pConnected: Boolean,
    notifEnabled: Boolean,
    bgServiceEnabled: Boolean,
    // ── Login / Register fields ───────────────────────────────────────────────
    regName: String,
    onRegNameChange: (String) -> Unit,
    regUsername: String,
    onRegUsernameChange: (String) -> Unit,
    regUsernameStatus: String,
    regUsernameValid: Boolean,
    regEmoji: String,
    onRegEmojiChange: (String) -> Unit,
    onRegRandomEmoji: () -> Unit,
    regPassword: String,
    onRegPasswordChange: (String) -> Unit,
    onRegSubmit: () -> Unit,
    regError: String,
    regEnabled: Boolean,
    authUsername: String,
    onAuthUsernameChange: (String) -> Unit,
    authPassword: String,
    onAuthPasswordChange: (String) -> Unit,
    onAuthSubmit: () -> Unit,
    authError: String,
    // ── Profile edit fields ───────────────────────────────────────────────────
    editName: String,
    onEditNameChange: (String) -> Unit,
    editBio: String,
    onEditBioChange: (String) -> Unit,
    editEmoji: String,
    onEditEmojiChange: (String) -> Unit,
    onEditRandomEmoji: () -> Unit,
    editStatus: String,
    onEditStatusChange: (String) -> Unit,
    onEditSave: () -> Unit,
    // ── Actions ───────────────────────────────────────────────────────────────
    onUrlChange: (String) -> Unit,
    onUpdateFiles: () -> Unit,
    onFileClick: (ScheduleFile) -> Unit,
    onGroupClick: (String) -> Unit,
    onModeChange: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onToggleGlass: () -> Unit,
    onThemeSelect: (String) -> Unit,
    onToggleHwDone: (Long) -> Unit,
    onDeleteHw: (Long) -> Unit,
    onAddHw: () -> Unit,
    onRetry: () -> Unit,
    onSwitchToWebView: () -> Unit,
    onReconnect: () -> Unit,
    onToggleNotif: () -> Unit,
    onToggleBgService: () -> Unit,
    onPickPhoto: () -> Unit,
    onFriendClick: (String) -> Unit,
    onRefreshFriends: () -> Unit,
    friendsSearchQuery: String,
    onFriendsSearchChange: (String) -> Unit,
) {
    val t = LocalTheme.current

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var activeNavTab  by remember { mutableStateOf(NavTab.Home) }
    var hwActiveTab   by remember { mutableStateOf(true) }
    var searchQuery   by remember { mutableStateOf("") }

    fun navigate(screen: Screen) { currentScreen = screen }
    fun goBack(to: Screen)       { currentScreen = to }

    val rootScreens = setOf(Screen.Home, Screen.Bells, Screen.Homework, Screen.Profile)
    val showNavBar  = currentScreen in rootScreens

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        // ── Основной контент ─────────────────────────────────────────
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val isBack = rootScreens.contains(targetState) && !rootScreens.contains(initialState)
                if (isBack) {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { -it / 3 },
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "screenTransition",
        ) { screen ->
            when (screen) {

                Screen.Home -> HomeScreen(
                    files          = files,
                    selectedFile   = selectedFile,
                    isLoading      = isLoading,
                    loadProgress   = loadProgress,
                    statusText     = statusText,
                    yandexUrl      = yandexUrl,
                    isTeacher      = isTeacher,
                    onModeChange   = onModeChange,
                    onFileClick    = { onFileClick(it); navigate(Screen.Groups) },
                    onRetry        = onRetry,
                    onOpenSettings = { navigate(Screen.Settings) },
                )

                Screen.Groups -> GroupsScreen(
                    title           = selectedFile?.name ?: "Выбор группы",
                    subtitle        = "${groups.size} групп",
                    groups          = groups,
                    selectedGroup   = selectedGroup,
                    isLoading       = isLoading,
                    loadProgress    = loadProgress,
                    statusText      = statusText,
                    searchQuery     = searchQuery,
                    onSearchChange  = { searchQuery = it },
                    onGroupClick    = {
                        searchQuery = ""
                        onGroupClick(it)
                        navigate(Screen.Schedule)
                    },
                    onBack = { goBack(Screen.Home) },
                )

                Screen.Schedule -> ScheduleScreen(
                    groupName  = selectedGroup ?: "—",
                    dateText   = scheduleDays.firstOrNull()?.header ?: "Загружаю...",
                    days       = scheduleDays,
                    isLoading  = isLoading,
                    onBack     = { goBack(Screen.Groups) },
                    onSearch   = {},
                    onShare    = {},
                )

                Screen.Bells -> BellsScreen(
                    schedules = bellSchedules,
                    onBack    = { goBack(Screen.Home) },
                )

                Screen.Settings -> SettingsScreen(
                    yandexUrl        = yandexUrl,
                    onUrlChange      = onUrlChange,
                    onUpdateFiles    = onUpdateFiles,
                    proxyStatus      = "",
                    currentThemeName = currentThemeId,
                    onOpenThemes     = { navigate(Screen.Themes) },
                    isMuted          = isMuted,
                    onToggleMute     = onToggleMute,
                    isGlassMode      = isGlassMode,
                    onToggleGlass    = onToggleGlass,
                    appVersion       = appVersion,
                    onBack           = { goBack(Screen.Home) },
                    onSwitchToWebView= onSwitchToWebView,
                )

                Screen.Themes -> ThemesScreen(
                    currentThemeId = currentThemeId,
                    onThemeSelect  = { onThemeSelect(it); goBack(Screen.Settings) },
                    onBack         = { goBack(Screen.Settings) },
                )

                Screen.Homework -> HomeworkScreen(
                    items        = hwItems,
                    activeTab    = hwActiveTab,
                    onTabChange  = { hwActiveTab = it },
                    onToggleDone = onToggleHwDone,
                    onDelete     = onDeleteHw,
                    onAdd        = onAddHw,
                    onBack       = { goBack(Screen.Home) },
                )

                Screen.Profile -> ProfileScreen(
                    profile          = userProfile,
                    p2pConnected     = p2pConnected,
                    notifEnabled     = notifEnabled,
                    bgServiceEnabled = bgServiceEnabled,
                    onLogin          = { navigate(Screen.Login) },
                    onPickPhoto      = onPickPhoto,
                    onEdit           = { navigate(Screen.ProfileEdit) },
                    onOpenSettings   = { navigate(Screen.Settings) },
                    onOpenLeaderboard= { navigate(Screen.Leaderboard) },
                    onOpenFriends    = { navigate(Screen.Friends) },
                    onOpenGroups     = { /* TODO: groups chat screen */ },
                    onReconnect      = onReconnect,
                    onToggleNotif    = onToggleNotif,
                    onToggleBgService= onToggleBgService,
                )

                Screen.Login -> LoginScreen(
                    isLoading           = isLoading,
                    regName             = regName,
                    onRegNameChange     = onRegNameChange,
                    regUsername         = regUsername,
                    onRegUsernameChange = onRegUsernameChange,
                    regUsernameStatus   = regUsernameStatus,
                    regUsernameValid    = regUsernameValid,
                    regEmoji            = regEmoji,
                    onRegEmojiChange    = onRegEmojiChange,
                    onRegRandomEmoji    = onRegRandomEmoji,
                    regPassword         = regPassword,
                    onRegPasswordChange = onRegPasswordChange,
                    onRegSubmit         = onRegSubmit,
                    regError            = regError,
                    regEnabled          = regEnabled,
                    authUsername        = authUsername,
                    onAuthUsernameChange= onAuthUsernameChange,
                    authPassword        = authPassword,
                    onAuthPasswordChange= onAuthPasswordChange,
                    onAuthSubmit        = onAuthSubmit,
                    authError           = authError,
                )

                Screen.ProfileEdit -> {
                    val p = userProfile
                    if (p != null) {
                        ProfileEditScreen(
                            profile         = p,
                            editName        = editName,
                            onNameChange    = onEditNameChange,
                            editBio         = editBio,
                            onBioChange     = onEditBioChange,
                            editEmoji       = editEmoji,
                            onEmojiChange   = onEditEmojiChange,
                            onRandomEmoji   = onEditRandomEmoji,
                            editStatus      = editStatus,
                            onStatusChange  = onEditStatusChange,
                            onSave          = { onEditSave(); goBack(Screen.Profile) },
                            onBack          = { goBack(Screen.Profile) },
                        )
                    } else {
                        goBack(Screen.Profile)
                    }
                }

                Screen.Friends -> FriendsScreen(
                    friends         = friends,
                    searchQuery     = friendsSearchQuery,
                    onSearchChange  = onFriendsSearchChange,
                    onFriendClick   = onFriendClick,
                    onRefresh       = onRefreshFriends,
                    onBack          = { goBack(Screen.Profile) },
                )

                Screen.Leaderboard -> LeaderboardScreen(
                    entries = leaderboard,
                    onBack  = { goBack(Screen.Profile) },
                )
            }
        }

        // ── Bottom navigation bar ────────────────────────────────────
        if (showNavBar) {
            BottomNavBar(
                activeTab = activeNavTab,
                onTabSelected = { tab ->
                    activeNavTab = tab
                    currentScreen = when (tab) {
                        NavTab.Home     -> Screen.Home
                        NavTab.Bells    -> Screen.Bells
                        NavTab.Messages -> Screen.Homework
                        NavTab.Profile  -> Screen.Profile
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}
