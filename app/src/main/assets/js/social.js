// ══════════════════════════════════════════════════════════════════════
// 👤 СИСТЕМА АККАУНТОВ И ПРОФИЛЯ
// Хранение: localStorage. P2P онлайн: PeerJS (id = "sapp-" + username)
// ══════════════════════════════════════════════════════════════════════

const PROFILE_KEY   = 'sapp_profile_v1';
const ACCOUNTS_KEY  = 'sapp_accounts_v1'; // все локальные аккаунты
const FRIENDS_KEY   = 'sapp_friends_v1';

const PROFILE_STATUSES = [
  { id:'online',  emoji:'🟢', label:'В сети',       color:'#4caf7d' },
  { id:'study',   emoji:'📚', label:'Учусь',         color:'#60cdff' },
  { id:'busy',    emoji:'🔴', label:'Занят',          color:'#c94f4f' },
  { id:'sleep',   emoji:'😴', label:'Сплю',           color:'#a78bfa' },
  { id:'game',    emoji:'🎮', label:'Играю',          color:'#f5c518' },
  { id:'away',    emoji:'🌙', label:'Отошёл',         color:'#e87722' },
];

const PROFILE_COLORS = [
  '#e87722','#60cdff','#a78bfa','#4caf7d','#f5c518','#c94f4f',
  '#ff66aa','#00e5ff','#ffb347','#b2ff59','#ff6e40','#40c4ff',
];

const AVATAR_EMOJIS = [
  '😊','😎','🤓','🥳','😏','🤩','🦊','🐺','🦋','🐸','🐱','🐶',
  '🦁','🐼','🐨','🦄','🐉','🦅','🎭','🤖','👻','💀','🎃','⚡',
  '🔥','💎','🌙','⭐','🌈','🎵','🎮','🏆',
];

// ── Helpers ──────────────────────────────────────────────────────
function profileLoad()    { try { return JSON.parse(localStorage.getItem(PROFILE_KEY)) || null; } catch(e){ return null; } }
function profileSave(p) {
  localStorage.setItem(PROFILE_KEY, JSON.stringify(p));
  // Keep accounts store in sync
  if (p && p.username) {
    const accounts = accountsLoad();
    if (!accounts[p.username]) accounts[p.username] = {};
    accounts[p.username].name = p.name;
    accounts[p.username].avatar = p.avatar;
    accounts[p.username].createdAt = p.createdAt;
    if (p.pwdHash) accounts[p.username].pwdHash = p.pwdHash;
    accountsSave(accounts);
  }
}
function accountsLoad()   { try { return JSON.parse(localStorage.getItem(ACCOUNTS_KEY)) || {}; } catch(e){ return {}; } }
function accountsSave(a)  { localStorage.setItem(ACCOUNTS_KEY, JSON.stringify(a)); }
function friendsLoad()    { try { return JSON.parse(localStorage.getItem(FRIENDS_KEY)) || []; } catch(e){ return []; } }
function friendsSave(f)   { localStorage.setItem(FRIENDS_KEY, JSON.stringify(f)); }

function profileHashPwd(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) { h = ((h << 5) - h + s.charCodeAt(i)) | 0; }
  return h.toString(16);
}

function profileGetPeerId(username) {
  return 'sapp-' + username.toLowerCase().replace(/[^a-z0-9_]/g, '');
}

// ── Инициализация при старте ─────────────────────────────────────
function profileBootstrap() {
  const p = profileLoad();
  updateNavProfileIcon(p);
  if (!p) {
    setTimeout(() => {
      if (!profileLoad()) {
        profileInitLoginScreen();
        showScreen('s-login');
        // Если есть сохранённые аккаунты — открываем вкладку входа
        const accounts = accountsLoad();
        const usernames = Object.keys(accounts);
        if (usernames.length > 0) {
          loginShowAuth(usernames[usernames.length - 1]);
        }
        // Иначе остаётся вкладка регистрации (дефолт)
      }
    }, 1200);
  } else {
    profileConnect(p);
  }
}

// ── Обновить иконку в нав-баре ───────────────────────────────────
function updateNavProfileIcon(p) {
  const btn = document.getElementById('nav-profile');
  const wrap = btn?.querySelector('.nav-icon-wrap');
  if (!wrap) return;
  if (p && p.avatarType === 'emoji' && p.avatar) {
    // Show emoji avatar in a small circle over the svg
    wrap.innerHTML = `<span style="font-size:20px;line-height:1">${p.avatar}</span>`;
  } else if (p && p.avatarType === 'photo' && p.avatarData) {
    wrap.innerHTML = `<img src="${p.avatarData}" style="width:26px;height:26px;border-radius:50%;object-fit:cover">`;
  } else {
    wrap.innerHTML = `<svg class="nav-icon" id="nav-profile-icon" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>`;
  }
}

// ══ ЭКРАН ЛОГИНА ═════════════════════════════════════════════════
let _loginSelectedEmoji = '😊';

function profileInitLoginScreen() {
  _loginSelectedEmoji = '😊';
  // Reset custom emoji input
  const customInput = document.getElementById('login-custom-emoji-input');
  const customPreview = document.getElementById('login-custom-emoji-preview');
  const customErr = document.getElementById('login-custom-emoji-error');
  if (customInput) { customInput.value = ''; customInput.style.borderColor = 'var(--surface3)'; }
  if (customPreview) customPreview.textContent = '';
  if (customErr) customErr.textContent = '';
  // Reset tabs to register view
  loginShowRegister();
  // Emoji grid removed - user inputs emoji via keyboard
}

// ── Валидация эмодзи ─────────────────────────────────────────────
function isEmoji(str) {
  if (!str || str.trim() === '') return false;
  // Extract all emoji-like segments using the Unicode emoji regex
  const emojiRegex = /^(?:\p{Emoji_Presentation}|\p{Emoji}\uFE0F|\p{Emoji_Modifier_Base}\p{Emoji_Modifier}?|\p{Emoji_Component}*\u200D)*[\p{Emoji_Presentation}\p{Emoji}\uFE0F\p{Emoji_Modifier_Base}]$/u;
  // Simple check: strip ZWJ, VS16, modifiers and see if we have emoji codepoints
  const cleaned = str.replace(/\u200D/g,'').replace(/\uFE0F/g,'').replace(/[\u{1F3FB}-\u{1F3FF}]/gu,'').trim();
  if (!cleaned) return false;
  // Check each codepoint: must be emoji range
  const cp = cleaned.codePointAt(0);
  if (!cp) return false;
  return (
    (cp >= 0x1F600 && cp <= 0x1F64F) || // Emoticons
    (cp >= 0x1F300 && cp <= 0x1F5FF) || // Misc Symbols and Pictographs
    (cp >= 0x1F680 && cp <= 0x1F6FF) || // Transport and Map
    (cp >= 0x1F700 && cp <= 0x1F77F) || // Alchemical Symbols
    (cp >= 0x1F780 && cp <= 0x1F7FF) || // Geometric Shapes Extended
    (cp >= 0x1F800 && cp <= 0x1F8FF) || // Supplemental Arrows-C
    (cp >= 0x1F900 && cp <= 0x1F9FF) || // Supplemental Symbols and Pictographs
    (cp >= 0x1FA00 && cp <= 0x1FA6F) || // Chess Symbols
    (cp >= 0x1FA70 && cp <= 0x1FAFF) || // Symbols and Pictographs Extended-A
    (cp >= 0x2600  && cp <= 0x27BF)  || // Misc symbols, Dingbats
    (cp >= 0x2300  && cp <= 0x23FF)  || // Misc Technical
    (cp >= 0x2B00  && cp <= 0x2BFF)  || // Misc Symbols and Arrows
    (cp >= 0xFE00  && cp <= 0xFE0F)  || // Variation Selectors
    (cp >= 0x1F000 && cp <= 0x1F02F) || // Mahjong tiles
    (cp >= 0x1F0A0 && cp <= 0x1F0FF) || // Playing cards
    (cp >= 0x1F100 && cp <= 0x1F1FF) || // Enclosed Alphanumeric Supplement
    (cp >= 0x1F200 && cp <= 0x1F2FF) || // Enclosed Ideographic Supplement
    (cp === 0x00A9 || cp === 0x00AE)  || // © ®
    (cp >= 0x203C  && cp <= 0x2049)  || // ‼ ⁉
    (cp >= 0x20D0  && cp <= 0x20FF)     // Combining Enclosing Keycap etc.
  );
}

// ── Рандомный эмодзи ──────────────────────────────────────────────
const _ALL_EMOJI = [
  // Смайлики и люди
  '😀','😃','😄','😁','😆','😅','🤣','😂','🙂','🙃','😉','😊','😇','🥰','😍','🤩',
  '😘','😗','😚','😙','🥲','😋','😛','😜','🤪','😝','🤑','🤗','🤭','🤫','🤔','🤐',
  '🤨','😐','😑','😶','😏','😒','🙄','😬','🤥','😌','😔','😪','🤤','😴','😷','🤒',
  '🤕','🤢','🤮','🤧','🥵','🥶','🥴','😵','🤯','🤠','🥳','🥸','😎','🤓','🧐','😕',
  '😟','🙁','☹️','😮','😯','😲','😳','🥺','😦','😧','😨','😰','😥','😢','😭','😱',
  '😖','😣','😞','😓','😩','😫','🥱','😤','😡','😠','🤬','😈','👿','💀','☠️','💩',
  '🤡','👹','👺','👻','👽','👾','🤖','😺','😸','😹','😻','😼','😽','🙀','😿','😾',
  // Жесты и тело
  '👋','🤚','🖐️','✋','🖖','👌','🤌','🤏','✌️','🤞','🤟','🤘','🤙','👈','👉','👆',
  '🖕','👇','☝️','👍','👎','✊','👊','🤛','🤜','👏','🙌','👐','🤲','🤝','🙏','✍️',
  '💅','🤳','💪','🦾','🦵','🦶','👂','🦻','👃','🧠','🦷','🦴','👀','👁️','👅','👄',
  // Люди
  '👶','🧒','👦','👧','🧑','👱','👨','🧔','👩','🧓','👴','👵','🙍','🙎','🙅','🙆',
  '💁','🙋','🧏','🙇','🤦','🤷','👮','🕵️','💂','🥷','👷','🤴','👸','👳','👲','🧕',
  '🤵','👰','🤰','🤱','👼','🎅','🤶','🦸','🦹','🧙','🧚','🧛','🧜','🧝','🧞','🧟',
  '💆','💇','🚶','🧍','🧎','🏃','💃','🕺','🕴️','👯','🧖','🧗','🏌️','🏇','🧘','🏄',
  // Животные
  '🐶','🐱','🐭','🐹','🐰','🦊','🐻','🐼','🐨','🐯','🦁','🐮','🐷','🐸','🐵','🙈',
  '🙉','🙊','🐔','🐧','🐦','🐤','🦆','🦅','🦉','🦇','🐺','🐗','🐴','🦄','🐝','🪱',
  '🐛','🦋','🐌','🐞','🐜','🦟','🦗','🕷️','🦂','🐢','🐍','🦎','🦖','🦕','🐙','🦑',
  '🦐','🦞','🦀','🐡','🐠','🐟','🐬','🐳','🐋','🦈','🐊','🐅','🐆','🦓','🦍','🦧',
  '🦣','🐘','🦛','🦏','🐪','🐫','🦒','🦘','🦬','🐃','🐂','🐄','🐎','🐖','🐏','🐑',
  '🦙','🐐','🦌','🐕','🐩','🦮','🐈','🐓','🦃','🦤','🦚','🦜','🦢','🦩','🕊️','🐇',
  '🦝','🦨','🦡','🦫','🦦','🦥','🐁','🐀','🐿️','🦔',
  // Еда
  '🍎','🍐','🍊','🍋','🍌','🍉','🍇','🍓','🫐','🍈','🍒','🍑','🥭','🍍','🥥','🥝',
  '🍅','🥑','🍆','🥔','🥕','🌽','🌶️','🫑','🥒','🥬','🥦','🧄','🧅','🍄','🥜','🌰',
  '🍞','🥐','🥖','🥨','🧀','🍳','🥚','🍔','🍟','🌭','🍕','🌮','🌯','🫔','🥙','🧆',
  '🍜','🍝','🍛','🍣','🍱','🍤','🍙','🍘','🍥','🥮','🍢','🧁','🍰','🎂','🍮','🍭',
  '🍬','🍫','🍿','🧂','🍩','🍪','🌰','🍯','🧃','🥤','🧋','☕','🍵','🧉','🍺','🍻',
  // Природа
  '🌸','🌺','🌻','🌹','🪷','🌷','🌼','🌱','🌿','☘️','🍀','🎍','🪴','🍁','🍂','🍃',
  '🍄','🌾','💐','🌵','🎋','🌲','🌳','🌴','🪵','🪨','🌊','💧','🔥','🌈','⭐','🌟',
  '✨','💫','⚡','🌙','☀️','🌤️','⛅','🌦️','🌧️','⛈️','🌩️','❄️','💨','🌀','🌈',
  // Предметы
  '⚽','🏀','🏈','⚾','🥎','🎾','🏐','🏉','🥏','🎱','🪀','🏓','🏸','🥊','🥋','🎯',
  '⛸️','🎿','🛷','🎮','🎲','🧩','🧸','🪁','🎭','🎨','🖼️','🎪','🎤','🎧','🎵','🎶',
  '🎸','🎹','🎷','🎺','🎻','🪘','🥁','📱','💻','⌨️','🖥️','🖨️','📷','📸','📹','🎥',
  '📺','📻','🎙️','⏰','⌛','⏳','📡','🔋','💡','🔦','🕯️','💎','🔮','🪄','🧲','🔑',
  '🗝️','🔒','🔓','🔨','🪛','🔧','⚙️','🪤','🧰','🗡️','🛡️','🪝','🧲','🎁','🎀',
  '🎈','🎉','🎊','🎏','🎐','🎑','🎃','🎄','🎆','🎇','✉️','📦','🏆','🥇','🥈','🥉',
  // Места и символы
  '🏠','🏡','🏢','🏣','🏤','🏥','🏦','🏨','🏪','🏫','🏬','🏭','🏯','🏰','🗼','🗽',
  '⛪','🕌','🛕','⛩️','🕍','🗾','🏔️','⛰️','🌋','🗻','🏕️','🏖️','🏜️','🏝️','🌅',
  '🚗','🚕','🚙','🚌','🚎','🏎️','🚓','🚑','🚒','🚐','🛻','🚚','🚛','🚜','🏍️',
  '🛵','🚲','🛴','🛺','🚁','🛸','✈️','🚀','🛩️','⛵','🚢','🚂','🚃','🚄','🚅',
  // Флаги/символы
  '❤️','🧡','💛','💚','💙','💜','🖤','🤍','🤎','💔','❣️','💕','💞','💓','💗','💖',
  '💘','💝','💟','☮️','✝️','☯️','🆒','🆓','🆕','🆙','🆚','🈵','🔴','🟠','🟡','🟢',
  '🔵','🟣','⚫','⚪','🟤','🔺','🔻','💠','🔷','🔶','🔹','🔸','▪️','▫️','🔲','🔳',
];

function randomEmojiPick(ctx) {
  const emoji = _ALL_EMOJI[Math.floor(Math.random() * _ALL_EMOJI.length)];
  if (ctx === 'edit') {
    _editSelectedEmoji = emoji;
    _profileAvatarMode = 'emoji';
    const preview = document.getElementById('edit-custom-emoji-preview');
    const inner   = document.getElementById('edit-avatar-inner');
    const inp     = document.getElementById('edit-custom-emoji-input');
    if (preview) preview.textContent = emoji;
    if (inner)   inner.textContent   = emoji;
    if (inp)     inp.value           = emoji;
    // Сброс фото-режима
    const editScreen = document.getElementById('s-profile-edit');
    if (editScreen) {
      const imgEl = editScreen.querySelector('#edit-avatar-preview img');
      if (imgEl) imgEl.remove();
    }
  } else {
    _loginSelectedEmoji = emoji;
    const preview = document.getElementById('login-custom-emoji-preview');
    const inp     = document.getElementById('login-custom-emoji-input');
    if (preview) preview.textContent = emoji;
    if (inp)     inp.value           = emoji;
  }
  // Маленькая анимация
  const previewEl = document.getElementById(ctx + '-custom-emoji-preview');
  if (previewEl) {
    previewEl.style.transform = 'scale(1.35) rotate(10deg)';
    setTimeout(() => { previewEl.style.transform = ''; }, 200);
  }
}

// Извлечь первый эмодзи из строки
function extractFirstEmoji(str) {
  if (!str) return null;
  // Match any emoji sequence (including ZWJ sequences, modifier sequences, etc.)
  const match = str.match(/\p{Emoji_Presentation}(?:\uFE0F?\u20E3?|\u200D[\p{Emoji_Presentation}\p{Emoji}])*|\p{Emoji}\uFE0F(?:\u20E3?|\u200D[\p{Emoji_Presentation}\p{Emoji}])*/u);
  if (!match) return null;
  const cp = match[0].codePointAt(0);
  if (!cp || cp < 0x00A9) return null; // reject plain ASCII/latin
  if (isEmoji(match[0])) return match[0];
  return null;
}

// Обработчик поля ввода эмодзи на экране регистрации
function loginCustomEmojiInput(el) {
  const errEl = document.getElementById('login-custom-emoji-error');
  const preview = document.getElementById('login-custom-emoji-preview');
  const val = el.value;
  if (!val.trim()) {
    if (errEl) errEl.textContent = '';
    if (preview) preview.textContent = '';
    el.style.borderColor = 'var(--surface3)';
    return;
  }
  const emoji = extractFirstEmoji(val);
  if (emoji) {
    _loginSelectedEmoji = emoji;
    if (preview) preview.textContent = emoji;
    if (errEl) errEl.textContent = '';
    el.style.borderColor = 'var(--accent)';
    // Deselect preset grid
    document.querySelectorAll('#login-emoji-grid button').forEach(b => b.style.borderColor = 'var(--surface3)');
  } else {
    if (errEl) errEl.textContent = 'Это не эмодзи — вставь символ с клавиатуры';
    if (preview) preview.textContent = '';
    el.style.borderColor = 'var(--danger,#c94f4f)';
  }
}

// Обработчик поля ввода эмодзи на экране редактирования профиля
function editCustomEmojiInput(el) {
  const errEl = document.getElementById('edit-custom-emoji-error');
  const preview = document.getElementById('edit-custom-emoji-preview');
  const inner = document.getElementById('edit-avatar-inner');
  const val = el.value;
  if (!val.trim()) {
    if (errEl) errEl.textContent = '';
    if (preview) preview.textContent = '';
    el.style.borderColor = 'var(--surface3)';
    return;
  }
  const emoji = extractFirstEmoji(val);
  if (emoji) {
    _editSelectedEmoji = emoji;
    _profileAvatarMode = 'emoji';
    if (preview) preview.textContent = emoji;
    if (inner) inner.textContent = emoji;
    if (errEl) errEl.textContent = '';
    el.style.borderColor = 'var(--accent)';
    // Deselect preset grid
    document.querySelectorAll('#edit-emoji-grid button').forEach(b => b.style.borderColor = 'var(--surface3)');
  } else {
    if (errEl) errEl.textContent = 'Это не эмодзи — вставь символ с клавиатуры';
    if (preview) preview.textContent = '';
    el.style.borderColor = 'var(--danger,#c94f4f)';
  }
}

let _loginCheckTimer = null;
function loginCheckUsername(val) {
  const status = document.getElementById('login-username-status');
  const btn = document.getElementById('login-submit-btn');
  if (!val) { if(status)status.textContent=''; if(btn)btn.disabled=true; return; }
  const clean = val.replace(/[^a-zA-Z0-9_]/g, '');
  if (document.getElementById('login-username')) document.getElementById('login-username').value = clean;
  if (clean.length < 3) {
    if(status){status.textContent='Минимум 3 символа'; status.style.color='var(--danger,#c94f4f)';}
    if(btn)btn.disabled=true; return;
  }
  // Check if taken locally
  const accounts = accountsLoad();
  if (accounts[clean.toLowerCase()]) {
    if(status){status.textContent='⚠️ Этот ник уже занят на устройстве'; status.style.color='var(--danger,#c94f4f)';}
    if(btn)btn.disabled=true; return;
  }
  if(status){status.textContent='⏳ Проверяем...'; status.style.color='var(--muted)';}
  if(btn)btn.disabled=true;
  clearTimeout(_loginCheckTimer);
  _loginCheckTimer = setTimeout(async () => {
    if (!sbReady()) {
      if(status){status.textContent='✓ Отлично!'; status.style.color='#4caf7d';}
      if(btn)btn.disabled=false; return;
    }
    try {
      // Сначала проверяем кэш _allKnownUsers чтобы не делать лишний запрос
      if (_allKnownUsers.some(u => u.username === clean.toLowerCase())) {
        if(status){status.textContent='❌ Ник уже занят'; status.style.color='var(--danger,#c94f4f)';}
        if(btn)btn.disabled=true; return;
      }
      const data = await sbGet('presence', `select=username&username=eq.${encodeURIComponent(clean.toLowerCase())}&limit=1`);
      if (Array.isArray(data) && data.length > 0) {
        if(status){status.textContent='❌ Ник уже занят'; status.style.color='var(--danger,#c94f4f)';}
        if(btn)btn.disabled=true;
      } else {
        if(status){status.textContent='✓ Ник свободен!'; status.style.color='#4caf7d';}
        if(btn)btn.disabled=false;
      }
    } catch(e) {
      if(status){status.textContent='✓ Отлично!'; status.style.color='#4caf7d';}
      if(btn)btn.disabled=false;
    }
  }, 900); // увеличен дебаунс с 600 до 900ms
}

function profileCreate() {
  const nameEl = document.getElementById('login-name');
  const unEl = document.getElementById('login-username');
  const pwdEl = document.getElementById('login-password');
  const errEl = document.getElementById('login-error');
  const name = (nameEl?.value || '').trim();
  const username = (unEl?.value || '').trim().toLowerCase();
  const pwd = (pwdEl?.value || '');
  if (!name) { if(errEl)errEl.textContent='Введи имя'; return; }
  if (username.length < 3) { if(errEl)errEl.textContent='Юзернейм слишком короткий'; return; }
  if (!pwd) { if(errEl)errEl.textContent='Придумай пароль'; return; }
  // Check uniqueness locally one more time
  const existingAccounts = accountsLoad();
  if (existingAccounts[username]) { if(errEl)errEl.textContent='Этот ник уже занят'; return; }
  const profile = {
    name, username,
    avatarType: 'emoji',
    avatar: _loginSelectedEmoji,
    bio: '',
    status: 'online',
    color: PROFILE_COLORS[0],
    createdAt: Date.now(),
    uid: Date.now().toString(36),
    pwdHash: profileHashPwd(pwd),
  };
  profileSave(profile);
  // Сохранить в локальный список аккаунтов
  const accounts = accountsLoad();
  accounts[username] = { name, avatar: _loginSelectedEmoji, createdAt: profile.createdAt, pwdHash: profile.pwdHash };
  accountsSave(accounts);
  // Сохранить полный профиль в Supabase users
  sbSaveUser(profile);
  updateNavProfileIcon(profile);
  profileConnect(profile);
  profileRenderScreen();
  showScreen('s-profile');
  toast('🎉 Добро пожаловать, ' + name + '!');
}

