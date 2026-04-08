/**
 * desktop-patch.js — патч для ScheduleApp Desktop (Electron) v3.0
 *
 * Что делает:
 *  1.  VIP с правильным сроком (1 мес / 3 мес / навсегда)
 *  2.  Liquid Glass на всех кнопках
 *  3.  Аватарка на ПК — фото и видео (через Electron picker)
 *  4.  Анимированная аватарка видна всем (sync в Supabase)
 *  5.  Ctrl+Shift+C для CMD-консоли
 *  6.  CMD /vip request + /vip status
 *  7.  [NEW] Fix отправки файлов в чате (Electron dialog)
 *  8.  [NEW] Fix стикеров на ПК (click-обработчики)
 *  9.  [NEW] Telegram-стиль attach-меню
 * 10.  [NEW] Стикер-панель с 3 вкладками: Emoji / Стикеры / GIF
 * 11.  [NEW] Кастомные стикер-паки (создание, загрузка, экспорт/импорт)
 */
(function desktopPatch() {
  'use strict';

  function _log(msg) {
    console.log('[desktop-patch]', msg);
    if (window.Android && typeof window.Android.log === 'function')
      window.Android.log('[desktop-patch] ' + msg);
  }

  function _waitFor(fn, cb, max, interval) {
    max = max || 40; interval = interval || 400;
    var n = 0;
    (function try_() {
      if (fn()) { cb(); return; }
      if (++n < max) setTimeout(try_, interval);
    })();
  }

  // ═══════════════════════════════════════════════════════════════
  // 1. VIP
  // ═══════════════════════════════════════════════════════════════
  var VIP_KEY  = 'sapp_vip_expires_v1';
  var TIER_DAYS = { 20: 30, 30: 90, 100: null };

  function vipExpiresAt() {
    var r = localStorage.getItem(VIP_KEY);
    if (!r) return null;
    var t = Number(r);
    return isNaN(t) ? null : t;
  }
  function vipIsExpired() {
    var e = vipExpiresAt(); if (e === null) return false; return Date.now() > e;
  }
  function vipSetExpiry(days) {
    if (days === null || days === undefined) localStorage.removeItem(VIP_KEY);
    else localStorage.setItem(VIP_KEY, String(Date.now() + days * 86400000));
  }
  function vipDaysLeft() {
    var e = vipExpiresAt(); if (e === null) return null;
    var ms = e - Date.now(); if (ms <= 0) return 0;
    return Math.ceil(ms / 86400000);
  }
  function vipExpiryLabel() {
    var d = vipDaysLeft();
    if (d === null) return 'VIP навсегда';
    if (d <= 0)    return 'VIP истёк';
    if (d === 1)   return 'VIP: 1 день';
    if (d < 30)    return 'VIP: ' + d + ' дн.';
    return 'VIP: ' + Math.floor(d / 30) + ' мес.';
  }

  _waitFor(function(){ return typeof window.vipCheck==='function'; }, function(){
    var _o = window.vipCheck;
    window.vipCheck = function() {
      if (!_o.apply(this, arguments)) return false;
      if (vipIsExpired()) {
        localStorage.removeItem('sapp_vip_v1');
        var p = typeof window.profileLoad==='function' ? window.profileLoad() : null;
        if (p && p.vip) { p.vip = false; typeof window.profileSave==='function' && window.profileSave(p); }
        return false;
      }
      return true;
    };
    _log('vipCheck patched');
  });

  _waitFor(function(){ return typeof window.donateConfirm==='function'; }, function(){
    var _o = window.donateConfirm;
    window.donateConfirm = async function() {
      var idx = window._selectedDoneTierIdx||0;
      var tier= (window.DONATE_TIERS||[])[idx]||{};
      var days= TIER_DAYS[tier.amount]!==undefined ? TIER_DAYS[tier.amount] : null;
      var r   = await _o.apply(this, arguments);
      localStorage.setItem('sapp_vip_pending_days', days===null?'forever':String(days));
      return r;
    };
    _patchVipActivate();
    _log('donateConfirm patched');
  });

  function _patchVipActivate() {
    if (typeof window.vipActivate!=='function') return;
    var _o = window.vipActivate;
    window.vipActivate = function(c) {
      var r = _o.apply(this, arguments);
      if (r) { vipSetExpiry(null); setTimeout(_showVipBadge, 500); }
      return r;
    };
  }

  _waitFor(function(){ return typeof window.vipSyncFromServer==='function'; }, function(){
    var _o = window.vipSyncFromServer;
    window.vipSyncFromServer = async function(u) {
      await _o.apply(this, arguments);
      try {
        if (typeof window.sbGet!=='function' || !window.sbReady?.()) return;
        var rows = await window.sbGet('users','select=vip_expires_at&username=eq.'+encodeURIComponent(u)+'&limit=1');
        if (!Array.isArray(rows)||!rows.length) return;
        var row = rows[0];
        if (row.vip_expires_at) { var e=new Date(row.vip_expires_at).getTime(); if(!isNaN(e)) localStorage.setItem(VIP_KEY,String(e)); }
        else if ('vip_expires_at' in row && row.vip_expires_at===null) localStorage.removeItem(VIP_KEY);
        _showVipBadge();
      } catch(_){}
    };
    _log('vipSyncFromServer patched');
  });

  function _showVipBadge() {
    if (typeof window.vipCheck!=='function'||!window.vipCheck()) return;
    requestAnimationFrame(function(){
      document.querySelectorAll('.vip-badge-pill').forEach(function(p){
        if (p.dataset.expiryShown) return;
        p.dataset.expiryShown='1'; p.title=vipExpiryLabel();
        var s=document.createElement('span');
        s.style.cssText='font-size:9px;opacity:.72;margin-left:4px;font-weight:500';
        s.textContent='('+vipExpiryLabel().replace('VIP ','').replace('VIP: ','')+')';
        p.appendChild(s);
      });
    });
  }
  new MutationObserver(_showVipBadge).observe(document.documentElement,{childList:true,subtree:true});


  // ═══════════════════════════════════════════════════════════════
  // 2. LIQUID GLASS
  // ═══════════════════════════════════════════════════════════════
  (function(){
    var css=`
body.glass-mode .btn,body.glass-mode .btn-primary,body.glass-mode .btn-accent,
body.glass-mode .btn-surface,body.glass-mode .btn-surface2,body.glass-mode .btn-surface3,
body.glass-mode .diff-btn,body.glass-mode .egg-card,body.glass-mode .dpad-btn,
body.glass-mode .nav-tab,body.glass-mode .tab-btn,body.glass-mode .action-btn,
body.glass-mode .chat-send-btn,body.glass-mode .msg-btn,body.glass-mode .profile-btn,
body.glass-mode .vip-badge-pill,body.glass-mode .pill-btn {
  position:relative;background:rgba(255,255,255,.11)!important;
  backdrop-filter:blur(20px) saturate(1.7)!important;
  -webkit-backdrop-filter:blur(20px) saturate(1.7)!important;
  border:1px solid rgba(255,255,255,.22)!important;
  box-shadow:inset 0 1.5px 0 rgba(255,255,255,.52),0 4px 20px rgba(0,0,0,.18)!important;
  overflow:hidden;transform:translateZ(0);
  transition:transform .22s cubic-bezier(.34,1.26,.64,1),background .2s ease!important;
}
body.glass-mode .btn:active,body.glass-mode .diff-btn:active,body.glass-mode .dpad-btn:active {
  transform:scale(0.96) translateZ(0)!important;background:rgba(255,255,255,.18)!important;
}
body.glass-mode.glass-optimized .btn,body.glass-mode.glass-optimized .diff-btn {
  backdrop-filter:none!important;background:rgba(255,255,255,.10)!important;
}`;
    function inj(){ var el=document.createElement('style');el.id='desktop-glass-buttons';el.textContent=css;document.head.appendChild(el); }
    if(document.head) inj(); else document.addEventListener('DOMContentLoaded',inj);
    _log('Glass CSS injected');
  })();


  // ═══════════════════════════════════════════════════════════════
  // 3 & 4. АВАТАРКА
  // ═══════════════════════════════════════════════════════════════
  window._onVideoAvatarPicked = function(dataUrl) {
    if (!dataUrl) return;
    var p = typeof window.profileLoad==='function'?window.profileLoad():null;
    if (!p) return;
    p.avatarType='video'; p.avatarVideo=dataUrl; p.avatarData=null;
    typeof window.profileSave==='function'&&window.profileSave(p);
    typeof window.updateNavProfileIcon==='function'&&window.updateNavProfileIcon(p);
    typeof window.profileRenderScreen==='function'&&window.profileRenderScreen();
    _syncVideoAvatarToSupabase(p, dataUrl);
    typeof window.sbPresencePut==='function'&&window.sbPresencePut(p);
    typeof window.toast==='function'&&window.toast('✅ Видео-аватар установлен');
  };
  window._onVideoAvatarError = function(m){ typeof window.toast==='function'&&window.toast(m||'❌ Ошибка видео'); };

  _waitFor(function(){
    return typeof window._profilePickPhotoOnly==='function'&&typeof window._profilePickVideoAvatar==='function';
  }, function(){
    var _oP = window._profilePickPhotoOnly;
    window._profilePickPhotoOnly = function(){
      if (window.Android&&typeof window.Android.pickImageForAvatar==='function'){
        // Новый отдельный метод — результат придёт в onNativeAvatarPhotoPicked,
        // а не в onNativeBgImagePicked. Никакого конфликта с фоном.
        window.Android.pickImageForAvatar();
      } else if (window.Android&&typeof window.Android.pickImageForBackground==='function'){
        // Фолбэк для старых сборок: перехватываем onNativeBgImagePicked разово
        var _origBgPicked = window.onNativeBgImagePicked;
        window.onNativeBgImagePicked = function(dataUrl) {
          window.onNativeBgImagePicked = _origBgPicked;
          _onAvatarPhotoReady(dataUrl);
        };
        window.Android.pickImageForBackground();
      } else { _oP.apply(this,arguments); }
    };

    // Колбэк для нового pickImageForAvatar
    window.onNativeAvatarPhotoPicked = function(dataUrl) {
      _onAvatarPhotoReady(dataUrl);
    };

    function _onAvatarPhotoReady(dataUrl) {
      if (!dataUrl) return;
      var p = typeof window.profileLoad==='function' ? window.profileLoad() : null;
      if (!p) return;
      p.avatarType='photo'; p.avatarData=dataUrl; p.avatarVideo=null;
      typeof window.profileSave==='function' && window.profileSave(p);
      typeof window.updateNavProfileIcon==='function' && window.updateNavProfileIcon(p);
      typeof window.profileRenderScreen==='function' && window.profileRenderScreen();
      typeof window.sbPresencePut==='function' && window.sbPresencePut(p);
      typeof window.toast==='function' && window.toast('\u2705 \u0410\u0432\u0430\u0442\u0430\u0440 \u0443\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d');
      _log('Avatar photo set from Android picker');
    }
    var _oV = window._profilePickVideoAvatar;
    window._profilePickVideoAvatar = function(){
      if (typeof window.vipCheck==='function'&&!window.vipCheck()){
        typeof window.toast==='function'&&window.toast('👑 Видео-аватар — только для VIP'); return;
      }
      if (window.Android&&typeof window.Android.pickVideoForAvatar==='function') window.Android.pickVideoForAvatar();
      else _oV.apply(this,arguments);
    };
    _log('Avatar pickers patched');
  });

  async function _syncVideoAvatarToSupabase(profile, videoDataUrl) {
    if (!profile||!videoDataUrl||typeof window.sbReady!=='function'||!window.sbReady()) return;
    if (!window.Android?.nativeUploadFileAsync||typeof window._sbFetch!=='function') return;
    try {
      typeof window.toast==='function'&&window.toast('⬆️ Загружаю видео на сервер...');
      var cbId='vidAvatar_'+Date.now();
      var p2 = new Promise(function(res,rej){
        var pD=window.onUploadDone,pE=window.onUploadError;
        window.onUploadDone=(id,url)=>{ if(id!==cbId){pD?.(id,url);return;} window.onUploadDone=pD; res(url); };
        window.onUploadError=(id,e)=>{ if(id!==cbId){pE?.(id,e);return;} window.onUploadError=pE; rej(new Error(e)); };
      });
      var b64=videoDataUrl.split(',')[1], mime=(videoDataUrl.match(/^data:([^;]+)/)||[])[1]||'video/mp4';
      window.Android.nativeUploadFileAsync(b64,'avatar_'+profile.username+'_'+Date.now()+'.'+mime.split('/')[1],mime,cbId);
      var url = await Promise.race([p2, new Promise((_,r)=>setTimeout(()=>r(new Error('timeout')),30000))]);
      if (url) {
        await window._sbFetch('PATCH','/rest/v1/users?username=eq.'+encodeURIComponent(profile.username),
          {avatar_type:'video',avatar_video_url:url},{'Content-Type':'application/json','Prefer':'return=minimal'});
        profile.avatarVideoUrl=url; typeof window.profileSave==='function'&&window.profileSave(profile);
        typeof window.toast==='function'&&window.toast('✅ Видео-аватар виден всем!');
      }
    } catch(e){ _log('Video sync failed: '+e.message); typeof window.toast==='function'&&window.toast('⚠️ Видео локально'); }
  }

  _waitFor(function(){ return typeof window.sbPresencePut==='function'; }, function(){
    var _o=window.sbPresencePut;
    window.sbPresencePut=async function(p){
      if (p&&p.avatarType==='video'&&!p.__videoProcessed) {
        try {
          if (p.avatarVideoUrl) return _o.call(this,Object.assign({},p,{__videoProcessed:true}));
          if (p.avatarVideo) {
            var th=await _extractVideoThumb(p.avatarVideo);
            if (th) return _o.call(this,Object.assign({},p,{__videoProcessed:true,avatarType:'photo',avatarData:th}));
          }
        } catch(_){}
      }
      return _o.apply(this,arguments);
    };
    _log('sbPresencePut patched');
  });

  function _extractVideoThumb(src) {
    return new Promise(function(resolve){
      var v=document.createElement('video'); v.muted=true; v.crossOrigin='anonymous';
      var t=setTimeout(()=>resolve(null),4000);
      v.onloadeddata=()=>{ clearTimeout(t); try{ v.currentTime=0; setTimeout(function(){
        var c=document.createElement('canvas'); c.width=c.height=80;
        c.getContext('2d').drawImage(v,0,0,80,80); resolve(c.toDataURL('image/jpeg',.75));
      },50); }catch(_){resolve(null);} };
      v.onerror=()=>{ clearTimeout(t); resolve(null); };
      v.src=src;
    });
  }


  // ═══════════════════════════════════════════════════════════════
  // 5. CTRL+SHIFT+C
  // ═══════════════════════════════════════════════════════════════
  document.addEventListener('keydown', function(e){
    if ((e.ctrlKey||e.metaKey)&&e.shiftKey&&e.key==='C'){
      e.preventDefault(); typeof window.cmdOpen==='function'&&window.cmdOpen();
    }
  });


  // ═══════════════════════════════════════════════════════════════
  // 6. CMD /vip
  // ═══════════════════════════════════════════════════════════════
  _waitFor(function(){ return typeof window.cmdExec==='function'&&typeof window.cmdPrint==='function'; }, function(){
    var _o=window.cmdExec;
    window.cmdExec=function(raw){
      var parts=raw.trim().split(/\s+/), cmd=(parts[0]||'').toLowerCase().replace(/^\//,'');
      if (cmd==='vip'){
        var sub=(parts[1]||'').toLowerCase();
        if (sub==='request'){_handleVipRequest(parts.slice(2).join(' '));return;}
        if (sub==='status'||sub==='expiry'){
          var p=typeof window.profileLoad==='function'?window.profileLoad():null;
          if (!p||!p.vip){window.cmdPrint('err','❌ VIP не активен');return;}
          window.cmdPrint('ok','👑 '+vipExpiryLabel()); return;
        }
      }
      var r=_o.apply(this,arguments);
      if (cmd==='help') setTimeout(function(){
        window.cmdPrint('out','  vip request [комм]  — заявка на VIP');
        window.cmdPrint('out','  vip status          — срок VIP');
      },10);
      return r;
    };
    _log('/vip cmd patched');
  });

  async function _handleVipRequest(comment) {
    var p=typeof window.profileLoad==='function'?window.profileLoad():null;
    if (!p?.username){window.cmdPrint('err','❌ Войди');return;}
    if (!window.sbReady?.()){ window.cmdPrint('err','❌ Нет сети');return;}
    window.cmdPrint('info','📤 Отправляю заявку...');
    try {
      var payload={username:p.username,amount:0,txn_id:'CMD_'+Date.now(),ts:Date.now(),status:'pending',tier:'👑 VIP (CMD)',vip_tier:true,comment:comment||'Запрос с ПК'};
      var res=await window._sbFetch('POST','/rest/v1/donations',payload,{'Content-Type':'application/json','Prefer':'return=minimal'});
      if (res.ok||res.status===201){window.cmdPrint('ok','✅ Заявка отправлена!'); typeof window._vipBotNotify==='function'&&window._vipBotNotify(p,0,'👑 VIP (CMD)',payload.txn_id);}
      else window.cmdPrint('err','❌ Ошибка: '+(res.status||'?'));
    } catch(e){window.cmdPrint('err','❌ '+e.message);}
  }


  // ═══════════════════════════════════════════════════════════════
  // 7. FIX ОТПРАВКИ ФАЙЛОВ В ЧАТЕ
  // На PC используем Electron dialog через Android bridge
  // ═══════════════════════════════════════════════════════════════

  // Ожидает callback _onChatFilePicked / _onChatFileCancelled от preload
  function _waitForChatFile(cbId, triggerFn) {
    return new Promise(function(resolve){
      var timer=setTimeout(function(){ cleanUp(); resolve(null); }, 120000);
      var prevP=window._onChatFilePicked, prevC=window._onChatFileCancelled;
      function cleanUp(){ clearTimeout(timer); }
      function onPicked(id,f){ if(id!==cbId){prevP?.(id,f);return;} cleanUp(); window._onChatFilePicked=prevP; window._onChatFileCancelled=prevC; resolve(f); }
      function onCancel(id){  if(id!==cbId){prevC?.(id);  return;} cleanUp(); window._onChatFilePicked=prevP; window._onChatFileCancelled=prevC; resolve(null); }
      window._onChatFilePicked=onPicked; window._onChatFileCancelled=onCancel;
      triggerFn();
    });
  }

  // Загрузка через nativeUploadFileAsync
  function _uploadBase64(base64, fileName, mimeType) {
    return new Promise(function(resolve, reject){
      if (!window.Android?.nativeUploadFileAsync){ reject(new Error('No upload bridge')); return; }
      var cbId='up_'+Date.now();
      var timer=setTimeout(function(){ reject(new Error('Upload timeout')); },120000);
      var pD=window.onUploadDone, pE=window.onUploadError;
      window.onUploadDone=function(id,url){  if(id!==cbId){pD?.(id,url);return;} clearTimeout(timer); window.onUploadDone=pD; window.onUploadError=pE; resolve(url); };
      window.onUploadError=function(id,err){ if(id!==cbId){pE?.(id,err);return;} clearTimeout(timer); window.onUploadDone=pD; window.onUploadError=pE; reject(new Error(err)); };
      window.Android.nativeUploadFileAsync(base64, fileName, mimeType, cbId);
    });
  }

  _waitFor(function(){
    return typeof window.mcPickImage==='function'&&typeof window.mcPickVideo==='function'
        && typeof window.mcPickFile==='function'&&typeof window.mcPickAudio==='function';
  }, function(){

    // ── mcPickImage ──
    window.mcPickImage = async function(){
      if (!window.Android?.pickImageForChat){ typeof window._origMcPickImage==='function'&&window._origMcPickImage(); return; }
      var cbId='ci_'+Date.now();
      var f=await _waitForChatFile(cbId,function(){ window.Android.pickImageForChat(cbId); });
      if (!f) return;
      if (f.size>20*1024*1024){ typeof window.toast==='function'&&window.toast('❌ Фото > 20 МБ'); return; }
      typeof window._mcInChatSendingShow==='function'&&window._mcInChatSendingShow('image',f.name);
      try {
        // Компрессия через canvas
        var b64c=await _compressImageB64(f.base64,f.mime,900);
        var url=await _uploadBase64(b64c,f.name,'image/jpeg');
        typeof window._mcSendMediaMsg==='function'&&window._mcSendMediaMsg({url,fileName:f.name,fileType:'image',fileSize:f.size});
        typeof window._mcInChatSendingHide==='function'&&window._mcInChatSendingHide();
      } catch(e){ typeof window._mcInChatSendingHide==='function'&&window._mcInChatSendingHide(); typeof window.toast==='function'&&window.toast('❌ '+e.message); }
    };

    // ── mcPickVideo ──
    window.mcPickVideo = async function(){
      if (!window.Android?.pickVideoForChat){ typeof window._origMcPickVideo==='function'&&window._origMcPickVideo(); return; }
      var cbId='cv_'+Date.now();
      var f=await _waitForChatFile(cbId,function(){ window.Android.pickVideoForChat(cbId); });
      if (!f) return;
      if (f.size>200*1024*1024){ typeof window.toast==='function'&&window.toast('❌ Видео > 200 МБ'); return; }
      typeof window._mcInChatSendingShow==='function'&&window._mcInChatSendingShow('video',f.name);
      try {
        var url=await _uploadBase64(f.base64,f.name,f.mime);
        typeof window._mcSendMediaMsg==='function'&&window._mcSendMediaMsg({url,fileName:f.name,fileType:'video',fileSize:f.size});
        typeof window._mcInChatSendingHide==='function'&&window._mcInChatSendingHide();
      } catch(e){ typeof window._mcInChatSendingHide==='function'&&window._mcInChatSendingHide(); typeof window.toast==='function'&&window.toast('❌ '+e.message); }
    };

    // ── mcPickAudio ──
    window.mcPickAudio = async function(){
      if (!window.Android?.pickAudioForChat){ typeof window._origMcPickAudio==='function'&&window._origMcPickAudio(); return; }
      var cbId='ca_'+Date.now();
      var f=await _waitForChatFile(cbId,function(){ window.Android.pickAudioForChat(cbId); });
      if (!f) return;
      typeof window._mcInChatSendingShow==='function'&&window._mcInChatSendingShow('voice',f.name);
      try {
        var url=await _uploadBase64(f.base64,f.name,f.mime);
        typeof window._mcSendMediaMsg==='function'&&window._mcSendMediaMsg({url,fileName:f.name,fileType:'voice',fileSize:f.size});
        typeof window._mcInChatSendingHide==='function'&&window._mcInChatSendingHide();
      } catch(e){ typeof window._mcInChatSendingHide==='function'&&window._mcInChatSendingHide(); typeof window.toast==='function'&&window.toast('❌ '+e.message); }
    };

    // ── mcPickFile ──
    window.mcPickFile = async function(){
      if (!window.Android?.pickAnyFileForChat){ typeof window._origMcPickFile==='function'&&window._origMcPickFile(); return; }
      var cbId='cf_'+Date.now();
      var f=await _waitForChatFile(cbId,function(){ window.Android.pickAnyFileForChat(cbId); });
      if (!f) return;
      if (f.size>200*1024*1024){ typeof window.toast==='function'&&window.toast('❌ Файл > 200 МБ'); return; }
      var ext=(f.name.split('.').pop()||'').toLowerCase();
      var isAud=['mp3','ogg','wav','flac','aac','m4a','opus','wma'].includes(ext);
      typeof window._mcInChatSendingShow==='function'&&window._mcInChatSendingShow(isAud?'voice':'file',f.name);
      try {
        var url=await _uploadBase64(f.base64,f.name,f.mime);
        typeof window._mcSendMediaMsg==='function'&&window._mcSendMediaMsg({url,fileName:f.name,fileType:isAud?'voice':'file',fileSize:f.size});
        typeof window._mcInChatSendingHide==='function'&&window._mcInChatSendingHide();
      } catch(e){ typeof window._mcInChatSendingHide==='function'&&window._mcInChatSendingHide(); typeof window.toast==='function'&&window.toast('❌ '+e.message); }
    };

    _log('mcPick* patched (Electron dialog)');
  });

  // Canvas-компрессия изображения из base64
  function _compressImageB64(base64, mime, maxDim) {
    return new Promise(function(resolve, reject){
      var img=new Image();
      img.onload=function(){
        var w=img.width,h=img.height;
        if (w>maxDim||h>maxDim){ var r=maxDim/Math.max(w,h); w=Math.round(w*r); h=Math.round(h*r); }
        var c=document.createElement('canvas'); c.width=w; c.height=h;
        c.getContext('2d').drawImage(img,0,0,w,h);
        resolve(c.toDataURL('image/jpeg',.88).split(',')[1]);
      };
      img.onerror=reject;
      img.src='data:'+mime+';base64,'+base64;
    });
  }


  // ═══════════════════════════════════════════════════════════════
  // 8. FIX СТИКЕРОВ (click вместо только touchend)
  // Mouse-эмулятор в preload.js при простом клике шлёт touchcancel.
  // Добавляем обычный click-обработчик к каждой ячейке emoji.
  // ═══════════════════════════════════════════════════════════════
  _waitFor(function(){ return typeof window.mcRenderStickerPanel==='function'; }, function(){
    var _o=window.mcRenderStickerPanel;
    window.mcRenderStickerPanel=function(){
      // Вызываем оригинал только для emoji-вкладки
      if ((window._stickerPanelTab||'emoji')==='emoji') _o.apply(this,arguments);
      // Фиксируем click после рендера
      setTimeout(_fixEmojiClicks, 80);
      setTimeout(_fixEmojiClicks, 300); // на случай lazy load
    };
    // Наблюдаем за ленивой загрузкой новых ячеек
    var obs=new MutationObserver(_fixEmojiClicks);
    function hookPanel(){
      var p=document.getElementById('mc-sticker-panel');
      if (p) obs.observe(p,{childList:true,subtree:true});
    }
    if (document.readyState!=='loading') hookPanel();
    else document.addEventListener('DOMContentLoaded',hookPanel);
    _log('mcRenderStickerPanel patched (click fix)');
  });

  function _fixEmojiClicks() {
    var panel=document.getElementById('mc-sticker-panel');
    if (!panel) return;
    panel.querySelectorAll('span').forEach(function(sp){
      if (sp.dataset.clickFixed) return;
      sp.dataset.clickFixed='1';
      // Определяем emoji: из img.alt, textContent или data-атрибута
      var emoji = sp.dataset.emoji
        || (sp.querySelector('img')&&(sp.querySelector('img').alt||sp.querySelector('img').dataset.emoji))
        || sp.textContent.trim();
      if (!emoji) return;
      sp.style.cursor='pointer';
      sp.addEventListener('click',function(e){
        e.preventDefault(); e.stopPropagation();
        typeof window.mcSendStickerOrEmoji==='function'&&window.mcSendStickerOrEmoji(emoji);
      });
    });
  }


  // ═══════════════════════════════════════════════════════════════
  // 9. TELEGRAM-СТИЛЬ ATTACH-МЕНЮ
  // ═══════════════════════════════════════════════════════════════
  var _recentMedia = []; // [{type,url,thumb,name}]

  _waitFor(function(){ return typeof window._mcSendMediaMsg==='function'; }, function(){
    var _o=window._mcSendMediaMsg;
    window._mcSendMediaMsg=function(opts){
      var r=_o.apply(this,arguments);
      if (opts&&(opts.fileType==='image'||opts.fileType==='video')){
        _recentMedia.unshift({type:opts.fileType,url:opts.url,thumb:opts.thumbData||null,name:opts.fileName||''});
        if (_recentMedia.length>12) _recentMedia.pop();
      }
      return r;
    };
  });

  _waitFor(function(){ return typeof window.mcPickMedia==='function'; }, function(){
    window.mcPickMedia=function(){
      var ex=document.getElementById('mc-media-sheet');
      if (ex){ex.remove();return;}

      var sheet=document.createElement('div');
      sheet.id='mc-media-sheet';
      sheet.style.cssText='position:fixed;inset:0;z-index:9100;display:flex;flex-direction:column;justify-content:flex-end;background:rgba(0,0,0,.45);animation:mcFadeIn .15s ease';

      var items=[
        {bg:'#2196f3',lbl:'Галерея', svg:'<path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"/>',fn:function(){sheet.remove();window.mcPickImage();}},
        {bg:'#e91e63',lbl:'Видео',   svg:'<path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"/>',fn:function(){sheet.remove();window.mcPickVideo();}},
        {bg:'#9c27b0',lbl:'Файл',    svg:'<path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/>',fn:function(){sheet.remove();window.mcPickFile();}},
        {bg:'#ff9800',lbl:'Аудио',   svg:'<path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/>',fn:function(){sheet.remove();window.mcPickAudio();}},
        {bg:'#4caf50',lbl:'Кружок',  svg:'<circle cx="12" cy="12" r="9" stroke="white" stroke-width="2" fill="none"/><circle cx="12" cy="9" r="3" fill="white"/><path d="M6 20c0-3.31 2.69-6 6-6s6 2.69 6 6" fill="white"/>',fn:function(){sheet.remove();typeof window.mcStartCircleRecord==='function'&&window.mcStartCircleRecord();}},
        {bg:'#607d8b',lbl:'Контакт', svg:'<path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm-8 2.75c1.24 0 2.25 1.01 2.25 2.25S13.24 11.25 12 11.25 9.75 10.24 9.75 9 10.76 6.75 12 6.75zM20 18H4v-.75c0-2.66 5.33-4 8-4s8 1.34 8 4V18z"/>',fn:function(){typeof window.toast==='function'&&window.toast('🚧 Скоро');}},
      ];

      // Превью последних медиа
      var recentHtml='';
      if (_recentMedia.length>0){
        var thumbs=_recentMedia.slice(0,6).map(function(m){
          var bg=m.thumb?'url('+m.thumb+') center/cover':(m.type==='video'?'#1a1a2e':'#0d0d1a');
          var icon=m.type==='video'?'<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center"><svg width="20" height="20" viewBox="0 0 24 24" fill="white" opacity=".9"><polygon points="5,3 19,12 5,21"/></svg></div>':'';
          return '<div style="width:68px;height:68px;border-radius:10px;background:'+bg+';position:relative;overflow:hidden;flex-shrink:0;cursor:pointer;border:1.5px solid rgba(255,255,255,.1)" onclick="window.open(\''+m.url+'\',\'_blank\')" title="'+m.name+'">'+icon+'</div>';
        }).join('');
        recentHtml='<div style="padding:10px 14px 8px;display:flex;gap:8px;overflow-x:auto;scrollbar-width:none">'+thumbs+'</div><div style="height:1px;background:rgba(255,255,255,.07);margin:0 14px"></div>';
      }

      var iconsHtml=items.map(function(it,i){
        return '<div class="_ai" data-i="'+i+'" style="display:flex;flex-direction:column;align-items:center;gap:6px;cursor:pointer;-webkit-tap-highlight-color:transparent">'
          +'<div class="attach-ico" style="width:54px;height:54px;border-radius:50%;background:'+it.bg+';display:flex;align-items:center;justify-content:center;box-shadow:0 3px 10px '+it.bg+'55;transition:transform .12s cubic-bezier(.34,1.3,.64,1)">'
          +'<svg width="24" height="24" viewBox="0 0 24 24" fill="white">'+it.svg+'</svg></div>'
          +'<span style="font-size:11px;color:var(--text);font-weight:500">'+it.lbl+'</span></div>';
      }).join('');

      sheet.innerHTML='<div style="background:var(--surface);border-radius:20px 20px 0 0;padding:0 16px calc(20px + var(--safe-bot));animation:mcSlideUp .26s cubic-bezier(.34,1.1,.64,1)" onclick="event.stopPropagation()">'
        +'<div style="width:40px;height:4px;background:var(--surface3);border-radius:2px;margin:12px auto 12px"></div>'
        +recentHtml
        +'<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:16px 8px;padding:14px 0 4px">'+iconsHtml+'</div>'
        +'</div>';

      sheet.querySelectorAll('._ai').forEach(function(el){
        var i=parseInt(el.dataset.i);
        el.addEventListener('click',function(e){
          e.stopPropagation();
          var ico=el.querySelector('.attach-ico');
          if(ico) ico.style.transform='scale(.88)';
          setTimeout(function(){ if(ico) ico.style.transform=''; items[i].fn(); },120);
        });
      });
      sheet.addEventListener('click',function(e){ if(e.target===sheet) sheet.remove(); });
      document.body.appendChild(sheet);
      _log('Attach sheet opened');
    };
    _log('mcPickMedia patched (Telegram-style)');
  });


  // ═══════════════════════════════════════════════════════════════
  // 10. СТИКЕР-ПАНЕЛЬ С 3 ВКЛАДКАМИ
  // ═══════════════════════════════════════════════════════════════
  window._stickerPanelTab='emoji';

  _waitFor(function(){ return typeof window.mcToggleStickerPanel==='function'; }, function(){
    var _o=window.mcToggleStickerPanel;
    window.mcToggleStickerPanel=function(){
      _o.apply(this,arguments);
      if (window._mcStickerPanelOpen) requestAnimationFrame(_renderStickerTabs);
    };
    _log('mcToggleStickerPanel patched (tabs)');
  });

  function _ts(active){
    return 'flex:1;background:none;border:none;color:'+(active?'var(--accent,#e87722)':'var(--muted)')
      +';font-size:13px;font-weight:'+(active?700:500)+';padding:9px 4px 7px;cursor:pointer;'
      +'border-bottom:2px solid '+(active?'var(--accent,#e87722)':'transparent')+';'
      +'transition:color .15s,border-color .15s;-webkit-tap-highlight-color:transparent';
  }

  function _renderStickerTabs(){
    var panel=document.getElementById('mc-sticker-panel');
    if (!panel||panel.querySelector('#_sticker-tabs')) return;
    var tabs=document.createElement('div');
    tabs.id='_sticker-tabs';
    tabs.style.cssText='display:flex;border-bottom:1px solid rgba(255,255,255,.08);position:sticky;top:0;background:var(--surface,#1c1c1e);z-index:2;flex-shrink:0';
    tabs.innerHTML='<button id="_stab-emoji" style="'+_ts(true)+'">😊 Emoji</button>'
      +'<button id="_stab-sticker" style="'+_ts(false)+'">🎨 Стикеры</button>'
      +'<button id="_stab-gif" style="'+_ts(false)+'">GIF</button>';
    tabs.querySelector('#_stab-emoji').addEventListener('click',function(){ window._switchStickerTab('emoji'); });
    tabs.querySelector('#_stab-sticker').addEventListener('click',function(){ window._switchStickerTab('sticker'); });
    tabs.querySelector('#_stab-gif').addEventListener('click',function(){ window._switchStickerTab('gif'); });
    panel.prepend(tabs);
    setTimeout(_fixEmojiClicks,100);
  }

  window._switchStickerTab=function(tab){
    window._stickerPanelTab=tab;
    ['emoji','sticker','gif'].forEach(function(t){
      var b=document.getElementById('_stab-'+t);
      if(b) b.style.cssText=_ts(t===tab);
    });
    var panel=document.getElementById('mc-sticker-panel');
    if (!panel) return;
    Array.from(panel.children).forEach(function(ch){ if(ch.id!=='_sticker-tabs') ch.remove(); });
    if (tab==='emoji'){
      typeof window.mcRenderStickerPanel==='function'&&window.mcRenderStickerPanel();
      setTimeout(_fixEmojiClicks,100);
    } else if (tab==='sticker'){
      _renderStickerPacksTab(panel);
    } else {
      _renderGifTab(panel);
    }
  };

  function _renderGifTab(panel){
    var d=document.createElement('div');
    d.style.cssText='display:flex;flex-direction:column;align-items:center;justify-content:center;padding:32px 16px;gap:10px;opacity:.75';
    d.innerHTML='<div style="font-size:44px">🎬</div>'
      +'<div style="font-size:15px;font-weight:700;color:var(--text)">GIF-стикеры</div>'
      +'<div style="font-size:12px;color:var(--muted);text-align:center;line-height:1.5">Скоро появится поиск и отправка GIF прямо из чата</div>'
      +'<div style="background:var(--accent,#e87722);color:#fff;border-radius:20px;padding:5px 14px;font-size:12px;font-weight:700;margin-top:4px">Скоро</div>';
    panel.appendChild(d);
  }


  // ═══════════════════════════════════════════════════════════════
  // 11. КАСТОМНЫЕ СТИКЕР-ПАКИ
  // ═══════════════════════════════════════════════════════════════
  var _PACKS_KEY='sapp_custom_packs_v1';
  function _loadPacks(){ try{return JSON.parse(localStorage.getItem(_PACKS_KEY)||'[]');}catch(_){return [];} }
  function _savePacks(p){ try{localStorage.setItem(_PACKS_KEY,JSON.stringify(p));}catch(_){} }
  function _genId(){ return Math.random().toString(36).slice(2,10)+Date.now().toString(36); }

  function _renderStickerPacksTab(panel){
    var packs=_loadPacks();
    var wrap=document.createElement('div');
    wrap.id='_custom-sticker-wrap';
    wrap.style.cssText='padding:6px 0;display:flex;flex-direction:column;flex:1;min-height:0';

    if (!packs.length){
      wrap.innerHTML='<div style="display:flex;flex-direction:column;align-items:center;padding:24px 16px;gap:8px">'
        +'<div style="font-size:42px">📦</div>'
        +'<div style="font-size:14px;font-weight:600;color:var(--text)">Паков нет</div>'
        +'<div style="font-size:12px;color:var(--muted);text-align:center">Создай свой пак или импортируй код от друга</div></div>';
    } else {
      var nav='<div style="display:flex;gap:6px;padding:6px 10px 8px;overflow-x:auto;scrollbar-width:none">';
      packs.forEach(function(pk,i){
        nav+='<button id="_pnav-'+pk.id+'" data-pid="'+pk.id+'"'
          +' style="flex-shrink:0;background:'+(i===0?'var(--accent,#e87722)':'var(--surface2)')+';border:none;border-radius:18px;'
          +'padding:5px 12px;font-size:13px;cursor:pointer;color:'+(i===0?'#fff':'var(--text)')+';'
          +'font-weight:600;transition:background .15s;white-space:nowrap">'+pk.icon+' '+pk.name+'</button>';
      });
      nav+='</div><div id="_pack-content"></div>';
      wrap.innerHTML=nav;
      // Вешаем клики на навигацию
      wrap.querySelectorAll('[id^="_pnav-"]').forEach(function(btn){
        btn.addEventListener('click',function(){ _showPackContent(btn.dataset.pid); });
      });
    }

    var btns=document.createElement('div');
    btns.style.cssText='display:flex;gap:8px;padding:8px 10px 4px;border-top:1px solid rgba(255,255,255,.07);flex-shrink:0';
    btns.innerHTML='<button id="_btn-create-pack" style="flex:1;background:var(--accent,#e87722);border:none;border-radius:12px;padding:9px;color:#fff;font-size:12px;font-weight:700;cursor:pointer">＋ Создать пак</button>'
      +'<button id="_btn-import-pack" style="flex:1;background:var(--surface2);border:none;border-radius:12px;padding:9px;color:var(--text);font-size:12px;font-weight:600;cursor:pointer">📥 Импорт</button>';
    wrap.appendChild(btns);
    panel.appendChild(wrap);

    wrap.querySelector('#_btn-create-pack').addEventListener('click',_openCreatePackDialog);
    wrap.querySelector('#_btn-import-pack').addEventListener('click',_openImportPackDialog);

    if (packs.length) _showPackContent(packs[0].id);
  }

  function _showPackContent(packId){
    var packs=_loadPacks(), pack=packs.find(function(p){return p.id===packId;});
    var cont=document.getElementById('_pack-content');
    if (!cont||!pack) return;
    // Подсвечиваем активный таб
    document.querySelectorAll('[id^="_pnav-"]').forEach(function(b){
      b.style.background= b.id==='_pnav-'+packId ? 'var(--accent,#e87722)':'var(--surface2)';
      b.style.color=      b.id==='_pnav-'+packId ? '#fff':'var(--text)';
    });
    if (!pack.stickers||!pack.stickers.length){
      cont.innerHTML='<div style="display:flex;flex-direction:column;align-items:center;padding:16px;gap:8px">'
        +'<div style="font-size:12px;color:var(--muted)">Пак пустой — добавь стикеры!</div>'
        +'<button id="_btn-add-sticker" style="background:var(--surface2);border:none;border-radius:10px;padding:7px 14px;color:var(--text);font-size:12px;cursor:pointer">＋ Добавить стикер</button></div>';
      cont.querySelector('#_btn-add-sticker').addEventListener('click',function(){ _addStickerToPack(packId); });
      return;
    }
    var html='<div style="display:flex;flex-wrap:wrap;padding:6px 6px;gap:4px">';
    pack.stickers.forEach(function(s){
      html+='<div data-sid="'+s.id+'" data-pid="'+packId+'" style="width:68px;height:68px;border-radius:10px;overflow:hidden;cursor:pointer;background:rgba(255,255,255,.04);flex-shrink:0;border:1.5px solid rgba(255,255,255,.07);transition:transform .1s" title="'+(s.name||'')+'">'
        +'<img src="'+s.dataUrl+'" style="width:100%;height:100%;object-fit:contain" loading="lazy"></div>';
    });
    html+='</div><div style="display:flex;gap:6px;padding:4px 10px 8px">'
      +'<button id="_btn-add-stk" style="flex:1;background:var(--surface2);border:none;border-radius:10px;padding:7px;color:var(--text);font-size:11px;cursor:pointer">＋ Стикер</button>'
      +'<button id="_btn-exp-pack" style="flex:1;background:var(--surface2);border:none;border-radius:10px;padding:7px;color:var(--text);font-size:11px;cursor:pointer">📤 Экспорт</button>'
      +'<button id="_btn-del-pack" style="background:rgba(255,80,80,.15);border:none;border-radius:10px;padding:7px 10px;color:#ff5050;font-size:11px;cursor:pointer">🗑</button>'
      +'</div>';
    cont.innerHTML=html;
    // Клики по стикерам
    cont.querySelectorAll('[data-sid]').forEach(function(el){
      el.addEventListener('mouseover',function(){ el.style.transform='scale(1.08)'; });
      el.addEventListener('mouseout', function(){ el.style.transform=''; });
      el.addEventListener('click',function(){ _sendCustomSticker(el.dataset.sid,el.dataset.pid); });
    });
    cont.querySelector('#_btn-add-stk').addEventListener('click',function(){ _addStickerToPack(packId); });
    cont.querySelector('#_btn-exp-pack').addEventListener('click',function(){ _exportPack(packId); });
    cont.querySelector('#_btn-del-pack').addEventListener('click',function(){ _deletePackConfirm(packId); });
  }

  function _sendCustomSticker(stickerId, packId){
    var packs=_loadPacks(), pack=packs.find(function(p){return p.id===packId;});
    var sticker=pack&&pack.stickers&&pack.stickers.find(function(s){return s.id===stickerId;});
    if (!sticker) return;
    var p=typeof window.profileLoad==='function'?window.profileLoad():null;
    if (!p||!window._msgCurrentChat){ typeof window.toast==='function'&&window.toast('❌ Открой чат'); return; }
    var ts=Date.now();
    var msg={from:p.username,to:window._msgCurrentChat,image:sticker.dataUrl,fileType:'sticker',ts,delivered:false,read:false};
    var msgs=typeof window.msgLoad==='function'?window.msgLoad():{};
    if (!msgs[window._msgCurrentChat]) msgs[window._msgCurrentChat]=[];
    msgs[window._msgCurrentChat].push(msg);
    typeof window.msgSave==='function'&&window.msgSave(msgs);
    typeof window.messengerRenderMessages==='function'&&window.messengerRenderMessages(true);
    if (window._mcStickerPanelOpen) typeof window.mcToggleStickerPanel==='function'&&window.mcToggleStickerPanel();
  }

  function _addStickerToPack(packId){
    if (!window.Android?.pickStickerImage){ typeof window.toast==='function'&&window.toast('❌ Picker недоступен'); return; }
    var cbId='si_'+Date.now();
    var timer=setTimeout(function(){ cleanUp(); },120000);
    function cleanUp(){ clearTimeout(timer); delete window._onStickerImagePicked; delete window._onStickerImageCancelled; }
    window._onStickerImagePicked=function(id,dataUrl,name){
      if(id!==cbId) return; cleanUp();
      var packs=_loadPacks(), pack=packs.find(function(p){return p.id===packId;});
      if(!pack) return;
      pack.stickers=pack.stickers||[];
      pack.stickers.push({id:_genId(),dataUrl,name:name||'Стикер'});
      _savePacks(packs);
      typeof window.toast==='function'&&window.toast('✅ Стикер добавлен!');
      _showPackContent(packId);
    };
    window._onStickerImageCancelled=function(id){ if(id!==cbId) return; cleanUp(); };
    window.Android.pickStickerImage(cbId);
  }

  function _openCreatePackDialog(){
    var ov=document.createElement('div');
    ov.style.cssText='position:fixed;inset:0;z-index:9999;background:rgba(0,0,0,.6);display:flex;align-items:center;justify-content:center;padding:20px';
    ov.innerHTML='<div style="background:var(--surface);border-radius:20px;padding:24px;width:100%;max-width:320px;animation:mcSlideUp .22s ease">'
      +'<div style="font-size:18px;font-weight:700;margin-bottom:16px">📦 Новый пак стикеров</div>'
      +'<div style="margin-bottom:12px"><div style="font-size:12px;color:var(--muted);margin-bottom:6px">Иконка</div>'
      +'<input id="_pk-ico" maxlength="4" value="🎨" style="width:100%;background:var(--surface2);border:none;border-radius:12px;padding:10px;color:var(--text);font-size:20px;text-align:center;box-sizing:border-box"></div>'
      +'<div style="margin-bottom:18px"><div style="font-size:12px;color:var(--muted);margin-bottom:6px">Название</div>'
      +'<input id="_pk-nm" placeholder="Мой пак" maxlength="30" style="width:100%;background:var(--surface2);border:none;border-radius:12px;padding:10px 14px;color:var(--text);font-size:14px;box-sizing:border-box"></div>'
      +'<div style="display:flex;gap:10px">'
      +'<button id="_pk-cancel" style="flex:1;padding:12px;background:var(--surface2);border:none;border-radius:14px;color:var(--text);font-size:14px;cursor:pointer">Отмена</button>'
      +'<button id="_pk-ok" style="flex:1;padding:12px;background:var(--accent,#e87722);border:none;border-radius:14px;color:#fff;font-size:14px;font-weight:700;cursor:pointer">Создать</button>'
      +'</div></div>';
    document.body.appendChild(ov);
    ov.querySelector('#_pk-cancel').addEventListener('click',function(){ov.remove();});
    ov.querySelector('#_pk-ok').addEventListener('click',function(){
      var icon=ov.querySelector('#_pk-ico').value.trim()||'📦';
      var name=ov.querySelector('#_pk-nm').value.trim();
      if(!name){typeof window.toast==='function'&&window.toast('❌ Введи название');return;}
      var packs=_loadPacks(); packs.push({id:_genId(),name,icon,stickers:[]}); _savePacks(packs);
      ov.remove(); typeof window.toast==='function'&&window.toast('✅ Пак создан!');
      var panel=document.getElementById('mc-sticker-panel');
      if (panel){ var w=document.getElementById('_custom-sticker-wrap'); if(w)w.remove(); _renderStickerPacksTab(panel); }
    });
  }

  function _exportPack(packId){
    var packs=_loadPacks(), pack=packs.find(function(p){return p.id===packId;});
    if (!pack) return;
    var b64=btoa(unescape(encodeURIComponent(JSON.stringify({_type:'sapp_sticker_pack',version:1,pack}))));
    var ov=document.createElement('div');
    ov.style.cssText='position:fixed;inset:0;z-index:9999;background:rgba(0,0,0,.6);display:flex;align-items:center;justify-content:center;padding:20px';
    ov.innerHTML='<div style="background:var(--surface);border-radius:20px;padding:24px;width:100%;max-width:340px">'
      +'<div style="font-size:16px;font-weight:700;margin-bottom:4px">📤 '+pack.icon+' '+pack.name+'</div>'
      +'<div style="font-size:12px;color:var(--muted);margin-bottom:10px">'+((pack.stickers||[]).length)+' стикеров · Скопируй код и отправь другу</div>'
      +'<textarea id="_exp-code" readonly style="width:100%;height:80px;background:var(--surface2);border:1px solid rgba(255,255,255,.1);border-radius:12px;padding:10px;color:var(--text);font-size:10px;font-family:monospace;resize:none;box-sizing:border-box;word-break:break-all">'+b64+'</textarea>'
      +'<div style="display:flex;gap:10px;margin-top:12px">'
      +'<button id="_exp-copy" style="flex:1;padding:11px;background:var(--accent,#e87722);border:none;border-radius:12px;color:#fff;font-size:13px;font-weight:700;cursor:pointer">📋 Копировать</button>'
      +'<button id="_exp-close" style="flex:1;padding:11px;background:var(--surface2);border:none;border-radius:12px;color:var(--text);font-size:13px;cursor:pointer">Закрыть</button>'
      +'</div></div>';
    document.body.appendChild(ov);
    ov.querySelector('#_exp-copy').addEventListener('click',function(){
      navigator.clipboard?.writeText(b64).then(function(){typeof window.toast==='function'&&window.toast('✅ Скопировано!');});
    });
    ov.querySelector('#_exp-close').addEventListener('click',function(){ov.remove();});
    ov.querySelector('#_exp-code').addEventListener('click',function(){this.select();});
  }

  function _openImportPackDialog(){
    var ov=document.createElement('div');
    ov.style.cssText='position:fixed;inset:0;z-index:9999;background:rgba(0,0,0,.6);display:flex;align-items:center;justify-content:center;padding:20px';
    ov.innerHTML='<div style="background:var(--surface);border-radius:20px;padding:24px;width:100%;max-width:340px;animation:mcSlideUp .22s ease">'
      +'<div style="font-size:18px;font-weight:700;margin-bottom:6px">📥 Импорт пака</div>'
      +'<div style="font-size:12px;color:var(--muted);margin-bottom:12px">Вставь код пака который тебе прислали</div>'
      +'<textarea id="_imp-code" placeholder="Вставь код здесь..." style="width:100%;height:80px;background:var(--surface2);border:1px solid rgba(255,255,255,.1);border-radius:12px;padding:10px;color:var(--text);font-size:11px;font-family:monospace;resize:none;box-sizing:border-box"></textarea>'
      +'<div style="display:flex;gap:10px;margin-top:12px">'
      +'<button id="_imp-cancel" style="flex:1;padding:12px;background:var(--surface2);border:none;border-radius:14px;color:var(--text);font-size:14px;cursor:pointer">Отмена</button>'
      +'<button id="_imp-ok" style="flex:1;padding:12px;background:var(--accent,#e87722);border:none;border-radius:14px;color:#fff;font-size:14px;font-weight:700;cursor:pointer">Импорт</button>'
      +'</div></div>';
    document.body.appendChild(ov);
    ov.querySelector('#_imp-cancel').addEventListener('click',function(){ov.remove();});
    ov.querySelector('#_imp-ok').addEventListener('click',function(){
      var code=ov.querySelector('#_imp-code').value.trim();
      if(!code) return;
      try {
        var obj=JSON.parse(decodeURIComponent(escape(atob(code))));
        if(obj._type!=='sapp_sticker_pack'||!obj.pack) throw new Error('Неверный формат');
        var packs=_loadPacks();
        if(packs.find(function(p){return p.id===obj.pack.id;})){
          typeof window.toast==='function'&&window.toast('ℹ️ Уже добавлен'); ov.remove(); return;
        }
        packs.push(obj.pack); _savePacks(packs);
        ov.remove();
        typeof window.toast==='function'&&window.toast('✅ Импортирован: '+obj.pack.name+' ('+((obj.pack.stickers||[]).length)+' стикеров)');
        var panel=document.getElementById('mc-sticker-panel');
        if(panel){ var w=document.getElementById('_custom-sticker-wrap'); if(w)w.remove(); _renderStickerPacksTab(panel); }
      } catch(e){ typeof window.toast==='function'&&window.toast('❌ '+e.message); }
    });
  }

  function _deletePackConfirm(packId){
    var packs=_loadPacks(), pack=packs.find(function(p){return p.id===packId;});
    if (!pack) return;
    var ov=document.createElement('div');
    ov.style.cssText='position:fixed;inset:0;z-index:9999;background:rgba(0,0,0,.6);display:flex;align-items:center;justify-content:center;padding:20px';
    ov.innerHTML='<div style="background:var(--surface);border-radius:20px;padding:24px;width:100%;max-width:300px">'
      +'<div style="font-size:28px;text-align:center;margin-bottom:10px">🗑</div>'
      +'<div style="font-size:16px;font-weight:700;text-align:center;margin-bottom:6px">Удалить пак?</div>'
      +'<div style="font-size:13px;color:var(--muted);text-align:center;margin-bottom:18px">'+pack.icon+' '+pack.name+' · '+((pack.stickers||[]).length)+' стикеров</div>'
      +'<div style="display:flex;gap:10px">'
      +'<button id="_dp-cancel" style="flex:1;padding:11px;background:var(--surface2);border:none;border-radius:12px;color:var(--text);font-size:14px;cursor:pointer">Отмена</button>'
      +'<button id="_dp-ok" style="flex:1;padding:11px;background:#ff5050;border:none;border-radius:12px;color:#fff;font-size:14px;font-weight:700;cursor:pointer">Удалить</button>'
      +'</div></div>';
    document.body.appendChild(ov);
    ov.querySelector('#_dp-cancel').addEventListener('click',function(){ov.remove();});
    ov.querySelector('#_dp-ok').addEventListener('click',function(){
      _savePacks(_loadPacks().filter(function(p){return p.id!==packId;}));
      ov.remove(); typeof window.toast==='function'&&window.toast('🗑 Пак удалён');
      var panel=document.getElementById('mc-sticker-panel');
      if(panel){ var w=document.getElementById('_custom-sticker-wrap'); if(w)w.remove(); _renderStickerPacksTab(panel); }
    });
  }


  // ═══════════════════════════════════════════════════════════════
  // vipGrantTo — пишем vip_expires_at в Supabase
  // ═══════════════════════════════════════════════════════════════
  _waitFor(function(){ return typeof window.vipGrantTo==='function'; }, function(){
    var _o=window.vipGrantTo;
    window.vipGrantTo=async function(username,daysOverride){
      await _o.apply(this,arguments);
      var days=daysOverride!==undefined?daysOverride:null;
      if (days===undefined||days===null){
        try {
          if(typeof window.sbGet==='function'&&window.sbReady?.()) {
            var rows=await window.sbGet('donations','select=amount&username=eq.'+encodeURIComponent(username)+'&status=eq.pending&order=ts.desc&limit=1');
            if(Array.isArray(rows)&&rows.length) days=TIER_DAYS[rows[0].amount];
          }
        }catch(_){}
      }
      try {
        if(typeof window._sbFetch==='function'){
          var expVal=(days===null||days===undefined)?null:new Date(Date.now()+days*86400000).toISOString();
          await window._sbFetch('PATCH','/rest/v1/users?username=eq.'+encodeURIComponent(username),
            {vip_expires_at:expVal},{'Content-Type':'application/json','Prefer':'return=minimal'});
          _log('vipGrantTo: set vip_expires_at='+expVal+' for '+username);
        }
      }catch(e){_log('vipGrantTo expiry write failed: '+e.message);}
    };
    window._TIER_DAYS_PATCH=TIER_DAYS;
    _log('vipGrantTo patched');
  });


  // ═══════════════════════════════════════════════════════════════
  // 12. FIX КНОПОК ПРИКРЕПЛЕНИЯ И СТИКЕРОВ НА ПК
  // preload.js шлёт touchcancel для простых кликов, поэтому кнопки
  // с ontouchend не срабатывают. Вешаем click-обработчики напрямую.
  // ═══════════════════════════════════════════════════════════════
  function _fixChatActionButtons() {
    // Кнопка "прикрепить файл" (attach)
    var attachBtn = document.getElementById('mc-attach-btn');
    if (attachBtn && !attachBtn.dataset.pcFixed) {
      attachBtn.dataset.pcFixed = '1';
      attachBtn.addEventListener('click', function(e) {
        e.preventDefault(); e.stopImmediatePropagation();
        typeof window.mcPickMedia === 'function' && window.mcPickMedia();
      });
      _log('mc-attach-btn click fixed');
    }

    // Кнопка "стикеры/emoji"
    var stickerBtn = document.getElementById('mc-sticker-btn');
    if (stickerBtn && !stickerBtn.dataset.pcFixed) {
      stickerBtn.dataset.pcFixed = '1';
      stickerBtn.addEventListener('click', function(e) {
        e.preventDefault(); e.stopImmediatePropagation();
        typeof window.mcToggleStickerPanel === 'function' && window.mcToggleStickerPanel();
      });
      _log('mc-sticker-btn click fixed');
    }

    // Кнопка "отправить изображение" внутри чата (если есть отдельная)
    var sendImgBtn = document.getElementById('mc-send-img-btn');
    if (sendImgBtn && !sendImgBtn.dataset.pcFixed) {
      sendImgBtn.dataset.pcFixed = '1';
      sendImgBtn.addEventListener('click', function(e) {
        e.preventDefault(); e.stopImmediatePropagation();
        typeof window.mcPickImage === 'function' && window.mcPickImage();
      });
    }

    // Кнопка "отправить видео" внутри чата (если есть отдельная)
    var sendVidBtn = document.getElementById('mc-send-vid-btn');
    if (sendVidBtn && !sendVidBtn.dataset.pcFixed) {
      sendVidBtn.dataset.pcFixed = '1';
      sendVidBtn.addEventListener('click', function(e) {
        e.preventDefault(); e.stopImmediatePropagation();
        typeof window.mcPickVideo === 'function' && window.mcPickVideo();
      });
    }
  }

  // Запускаем сразу и следим за DOM (кнопки могут появиться позже)
  _waitFor(
    function() { return !!document.getElementById('mc-attach-btn') || !!document.getElementById('mc-sticker-btn'); },
    _fixChatActionButtons, 60, 300
  );
  new MutationObserver(function(muts) {
    muts.forEach(function(m) {
      m.addedNodes.forEach(function(n) {
        if (n.nodeType === 1) _fixChatActionButtons();
      });
    });
  }).observe(document.body || document.documentElement, { childList: true, subtree: true });


  // ═══════════════════════════════════════════════════════════════
  // 13. ПОДДЕРЖКА GIF-АВАТАРОК — синхронизация через Supabase
  // GIF хранится локально как dataUrl, но другие видят его
  // через avatar_data в таблице users (base64 в поле text)
  // ═══════════════════════════════════════════════════════════════
  _waitFor(function() { return typeof window.profileSave === 'function'; }, function() {
    var _oSave = window.profileSave;
    window.profileSave = function(p) {
      var r = _oSave.apply(this, arguments);
      // Если аватарка GIF — синхронизируем в Supabase avatar_data
      if (p && p.avatarType === 'photo' && p.avatarData && p.avatarData.startsWith('data:image/gif')) {
        _syncGifAvatarToSupabase(p);
      }
      return r;
    };
    _log('profileSave patched for GIF sync');
  });

  function _syncGifAvatarToSupabase(profile) {
    if (!profile || !profile.username || !profile.avatarData) return;
    if (typeof window.sbReady !== 'function' || !window.sbReady()) return;
    if (typeof window._sbFetch !== 'function') return;
    // Не синхронизируем если GIF больше 2 МБ (Supabase text лимит)
    var sizeEst = Math.round(profile.avatarData.length * 0.75);
    if (sizeEst > 2 * 1024 * 1024) {
      _log('GIF аватарка слишком большая для прямой синхронизации (' + Math.round(sizeEst/1024) + ' кБ)');
      typeof window.toast === 'function' && window.toast('⚠️ GIF > 2 МБ — другие не увидят анимацию. Загрузи поменьше.');
      return;
    }
    window._sbFetch('PATCH', '/rest/v1/users?username=eq.' + encodeURIComponent(profile.username),
      { avatar_type: 'photo', avatar_data: profile.avatarData },
      { 'Content-Type': 'application/json', 'Prefer': 'return=minimal' }
    ).then(function() {
      _log('GIF avatar synced to Supabase for ' + profile.username);
    }).catch(function(e) {
      _log('GIF sync failed: ' + (e.message || e));
    });
  }


  _log('v3.3 — все патчи применены (fix: pickImageForAvatar отдельный метод + колбэк)');
})();
