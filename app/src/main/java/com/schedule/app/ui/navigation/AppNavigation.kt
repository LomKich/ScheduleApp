package com.schedule.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.schedule.app.ui.AppViewModel
import com.schedule.app.ui.FunOverlay
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
    object Messenger   : Screen()
    object OnlineUsers : Screen()
    object GroupChats  : Screen()
    data class Chat(val withUsername: String)       : Screen()
    data class GroupChat(val groupId: String)       : Screen()
    data class PeerProfile(val username: String)    : Screen()
    object Games                                    : Screen()
    object Teachers                                 : Screen()
}

// ─────────────────────────────────────────────────────────────────────────────
// AppScreen — корневой контейнер
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppScreen(
    vm: AppViewModel,
    onSwitchToWebView: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickBgImage: () -> Unit = {},
) {
    val t = LocalTheme.current

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var activeNavTab  by remember { mutableStateOf(NavTab.Home) }
    var hwActiveTab   by remember { mutableStateOf(true) }
    var searchQuery   by remember { mutableStateOf("") }

    fun navigate(screen: Screen) { currentScreen = screen }
    fun goBack(to: Screen)       { currentScreen = to }

    val rootScreens = setOf(Screen.Home, Screen.Bells, Screen.Messenger, Screen.Profile)
    val showNavBar  = currentScreen in rootScreens

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val isBack = rootScreens.contains(targetState) && !rootScreens.contains(initialState)
                if (isBack) {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(260, easing = FastOutSlowInEasing)) togetherWith
                    slideOutHorizontally(targetOffsetX = { it },  animationSpec = tween(220, easing = FastOutSlowInEasing))
                } else {
                    slideInHorizontally(initialOffsetX = { it },   animationSpec = tween(260, easing = FastOutSlowInEasing)) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it/3 },animationSpec = tween(220, easing = FastOutSlowInEasing))
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "screenTransition",
        ) { screen ->
            when (screen) {

                Screen.Home -> HomeScreen(
                    files          = vm.files,
                    selectedFile   = vm.selectedFile,
                    isLoading      = vm.isLoading,
                    loadProgress   = vm.loadProgress,
                    statusText     = vm.statusText,
                    yandexUrl      = vm.yandexUrl,
                    isTeacher      = vm.isTeacher,
                    hwActiveCount  = vm.hwItems.count { !it.done },
                    onModeChange   = vm::setMode,
                    onFileClick    = { 
                        vm.selectFile(it)
                        if (vm.isTeacher) navigate(Screen.Teachers)
                        else navigate(Screen.Groups)
                    },
                    onRetry        = vm::loadFiles,
                    onOpenSettings = { navigate(Screen.Settings) },
                    onOpenHomework = { navigate(Screen.Homework) },
                )

                Screen.Groups -> GroupsScreen(
                    title          = vm.selectedFile?.name ?: "Выбор группы",
                    subtitle       = "${vm.groups.size} групп",
                    groups         = vm.groups,
                    selectedGroup  = vm.selectedGroup,
                    isLoading      = vm.isLoading,
                    loadProgress   = vm.loadProgress,
                    statusText     = vm.statusText,
                    searchQuery    = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onGroupClick   = {
                        searchQuery = ""
                        vm.selectGroup(it)
                        navigate(Screen.Schedule)
                    },
                    onBack = { goBack(Screen.Home) },
                )

                Screen.Schedule -> ScheduleScreen(
                    groupName    = vm.selectedGroup ?: "—",
                    dateText     = vm.scheduleDays.firstOrNull()?.header ?: "Загружаю...",
                    days         = vm.scheduleDays,
                    isLoading    = vm.isLoading,
                    focusMode    = vm.focusMode,
                    searchQuery        = vm.schedSearchQuery,
                    onSearchQueryChange= { vm.schedSearchQuery = it },
                    searchVisible      = vm.schedSearchVisible,
                    starredPairs = vm.starredPairs,
                    pairNotes    = vm.pairNotes,
                    onBack       = { goBack(Screen.Groups) },
                    onSearch     = { vm.schedSearchVisible = !vm.schedSearchVisible },
                    onShare      = {
                        val text = vm.buildShareText()
                        if (text.isNotEmpty()) vm.pendingShareText = text
                    },
                    onToggleFocus  = { vm.focusMode = !vm.focusMode; vm.showToast(if (vm.focusMode) "🎯 Режим фокуса" else "🎯 Выключен") },
                    onToggleStar   = { key -> vm.toggleStarPair(key) },
                    onOpenNote     = { key -> vm.editingNoteKey = key },
                    onCopyPair     = { text -> vm.pendingShareText = text },
                    onConsole      = { vm.showConsole = true },
                    onExcuse       = { vm.activeFunOverlay = FunOverlay.Excuse },
                )

                Screen.Bells -> BellsScreen(
                    schedules = vm.bellSchedules,
                    onBack    = { goBack(Screen.Home) },
                )

                Screen.Settings -> SettingsScreen(
                    yandexUrl          = vm.yandexUrl,
                    onUrlChange        = vm::setUrl,
                    onUpdateFiles      = vm::loadFiles,
                    proxyStatus        = "",
                    currentThemeName   = vm.themeState.current.id,
                    onOpenThemes       = { navigate(Screen.Themes) },
                    isMuted            = vm.isMuted,
                    onToggleMute       = vm::toggleMute,
                    isGlassMode        = vm.isGlassMode,
                    onToggleGlass      = vm::toggleGlass,
                    isGlassOptMode     = vm.isGlassOptMode,
                    onToggleGlassOpt   = vm::toggleGlassOpt,
                    hasBgImage         = vm.hasBgImage,
                    bgImageData        = vm.bgImageData,
                    isBgBlurEnabled    = vm.isBgBlurEnabled,
                    onToggleBgBlur     = vm::toggleBgBlur,
                    onPickBgImage      = onPickBgImage,
                    onRemoveBgImage    = vm::removeBgImage,
                    isIphoneEmoji      = vm.isIphoneEmoji,
                    onToggleIphoneEmoji= vm::toggleIphoneEmoji,
                    hotPatchStatus     = vm.hotPatchStatus,
                    onHotPatch         = vm::checkHotPatch,
                    appVersion         = "4.8.7",
                    onBack             = { goBack(Screen.Home) },
                    onSwitchToWebView  = onSwitchToWebView,
                    onCheckUpdate      = { vm.showToast("🔄 Проверяю обновления...") },
                    onOpenBackups      = { vm.showToast("💾 Резервные копии — скоро") },
                    onOpenGames        = { navigate(Screen.Games) },
                    visitStreak        = vm.visitStreak,
                )

                Screen.Themes -> ThemesScreen(
                    currentThemeId = vm.themeState.current.id,
                    onThemeSelect  = { vm.setTheme(it); goBack(Screen.Settings) },
                    currentFontId  = vm.currentFontId,
                    onFontSelect   = { vm.setFont(it) },
                    onBack         = { goBack(Screen.Settings) },
                )

                Screen.Homework -> HomeworkScreen(
                    items       = vm.hwItems,
                    activeTab   = hwActiveTab,
                    onTabChange = { hwActiveTab = it },
                    onToggleDone= vm::toggleHwDone,
                    onDelete    = vm::deleteHw,
                    onAdd       = vm::showAddHwDialog,
                    onBack      = { goBack(Screen.Home) },
                )

                Screen.Profile -> ProfileScreen(
                    profile          = vm.userProfile,
                    p2pConnected     = vm.p2pConnected,
                    notifEnabled     = vm.notifEnabled,
                    bgServiceEnabled = vm.bgServiceEnabled,
                    onLogin          = { navigate(Screen.Login) },
                    onPickPhoto      = onPickPhoto,
                    onEdit           = { vm.startEditProfile(); navigate(Screen.ProfileEdit) },
                    onOpenSettings   = { navigate(Screen.Settings) },
                    onOpenLeaderboard= { navigate(Screen.Leaderboard) },
                    onOpenFriends    = { vm.loadOnlineUsers(); navigate(Screen.OnlineUsers) },
                    onOpenGroups     = { vm.loadGroupChats(); navigate(Screen.GroupChats) },
                    onOpenGames      = { navigate(Screen.Games) },
                    onReconnect      = { },
                    onToggleNotif    = vm::toggleNotif,
                    onToggleBgService= vm::toggleBgService,
                    onLogout         = { vm.doLogout(); navigate(Screen.Profile) },
                )

                Screen.Login -> LoginScreen(
                    isLoading           = vm.authLoading,
                    regName             = vm.regName,
                    onRegNameChange     = vm::onRegNameChange,
                    regUsername         = vm.regUsername,
                    onRegUsernameChange = vm::onRegUsernameChange,
                    regUsernameStatus   = vm.regUsernameStatus,
                    regUsernameValid    = vm.regUsernameValid,
                    regEmoji            = vm.regEmoji,
                    onRegEmojiChange    = vm::onRegEmojiChange,
                    onRegRandomEmoji    = vm::onRegRandomEmoji,
                    regPassword         = vm.regPassword,
                    onRegPasswordChange = vm::onRegPasswordChange,
                    onRegSubmit         = {
                        vm.doRegister { goBack(Screen.Profile) }
                    },
                    regError            = vm.regError,
                    regEnabled          = vm.regEnabled,
                    authUsername        = vm.authUsername,
                    onAuthUsernameChange= vm::onAuthUsernameChange,
                    authPassword        = vm.authPassword,
                    onAuthPasswordChange= vm::onAuthPasswordChange,
                    onAuthSubmit        = {
                        vm.doLogin { goBack(Screen.Profile) }
                    },
                    authError           = vm.authError,
                )

                Screen.ProfileEdit -> {
                    val p = vm.userProfile
                    if (p != null) {
                        ProfileEditScreen(
                            profile          = p,
                            editName         = vm.editName,
                            onNameChange     = vm::onEditNameChange,
                            editUsername     = vm.editUsername,
                            onUsernameChange = vm::onEditUsernameChange,
                            editBio          = vm.editBio,
                            onBioChange      = vm::onEditBioChange,
                            editEmoji        = vm.editEmoji,
                            onEmojiChange    = vm::onEditEmojiChange,
                            onRandomEmoji    = vm::onEditRandomEmoji,
                            editStatus       = vm.editStatus,
                            onStatusChange   = vm::onEditStatusChange,
                            editColor        = vm.editColor,
                            onColorChange    = vm::onEditColorChange,
                            onDeleteAccount  = { vm.processCommand("deleteaccount ПОДТВЕРЖДАЮ"); goBack(Screen.Profile) },
                            onSave           = { vm.saveEditProfile(); goBack(Screen.Profile) },
                            onBack           = { goBack(Screen.Profile) },
                            onPickPhoto      = onPickPhoto,
                        )
                    } else {
                        goBack(Screen.Profile)
                    }
                }

                Screen.Teachers -> TeachersScreen(
                    title         = "Преподаватели",
                    subtitle      = "${vm.teachers.size} препод.",
                    teachers      = vm.teachers,
                    selectedTeacher = vm.selectedTeacher,
                    isLoading     = vm.isLoading,
                    searchQuery   = vm.teacherSearchQuery,
                    onSearchChange= { vm.teacherSearchQuery = it },
                    onTeacherClick= {
                        vm.selectTeacher(it)
                        navigate(Screen.Schedule)
                    },
                    onBack        = { goBack(Screen.Home) },
                )

                Screen.Friends -> FriendsScreen(
                    friends        = vm.friends,
                    searchQuery    = vm.friendsSearchQuery,
                    onSearchChange = { vm.friendsSearchQuery = it },
                    onFriendClick  = { username ->
                        vm.openChat(username)
                        navigate(Screen.Chat(username))
                    },
                    onRefresh      = vm::refreshFriends,
                    onBack         = { goBack(Screen.Profile) },
                )

                Screen.Leaderboard -> LeaderboardScreen(
    entries        = vm.leaderboard,
    selectedGame   = vm.leaderboardGame,
    onGameSelect   = { game -> vm.leaderboardGame = game },   // ✅ правильно
    isLoading      = vm.leaderboardLoading,
    onBack         = { goBack(Screen.Profile) },
)
                )

                is Screen.Chat -> {
                    ChatScreen(
                        withUsername   = screen.withUsername,
                        myUsername     = vm.userProfile?.username ?: "",
                        messages       = vm.messages,
                        isLoading      = vm.messagesLoading,
                        messageInput   = vm.messageInput,
                        onInputChange  = { vm.messageInput = it },
                        onSend         = vm::sendMessage,
                        onBack         = { vm.closeChat(); goBack(Screen.Messenger) },
                    )
                }

                Screen.Messenger -> {
                    LaunchedEffect(Unit) { vm.startMessengerPoller() }
                    MessengerScreen(
                    chats        = vm.messengerChats,
                    isLoading    = vm.messengerLoading,
                    searchQuery  = vm.messengerSearch,
                    onSearchChange = { vm.messengerSearch = it },
                    onChatClick  = { username ->
                        vm.openChat(username)
                        navigate(Screen.Chat(username))
                    },
                    onNewChat    = { vm.loadOnlineUsers(); navigate(Screen.OnlineUsers) },
                    onNewGroup   = { vm.loadGroupChats(); navigate(Screen.GroupChats) },
                    onBack       = { goBack(Screen.Home) },
                    )
                }

                Screen.OnlineUsers -> OnlineUsersScreen(
                    users        = vm.onlineUsersList,
                    myUsername   = vm.userProfile?.username ?: "",
                    isLoading    = vm.onlineListLoading,
                    searchQuery  = vm.onlineSearchQuery,
                    onSearchChange = { vm.onlineSearchQuery = it },
                    onUserClick  = { username ->
                        vm.loadPeerProfile(username)
                        navigate(Screen.PeerProfile(username))
                    },
                    onRefresh    = vm::loadOnlineUsers,
                    onBack       = { goBack(Screen.Messenger) },
                )

                is Screen.PeerProfile -> PeerProfileScreen(
                    peer         = vm.peerProfile,
                    isLoading    = vm.peerProfileLoading,
                    isFriend     = vm.friends.any { it.username == screen.username },
                    onMessage    = {
                        vm.openChat(screen.username)
                        navigate(Screen.Chat(screen.username))
                    },
                    onAddFriend    = { vm.addFriend(screen.username) },
                    onRemoveFriend = { vm.removeFriend(screen.username) },
                    onBack       = { goBack(Screen.OnlineUsers) },
                )

                Screen.GroupChats -> GroupChatsScreen(
                    groups       = vm.groupChats,
                    isLoading    = vm.groupChatsLoading,
                    onGroupClick = { groupId ->
                        vm.openGroupChat(groupId)
                        navigate(Screen.GroupChat(groupId))
                    },
                    onCreateGroup = { vm.showCreateGroupDialog = true },
                    onBack       = { goBack(Screen.Profile) },
                )

                is Screen.GroupChat -> ChatScreen(
                    withUsername = screen.groupId,
                    myUsername   = vm.userProfile?.username ?: "",
                    messages     = vm.messages,
                    isLoading    = vm.messagesLoading,
                    messageInput = vm.messageInput,
                    onInputChange= { vm.messageInput = it },
                    onSend       = vm::sendMessage,
                    onBack       = { vm.closeChat(); goBack(Screen.GroupChats) },
                )

                Screen.Games -> {
                    // Запускаем GamesActivity через LaunchedEffect
                    LaunchedEffect(Unit) { vm.openGames() }
                    // Пока активити грузится — возвращаемся назад
                    goBack(Screen.Profile)
                }
            }
        }

        // ── Fun overlays ─────────────────────────────────────────────────────
        when (vm.activeFunOverlay) {
            FunOverlay.Excuse  -> ExcuseCard    { vm.activeFunOverlay = FunOverlay.None }
            FunOverlay.Quiz    -> ScheduleQuizDialog { vm.activeFunOverlay = FunOverlay.None }
            FunOverlay.Haiku   -> HaikuDialog   { vm.activeFunOverlay = FunOverlay.None }
            FunOverlay.BpmTapper -> BpmTapper(
                onResult  = { vm.showToast(it) },
                onDismiss = { vm.activeFunOverlay = FunOverlay.None },
            )
            FunOverlay.Stats   -> AppStatsDialog(
                totalOpens          = vm.totalOpens,
                streak              = vm.visitStreak,
                remainingPairsToday = vm.scheduleDays.firstOrNull()?.pairs
                    ?.count { !it.isWindow } ?: 0,
                notesCount          = vm.pairNotes.size,
                onDismiss           = { vm.activeFunOverlay = FunOverlay.None },
            )
            FunOverlay.Greeting -> {}
            FunOverlay.Console  -> {}
            FunOverlay.None     -> {}
        }

        // ── Special date greeting ─────────────────────────────────────────────
        if (vm.showGreeting) {
            SpecialDateGreetingOverlay { vm.showGreeting = false }
        }

        // ── Dev Console ───────────────────────────────────────────────────────
        if (vm.showConsole) {
            Box(modifier = Modifier.fillMaxSize()) {
                DevConsole(
                    entries       = vm.consoleEntries,
                    input         = vm.consoleInput,
                    onInputChange = { vm.consoleInput = it },
                    onSubmit      = { vm.processCommand(it) },
                    onClose       = { vm.showConsole = false },
                )
            }
        }

        // ── Pair note dialog ──────────────────────────────────────────────────
        vm.editingNoteKey?.let { key ->
            PairNoteDialog(
                pairKey      = key,
                existingNote = vm.pairNotes[key] ?: "",
                onSave       = { text -> vm.saveNote(key, text); vm.editingNoteKey = null },
                onDismiss    = { vm.editingNoteKey = null },
            )
        }

        // ── Toast ─────────────────────────────────────────────────────────────
        vm.toastMessage?.let { state ->
            AppToast(
                state = state,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showNavBar) 90.dp else 24.dp),
            )
        }

        // ── Диалог добавления ДЗ ─────────────────────────────────────────────
        if (vm.showAddHwDialog) {
            AddHwDialog(
                onConfirm = { subject, task, deadline, urgent ->
                    vm.addHw(subject, task, deadline, urgent)
                    vm.showAddHwDialog = false
                },
                onDismiss = { vm.showAddHwDialog = false },
            )
        }

        // ── Диалог создания группы ────────────────────────────────────────────
        if (vm.showCreateGroupDialog) {
            CreateGroupDialog(
                onConfirm = { name, avatar ->
                    vm.createGroup(name, avatar)
                    vm.showCreateGroupDialog = false
                },
                onDismiss = { vm.showCreateGroupDialog = false },
            )
        }

        // ── Bottom Nav ───────────────────────────────────────────────────────
        if (showNavBar) {
            BottomNavBar(
                activeTab = activeNavTab,
                onTabSelected = { tab ->
                    activeNavTab = tab
                    currentScreen = when (tab) {
                        NavTab.Home     -> Screen.Home
                        NavTab.Bells    -> Screen.Bells
                        NavTab.Messages -> Screen.Messenger
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