function loginShowRegister() {
  document.getElementById('login-register-form').style.display = '';
  document.getElementById('login-auth-form').style.display = 'none';
  const tabReg = document.getElementById('login-tab-reg');
  const tabAuth = document.getElementById('login-tab-auth');
  if (tabReg) { tabReg.style.background = 'var(--accent)'; tabReg.style.color = 'var(--btn-text,#fff)'; }
  if (tabAuth) { tabAuth.style.background = 'transparent'; tabAuth.style.color = 'var(--muted)'; }
}

function loginShowAuth(username) {
  document.getElementById('login-register-form').style.display = 'none';
  document.getElementById('login-auth-form').style.display = '';
  const tabReg = document.getElementById('login-tab-reg');
  const tabAuth = document.getElementById('login-tab-auth');
  if (tabReg) { tabReg.style.background = 'transparent'; tabReg.style.color = 'var(--muted)'; }
  if (tabAuth) { tabAuth.style.background = 'var(--accent)'; tabAuth.style.color = 'var(--btn-text,#fff)'; }
  const unEl = document.getElementById('login-auth-username');
  if (unEl && username) unEl.value = username;
  setTimeout(() => {
    const focus = username
      ? document.getElementById('login-auth-password')
      : document.getElementById('login-auth-username');
    focus?.focus();
  }, 200);
}

function loginAuthUsernameInput(val) {
  const errEl = document.getElementById('login-auth-error');
  if (errEl) errEl.textContent = '';
  const clean = val.replace(/[^a-zA-Z0-9_]/g, '');
  const el = document.getElementById('login-auth-username');
  if (el && el.value !== clean) el.value = clean;
}

function loginDoAuth() {
  const unEl  = document.getElementById('login-auth-username');
  const pwdEl = document.getElementById('login-auth-password');
  const errEl = document.getElementById('login-auth-error');
  const btnEl = document.querySelector('#login-auth-form button');
  const username = (unEl?.value || '').trim().toLowerCase();
  const pwd = pwdEl?.value || '';
  if (!username) { if(errEl) errEl.textContent = 'Введи юзернейм'; return; }
  if (!pwd)      { if(errEl) errEl.textContent = 'Введи пароль'; return; }

  // 1) Проверяем локальный список
  const accounts = accountsLoad();
  const acc = accounts[username];
  if (acc) {
    if (!acc.pwdHash) { loginRestoreAccount(username, acc); return; }
    if (profileHashPwd(pwd) !== acc.pwdHash) {
      if(errEl) errEl.textContent = 'Неверный пароль';
      if(pwdEl) { pwdEl.value = ''; pwdEl.style.borderColor = 'var(--danger,#c94f4f)'; }
      setTimeout(() => { if(pwdEl) pwdEl.style.borderColor = ''; }, 1500);
      return;
    }
    loginRestoreAccount(username, acc);
    return;
  }

  // 2) Аккаунта нет локально — ищем в Supabase
  if (!sbReady()) {
    if(errEl) errEl.textContent = 'Аккаунт не найден на устройстве и Supabase недоступен';
    return;
  }
  if(errEl) errEl.textContent = '⏳ Ищем аккаунт в облаке...';
  if(btnEl) { btnEl.disabled = true; btnEl.textContent = '⏳ Вход...'; }

  // Сначала ищем в таблице users (полный профиль с паролем)
  // Если нет — fallback в presence (для старых аккаунтов)
  sbLoadUser(username)
    .then(async row => {
      if (!row) {
        // Fallback: presence (старые аккаунты без таблицы users)
        const presRows = await sbGet('presence', `select=*&username=eq.${encodeURIComponent(username)}&limit=1`);
        row = Array.isArray(presRows) && presRows.length > 0 ? presRows[0] : null;
      }
      if(btnEl) { btnEl.disabled = false; btnEl.textContent = 'Войти'; }
      if (!row) {
        if(errEl) errEl.textContent = 'Аккаунт @' + username + ' не найден';
        return;
      }
      if (!row.pwd_hash) {
        loginRestoreFromCloud(row);
        return;
      }
      if (profileHashPwd(pwd) !== row.pwd_hash) {
        if(errEl) errEl.textContent = 'Неверный пароль';
        if(pwdEl) { pwdEl.value = ''; pwdEl.style.borderColor = 'var(--danger,#c94f4f)'; }
        setTimeout(() => { if(pwdEl) pwdEl.style.borderColor = ''; }, 1500);
        return;
      }
      if(errEl) errEl.textContent = '';
      loginRestoreFromCloud(row);
    })
    .catch(() => {
      if(btnEl) { btnEl.disabled = false; btnEl.textContent = 'Войти'; }
      if(errEl) errEl.textContent = 'Ошибка подключения к облаку';
    });
}

// Восстановление профиля из строки Supabase presence
function loginRestoreFromCloud(row) {
  const profile = {
    name:       row.name        || row.username,
    username:   row.username,
    avatarType: row.avatar_type || 'emoji',
    avatar:     row.avatar      || '😊',
    avatarData: row.avatar_data || null,
    bio:        row.bio         || '',
    status:     row.status      || 'online',
    color:      row.color       || PROFILE_COLORS[0],
    vip:        row.vip         || false,
    badge:      row.badge       || null,
    frame:      row.frame       || null,
    banner:     row.banner      || null,
    createdAt:  row.created_at  || Date.now(),
    uid:        row.uid         || Date.now().toString(36),
    pwdHash:    row.pwd_hash    || null,
  };
  profileSave(profile);
  // Сохраняем в локальный список аккаунтов
  const accounts = accountsLoad();
  accounts[row.username] = {
    name: profile.name, avatar: profile.avatar,
    createdAt: profile.createdAt, pwdHash: profile.pwdHash
  };
  accountsSave(accounts);
  updateNavProfileIcon(profile);
  profileConnect(profile);
  profileRenderScreen();
  showScreen('s-profile');
  toast('☁️ Добро пожаловать, ' + profile.name + '!');
}

function loginRestoreAccount(username, acc) {
  // Restore from accounts store into profile
  const existing = profileLoad();
  if (existing && existing.username === username) {
    // Already saved, just connect
    updateNavProfileIcon(existing);
    profileConnect(existing);
    profileRenderScreen();
    showScreen('s-profile');
    toast('👋 Добро пожаловать, ' + existing.name + '!');
    return;
  }
  // Re-create minimal profile from accounts store
  const profile = {
    name: acc.name, username,
    avatarType: 'emoji',
    avatar: acc.avatar || '😊',
    bio: '', status: 'online',
    color: PROFILE_COLORS[0],
    createdAt: acc.createdAt || Date.now(),
    uid: acc.uid || Date.now().toString(36),
    pwdHash: acc.pwdHash,
  };
  profileSave(profile);
  updateNavProfileIcon(profile);
  profileConnect(profile);
  profileRenderScreen();
  showScreen('s-profile');
  toast('👋 Добро пожаловать, ' + profile.name + '!');
}

// ══ ЭКРАН ПРОФИЛЯ ════════════════════════════════════════════════
function profileRenderScreen() {
  const p = profileLoad();
  const body = document.getElementById('profile-body');
  if (!body) return;
  if (!p) {
    body.innerHTML = '<div style="text-align:center;padding:40px"><button class="btn btn-accent" onclick="profileInitLoginScreen();showScreen(\'s-login\')">Войти / Создать аккаунт</button></div>';
    return;
  }

  const statusObj = PROFILE_STATUSES.find(s => s.id === p.status) || PROFILE_STATUSES[0];
  const onlinePeers = _profileOnlinePeers || [];
  const vip = p.vip;
  if (typeof PROFILE_FRAMES === 'undefined') { setTimeout(profileRenderScreen, 200); return; }
  const frameStyle = PROFILE_FRAMES[p.frame] || PROFILE_FRAMES['none'];
  const badgeObj = PROFILE_BADGES ? PROFILE_BADGES.find(b => b.id === p.badge) : null;
  const hi = loadHiScores();

  // Баннер — p.banner уже содержит готовый CSS вроде 'background:linear-gradient(...)'
  const bannerStyle = p.banner
    ? (p.banner.startsWith('background:') ? p.banner : `background:${p.banner}`)
    : `background:linear-gradient(135deg,${p.color||'var(--accent)'}44,var(--surface2))`;

  body.innerHTML = `
    <!-- Баннер + аватар (Telegram-style) -->
    <div style="position:relative;margin:-16px -18px 0;margin-bottom:0">
      <div style="${bannerStyle};height:110px;width:100%;background-size:cover;background-position:center"></div>
      <div style="position:absolute;bottom:-44px;left:20px">
        <div style="position:relative;display:inline-block">
          <div class="profile-avatar ${frameStyle.cls}" style="width:88px;height:88px;font-size:42px;border-color:${p.color||'var(--accent)'};${frameStyle.style}">
            ${p.avatarType === 'photo' && p.avatarData
              ? `<img src="${p.avatarData}" alt="avatar" style="width:88px;height:88px;object-fit:cover;border-radius:50%">`
              : `<span>${p.avatar||'😊'}</span>`}
          </div>
          ${vip ? '<div style="position:absolute;bottom:-4px;right:-4px;font-size:20px;line-height:1;filter:drop-shadow(0 1px 3px rgba(0,0,0,.7))"><span class="vip-crown">👑</span></div>' : ''}
        </div>
      </div>
      ${badgeObj ? `<div style="position:absolute;bottom:-38px;left:118px;font-size:13px;padding:4px 10px;border-radius:12px;font-weight:700;background:${badgeObj.color}22;color:${badgeObj.color};border:1px solid ${badgeObj.color}44">${badgeObj.emoji} ${badgeObj.label}</div>` : ''}
    </div>
    <div style="height:52px"></div>

    <!-- Имя и инфо -->
    <div style="padding:0 4px 12px">
      <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
        <span style="font-size:22px;font-weight:800;color:var(--text)">${escHtml(p.name)}</span>
        ${vip ? '<span class="vip-badge-pill"><span class="vip-crown">👑</span> VIP</span>' : ''}
      </div>
      <div class="profile-username" style="margin-top:2px">@${escHtml(p.username)}</div>
      <div class="profile-status-badge" style="background:${statusObj.color}22;color:${statusObj.color};margin-top:8px">
        ${statusObj.emoji} ${statusObj.label}
      </div>
      ${p.bio ? `<div class="profile-bio" style="margin-top:10px">${escHtml(p.bio)}</div>` : ''}
    </div>

    <!-- Статистика -->
    <div class="profile-card" style="display:flex;gap:8px;margin-top:4px">
      <div class="profile-stat">
        <div class="profile-stat-val">${onlinePeers.length + 1}</div>
        <div class="profile-stat-lbl">Онлайн</div>
      </div>
      <div style="width:1px;background:var(--surface3)"></div>
      <div class="profile-stat">
        <div class="profile-stat-val">${friendsLoad().length}</div>
        <div class="profile-stat-lbl">Друзья</div>
      </div>
    </div>

    <!-- P2P статус -->
    <div class="settings-row" style="margin-bottom:8px">
      <div style="flex:1;min-width:0">
        <div style="font-size:13px;font-weight:700">📡 P2P соединение</div>
        <div class="settings-row-sub" id="profile-p2p-status">${_profilePeerReady ? '🟢 Подключено' : '🔴 Отключено'}</div>
        <div style="font-size:10px;color:var(--muted);margin-top:2px" id="profile-p2p-strategy">
          ${(() => { const s = p2pActiveStrategy(); return s.emoji + ' Канал: ' + s.label; })()}
        </div>
      </div>
      <div style="display:flex;flex-direction:column;gap:4px;flex-shrink:0">
        <button class="btn btn-surface" style="width:auto;padding:6px 10px;font-size:11px" onclick="profileConnect(profileLoad())">↺ Переподк.</button>
        <button class="btn btn-surface" style="width:auto;padding:6px 10px;font-size:11px" onclick="p2pManualSwitch()">⚡ Канал</button>
      </div>
    </div>

    <!-- Push уведомления -->
    <div class="settings-row" style="margin-bottom:8px" id="push-notif-row">
      <div>
        <div style="font-size:13px;font-weight:700">🔔 Уведомления на телефон</div>
        <div class="settings-row-sub" id="push-notif-status">${pushGetStatusText()}</div>
      </div>
      <button class="btn btn-surface" style="width:auto;padding:8px 14px;font-size:12px;flex-shrink:0" onclick="pushRequestPermission()" id="push-notif-btn">${pushGetBtnText()}</button>
    </div>

    <!-- Кнопки -->
    <div style="display:flex;flex-direction:column;gap:8px">
      <button class="btn btn-surface" onclick="profileRenderOnline();showScreen('s-online')">
        👥 Пользователи онлайн <span style="color:var(--accent);margin-left:4px">${onlinePeers.length + 1}</span>
      </button>
      <button class="btn btn-surface" onclick="showScreen('s-leaderboard')">
        🏆 Таблица лидеров
      </button>
      <!-- MESSENGER_HIDDEN_ENTRY — переместить в нав-бар после готовности -->
      <button class="btn btn-surface" onclick="messengerOpen()" style="position:relative">
        💬 Сообщения
        <span id="msg-unread-badge" style="display:none;position:absolute;top:8px;right:12px;background:var(--accent);color:#000;border-radius:10px;font-size:11px;font-weight:800;padding:2px 7px">0</span>
      </button>
    </div>
  `;
}

