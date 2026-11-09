'use strict';
/*
 * WAEX Web: the page served by the phone. Everything shown comes from /api on the same address.
 * Text from chats is always put on the page as text (textContent), never as HTML.
 */
(() => {
  const $ = (id) => document.getElementById(id);
  const el = (tag, cls, text) => {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  };
  const sleep = (ms) => new Promise((done) => setTimeout(done, ms));
  const enc = encodeURIComponent;

  // ─── Languages ───────────────────────────────────────────────────────
  const WORDS = {
    en: {
      intro: 'Read and write your WhatsApp chats from this computer. Your phone shows a code to let this browser in.',
      askCode: 'Send a code to my phone', codeLabel: 'Code from the notification on your phone',
      passwordLabel: 'Password', confirm: 'Sign in', newCode: 'Ask for a new code',
      loginNote: 'Works only on the same Wi-Fi as the phone. The connection is not encrypted (plain HTTP), so use it only on a network you trust.',
      wrong_code: 'That code is not right.', wrong_password: 'That password is not right.',
      expired: 'The code expired. Ask for a new one.', locked: 'Too many wrong tries. Try again in {s} s.',
      wait: 'A code was already sent. You can ask again in {s} s.', failed: 'Something went wrong. Try again.',
      offline: 'The phone does not answer. Is WhatsApp running and on the same Wi-Fi?',
      search: 'Search chats and messages', all: 'All', unread: 'Unread', groups: 'Groups', archived: 'Archived',
      noChats: 'No chats here.', messagesSection: 'Messages', chatsSection: 'Chats',
      emptyText: 'Pick a chat on the left.', group: 'Group', you: 'You',
      typeHere: 'Type a message', older: 'Load older messages', today: 'Today', yesterday: 'Yesterday',
      deleted: 'This message was deleted', viewOnce: 'View once message (open it on the phone)',
      contact: 'Contact card', location: 'Location', liveLocation: 'Live location', poll: 'Poll',
      photo: 'Photo', video: 'Video', audio: 'Audio', voice: 'Voice message', document: 'Document',
      sticker: 'Sticker', gif: 'GIF', unsupported: 'This kind of message can only be seen on the phone',
      notOnPhone: 'Not downloaded on the phone', save: 'Download',
      noSend: 'Sending from the browser does not work with this WhatsApp version.',
      openOnPhone: 'Open on the phone instead', openedOnPhone: 'Opened on the phone. Confirm sending there.',
      not_a_phone_chat: 'This chat can only be opened on the phone by hand.',
      queued: 'Sent to WhatsApp. It will show up here in a moment.', slow_down: 'Too many messages at once. Wait a moment.',
      settings: 'Settings', look: 'Look', theme: 'Theme', system: 'Same as system', light: 'Light', dark: 'Dark',
      black: 'Black', accent: 'Colour', wallpaper: 'Chat background', wallClassic: 'Classic', wallDots: 'Dots',
      wallGradient: 'Gradient', wallPlain: 'Plain', fontSize: 'Text size', corners: 'Bubble corners',
      enterSends: 'Enter sends the message', language: 'Language',
      security: 'Security', passwordOff: 'No password. Anyone who can read the code on your phone can sign in.',
      passwordOn: 'A password is needed together with the code.', currentPassword: 'Current password',
      newPassword: 'New password (at least 6 characters)', setPassword: 'Save password',
      removePassword: 'Remove password', saved: 'Saved.', password_length: 'Use 6 to 128 characters.',
      browsers: 'Signed-in browsers', thisBrowser: 'this browser', lastSeen: 'last used', signOutOne: 'Sign out',
      signOutAll: 'Sign out everywhere', signOut: 'Sign out of this browser',
      tokens: 'API tokens', tokensInfo: 'For your own scripts. A token can read chats and send messages, so keep it secret.',
      tokenName: 'Name, for example "home script"', createToken: 'Create token',
      tokenOnce: 'Copy it now. It will not be shown again:', remove: 'Delete', apiDocs: 'API description',
      too_many_tokens: 'You already have 10 tokens. Delete one first.', copied: 'Copied.'
    },
    sk: {
      intro: 'Čítaj a píš svoje WhatsApp správy z tohto počítača. Telefón ukáže kód, ktorým tento prehliadač pustíš dnu.',
      askCode: 'Poslať kód do telefónu', codeLabel: 'Kód z notifikácie v telefóne',
      passwordLabel: 'Heslo', confirm: 'Prihlásiť', newCode: 'Vyžiadať nový kód',
      loginNote: 'Funguje len na rovnakej Wi-Fi ako telefón. Spojenie nie je šifrované (obyčajné HTTP), používaj ho len v sieti, ktorej veríš.',
      wrong_code: 'Kód nie je správny.', wrong_password: 'Heslo nie je správne.',
      expired: 'Kód vypršal. Vyžiadaj si nový.', locked: 'Priveľa zlých pokusov. Skús to o {s} s.',
      wait: 'Kód už bol poslaný. Nový si môžeš vyžiadať o {s} s.', failed: 'Niečo sa nepodarilo. Skús to znova.',
      offline: 'Telefón neodpovedá. Beží WhatsApp a je na rovnakej Wi-Fi?',
      search: 'Hľadať v chatoch a správach', all: 'Všetky', unread: 'Neprečítané', groups: 'Skupiny', archived: 'Archív',
      noChats: 'Žiadne chaty.', messagesSection: 'Správy', chatsSection: 'Chaty',
      emptyText: 'Vyber si chat vľavo.', group: 'Skupina', you: 'Ty',
      typeHere: 'Napíš správu', older: 'Načítať staršie správy', today: 'Dnes', yesterday: 'Včera',
      deleted: 'Táto správa bola vymazaná', viewOnce: 'Správa na jedno zobrazenie (otvor ju v telefóne)',
      contact: 'Kontakt', location: 'Poloha', liveLocation: 'Poloha naživo', poll: 'Anketa',
      photo: 'Fotka', video: 'Video', audio: 'Zvuk', voice: 'Hlasová správa', document: 'Dokument',
      sticker: 'Nálepka', gif: 'GIF', unsupported: 'Tento druh správy sa dá pozrieť len v telefóne',
      notOnPhone: 'V telefóne nie je stiahnuté', save: 'Stiahnuť',
      noSend: 'Posielanie z prehliadača s touto verziou WhatsAppu nefunguje.',
      openOnPhone: 'Otvoriť radšej v telefóne', openedOnPhone: 'Otvorené v telefóne. Odoslanie potvrď tam.',
      not_a_phone_chat: 'Tento chat sa dá v telefóne otvoriť len ručne.',
      queued: 'Odovzdané WhatsAppu. O chvíľu sa tu správa objaví.', slow_down: 'Priveľa správ naraz. Chvíľu počkaj.',
      settings: 'Nastavenia', look: 'Vzhľad', theme: 'Téma', system: 'Podľa systému', light: 'Svetlá', dark: 'Tmavá',
      black: 'Čierna', accent: 'Farba', wallpaper: 'Pozadie chatu', wallClassic: 'Klasické', wallDots: 'Bodky',
      wallGradient: 'Prechod', wallPlain: 'Čisté', fontSize: 'Veľkosť písma', corners: 'Rohy bublín',
      enterSends: 'Enter odošle správu', language: 'Jazyk',
      security: 'Zabezpečenie', passwordOff: 'Bez hesla. Prihlási sa každý, kto si v telefóne prečíta kód.',
      passwordOn: 'Ku kódu treba aj heslo.', currentPassword: 'Terajšie heslo',
      newPassword: 'Nové heslo (aspoň 6 znakov)', setPassword: 'Uložiť heslo',
      removePassword: 'Zrušiť heslo', saved: 'Uložené.', password_length: 'Použi 6 až 128 znakov.',
      browsers: 'Prihlásené prehliadače', thisBrowser: 'tento prehliadač', lastSeen: 'naposledy', signOutOne: 'Odhlásiť',
      signOutAll: 'Odhlásiť všade', signOut: 'Odhlásiť tento prehliadač',
      tokens: 'API tokeny', tokensInfo: 'Pre tvoje vlastné skripty. Token vie čítať chaty a posielať správy, drž ho v tajnosti.',
      tokenName: 'Názov, napríklad „domáci skript“', createToken: 'Vytvoriť token',
      tokenOnce: 'Skopíruj si ho teraz. Znova sa už neukáže:', remove: 'Vymazať', apiDocs: 'Popis API',
      too_many_tokens: 'Máš už 10 tokenov. Najprv jeden vymaž.', copied: 'Skopírované.'
    }
  };

  // ─── Look and feel, kept in this browser only ────────────────────────
  const DEFAULTS = { theme: 'system', accent: '#1fa855', wall: 'classic', font: 15, radius: 12, enter: true, lang: '' };
  const ACCENTS = ['#1fa855', '#0b84ff', '#7c4dff', '#e91e63', '#ff7043', '#f5a623', '#00a3a3', '#607d8b'];
  let prefs = { ...DEFAULTS };
  try { prefs = { ...DEFAULTS, ...JSON.parse(localStorage.getItem('waex_prefs') || '{}') }; } catch (_) { /* private mode */ }
  const savePrefs = () => { try { localStorage.setItem('waex_prefs', JSON.stringify(prefs)); } catch (_) { /* ignore */ } };
  const lang = () => (WORDS[prefs.lang] ? prefs.lang : (navigator.language || 'en').slice(0, 2) === 'sk' ? 'sk' : 'en');
  const t = (key, vars) => {
    let text = WORDS[lang()][key] || WORDS.en[key] || key;
    if (vars) for (const k of Object.keys(vars)) text = text.replace('{' + k + '}', vars[k]);
    return text;
  };
  const darkQuery = window.matchMedia('(prefers-color-scheme: dark)');
  function applyPrefs() {
    const root = document.documentElement;
    const theme = prefs.theme === 'system' ? (darkQuery.matches ? 'dark' : 'light') : prefs.theme;
    root.dataset.theme = theme;
    root.lang = lang();
    root.style.setProperty('--accent', /^#[0-9a-f]{6}$/i.test(prefs.accent) ? prefs.accent : DEFAULTS.accent);
    root.style.setProperty('--font', Math.min(22, Math.max(12, +prefs.font || 15)) + 'px');
    root.style.setProperty('--radius', Math.min(22, Math.max(0, +prefs.radius)) + 'px');
    $('messages').dataset.wall = prefs.wall;
  }
  darkQuery.addEventListener('change', applyPrefs);

  function applyWords() {
    $('loginIntro').textContent = t('intro');
    $('askCode').textContent = t('askCode');
    $('codeLabel').textContent = t('codeLabel');
    $('passwordLabel').textContent = t('passwordLabel');
    $('confirmCode').textContent = t('confirm');
    $('newCode').textContent = t('newCode');
    $('loginNote').textContent = t('loginNote');
    $('search').placeholder = t('search');
    $('emptyText').textContent = t('emptyText');
    $('text').placeholder = t('typeHere');
    $('settingsTitle').textContent = t('settings');
    $('viewerSave').textContent = t('save');
    for (const b of $('tabs').children) b.textContent = t(b.dataset.tab);
  }

  // ─── Talking to the phone ────────────────────────────────────────────
  class ApiError extends Error {
    constructor(code, status, data) { super(code); this.code = code; this.status = status; this.data = data || {}; }
  }
  async function api(path, body) {
    const options = { credentials: 'same-origin', cache: 'no-store' };
    if (body !== undefined) {
      options.method = 'POST';
      options.headers = { 'Content-Type': 'application/json', 'X-WAEX': '1' };
      options.body = JSON.stringify(body);
    }
    let response;
    try { response = await fetch(path, options); } catch (_) { throw new ApiError('offline', 0); }
    let data = {};
    try { data = await response.json(); } catch (_) { /* empty body */ }
    if (response.status === 401 && state.signedIn) showLogin();
    if (!response.ok) throw new ApiError(data.error || 'failed', response.status, data);
    return data;
  }
  const errorText = (e) => (WORDS.en[e.code] ? t(e.code, { s: e.data && e.data.seconds }) : t('failed'));

  let toastTimer = 0;
  function toast(text) {
    $('toast').textContent = text;
    $('toast').hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { $('toast').hidden = true; }, 3500);
  }

  // ─── State ───────────────────────────────────────────────────────────
  const state = {
    signedIn: false, canSend: false, rev: null, chats: [], tab: 'all', query: '', hits: [],
    current: null, messages: new Map(), nodes: new Map(), noOlder: false, detached: false, drafts: new Map(),
    pairId: null, noAvatar: new Set()
  };

  // ─── Signing in ──────────────────────────────────────────────────────
  function showLogin() {
    state.signedIn = false;
    $('app').hidden = true;
    $('login').hidden = false;
    $('loginStep1').hidden = false;
    $('loginStep2').hidden = true;
    $('loginError').textContent = '';
    if ($('settings').open) $('settings').close();
  }
  async function askCode() {
    $('loginError').textContent = '';
    try {
      const r = await api('/api/pair/request', {});
      state.pairId = r.pairId;
      $('passwordRow').hidden = !r.passwordSet;
      $('loginStep1').hidden = true;
      $('loginStep2').hidden = false;
      $('code').value = '';
      $('password').value = '';
      $('code').focus();
    } catch (e) { $('loginError').textContent = errorText(e); }
  }
  async function confirmCode(event) {
    event.preventDefault();
    $('loginError').textContent = '';
    try {
      await api('/api/pair/confirm', { pairId: state.pairId, code: $('code').value, password: $('password').value });
      $('password').value = '';
      start();
    } catch (e) {
      $('loginError').textContent = errorText(e);
      if (e.code === 'expired') { $('loginStep1').hidden = false; $('loginStep2').hidden = true; }
    }
  }

  // ─── Chat list ───────────────────────────────────────────────────────
  const TYPE_WORD = { 1: 'photo', 2: 'audio', 3: 'video', 4: 'contact', 5: 'location', 9: 'document', 13: 'gif',
    15: 'deleted', 16: 'liveLocation', 20: 'sticker', 42: 'viewOnce', 43: 'viewOnce', 46: 'poll' };
  const TYPE_ICON = { 1: '📷', 2: '🎤', 3: '🎬', 4: '👤', 5: '📍', 9: '📄', 13: '🎞', 15: '🚫', 16: '📍', 20: '🌟', 42: '①', 43: '①', 46: '📊' };

  function shortTime(ts) {
    const d = new Date(ts), now = new Date();
    if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    if (now - d < 6 * 864e5) return d.toLocaleDateString([], { weekday: 'short' });
    return d.toLocaleDateString([], { day: 'numeric', month: 'numeric', year: '2-digit' });
  }
  function avatar(node, jid, name) {
    node.textContent = '';
    let hash = 0;
    for (let i = 0; i < jid.length; i++) hash = (hash * 31 + jid.charCodeAt(i)) | 0;
    node.style.background = 'hsl(' + (Math.abs(hash) % 360) + ' 45% 48%)';
    const letter = (Array.from((name || '?').trim())[0] || '?').toUpperCase();
    if (state.noAvatar.has(jid)) { node.textContent = letter; return; }
    const img = el('img');
    img.alt = '';
    img.loading = 'lazy';
    img.addEventListener('error', () => { state.noAvatar.add(jid); node.textContent = letter; });
    img.src = '/api/avatar/' + enc(jid);
    node.append(img);
  }
  function preview(chat) {
    const word = TYPE_WORD[chat.lastType];
    const body = chat.lastText || (word ? t(word) : '');
    return (chat.lastFromMe ? ticks(chat.lastStatus).text + ' ' : '') + (TYPE_ICON[chat.lastType] ? TYPE_ICON[chat.lastType] + ' ' : '') + body;
  }
  function renderChats() {
    const box = $('chats');
    const keep = box.scrollTop;
    box.textContent = '';
    const q = state.query.toLowerCase();
    const list = state.chats.filter((c) => {
      if (q) return c.name.toLowerCase().includes(q) || c.jid.includes(q);
      if (state.tab === 'archived') return c.archived;
      if (c.archived) return false;
      if (state.tab === 'unread') return c.unread > 0;
      if (state.tab === 'groups') return c.group;
      return true;
    });
    if (q && list.length) box.append(el('div', 'section', t('chatsSection')));
    for (const chat of list) {
      const row = el('div', 'row' + (chat.jid === state.current ? ' on' : ''));
      row.setAttribute('role', 'listitem');
      row.tabIndex = 0;
      const pic = el('div', 'avatar');
      avatar(pic, chat.jid, chat.name);
      const main = el('div', 'grow');
      const top = el('div', 'top');
      top.append(el('div', 'name grow', chat.name), el('div', 'time', shortTime(chat.ts)));
      const bottom = el('div', 'bottom');
      bottom.append(el('div', 'last grow', preview(chat)));
      if (chat.unread > 0) bottom.append(el('div', 'badge', String(chat.unread)));
      main.append(top, bottom);
      row.append(pic, main);
      const open = () => openChat(chat.jid);
      row.addEventListener('click', open);
      row.addEventListener('keydown', (e) => { if (e.key === 'Enter') open(); });
      box.append(row);
    }
    if (q && state.hits.length) {
      box.append(el('div', 'section', t('messagesSection')));
      for (const hit of state.hits) {
        const row = el('div', 'row');
        row.tabIndex = 0;
        const main = el('div', 'grow');
        const top = el('div', 'top');
        top.append(el('div', 'name grow', hit.chat), el('div', 'time', shortTime(hit.ts)));
        main.append(top, el('div', 'last', (hit.fromMe ? t('you') + ': ' : '') + hit.text));
        row.append(main);
        const open = () => openChat(hit.jid, hit);
        row.addEventListener('click', open);
        row.addEventListener('keydown', (e) => { if (e.key === 'Enter') open(); });
        box.append(row);
      }
    }
    if (!box.children.length) box.append(el('div', 'section muted', t('noChats')));
    box.scrollTop = keep;
    const unread = state.chats.filter((c) => c.unread > 0 && !c.archived).length;
    document.title = (unread ? '(' + unread + ') ' : '') + 'WAEX Web';
  }
  async function loadChats() {
    state.chats = (await api('/api/chats')).chats;
    renderChats();
    const chat = currentChat();
    if (chat) $('chatName').textContent = chat.name;
  }
  const currentChat = () => state.chats.find((c) => c.jid === state.current);

  let searchTimer = 0;
  function onSearch() {
    state.query = $('search').value.trim();
    state.hits = [];
    renderChats();
    clearTimeout(searchTimer);
    const q = state.query;
    if (q.length < 2) return;
    searchTimer = setTimeout(async () => {
      try {
        const r = await api('/api/search?q=' + enc(q));
        if (state.query === q) { state.hits = r.results; renderChats(); }
      } catch (_) { /* shown on the next action */ }
    }, 350);
  }

  // ─── Messages ────────────────────────────────────────────────────────
  function ticks(status) {
    if (status === 13 || status === 8 || status === 10) return { text: '✓✓', read: true };
    if (status === 5) return { text: '✓✓', read: false };
    if (status === 4) return { text: '✓', read: false };
    return { text: '🕓', read: false };
  }
  const order = (a, b) => a.sort - b.sort || a.id - b.id;
  const sorted = () => Array.from(state.messages.values()).sort(order);
  const dayKey = (ts) => new Date(ts).toDateString();
  function dayLabel(ts) {
    const d = new Date(ts), now = new Date();
    if (d.toDateString() === now.toDateString()) return t('today');
    if (new Date(now - 864e5).toDateString() === d.toDateString()) return t('yesterday');
    return d.toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  }
  function size(bytes) {
    if (!bytes) return '';
    const units = ['B', 'kB', 'MB', 'GB'];
    let i = 0, n = bytes;
    while (n >= 1024 && i < 3) { n /= 1024; i++; }
    return (i ? n.toFixed(1) : n) + ' ' + units[i];
  }

  /** WhatsApp style *bold*, _italic_, ~strike~, `code` and links, built as elements. */
  function rich(parent, text) {
    const pattern = /(https?:\/\/[^\s<>"']+)|(^|[\s(])([*_~`])(\S(?:[^\n]*?\S)?)\3(?=$|[\s).,!?:;])/g;
    const tags = { '*': 'b', _: 'i', '~': 's', '`': 'code' };
    let last = 0, m;
    while ((m = pattern.exec(text))) {
      if (m.index > last) parent.append(text.slice(last, m.index));
      if (m[1]) {
        const url = m[1].replace(/[).,!?:;]+$/, '');
        const a = el('a', null, url);
        a.href = url;
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        parent.append(a, m[1].slice(url.length));
      } else {
        parent.append(m[2], el(tags[m[3]], null, m[4]));
      }
      last = pattern.lastIndex;
    }
    if (last < text.length) parent.append(text.slice(last));
  }

  function mediaNode(m) {
    const media = m.media, mime = media.mime || '', url = '/api/media/' + m.id;
    if (m.type === 42 || m.type === 43) return el('div', 'note', '① ' + t('viewOnce'));
    if (!media.available) {
      const box = el('div');
      if (m.thumb) {
        const img = el('img', 'pic');
        img.alt = '';
        img.style.cursor = 'default';
        img.src = '/api/thumb/' + m.id;
        box.append(img);
      }
      box.append(el('div', 'note', (TYPE_ICON[m.type] || '📎') + ' ' + t(TYPE_WORD[m.type] || 'document') + ' · ' + t('notOnPhone')));
      return box;
    }
    if (m.type === 20) {
      const img = el('img', 'pic sticker');
      img.alt = t('sticker');
      img.loading = 'lazy';
      img.src = url;
      return img;
    }
    if (/^image\/(jpeg|png|webp|gif)$/.test(mime)) {
      const img = el('img', 'pic');
      img.alt = t('photo');
      img.loading = 'lazy';
      if (media.w > 0 && media.h > 0) {
        img.style.aspectRatio = media.w + ' / ' + media.h;
        img.style.width = Math.min(340, media.w) + 'px';
      }
      img.src = url;
      img.addEventListener('click', () => {
        $('viewerImg').src = url;
        $('viewerSave').href = url;
        $('viewerSave').download = media.name || 'photo.jpg';
        $('viewer').hidden = false;
      });
      return img;
    }
    if (mime.startsWith('video/')) {
      const video = el('video');
      video.controls = true;
      video.preload = 'none';
      video.playsInline = true;
      if (m.type === 13) { video.loop = true; video.muted = true; }
      if (m.thumb) video.poster = '/api/thumb/' + m.id;
      video.src = url;
      return video;
    }
    if (mime.startsWith('audio/')) {
      const audio = el('audio');
      audio.controls = true;
      audio.preload = 'none';
      audio.src = url;
      return audio;
    }
    const link = el('a', 'file');
    link.href = url;
    link.download = media.name || 'file';
    const info = el('span');
    info.append(el('b', null, media.name || t('document')), size(media.size));
    link.append('📄', info);
    return link;
  }

  function messageNode(m, fresh) {
    const node = el('div', 'msg' + (m.fromMe ? ' me' : '') + (fresh ? ' new' : ''));
    node.dataset.sig = JSON.stringify(m);
    if (m.sender) node.append(el('div', 'sender', m.sender));
    if (m.quote) node.append(el('div', 'quote', m.quote));
    if (m.type === 15) {
      node.append(el('span', 'note', '🚫 ' + t('deleted')));
    } else {
      if (m.media) node.append(mediaNode(m));
      else if (TYPE_WORD[m.type]) node.append(el('div', 'note', TYPE_ICON[m.type] + ' ' + t(TYPE_WORD[m.type])));
      if (m.text) {
        const body = el('span', 'body');
        rich(body, m.text);
        node.append(body);
      } else if (!m.media && !TYPE_WORD[m.type]) {
        node.append(el('span', 'note', t('unsupported')));
      }
    }
    const meta = el('span', 'meta', (m.starred ? '★ ' : '') +
      new Date(m.ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
    if (m.fromMe && m.type !== 15) {
      const tick = ticks(m.status);
      meta.append(' ', el('span', tick.read ? 'read' : null, tick.text));
    }
    node.append(meta);
    if (m.reactions) node.append(el('div', null), el('span', 'react', m.reactions));
    return node;
  }

  function renderAll() {
    const box = $('messages');
    box.textContent = '';
    state.nodes.clear();
    if (!state.noOlder && state.messages.size) {
      const more = el('button', 'more', t('older'));
      more.addEventListener('click', loadOlder);
      box.append(more);
    }
    let day = '';
    for (const m of sorted()) {
      if (dayKey(m.ts) !== day) { day = dayKey(m.ts); box.append(el('div', 'day', dayLabel(m.ts))); }
      const node = messageNode(m, false);
      state.nodes.set(m.id, node);
      box.append(node);
    }
  }
  const nearBottom = () => {
    const box = $('messages');
    return box.scrollHeight - box.scrollTop - box.clientHeight < 140;
  };
  const toBottom = () => { $('messages').scrollTop = $('messages').scrollHeight; };

  /** Adds new messages at the end and redraws the ones that changed (ticks, reactions, deletions). */
  function merge(list) {
    const box = $('messages');
    const stick = nearBottom();
    const all = sorted();
    let newest = all.length ? all[all.length - 1] : null;
    let disorder = false;
    for (const m of list) {
      const old = state.nodes.get(m.id);
      state.messages.set(m.id, m);
      if (old) {
        if (old.dataset.sig !== JSON.stringify(m)) {
          const node = messageNode(m, false);
          old.replaceWith(node);
          state.nodes.set(m.id, node);
        }
      } else if (newest && order(m, newest) < 0) {
        disorder = true;
      } else {
        if (!newest || dayKey(newest.ts) !== dayKey(m.ts)) box.append(el('div', 'day', dayLabel(m.ts)));
        const node = messageNode(m, true);
        state.nodes.set(m.id, node);
        box.append(node);
        newest = m;
      }
    }
    if (disorder) renderAll();
    if (stick) toBottom();
  }

  const messagesUrl = (jid, query) => '/api/chats/' + enc(jid) + '/messages?limit=60' + (query || '');

  async function openChat(jid, hit) {
    if (state.current) state.drafts.set(state.current, $('text').value);
    state.current = jid;
    state.messages = new Map();
    state.nodes = new Map();
    state.noOlder = false;
    state.detached = !!hit;
    const chat = currentChat();
    $('app').classList.add('open');
    $('empty').hidden = true;
    $('chat').hidden = false;
    $('toLatest').hidden = !hit;
    $('chatName').textContent = chat ? chat.name : jid;
    $('chatSub').textContent = chat && chat.group ? t('group') : jid.endsWith('@s.whatsapp.net') ? '+' + jid.split('@')[0] : '';
    avatar($('chatAvatar'), jid, chat ? chat.name : '?');
    $('messages').textContent = '';
    $('text').value = state.drafts.get(jid) || '';
    autosize();
    renderChats();
    try {
      const query = hit ? '&beforeSort=' + hit.sort + '&beforeId=' + (hit.id + 1) : '';
      const r = await api(messagesUrl(jid, query));
      if (state.current !== jid) return;
      state.canSend = r.canSend;
      for (const m of r.messages) state.messages.set(m.id, m);
      if (r.messages.length < 60) state.noOlder = true;
      renderAll();
      const target = hit && state.nodes.get(hit.id);
      if (target) { target.classList.add('hit'); target.scrollIntoView({ block: 'center' }); } else toBottom();
      showSendNote();
      if (window.matchMedia('(min-width: 761px)').matches) $('text').focus();
    } catch (e) { toast(errorText(e)); }
  }
  async function loadOlder() {
    const jid = state.current, first = sorted()[0];
    if (!jid || !first) return;
    try {
      const r = await api(messagesUrl(jid, '&beforeSort=' + first.sort + '&beforeId=' + first.id));
      if (state.current !== jid) return;
      if (r.messages.length < 60) state.noOlder = true;
      const box = $('messages'), before = box.scrollHeight;
      for (const m of r.messages) state.messages.set(m.id, m);
      renderAll();
      box.scrollTop += box.scrollHeight - before;
    } catch (e) { toast(errorText(e)); }
  }
  async function loadLatest() {
    const jid = state.current;
    if (!jid || state.detached) return;
    const r = await api(messagesUrl(jid));
    if (state.current !== jid) return;
    state.canSend = r.canSend;
    merge(r.messages);
    showSendNote();
  }

  // ─── Writing ─────────────────────────────────────────────────────────
  function autosize() {
    const box = $('text');
    box.style.height = 'auto';
    box.style.height = Math.min(160, box.scrollHeight + 2) + 'px';
  }
  function showSendNote() {
    const note = $('sendNote');
    note.textContent = '';
    note.hidden = state.canSend;
    if (state.canSend) return;
    const button = el('button', null, t('openOnPhone'));
    button.addEventListener('click', openOnPhone);
    note.append(t('noSend') + ' ', button);
  }
  async function openOnPhone() {
    try {
      await api('/api/chats/' + enc(state.current) + '/open', { text: $('text').value });
      toast(t('openedOnPhone'));
    } catch (e) { toast(errorText(e)); }
  }
  let sending = false;
  async function send() {
    const jid = state.current, text = $('text').value.trim();
    if (!jid || !text || sending) return;
    if (!state.canSend) { openOnPhone(); return; }
    sending = true;
    $('send').disabled = true;
    try {
      const r = await api('/api/chats/' + enc(jid) + '/send', { text });
      if (state.current === jid) { $('text').value = ''; autosize(); }
      state.drafts.delete(jid);
      if (state.detached) await openChat(jid); else await loadLatest();
      toBottom();
      if (!r.stored) toast(t('queued'));
    } catch (e) {
      if (e.code === 'send_unavailable' || e.code === 'send_failed') { state.canSend = false; showSendNote(); } else toast(errorText(e));
    } finally {
      sending = false;
      $('send').disabled = false;
      $('text').focus();
    }
  }

  // ─── Live updates ────────────────────────────────────────────────────
  let polling = false;
  async function poll() {
    if (polling) return;
    polling = true;
    while (state.signedIn) {
      try {
        const u = await api('/api/updates' + (state.rev == null ? '' : '?rev=' + state.rev));
        if (u.canSend !== state.canSend) { state.canSend = u.canSend; if (state.current) showSendNote(); }
        if (u.rev !== state.rev) {
          state.rev = u.rev;
          await loadChats();
          await loadLatest();
          await sleep(600);
        }
      } catch (e) {
        if (e.status === 401) break;
        await sleep(3000);
      }
    }
    polling = false;
  }

  // ─── Settings ────────────────────────────────────────────────────────
  function field(label, control) {
    const row = el('label', 'field');
    row.append(el('span', null, label), control);
    return row;
  }
  function select(options, value, onChange) {
    const node = el('select');
    for (const [key, label] of options) {
      const option = el('option', null, label);
      option.value = key;
      node.append(option);
    }
    node.value = value;
    node.addEventListener('change', () => onChange(node.value));
    return node;
  }
  function range(min, max, value, onChange) {
    const node = el('input');
    node.type = 'range';
    node.min = min;
    node.max = max;
    node.value = value;
    node.addEventListener('input', () => onChange(+node.value));
    return node;
  }
  const change = (key, redraw) => (value) => {
    prefs[key] = value;
    savePrefs();
    applyPrefs();
    if (redraw) { applyWords(); renderChats(); if (state.current) { renderAll(); showSendNote(); } openSettings(); }
  };

  async function openSettings() {
    const body = $('settingsBody');
    body.textContent = '';
    body.append(el('h3', null, t('look')));
    body.append(field(t('theme'), select([['system', t('system')], ['light', t('light')], ['dark', t('dark')], ['black', t('black')]], prefs.theme, change('theme'))));
    const swatches = el('div', 'swatches');
    for (const colour of ACCENTS) {
      const dot = el('button', 'swatch' + (colour === prefs.accent ? ' on' : ''));
      dot.style.background = colour;
      dot.setAttribute('aria-label', colour);
      dot.addEventListener('click', () => {
        change('accent')(colour);
        for (const other of swatches.children) other.classList.toggle('on', other === dot);
      });
      swatches.append(dot);
    }
    const custom = el('input');
    custom.type = 'color';
    custom.value = prefs.accent;
    custom.style.cssText = 'width:36px;height:28px;padding:0';
    custom.addEventListener('input', () => change('accent')(custom.value));
    swatches.append(custom);
    body.append(field(t('accent'), swatches));
    body.append(field(t('wallpaper'), select([['classic', t('wallClassic')], ['dots', t('wallDots')], ['gradient', t('wallGradient')], ['plain', t('wallPlain')]], prefs.wall, change('wall'))));
    body.append(field(t('fontSize'), range(12, 22, prefs.font, change('font'))));
    body.append(field(t('corners'), range(0, 22, prefs.radius, change('radius'))));
    const enter = el('input');
    enter.type = 'checkbox';
    enter.checked = prefs.enter;
    enter.addEventListener('change', () => change('enter')(enter.checked));
    body.append(field(t('enterSends'), enter));
    body.append(field(t('language'), select([['', t('system')], ['en', 'English'], ['sk', 'Slovenčina']], prefs.lang, change('lang', true))));

    const account = el('div');
    body.append(account);
    if (!$('settings').open) $('settings').showModal();
    try { drawAccount(account, await api('/api/account')); } catch (e) { account.append(el('p', 'error', errorText(e))); }
  }

  function drawAccount(box, info) {
    const reload = async () => { box.textContent = ''; drawAccount(box, await api('/api/account')); };
    const act = (work) => async () => { try { await work(); } catch (e) { toast(errorText(e)); } };

    box.append(el('h3', null, t('security')));
    box.append(el('p', 'muted small', t(info.passwordSet ? 'passwordOn' : 'passwordOff')));
    const current = el('input');
    current.type = 'password';
    current.placeholder = t('currentPassword');
    current.autocomplete = 'current-password';
    const next = el('input');
    next.type = 'password';
    next.placeholder = t('newPassword');
    next.autocomplete = 'new-password';
    if (info.passwordSet) box.append(current);
    const row = el('div', 'inline');
    const save = el('button', 'primary', t('setPassword'));
    save.addEventListener('click', act(async () => {
      await api('/api/account/password', { current: current.value, new: next.value });
      toast(t('saved'));
      await reload();
    }));
    row.append(next, save);
    box.append(row);
    if (info.passwordSet) {
      const remove = el('button', 'link danger', t('removePassword'));
      remove.addEventListener('click', act(async () => {
        await api('/api/account/password', { current: current.value, new: null });
        toast(t('saved'));
        await reload();
      }));
      box.append(remove);
    }

    box.append(el('h3', null, t('browsers')));
    for (const s of info.sessions) {
      const item = el('div', 'item');
      const text = el('div', 'grow');
      text.append(el('div', null, s.label + (s.current ? ' (' + t('thisBrowser') + ')' : '')),
        el('div', 'muted small', t('lastSeen') + ' ' + new Date(s.lastSeen).toLocaleString()));
      item.append(text);
      if (!s.current) {
        const out = el('button', 'link danger', t('signOutOne'));
        out.addEventListener('click', act(async () => { await api('/api/account/sessions', { id: s.id }); await reload(); }));
        item.append(out);
      }
      box.append(item);
    }
    const everywhere = el('button', 'link danger', t('signOutAll'));
    everywhere.addEventListener('click', act(async () => { await api('/api/account/sessions', { all: true }); showLogin(); }));
    const here = el('button', 'link', t('signOut'));
    here.addEventListener('click', act(async () => { await api('/api/logout', {}); showLogin(); }));
    const outs = el('div', 'inline');
    outs.append(here, everywhere);
    box.append(outs);

    box.append(el('h3', null, t('tokens')));
    box.append(el('p', 'muted small', t('tokensInfo')));
    for (const token of info.tokens) {
      const item = el('div', 'item');
      const text = el('div', 'grow');
      text.append(el('div', null, token.name), el('div', 'muted small', new Date(token.created).toLocaleDateString()));
      const remove = el('button', 'link danger', t('remove'));
      remove.addEventListener('click', act(async () => { await api('/api/account/tokens/delete', { id: token.id }); await reload(); }));
      item.append(text, remove);
      box.append(item);
    }
    const tokenRow = el('div', 'inline');
    const name = el('input');
    name.placeholder = t('tokenName');
    name.maxLength = 60;
    const create = el('button', 'primary', t('createToken'));
    create.addEventListener('click', act(async () => {
      const r = await api('/api/account/tokens', { name: name.value });
      await reload();
      // Shown once, right under the list; reload() redrew the section, so add it to the new one.
      const fresh = el('div');
      fresh.append(el('p', 'small', t('tokenOnce')), el('div', 'tokenbox', r.token));
      box.insertBefore(fresh, box.lastElementChild);
    }));
    tokenRow.append(name, create);
    box.append(tokenRow);
    const docs = el('a', 'link', t('apiDocs'));
    docs.href = '/api/docs';
    docs.target = '_blank';
    docs.rel = 'noopener';
    box.append(docs);
  }

  // ─── Start ───────────────────────────────────────────────────────────
  async function start() {
    let status;
    try { status = await api('/api/status'); } catch (e) {
      showLogin();
      $('loginError').textContent = t('offline');
      return;
    }
    if (!status.signedIn) { showLogin(); return; }
    state.signedIn = true;
    state.canSend = status.canSend;
    state.rev = null;
    $('login').hidden = true;
    $('app').hidden = false;
    try { await loadChats(); } catch (_) { /* the poll loop retries */ }
    poll();
  }

  $('askCode').addEventListener('click', askCode);
  $('newCode').addEventListener('click', askCode);
  $('loginStep2').addEventListener('submit', confirmCode);
  $('search').addEventListener('input', onSearch);
  $('tabs').addEventListener('click', (e) => {
    const tab = e.target.dataset && e.target.dataset.tab;
    if (!tab) return;
    state.tab = tab;
    for (const b of $('tabs').children) b.classList.toggle('on', b === e.target);
    renderChats();
  });
  $('back').addEventListener('click', () => { $('app').classList.remove('open'); });
  $('toLatest').addEventListener('click', () => openChat(state.current));
  $('send').addEventListener('click', send);
  $('text').addEventListener('input', autosize);
  $('text').addEventListener('keydown', (e) => {
    if (e.key !== 'Enter' || e.isComposing) return;
    if (prefs.enter ? !e.shiftKey : (e.ctrlKey || e.metaKey)) { e.preventDefault(); send(); }
  });
  $('openSettings').addEventListener('click', openSettings);
  $('closeSettings').addEventListener('click', () => $('settings').close());
  $('viewer').addEventListener('click', (e) => { if (e.target !== $('viewerSave')) { $('viewer').hidden = true; $('viewerImg').removeAttribute('src'); } });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') $('viewer').hidden = true; });

  applyPrefs();
  applyWords();
  start();
})();
