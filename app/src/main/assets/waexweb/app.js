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
      openedPlain: 'The chat is open on the phone now.',
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
      too_many_tokens: 'You already have 10 tokens. Delete one first.', copied: 'Copied.',
      reply: 'Reply', react: 'React', copy: 'Copy text', cancel: 'Cancel', emoji: 'Emoji',
      doubleReact: 'Double click reacts with', sendProblem: 'WhatsApp did not take the message ({s}). Try again.',
      react_unavailable: 'Reactions do not work with this WhatsApp version.',
      message_not_found: 'WhatsApp no longer has that message.', online: 'Connected to the phone',
      reconnecting: 'The phone does not answer. Trying again…',
      deletedBy: 'Deleted by the sender {s}', deletedShort: 'Deleted', delete: 'Delete',
      deleteForMe: 'Delete for me', deleteForAll: 'Delete for everyone', deleteSure: 'Delete this message?',
      deleted_ok: 'Deleted.', delete_unavailable: 'Deleting does not work with this WhatsApp version.',
      not_your_message: 'Only your own messages can be deleted for everyone.', accounts: 'Accounts', sendAs: 'Send as',
      switchConfirm: 'Switch WhatsApp on the phone to {s}? WhatsApp restarts, which takes a few seconds.',
      switching: 'Switching the account on the phone…', switched: 'Now using {s}.',
      switchSlow: 'The phone did not switch. Open WhatsApp on the phone, or tap the WAEX notification there.',
      notInUse: 'not in use', account_inactive: 'This account is not in use on the phone. Switch to it first.',
      account_logged_out: 'That account is logged out.', cannot_switch: 'WhatsApp could not switch accounts.',
      chat_not_found: 'This account has no chat with them yet.', viewOnceBadge: 'View once',
      viewOnceGone: 'View once message. The file is no longer on the phone.',
      appearance: 'Appearance',
      chatBackground: 'Chat background',
      chatsLook: 'Chats',
      chatList: 'Chat list',
      writing: 'Writing',
      ocean: 'Ocean',
      paper: 'Paper',
      accentCustom: 'Custom colour',
      automatic: 'Automatic',
      bubbleOwn: 'My bubbles',
      bubbleOther: 'Other bubbles',
      tails: 'Bubble tails',
      fontFamily: 'Font',
      fontSystem: 'System',
      fontRounded: 'Rounded',
      fontSerif: 'Serif',
      wallDoodles: 'Doodles',
      wallGrid: 'Grid',
      wallLines: 'Lines',
      wallWaves: 'Waves',
      wallAurora: 'Aurora',
      wallColor: 'Background colour',
      wallOpacity: 'Pattern strength',
      wallDim: 'Dim background',
      density: 'Message spacing',
      listDensity: 'List spacing',
      comfortable: 'Comfortable',
      compact: 'Compact',
      msgWidth: 'Bubble width',
      sideWidth: 'Panel width',
      listAvatars: 'Pictures in the chat list',
      groupAvatars: 'Sender pictures in groups',
      senderColors: 'Coloured sender names',
      bigEmoji: 'Large emoji',
      clock: 'Clock',
      clockAuto: 'Automatic',
      clock24: '24-hour',
      clock12: '12-hour',
      motion: 'Animations',
      motionSystem: 'Same as system',
      motionReduce: 'Reduced',
      motionFull: 'Always on',
      quickReactions: 'Quick reactions',
      quickHint: 'Pick a slot, then choose an emoji for it.',
      enterHint: 'Shift+Enter makes a new line',
      titleCount: 'Unread count in the tab title',
      resetLook: 'Reset look',
      resetDone: 'Look was reset.',
      previewIn: 'Hi! This is how your chats look.',
      previewOut: 'Nice. I like it.',
      switchTheme: 'Switch light / dark',
      openPhone: 'Open on the phone',
      toLatest: 'Newest messages',
      back: 'Back',
      close: 'Close',
      emptyTitle: 'Your chats, on the big screen',
      emptyHint: 'Everything stays on your phone and your Wi-Fi.',
      account: 'Account'
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
      openedPlain: 'Chat je teraz otvorený v telefóne.',
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
      too_many_tokens: 'Máš už 10 tokenov. Najprv jeden vymaž.', copied: 'Skopírované.',
      reply: 'Odpovedať', react: 'Reagovať', copy: 'Kopírovať text', cancel: 'Zrušiť', emoji: 'Emoji',
      doubleReact: 'Dvojklik reaguje pomocou', sendProblem: 'WhatsApp správu neprevzal ({s}). Skús to znova.',
      react_unavailable: 'Reakcie s touto verziou WhatsAppu nefungujú.',
      message_not_found: 'WhatsApp už túto správu nemá.', online: 'Spojené s telefónom',
      reconnecting: 'Telefón neodpovedá. Skúšam znova…',
      deletedBy: 'Odosielateľ to vymazal {s}', deletedShort: 'Vymazané', delete: 'Vymazať',
      deleteForMe: 'Vymazať pre mňa', deleteForAll: 'Vymazať pre všetkých', deleteSure: 'Vymazať túto správu?',
      deleted_ok: 'Vymazané.', delete_unavailable: 'Mazanie s touto verziou WhatsAppu nefunguje.',
      not_your_message: 'Pre všetkých sa dajú vymazať len tvoje vlastné správy.', accounts: 'Účty', sendAs: 'Odoslať ako',
      switchConfirm: 'Prepnúť WhatsApp v telefóne na {s}? WhatsApp sa reštartuje, trvá to pár sekúnd.',
      switching: 'Prepínam účet v telefóne…', switched: 'Teraz sa používa {s}.',
      switchSlow: 'Telefón účet neprepol. Otvor WhatsApp v telefóne alebo ťukni tam na notifikáciu WAEX.',
      notInUse: 'nepoužíva sa', account_inactive: 'Tento účet sa v telefóne nepoužíva. Najprv sa naň prepni.',
      account_logged_out: 'Ten účet je odhlásený.', cannot_switch: 'WhatsApp nevedel prepnúť účet.',
      chat_not_found: 'Tento účet s nimi ešte nemá chat.', viewOnceBadge: 'Na jedno zobrazenie',
      viewOnceGone: 'Správa na jedno zobrazenie. Súbor už v telefóne nie je.',
      appearance: 'Vzhľad',
      chatBackground: 'Pozadie chatu',
      chatsLook: 'Chaty',
      chatList: 'Zoznam chatov',
      writing: 'Písanie',
      ocean: 'Oceán',
      paper: 'Papier',
      accentCustom: 'Vlastná farba',
      automatic: 'Automaticky',
      bubbleOwn: 'Moje bubliny',
      bubbleOther: 'Bubliny ostatných',
      tails: 'Chvostíky bublín',
      fontFamily: 'Písmo',
      fontSystem: 'Systémové',
      fontRounded: 'Zaoblené',
      fontSerif: 'Pätkové',
      wallDoodles: 'Kresbičky',
      wallGrid: 'Mriežka',
      wallLines: 'Čiary',
      wallWaves: 'Vlny',
      wallAurora: 'Polárna žiara',
      wallColor: 'Farba pozadia',
      wallOpacity: 'Výraznosť vzoru',
      wallDim: 'Stmaviť pozadie',
      density: 'Rozostupy správ',
      listDensity: 'Rozostupy v zozname',
      comfortable: 'Pohodlné',
      compact: 'Husté',
      msgWidth: 'Šírka bublín',
      sideWidth: 'Šírka panela',
      listAvatars: 'Obrázky v zozname chatov',
      groupAvatars: 'Obrázky odosielateľov v skupinách',
      senderColors: 'Farebné mená odosielateľov',
      bigEmoji: 'Veľké emoji',
      clock: 'Formát času',
      clockAuto: 'Automaticky',
      clock24: '24-hodinový',
      clock12: '12-hodinový',
      motion: 'Animácie',
      motionSystem: 'Podľa systému',
      motionReduce: 'Obmedzené',
      motionFull: 'Vždy zapnuté',
      quickReactions: 'Rýchle reakcie',
      quickHint: 'Vyber miesto a potom emoji, ktoré tam má byť.',
      enterHint: 'Shift+Enter urobí nový riadok',
      titleCount: 'Počet neprečítaných v názve karty',
      resetLook: 'Obnoviť vzhľad',
      resetDone: 'Vzhľad bol obnovený.',
      previewIn: 'Ahoj! Takto vyzerajú tvoje chaty.',
      previewOut: 'Pekné. Páči sa mi to.',
      switchTheme: 'Prepnúť svetlú / tmavú',
      openPhone: 'Otvoriť v telefóne',
      toLatest: 'Najnovšie správy',
      back: 'Späť',
      close: 'Zavrieť',
      emptyTitle: 'Tvoje chaty na veľkej obrazovke',
      emptyHint: 'Všetko ostáva v telefóne a na tvojej Wi-Fi.',
      account: 'Účet'
    }
  };

  // ─── Look and feel, kept in this browser only ────────────────────────
  const DEFAULTS = {
    theme: 'system', accent: '#0b7f63', bubbleOwn: '', bubbleOther: '',
    wall: 'classic', wallColor: '', wallOpacity: 100, wallDim: 0,
    font: 14.5, family: 'system', radius: 10, tails: true,
    density: 'comfortable', list: 'comfortable', listAvatars: true,
    groupAvatars: true, senderColors: true, bigEmoji: true, msgWidth: 65, sideWidth: 380,
    clock: 'auto', motion: 'system', enter: true, react: '❤️',
    quick: ['👍', '❤️', '😂', '😮', '😢', '🙏'], titleCount: true, lang: ''
  };
  /** Prefs that only change the look; "Reset look" puts these back. */
  const LOOK_KEYS = ['theme', 'accent', 'bubbleOwn', 'bubbleOther', 'wall', 'wallColor', 'wallOpacity', 'wallDim', 'font', 'family',
    'radius', 'tails', 'density', 'list', 'listAvatars', 'groupAvatars', 'senderColors', 'bigEmoji', 'msgWidth', 'sideWidth', 'motion'];
  const EMOJI = ('😀 😁 😂 🤣 😊 😇 🙂 😉 😍 🥰 😘 😋 😜 🤪 🤔 🤨 😐 😏 😒 🙄 😬 😴 😷 🤒 🤕 🤢 🥵 🥶 😎 🤓 😕 😟 😮 😳 🥺 😢 😭 😱 😤 😡 🤬 💀 💩 🤡 👻 👍 👎 👌 ✌️ 🤞 🤝 👏 🙌 🙏 💪 👀 🫶 ❤️ 🧡 💛 💚 💙 💜 🖤 💔 💯 🔥 ✨ 🎉 🎂 🎁 ☀️ 🌙 ⭐ ☕ 🍕 🍺 ⚽ 🚗 ✈️ 🏠 📞 💻 ✅ ❌ ❓ ❗').split(' ');
  const ACCENTS = ['#0b7f63', '#0c7a86', '#1a6fd6', '#5257d6', '#7e4fd0', '#c2336a', '#c74a2f', '#a66a10', '#56697a'];
  const THEMES = ['system', 'light', 'dark', 'black', 'ocean', 'paper'];
  const DARK = new Set(['dark', 'black', 'ocean']);
  const WALLS = ['classic', 'dots', 'grid', 'lines', 'waves', 'gradient', 'aurora', 'plain'];
  // Only fonts the computer already has: the page never loads anything from the internet.
  const FAMILIES = {
    system: null,
    rounded: 'ui-rounded, "SF Pro Rounded", "Nunito", "Segoe UI", system-ui, sans-serif',
    serif: 'ui-serif, "Iowan Old Style", "Palatino Linotype", Georgia, serif'
  };
  let prefs = { ...DEFAULTS };
  try { prefs = { ...DEFAULTS, ...JSON.parse(localStorage.getItem('waex_prefs') || '{}') }; } catch (_) { /* private mode */ }
  // Older versions stored the old default colour; move those to the new one.
  if (prefs.accent === '#00a884' || prefs.accent === undefined) prefs.accent = DEFAULTS.accent;
  if (!Array.isArray(prefs.quick) || prefs.quick.length !== 6) prefs.quick = DEFAULTS.quick.slice();
  const savePrefs = () => { try { localStorage.setItem('waex_prefs', JSON.stringify(prefs)); } catch (_) { /* ignore */ } };
  const quick = () => prefs.quick;
  const reactEmoji = () => (prefs.quick.includes(prefs.react) ? prefs.react : prefs.quick[0]);
  const lang = () => (WORDS[prefs.lang] ? prefs.lang : (navigator.language || 'en').slice(0, 2) === 'sk' ? 'sk' : 'en');
  const t = (key, vars) => {
    let text = WORDS[lang()][key] || WORDS.en[key] || key;
    if (vars) for (const k of Object.keys(vars)) text = text.replace('{' + k + '}', vars[k]);
    return text;
  };
  const darkQuery = window.matchMedia('(prefers-color-scheme: dark)');

  const isHex = (v) => /^#[0-9a-f]{6}$/i.test(v);
  const rgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  const lum = (hex) => {
    const [r, g, b] = rgb(hex).map((v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };
  const contrast = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
  const mix = (a, b, p) => '#' + rgb(a).map((v, i) => Math.round(v * p + rgb(b)[i] * (1 - p)).toString(16).padStart(2, '0')).join('');
  const best = (bg, a, b) => (contrast(a, bg) >= contrast(b, bg) ? a : b);
  const clamp = (v, lo, hi, d) => Math.min(hi, Math.max(lo, Number.isFinite(+v) ? +v : d));
  const resolvedTheme = () => (prefs.theme === 'system' || !THEMES.includes(prefs.theme) ? (darkQuery.matches ? 'dark' : 'light') : prefs.theme);

  function applyPrefs() {
    const root = document.documentElement;
    const set = (k, v) => (v == null ? root.style.removeProperty(k) : root.style.setProperty(k, v));
    const flag = (name, value) => { if (value == null) delete root.dataset[name]; else root.dataset[name] = value; };
    const theme = resolvedTheme();
    const dark = DARK.has(theme);
    root.dataset.theme = theme;
    root.lang = lang();

    const accent = isHex(prefs.accent) ? prefs.accent : DEFAULTS.accent;
    set('--accent', accent);
    set('--on-accent', best(accent, '#ffffff', '#0b1014'));
    // The accent used as text must stay readable on the theme's surfaces.
    const surface = getComputedStyle(root).getPropertyValue('--panel3').trim() || (dark ? '#24323a' : '#e6eaec');
    let ink = mix(accent, dark ? '#ffffff' : '#000000', dark ? 0.55 : 0.74);
    for (let i = 0; i < 12 && isHex(surface) && contrast(ink, surface) < 4.5; i++) ink = mix(ink, dark ? '#ffffff' : '#000000', 0.85);
    set('--accent-text-custom', ink);

    const own = isHex(prefs.bubbleOwn) ? prefs.bubbleOwn : null, other = isHex(prefs.bubbleOther) ? prefs.bubbleOther : null;
    set('--out-custom', own); set('--out-text-custom', own && best(own, '#121b21', '#f1f4f6'));
    set('--in-custom', other); set('--in-text-custom', other && best(other, '#121b21', '#f1f4f6'));
    const wall = isHex(prefs.wallColor) ? prefs.wallColor : null;
    set('--wall-custom', wall); set('--wall-ink-custom', wall && best(wall, '#000000', '#ffffff'));
    set('--wall-opacity', String(clamp(prefs.wallOpacity, 0, 200, 100) / 100));
    set('--wall-dim', String(clamp(prefs.wallDim, 0, 60, 0) / 100));

    set('--font', clamp(prefs.font, 12, 20, 14.5) + 'px');
    set('--radius', clamp(prefs.radius, 0, 22, 10) + 'px');
    set('--msg-max', clamp(prefs.msgWidth, 50, 85, 65) + '%');
    set('--side-w', clamp(prefs.sideWidth, 280, 520, 380) + 'px');
    set('--family', FAMILIES[prefs.family] || null);

    flag('density', prefs.density === 'compact' ? 'compact' : null);
    flag('list', prefs.list === 'compact' ? 'compact' : null);
    flag('tails', prefs.tails ? null : 'off');
    flag('listAvatars', prefs.listAvatars ? null : 'off');
    flag('senderColors', prefs.senderColors ? null : 'off');
    flag('motion', prefs.motion === 'reduce' || prefs.motion === 'full' ? prefs.motion : null);
    const wallName = WALLS.includes(prefs.wall) ? prefs.wall : 'classic';
    for (const box of document.querySelectorAll('.messages')) box.dataset.wall = wallName;
  }
  darkQuery.addEventListener('change', () => { applyPrefs(); if ($('settings').open) refreshSettings(); });

  const SVG = 'http://www.w3.org/2000/svg';
  const icon = (name) => {
    const svg = document.createElementNS(SVG, 'svg');
    svg.setAttribute('class', 'ic');
    svg.setAttribute('aria-hidden', 'true');
    const use = document.createElementNS(SVG, 'use');
    use.setAttribute('href', '#i-' + name);
    svg.append(use);
    return svg;
  };
  const clockOpts = () => ({ hour: '2-digit', minute: '2-digit', ...(prefs.clock === 'auto' ? {} : { hour12: prefs.clock === '12' }) });

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
    $('emojiButton').title = t('emoji');
    $('replyCancel').title = t('cancel');
    const label = (id, key) => { $(id).title = t(key); $(id).setAttribute('aria-label', t(key)); };
    label('back', 'back'); label('closeSettings', 'close'); label('toLatest', 'toLatest');
    label('openSettings', 'settings'); label('toggleTheme', 'switchTheme'); label('phoneOpen', 'openPhone');
    $('emptyHint').lastElementChild.textContent = t('emptyHint');
    $('conn').title = t('online');
    renderTabs();
  }
  function renderTabs() {
    const unread = state.chats.filter((c) => c.unread > 0 && !c.archived).length;
    for (const b of $('tabs').children) {
      b.textContent = t(b.dataset.tab);
      if (b.dataset.tab === 'unread' && unread > 0) b.append(el('span', 'count', String(unread)));
    }
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
    signedIn: false, canSend: false, canReact: false, canReply: false, canDelete: false, reply: null, rev: null, chats: [], tab: 'all', query: '', hits: [],
    current: null, messages: new Map(), nodes: new Map(), noOlder: false, detached: false, drafts: new Map(),
    pairId: null, noAvatar: new Set(),
    // Accounts on the phone; `account` is the account of the open chat ('' = the one in use).
    accounts: [], merged: false, activeAccount: '', account: '', sendAs: '', switchingTo: null, pending: null
  };
  /** "?account=…" (or "&account=…") when the open chat belongs to an account that is not in use. */
  const accParam = (sep, account) => {
    const id = account === undefined ? state.account : account;
    return id && id !== state.activeAccount ? sep + 'account=' + encodeURIComponent(id) : '';
  };
  const accountName = (id) => { const a = state.accounts.find((x) => x.id === id); return a ? a.name : id; };

  // ─── Signing in ──────────────────────────────────────────────────────
  function showLogin() {
    state.signedIn = false;
    $('app').hidden = true;
    $('login').hidden = false;
    $('loginStep1').hidden = false;
    $('loginStep2').hidden = true;
    $('step2Dot').classList.remove('on');
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
      $('step2Dot').classList.add('on');
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
  const TYPE_ICON = { 1: 'photo', 2: 'mic', 3: 'video', 4: 'user', 5: 'pin', 9: 'file', 13: 'video', 15: 'block', 16: 'pin',
    20: null, 42: 'lock', 43: 'lock', 46: 'poll' };
  /** A short line with a symbol in front, e.g. "📷 Photo". */
  function labelled(cls, type, text) {
    const node = el('div', cls);
    const name = TYPE_ICON[type];
    if (name) node.append(icon(name), ' ');
    node.append(text);
    return node;
  }

  function shortTime(ts) {
    const d = new Date(ts), now = new Date();
    if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString([], clockOpts());
    if (now - d < 6 * 864e5) return d.toLocaleDateString([], { weekday: 'short' });
    return d.toLocaleDateString([], { day: 'numeric', month: 'numeric', year: '2-digit' });
  }
  function avatar(node, jid, name, account) {
    node.textContent = '';
    let hash = 0;
    for (let i = 0; i < jid.length; i++) hash = (hash * 31 + jid.charCodeAt(i)) | 0;
    node.style.background = 'hsl(' + (Math.abs(hash) % 360) + ' 42% 44%)';
    const letter = (Array.from((name || '?').trim())[0] || '?').toUpperCase();
    if (state.noAvatar.has(jid)) { node.textContent = letter; return; }
    const img = el('img');
    img.alt = '';
    img.loading = 'lazy';
    img.addEventListener('error', () => { state.noAvatar.add(jid); node.textContent = letter; });
    img.src = '/api/avatar/' + enc(jid) + accParam('?', account || '');
    node.append(img);
  }
  function preview(node, chat) {
    const word = TYPE_WORD[chat.lastType];
    const body = chat.lastText || (word ? t(word) : '');
    if (chat.lastFromMe) {
      const tick = ticks(chat.lastStatus);
      const span = el('span', tick.read ? 'read' : null);
      span.append(icon(tick.icon));
      node.append(span, ' ');
    }
    if (TYPE_ICON[chat.lastType]) node.append(icon(TYPE_ICON[chat.lastType]), ' ');
    node.append(body);
    return node;
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
      const row = el('div', 'row' + (sameChat(chat) ? ' on' : '') + (chat.unread > 0 ? ' fresh' : ''));
      row.setAttribute('role', 'listitem');
      row.tabIndex = 0;
      const pic = el('div', 'avatar');
      avatar(pic, chat.jid, chat.name, chat.account || '');
      const main = el('div', 'grow');
      const top = el('div', 'top');
      const name = el('div', 'name grow', chat.name);
      if (state.merged && chat.account) {
        const tag = el('span', 'acct' + (chat.account === state.activeAccount ? '' : ' idle'), chat.accountName || '');
        name.append(' ', tag);
      }
      top.append(name, el('div', 'time', shortTime(chat.ts)));
      const bottom = el('div', 'bottom');
      bottom.append(preview(el('div', 'last grow'), chat));
      if (chat.unread > 0) bottom.append(el('div', 'badge', String(chat.unread)));
      main.append(top, bottom);
      row.append(pic, main);
      const open = () => openChat(chat.jid, null, chat.account || '');
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
    document.title = (unread && prefs.titleCount ? '(' + unread + ') ' : '') + 'WAEX Web';
    renderTabs();
  }
  async function loadChats() {
    state.chats = (await api('/api/chats')).chats;
    renderChats();
    const chat = currentChat();
    if (chat) $('chatName').textContent = chat.name;
  }
  const sameChat = (c) => c.jid === state.current && ((c.account && c.account !== state.activeAccount) ? c.account : '') === state.account;
  const currentChat = () => state.chats.find(sameChat);

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
    if (status === 13 || status === 8 || status === 10) return { icon: 'checks', read: true };
    if (status === 5) return { icon: 'checks', read: false };
    if (status === 4) return { icon: 'check', read: false };
    return { icon: 'clock', read: false };
  }
  /** A new run of bubbles starts with a tail: other side, other sender, a new day or a long pause. */
  const startsRun = (prev, m) => !prev || prev.fromMe !== m.fromMe || (prev.sender || '') !== (m.sender || '') ||
    dayKey(prev.ts) !== dayKey(m.ts) || m.ts - prev.ts > 10 * 60000;
  const JUMBO = /^(?:\p{Extended_Pictographic}(?:\uFE0F|\u200D\p{Extended_Pictographic}|[\u{1F3FB}-\u{1F3FF}])*\s*){1,3}$/u;
  const order = (a, b) => a.sort - b.sort || a.id - b.id;
  /** Kinds of messages the web cannot show at all; they are left out instead of a placeholder. */
  const shown = (m) => !!(m.text || m.media || TYPE_WORD[m.type] || m.type === 15);
  const sorted = () => Array.from(state.messages.values()).filter(shown).sort(order);
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
    const pattern = /(https?:\/\/[^\s<>"']+)|(^|[\s(])(```|[*_~`])(\S(?:[^\n]*?\S)?)\3(?=$|[\s).,!?:;])/g;
    const tags = { '*': 'b', _: 'i', '~': 's', '`': 'code', '```': 'code' };
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
    const media = m.media, mime = media.mime || '', url = '/api/media/' + m.id + accParam('?');
    if ((m.type === 42 || m.type === 43) && !media.available) return labelled('note', m.type, t('viewOnceGone'));
    if (!media.available) {
      const box = el('div');
      if (m.thumb) {
        const img = el('img', 'pic');
        img.alt = '';
        img.style.cursor = 'default';
        img.src = '/api/thumb/' + m.id + accParam('?');
        box.append(img);
      }
      box.append(labelled('note', TYPE_ICON[m.type] ? m.type : 9, t(TYPE_WORD[m.type] || 'document') + ' · ' + t('notOnPhone')));
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
      if (m.thumb) video.poster = '/api/thumb/' + m.id + accParam('?');
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
    link.append(icon('file'), info);
    return link;
  }

  function messageNode(m, fresh, starts) {
    const node = el('div', 'msg' + (m.fromMe ? ' me' : '') + (fresh ? ' new' : '') + (starts ? ' tail' : ' cont'));
    node.dataset.sig = JSON.stringify(m);
    if (m.sender && !m.fromMe) {
      let h = 0;
      for (const ch of m.sender) h = (h * 31 + ch.codePointAt(0)) | 0;
      h = Math.abs(h) % 360;
      node.classList.add('hued');
      node.style.setProperty('--h', h);
      if (prefs.groupAvatars && starts) {
        const pic = el('div', 'avatar mini', (Array.from(m.sender.trim())[0] || '?').toUpperCase());
        pic.style.background = 'hsl(' + h + ' 42% 44%)';
        pic.setAttribute('aria-hidden', 'true');
        node.append(pic);
      }
    }
    if (prefs.bigEmoji && !m.media && !m.quote && m.text && JUMBO.test(m.text)) node.classList.add('jumbo');
    if (m.sender && starts) node.append(el('div', 'sender', m.sender));
    if (m.quote) node.append(el('div', 'quote', m.quote));
    if (m.type === 15) {
      const note = el('span', 'note');
      note.append(icon('block'), ' ', t('deleted'));
      node.append(note);
    } else {
      if (m.viewOnce && m.media && m.media.available) node.append(labelled('note once', 42, t('viewOnceBadge')));
      if (m.media) node.append(mediaNode(m));
      else if (TYPE_WORD[m.type]) node.append(labelled('note', m.type, t(TYPE_WORD[m.type])));
      if (m.text) {
        const body = el('span', 'body');
        rich(body, m.text);
        node.append(body);
      }
    }
    const meta = el('span', 'meta');
    if (m.deletedAt) {
      // Shown as in WAEX on the phone: the message stays readable, with a small "deleted" mark.
      node.classList.add('revoked');
      const when = new Date(m.deletedAt);
      const mark = el('span', 'revokedmark');
      mark.title = t('deletedBy', { s: when.toLocaleDateString() + ' ' + when.toLocaleTimeString([], clockOpts()) });
      mark.append(icon('block'), ' ', t('deletedShort'));
      meta.append(mark, ' ');
    }
    if (m.starred) meta.append(icon('star'), ' ');
    meta.append(new Date(m.ts).toLocaleTimeString([], clockOpts()));
    if (m.fromMe && m.type !== 15) {
      const tick = ticks(m.status);
      const span = el('span', tick.read ? 'read' : null);
      span.append(icon(tick.icon));
      meta.append(' ', span);
    }
    node.append(meta);
    if (m.reactions) {
      const chip = el('button', 'react' + (m.myReaction ? ' mine' : ''), m.reactions);
      if (m.myReaction) chip.addEventListener('click', () => react(m, ''));
      node.append(chip);
      node.classList.add('reacted');
    }
    if (m.type !== 15) {
      const tools = el('div', 'tools');
      const add = (name, label, action) => {
        const b = el('button');
        b.append(icon(name));
        b.title = label;
        b.setAttribute('aria-label', label);
        b.addEventListener('click', (e) => { e.stopPropagation(); action(e); });
        tools.append(b);
      };
      if (state.canReact) add('emoji', t('react'), () => showReactBar(node, m));
      if (state.canReply && state.canSend) add('reply', t('reply'), () => setReply(m));
      if (state.canDelete) add('trash', t('delete'), () => showDeleteBar(node, m));
      if (m.text) add('copy', t('copy'), () => { navigator.clipboard && navigator.clipboard.writeText(m.text).then(() => toast(t('copied'))); });
      if (tools.children.length) node.append(tools);
      node.addEventListener('click', (e) => {
        if (e.target.closest('a, video, audio, button, img')) return;
        const was = node.classList.contains('sel');
        for (const other of document.querySelectorAll('.msg.sel')) other.classList.remove('sel');
        if (!was && window.matchMedia('(hover: none)').matches) node.classList.add('sel');
      });
      node.addEventListener('dblclick', (e) => {
        if (!state.canReact || e.target.closest('a, video, audio, button')) return;
        e.preventDefault();
        const sel = window.getSelection();
        if (sel) sel.removeAllRanges();
        const emoji = reactEmoji();
        react(m, m.myReaction === emoji ? '' : emoji);
      });
    }
    return node;
  }

  // ─── Reactions and replies ───────────────────────────────────────────
  async function react(m, emoji) {
    const jid = state.current;
    try {
      await api('/api/chats/' + enc(jid) + '/react', { id: m.id, emoji });
      await sleep(350);
      if (state.current === jid) await loadLatest();
    } catch (e) {
      toast(e.code === 'send_failed' ? t('sendProblem', { s: e.data.step || '?' }) : errorText(e));
    }
  }
  function showReactBar(node, m) {
    const open = document.querySelector('.reactbar');
    if (open) open.remove();
    const bar = el('div', 'reactbar');
    for (const emoji of quick()) {
      const b = el('button', m.myReaction === emoji ? 'on' : null, emoji);
      b.addEventListener('click', (e) => { e.stopPropagation(); bar.remove(); react(m, m.myReaction === emoji ? '' : emoji); });
      bar.append(b);
    }
    node.append(bar);
    setTimeout(() => document.addEventListener('click', () => bar.remove(), { once: true }), 0);
  }
  function showDeleteBar(node, m) {
    for (const old of document.querySelectorAll('.deletebar')) old.remove();
    const bar = el('div', 'deletebar');
    const choice = (label, everyone) => {
      const b = el('button', null, label);
      b.type = 'button';
      b.addEventListener('click', async (e) => {
        e.stopPropagation();
        bar.remove();
        try {
          await api('/api/chats/' + enc(state.current) + '/delete', { id: m.id, everyone });
          state.messages.delete(m.id);
          const gone = state.nodes.get(m.id);
          if (gone) gone.remove();
          state.nodes.delete(m.id);
          toast(t('deleted_ok'));
          await sleep(500);
          await loadLatest();
        } catch (err) {
          toast(err.code === 'send_failed' ? t('sendProblem', { s: err.data.step || '?' }) : errorText(err));
        }
      });
      bar.append(b);
    };
    choice(t('deleteForMe'), false);
    if (m.fromMe && Date.now() - m.ts < 2 * 864e5) choice(t('deleteForAll'), true);
    const cancel = el('button', 'cancel', t('cancel'));
    cancel.type = 'button';
    cancel.addEventListener('click', (e) => { e.stopPropagation(); bar.remove(); });
    bar.append(cancel);
    node.append(bar);
  }
  function setReply(m) {
    state.reply = m;
    const bar = $('replyBar');
    bar.hidden = !m;
    if (m) {
      $('replyWho').textContent = m.fromMe ? t('you') : (m.sender || $('chatName').textContent);
      $('replyText').textContent = m.text || t(TYPE_WORD[m.type] || 'document');
      $('text').focus();
    }
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
    let day = '', prev = null;
    for (const m of sorted()) {
      if (dayKey(m.ts) !== day) { day = dayKey(m.ts); box.append(el('div', 'day', dayLabel(m.ts))); prev = null; }
      const node = messageNode(m, false, startsRun(prev, m));
      prev = m;
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
      if (!shown(m)) { if (old) { old.remove(); state.nodes.delete(m.id); } continue; }
      if (old) {
        if (old.dataset.sig !== JSON.stringify(m)) {
          const node = messageNode(m, false, old.classList.contains('tail'));
          old.replaceWith(node);
          state.nodes.set(m.id, node);
        }
      } else if (newest && order(m, newest) < 0) {
        disorder = true;
      } else {
        if (!newest || dayKey(newest.ts) !== dayKey(m.ts)) box.append(el('div', 'day', dayLabel(m.ts)));
        const node = messageNode(m, true, startsRun(newest, m));
        state.nodes.set(m.id, node);
        box.append(node);
        newest = m;
      }
    }
    if (disorder) renderAll();
    if (stick) toBottom();
  }

  const messagesUrl = (jid, query) => '/api/chats/' + enc(jid) + '/messages?limit=60' + (query || '') + accParam('&');
  const draftKey = () => state.current + '|' + state.account;

  async function openChat(jid, hit, account) {
    if (state.current) state.drafts.set(draftKey(), $('text').value);
    state.current = jid;
    state.account = account && account !== state.activeAccount ? account : '';
    state.sendAs = state.account || state.activeAccount;
    $('phoneOpen').hidden = !!state.account;
    drawSendAs();
    state.messages = new Map();
    state.nodes = new Map();
    state.noOlder = false;
    state.detached = !!hit;
    setReply(null);
    const chat = currentChat();
    $('app').classList.add('open');
    $('empty').hidden = true;
    $('chat').hidden = false;
    $('toLatest').hidden = !hit;
    $('chatName').textContent = chat ? chat.name : jid;
    $('chatSub').textContent = chat && chat.group ? t('group') : jid.endsWith('@s.whatsapp.net') ? '+' + jid.split('@')[0] : '';
    avatar($('chatAvatar'), jid, chat ? chat.name : '?', state.account);
    $('messages').textContent = '';
    if (chat && chat.group && prefs.groupAvatars) $('messages').dataset.group = '1'; else delete $('messages').dataset.group;
    $('text').value = state.drafts.get(draftKey()) || '';
    autosize();
    renderChats();
    try {
      const query = hit ? '&beforeSort=' + hit.sort + '&beforeId=' + (hit.id + 1) : '';
      const r = await api(messagesUrl(jid, query));
      if (state.current !== jid) return;
      // Another account's chat is read-only until WhatsApp switches to it.
      const live = !state.account;
      state.canSend = r.canSend;
      state.canReact = live && !!r.canReact;
      state.canReply = live && !!r.canReply;
      state.canDelete = live && !!r.canDelete;
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
    box.style.height = '';
    // An empty or not yet laid out box keeps its natural one-line height.
    if (!box.value || !box.clientWidth) return;
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
    // Sending from an account that is not in use: switch first, then send.
    if (state.sendAs && state.sendAs !== state.activeAccount) {
      if (!window.confirm(t('switchConfirm', { s: accountName(state.sendAs) }))) return;
      state.pending = { jid, text };
      $('text').value = '';
      autosize();
      await switchAccount(state.sendAs, true);
      return;
    }
    sending = true;
    $('send').disabled = true;
    try {
      const body = { text };
      if (state.reply) body.replyTo = state.reply.id;
      const r = await api('/api/chats/' + enc(jid) + '/send', body);
      if (state.current === jid) { $('text').value = ''; autosize(); setReply(null); }
      state.drafts.delete(draftKey());
      if (state.detached) await openChat(jid); else await loadLatest();
      toBottom();
      if (!r.stored) toast(t('queued'));
    } catch (e) {
      if (e.code === 'send_unavailable') { state.canSend = false; showSendNote(); }
      else if (e.code === 'send_failed') toast(t('sendProblem', { s: e.data.step || '?' }));
      else toast(errorText(e));
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
        $('offline').hidden = true;
        $('conn').classList.remove('off');
        $('conn').title = t('online');
        if (u.canSend !== state.canSend) { state.canSend = u.canSend; if (state.current) showSendNote(); }
        if (state.switchingTo) await checkSwitched();
        if (u.rev !== state.rev) {
          state.rev = u.rev;
          await loadChats();
          await loadLatest();
          await sleep(600);
        }
      } catch (e) {
        if (e.status === 401) break;
        if (e.code === 'offline') {
          $('offline').textContent = t(state.switchingTo ? 'switching' : 'reconnecting');
          $('offline').hidden = false;
          $('conn').classList.add('off');
          $('conn').title = t('reconnecting');
        }
        await sleep(3000);
      }
    }
    polling = false;
  }

  // ─── Accounts ────────────────────────────────────────────────────────
  async function loadAccounts() {
    try {
      const r = await api('/api/accounts');
      state.accounts = (r.accounts || []).filter((a) => !a.loggedOut);
      state.merged = !!r.merged;
      const active = state.accounts.find((a) => a.active);
      state.activeAccount = active ? active.id : '';
    } catch (_) {
      state.accounts = [];
    }
    drawAccountButton();
    drawSendAs();
  }
  function drawAccountButton() {
    const button = $('accountButton');
    const active = state.accounts.find((a) => a.active);
    button.hidden = state.accounts.length < 2;
    button.textContent = active ? active.name : t('accounts');
    button.title = t('accounts');
  }
  function showAccountMenu(e) {
    e.stopPropagation();
    const old = document.querySelector('.acctmenu');
    if (old) { old.remove(); return; }
    const menu = el('div', 'acctmenu');
    menu.setAttribute('role', 'menu');
    for (const a of state.accounts) {
      const item = el('button', a.active ? 'on' : null);
      item.type = 'button';
      item.append(el('b', null, a.name), el('small', null, (a.phone || '') + (a.active ? '' : ' · ' + t('notInUse'))));
      item.addEventListener('click', () => {
        menu.remove();
        if (a.active) return;
        if (window.confirm(t('switchConfirm', { s: a.name }))) switchAccount(a.id, false);
      });
      menu.append(item);
    }
    $('side').append(menu);
    setTimeout(() => document.addEventListener('click', () => menu.remove(), { once: true }), 0);
  }
  /** The "Send as" picker above the composer, shown when the phone has several accounts. */
  function drawSendAs() {
    const box = $('sendAs');
    box.textContent = '';
    box.hidden = state.accounts.length < 2 || !state.current;
    if (box.hidden) return;
    const label = el('span', null, t('sendAs') + ':');
    const pick = el('select');
    for (const a of state.accounts) {
      const option = el('option', null, a.name + (a.active ? '' : ' (' + t('notInUse') + ')'));
      option.value = a.id;
      pick.append(option);
    }
    pick.value = state.sendAs || state.activeAccount;
    pick.addEventListener('change', () => { state.sendAs = pick.value; });
    box.append(label, pick);
  }
  let switchTimer = 0;
  async function switchAccount(id, forSending) {
    try {
      await api('/api/accounts/switch', { id });
    } catch (e) {
      state.pending = null;
      toast(errorText(e));
      return;
    }
    state.switchingTo = id;
    if (!forSending) state.pending = null;
    $('offline').textContent = t('switching');
    $('offline').hidden = false;
    clearTimeout(switchTimer);
    switchTimer = setTimeout(() => {
      if (!state.switchingTo) return;
      state.switchingTo = null;
      $('offline').hidden = true;
      if (state.pending) { $('text').value = state.pending.text; autosize(); state.pending = null; }
      toast(t('switchSlow'));
    }, 90000);
  }
  async function checkSwitched() {
    let status;
    try { status = await api('/api/status'); } catch (_) { return; }
    if (!status.signedIn || status.account !== state.switchingTo) return;
    const id = state.switchingTo;
    state.switchingTo = null;
    clearTimeout(switchTimer);
    $('offline').hidden = true;
    await loadAccounts();
    await loadChats();
    toast(t('switched', { s: accountName(id) }));
    const pending = state.pending;
    state.pending = null;
    if (pending) {
      await openChat(pending.jid, null, '');
      $('text').value = pending.text;
      autosize();
      await send();
    } else if (state.current) {
      await openChat(state.current, null, '');
    }
  }

  // ─── Settings ────────────────────────────────────────────────────────
  // Controls update in place; only a language change rebuilds the dialog.
  const refreshers = [];
  const refreshSettings = () => { for (const fn of refreshers) fn(); };
  function setPref(key, value, redraw) {
    prefs[key] = value;
    savePrefs();
    applyPrefs();
    if (redraw) { renderChats(); if (state.current) renderAll(); }
    refreshSettings();
  }
  function field(label, control, hint) {
    const row = el('label', 'field');
    const name = el('span', null, label);
    if (hint) name.append(el('small', null, hint));
    row.append(name, control);
    return row;
  }
  /** A row holding several buttons; a <label> would forward clicks on its text to the first one. */
  function group(label, control, column) {
    const row = el('div', 'field' + (column ? ' col' : ''));
    row.append(el('span', null, label), control);
    return row;
  }
  function select(options, key, redraw) {
    const node = el('select');
    for (const [value, label] of options) {
      const option = el('option', null, label);
      option.value = value;
      node.append(option);
    }
    node.value = prefs[key];
    node.addEventListener('change', () => setPref(key, node.value, redraw));
    refreshers.push(() => { node.value = prefs[key]; });
    return node;
  }
  function slider(label, key, min, max, step, unit, redraw) {
    const row = el('label', 'field');
    const input = el('input');
    input.type = 'range';
    input.min = min; input.max = max; input.step = step;
    const out = el('output');
    const show = () => { input.value = prefs[key]; out.textContent = prefs[key] + ' ' + unit; };
    input.addEventListener('input', () => { setPref(key, +input.value, redraw); });
    refreshers.push(show);
    show();
    row.append(el('span', null, label), input, out);
    return row;
  }
  function toggle(label, key, redraw, hint) {
    const box = el('input');
    box.type = 'checkbox';
    box.addEventListener('change', () => setPref(key, box.checked, redraw));
    refreshers.push(() => { box.checked = !!prefs[key]; });
    box.checked = !!prefs[key];
    return field(label, box, hint);
  }
  function choice(cls, items, key, redraw) {
    const box = el('div', cls);
    box.setAttribute('role', 'group');
    const mark = () => {
      for (const b of box.children) {
        if (!b.dataset.value && b.dataset.value !== '') continue;
        const on = b.dataset.value === String(prefs[key]);
        b.classList.toggle('on', on);
        b.setAttribute('aria-pressed', String(on));
      }
    };
    for (const item of items) {
      const b = item.node || el('button', null, item.label);
      b.type = 'button';
      b.dataset.value = item.value;
      b.addEventListener('click', () => setPref(key, item.value, redraw));
      box.append(b);
    }
    refreshers.push(mark);
    mark();
    return box;
  }
  const seg = (items, key, redraw) => choice('seg', items, key, redraw);
  /** Colour swatches; with `auto`, an "automatic" swatch stores ''. Always ends with a colour picker. */
  function colours(key, presets, auto) {
    const items = [];
    if (auto) {
      const b = el('button', 'swatch auto');
      b.setAttribute('aria-label', t('automatic'));
      b.title = t('automatic');
      items.push({ value: '', node: b });
    }
    for (const c of presets) {
      const b = el('button', 'swatch');
      b.style.background = c;
      b.setAttribute('aria-label', c);
      items.push({ value: c, node: b });
    }
    const box = choice('swatches', items, key);
    const custom = el('input');
    custom.type = 'color';
    custom.setAttribute('aria-label', t('accentCustom'));
    custom.title = t('accentCustom');
    const sync = () => { custom.value = isHex(prefs[key]) ? prefs[key] : (isHex(prefs.accent) ? prefs.accent : DEFAULTS.accent); };
    custom.addEventListener('input', () => setPref(key, custom.value));
    refreshers.push(sync);
    sync();
    box.append(custom);
    return box;
  }
  function previewBox() {
    const chat = el('div', 'chat preview');
    chat.setAttribute('aria-hidden', 'true');
    const box = el('div', 'messages');
    box.dataset.wall = prefs.wall;
    const now = Date.now();
    const time = (ms) => new Date(ms).toLocaleTimeString([], clockOpts());
    const inMsg = el('div', 'msg tail');
    inMsg.append(el('span', 'body', t('previewIn')), el('span', 'meta', time(now - 60000)));
    const outMsg = el('div', 'msg me tail');
    const meta = el('span', 'meta', time(now) + ' ');
    const read = el('span', 'read');
    read.append(icon('checks'));
    meta.append(read);
    outMsg.append(el('span', 'body', t('previewOut')), meta);
    box.append(el('div', 'day', t('today')), inMsg, outMsg);
    chat.append(box);
    return chat;
  }
  function section(id, title) {
    const box = el('section');
    box.id = 'set-' + id;
    box.append(el('h3', null, title));
    const b = el('button', null, title);
    b.type = 'button';
    b.addEventListener('click', () => {
      box.scrollIntoView({ block: 'start', behavior: 'smooth' });
      for (const other of $('settingsNav').children) other.classList.toggle('on', other === b);
    });
    $('settingsNav').append(b);
    return box;
  }

  async function openSettings() {
    const body = $('settingsBody');
    body.textContent = '';
    $('settingsNav').textContent = '';
    refreshers.length = 0;

    const look = section('look', t('appearance'));
    look.append(previewBox());
    const themes = THEMES.map((name) => {
      const b = el('button', 'tile' + (name === 'system' ? ' auto' : ''));
      const thumb = el('i', 'thumb mock');
      thumb.dataset.theme = name === 'system' ? (darkQuery.matches ? 'dark' : 'light') : name;
      b.append(thumb, el('span', null, t(name)));
      return { value: name, node: b };
    });
    look.append(group(t('theme'), choice('tiles', themes, 'theme'), true));
    look.append(group(t('accent'), colours('accent', ACCENTS, false), true));
    look.append(group(t('bubbleOwn'), colours('bubbleOwn', [], true), true));
    look.append(group(t('bubbleOther'), colours('bubbleOther', [], true), true));
    look.append(slider(t('corners'), 'radius', 0, 22, 1, 'px'));
    look.append(toggle(t('tails'), 'tails'));
    look.append(slider(t('fontSize'), 'font', 12, 20, 0.5, 'px'));
    look.append(field(t('fontFamily'), select([['system', t('fontSystem')], ['rounded', t('fontRounded')], ['serif', t('fontSerif')]], 'family')));
    look.append(field(t('motion'), select([['system', t('motionSystem')], ['reduce', t('motionReduce')], ['full', t('motionFull')]], 'motion')));
    const reset = el('button', 'reset', t('resetLook'));
    reset.type = 'button';
    reset.addEventListener('click', () => {
      for (const key of LOOK_KEYS) prefs[key] = Array.isArray(DEFAULTS[key]) ? DEFAULTS[key].slice() : DEFAULTS[key];
      savePrefs();
      applyPrefs();
      renderChats();
      if (state.current) renderAll();
      refreshSettings();
      toast(t('resetDone'));
    });
    look.append(reset);

    const wallSec = section('wall', t('chatBackground'));
    const wallWords = { classic: 'wallDoodles', dots: 'wallDots', grid: 'wallGrid', lines: 'wallLines', waves: 'wallWaves',
      gradient: 'wallGradient', aurora: 'wallAurora', plain: 'wallPlain' };
    const walls = WALLS.map((name) => {
      const b = el('button', 'tile');
      b.dataset.wall = name;
      b.append(el('i', 'thumb wall'), el('span', null, t(wallWords[name])));
      return { value: name, node: b };
    });
    wallSec.append(group(t('wallpaper'), choice('tiles', walls, 'wall'), true));
    wallSec.append(group(t('wallColor'), colours('wallColor', [], true), true));
    const strength = slider(t('wallOpacity'), 'wallOpacity', 0, 200, 10, '%');
    refreshers.push(() => strength.classList.toggle('off', ['gradient', 'aurora', 'plain'].includes(prefs.wall)));
    wallSec.append(strength);
    wallSec.append(slider(t('wallDim'), 'wallDim', 0, 60, 5, '%'));

    const chats = section('chats', t('chatsLook'));
    chats.append(group(t('density'), seg([{ value: 'comfortable', label: t('comfortable') }, { value: 'compact', label: t('compact') }], 'density')));
    chats.append(slider(t('msgWidth'), 'msgWidth', 50, 85, 5, '%'));
    chats.append(toggle(t('groupAvatars'), 'groupAvatars', true));
    chats.append(toggle(t('senderColors'), 'senderColors'));
    chats.append(toggle(t('bigEmoji'), 'bigEmoji', true));
    chats.append(group(t('clock'), seg([{ value: 'auto', label: t('clockAuto') }, { value: '24', label: t('clock24') },
      { value: '12', label: t('clock12') }], 'clock', true)));

    const list = section('list', t('chatList'));
    list.append(group(t('listDensity'), seg([{ value: 'comfortable', label: t('comfortable') }, { value: 'compact', label: t('compact') }], 'list')));
    list.append(toggle(t('listAvatars'), 'listAvatars'));
    list.append(slider(t('sideWidth'), 'sideWidth', 280, 520, 10, 'px'));
    list.append(toggle(t('titleCount'), 'titleCount', true));

    const writing = section('writing', t('writing'));
    writing.append(toggle(t('enterSends'), 'enter', false, t('enterHint')));
    // Quick reactions: pick a slot, then the emoji that goes there.
    const quickRow = el('div', 'field col');
    const slots = el('div', 'quickset');
    const panel = el('div', 'emojipanel');
    panel.hidden = true;
    let slot = -1;
    const drawSlots = () => {
      slots.textContent = '';
      prefs.quick.forEach((emoji, i) => {
        const b = el('button', i === slot ? 'on' : null, emoji);
        b.type = 'button';
        b.setAttribute('aria-pressed', String(i === slot));
        b.addEventListener('click', () => { slot = slot === i ? -1 : i; panel.hidden = slot < 0; drawSlots(); });
        slots.append(b);
      });
    };
    for (const emoji of EMOJI) {
      const b = el('button', null, emoji);
      b.type = 'button';
      b.addEventListener('click', (e) => {
        e.stopPropagation();
        if (slot < 0) return;
        const next = prefs.quick.slice();
        next[slot] = emoji;
        slot = -1;
        panel.hidden = true;
        setPref('quick', next, true);
      });
      panel.append(b);
    }
    refreshers.push(drawSlots);
    drawSlots();
    const quickLabel = el('span', null, t('quickReactions'));
    quickLabel.append(el('small', null, t('quickHint')));
    quickRow.append(quickLabel, slots, panel);
    writing.append(quickRow);
    const doubleBox = el('div', 'quickset');
    const drawDouble = () => {
      doubleBox.textContent = '';
      for (const emoji of prefs.quick) {
        const on = emoji === reactEmoji();
        const b = el('button', on ? 'on' : null, emoji);
        b.type = 'button';
        b.setAttribute('aria-pressed', String(on));
        b.addEventListener('click', () => setPref('react', emoji));
        doubleBox.append(b);
      }
    };
    refreshers.push(drawDouble);
    drawDouble();
    writing.append(group(t('doubleReact'), doubleBox, true));
    const langSelect = el('select');
    for (const [value, label] of [['', t('system')], ['en', 'English'], ['sk', 'Slovenčina']]) {
      const option = el('option', null, label);
      option.value = value;
      langSelect.append(option);
    }
    langSelect.value = prefs.lang;
    langSelect.addEventListener('change', () => {
      prefs.lang = langSelect.value;
      savePrefs();
      applyPrefs();
      applyWords();
      renderChats();
      if (state.current) { renderAll(); showSendNote(); }
      openSettings();
    });
    writing.append(field(t('language'), langSelect));

    body.append(look, wallSec, chats, list, writing);
    const accountSec = section('account', t('account'));
    const account = el('div');
    accountSec.append(account);
    body.append(accountSec);
    $('settingsNav').firstElementChild.classList.add('on');
    applyPrefs();
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
    await loadAccounts();
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
  $('toLatest').addEventListener('click', () => openChat(state.current, null, state.account));
  $('send').addEventListener('click', send);
  $('text').addEventListener('input', autosize);
  $('text').addEventListener('keydown', (e) => {
    if (e.key !== 'Enter' || e.isComposing) return;
    if (prefs.enter ? !e.shiftKey : (e.ctrlKey || e.metaKey)) { e.preventDefault(); send(); }
  });
  $('replyCancel').addEventListener('click', () => setReply(null));
  $('emojiButton').addEventListener('click', (e) => {
    e.stopPropagation();
    const panel = $('emojiPanel');
    if (!panel.children.length) {
      for (const emoji of EMOJI) {
        const b = el('button', null, emoji);
        b.type = 'button';
        b.addEventListener('click', (ev) => {
          ev.stopPropagation();
          const box = $('text'), at = box.selectionStart || box.value.length;
          box.value = box.value.slice(0, at) + emoji + box.value.slice(box.selectionEnd || at);
          box.selectionStart = box.selectionEnd = at + emoji.length;
          box.focus();
          autosize();
        });
        panel.append(b);
      }
    }
    panel.hidden = !panel.hidden;
  });
  document.addEventListener('click', (e) => { if (!e.target.closest('#emojiPanel')) $('emojiPanel').hidden = true; });
  $('openSettings').addEventListener('click', openSettings);
  $('toggleTheme').addEventListener('click', () => {
    prefs.theme = DARK.has(resolvedTheme()) ? 'light' : 'dark';
    savePrefs();
    applyPrefs();
  });
  $('phoneOpen').addEventListener('click', async () => {
    if (!state.current) return;
    try {
      await api('/api/chats/' + enc(state.current) + '/open', { text: '' });
      toast(t('openedPlain'));
    } catch (e) { toast(errorText(e)); }
  });
  $('accountButton').addEventListener('click', showAccountMenu);
  // Drag the edge of the chat list to change its width; double click puts it back.
  const grip = $('sideGrip');
  grip.addEventListener('pointerdown', (e) => {
    grip.setPointerCapture(e.pointerId);
    grip.classList.add('on');
  });
  grip.addEventListener('pointermove', (e) => {
    if (!grip.classList.contains('on')) return;
    prefs.sideWidth = Math.round(clamp(e.clientX - $('side').getBoundingClientRect().left, 280, 520, 380));
    applyPrefs();
  });
  const endDrag = () => { if (grip.classList.contains('on')) { grip.classList.remove('on'); savePrefs(); } };
  grip.addEventListener('pointerup', endDrag);
  grip.addEventListener('pointercancel', endDrag);
  grip.addEventListener('dblclick', () => { prefs.sideWidth = DEFAULTS.sideWidth; savePrefs(); applyPrefs(); });
  $('closeSettings').addEventListener('click', () => $('settings').close());
  $('viewer').addEventListener('click', (e) => { if (e.target !== $('viewerSave')) { $('viewer').hidden = true; $('viewerImg').removeAttribute('src'); } });
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    $('viewer').hidden = true;
    $('emojiPanel').hidden = true;
    if (state.reply) setReply(null);
  });

  applyPrefs();
  applyWords();
  start();
})();