function escHtml(s) {
  return (s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

// ── profileInitEditScreen полностью переписан — без monkey-patch ──
// Вызывается из кнопки "Изменить" вверху профиля через profileToggleEdit()
function profileInitEditScreen() {
  const p = profileLoad();
  if (!p) return;
  const isVip = typeof vipCheck === 'function' ? vipCheck() : false;

  // ── Основные поля ──
  const nameEl = document.getElementById('edit-name');
  const unEl   = document.getElementById('edit-username');
  const bioEl  = document.getElementById('edit-bio');
  if (nameEl) nameEl.value = p.name || '';
  if (unEl)   unEl.value   = p.username || '';
  if (bioEl) {
    bioEl.value = p.bio || '';
    bioEl.oninput = () => {
      const cnt = document.getElementById('edit-bio-count');
      if (cnt) cnt.textContent = bioEl.value.length;
    };
    const cnt = document.getElementById('edit-bio-count');
    if (cnt) cnt.textContent = (p.bio||'').length;
  }

  // ── Аватар превью ──
  _editSelectedEmoji  = p.avatar || '😊';
  _editSelectedColor  = p.color  || PROFILE_COLORS[0];
  _editSelectedStatus = p.status || 'online';
  _profileAvatarMode  = p.avatarType === 'photo' ? 'photo' : 'emoji';

  const inner   = document.getElementById('edit-avatar-inner');
  const preview = document.getElementById('edit-avatar-preview');
  if (preview) preview.style.borderColor = p.color || 'var(--accent)';
  if (inner) {
    if (p.avatarType === 'photo' && p.avatarData) {
      inner.innerHTML = `<img src="${p.avatarData}" style="width:96px;height:96px;object-fit:cover;border-radius:50%">`;
    } else {
      inner.textContent = p.avatar || '😊';
    }
  }

  // ── Эмодзи-пикер (только ввод с клавиатуры) ──
  // Grid removed - emoji selected via keyboard input only
  const custIn = document.getElementById('edit-custom-emoji-input');
  const custPrev = document.getElementById('edit-custom-emoji-preview');
  const custErr = document.getElementById('edit-custom-emoji-error');
  // If current emoji is not in preset list, show it in custom input
  const isPreset = AVATAR_EMOJIS.includes(_editSelectedEmoji);
  if (custIn) {
    custIn.value = isPreset ? '' : _editSelectedEmoji;
    custIn.style.borderColor = (!isPreset && _editSelectedEmoji) ? 'var(--accent)' : 'var(--surface3)';
  }
  if (custPrev) custPrev.textContent = isPreset ? _editSelectedEmoji : _editSelectedEmoji;
  if (custErr) custErr.textContent = '';

  // ── Статусы ──
  const statusPicker = document.getElementById('edit-status-picker');
  if (statusPicker) {
    statusPicker.innerHTML = '';
    PROFILE_STATUSES.forEach(s => {
      const btn = document.createElement('button');
      btn.className = 'status-chip' + (s.id === _editSelectedStatus ? ' selected' : '');
      btn.style.background = s.id === _editSelectedStatus ? s.color + '22' : 'var(--surface2)';
      btn.style.color = s.color;
      btn.innerHTML = `${s.emoji} ${s.label}`;
      btn.onclick = () => {
        _editSelectedStatus = s.id;
        statusPicker.querySelectorAll('.status-chip').forEach(c => {
          c.classList.remove('selected'); c.style.background = 'var(--surface2)';
        });
        btn.classList.add('selected');
        btn.style.background = s.color + '22';
      };
      statusPicker.appendChild(btn);
    });
  }

  // ── Цвета ──
  const colorPicker = document.getElementById('edit-color-picker');
  if (colorPicker) {
    colorPicker.innerHTML = '';
    PROFILE_COLORS.forEach(c => {
      const dot = document.createElement('div');
      dot.className = 'color-dot' + (c === _editSelectedColor ? ' selected' : '');
      dot.style.background = c;
      dot.onclick = () => {
        _editSelectedColor = c;
        colorPicker.querySelectorAll('.color-dot').forEach(d => d.classList.remove('selected'));
        dot.classList.add('selected');
        if (preview) preview.style.borderColor = c;
      };
      colorPicker.appendChild(dot);
    });
  }

  // ── VIP-секции (рамки, значки, баннер, фото) ──
  document.getElementById('vip-edit-section')?.remove();

  if (typeof PROFILE_FRAMES === 'undefined') return; // VIP скрипт ещё не загружен

  const body = document.querySelector('#s-profile-edit .body');
  if (!body) return;

  const vipSec = document.createElement('div');
  vipSec.id = 'vip-edit-section';

  // Фото-аватар
  const photoRow = document.createElement('div');
  photoRow.innerHTML = `<div class="sep"></div>
    <div class="section-label" style="display:flex;align-items:center;gap:8px">
      📷 Фото аватара
      ${isVip
        ? '<span style="font-size:10px;color:#4caf7d;font-weight:700">✓ VIP</span>'
        : '<span style="font-size:10px;font-weight:800;background:linear-gradient(90deg,#f5c518,#e87722);color:#000;padding:2px 7px;border-radius:6px">VIP</span>'}
    </div>`;
  if (isVip) {
    const photoBtn = document.createElement('button');
    photoBtn.className = 'btn btn-surface';
    photoBtn.style.marginBottom = '12px';
    photoBtn.textContent = '📷 Выбрать фото из галереи';
    photoBtn.onclick = profilePickPhoto;
    photoRow.appendChild(photoBtn);
  } else {
    const lockNote = document.createElement('div');
    lockNote.style.cssText = 'font-size:12px;color:var(--muted);margin-bottom:12px;padding:10px;background:var(--surface2);border-radius:10px';
    lockNote.textContent = '🔒 Введи /vip КОД в CMD для активации';
    photoRow.appendChild(lockNote);
  }
  vipSec.appendChild(photoRow);

  // Рамки
  const framesDiv = document.createElement('div');
  framesDiv.innerHTML = '<div class="section-label">🖼 Рамка профиля</div>';
  const framesWrap = document.createElement('div');
  framesWrap.style.cssText = 'display:flex;flex-wrap:wrap;gap:8px;margin-bottom:16px';
  Object.entries(PROFILE_FRAMES).forEach(([id, f]) => {
    const locked = f.vip && !isVip;
    const sel = (p.frame || 'none') === id;
    const btn = document.createElement('button');
    btn.className = 'btn btn-surface';
    btn.style.cssText = `width:auto;padding:8px 14px;font-size:12px;${sel ? 'color:var(--accent);box-shadow:0 0 0 2px var(--accent);' : ''}${locked ? 'opacity:.45;' : ''}`;
    btn.textContent = f.label + (locked ? ' 👑' : '');
    btn.onclick = locked ? () => toast('🔒 Только VIP') : () => profileSetFrame(id);
    framesWrap.appendChild(btn);
  });
  framesDiv.appendChild(framesWrap);
  vipSec.appendChild(framesDiv);

  // Значки
  const badgesDiv = document.createElement('div');
  badgesDiv.innerHTML = '<div class="section-label">🏷 Значок профиля</div>';
  const badgesWrap = document.createElement('div');
  badgesWrap.style.cssText = 'display:flex;flex-wrap:wrap;gap:8px;margin-bottom:16px';
  // Кнопка "Нет"
  const noneBtn = document.createElement('button');
  noneBtn.className = 'btn btn-surface';
  noneBtn.style.cssText = `width:auto;padding:8px 14px;font-size:12px;${!p.badge ? 'color:var(--accent);' : ''}`;
  noneBtn.textContent = 'Нет';
  noneBtn.onclick = () => profileSetBadge(null);
  badgesWrap.appendChild(noneBtn);
  PROFILE_BADGES.forEach(b => {
    const locked = b.vip && !isVip;
    const sel = p.badge === b.id;
    const btn = document.createElement('button');
    btn.className = 'btn btn-surface';
    btn.style.cssText = `width:auto;padding:8px 14px;font-size:12px;color:${b.color};${sel ? `box-shadow:0 0 0 2px ${b.color};` : ''}${locked ? 'opacity:.45;' : ''}`;
    btn.textContent = b.emoji + ' ' + b.label + (locked ? ' 👑' : '');
    btn.onclick = locked ? () => toast('🔒 Только VIP') : () => profileSetBadge(b.id);
    badgesWrap.appendChild(btn);
  });
  badgesDiv.appendChild(badgesWrap);
  vipSec.appendChild(badgesDiv);

  // Баннеры
  const bannersDiv = document.createElement('div');
  const bannerLabel = isVip ? '🎨 Баннер профиля' : '🎨 Баннер профиля <span style="font-size:10px;font-weight:800;background:linear-gradient(90deg,#f5c518,#e87722);color:#000;padding:2px 7px;border-radius:6px">VIP</span>';
  bannersDiv.innerHTML = `<div class="section-label">${bannerLabel}</div>`;
  const bannersWrap = document.createElement('div');
  bannersWrap.style.cssText = 'display:flex;flex-wrap:wrap;gap:8px;margin-bottom:8px';
  PROFILE_BANNERS.forEach(b => {
    const locked = b.vip && !isVip;
    const sel = p.banner === b.style || (!p.banner && b.id === 'none');
    const btn = document.createElement('button');
    btn.className = 'btn btn-surface';
    btn.style.cssText = `width:auto;padding:8px 14px;font-size:12px;${sel ? 'color:var(--accent);border-color:var(--accent);' : ''}${locked ? 'opacity:.45;' : ''}`;
    btn.textContent = b.label + (locked ? ' 👑' : '');
    btn.onclick = locked ? () => toast('🔒 Только VIP') : () => profileSetBanner(b.id);
    bannersWrap.appendChild(btn);
  });
  bannersDiv.appendChild(bannersWrap);
  // VIP-only: upload custom photo banner
  if (isVip) {
    const uploadRow = document.createElement('div');
    uploadRow.style.cssText = 'display:flex;align-items:center;gap:10px;margin-bottom:16px';
    const hasPhotoBanner = p.banner && p.banner.startsWith('background:url(');
    uploadRow.innerHTML = `
      <label style="flex:1;display:flex;align-items:center;gap:10px;background:var(--surface2);border-radius:12px;padding:10px 14px;cursor:pointer;border:2px solid ${hasPhotoBanner ? 'var(--accent)' : 'var(--surface3)'}">
        <span style="font-size:18px">🖼</span>
        <span style="font-size:13px;color:${hasPhotoBanner ? 'var(--accent)' : 'var(--muted)'};">${hasPhotoBanner ? 'Фото-баннер установлен' : 'Загрузить фото как баннер'}</span>
        <input type="file" accept="image/*" style="display:none" onchange="profileUploadPhotoBanner(this)">
      </label>
      ${hasPhotoBanner ? '<button class="btn btn-surface" style="width:auto;padding:8px 14px;font-size:12px;flex-shrink:0" onclick="profileSetBanner(\'none\')">✕ Убрать</button>' : ''}
    `;
    bannersDiv.appendChild(uploadRow);
  } else {
    const spacer = document.createElement('div');
    spacer.style.marginBottom = '16px';
    bannersDiv.appendChild(spacer);
  }
  vipSec.appendChild(bannersDiv);

  // VIP промо-блок если не VIP
  if (!isVip) {
    const promo = document.createElement('div');
    promo.innerHTML = `
      <div class="sep"></div>
      <div style="background:linear-gradient(135deg,#f5c51822,#e8772222);border:1px solid #f5c51844;border-radius:14px;padding:16px;text-align:center;margin-bottom:12px">
        <div style="font-size:24px;margin-bottom:6px">👑</div>
        <div style="font-weight:800;margin-bottom:4px">VIP Аккаунт</div>
        <div style="font-size:12px;color:var(--muted);margin-bottom:8px">Фото-аватар · рамки · значки · баннеры</div>
        <div style="font-size:11px;color:var(--muted)">Введи <code style="color:var(--accent);background:var(--surface3);padding:2px 6px;border-radius:4px">/vip КОД</code> в CMD</div>
      </div>
    `;
    vipSec.appendChild(promo);
  }

  // Вставить перед кнопкой "Выйти"
  const logoutBtn = body.querySelector('button[onclick*="profileLogout"]') || body.lastElementChild;
  body.insertBefore(vipSec, logoutBtn);
}

function profileToggleEdit() {
  profileInitEditScreen();
  showScreen('s-profile-edit');
}

function profileCheckUsername(val) {
  const status = document.getElementById('edit-username-status');
  if (!status) return;
  const clean = val.replace(/[^a-zA-Z0-9_]/g, '');
  const el = document.getElementById('edit-username');
  if (el) el.value = clean;
  if (clean.length < 3) {
    status.textContent = 'Минимум 3 символа'; status.style.color = 'var(--danger,#c94f4f)';
  } else {
    status.textContent = '✓ Ок'; status.style.color = '#4caf7d';
  }
}

let _editSelectedEmoji = '😊', _editSelectedColor = PROFILE_COLORS[0], _editSelectedStatus = 'online';
let _profileAvatarMode = 'emoji';
let _profileWaitingForPhoto = false;

function profilePickAvatarType() {
  const pickerEl = document.getElementById('edit-avatar-emoji-picker');
  if (!pickerEl) return;
  if (pickerEl.style.display === 'none') {
    pickerEl.style.display = '';
  } else {
    pickerEl.style.display = 'none';
  }
}

// ══ IMAGE CROP ENGINE ════════════════════════════════════════════
// openImageCrop(dataUrl, options)
//   options.mode  : 'avatar' | 'banner'
//   options.onDone: function(croppedDataUrl)
const _cropState = {};

function openImageCrop(dataUrl, options) {
  const mode    = options.mode  || 'avatar';
  const onDone  = options.onDone || (() => {});
  const isAvatar = mode === 'avatar';

  // Viewport size
  const vw = Math.min(window.innerWidth, 420);
  const cropW = isAvatar ? Math.min(vw - 48, 280) : Math.min(vw - 32, 360);
  const cropH = isAvatar ? cropW : Math.round(cropW * 300 / 800);

  // Remove any existing crop overlay
  document.getElementById('img-crop-overlay')?.remove();

  const overlay = document.createElement('div');
  overlay.id = 'img-crop-overlay';
  overlay.className = 'img-crop-overlay';

  overlay.innerHTML = `
    <div style="color:#fff;font-size:15px;font-weight:700;margin-bottom:12px">
      ${isAvatar ? '✂️ Кадрировать аватар' : '🖼 Кадрировать баннер'}
    </div>
    <div class="img-crop-container${isAvatar ? ' round' : ''}" id="crop-box"
         style="width:${cropW}px;height:${cropH}px">
      <img id="crop-img" class="img-crop-img" draggable="false">
      <div class="img-crop-guide"></div>
    </div>
    <div class="img-crop-hint">Перетащи пальцем · Масштаб ниже</div>
    <div class="img-crop-controls">
      <label>−</label>
      <input type="range" id="crop-zoom" min="100" max="400" value="100" step="1">
      <label>+</label>
    </div>
    <div class="img-crop-actions">
      <button class="btn btn-surface" onclick="closeCrop()">Отмена</button>
      <button class="btn" style="background:var(--accent);color:var(--btn-text,#fff)" onclick="applyCrop()">✓ Применить</button>
    </div>
  `;
  document.body.appendChild(overlay);

  const imgEl   = document.getElementById('crop-img');
  const zoomEl  = document.getElementById('crop-zoom');
  const cropBox = document.getElementById('crop-box');

  const st = _cropState;
  st.x = 0; st.y = 0; st.scale = 1;
  st.dragging = false; st.lastX = 0; st.lastY = 0;
  st.cropW = cropW; st.cropH = cropH;
  st.isAvatar = isAvatar;
  st.onDone = onDone;
  st.rawSrc = dataUrl;

  // Load image and centre it
  const tmpImg = new Image();
  tmpImg.onload = () => {
    st.natW = tmpImg.naturalWidth;
    st.natH = tmpImg.naturalHeight;
    // Initial scale: fit so shorter side fills the crop box
    const fitScale = Math.max(cropW / st.natW, cropH / st.natH);
    st.minScale = fitScale;
    st.scale = fitScale;
    zoomEl.min  = Math.round(fitScale * 100);
    zoomEl.max  = Math.round(fitScale * 400);
    zoomEl.value = Math.round(fitScale * 100);
    _cropApplyTransform();
    imgEl.src = dataUrl;
  };
  tmpImg.src = dataUrl;

  // ── Pointer drag ──────────────────────────────────────────────
  function onDown(e) {
    e.preventDefault();
    st.dragging = true;
    const pt = e.touches ? e.touches[0] : e;
    st.lastX = pt.clientX; st.lastY = pt.clientY;
    // Pinch-zoom init
    if (e.touches && e.touches.length === 2) {
      st.pinching = true;
      st.pinchDist = _pinchDist(e.touches);
      st.pinchScale = st.scale;
    }
  }
  function onMove(e) {
    e.preventDefault();
    if (!st.dragging) return;
    if (e.touches && e.touches.length === 2 && st.pinching) {
      const d = _pinchDist(e.touches);
      const newScale = Math.max(st.minScale, Math.min(st.minScale * 4, st.pinchScale * d / st.pinchDist));
      st.scale = newScale;
      zoomEl.value = Math.round(newScale * 100);
      _cropClamp();
      _cropApplyTransform();
      return;
    }
    st.pinching = false;
    const pt = e.touches ? e.touches[0] : e;
    st.x += pt.clientX - st.lastX;
    st.y += pt.clientY - st.lastY;
    st.lastX = pt.clientX; st.lastY = pt.clientY;
    _cropClamp();
    _cropApplyTransform();
  }
  function onUp() { st.dragging = false; st.pinching = false; }

  cropBox.addEventListener('mousedown',  onDown, {passive:false});
  cropBox.addEventListener('touchstart', onDown, {passive:false});
  window.addEventListener('mousemove',  onMove, {passive:false});
  window.addEventListener('touchmove',  onMove, {passive:false});
  window.addEventListener('mouseup',   onUp);
  window.addEventListener('touchend',  onUp);
  st._cleanupListeners = () => {
    cropBox.removeEventListener('mousedown',  onDown);
    cropBox.removeEventListener('touchstart', onDown);
    window.removeEventListener('mousemove',  onMove);
    window.removeEventListener('touchmove',  onMove);
    window.removeEventListener('mouseup',   onUp);
    window.removeEventListener('touchend',  onUp);
  };

  // ── Zoom slider ───────────────────────────────────────────────
  zoomEl.addEventListener('input', () => {
    st.scale = parseFloat(zoomEl.value) / 100;
    _cropClamp();
    _cropApplyTransform();
  });
}

function _pinchDist(touches) {
  const dx = touches[0].clientX - touches[1].clientX;
  const dy = touches[0].clientY - touches[1].clientY;
  return Math.sqrt(dx*dx + dy*dy);
}

function _cropApplyTransform() {
  const imgEl = document.getElementById('crop-img');
  if (!imgEl) return;
  const st = _cropState;
  const w = st.natW * st.scale;
  const h = st.natH * st.scale;
  imgEl.style.width  = w + 'px';
  imgEl.style.height = h + 'px';
  imgEl.style.left   = st.x + 'px';
  imgEl.style.top    = st.y + 'px';
}

function _cropClamp() {
  const st = _cropState;
  const w = st.natW * st.scale;
  const h = st.natH * st.scale;
  // Don't let image leave the crop area
  st.x = Math.min(0, Math.max(st.cropW - w, st.x));
  st.y = Math.min(0, Math.max(st.cropH - h, st.y));
}

function closeCrop() {
  _cropState._cleanupListeners?.();
  document.getElementById('img-crop-overlay')?.remove();
}

function applyCrop() {
  const st = _cropState;
  const canvas = document.createElement('canvas');
  canvas.width  = st.cropW;
  canvas.height = st.cropH;
  const ctx = canvas.getContext('2d');
  if (st.isAvatar) {
    ctx.beginPath();
    ctx.arc(st.cropW/2, st.cropH/2, st.cropW/2, 0, Math.PI*2);
    ctx.clip();
  }
  const tmpImg = new Image();
  tmpImg.onload = () => {
    ctx.drawImage(tmpImg, st.x, st.y, st.natW * st.scale, st.natH * st.scale);
    const out = canvas.toDataURL('image/jpeg', 0.88);
    closeCrop();
    st.onDone(out);
  };
  tmpImg.src = st.rawSrc;
}
// ═════════════════════════════════════════════════════════════════

function profilePickPhoto() {
  _profileWaitingForPhoto = true;
  if (window.Android && typeof Android.pickImageForBackground === 'function') {
    Android.pickImageForBackground();
  } else {
    // Fallback: file input for non-Android environments
    const inp = document.createElement('input');
    inp.type = 'file'; inp.accept = 'image/*';
    inp.onchange = () => {
      const file = inp.files?.[0]; if (!file) return;
      const reader = new FileReader();
      reader.onload = e => _profileHandleAvatarDataUrl(e.target.result);
      reader.readAsDataURL(file);
    };
    inp.click();
  }
}

function _profileHandleAvatarDataUrl(dataUrl) {
  _profileWaitingForPhoto = false;
  openImageCrop(dataUrl, {
    mode: 'avatar',
    onDone: cropped => {
      _profileAvatarMode = 'photo';
      const inner = document.getElementById('edit-avatar-inner');
      if (inner) inner.innerHTML = `<img src="${cropped}" style="width:96px;height:96px;object-fit:cover;border-radius:50%">`;
      window._profileTempAvatarData = cropped;
    }
  });
}

// Перехватываем onNativeBgImagePicked для профиля если ждём фото
const _origOnNativeBgImagePicked = window.onNativeBgImagePicked;
window.onNativeBgImagePicked = function(dataUrl) {
  if (_profileWaitingForPhoto) {
    _profileHandleAvatarDataUrl(dataUrl);
  } else {
    if (_origOnNativeBgImagePicked) _origOnNativeBgImagePicked(dataUrl);
  }
};

async function profileSaveEdit() {
  const p = profileLoad();
  if (!p) return;
  const name     = (document.getElementById('edit-name')?.value     || '').trim();
  const username = (document.getElementById('edit-username')?.value || '').trim().toLowerCase();
  const bio      = (document.getElementById('edit-bio')?.value      || '').trim();
  if (!name || username.length < 3) { toast('❌ Заполни имя и юзернейм'); return; }

  const oldUsername = p.username;
  const oldName     = p.name;
  const usernameChanged = (oldUsername !== username);
  const nameChanged     = (oldName     !== name);

  p.name     = name;
  p.username = username;
  p.bio      = bio;
  p.status   = _editSelectedStatus;
  p.color    = _editSelectedColor;
  if (_profileAvatarMode === 'photo' && window._profileTempAvatarData) {
    p.avatarType = 'photo';
    p.avatarData = window._profileTempAvatarData;
    window._profileTempAvatarData = null;
  } else {
    p.avatarType = 'emoji';
    p.avatar     = _editSelectedEmoji;
    delete p.avatarData;
  }

  // ── Миграция данных при смене username ────────────────────────────
  if (usernameChanged) {
    toast('⏳ Переименовываем аккаунт...');

    // 1. accounts: переносим ключ
    const accounts = accountsLoad();
    if (accounts[oldUsername]) {
      accounts[username] = { ...accounts[oldUsername], name, avatar: p.avatar };
      delete accounts[oldUsername];
      accountsSave(accounts);
    }

    // 2. Локальные сообщения: обновляем поля from/to
    const msgs  = msgLoad();
    const newMsgs = {};
    Object.entries(msgs).forEach(([chatKey, arr]) => {
      newMsgs[chatKey] = arr.map(m => ({
        ...m,
        from: m.from === oldUsername ? username : m.from,
        to:   m.to   === oldUsername ? username : m.to,
      }));
    });
    msgSave(newMsgs);

    // 3. friends — не меняются (это список ДРУГИХ пользователей)

    // 4. Supabase: обновляем messages
    //    Для каждого чата: fetch → delete old → insert new с новыми ключами
    if (sbReady()) {
      const chats = chatsLoad();
      for (const otherUser of chats) {
        const oldKey = sbChatKey(oldUsername, otherUser);
        const newKey = sbChatKey(username,    otherUser);
        try {
          // Получаем все сообщения по старому ключу
          const rows = await sbGet('messages',
            `select=*&chat_key=eq.${encodeURIComponent(oldKey)}&order=ts.asc&limit=1000`);
          if (!Array.isArray(rows) || rows.length === 0) continue;

          // Удаляем старые
          await sbDelete('messages', `chat_key=eq.${encodeURIComponent(oldKey)}`);

          // Вставляем заново с новыми полями
          const updated = rows.map(r => ({
            chat_key:  newKey,
            from_user: r.from_user === oldUsername ? username : r.from_user,
            to_user:   r.to_user   === oldUsername ? username : r.to_user,
            text:      r.text,
            ts:        r.ts,
          }));
          // Вставляем батчем
          await fetch(`${sbUrl()}/rest/v1/messages`, {
            method: 'POST',
            headers: {
              apikey: sbKey(), Authorization: `Bearer ${sbKey()}`,
              'Content-Type': 'application/json',
              'Prefer': 'return=minimal',
            },
            body: JSON.stringify(updated),
          });
        } catch(e) {}

        // Обновляем _fbLastMsgTs ключи
        if (_fbLastMsgTs[oldKey]) {
          _fbLastMsgTs[newKey] = _fbLastMsgTs[oldKey];
          delete _fbLastMsgTs[oldKey];
        }
      }

      // Суpabase presence: старая запись удалится через DELETE+INSERT в profileConnect
      await sbDelete('presence', `username=eq.${encodeURIComponent(oldUsername)}`);

      // leaderboard
      await fetch(`${sbUrl()}/rest/v1/leaderboard?username=eq.${encodeURIComponent(oldUsername)}`, {
        method: 'PATCH',
        headers: {
          apikey: sbKey(), Authorization: `Bearer ${sbKey()}`,
          'Content-Type': 'application/json', 'Prefer': 'return=minimal',
        },
        body: JSON.stringify({ username }),
      }).catch(() => {});
    }

    // 5. Перезапускаем потоки с новыми chat_key
    Object.values(_fbMsgStreams).forEach(t => clearInterval(t));
    _fbMsgStreams = {};
  }

  // Если изменилось только имя — обновляем в presence через profileConnect
  profileSave(p);
  sbSaveUser(p); // синхронизируем полный профиль в облако
  profileConnect(p);
  updateNavProfileIcon(p);
  profileBroadcast({ type: 'profile_update', profile: profilePublicData(p) });

  showScreen('s-profile', 'back');
  setTimeout(profileRenderScreen, 100);
  toast(usernameChanged ? '✅ Юзернейм изменён — чаты сохранены' : '✅ Профиль сохранён');
}

async function profileDeleteAccount() {
  const p = profileLoad();
  if (!p) return;
  // Двойное подтверждение
  if (!confirm('Удалить аккаунт @' + p.username + '?\n\nЭто действие необратимо: профиль, все сообщения и данные будут удалены.')) return;
  if (!confirm('Точно удалить аккаунт @' + p.username + '? Отмены нет.')) return;

  toast('⏳ Удаляем аккаунт...');
  profileDisconnect();

  // 1. Supabase: удаляем presence
  if (sbReady()) {
    await sbDelete('presence', 'username=eq.' + encodeURIComponent(p.username));
    // 2. Supabase: удаляем все сообщения где участвует этот юзер
    await sbDelete('messages', 'from_user=eq.' + encodeURIComponent(p.username));
    await sbDelete('messages', 'to_user=eq.'   + encodeURIComponent(p.username));
    // 3. Supabase: удаляем из leaderboard
    await sbDelete('leaderboard', 'username=eq.' + encodeURIComponent(p.username));
  }

  // 4. Локальное хранилище: удаляем профиль из accounts
  const accounts = accountsLoad();
  delete accounts[p.username];
  accountsSave(accounts);

  // 5. Очищаем все личные данные
  localStorage.removeItem(PROFILE_KEY);
  localStorage.removeItem(FRIENDS_KEY);
  localStorage.removeItem(MSG_STORE_KEY);
  localStorage.removeItem(MSG_CHATS_KEY);

  updateNavProfileIcon(null);
  profileInitLoginScreen();
  showScreen('s-login');
  loginShowRegister();
  toast('✅ Аккаунт удалён');
}

function profileLogout() {
  if (!confirm('Выйти из аккаунта? Данные профиля останутся на устройстве')) return;
  const p = profileLoad();
  // Make sure pwdHash is saved in accounts before clearing profile
  if (p) {
    const accounts = accountsLoad();
    if (accounts[p.username]) {
      accounts[p.username].pwdHash = p.pwdHash;
      accounts[p.username].name = p.name;
      accounts[p.username].avatar = p.avatar;
      accountsSave(accounts);
    }
  }
  profileDisconnect();
  localStorage.removeItem(PROFILE_KEY);
  updateNavProfileIcon(null);
  profileInitLoginScreen();
  showScreen('s-login');
  // Открываем вкладку входа с предзаполненным юзернеймом
  if (p) loginShowAuth(p.username);
  toast('👋 До встречи!');
}

function profilePublicData(p) {
  return { name: p.name, username: p.username, avatar: p.avatar, avatarType: p.avatarType, status: p.status, color: p.color, bio: p.bio, vip: p.vip || false, badge: p.badge || null };
}

// ══════════════════════════════════════════════════════════════════
// ⚡ MULTI-STRATEGY P2P CONNECTION ENGINE
// ══════════════════════════════════════════════════════════════════
const SB_CONFIG_KEY  = 'sapp_supabase_config';
const SB_DEFAULT_URL = 'https://mjonazsosajvgevqllzs.supabase.co';
const SB_DEFAULT_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1qb25henNvc2FqdmdldnFsbHpzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM2NjAwNDMsImV4cCI6MjA4OTIzNjA0M30.yUB_HRQSeh3TeOseZ4_ARNiBuklr5AoEbBgvJJl5p3Y';

function sbConfig() {
  try {
    const stored = JSON.parse(localStorage.getItem(SB_CONFIG_KEY) || 'null');
    return stored || { url: SB_DEFAULT_URL, key: SB_DEFAULT_KEY };
  } catch(e) { return { url: SB_DEFAULT_URL, key: SB_DEFAULT_KEY }; }
}
function sbUrl()  { return (sbConfig()?.url || SB_DEFAULT_URL).replace(/\/$/, ''); }
function sbKey()  { return sbConfig()?.key || SB_DEFAULT_KEY; }
function sbReady(){ return true; }

// ── Стратегии подключения ─────────────────────────────────────────
// Каждая стратегия — отдельный способ добраться до Supabase REST API.
// Движок сам определяет какая работает и автоматически переключается.

const _P2P_STRATEGIES = [
  {
    id: 'direct',
    label: 'Прямое',
    emoji: '🔗',
    // Прямой fetch к Supabase без посредников
    async fetch(method, path, body, extraHeaders) {
      const url = `${sbUrl()}${path}`;
      const r = await fetch(url, {
        method,
        headers: { apikey: sbKey(), Authorization: `Bearer ${sbKey()}`, ...extraHeaders },
        body: body ? JSON.stringify(body) : undefined,
        signal: AbortSignal.timeout(8000),
      });
      return { ok: r.ok, status: r.status, json: () => r.json(), text: () => r.text() };
    },
  },
  {
    id: 'native',
    label: 'Нативный',
    emoji: '📲',
    // Через Android JavascriptInterface — обходит WebView сетевые ограничения
    async fetch(method, path, body, extraHeaders) {
      if (typeof window.Android?.nativeFetch !== 'function') throw new Error('no bridge');
      // nativeFetch поддерживает только GET, для остальных — делаем запрос через мост с телом
      const url  = `${sbUrl()}${path}`;
      // Строим псевдо-запрос: передаём метод и тело через специальный хелпер если есть,
      // иначе только GET
      if (method !== 'GET') throw new Error('native only GET');
      const raw  = await window.Android.nativeFetch(url);
      const data = JSON.parse(raw || '{}');
      if (!data.ok && data.status === 0) throw new Error('native fetch failed');
      return {
        ok: data.ok,
        status: data.status,
        json: async () => JSON.parse(data.body || 'null'),
        text: async () => data.body || '',
      };
    },
  },
  {
    id: 'allorigins',
    label: 'Прокси A',
    emoji: '🌐',
    async fetch(method, path, body, extraHeaders) {
      if (method !== 'GET') throw new Error('proxy only GET');
      const target = encodeURIComponent(`${sbUrl()}${path}`);
      const r = await fetch(`https://api.allorigins.win/raw?url=${target}`, {
        signal: AbortSignal.timeout(10000),
      });
      if (!r.ok) throw new Error('proxy ' + r.status);
      return { ok: true, status: 200, json: () => r.json(), text: () => r.text() };
    },
  },
  {
    id: 'corsproxy',
    label: 'Прокси B',
    emoji: '🔄',
    async fetch(method, path, body, extraHeaders) {
      // corsproxy.io пробрасывает любой метод с заголовками
      const url = `https://corsproxy.io/?${encodeURIComponent(`${sbUrl()}${path}`)}`;
      const r = await fetch(url, {
        method,
        headers: { apikey: sbKey(), Authorization: `Bearer ${sbKey()}`, ...extraHeaders },
        body: body ? JSON.stringify(body) : undefined,
        signal: AbortSignal.timeout(10000),
      });
      return { ok: r.ok, status: r.status, json: () => r.json(), text: () => r.text() };
    },
  },
  {
    id: 'jsonp',
    label: 'Прокси C',
    emoji: '🛰',
    async fetch(method, path, body, extraHeaders) {
      if (method !== 'GET') throw new Error('proxy only GET');
      const target = encodeURIComponent(`${sbUrl()}${path}`);
      const r = await fetch(`https://thingproxy.freeboard.io/fetch/${sbUrl()}${path}`, {
        headers: { apikey: sbKey(), Authorization: `Bearer ${sbKey()}` },
        signal: AbortSignal.timeout(10000),
      });
      if (!r.ok) throw new Error('proxy ' + r.status);
      return { ok: true, status: 200, json: () => r.json(), text: () => r.text() };
    },
  },
];

// ── Движок выбора стратегии ───────────────────────────────────────
let _p2pActiveIdx     = 0;        // индекс текущей стратегии
let _p2pFailCount     = 0;        // кол-во последовательных сбоев
let _p2pCheckTimer    = null;     // фоновый health-check
let _p2pSwitching     = false;    // флаг: идёт переключение
const P2P_FAIL_THRESH = 3;        // сбоев до переключения
const P2P_CHECK_INTERVAL = 20000; // мс между health-check
const P2P_STRATEGY_KEY = 'sapp_p2p_strategy';

function p2pActiveStrategy() {
  return _P2P_STRATEGIES[_p2pActiveIdx] || _P2P_STRATEGIES[0];
}

function p2pSaveStrategy(idx) {
  _p2pActiveIdx = idx;
  try { localStorage.setItem(P2P_STRATEGY_KEY, String(idx)); } catch(e) {}
}

function p2pLoadSavedStrategy() {
  try {
    const idx = parseInt(localStorage.getItem(P2P_STRATEGY_KEY) || '0');
    _p2pActiveIdx = (idx >= 0 && idx < _P2P_STRATEGIES.length) ? idx : 0;
  } catch(e) { _p2pActiveIdx = 0; }
}
p2pLoadSavedStrategy();

// Проверяем одну стратегию (лёгкий GET к presence)
async function p2pTestStrategy(idx) {
  const s = _P2P_STRATEGIES[idx];
  if (!s) return false;
  try {
    const r = await s.fetch('GET', `/rest/v1/presence?select=username&limit=1`, null, {});
    return r.ok || r.status === 200 || r.status === 404 || r.status === 406;
  } catch(e) { return false; }
}

// Находим первую работающую стратегию (обходим по порядку)
// Параллельная гонка: все стратегии стартуют одновременно, побеждает первая
async function p2pFindWorking(startIdx) {
  // Сначала быстро проверяем direct и native (самые надёжные)
  const priority = [0, 1]; // direct, native
  for (const idx of priority) {
    if (idx === _p2pActiveIdx) continue; // уже пробовали
    const ok = await p2pTestStrategy(idx);
    if (ok) return idx;
  }

  // Запускаем все остальные параллельно — кто первый ответит
  p2pSetStatus('🔍 Ищу рабочий канал...');
  return new Promise(resolve => {
    let settled = false;
    let pending  = 0;
    for (let i = 0; i < _P2P_STRATEGIES.length; i++) {
      if (priority.includes(i)) continue; // уже проверили выше
      pending++;
      p2pTestStrategy(i).then(ok => {
        if (ok && !settled) { settled = true; resolve(i); }
        pending--;
        if (pending === 0 && !settled) resolve(-1);
      }).catch(() => {
        pending--;
        if (pending === 0 && !settled) resolve(-1);
      });
    }
    if (pending === 0 && !settled) resolve(-1);
  });
}

// Вызывается после нескольких сбоев — ищем новую рабочую стратегию
async function p2pFailover() {
  if (_p2pSwitching) return;
  _p2pSwitching = true;
  _p2pFailCount  = 0;
  _profilePeerReady = false;

  p2pSetStatus('⚡ Переключаю канал...');

  const nextStart = (_p2pActiveIdx + 1) % _P2P_STRATEGIES.length;
  const found = await p2pFindWorking(nextStart);

  if (found >= 0) {
    p2pSaveStrategy(found);
    const s = _P2P_STRATEGIES[found];
    p2pSetStatus(`✅ Канал: ${s.emoji} ${s.label}`);
    _p2pSwitching = false;
    const p = profileLoad();
    if (p) profileConnect(p);
  } else {
    // Ничего не нашли, но всё равно пробуем прямое подключение
    p2pSaveStrategy(0);
    _p2pSwitching = false;
    p2pSetStatus('⚠️ Пробую напрямую...');
    setTimeout(() => {
      const p = profileLoad();
      if (p) profileConnect(p);
    }, 5000);
  }
}

// Фоновый health-check активной стратегии
function p2pStartHealthCheck() {
  clearInterval(_p2pCheckTimer);
  _p2pCheckTimer = setInterval(async () => {
    if (_p2pSwitching) return;
    const ok = await p2pTestStrategy(_p2pActiveIdx);
    if (ok) {
      _p2pFailCount = 0;
    } else {
      _p2pFailCount++;
      if (_p2pFailCount >= P2P_FAIL_THRESH) {
        p2pFailover();
      }
    }
  }, P2P_CHECK_INTERVAL);
}

function p2pStopHealthCheck() {
  clearInterval(_p2pCheckTimer);
  _p2pCheckTimer = null;
}

function p2pSetStatus(text) {
  profileUpdateP2PStatus(text);
}

// ── Обёртка fetch через активную стратегию ───────────────────────
async function p2pFetch(method, path, body, extraHeaders) {
  const s = p2pActiveStrategy();
  try {
    const r = await s.fetch(method, path, body, extraHeaders || {});
    _p2pFailCount = 0;
    return r;
  } catch(e) {
    _p2pFailCount++;
    // Если write-метод упал — пробуем напрямую как запасной вариант
    if (method !== 'GET') {
      try { return await _directFetch(method, path, body, extraHeaders || {}); } catch(e2) {}
    }
    if (_p2pFailCount >= P2P_FAIL_THRESH && !_p2pSwitching) {
      p2pFailover();
    }
    throw e;
  }
}

// ── Supabase REST helpers (переписаны через p2pFetch) ─────────────
async function sbGet(table, query = '') {
  if (!sbReady()) return null;
  try {
    const r = await p2pFetch('GET', `/rest/v1/${table}?${query}`, null, {});
    if (!r.ok && r.status !== 200) return null;
    _lastSuccessfulPoll = Date.now(); // watchdog: соединение живо
    return await r.json();
  } catch(e) { return null; }
}

async function sbUpsert(table, data) {
  if (!sbReady()) return false;
  try {
    const r = await p2pFetch('POST', `/rest/v1/${table}`, data, {
      'Content-Type': 'application/json',
      'Prefer': 'resolution=merge-duplicates,return=minimal',
    });
    return r.ok;
  } catch(e) { return false; }
}

async function sbInsert(table, data) {
  if (!sbReady()) return null;
  try {
    const r = await p2pFetch('POST', `/rest/v1/${table}`, data, {
      'Content-Type': 'application/json',
      'Prefer': 'return=representation',
    });
    if (!r.ok) return null;
    return await r.json();
  } catch(e) { return null; }
}

async function sbDelete(table, query) {
  if (!sbReady()) return;
  try {
    await p2pFetch('DELETE', `/rest/v1/${table}?${query}`, null, {});
  } catch(e) {}
}

// ── Инициализация при старте ──────────────────────────────────────
// Запускается в фоне — НЕ блокирует старт приложения
(function p2pInit() {
  // Пробуем сохранённую стратегию сразу
  // Если не работает — тихо ищем рабочую в фоне
  p2pTestStrategy(_p2pActiveIdx).then(ok => {
    if (!ok) {
      p2pFindWorking(0).then(found => {
        if (found >= 0) p2pSaveStrategy(found);
        // Если found === -1 — оставляем strategy=0 (direct), авось сеть поднимется
      });
    }
  });
})();

function supabaseSaveConfig() {
  const url = document.getElementById('sb-url-input')?.value?.trim();
  const key = document.getElementById('sb-key-input')?.value?.trim();
  const st  = document.getElementById('sb-status');
  if (!url || !key) { if(st) st.textContent = '❌ Заполни оба поля'; return; }
  localStorage.setItem(SB_CONFIG_KEY, JSON.stringify({ url, key }));
  if(st) st.textContent = '⏳ Проверяем подключение...';
  sbTestConnection().then(ok => {
    if(st) st.textContent = ok ? '🟢 Подключено!' : '🔴 Ошибка — проверь URL и ключ';
    if(ok) { toast('✅ Supabase подключён!'); profileConnect(profileLoad()); }
  });
}

async function sbTestConnection() {
  try {
    const r = await p2pFetch('GET', `/rest/v1/presence?select=username&limit=1`, null, {});
    return r.ok || r.status === 404;
  } catch(e) { return false; }
}

function sbFillSettings() {
  const c = sbConfig();
  const urlEl = document.getElementById('sb-url-input');
  const keyEl = document.getElementById('sb-key-input');
  if (urlEl && c?.url) urlEl.value = c.url;
  if (keyEl && c?.key) keyEl.value = c.key;
  if (c && document.getElementById('sb-status'))
    document.getElementById('sb-status').textContent = sbReady() ? '🟢 Настроено' : '';
}

// Устаревший sbStream (заглушка)
function sbStream(table, query, onData) { return null; }

// ── Переменные состояния ──────────────────────────────────────────
let _profileOnlinePeers  = [];
let _allKnownUsers       = [];
let _profilePeerReady    = false;
let _sbPresenceTimer     = null;
let _fbPollTimer         = null;
let _fbMsgStreams         = {};
let _fbLastMsgTs         = {};
let _connectSessionId    = 0;
let _fbInboxTimer        = null;
let _fbInboxLastTs       = 0;
// ── Watchdog: следит что соединение реально живо ──────────────────
let _watchdogTimer      = null;
let _lastSuccessfulPoll = 0;     // ts последнего успешного запроса к Supabase
const WATCHDOG_INTERVAL = 30000; // проверяем каждые 30 сек
const WATCHDOG_TIMEOUT  = 75000; // если > 75 сек без ответа — переподключаемся

// ── Watchdog: детектирует смерть соединения и переподключается ──────
function _startWatchdog(p) {
  clearInterval(_watchdogTimer);
  _lastSuccessfulPoll = Date.now();
  _watchdogTimer = setInterval(() => {
    if (!_profilePeerReady) return; // уже переподключаемся
    const silent = Date.now() - _lastSuccessfulPoll;
    if (silent > WATCHDOG_TIMEOUT) {
      console.warn('[Watchdog] Нет ответа ' + Math.round(silent/1000) + 'с — переподключаюсь');
      profileConnect(p);
    }
  }, WATCHDOG_INTERVAL);
}

// ── Инициализируем таблицы Supabase при первом подключении ────────
async function sbInitTables() {
  if (!sbReady()) return;
}

// ── Подключение ───────────────────────────────────────────────────
// ── Сохранение/загрузка полного профиля в таблице users ─────────
async function sbSaveUser(p) {
  if (!sbReady() || !p) return;
  const payload = {
    username:    p.username,
    name:        p.name,
    pwd_hash:    p.pwdHash    || null,
    avatar:      p.avatar     || '😊',
    avatar_type: p.avatarType || 'emoji',
    avatar_data: p.avatarData || null,
    bio:         p.bio        || '',
    status:      p.status     || 'online',
    color:       p.color      || '#e87722',
    vip:         p.vip        || false,
    badge:       p.badge      || null,
    frame:       p.frame      || null,
    banner:      p.banner     || null,
    created_at:  p.createdAt  || Date.now(),
  };
  try {
    await p2pFetch('POST', '/rest/v1/users', payload, {
      'Content-Type': 'application/json',
      'Prefer': 'resolution=merge-duplicates,return=minimal',
    });
  } catch(e) {}
}

async function sbLoadUser(username) {
  if (!sbReady()) return null;
  try {
    const rows = await sbGet('users', `select=*&username=eq.${encodeURIComponent(username)}&limit=1`);
    if (Array.isArray(rows) && rows.length > 0) return rows[0];
  } catch(e) {}
  return null;
}

async function profileConnect(p) {
  if (!p) return;

  // Сбрасываем флаг первого входа — каждый connect делает DELETE+INSERT
  _presenceFirstPut = true;
  // Инкрементируем session ID — все предыдущие вызовы становятся "устаревшими"
  const sessionId = ++_connectSessionId;

  // Останавливаем все таймеры немедленно
  clearInterval(_sbPresenceTimer); _sbPresenceTimer = null;
  clearInterval(_fbPollTimer);     _fbPollTimer = null;
  clearInterval(_fbInboxTimer);    _fbInboxTimer = null;
  clearInterval(_watchdogTimer);   _watchdogTimer = null;
  p2pStopHealthCheck();
  Object.values(_fbMsgStreams).forEach(t => clearInterval(t));
  _fbMsgStreams = {};
  _profilePeerReady = false;

  if (!sbReady()) {
    profileUpdateP2PStatus('⚙️ Supabase не настроен');
    return;
  }

  const s = p2pActiveStrategy();
  profileUpdateP2PStatus(`⏳ Подключаюсь (${s.emoji} ${s.label})...`);
  try {
    await sbPresencePut(p);

    if (sessionId !== _connectSessionId) return;

    _profilePeerReady = true;
    const sNow = p2pActiveStrategy();
    profileUpdateP2PStatus(`🟢 @${p.username} · ${sNow.emoji} ${sNow.label}`);

    _sbPresenceTimer = setInterval(() => {
      const pr = profileLoad(); if(pr) sbPresencePut(pr);
    }, 25000);

    _fbPollTimer = setInterval(sbPollPresence, 15000);
    p2pStartHealthCheck();
    _startWatchdog(p);

    await sbPollPresence();
    if (sessionId !== _connectSessionId) return;

    sbStartMsgPolling(p);
    sbStartInboxPolling(p);

    // Сохраняем конфиг в нативный SharedPreferences — WorkManager читает его
    // для фоновых уведомлений когда приложение закрыто.
    if (window.Android && typeof window.Android.savePushConfig === 'function') {
      try { window.Android.savePushConfig(p.username, sbUrl(), sbKey()); } catch(_){}
    }
  } catch(e) {
    if (sessionId === _connectSessionId) {
      profileUpdateP2PStatus('🔴 Ошибка — ищу другой канал...');
      p2pFailover();
    }
  }
}

// Восстанавливаем присутствие когда вкладка снова становится активной
let _visibilityDebounce = null;
let _lastVisibleTs = Date.now();
let _lastHiddenTs  = 0;
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState !== 'visible') {
    _lastVisibleTs = Date.now();
    _lastHiddenTs  = Date.now();
    return;
  }
  clearTimeout(_visibilityDebounce);
  _visibilityDebounce = setTimeout(() => {
    const p = profileLoad();
    if (!p || !sbReady()) return;
    const hiddenMs = Date.now() - (_lastHiddenTs || 0);
    // Если были в фоне > 60 сек — всегда переподключаемся полностью
    // (Android мог заморозить/убить таймеры)
    if (hiddenMs > 60000 || !_profilePeerReady) {
      profileConnect(p);
      return;
    }
    // Были в фоне меньше — просто обновляем и проверяем пропущенные
    _lastSuccessfulPoll = Date.now(); // сбрасываем watchdog
    sbPresencePut(p);
    sbPollPresence();
    _sbForcePollAllChats(p);
  }, 300);
});

// Принудительно опрашивает все известные чаты + inbox прямо сейчас
async function _sbForcePollAllChats(p) {
  if (!p || !sbReady()) return;
  // 1. Inbox — сообщения пришедшие за время пока были в фоне
  try {
    const sinceTs = Math.max(0, _fbInboxLastTs, _lastVisibleTs - 5000);
    const data = await sbGet('messages',
      `select=*&to_user=eq.${encodeURIComponent(p.username)}&ts=gt.${sinceTs}&order=ts.asc&limit=200`
    );
    if (Array.isArray(data) && data.length > 0) {
      const bySender = {};
      data.forEach(msg => {
        if (!bySender[msg.from_user]) bySender[msg.from_user] = [];
        bySender[msg.from_user].push(msg);
        _fbInboxLastTs = Math.max(_fbInboxLastTs, msg.ts);
      });
      Object.entries(bySender).forEach(([sender, msgs]) => {
        sbPollChat(p.username, sender);
        sbHandleIncomingMessages(p.username, sender, msgs);
      });
    }
  } catch(e) {}
  // 2. Убиваем и перезапускаем все per-chat таймеры (Android мог их throttle-нуть)
  const deadKeys = Object.keys(_fbMsgStreams);
  deadKeys.forEach(key => {
    clearInterval(_fbMsgStreams[key]);
    delete _fbMsgStreams[key];
  });
  // Перезапуск polling для всех известных чатов
  const chats = chatsLoad();
  chats.forEach(username => sbPollChat(p.username, username));
  // Перезапуск inbox polling
  sbStartInboxPolling(p);
}

function profileDisconnect() {
  _profilePeerReady = false;
  clearInterval(_sbPresenceTimer);
  clearInterval(_fbPollTimer);
  clearInterval(_fbInboxTimer);  _fbInboxTimer = null;
  clearInterval(_watchdogTimer); _watchdogTimer = null;
  p2pStopHealthCheck();
  Object.values(_fbMsgStreams).forEach(t => clearInterval(t));
  _fbMsgStreams = {};
  _sbPresenceTimer = null;
  _fbPollTimer = null;
  _profileOnlinePeers = [];
}

// ── Присутствие ───────────────────────────────────────────────────
// Первый раз делаем DELETE+INSERT чтобы аккаунт гарантированно появился в поиске
let _presenceFirstPut = true;
async function sbPresencePut(p) {
  if (!p || !sbReady()) return;
  // Compress photo avatar to 60x60px base64 for presence storage
  let avatarDataToStore = null;
  if (p.avatarType === 'photo' && p.avatarData) {
    try {
      const cv = document.createElement('canvas'); cv.width = cv.height = 60;
      const img = new Image(); img.src = p.avatarData;
      cv.getContext('2d').drawImage(img, 0, 0, 60, 60);
      avatarDataToStore = cv.toDataURL('image/jpeg', 0.7);
    } catch(_) {}
  }
  const payload = {
    username: p.username, name: p.name,
    avatar: p.avatar || '\u{1F60A}', avatar_type: p.avatarType || 'emoji',
    avatar_data: avatarDataToStore,
    color: p.color || '#e87722', status: p.status || 'online',
    vip: p.vip || false, badge: p.badge || null,
    pwd_hash: p.pwdHash || null,
    ts: Date.now()
  };
  try {
    if (_presenceFirstPut) {
      _presenceFirstPut = false;
      // DELETE старой записи чтобы снять любые конфликты
      await fetch(`${sbUrl()}/rest/v1/presence?username=eq.${encodeURIComponent(p.username)}`, {
        method: 'DELETE',
        headers: { apikey: sbKey(), Authorization: `Bearer ${sbKey()}` }
      });
      // INSERT чистой новой записи
      await fetch(`${sbUrl()}/rest/v1/presence`, {
        method: 'POST',
        headers: {
          apikey: sbKey(), Authorization: `Bearer ${sbKey()}`,
          'Content-Type': 'application/json',
          'Prefer': 'return=minimal'
        },
        body: JSON.stringify(payload)
      });
    } else {
      // Последующие heartbeat: обычный upsert
      await fetch(`${sbUrl()}/rest/v1/presence?on_conflict=username`, {
        method: 'POST',
        headers: {
          apikey: sbKey(), Authorization: `Bearer ${sbKey()}`,
          'Content-Type': 'application/json',
          'Prefer': 'resolution=merge-duplicates,return=minimal'
        },
        body: JSON.stringify(payload)
      });
    }
  } catch(e) {}
}

async function sbPollPresence() {
  if (!sbReady()) return;
  const data = await sbGet('presence', 'select=*&order=ts.desc&limit=500');
  if (!Array.isArray(data)) return;
  const p = profileLoad();
  const now = Date.now();
  const myUsername = p?.username;

  // Дедуплицируем по username — берём самую свежую строку на пользователя
  const seen = new Set();
  const unique = [];
  for (const u of data) {
    if (!u.username || seen.has(u.username)) continue;
    seen.add(u.username);
    unique.push(u);
  }

  // Online = seen within 90 sec
  const _mapU = u => ({
    username: u.username, name: u.name,
    avatar: u.avatar, avatarType: u.avatar_type, avatarData: u.avatar_data,
    color: u.color, status: u.status,
    vip: u.vip, badge: u.badge
  });
  _profileOnlinePeers = unique
    .filter(u => u.username !== myUsername && (now - (u.ts||0)) < 90000)
    .map(_mapU);
  // All known users (including offline) for search
  _allKnownUsers = unique
    .filter(u => u.username !== myUsername)
    .map(u => ({ ..._mapU(u), _online: (now - (u.ts||0)) < 90000 }));
  profileUpdateOnlineCount();
}

function profileUpdateOnlineCount() {
  const count = _profileOnlinePeers.length + 1;
  profileUpdateP2PStatus(
    '🟢 Онлайн • ' + count + ' ' +
    (count === 1 ? 'пользователь' : count < 5 ? 'пользователя' : 'пользователей')
  );
}

// ── Сообщения ─────────────────────────────────────────────────────
function sbChatKey(a, b) { return [a, b].sort().join('__'); }

function sbStartMsgPolling(p) {
  const chats = chatsLoad();
  chats.forEach(username => sbPollChat(p.username, username));
}

function sbPollChat(myUsername, otherUsername) {
  const key = sbChatKey(myUsername, otherUsername);
  if (_fbMsgStreams[key]) return;
  // Poll every 3 seconds for new messages
  const lastTs = _fbLastMsgTs[key] || 0;
  const doCheck = async () => {
    if (!sbReady()) return;
    const data = await sbGet('messages',
      `select=*&chat_key=eq.${key}&ts=gt.${_fbLastMsgTs[key]||0}&order=ts.asc&limit=50`
    );
    if (!Array.isArray(data) || data.length === 0) return;
    sbHandleIncomingMessages(myUsername, otherUsername, data);
  };
  doCheck();
  _fbMsgStreams[key] = setInterval(doCheck, 2000);
}

// Принудительно сбросить и перезапустить polling конкретного чата.
// Используется при открытии чата — немедленно запрашивает свежие сообщения из Supabase.
function sbForceRecheckChat(myUsername, otherUsername) {
  const key = sbChatKey(myUsername, otherUsername);
  if (_fbMsgStreams[key]) {
    clearInterval(_fbMsgStreams[key]);
    delete _fbMsgStreams[key];
  }
  sbPollChat(myUsername, otherUsername);
}

function sbStartInboxPolling(p) {
  clearInterval(_fbInboxTimer);
  _fbInboxLastTs = _fbInboxLastTs || (Date.now() - 30000); // смотрим за последние 30 сек
  const doInboxCheck = async () => {
    if (!sbReady() || !p) return;
    // Ищем все сообщения адресованные МНЕ, которые я ещё не видел
    const data = await sbGet('messages',
      `select=*&to_user=eq.${encodeURIComponent(p.username)}&ts=gt.${_fbInboxLastTs}&order=ts.asc&limit=100`
    );
    if (!Array.isArray(data) || data.length === 0) return;
    // Группируем по отправителю
    const bySender = {};
    data.forEach(msg => {
      if (!bySender[msg.from_user]) bySender[msg.from_user] = [];
      bySender[msg.from_user].push(msg);
      _fbInboxLastTs = Math.max(_fbInboxLastTs, msg.ts);
    });
    // Для каждого нового отправителя — запускаем полноценный poll и обрабатываем
    Object.entries(bySender).forEach(([sender, msgs]) => {
      sbPollChat(p.username, sender); // запускает постоянный poll
      sbHandleIncomingMessages(p.username, sender, msgs);
    });
  };
  doInboxCheck();
  _fbInboxTimer = setInterval(doInboxCheck, 2000); // 2 сек — быстрее получаем сообщения
}

function sbHandleIncomingMessages(myUsername, otherUsername, rows) {
  if (!rows || rows.length === 0) return;
  const msgs = msgLoad();
  const key = sbChatKey(myUsername, otherUsername);
  let hasNew = false;

  rows.forEach(msg => {
    if (msg.from_user === myUsername) {
      // Обновляем статус доставки
      if (!msgs[otherUsername]) msgs[otherUsername] = [];
      const local = msgs[otherUsername].find(m => m.ts === msg.ts && m.from === myUsername);
      if (local && !local.delivered) { local.delivered = true; hasNew = true; }
      _fbLastMsgTs[key] = Math.max(_fbLastMsgTs[key]||0, msg.ts);
      return;
    }
    if (!msgs[otherUsername]) msgs[otherUsername] = [];
    const exists = msgs[otherUsername].some(m => m.ts === msg.ts && m.from === msg.from_user);
    // ВСЕГДА обновляем ts — даже если сообщение уже есть локально,
    // иначе _fbLastMsgTs застревает на 0 и мы вечно тянем одно и то же
    _fbLastMsgTs[key] = Math.max(_fbLastMsgTs[key]||0, msg.ts);
    if (!exists) {
      const alreadyRead = _msgCurrentChat === otherUsername;
      // Parse sticker: single emoji-only messages stored as sticker field
      let inText = msg.text || '';
      let inSticker = msg.sticker || null;
      if (!inSticker) {
        const emojiOnly = /^(\p{Emoji_Presentation}|\p{Extended_Pictographic}){1,2}$/u;
        if (emojiOnly.test(inText.trim())) { inSticker = inText.trim(); inText = ''; }
      }
      // Parse replyTo from extra field
      let inReplyTo = null;
      try { if (msg.extra) inReplyTo = JSON.parse(msg.extra)?.replyTo || null; } catch(_){}
      msgs[otherUsername].push({
        from: msg.from_user, to: myUsername,
        text: inText, sticker: inSticker, ts: msg.ts,
        replyTo: inReplyTo, delivered: true, read: alreadyRead
      });
      hasNew = true;
    }
  });

  if (hasNew) {
    msgSave(msgs);
    const chats = chatsLoad();
    if (!chats.includes(otherUsername)) { chats.unshift(otherUsername); chatsSave(chats); }
    if (_msgCurrentChat === otherUsername) messengerRenderMessages();
    else {
      const peer = _profileOnlinePeers.find(u => u.username === otherUsername) || {};
      const lastMsg = msgs[otherUsername]?.[msgs[otherUsername].length-1];
      messengerUpdateBadge();
      if (lastMsg && lastMsg.from !== myUsername) {
        const senderName = peer.name || ('@' + otherUsername);
        const msgText    = lastMsg.text.slice(0, 60);
        showIosNotif({
          sender: senderName,
          text: msgText,
          avatar: peer.avatar,
          avatarType: peer.avatarType,
          color: peer.color,
          onTap: function() { messengerOpenChat(otherUsername); },
        });
        // Push на телефон (только если приложение свёрнуто)
        pushSend(senderName, msgText);
      }
    }
  }
}

// Отправка сообщений

// ══ PUSH УВЕДОМЛЕНИЯ (нативный мост Android) ══════════════
// Если Android-мост доступен — используем его; иначе — веб-Notification API
const _isAndroidApp = typeof window.Android === 'object' && typeof window.Android.showNotification === 'function';

function pushGetPermission() {
  if (_isAndroidApp) {
    try { return window.Android.getNotificationPermission(); } catch(e) {}
    return 'default';
  }
  if (!('Notification' in window)) return 'unsupported';
  return Notification.permission;
}

function pushGetStatusText() {
  const p = pushGetPermission();
  if (p === 'unsupported') return '⚠️ Не поддерживается браузером';
  if (p === 'granted')     return '🟢 Включены';
  if (p === 'denied')      return '🔴 Заблокированы (разреши в настройках → Приложения)';
  return '⚪️ Не настроены';
}

function pushGetBtnText() {
  const p = pushGetPermission();
  if (p === 'granted')     return 'Включены ✓';
  if (p === 'denied')      return 'Заблокированы';
  if (p === 'unsupported') return 'Недоступно';
  return 'Включить';
}

// Колбэк от Java после решения runtime-разрешения
function onNativeNotifPermissionResult(result) {
  const statusEl = document.getElementById('push-notif-status');
  const btnEl    = document.getElementById('push-notif-btn');
  if (statusEl) statusEl.textContent = pushGetStatusText();
  if (btnEl)    btnEl.textContent    = pushGetBtnText();
  if (result === 'granted') {
    toast('🔔 Уведомления включены!');
    setTimeout(() => pushSend('Расписание', 'Уведомления настроены ✅'), 800);
  } else {
    toast('❌ Уведомления не разрешены');
  }
}

function pushRequestPermission() {
  const p = pushGetPermission();
  if (p === 'granted') { toast('✅ Уведомления уже включены'); return; }
  if (p === 'denied')  { toast('🔴 Заблокированы — открой Настройки → Приложения → Уведомления'); return; }
  if (_isAndroidApp) {
    // Нативный запрос разрешения Android 13+
    try { window.Android.requestNotificationPermission(); } catch(e) {}
    return;
  }
  // Фоллбэк для браузера
  if (!('Notification' in window)) { toast('❌ Браузер не поддерживает уведомления'); return; }
  Notification.requestPermission().then(result => onNativeNotifPermissionResult(result));
}

function pushSend(title, body) {
  if (pushGetPermission() !== 'granted') return;
  if (document.visibilityState === 'visible') return; // приложение видно — не дублируем
  if (_isAndroidApp) {
    try { window.Android.showNotification(title, body); } catch(e) {}
    return;
  }
  // Фоллбэк веб
  try {
    const n = new Notification(title, { body, tag: 'sapp-msg', renotify: true });
    n.onclick = () => { window.focus(); n.close(); };
  } catch(e) {}
}

// iOS-style notification banners
let _iosNotifQueue = [];
let _iosNotifBusy  = false;
const _iosNotifDur = 4200;

function showIosNotif(opts) {
  _iosNotifQueue.push(opts);
  if (!_iosNotifBusy) _iosNotifNext();
}

function _iosNotifNext() {
  if (_iosNotifQueue.length === 0) { _iosNotifBusy = false; return; }
  _iosNotifBusy = true;
  var item = _iosNotifQueue.shift();
  var el = document.createElement('div');
  el.className = 'ios-notif';
  var avatarHtml = '';
  if (item.avatarType === 'img' && item.avatar) {
    avatarHtml = '<img src="' + item.avatar + '" alt="">';
  } else {
    avatarHtml = '<span>' + (item.avatar || '💬') + '</span>';
  }
  var accentColor = item.color || 'var(--accent)';
  var now = new Date();
  var timeStr = String(now.getHours()).padStart(2,'0') + ':' + String(now.getMinutes()).padStart(2,'0');
  el.innerHTML =
    '<div class="ios-notif-avatar" style="background:color-mix(in srgb,' + accentColor + ' 22%,var(--surface3));">' +
      avatarHtml +
      '<div class="ios-notif-app-icon">💬</div>' +
    '</div>' +
    '<div class="ios-notif-body">' +
      '<div class="ios-notif-header">' +
        '<span class="ios-notif-app">Сообщение</span>' +
        '<span class="ios-notif-time">' + timeStr + '</span>' +
      '</div>' +
      '<div class="ios-notif-sender">' + (item.sender||'') + '</div>' +
      '<div class="ios-notif-text">' + (item.text||'') + '</div>' +
    '</div>';
  el.addEventListener('click', function() {
    _iosNotifDismiss(el);
    if (item.onTap) item.onTap();
  });
  var container = document.getElementById('ios-notif-container');
  if (container) container.appendChild(el);
  requestAnimationFrame(function() { requestAnimationFrame(function() { el.classList.add('ios-show'); }); });
  try { SFX.play('toastShow'); } catch(e) {}
  el._notifTimer = setTimeout(function() { _iosNotifDismiss(el); }, _iosNotifDur);
}

function _iosNotifDismiss(el) {
  if (!el || el._notifDismissed) return;
  el._notifDismissed = true;
  clearTimeout(el._notifTimer);
  el.classList.remove('ios-show');
  el.classList.add('ios-hide');
  setTimeout(function() { el.remove(); setTimeout(_iosNotifNext, 250); }, 350);
}
// messengerSend и остальные функции мессенджера — в блоке ниже

// ── Broadcast (профиль, лидерборд) ───────────────────────────────
function profileBroadcast(data) {
  if (data.type === 'profile_update') {
    const p = profileLoad();
    if (p && sbReady()) sbPresencePut(p);
  }
  if (data.type === 'lb_update' && data.lb) {
    const p = profileLoad();
    if (!p || !sbReady()) return;
    Object.entries(data.lb).forEach(([game, entries]) => {
      const myEntry = entries.find(e => e.username === p.username);
      if (myEntry) sbUpsert('leaderboard', {
        game, username: p.username, name: p.name,
        avatar: p.avatar, color: p.color,
        score: myEntry.score, ts: Date.now()
      });
    });
  }
}

// lbSubmitMyScores уже встроен с поддержкой Supabase выше

// leaderboardRender уже встроен с поддержкой Supabase выше

function profileUpdateP2PStatus(msg) {
  const el = document.getElementById('profile-p2p-status');
  if (el) el.textContent = msg;
  // Обновляем метку активного канала
  const sEl = document.getElementById('profile-p2p-strategy');
  if (sEl) {
    const s = p2pActiveStrategy();
    sEl.textContent = s.emoji + ' Канал: ' + s.label;
  }
}

// Ручное переключение канала — показывает шит со всеми стратегиями
function p2pManualSwitch() {
  const existing = document.getElementById('p2p-strategy-sheet');
  if (existing) { existing.remove(); return; }

  const sheet = document.createElement('div');
  sheet.id = 'p2p-strategy-sheet';
  sheet.style.cssText = 'position:fixed;inset:0;z-index:8000;display:flex;flex-direction:column;justify-content:flex-end;background:rgba(0,0,0,.55)';
  sheet.innerHTML = `
    <div style="background:var(--surface);border-radius:20px 20px 0 0;padding:8px 0 calc(16px + var(--safe-bot))">
      <div style="width:40px;height:4px;border-radius:2px;background:var(--surface3);margin:10px auto 14px"></div>
      <div style="font-size:14px;font-weight:700;padding:0 20px 10px;color:var(--text)">⚡ Выбор канала подключения</div>
      ${_P2P_STRATEGIES.map((s, i) => `
        <button onclick="p2pSelectStrategy(${i});document.getElementById('p2p-strategy-sheet').remove()"
          style="width:100%;padding:14px 20px;background:${i === _p2pActiveIdx ? 'rgba(255,255,255,.07)' : 'none'};border:none;color:var(--text);font-family:inherit;font-size:14px;text-align:left;cursor:pointer;display:flex;align-items:center;gap:12px;border-bottom:1px solid rgba(255,255,255,.04)">
          <span style="font-size:22px">${s.emoji}</span>
          <span style="flex:1">${s.label}</span>
          ${i === _p2pActiveIdx ? '<span style="font-size:11px;font-weight:700;color:var(--accent)">● АКТИВЕН</span>' : ''}
          <span id="p2p-test-${s.id}" style="font-size:11px;color:var(--muted)">...</span>
        </button>`).join('')}
    </div>`;
  sheet.addEventListener('click', e => { if (e.target === sheet) sheet.remove(); });
  document.body.appendChild(sheet);

  // Тестируем все стратегии в фоне
  _P2P_STRATEGIES.forEach(async (s, i) => {
    const el = document.getElementById('p2p-test-' + s.id);
    const ok = await p2pTestStrategy(i);
    if (el) { el.textContent = ok ? '✅' : '❌'; el.style.color = ok ? '#4aff8a' : '#ff4455'; }
  });
}

async function p2pSelectStrategy(idx) {
  p2pSaveStrategy(idx);
  const s = _P2P_STRATEGIES[idx];
  toast(`${s.emoji} Канал: ${s.label}`);
  const p = profileLoad();
  if (p) profileConnect(p);
}

// Заполнить поля настроек при открытии
const _origShowScreenForSb = window.showScreen;
window.showScreen = (function(orig) {
  return function(id, dir) {
    if (id === 's-settings') sbFillSettings();
    if (orig) orig(id, dir);
  };
})(window.showScreen);

// ══ ЭКРАН ОНЛАЙН ═════════════════════════════════════════════════
async function onlineRefresh() {
  const btn = document.getElementById('online-refresh-btn');
  if (btn) { btn.textContent = '⏳'; btn.disabled = true; }
  try {
    // Обновляем своё присутствие и сразу получаем всех
    const p = profileLoad();
    if (p && sbReady()) await sbPresencePut(p);
    await sbPollPresence();
  } catch(e) {}
  profileRenderOnline();
  if (btn) { btn.textContent = '↻ Обновить'; btn.disabled = false; }
}

let _onlineSearchTimer = null;
function profileRenderOnline() {
  const list = document.getElementById('online-list');
  const hdr  = document.getElementById('online-count-hdr');
  if (!list) return;
  const p = profileLoad();
  const raw = (document.getElementById('online-search')?.value || '').trim();

  if (hdr) hdr.textContent = (_profileOnlinePeers.length + 1) + ' онлайн';

  // ── Логика поиска ────────────────────────────────────────────────
  // Нет запроса → показываем только онлайн
  // @ник       → поиск по username (только не-друзья)
  // текст      → поиск по имени (только друзья)
  const isAtSearch   = raw.startsWith('@');
  const searchVal    = isAtSearch ? raw.slice(1).toLowerCase() : raw.toLowerCase();
  const friends      = friendsLoad();

  const me = p ? [{ ...profilePublicData(p), _isMe: true, _online: true }] : [];
  let peers = [];

  if (!raw) {
    // Без поиска — только онлайн
    peers = [...me, ..._profileOnlinePeers];
  } else if (isAtSearch) {
    // @поиск — только по username, только не-друзья
    const candidates = _allKnownUsers.filter(u =>
      !friends.includes(u.username) &&
      u.username !== p?.username &&
      u.username?.toLowerCase().includes(searchVal)
    );
    peers = [...candidates];
  } else {
    // Обычный поиск — только по имени среди друзей
    const candidates = _allKnownUsers.filter(u =>
      friends.includes(u.username) &&
      u.name?.toLowerCase().includes(searchVal)
    );
    peers = [...candidates];
  }

  // Подсказка в заголовке
  const hintEl = document.getElementById('online-search-hint');
  if (hintEl) {
    if (!raw)           hintEl.textContent = '';
    else if (isAtSearch) hintEl.textContent = '🔍 Поиск по @юзернейму';
    else                 hintEl.textContent = '👥 Поиск по имени среди друзей';
  }

  if (peers.length === 0) {
    let emptyText = 'Никого нет онлайн';
    if (raw && isAtSearch)  emptyText = 'Ищем @' + searchVal + '...';
    if (raw && !isAtSearch) emptyText = 'Друзей с именем «' + raw + '» не найдено';
    list.innerHTML = `<div style="color:var(--muted);text-align:center;padding:30px" id="online-empty-msg">${emptyText}</div>`;
  }

  // Supabase-поиск: только при @поиске
  if (isAtSearch && searchVal && sbReady()) {
    clearTimeout(_onlineSearchTimer);
    _onlineSearchTimer = setTimeout(async () => {
      try {
        const q = encodeURIComponent(searchVal);
        const rows = await sbGet('presence', `select=*&username=ilike.*${q}*&limit=20`);
        if (!Array.isArray(rows)) return;
        let added = false;
        rows.forEach(u => {
          if (u.username === p?.username) return;
          if (!_allKnownUsers.some(x => x.username === u.username)) {
            _allKnownUsers.push({
              username: u.username, name: u.name,
              avatar: u.avatar, avatarType: u.avatar_type,
              avatarData: u.avatar_data, color: u.color,
              status: u.status, vip: u.vip, badge: u.badge, _online: false
            });
            added = true;
          }
        });
        if (added) profileRenderOnline();
        else if (peers.length === 0) {
          const el = document.getElementById('online-empty-msg');
          if (el) el.textContent = 'Пользователь @' + searchVal + ' не найден';
        }
      } catch(e) {
        if (peers.length === 0) {
          const el = document.getElementById('online-empty-msg');
          if (el) el.textContent = 'Ошибка поиска';
        }
      }
    }, 400);
  }

  if (peers.length === 0) return;
  list.innerHTML = peers.map(u => {
    const statusObj = PROFILE_STATUSES.find(s => s.id === u.status) || PROFILE_STATUSES[0];
    const isFriend = friendsLoad().includes(u.username);
    const isMe = u._isMe;
    const isOnline = u._isMe || u._online || _profileOnlinePeers.some(x => x.username === u.username);
    const badgeObj = typeof PROFILE_BADGES !== 'undefined' && u.badge
      ? PROFILE_BADGES.find(b => b.id === u.badge) : null;
    return `
      <div class="online-user-row">
        <div style="width:48px;height:48px;border-radius:50%;background:${u.color||'var(--surface3)'};display:flex;align-items:center;justify-content:center;font-size:26px;flex-shrink:0;position:relative">
          ${u.avatarType === 'photo' && u.avatarData
            ? `<img src="${u.avatarData}" style="width:48px;height:48px;border-radius:50%;object-fit:cover">`
            : (u.avatar || '😊')}
          <div style="position:absolute;bottom:1px;right:1px;width:12px;height:12px;border-radius:50%;background:${isOnline?'#4caf7d':'#666'};border:2px solid var(--surface)"></div>
        </div>
        <div style="flex:1;min-width:0">
          <div style="font-size:14px;font-weight:700;display:flex;align-items:center;gap:6px;flex-wrap:wrap">
            ${escHtml(u.name)}
            ${isMe ? '<span style="color:var(--accent);font-size:11px">(ты)</span>' : ''}
            ${u.vip ? '<span style="font-size:10px;font-weight:800;background:linear-gradient(90deg,#f5c518,#e87722);color:#000;padding:2px 6px;border-radius:6px">👑 VIP</span>' : ''}
            ${badgeObj ? `<span style="font-size:10px;font-weight:700;padding:2px 7px;border-radius:8px;background:${badgeObj.color}22;color:${badgeObj.color}">${badgeObj.emoji} ${badgeObj.label}</span>` : ''}
          </div>
          <div style="font-size:12px;color:var(--muted)">@${escHtml(u.username)}</div>
          <div style="font-size:11px;color:${isOnline?statusObj.color:'var(--muted)'};margin-top:2px">${isOnline ? statusObj.emoji+' '+statusObj.label : '⚫ Не в сети'}</div>
        </div>
        <div style="display:flex;flex-direction:column;gap:6px;align-items:flex-end">
          ${!isMe && !isFriend ? `<button class="btn btn-surface" style="width:auto;padding:6px 12px;font-size:11px" onclick="profileAddFriend('${escHtml(u.username)}')">+ Друг</button>` : ''}
          ${!isMe ? `<button class="btn btn-accent" style="width:auto;padding:6px 12px;font-size:11px" onclick="messengerOpenChatFrom('${escHtml(u.username)}')">💬 Написать</button>` : ''}
          ${isFriend && !isMe ? '<span style="font-size:18px">👥</span>' : ''}
        </div>
      </div>
    `;
  }).join('');
}

function profileAddFriend(username) {
  const friends = friendsLoad();
  if (!friends.includes(username)) {
    friends.push(username);
    friendsSave(friends);
    toast('👥 @' + username + ' добавлен в друзья!');
    // Запустить стрим сообщений для этого чата
    const p = profileLoad();
    if (p) sbPollChat(p.username, username);
  }
  profileRenderOnline();
}

// ══ ХУКИ: показ экрана профиля ═══════════════════════════════════
const _origShowScreen = window.showScreen;
window.showScreen = function(id, dir) {
  if (id === 's-profile')     profileRenderScreen();
  if (id === 's-online')      profileRenderOnline();
  if (id === 's-leaderboard') leaderboardRender();
  if (id === 's-messenger')   messengerRenderList();
  if (_origShowScreen) _origShowScreen(id, dir);
};

// Обновить nav items (4 кнопки)
const _origUpdateNavActive = window.updateNavActive;
window.updateNavActive = function(aid) {
  ['nav-home','nav-bells','nav-profile','nav-settings'].forEach(id =>
    document.getElementById(id)?.classList.toggle('active', id === aid)
  );
  if (typeof _navMovePill === 'function') _navMovePill(aid);
};

// ══ ЗАПУСК ════════════════════════════════════════════════════════
document.addEventListener('DOMContentLoaded', () => {
  setTimeout(profileBootstrap, 500);
  // Twemoji: parse initial DOM then watch mutations
  _initTwemoji();
});

function _twemojiParse(node) {
  if (typeof twemoji === 'undefined') return;
  twemoji.parse(node || document.body, {
    folder: 'svg',
    ext: '.svg',
    base: 'https://cdnjs.cloudflare.com/ajax/libs/twemoji/14.0.2/',
    // Apple emoji: берём PNG из emoji-datasource-apple через jsDelivr
    // Важно: фильтруем fe0f (variation selector-16) — он не входит в имена файлов Apple CDN
    callback: (icon) => {
      const cleaned = icon.split('-').filter(p => p.toLowerCase() !== 'fe0f').join('-') || icon;
      return `https://cdn.jsdelivr.net/npm/emoji-datasource-apple@15.0.1/img/apple/64/${cleaned}.png`;
    }
  });
}

function _initTwemoji() {
  if (typeof twemoji === 'undefined') {
    setTimeout(_initTwemoji, 200);
    return;
  }
  _twemojiParse(document.body);

  const observer = new MutationObserver(mutations => {
    for (const m of mutations) {
      for (const node of m.addedNodes) {
        if (node.nodeType === 1) {
          const tag = node.tagName;
          if (tag && !['CANVAS','SCRIPT','STYLE','INPUT','TEXTAREA'].includes(tag)) {
            _twemojiParse(node);
          }
        }
      }
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });
}

// ══════════════════════════════════════════════════════════════════
// 👑 VIP СИСТЕМА
// Активация: /vip LOMKICH2025  (секретный код в CMD)
// ══════════════════════════════════════════════════════════════════
const VIP_CODES = ['LOMKICH2025','SAPP_VIP','SCHEDULEAPP_PRO'];
const VIP_KEY = 'sapp_vip_v1';

function vipCheck() { return !!localStorage.getItem(VIP_KEY); }
function vipActivate(code) {
  if (!VIP_CODES.includes(code.toUpperCase())) return false;
  localStorage.setItem(VIP_KEY, '1');
  const p = profileLoad();
  if (p) { p.vip = true; profileSave(p); }
  return true;
}

// CMD команда /vip
// Добавляем в cmdExec — патч через хук
const _origCmdExecForVip = window.cmdExec;
if (typeof cmdExec === 'function') {
  // Вставим /vip перед default кейсом через monkey-patch
  window._vipCmdHandler = function(cmd, arg) {
    if (cmd === '/vip') {
      if (!arg) { cmdPrint('info','👑 Использование: /vip <КОД>  (секретный код VIP)'); return true; }
      if (vipActivate(arg)) {
        cmdPrint('ok','👑 VIP активирован! Теперь доступны: фото-аватар, рамки профиля, значки, кастомный баннер');
        toast('👑 Добро пожаловать в VIP!');
        profileRenderScreen();
      } else {
        cmdPrint('err','❌ Неверный код VIP');
      }
      return true;
    }
    return false;
  };
}

// ══════════════════════════════════════════════════════════════════
// 🖼 РАМКИ, ЗНАЧКИ, БАННЕРЫ
// ══════════════════════════════════════════════════════════════════
const PROFILE_FRAMES = {
  'none':     { cls: '',          style: '',                                               label: 'Нет',         vip: false },
  'accent':   { cls: '',          style: 'box-shadow:0 0 0 3px var(--accent)',              label: '🔶 Акцент',   vip: false },
  'glow':     { cls: '',          style: 'box-shadow:0 0 16px 4px var(--accent)',           label: '✨ Свечение', vip: true  },
  'rainbow':  { cls: 'frame-rainbow', style: '',                                           label: '🌈 Радуга',   vip: true  },
  'gold':     { cls: '',          style: 'box-shadow:0 0 0 3px #f5c518,0 0 12px #f5c51866', label: '🥇 Золото',  vip: true  },
  'neon':     { cls: '',          style: 'box-shadow:0 0 0 3px #00e5ff,0 0 20px #00e5ff88', label: '💠 Неон',    vip: true  },
  'fire':     { cls: 'frame-fire', style: '',                                              label: '🔥 Огонь',    vip: true  },
};

const PROFILE_BADGES = [
  { id: 'early',  emoji: '🌟', label: 'Первый',  color: '#f5c518', vip: false },
  { id: 'gamer',  emoji: '🎮', label: 'Геймер',  color: '#a78bfa', vip: false },
  { id: 'vip',    emoji: '👑', label: 'VIP',     color: '#f5c518', vip: true  },
  { id: 'dev',    emoji: '⚙️', label: 'Dev',     color: '#60cdff', vip: true  },
  { id: 'fire',   emoji: '🔥', label: 'Огонь',   color: '#e87722', vip: true  },
  { id: 'ghost',  emoji: '👻', label: 'Призрак', color: '#a78bfa', vip: true  },
];

const PROFILE_BANNERS = [
  { id: 'none',    label: 'Нет',          style: 'background:var(--surface2)',                                   vip: false },
  { id: 'accent',  label: 'Акцент',       style: 'background:linear-gradient(135deg,var(--accent)66,var(--surface2))', vip: false },
  { id: 'sunset',  label: '🌅 Закат',     style: 'background:linear-gradient(135deg,#e87722,#c94f4f,#a78bfa)',   vip: true  },
  { id: 'ocean',   label: '🌊 Океан',     style: 'background:linear-gradient(135deg,#00e5ff,#60cdff,#4caf7d)',   vip: true  },
  { id: 'night',   label: '🌌 Ночь',      style: 'background:linear-gradient(135deg,#1a1a2e,#16213e,#0f3460)',   vip: true  },
  { id: 'candy',   label: '🍭 Конфета',   style: 'background:linear-gradient(135deg,#ff66aa,#a78bfa,#60cdff)',   vip: true  },
  { id: 'gold',    label: '🥇 Золото',    style: 'background:linear-gradient(135deg,#f5c518,#e87722,#c94f4f)',   vip: true  },
  { id: 'matrix',  label: '💚 Матрица',   style: 'background:linear-gradient(135deg,#0d0d0d,#0a3d0a,#00ff41)',   vip: true  },
];

// ── CSS для спец-рамок и мессенджера ────────────────────────────
(function injectFrameCSS() {
  const style = document.createElement('style');
  style.textContent = `
    @keyframes rainbow-border {
      0%{border-color:#ff0000} 14%{border-color:#ff9900} 28%{border-color:#ffff00}
      42%{border-color:#00ff00} 57%{border-color:#0099ff} 71%{border-color:#6600ff} 85%{border-color:#ff00ff} 100%{border-color:#ff0000}
    }
    @keyframes fire-border {
      0%{border-color:#e87722;box-shadow:0 0 8px #e87722} 50%{border-color:#f5c518;box-shadow:0 0 18px #f5c518} 100%{border-color:#c94f4f;box-shadow:0 0 8px #c94f4f}
    }
    .frame-rainbow{animation:rainbow-border 2s linear infinite;border-width:3px!important;border-style:solid!important}
    .frame-fire{animation:fire-border 1s ease-in-out infinite;border-width:3px!important;border-style:solid!important}
    .profile-avatar{position:relative}
    #s-messenger-chat{display:flex!important;flex-direction:column!important;padding-bottom:0!important}
    #s-messenger{padding-bottom:0!important}
    #s-peer-profile{padding-bottom:0!important}
    #mc-messages{flex:1;overflow-y:auto;-webkit-overflow-scrolling:touch}
  `;
  document.head.appendChild(style);
})();

// profileInitEditScreen теперь единая функция в profile-script блоке выше.
// VIP-секции рендерятся внутри неё напрямую через PROFILE_FRAMES/BADGES/BANNERS.

function profileSetFrame(id) {
  const p = profileLoad(); if (!p) return;
  p.frame = id; profileSave(p);
  toast('✅ Рамка: ' + PROFILE_FRAMES[id].label);
  profileInitEditScreen();
}
function profileSetBadge(id) {
  const p = profileLoad(); if (!p) return;
  p.badge = id; profileSave(p);
  toast(id ? '✅ Значок выбран' : '✅ Значок убран');
  profileInitEditScreen();
}
function profileSetBanner(id) {
  const p = profileLoad(); if (!p) return;
  const b = PROFILE_BANNERS.find(x => x.id === id);
  p.banner = b ? b.style : null; profileSave(p);
  toast('✅ Баннер обновлён');
  profileInitEditScreen();
}

function profileUploadPhotoBanner(input) {
  const file = input?.files?.[0];
  if (!file) return;
  if (file.size > 5 * 1024 * 1024) { toast('❌ Фото слишком большое (макс. 5МБ)'); return; }
  const reader = new FileReader();
  reader.onload = e => {
    openImageCrop(e.target.result, {
      mode: 'banner',
      onDone: cropped => {
        const p = profileLoad(); if (!p) return;
        p.banner = `background:url(${cropped}) center/cover no-repeat`;
        profileSave(p);
        toast('✅ Фото-баннер установлен');
        profileInitEditScreen();
      }
    });
  };
  reader.readAsDataURL(file);
}

// ══════════════════════════════════════════════════════════════════
// 🏆 ТАБЛИЦА ЛИДЕРОВ  (экран s-leaderboard добавлен ниже в HTML)
// ══════════════════════════════════════════════════════════════════
const LEADERBOARD_GAMES = [
  { id: 'snake',      emoji: '🐍', label: 'Змейка'        },
  { id: 'tetris',     emoji: '🧱', label: 'Тетрис'        },
  { id: 'pong',       emoji: '🏓', label: 'Пинг-понг'     },
  { id: 'dino',       emoji: '🦕', label: 'Динозавр'      },
  { id: 'blockblast', emoji: '💥', label: 'Block Blast'   },
  { id: 'breakout',   emoji: '🏏', label: 'Арканоид'      },
  { id: 'flappy',     emoji: '🐦', label: 'Флаппи'        },
];
const LB_STORE_KEY = 'sapp_leaderboard_v1';
let _lbSelectedGame = 'snake';
let _lbFetching = false;

function lbLoad() { try { return JSON.parse(localStorage.getItem(LB_STORE_KEY)||'{}'); } catch(e){ return {}; } }
function lbSave(d) { localStorage.setItem(LB_STORE_KEY, JSON.stringify(d)); }

// Записать свой рекорд в глобальную таблицу лидеров при сохранении
function lbSubmitMyScores() {
  const p = profileLoad();
  if (!p) return;
  const hi = loadHiScores();
  const lb = lbLoad();
  LEADERBOARD_GAMES.forEach(g => {
    const score = hi[g.id] || 0;
    if (!score) return;
    if (!lb[g.id]) lb[g.id] = [];
    const existing = lb[g.id].find(e => e.username === p.username);
    if (existing) {
      if (score > existing.score) { existing.score = score; existing.name = p.name; existing.avatar = p.avatar; }
    } else {
      lb[g.id].push({ username: p.username, name: p.name, avatar: p.avatar, color: p.color, score });
    }
    lb[g.id].sort((a,b) => b.score - a.score);
    lb[g.id] = lb[g.id].slice(0, 50);
  });
  lbSave(lb);
  // Отправить свои рекорды в Supabase
  if (sbReady()) {
    const p2 = profileLoad();
    if (p2) LEADERBOARD_GAMES.forEach(g => {
      const score = hi[g.id] || 0;
      if (!score) return;
      sbUpsert('leaderboard', {
        game: g.id, username: p2.username, name: p2.name,
        avatar: p2.avatar || '😊', color: p2.color || '#e87722',
        score, ts: Date.now()
      });
    });
  }
}

async function leaderboardRender(skipFetch) {
  // Сначала отрисовываем локальные данные мгновенно
  _leaderboardDrawLocal();

  if (skipFetch || _lbFetching) return;
  _lbFetching = true;

  // Загружаем из Supabase
  const container = document.getElementById('lb-content');
  const refreshBtn = container?.querySelector('.lb-refresh-btn');
  if (refreshBtn) { refreshBtn.disabled = true; refreshBtn.textContent = '⏳ Загрузка...'; }

  try {
    const data = await sbGet('leaderboard', `game=eq.${_lbSelectedGame}&order=score.desc&limit=50`);
    if (Array.isArray(data) && data.length > 0) {
      const lb = lbLoad();
      if (!lb[_lbSelectedGame]) lb[_lbSelectedGame] = [];
      data.forEach(e => {
        const idx = lb[_lbSelectedGame].findIndex(x => x.username === e.username);
        if (idx >= 0) { if (e.score > lb[_lbSelectedGame][idx].score) lb[_lbSelectedGame][idx] = {...e}; }
        else lb[_lbSelectedGame].push({...e});
      });
      lb[_lbSelectedGame].sort((a,b) => b.score - a.score);
      lb[_lbSelectedGame] = lb[_lbSelectedGame].slice(0, 50);
      lbSave(lb);
    }
  } catch(e) { /* ignore */ }
  _lbFetching = false;
  // Перерисовываем с актуальными данными
  _leaderboardDrawLocal();
}

function _leaderboardDrawLocal() {
  lbSubmitMyScores();
  const container = document.getElementById('lb-content');
  if (!container) return;
  const lb = lbLoad();
  const p = profileLoad();

  const tabsHtml = LEADERBOARD_GAMES.map(g =>
    `<button class="diff-btn${_lbSelectedGame===g.id?' active':''}" onclick="_lbSelectedGame='${g.id}';_lbFetching=false;leaderboardRender()" style="font-size:12px;padding:7px 12px">${g.emoji} ${g.label}</button>`
  ).join('');

  const entries = lb[_lbSelectedGame] || [];
  const myHi = loadHiScores()[_lbSelectedGame] || 0;
  const myRank = entries.findIndex(e => e.username === p?.username) + 1;

  const rowsHtml = entries.length === 0
    ? `<div style="text-align:center;padding:30px;color:var(--muted)">Рекордов пока нет.<br>Сыграй и стань первым! 🏆</div>`
    : entries.slice(0, 30).map((e, i) => {
        const isMe = e.username === p?.username;
        const medal = i === 0 ? '🥇' : i === 1 ? '🥈' : i === 2 ? '🥉' : `${i+1}.`;
        return `
          <div style="display:flex;align-items:center;gap:12px;padding:12px 14px;background:${isMe?'color-mix(in srgb,var(--accent) 10%,var(--surface2))':'var(--surface2)'};border-radius:14px;border:1.5px solid ${isMe?'var(--accent)':'var(--surface3)'};margin-bottom:8px">
            <div style="font-size:18px;width:28px;text-align:center;flex-shrink:0">${medal}</div>
            <div style="width:38px;height:38px;border-radius:50%;background:${e.color||'var(--surface3)'};display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0">${e.avatar||'😊'}</div>
            <div style="flex:1;min-width:0">
              <div style="font-size:14px;font-weight:700;${isMe?'color:var(--accent)':''}">${escHtml(e.name||e.username)}${isMe?' (ты)':''}</div>
              <div style="font-size:11px;color:var(--muted)">@${escHtml(e.username)}</div>
            </div>
            <div style="font-size:18px;font-weight:800;color:var(--accent);font-family:'JetBrains Mono',monospace">${e.score}</div>
          </div>`;
      }).join('');

  const myInfoHtml = p && myHi > 0 ? `
    <div style="background:var(--surface2);border-radius:12px;padding:12px 16px;display:flex;justify-content:space-between;margin-bottom:12px;font-size:13px">
      <span style="color:var(--muted)">Твой рекорд:</span>
      <span style="font-weight:700;color:var(--accent)">${myHi}${myRank > 0 ? ` • #${myRank}` : ' • не в топе'}</span>
    </div>` : '';

  container.innerHTML = `
    <div class="diff-picker" style="flex-wrap:wrap;gap:6px;margin-bottom:12px">${tabsHtml}</div>
    ${myInfoHtml}
    ${rowsHtml}
    <button class="btn btn-surface lb-refresh-btn" style="margin-top:8px" onclick="leaderboardRender()">🔄 Обновить из сети</button>
  `;
}

// ══════════════════════════════════════════════════════════════════
// 💬 МЕССЕНДЖЕР v2 — Telegram стиль
// ══════════════════════════════════════════════════════════════════
const MSG_STORE_KEY = 'sapp_messages_v2';
const MSG_CHATS_KEY = 'sapp_chats_v1';
let _msgCurrentChat = null;
let _mcPollTimer = null;

function msgLoad()    { try { return JSON.parse(localStorage.getItem(MSG_STORE_KEY)||'{}'); } catch(e){ return {}; } }
function msgSave(d)   { localStorage.setItem(MSG_STORE_KEY, JSON.stringify(d)); }
function chatsLoad()  { try { return JSON.parse(localStorage.getItem(MSG_CHATS_KEY)||'[]'); } catch(e){ return []; } }
function chatsSave(d) { localStorage.setItem(MSG_CHATS_KEY, JSON.stringify(d)); }

function messengerOpen() { showScreen('s-messenger'); }

// ── Список чатов ─────────────────────────────────────────────────
// ── Мультиселект чатов ────────────────────────────────────────────
let _msgSelectMode = false;
const _msgSelected = new Set();

function msgSelectEnter(username) {
  _msgSelectMode = true;
  _msgSelected.clear();
  _msgSelected.add(username);
  document.getElementById('msg-hdr-normal').style.display = 'none';
  document.getElementById('msg-hdr-select').style.display = '';
  messengerRenderList();
  _msgUpdateSelectCount();
  SFX.play('btnClick');
}

function msgSelectCancel() {
  _msgSelectMode = false;
  _msgSelected.clear();
  document.getElementById('msg-hdr-normal').style.display = '';
  document.getElementById('msg-hdr-select').style.display = 'none';
  messengerRenderList();
}

function msgToggleSelect(username, e) {
  e.stopPropagation();
  if (_msgSelected.has(username)) _msgSelected.delete(username);
  else _msgSelected.add(username);
  _msgUpdateSelectCount();
  // Обновить визуал конкретной строки
  const row = document.querySelector(`[data-chat-user="${CSS.escape(username)}"]`);
  if (row) row.classList.toggle('chat-selected', _msgSelected.has(username));
}

function _msgUpdateSelectCount() {
  const el = document.getElementById('msg-select-count');
  if (el) el.textContent = 'Выбрано: ' + _msgSelected.size;
}

function msgDeleteSelected() {
  if (_msgSelected.size === 0) return;
  if (!confirm('Удалить ' + _msgSelected.size + ' чат(ов)?')) return;
  const msgs   = msgLoad();
  const chats  = chatsLoad();
  _msgSelected.forEach(u => {
    delete msgs[u];
    const idx = chats.indexOf(u);
    if (idx !== -1) chats.splice(idx, 1);
    // Остановить polling
    const p = profileLoad();
    if (p) {
      const key = sbChatKey(p.username, u);
      clearInterval(_fbMsgStreams[key]);
      delete _fbMsgStreams[key];
    }
  });
  msgSave(msgs);
  chatsSave(chats);
  msgSelectCancel();
  messengerUpdateBadge();
}

// ── Удаление одного чата изнутри ─────────────────────────────────
function messengerDeleteCurrentChat() {
  const username = _msgCurrentChat;
  if (!username) return;
  if (!confirm('Удалить чат с @' + username + '?')) return;
  const msgs  = msgLoad();
  const chats = chatsLoad();
  delete msgs[username];
  const idx = chats.indexOf(username);
  if (idx !== -1) chats.splice(idx, 1);
  msgSave(msgs);
  chatsSave(chats);
  const p = profileLoad();
  if (p) {
    const key = sbChatKey(p.username, username);
    clearInterval(_fbMsgStreams[key]);
    delete _fbMsgStreams[key];
  }
  showScreen('s-messenger', 'back');
  messengerRenderList();
  messengerUpdateBadge();
  toast('🗑 Чат удалён');
}

function messengerRenderList(filter) {
  const list = document.getElementById('messenger-list');
  if (!list) return;
  const p = profileLoad();
  let chats = chatsLoad();
  if (filter) chats = chats.filter(u => u.toLowerCase().includes(filter.toLowerCase()));
  const msgs = msgLoad();

  if (chats.length === 0 && !_msgSelectMode) {
    list.innerHTML = `
      <div style="display:flex;flex-direction:column;align-items:center;padding:60px 20px;color:var(--muted);gap:12px">
        <div style="font-size:52px">💬</div>
        <div style="font-size:17px;font-weight:700;color:var(--text)">Нет сообщений</div>
        <div style="font-size:13px;text-align:center">Открой список онлайн и начни общаться</div>
        <button class="btn btn-accent" style="margin-top:8px;width:auto;padding:12px 24px" onclick="profileRenderOnline();showScreen('s-online')">👥 Найти собеседника</button>
      </div>`;
    return;
  }

  const sorted = [...chats].sort((a, b) => {
    const la  = msgs[a]?.slice(-1)[0]?.ts || 0;
    const lb2 = msgs[b]?.slice(-1)[0]?.ts || 0;
    return lb2 - la;
  });

  list.innerHTML = sorted.map(username => {
    const chatMsgs = msgs[username] || [];
    const last     = chatMsgs[chatMsgs.length - 1];
    const unread   = chatMsgs.filter(m => m.from !== p?.username && !m.read).length;
    const peer     = _profileOnlinePeers.find(u => u.username === username)
                   || _allKnownUsers.find(u => u.username === username);
    const isOnline = !!_profileOnlinePeers.find(u => u.username === username);
    const name     = peer?.name || username;
    const avatar   = peer?.avatar || '😊';
    const color    = peer?.color || 'var(--surface3)';
    const preview  = last
      ? (last.from === p?.username ? '<span style="color:var(--accent)">Ты: </span>' : '') + escHtml(last.text?.slice(0, 50) || '')
      : '<span style="color:var(--muted)">Нет сообщений</span>';
    const timeStr  = last ? msgFormatTime(last.ts) : '';
    const isSel    = _msgSelected.has(username);

    const rowClick  = _msgSelectMode
      ? `msgToggleSelect('${username}', event)`
      : `messengerOpenChat('${username}')`;

    // avatar: photo support
    const avatarData = peer?.avatarData || peer?.avatar_data;
    const avatarHtml = (peer?.avatarType === 'photo' || peer?.avatar_type === 'photo') && avatarData
      ? `<img src="${avatarData}" style="width:52px;height:52px;border-radius:50%;object-fit:cover">`
      : `<span style="font-size:28px">${avatar}</span>`;

    return `<div
        data-chat-user="${escHtml(username)}"
        onclick="${rowClick}"
        oncontextmenu="event.preventDefault();msgSelectEnter('${username}')"
        ontouchstart="msgRowTouchStart(this,'${escHtml(username)}')"
        ontouchend="msgRowTouchEnd()"
        ontouchmove="msgRowTouchEnd()"
        class="chat-row${isSel ? ' chat-selected' : ''}"
        style="display:flex;align-items:center;gap:12px;padding:10px 16px;cursor:pointer;border-bottom:1px solid rgba(255,255,255,.04)">
      ${_msgSelectMode ? `<div class="chat-select-circle">${isSel ? '✓' : ''}</div>` : ''}
      <div style="position:relative;flex-shrink:0">
        <div style="width:52px;height:52px;border-radius:50%;background:${color};display:flex;align-items:center;justify-content:center;overflow:hidden">${avatarHtml}</div>
        ${isOnline ? '<div style="position:absolute;bottom:2px;right:2px;width:13px;height:13px;border-radius:50%;background:#4caf7d;border:2.5px solid var(--bg)"></div>' : ''}
      </div>
      <div style="flex:1;min-width:0">
        <div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:3px">
          <div style="font-size:15px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:65%">${escHtml(name)}</div>
          <div style="font-size:11px;color:${unread>0?'var(--accent)':'var(--muted)'};flex-shrink:0">${timeStr}</div>
        </div>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div style="font-size:13px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:75%">${preview}</div>
          ${unread > 0 ? '<div style="background:var(--accent);color:var(--btn-text,#000);border-radius:10px;font-size:11px;font-weight:800;padding:2px 7px;flex-shrink:0;min-width:20px;text-align:center">'+unread+'</div>' : ''}
        </div>
      </div>
    </div>`;
  }).join('');

  messengerUpdateBadge();
}


// Long-press для мобильного (зажатие → мультиселект)
let _msgLongPressTimer = null;
function msgRowTouchStart(el, username) {
  el.style.background = 'var(--surface2)';
  _msgLongPressTimer = setTimeout(() => {
    el.style.background = '';
    if (!_msgSelectMode) msgSelectEnter(username);
    else msgToggleSelect(username, { stopPropagation: () => {} });
  }, 500);
}
function msgRowTouchEnd() {
  clearTimeout(_msgLongPressTimer);
}
function messengerFilterChats(q) { messengerRenderList(q); }

// ── Открытие чата ─────────────────────────────────────────────────
function messengerOpenChat(username) {
  _msgCurrentChat = username;
  // Читаем сообщения
  const msgs = msgLoad();
  const p = profileLoad();
  if (msgs[username]) {
    msgs[username].forEach(m => { if (m.from !== p?.username) m.read = true; });
    msgSave(msgs);
  }
  messengerUpdateBadge();

  // Обновляем шапку
  const peer = _profileOnlinePeers.find(u => u.username === username);
  const hdrName = document.getElementById('mc-hdr-name');
  const hdrSub  = document.getElementById('mc-hdr-sub');
  const hdrAvatar = document.getElementById('mc-hdr-avatar');
  if (hdrName) hdrName.textContent = peer?.name || username;
  if (hdrSub)  hdrSub.textContent  = peer ? '🟢 В сети' : '@' + username;
  if (hdrAvatar) {
    const hasPhoto = (peer?.avatarType === 'photo') && peer?.avatarData;
    hdrAvatar.style.background = hasPhoto ? 'transparent' : (peer?.color || 'var(--surface3)');
    hdrAvatar.innerHTML = hasPhoto
      ? `<img src="${peer.avatarData}" style="width:36px;height:36px;border-radius:50%;object-fit:cover">`
      : (peer?.avatar || '😊');
  }

  showScreen('s-messenger-chat');
  messengerRenderMessages();

  // Фокус на поле ввода — вызывает клавиатуру на Android
  // Задержка чуть больше длины анимации перехода экрана (280ms)
  setTimeout(() => {
    const inp = document.getElementById('mc-input');
    if (inp) inp.focus();
  }, 320);

  // Fix: принудительно перезапускаем polling при открытии чата —
  // это гарантирует немедленный запрос к Supabase, не ждём следующего тика.
  // Решает проблему "нужно перезаходить чтобы увидеть новые сообщения".
  if (p) {
    sbForceRecheckChat(p.username, username);
    // Сообщаем нативному Worker'у — сдвигаем окно чтобы не дублировать уведомления
    if (window.Android && typeof window.Android.updateLastMsgTs === 'function') {
      try { window.Android.updateLastMsgTs(Date.now()); } catch(_){}
    }
  }

  // Запустить polling для этого чата (таймер-сторож, 2 сек)
  // Если стрим по какой-то причине умер — перезапускаем его немедленно
  clearInterval(_mcPollTimer);
  _mcPollTimer = setInterval(() => {
    const p2 = profileLoad();
    if (!p2 || !_msgCurrentChat) return;
    const key = sbChatKey(p2.username, _msgCurrentChat);
    if (!_fbMsgStreams[key]) sbForceRecheckChat(p2.username, _msgCurrentChat);
  }, 2000);
}

// ── Рендер сообщений ──────────────────────────────────────────────
function messengerRenderMessages(animateLast) {
  const body = document.getElementById('mc-messages');
  if (!body || !_msgCurrentChat) return;
  const msgs = msgLoad();
  const p = profileLoad();
  const chatMsgs = msgs[_msgCurrentChat] || [];

  if (chatMsgs.length === 0) {
    body.innerHTML = `<div style="text-align:center;margin:auto;padding:30px;color:var(--muted)">
      <div style="font-size:36px;margin-bottom:8px">👋</div>
      <div style="font-size:13px">Начни разговор!</div>
    </div>`;
    return;
  }

  let lastDate = null;
  let lastFrom = null;
  const html = chatMsgs.map((msg, idx) => {
    const isMe = msg.from === p?.username;
    const dt = new Date(msg.ts);
    const dateStr = dt.toLocaleDateString('ru', {day:'numeric', month:'long'});
    const showDate = dateStr !== lastDate;
    lastDate = dateStr;
    const showAvatar = !isMe && (lastFrom !== msg.from || showDate);
    lastFrom = msg.from;
    const isLast = idx === chatMsgs.length - 1;
    const nextIsOther = idx < chatMsgs.length - 1 && chatMsgs[idx+1].from !== msg.from;
    const showTail = isLast || nextIsOther;

    const peer = _profileOnlinePeers.find(u => u.username === msg.from);
    const avatarEl = showAvatar && !isMe
      ? `<div style="width:28px;height:28px;border-radius:50%;background:${peer?.color||'var(--surface3)'};display:flex;align-items:center;justify-content:center;font-size:14px;flex-shrink:0;align-self:flex-end">${peer?.avatar||'😊'}</div>`
      : `<div style="width:28px;flex-shrink:0"></div>`;

    const status = isMe
      ? `<span style="font-size:9px;opacity:.7;margin-left:3px">${msg.delivered ? '✓✓' : '✓'}</span>`
      : '';

    const bubbleRadius = isMe
      ? (showTail ? '18px 18px 4px 18px' : '18px 18px 18px 18px')
      : (showTail ? '18px 18px 18px 4px' : '18px 18px 18px 18px');

    const dateSep = showDate ? `<div style="text-align:center;margin:12px 0 8px;display:flex;align-items:center;gap:10px">
      <div style="flex:1;height:1px;background:rgba(255,255,255,.08)"></div>
      <div style="font-size:11px;font-weight:600;color:var(--muted);padding:3px 10px;background:var(--surface2);border-radius:10px;white-space:nowrap">${dateStr}</div>
      <div style="flex:1;height:1px;background:rgba(255,255,255,.08)"></div>
    </div>` : '';

    // Reply quote if msg has replyTo
    const replyQuote = msg.replyTo ? `
      <div style="border-left:3px solid ${isMe?'rgba(255,255,255,.5)':'var(--accent)'};padding:3px 8px;margin-bottom:4px;border-radius:4px;background:rgba(0,0,0,.12)">
        <div style="font-size:10px;font-weight:700;opacity:.85">${escHtml(msg.replyTo.from)}</div>
        <div style="font-size:11px;opacity:.7;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:200px">${escHtml(msg.replyTo.text?.slice(0,60)||'')}</div>
      </div>` : '';

    // Sticker rendering
    const isSticker = msg.sticker;
    const bubbleBg  = isSticker ? 'transparent' : (isMe ? 'var(--accent)' : 'var(--surface2)');
    const bubblePad = isSticker ? '0' : '8px 12px 6px';
    const msgContent = isSticker
      ? `<div class="mc-sticker-wrap" style="font-size:56px;line-height:1.1;text-align:center">${escHtml(msg.sticker)}</div>`
      : (replyQuote + escHtml(msg.text));

    // Reactions display
    const reactionsHtml = msg.reactions && Object.keys(msg.reactions).length
      ? `<div style="display:flex;flex-wrap:wrap;gap:3px;margin-top:4px">
          ${Object.entries(msg.reactions).map(([em,users])=>
            `<span onclick="mcToggleReaction(${idx},'${em}')" style="background:rgba(255,255,255,.12);border-radius:10px;padding:2px 7px;font-size:13px;cursor:pointer">${em} ${users.length}</span>`
          ).join('')}
         </div>` : '';

    return `${dateSep}
    <div data-msg-bubble data-msg-me="${isMe?'1':'0'}" data-msg-idx="${idx}"
      style="display:flex;gap:6px;justify-content:${isMe?'flex-end':'flex-start'};align-items:flex-end;margin-bottom:${showTail?'4px':'1px'};position:relative;touch-action:pan-y;user-select:none;-webkit-user-select:none"
      ontouchstart="mcBubbleTouchStart(event,this,${idx})"
      ontouchmove="mcBubbleTouchMove(event,this,${idx})"
      ontouchend="mcBubbleTouchEnd(event,this,${idx})"
      onmousedown="mcBubbleMouseDown(event,this,${idx})"
      ondblclick="mcBubbleDblClick(event,${idx})"
      onclick="mcBubbleClick(event,${idx})">
      ${isMe ? '' : avatarEl}
      <!-- Swipe reply-arrow hint -->
      <div class="mc-reply-hint" style="position:absolute;${isMe?'left:2px':'right:2px'};top:50%;transform:translateY(-50%);opacity:0;transition:opacity .12s;font-size:18px;pointer-events:none;z-index:1">↩</div>
      <div class="mc-bubble-inner" style="max-width:78%;padding:${bubblePad};border-radius:${bubbleRadius};background:${bubbleBg};color:${isMe?'var(--btn-text,#fff)':'var(--text)'};font-size:14px;line-height:1.5;word-break:break-word;position:relative;transition:transform .18s cubic-bezier(.4,0,.2,1)">
        ${msgContent}
        ${isSticker ? '' : `<div style="font-size:10px;opacity:.65;text-align:right;margin-top:2px;display:flex;align-items:center;justify-content:flex-end;gap:2px">${msgFormatTime(msg.ts)}${status}</div>`}
        ${reactionsHtml}
      </div>
    </div>`;
  }).join('');

  body.innerHTML = html;
  requestAnimationFrame(() => {
    body.scrollTop = body.scrollHeight;
    // Animate last bubble
    if (animateLast) {
      const bubbles = body.querySelectorAll('[data-msg-bubble]');
      const last = bubbles[bubbles.length - 1];
      if (last) {
        const isMe = last.dataset.msgMe === '1';
        last.classList.add(isMe ? 'msg-anim-out' : 'msg-anim-in');
      }
    }
  });
}

// ── Отправка ──────────────────────────────────────────────────────
function messengerSend() {
  const inp = document.getElementById('mc-input');
  if (!inp || !_msgCurrentChat) return;
  const text = inp.value.trim();
  if (!text) return;
  inp.value = '';
  inp.style.height = '';
  const p = profileLoad();
  if (!p) return;

  const ts = Date.now();
  const replyTo = _mcReplyTo ? { from: _mcReplyTo.from, text: _mcReplyTo.text } : null;
  const msg = { from: p.username, to: _msgCurrentChat, text, ts, delivered: false, read: false, replyTo };
  mcCancelReply();
  const msgs = msgLoad();
  if (!msgs[_msgCurrentChat]) msgs[_msgCurrentChat] = [];
  msgs[_msgCurrentChat].push(msg);
  msgSave(msgs);
  const chats = chatsLoad();
  if (!chats.includes(_msgCurrentChat)) { chats.unshift(_msgCurrentChat); chatsSave(chats); }
  messengerRenderMessages(true); // true = animate last msg

  // Звук отправки
  SFX.play('msgSend');

  // Запустить polling
  sbPollChat(p.username, _msgCurrentChat);

  const chatKey = sbChatKey(p.username, _msgCurrentChat);
  sbInsert('messages', {
    chat_key: chatKey, from_user: p.username,
    to_user: _msgCurrentChat, text, ts,
    extra: replyTo ? JSON.stringify({ replyTo }) : null
  }).then(res => {
    if (res) {
      msg.delivered = true;
      msgSave(msgs);
      // Обновляем только иконку доставки без полного перерендера
      messengerRenderMessages();
    }
  });
}


// ── Reply ─────────────────────────────────────────────────────────
let _mcReplyTo = null;

function mcSetReply(idx) {
  const msgs = msgLoad();
  const chatMsgs = msgs[_msgCurrentChat] || [];
  const msg = chatMsgs[idx];
  if (!msg) return;
  _mcReplyTo = msg;
  const bar  = document.getElementById('mc-reply-bar');
  const name = document.getElementById('mc-reply-name');
  const text = document.getElementById('mc-reply-text');
  if (bar)  bar.style.display = '';
  if (name) name.textContent  = msg.from;
  if (text) text.textContent  = msg.text?.slice(0, 80) || '';
  document.getElementById('mc-input')?.focus();
}

function mcCancelReply() {
  _mcReplyTo = null;
  const bar = document.getElementById('mc-reply-bar');
  if (bar) bar.style.display = 'none';
}

// ── Reactions ─────────────────────────────────────────────────────
// ── Реакции ───────────────────────────────────────────────────────
// Бесплатные (первые 7) + VIP-эксклюзивные (остальные, как в Telegram Premium)
const MC_REACTIONS_FREE = ['❤️','😂','👍','👎','🔥','😮','😢'];
const MC_REACTIONS_VIP  = [
  '🤩','🎉','💯','😈','🤯','🤮','😴','🥰','😤','🫡',
  '👀','💀','🫠','🤡','🦄','💎','⚡','🌊','🍀','🎭',
  '🔮','🌈','☄️','🏆','🫧','🧨','🌸','🐉','🎪','✨'
];
const MC_REACTIONS_ALL  = [...MC_REACTIONS_FREE, ...MC_REACTIONS_VIP];
const MC_FREE_REACTION_LIMIT = 2; // без VIP — макс 2 реакции на одно сообщение

function mcToggleReaction(idx, emoji) {
  const msgs = msgLoad();
  const p = profileLoad();
  const isVip = vipCheck();
  const chatMsgs = msgs[_msgCurrentChat] || [];
  const msg = chatMsgs[idx];
  if (!msg) return;

  // VIP-check: платные эмодзи
  if (MC_REACTIONS_VIP.includes(emoji) && !isVip) {
    toast('🔒 Эта реакция только для VIP');
    return;
  }

  if (!msg.reactions) msg.reactions = {};

  // Проверка лимита реакций (без VIP — не более 2 разных на сообщение)
  const myReactionsOnMsg = Object.entries(msg.reactions)
    .filter(([, users]) => users.includes(p.username))
    .map(([em]) => em);
  const alreadyReacted = myReactionsOnMsg.includes(emoji);

  if (!alreadyReacted && !isVip && myReactionsOnMsg.length >= MC_FREE_REACTION_LIMIT) {
    toast('🔒 Более ' + MC_FREE_REACTION_LIMIT + ' реакций — только VIP');
    return;
  }

  if (!msg.reactions[emoji]) msg.reactions[emoji] = [];
  const users = msg.reactions[emoji];
  const me = users.indexOf(p.username);
  if (me === -1) users.push(p.username);
  else           users.splice(me, 1);
  if (users.length === 0) delete msg.reactions[emoji];
  msgSave(msgs);
  messengerRenderMessages();
}

// ── Bubble interaction ────────────────────────────────────────────
// Свайп (touch + mouse) → меню
// Одиночный клик        → меню  (если не свайп)
// Двойной клик/тап      → ответить
// Долгое нажатие        → меню

const MC_SWIPE_THRESHOLD = 52;  // px для открытия меню
const MC_SWIPE_MAX       = 75;  // px максимальный ход пузыря

let _mcLongPressTimer = null;
let _mcDragStartX     = 0;
let _mcDragStartY     = 0;
let _mcDragging       = false;
let _mcDragTriggered  = false; // свайп уже открыл меню — блокируем click

// ── Touch (мобайл + Android WebView) ──────────────────────────────
function mcBubbleTouchStart(e, row, idx) {
  const t = e.touches[0];
  _mcDragStartX = t.clientX;
  _mcDragStartY = t.clientY;
  _mcDragging = false;
  _mcDragTriggered = false;
  _mcLongPressTimer = setTimeout(() => {
    _mcLongPressTimer = null;
    _mcDragTriggered = true;
    mcShowMsgMenu(idx);
  }, 430);
}

function mcBubbleTouchMove(e, row, idx) {
  const t  = e.touches[0];
  const dx = t.clientX - _mcDragStartX;
  const dy = Math.abs(t.clientY - _mcDragStartY);
  if (dy > 14) { clearTimeout(_mcLongPressTimer); return; } // вертикальный скролл
  if (Math.abs(dx) < 5 && !_mcDragging) return;

  const isMe    = row.dataset.msgMe === '1';
  const validDir = isMe ? dx < 0 : dx > 0;   // свои — влево, чужие — вправо
  if (!validDir && !_mcDragging) return;

  clearTimeout(_mcLongPressTimer);
  _mcDragging = true;
  e.preventDefault(); // подавляем скролл

  const travel = Math.min(Math.abs(dx), MC_SWIPE_MAX);
  _mcApplySwipeVisual(row, isMe, travel);

  if (!_mcDragTriggered && travel >= MC_SWIPE_THRESHOLD) {
    _mcDragTriggered = true;
    try { window.Android?.vibrate?.(22); } catch(_){}
    SFX.play && SFX.play('btnClick');
    mcShowMsgMenu(idx);
  }
}

function mcBubbleTouchEnd(e, row, idx) {
  clearTimeout(_mcLongPressTimer);
  _mcDragging = false;
  _mcReturnBubble(row);
}

// ── Mouse (веб-браузер на ПК) ──────────────────────────────────────
function mcBubbleMouseDown(e, row, idx) {
  if (e.button !== 0) return;
  _mcDragStartX = e.clientX;
  _mcDragStartY = e.clientY;
  _mcDragging = false;
  _mcDragTriggered = false;

  const onMove = (ev) => {
    const dx = ev.clientX - _mcDragStartX;
    const dy = Math.abs(ev.clientY - _mcDragStartY);
    if (dy > 14) return;
    if (Math.abs(dx) < 5 && !_mcDragging) return;
    const isMe    = row.dataset.msgMe === '1';
    const validDir = isMe ? dx < 0 : dx > 0;
    if (!validDir && !_mcDragging) return;
    _mcDragging = true;
    const travel = Math.min(Math.abs(dx), MC_SWIPE_MAX);
    _mcApplySwipeVisual(row, isMe, travel);
    if (!_mcDragTriggered && travel >= MC_SWIPE_THRESHOLD) {
      _mcDragTriggered = true;
      SFX.play && SFX.play('btnClick');
      mcShowMsgMenu(idx);
    }
  };

  const onUp = () => {
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    _mcDragging = false;
    _mcReturnBubble(row);
  };

  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
}

// ── Визуал свайпа ──────────────────────────────────────────────────
function _mcApplySwipeVisual(row, isMe, travel) {
  const bubble = row.querySelector('.mc-bubble-inner');
  const hint   = row.querySelector('.mc-reply-hint');
  if (bubble) {
    bubble.style.transition = 'none';
    bubble.style.transform  = `translateX(${isMe ? -travel : travel}px)`;
  }
  if (hint) hint.style.opacity = String(Math.min(travel / MC_SWIPE_THRESHOLD * 0.9, 0.85));
}

function _mcReturnBubble(row) {
  const bubble = row.querySelector('.mc-bubble-inner');
  const hint   = row.querySelector('.mc-reply-hint');
  if (bubble) { bubble.style.transition = 'transform .2s cubic-bezier(.4,0,.2,1)'; bubble.style.transform = ''; }
  if (hint)   hint.style.opacity = '0';
}

// ── Click / dblclick ──────────────────────────────────────────────
function mcBubbleClick(e, idx) {
  if (_mcDragTriggered) { _mcDragTriggered = false; return; } // уже обработано свайпом
  if (_mcDragging) return;
  mcShowMsgMenu(idx);
}

function mcBubbleDblClick(e, idx) {
  e.preventDefault(); e.stopPropagation();
  _mcDragTriggered = true; // блокируем click после dblclick
  mcCloseMenu();
  const bubble = e.currentTarget.querySelector('.mc-bubble-inner');
  if (bubble) {
    bubble.style.transition = 'transform .1s';
    bubble.style.transform  = 'scale(.94)';
    setTimeout(() => { bubble.style.transform = ''; }, 130);
  }
  try { window.Android?.vibrate?.(22); } catch(_){}
  SFX.play && SFX.play('btnClick');
  mcSetReply(idx);
}

function mcShowBubbleMenu(el, idx) { mcShowMsgMenu(idx); }
function mcShowReactionPicker(idx) { mcShowMsgMenu(idx); }

// ── Telegram-style: единое меню (реакции + действия) ──────────────
// Тап на стрелку → полная сетка эмодзи выезжает снизу вверх
function mcShowMsgMenu(idx) {
  SFX.play && SFX.play('btnClick');
  const existing = document.getElementById('mc-msg-menu');
  if (existing) { existing.remove(); return; }

  const isVip  = vipCheck();
  const msgs   = msgLoad();
  const p      = profileLoad();
  const msg    = (msgs[_msgCurrentChat] || [])[idx];
  if (!msg) return;
  const isMe   = msg.from === p?.username;

  // ── CSS animation keyframes (добавляем один раз) ──
  if (!document.getElementById('mc-menu-style')) {
    const st = document.createElement('style');
    st.id = 'mc-menu-style';
    st.textContent = `
      @keyframes mcSlideUp   { from { transform:translateY(100%); opacity:0 } to { transform:translateY(0); opacity:1 } }
      @keyframes mcSlideDown { from { transform:translateY(0) scale(1); opacity:1 } to { transform:translateY(110%) scale(.97); opacity:0 } }
      @keyframes mcFadeIn    { from { opacity:0 } to { opacity:1 } }
      @keyframes mcFadeOut   { from { opacity:1 } to { opacity:0 } }
      @keyframes mcEmojiIn   { from { transform:translateY(40px) scale(.7); opacity:0 } to { transform:translateY(0) scale(1); opacity:1 } }
      .mc-reaction-btn {
        font-size:30px;background:none;border:none;cursor:pointer;
        padding:5px 3px;line-height:1;transition:transform .12s;
        display:flex;align-items:center;justify-content:center;
      }
      .mc-reaction-btn:active { transform:scale(1.4) !important; }
      .mc-action-btn {
        width:100%;padding:14px 20px;background:none;border:none;
        color:var(--text);font-family:inherit;font-size:15px;
        text-align:left;cursor:pointer;display:flex;align-items:center;gap:16px;
        transition:background .1s;
      }
      .mc-action-btn:active { background:rgba(255,255,255,.07); }
      .mc-action-sep { height:1px;background:rgba(255,255,255,.06);margin:0 16px; }
      #mc-emoji-grid {
        display:grid;
        grid-template-columns:repeat(8,1fr);
        gap:2px;
        max-height:0;overflow:hidden;
        transition:max-height .35s cubic-bezier(.4,0,.2,1), opacity .25s ease;
        opacity:0;
      }
      #mc-emoji-grid.open {
        max-height:400px;opacity:1;
      }
      #mc-emoji-grid .mc-reaction-btn {
        font-size:28px;padding:5px 2px;
        width:100%;aspect-ratio:1;
        display:flex;align-items:center;justify-content:center;
      }
    `;
    document.head.appendChild(st);
  }

  const overlay = document.createElement('div');
  overlay.id = 'mc-msg-menu';
  overlay.style.cssText = 'position:fixed;inset:0;z-index:9100;background:rgba(0,0,0,.45);animation:mcFadeIn .15s ease';

  // Reaction row — 7 бесплатных + кнопка «развернуть»
  const freeButtons = MC_REACTIONS_FREE.map((em, i) =>
    `<button class="mc-reaction-btn"
      style="animation:mcEmojiIn .22s cubic-bezier(.34,1.3,.64,1) ${i*25}ms both"
      onclick="mcToggleReaction(${idx},'${em}');mcCloseMenu()">${em}</button>`
  ).join('');

  // VIP grid (все 30)
  const vipButtons = [...MC_REACTIONS_FREE, ...MC_REACTIONS_VIP].map((em, i) => {
    const locked = MC_REACTIONS_VIP.includes(em) && !isVip;
    return `<button class="mc-reaction-btn"
      style="opacity:${locked?'.35':'1'}"
      onclick="${locked ? "toast('🔒 VIP')" : `mcToggleReaction(${idx},'${em}');mcCloseMenu()`}">${em}</button>`;
  }).join('');

  overlay.innerHTML = `
    <div data-menu-inner style="position:absolute;bottom:0;left:0;right:0;
      animation:mcSlideUp .28s cubic-bezier(.34,1.26,.64,1)"
      onclick="event.stopPropagation()">

      <!-- Reaction strip (pill shape, floating) -->
      <div style="margin:0 12px 8px;background:var(--surface);border-radius:50px;
        padding:6px 8px;display:flex;align-items:center;justify-content:space-between;
        box-shadow:0 4px 24px rgba(0,0,0,.55)">
        <div style="display:flex;flex:1;justify-content:space-around">
          ${freeButtons}
        </div>
        <!-- Expand button -->
        <button id="mc-expand-btn"
          style="width:38px;height:38px;border-radius:50%;background:var(--surface2);
            border:none;color:var(--muted);font-size:16px;cursor:pointer;flex-shrink:0;
            display:flex;align-items:center;justify-content:center;
            transition:transform .25s cubic-bezier(.34,1.3,.64,1)"
          onclick="mcExpandReactions(this)">⌄</button>
      </div>

      <!-- Expanded emoji grid (hidden initially) -->
      <div id="mc-emoji-grid" style="margin:0 12px 6px;background:var(--surface);border-radius:16px;
        padding:6px 6px 4px;box-shadow:0 4px 24px rgba(0,0,0,.45);overflow:hidden">
        ${vipButtons}
        ${!isVip ? '<div style="grid-column:1/-1;font-size:10px;color:var(--muted);text-align:center;padding:4px 0 6px">🔒 Тёмные — <b style=\"color:var(--accent)\">VIP</b></div>' : ''}
      </div>

      <!-- Action sheet -->
      <div style="margin:0 12px calc(14px + var(--safe-bot));background:var(--surface);
        border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.45)">
        <button class="mc-action-btn"
          onclick="mcSetReply(${idx});mcCloseMenu()">
          <span style="font-size:20px">↩</span> Ответить
        </button>
        <div class="mc-action-sep"></div>
        <button class="mc-action-btn"
          onclick="mcForwardMsg(${idx});mcCloseMenu()">
          <span style="font-size:20px">↪</span> Переслать
        </button>
        <div class="mc-action-sep"></div>
        <button class="mc-action-btn"
          onclick="mcCopyMsg(${idx});mcCloseMenu()">
          <span style="font-size:20px">📋</span> Копировать
        </button>
        ${isMe ? `<div class="mc-action-sep"></div>
        <button class="mc-action-btn" style="color:var(--danger,#e05555)"
          onclick="mcDeleteMsg(${idx});mcCloseMenu()">
          <span style="font-size:20px">🗑</span> Удалить
        </button>` : ''}
      </div>
    </div>`;

  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) mcCloseMenu();
  });
  document.body.appendChild(overlay);
}

// ── Закрытие меню с анимацией slide-down ──────────────────────────
function mcCloseMenu() {
  const overlay = document.getElementById('mc-msg-menu');
  if (!overlay) return;
  const inner = overlay.querySelector('[data-menu-inner]');
  if (inner) {
    inner.style.animation = 'mcSlideDown .22s cubic-bezier(.4,0,.8,.6) forwards';
    overlay.style.animation = 'mcFadeOut .22s ease forwards';
  }
  setTimeout(() => overlay.remove(), 200);
}

// Разворачивает/сворачивает сетку эмодзи с анимацией
function mcExpandReactions(btn) {
  const grid = document.getElementById('mc-emoji-grid');
  if (!grid) return;
  const open = grid.classList.toggle('open');
  // Крутим стрелку
  btn.style.transform = open ? 'rotate(180deg)' : 'rotate(0deg)';
  // Staggered появление кнопок при открытии
  if (open) {
    grid.querySelectorAll('.mc-reaction-btn').forEach((b, i) => {
      b.style.animation = `mcEmojiIn .2s cubic-bezier(.34,1.3,.64,1) ${i*12}ms both`;
    });
  }
}

function mcCopyMsg(idx) {
  const msgs = msgLoad();
  const msg  = (msgs[_msgCurrentChat] || [])[idx];
  if (!msg) return;
  navigator.clipboard?.writeText(msg.text || msg.sticker || '').catch(()=>{});
  toast('📋 Скопировано');
}

function mcDeleteMsg(idx) {
  const msgs = msgLoad();
  if (!msgs[_msgCurrentChat]) return;
  msgs[_msgCurrentChat].splice(idx, 1);
  msgSave(msgs);
  messengerRenderMessages();
}

// ── Forward ───────────────────────────────────────────────────────
let _mcForwardText = null;

function mcForwardMsg(idx) {
  const msgs = msgLoad();
  const msg  = (msgs[_msgCurrentChat] || [])[idx];
  if (!msg) return;
  _mcForwardText = msg.text || msg.sticker || '';
  // Show chat picker
  const chats = chatsLoad().filter(u => u !== _msgCurrentChat);
  if (chats.length === 0) { toast('Нет других чатов'); return; }

  const sheet = document.createElement('div');
  sheet.id = 'mc-forward-sheet';
  sheet.style.cssText = 'position:fixed;inset:0;z-index:9200;display:flex;flex-direction:column;justify-content:flex-end;background:rgba(0,0,0,.5);animation:fadeIn .12s ease';
  const p = profileLoad();
  const chatItems = chats.map(u => {
    const peer = _profileOnlinePeers.find(x=>x.username===u) || _allKnownUsers.find(x=>x.username===u);
    return `<button onclick="mcDoForward('${escHtml(u)}');document.getElementById('mc-forward-sheet').remove()"
      style="width:100%;padding:12px 20px;background:none;border:none;color:var(--text);font-family:inherit;font-size:15px;text-align:left;cursor:pointer;display:flex;align-items:center;gap:12px;border-bottom:1px solid rgba(255,255,255,.04)">
      <div style="width:38px;height:38px;border-radius:50%;background:${peer?.color||'var(--surface3)'};display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0">${peer?.avatar||'😊'}</div>
      <span style="font-weight:600">${escHtml(peer?.name||u)}</span>
    </button>`;
  }).join('');
  sheet.innerHTML = `
    <div style="background:var(--surface);border-radius:20px 20px 0 0;padding:8px 0 calc(14px + var(--safe-bot));max-height:70vh;overflow-y:auto" onclick="event.stopPropagation()">
      <div style="width:40px;height:4px;background:var(--surface3);border-radius:2px;margin:8px auto 4px"></div>
      <div style="font-size:14px;font-weight:700;color:var(--muted);padding:8px 20px 10px">Переслать в...</div>
      ${chatItems}
    </div>`;
  sheet.addEventListener('click', () => sheet.remove());
  document.body.appendChild(sheet);
}

function mcDoForward(toUsername) {
  if (!_mcForwardText) return;
  const p = profileLoad();
  if (!p) return;
  const ts = Date.now();
  const msg = { from: p.username, to: toUsername, text: '↪ ' + _mcForwardText, ts, delivered: false, read: false };
  const msgs = msgLoad();
  if (!msgs[toUsername]) msgs[toUsername] = [];
  msgs[toUsername].push(msg);
  msgSave(msgs);
  const chats = chatsLoad();
  if (!chats.includes(toUsername)) { chats.unshift(toUsername); chatsSave(chats); }
  sbInsert('messages', { chat_key: sbChatKey(p.username, toUsername), from_user: p.username, to_user: toUsername, text: msg.text, ts });
  _mcForwardText = null;
  toast('↪️ Переслано');
}

// ── Stickers ──────────────────────────────────────────────────────
const MC_STICKER_PACKS = [
  { id: 'basic', name: '😊 Базовые', vip: false, stickers: [
    '😂','😭','😍','🥹','😎','🤩','😡','😴','🥳','😱',
    '🤡','💀','👻','🎉','🔥','💔','❤️','🤝','🫡','🫠'
  ]},
  { id: 'vip1', name: '✨ Премиум', vip: true, stickers: [
    '🦋','🌊','⚡','🍀','🎭','🧨','💎','🫧','🌈','🎪',
    '🦄','🐉','🌸','🎯','🏆','💫','🌙','☄️','🎆','🎇'
  ]},
  { id: 'vip2', name: '🎨 Арт', vip: true, stickers: [
    '🖼️','🎨','🖌️','✏️','📝','🗿','🏛️','🎭','🎪','🎠',
    '🎡','🎢','🎪','🌅','🌄','🌃','🏙️','🌆','🌇','🌉'
  ]},
];

let _mcStickerPanelOpen = false;

function mcToggleStickerPanel() {
  const panel = document.getElementById('mc-sticker-panel');
  if (!panel) return;
  _mcStickerPanelOpen = !_mcStickerPanelOpen;
  panel.style.display = _mcStickerPanelOpen ? '' : 'none';
  if (_mcStickerPanelOpen) mcRenderStickerPanel();
}

function mcRenderStickerPanel() {
  const panel = document.getElementById('mc-sticker-panel');
  if (!panel) return;
  const isVip = vipCheck();
  let html = '';
  MC_STICKER_PACKS.forEach(pack => {
    const locked = pack.vip && !isVip;
    html += `<div style="margin-bottom:12px">
      <div style="font-size:11px;font-weight:700;color:var(--muted);margin-bottom:6px;display:flex;align-items:center;gap:6px">
        ${pack.name} ${locked ? '<span style="font-size:10px;background:linear-gradient(90deg,#f5c518,#e87722);color:#000;padding:1px 6px;border-radius:6px;font-weight:800">VIP</span>' : ''}
      </div>
      <div style="display:flex;flex-wrap:wrap;gap:4px">
        ${pack.stickers.map(s => locked
          ? `<span style="font-size:36px;opacity:.3;cursor:not-allowed" onclick="toast('🔒 Стикер только для VIP')">${s}</span>`
          : `<span style="font-size:36px;cursor:pointer;transition:transform .1s" ontouchstart="this.style.transform='scale(1.25)'" ontouchend="this.style.transform=''" onclick="mcSendSticker('${s}')">${s}</span>`
        ).join('')}
      </div>
    </div>`;
  });
  panel.innerHTML = html;
}

function mcSendSticker(emoji) {
  const p = profileLoad();
  if (!p || !_msgCurrentChat) return;
  const ts = Date.now();
  const msg = { from: p.username, to: _msgCurrentChat, sticker: emoji, text: '', ts, delivered: false, read: false };
  const msgs = msgLoad();
  if (!msgs[_msgCurrentChat]) msgs[_msgCurrentChat] = [];
  msgs[_msgCurrentChat].push(msg);
  msgSave(msgs);
  messengerRenderMessages(true);
  SFX.play && SFX.play('msgSend');
  sbInsert('messages', { chat_key: sbChatKey(p.username, _msgCurrentChat), from_user: p.username, to_user: _msgCurrentChat, text: emoji, ts });
  // close panel
  _mcStickerPanelOpen = false;
  const panel = document.getElementById('mc-sticker-panel');
  if (panel) panel.style.display = 'none';
}

function mcAutoResize(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 120) + 'px';
}

// ── Telegram-style: весь экран чата двигается вверх при клавиатуре ──
(function() {
  function onViewportResize() {
    const chatScreen = document.getElementById('s-messenger-chat');
    if (!chatScreen || !chatScreen.classList.contains('active')) return;
    const vv = window.visualViewport;
    if (!vv) return;
    const kbOffset = Math.max(0, window.innerHeight - vv.height - vv.offsetTop);
    const t = 'transform 0.18s cubic-bezier(0.4,0,0.2,1)';
    const ty = kbOffset > 10 ? `translateY(-${kbOffset}px)` : '';
    // Двигаем весь экран чата — header + messages + inputbar вместе
    chatScreen.style.transition = t;
    chatScreen.style.transform = ty;
    // Скроллим сообщения вниз
    const list = document.getElementById('mc-messages');
    if (list) setTimeout(() => { list.scrollTop = list.scrollHeight; }, 20);
  }

  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', onViewportResize);
    window.visualViewport.addEventListener('scroll', onViewportResize);
  }
})();

// ── Доп функции ───────────────────────────────────────────────────
function messengerShowMore() {
  const username = _msgCurrentChat;
  if (!username) return;

  // Telegram-style bottom sheet
  const existing = document.getElementById('msg-action-sheet');
  if (existing) existing.remove();

  const sheet = document.createElement('div');
  sheet.id = 'msg-action-sheet';
  sheet.style.cssText = `
    position:fixed;inset:0;z-index:8888;display:flex;flex-direction:column;
    justify-content:flex-end;background:rgba(0,0,0,.5);
    animation:fadeIn .15s ease;
  `;
  sheet.innerHTML = `
    <div style="background:var(--surface);border-radius:20px 20px 0 0;padding:8px 0 calc(16px + var(--safe-bot));overflow:hidden">
      <div style="width:40px;height:4px;border-radius:2px;background:var(--surface3);margin:8px auto 16px"></div>
      <button onclick="peerProfileOpen('${username}');document.getElementById('msg-action-sheet').remove()"
        style="width:100%;padding:16px 20px;background:none;border:none;color:var(--text);font-family:inherit;font-size:15px;text-align:left;cursor:pointer;display:flex;align-items:center;gap:14px">
        <span style="font-size:22px">👤</span> Профиль пользователя
      </button>
      <div style="height:1px;background:rgba(255,255,255,.06);margin:0 20px"></div>
      <button onclick="messengerClearChat('${username}');document.getElementById('msg-action-sheet').remove()"
        style="width:100%;padding:16px 20px;background:none;border:none;color:var(--danger,#c94f4f);font-family:inherit;font-size:15px;text-align:left;cursor:pointer;display:flex;align-items:center;gap:14px">
        <span style="font-size:22px">🗑</span> Удалить чат
      </button>
    </div>
  `;
  sheet.addEventListener('click', (e) => {
    if (e.target === sheet) sheet.remove();
  });
  document.body.appendChild(sheet);
}

function messengerClearChat(username) {
  if (!confirm('Удалить чат с @' + username + '?')) return;
  const msgs = msgLoad();
  delete msgs[username];
  msgSave(msgs);
  const chats = chatsLoad().filter(u => u !== username);
  chatsSave(chats);
  showScreen('s-messenger', 'back');
  messengerRenderList();
  toast('🗑 Чат удалён');
}

function messengerShowInfo() {
  const username = _msgCurrentChat;
  if (username) peerProfileOpen(username);
}

// ── Открытие профиля другого пользователя ─────────────────────────
function peerProfileOpen(username) {
  const peer = _profileOnlinePeers.find(u => u.username === username);
  const titleEl = document.getElementById('peer-profile-title');
  const body = document.getElementById('peer-profile-body');
  if (titleEl) titleEl.textContent = peer?.name || ('@' + username);

  // Update back button to go back to chat
  const backBtn = document.querySelector('#s-peer-profile .hdr-back');
  if (backBtn) {
    backBtn.onclick = () => { SFX.play('screenBack'); showScreen('s-messenger-chat', 'back'); };
  }

  if (!peer) {
    if (body) body.innerHTML = `
      <div style="padding:32px 20px;text-align:center">
        <div style="font-size:56px;margin-bottom:12px">😶</div>
        <div style="font-size:16px;font-weight:700;margin-bottom:8px">@${username}</div>
        <div style="font-size:13px;color:var(--muted)">Пользователь сейчас не в сети</div>
        <button class="btn btn-surface" style="margin-top:20px;max-width:240px" onclick="messengerOpenChatFrom('${username}');showScreen('s-messenger-chat')">💬 Написать</button>
      </div>`;
    showScreen('s-peer-profile');
    return;
  }

  const statusObj = PROFILE_STATUSES?.find(s => s.id === peer.status) || { emoji:'🟢', label:'В сети', color:'#4caf7d' };
  const bannerGrad = `background:linear-gradient(135deg,${peer.color||'var(--accent)'}55,var(--surface2))`;
  const isVip = peer.vip;
  const vipHtml = isVip
    ? `<span class="vip-badge-pill"><span class="vip-crown">👑</span> VIP</span>`
    : '';

  const avatarHtml = (peer.avatarType === 'photo' && peer.avatarData)
    ? `<img src="${peer.avatarData}" onclick="photoZoomOpen('${peer.avatarData}')" style="width:88px;height:88px;object-fit:cover;border-radius:50%;cursor:zoom-in">`
    : `<span style="font-size:42px">${peer.avatar||'😊'}</span>`;

  if (body) body.innerHTML = `
    <div style="position:relative;margin-bottom:0">
      <div style="${bannerGrad};height:110px;width:100%;background-size:cover;background-position:center"></div>
      <div style="position:absolute;bottom:-44px;left:20px">
        <div style="width:88px;height:88px;border-radius:50%;border:3px solid ${peer.color||'var(--accent)'};background:var(--surface2);display:flex;align-items:center;justify-content:center;overflow:hidden">
          ${avatarHtml}
        </div>
      </div>
    </div>
    <div style="height:52px"></div>
    <div style="padding:0 20px 12px">
      <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
        <span style="font-size:22px;font-weight:800">${escHtml(peer.name||username)}</span>
        ${vipHtml}
      </div>
      <div style="font-size:13px;color:var(--muted);margin-top:2px">@${escHtml(username)}</div>
      <div style="display:inline-flex;align-items:center;gap:5px;padding:4px 10px;border-radius:20px;font-size:11px;font-weight:700;margin-top:8px;background:${statusObj.color}22;color:${statusObj.color}">
        ${statusObj.emoji} ${statusObj.label}
      </div>
      ${peer.bio ? `<div style="font-size:13px;color:var(--text);margin-top:10px;line-height:1.5;white-space:pre-wrap">${escHtml(peer.bio)}</div>` : ''}
    </div>
    <div style="padding:0 16px;display:flex;flex-direction:column;gap:8px">
      <button class="btn btn-accent" onclick="messengerOpenChatFrom('${username}');showScreen('s-messenger-chat')">
        💬 Написать сообщение
      </button>
      ${!friendsLoad().includes(username) ? `<button class="btn btn-surface" onclick="profileAddFriend('${username}');this.textContent='✅ Добавлен в друзья';this.disabled=true">👥 Добавить в друзья</button>` : '<div style="text-align:center;color:var(--muted);font-size:13px;padding:8px">👥 Уже в друзьях</div>'}
    </div>
  `;

  showScreen('s-peer-profile');
}

// ── Photo zoom (pinch + double-tap) ───────────────────────────────
(function(){
  function photoZoomOpen(src) {
    const ov = document.getElementById('photo-zoom-overlay');
    const img = document.getElementById('photo-zoom-img');
    if (!ov || !img) return;
    img.src = src;
    ov.classList.add('show');
    _pz.reset();
  }
  window.photoZoomOpen = photoZoomOpen;

  const _pz = {
    scale:1, px:0, py:0,
    startDist:0, startScale:1, startX:0, startY:0,
    lastTap:0,
    reset() { this.scale=1; this.px=0; this.py=0; this.applyTransform(); },
    applyTransform() {
      const img = document.getElementById('photo-zoom-img');
      if (img) img.style.transform = `translate(${this.px}px,${this.py}px) scale(${this.scale})`;
    }
  };

  function dist(t) {
    const dx = t[0].clientX - t[1].clientX;
    const dy = t[0].clientY - t[1].clientY;
    return Math.hypot(dx, dy);
  }

  const ov = document.getElementById('photo-zoom-overlay');
  if (!ov) return;

  // Close on bg tap (but not on img)
  ov.addEventListener('click', e => {
    if (e.target === ov) { ov.classList.remove('show'); _pz.reset(); }
  });

  ov.addEventListener('touchstart', e => {
    if (e.touches.length === 2) {
      e.preventDefault();
      _pz.startDist = dist(e.touches);
      _pz.startScale = _pz.scale;
    } else if (e.touches.length === 1) {
      const now = Date.now();
      if (now - _pz.lastTap < 300) {
        // Double tap — toggle zoom
        _pz.scale = _pz.scale > 1.2 ? 1 : 2.5;
        _pz.px = 0; _pz.py = 0;
        _pz.applyTransform();
      }
      _pz.lastTap = now;
      _pz.startX = e.touches[0].clientX - _pz.px;
      _pz.startY = e.touches[0].clientY - _pz.py;
    }
  }, { passive: false });

  ov.addEventListener('touchmove', e => {
    e.preventDefault();
    if (e.touches.length === 2) {
      const d = dist(e.touches);
      _pz.scale = Math.min(5, Math.max(0.5, _pz.startScale * (d / _pz.startDist)));
      _pz.applyTransform();
    } else if (e.touches.length === 1 && _pz.scale > 1) {
      _pz.px = e.touches[0].clientX - _pz.startX;
      _pz.py = e.touches[0].clientY - _pz.startY;
      _pz.applyTransform();
    }
  }, { passive: false });

  ov.addEventListener('touchend', e => {
    if (_pz.scale < 1.05) { _pz.scale = 1; _pz.px = 0; _pz.py = 0; _pz.applyTransform(); }
  }, { passive: true });
})();



function messengerUpdateBadge() {
  const p = profileLoad();
  const msgs = msgLoad();
  let total = 0;
  Object.values(msgs).forEach(chatMsgs => {
    total += (chatMsgs||[]).filter(m => m.from !== p?.username && !m.read).length;
  });
  const badge = document.getElementById('msg-unread-badge');
  if (badge) { badge.style.display = total > 0 ? '' : 'none'; badge.textContent = total; }
}

function messengerOpenChatFrom(username) {
  // Добавить в список чатов если нет
  const chats = chatsLoad();
  if (!chats.includes(username)) { chats.unshift(username); chatsSave(chats); }
  // Запустить polling
  const p = profileLoad();
  if (p) sbPollChat(p.username, username);
  messengerOpenChat(username);
  showScreen('s-messenger-chat');
}

function msgFormatTime(ts) {
  if (!ts) return '';
  const d = new Date(ts);
  return d.toLocaleTimeString('ru', {hour:'2-digit',minute:'2-digit'});
}

// Патч CMD для /vip
const _origCmdExecForVip2 = window.cmdExec;
if (typeof cmdExec !== 'undefined') {
  const origFn = cmdExec;
  window.cmdExec = function(raw) {
    const parts = raw.trim().split(/\s+/);
    const cmd = parts[0].toLowerCase();
    const arg = parts.slice(1).join(' ').trim();
    if (cmd === '/vip') {
      if (!arg) { cmdPrint('info','👑 /vip <КОД> — активировать VIP'); return; }
      if (vipActivate(arg)) {
        cmdPrint('ok','👑 VIP активирован! Доступны: фото-аватар, рамки, значки, баннеры');
        toast('👑 Добро пожаловать в VIP клуб!');
        setTimeout(profileRenderScreen, 200);
      } else {
        cmdPrint('err','❌ Неверный VIP-код');
      }
      return;
    }
    origFn(raw);
  };
}
