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
      emptyText: 'Pick a chat on the left to read and write from here.', group: 'Group', you: 'You',
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
      deleteForMe: 'Delete for me', deleteForAll: 'Delete for everyone', deleteSure: 'Delete message?',
      deleted_ok: 'Deleted.', delete_unavailable: 'Deleting does not work with this WhatsApp version.',
      not_your_message: 'Only your own messages can be deleted for everyone.', accounts: 'Accounts', sendAs: 'Send as',
      switchConfirm: 'WhatsApp on the phone switches to {s} and restarts. It takes a few seconds.',
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
      emptyTitle: 'WAEX Web',
      emptyHint: 'Everything stays on your phone and your Wi-Fi.',
      account: 'Account',
      loginTitle: 'Open your chats on this computer',
      how1: 'Press the button below.', how2: 'Your phone shows a notification with a 6-digit code.', how3: 'Type the code here.',
      findInChat: 'Search in this chat', noMatches: 'Nothing found', chatInfo: 'Chat info', media: 'Photos and videos',
      docs: 'Documents', links: 'Links', nothingYet: 'Nothing in the loaded messages.',
      unreadCount: 'Unread messages: {s}', draft: 'Draft', notSent: 'Not sent', retry: 'Try again', download: 'Download',
      more: 'More', privacy: 'Privacy', blur: 'Blur messages', blurHint: 'Text and pictures are blurred until the pointer is on them',
      sound: 'Sound for new messages', switchTitle: 'Switch account?', loadProblem: 'The messages could not be loaded.',
      shortcuts: 'Keyboard shortcuts', scSearch: 'Search chats', scFind: 'Search in the open chat', scNext: 'Next / previous chat',
      scClose: 'Close what is open', recent: 'Recently used', smileys: 'Smileys', people: 'People', nature: 'Animals and nature',
      food: 'Food and drink', activity: 'Activities', travel: 'Travel and places', objects: 'Objects', symbols: 'Symbols',
      speed: 'Playback speed', readMore: 'Read more', showAll: 'All', timeout: 'The phone took too long to answer. Try again.',
      chats: 'Chats', confirmSwitch: 'Switch',
      statusTitle: 'Status', myStatus: 'My status', recentUpdates: 'Recent updates', viewedUpdates: 'Viewed updates',
      noStatuses: 'No status updates from the last 24 hours.', statusesUnavailable: 'Statuses could not be read from the phone.',
      secretView: 'Viewing here does not tell them you saw it.', statusGone: 'Not downloaded on the phone yet.',
      markReadPref: 'Mark chats as read when opened', markReadHint: 'WhatsApp sends blue ticks, unless WAEX hides them.',
      read_unavailable: 'Marking as read does not work with this WhatsApp version.', pause: 'Pause', play: 'Play',
      prevPerson: 'Previous person', nextPerson: 'Next person', updates: '{s} new',
      channels: 'Channels', channel: 'Channel', noChannels: 'You do not follow any channels.', channelReadOnly: 'Only the channel\'s admins can post here.', searchChannels: 'Search channels'
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
      emptyText: 'Vyber si vľavo chat a čítaj aj píš odtiaľto.', group: 'Skupina', you: 'Ty',
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
      deleteForMe: 'Vymazať pre mňa', deleteForAll: 'Vymazať pre všetkých', deleteSure: 'Vymazať správu?',
      deleted_ok: 'Vymazané.', delete_unavailable: 'Mazanie s touto verziou WhatsAppu nefunguje.',
      not_your_message: 'Pre všetkých sa dajú vymazať len tvoje vlastné správy.', accounts: 'Účty', sendAs: 'Odoslať ako',
      switchConfirm: 'WhatsApp v telefóne sa prepne na {s} a reštartuje sa. Trvá to pár sekúnd.',
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
      emptyTitle: 'WAEX Web',
      emptyHint: 'Všetko ostáva v telefóne a na tvojej Wi-Fi.',
      account: 'Účet',
      loginTitle: 'Otvor si chaty na tomto počítači',
      how1: 'Stlač tlačidlo nižšie.', how2: 'Telefón ukáže notifikáciu so 6-miestnym kódom.', how3: 'Kód napíš sem.',
      findInChat: 'Hľadať v tomto chate', noMatches: 'Nič sa nenašlo', chatInfo: 'Informácie o chate', media: 'Fotky a videá',
      docs: 'Dokumenty', links: 'Odkazy', nothingYet: 'V načítaných správach nič nie je.',
      unreadCount: 'Neprečítané správy: {s}', draft: 'Koncept', notSent: 'Neodoslané', retry: 'Skúsiť znova', download: 'Stiahnuť',
      more: 'Viac', privacy: 'Súkromie', blur: 'Rozmazať správy', blurHint: 'Text a obrázky sú rozmazané, kým na ne neprídeš myšou',
      sound: 'Zvuk pri novej správe', switchTitle: 'Prepnúť účet?', loadProblem: 'Správy sa nepodarilo načítať.',
      shortcuts: 'Klávesové skratky', scSearch: 'Hľadať v chatoch', scFind: 'Hľadať v otvorenom chate', scNext: 'Ďalší / predošlý chat',
      scClose: 'Zavrieť, čo je otvorené', recent: 'Naposledy použité', smileys: 'Smajlíky', people: 'Ľudia', nature: 'Zvieratá a príroda',
      food: 'Jedlo a pitie', activity: 'Aktivity', travel: 'Cestovanie a miesta', objects: 'Predmety', symbols: 'Symboly',
      speed: 'Rýchlosť prehrávania', readMore: 'Čítať ďalej', showAll: 'Všetko', timeout: 'Telefón odpovedal príliš dlho. Skús to znova.',
      chats: 'Chaty', confirmSwitch: 'Prepnúť',
      statusTitle: 'Statusy', myStatus: 'Môj status', recentUpdates: 'Nové', viewedUpdates: 'Pozreté',
      noStatuses: 'Za posledných 24 hodín nikto nepridal status.', statusesUnavailable: 'Statusy sa z telefónu nepodarilo načítať.',
      secretView: 'Keď si ich pozrieš tu, nedozvedia sa to.', statusGone: 'V telefóne ešte nie je stiahnutý.',
      markReadPref: 'Označiť chat ako prečítaný po otvorení', markReadHint: 'WhatsApp pošle modré fajky, ak ich WAEX neskrýva.',
      read_unavailable: 'Označovanie ako prečítané s touto verziou WhatsAppu nefunguje.', pause: 'Pozastaviť', play: 'Prehrať',
      prevPerson: 'Predošlá osoba', nextPerson: 'Ďalšia osoba', updates: 'Nové: {s}',
      channels: 'Kanály', channel: 'Kanál', noChannels: 'Nesleduješ žiadne kanály.', channelReadOnly: 'Písať sem môžu len správcovia kanála.', searchChannels: 'Hľadať v kanáloch'
    }
  };

  // ─── Look and feel, kept in this browser only ────────────────────────
  const DEFAULTS = {
    theme: 'system', accent: '#1daa61', bubbleOwn: '', bubbleOther: '',
    wall: 'classic', wallColor: '', wallOpacity: 100, wallDim: 0,
    font: 14.2, family: 'system', radius: 7.5, tails: true,
    density: 'comfortable', list: 'comfortable', listAvatars: true,
    groupAvatars: true, senderColors: true, bigEmoji: true, msgWidth: 65, sideWidth: 400, blur: false, sound: false, markRead: true, v: 2,
    clock: 'auto', motion: 'system', enter: true, react: '❤️',
    quick: ['👍', '❤️', '😂', '😮', '😢', '🙏'], titleCount: true, lang: ''
  };
  /** Prefs that only change the look; "Reset look" puts these back. */
  const LOOK_KEYS = ['theme', 'accent', 'bubbleOwn', 'bubbleOther', 'wall', 'wallColor', 'wallOpacity', 'wallDim', 'font', 'family',
    'radius', 'tails', 'density', 'list', 'listAvatars', 'groupAvatars', 'senderColors', 'bigEmoji', 'msgWidth', 'sideWidth', 'motion', 'blur'];
  const EMOJI_SETS = [
    ['smileys', '😀', '😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾'],
    ['people', '👋', '👋 🤚 🖐️ ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦵 🦶 👂 👃 🧠 👀 👁️ 👅 👄 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🙇 🤦 🤷 👮 🕵️ 💂 👷 🤴 👸 🤵 👰 🤰 👼 🎅 🦸 🧙 🧟 💆 💇 🚶 🏃 💃 🕺 👫 👪 🫶 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝'],
    ['nature', '🐶', '🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🕷️ 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦏 🐪 🦒 🐃 🐄 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🕊️ 🐇 🐁 🐿️ 🦔 🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍃 🍂 🍁 🍄 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌚 🌙 ⭐ 🌟 ✨ ⚡ 🔥 💥 ☄️ ☀️ 🌤️ ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ 🌈 💧 🌊'],
    ['food', '🍕', '🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🍞 🥖 🥨 🧀 🥚 🍳 🥞 🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 ☕ 🍵 🧃 🥤 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🧉 🍾 🧊 🥄 🍴 🍽️'],
    ['activity', '⚽', '⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🏓 🏸 🏒 🏑 🥍 🏏 🥅 ⛳ 🏹 🎣 🥊 🥋 🎽 🛹 🛷 ⛸️ 🥌 🎿 ⛷️ 🏂 🏋️ 🤼 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖️ 🎗️ 🎫 🎟️ 🎪 🤹 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 ♟️ 🎯 🎳 🎮 🎰 🧩 🎉 🎊 🎈 🎁 🎀'],
    ['travel', '🚗', '🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🛵 🏍️ 🚲 🛴 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 💺 🚀 🛸 🚁 🛶 ⛵ 🚤 🛳️ ⛴️ 🚢 ⚓ ⛽ 🚧 🚦 🚥 🗺️ 🗿 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️ 🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏔️ 🗻 🏕️ ⛺ 🏠 🏡 🏘️ 🏚️ 🏗️ 🏭 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫 🏩 💒 🏛️ ⛪ 🕌 🕍 🌍 🌎 🌏'],
    ['objects', '💡', '⌚ 📱 📲 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💽 💾 💿 📀 📷 📸 📹 🎥 📞 ☎️ 📟 📠 📺 📻 🎙️ ⏱️ ⏲️ ⏰ 🕰️ ⌛ ⏳ 📡 🔋 🔌 💡 🔦 🕯️ 🧯 💸 💵 💴 💶 💷 💰 💳 💎 ⚖️ 🧰 🔧 🔨 ⚒️ 🛠️ ⛏️ 🔩 ⚙️ 🧱 🧲 🔫 💣 🔪 🗡️ 🛡️ 🚬 ⚰️ 🏺 🔮 📿 💈 ⚗️ 🔭 🔬 🩹 🩺 💊 💉 🧬 🧹 🧺 🧻 🚽 🚿 🛁 🧼 🧽 🔑 🗝️ 🚪 🛋️ 🛏️ 🧸 🖼️ 🛍️ 🛒 🎁 ✉️ 📩 📨 📧 💌 📦 📜 📃 📄 📊 📈 📉 📆 📅 📇 📋 📁 📂 📰 📓 📔 📒 📕 📗 📘 📙 📚 📖 🔖 📎 🖇️ 📐 📏 📌 📍 ✂️ 🖊️ 🖋️ ✒️ 🖌️ 🖍️ 📝 ✏️ 🔍 🔎 🔒 🔓'],
    ['symbols', '✅', '✅ ❌ ❓ ❗ ‼️ ⁉️ 💯 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟥 🟧 🟨 🟩 🟦 🟪 ⬛ ⬜ 🔶 🔷 🔺 🔻 💠 🔘 ✔️ ☑️ ➕ ➖ ➗ ✖️ ♾️ 💲 ™️ ©️ ®️ 〰️ ➰ ➿ 🔚 🔙 🔛 🔝 🔜 ⬆️ ↗️ ➡️ ↘️ ⬇️ ↙️ ⬅️ ↖️ ↕️ ↔️ ↩️ ↪️ ⤴️ ⤵️ 🔃 🔄 🔀 🔁 🔂 ▶️ ⏩ ⏭️ ⏯️ ◀️ ⏪ ⏮️ 🔼 ⏫ 🔽 ⏬ ⏸️ ⏹️ ⏺️ 🎦 🔅 🔆 📶 📳 📴 ♻️ ⚠️ 🚸 ⛔ 🚫 🚳 🚭 🚯 🚱 🚷 📵 🔞 ☢️ ☣️ 🆗 🆙 🆒 🆕 🆓 🔟 🔢 #️⃣ *️⃣ ℹ️ 🔤 🔡 🔠 🆘 💤 💢 💬 💭 🗯️ ♠️ ♣️ ♥️ ♦️ 🃏 🎴 🀄 🔔 🔕 🎵 🎶 🏁 🚩 🏳️ 🏴 🏳️‍🌈']
  ].map(([id, tab, list]) => ({ id, tab, list: list.split(' ') }));
  const EMOJI = EMOJI_SETS.slice(0, 2).flatMap((set) => set.list).concat(EMOJI_SETS[7].list.slice(0, 5), ['🔥', '✨', '🎉', '🎂', '🎁', '☕', '🍕', '🍺', '⚽']);
  const ACCENTS = ['#1daa61', '#0b7f63', '#0c7a86', '#1a6fd6', '#5257d6', '#7e4fd0', '#c2336a', '#c74a2f', '#a66a10', '#56697a'];
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
  // Version 2 brought the new look: values that were simply the old defaults move to the new ones.
  let stored = {};
  try { stored = JSON.parse(localStorage.getItem('waex_prefs') || '{}'); } catch (_) { /* private mode */ }
  if (stored.v !== 2) {
    if (prefs.accent === '#0b7f63') prefs.accent = DEFAULTS.accent;
    if (prefs.font === 14.5) prefs.font = DEFAULTS.font;
    if (prefs.radius === 10) prefs.radius = DEFAULTS.radius;
    if (prefs.sideWidth === 380) prefs.sideWidth = DEFAULTS.sideWidth;
    prefs.v = 2;
  }
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
    set('--on-accent', !dark && contrast(accent, '#ffffff') >= 2.6 ? '#ffffff' : best(accent, '#ffffff', '#0b1014'));
    // The accent used as text must stay readable on the theme's surfaces.
    const surface = getComputedStyle(root).getPropertyValue('--panel3').trim() || (dark ? '#24323a' : '#e6eaec');
    let ink = mix(accent, dark ? '#ffffff' : '#000000', dark ? 0.55 : 0.74);
    for (let i = 0; i < 12 && isHex(surface) && contrast(ink, surface) < 4.5; i++) ink = mix(ink, dark ? '#ffffff' : '#000000', 0.85);
    set('--accent-text-custom', ink);

    // A custom accent also tints the own bubbles, unless they have their own colour.
    const tinted = accent.toLowerCase() === DEFAULTS.accent ? null : mix(accent, dark ? '#161717' : '#ffffff', dark ? 0.36 : 0.22);
    const own = isHex(prefs.bubbleOwn) ? prefs.bubbleOwn : tinted, other = isHex(prefs.bubbleOther) ? prefs.bubbleOther : null;
    set('--out-custom', own); set('--out-text-custom', own && best(own, '#121b21', '#f1f4f6'));
    set('--in-custom', other); set('--in-text-custom', other && best(other, '#121b21', '#f1f4f6'));
    const wall = isHex(prefs.wallColor) ? prefs.wallColor : null;
    set('--wall-custom', wall); set('--wall-ink-custom', wall && best(wall, '#000000', '#ffffff'));
    set('--wall-opacity', String(clamp(prefs.wallOpacity, 0, 200, 100) / 100));
    set('--wall-dim', String(clamp(prefs.wallDim, 0, 60, 0) / 100));

    set('--font', clamp(prefs.font, 12, 20, 14.2) + 'px');
    set('--radius', clamp(prefs.radius, 0, 22, 7.5) + 'px');
    set('--msg-max', clamp(prefs.msgWidth, 50, 85, 65) + '%');
    set('--side-w', clamp(prefs.sideWidth, 300, 560, 400) + 'px');
    set('--family', FAMILIES[prefs.family] || null);

    flag('density', prefs.density === 'compact' ? 'compact' : null);
    flag('list', prefs.list === 'compact' ? 'compact' : null);
    flag('tails', prefs.tails ? null : 'off');
    flag('listAvatars', prefs.listAvatars ? null : 'off');
    flag('senderColors', prefs.senderColors ? null : 'off');
    flag('blur', prefs.blur ? 'on' : null);
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
    $('loginTitle').textContent = t('loginTitle');
    $('how1').textContent = t('how1');
    $('how2').textContent = t('how2');
    $('how3').textContent = t('how3');
    $('askCode').textContent = t('askCode');
    $('codeLabel').textContent = t('codeLabel');
    $('passwordLabel').textContent = t('passwordLabel');
    $('confirmCode').textContent = t('confirm');
    $('newCode').textContent = t('newCode');
    $('loginNote').lastElementChild.textContent = t('loginNote');
    $('search').placeholder = t('search');
    $('emptyTitle').textContent = t('emptyTitle');
    $('emptyText').textContent = t('emptyText');
    $('text').placeholder = t('typeHere');
    $('findText').placeholder = t('findInChat');
    $('settingsTitle').textContent = t('settings');
    $('infoTitle').textContent = t('chatInfo');
    const label = (id, key) => { $(id).title = t(key); $(id).setAttribute('aria-label', t(key)); };
    label('back', 'back'); label('closeSettings', 'close'); label('toLatest', 'toLatest');
    label('openSettings', 'settings'); label('toggleTheme', 'switchTheme'); label('phoneOpen', 'openPhone');
    label('findOpen', 'findInChat'); label('findClose', 'close'); label('infoOpen', 'chatInfo'); label('infoClose', 'close');
    label('emojiButton', 'emoji'); label('replyCancel', 'cancel'); label('railChats', 'chats'); label('accountButton', 'accounts');
    label('viewerClose', 'close'); label('viewerSave', 'download'); label('viewerPrev', 'back'); label('send', 'typeHere');
    label('chatHead', 'chatInfo'); label('railStatus', 'statusTitle'); label('railChannels', 'channels'); label('statusBack', 'back');
    label('storyClose', 'close'); label('storySave', 'download'); label('storyPause', 'pause');
    label('storyPrevPerson', 'prevPerson'); label('storyNextPerson', 'nextPerson');
    $('statusTitle').textContent = t('statusTitle');
    $('statusHint').lastElementChild.textContent = t('secretView');
    $('emptyHint').lastElementChild.textContent = t('emptyHint');
    $('emojiPanel').textContent = '';
    renderTabs();
  }
  function renderTabs() {
    const unread = state.chats.filter((c) => c.unread > 0 && !c.archived && !c.channel).length;
    for (const b of $('tabs').children) {
      b.textContent = t(b.dataset.tab);
      if (b.dataset.tab === 'unread' && unread > 0) b.append(el('span', 'count', String(unread)));
    }
  }

  // ─── Talking to the phone ────────────────────────────────────────────
  class ApiError extends Error {
    constructor(code, status, data) { super(code); this.code = code; this.status = status; this.data = data || {}; }
  }
  async function api(path, body, timeout) {
    const options = { credentials: 'same-origin', cache: 'no-store' };
    if (body !== undefined) {
      options.method = 'POST';
      options.headers = { 'Content-Type': 'application/json', 'X-WAEX': '1' };
      options.body = JSON.stringify(body);
    }
    // A sleeping phone can leave a request hanging forever; give up after a while instead.
    const stop = new AbortController();
    options.signal = stop.signal;
    let late = false;
    const timer = setTimeout(() => { late = true; stop.abort(); }, timeout || 30000);
    let response, data = {};
    try {
      response = await fetch(path, options);
      try { data = await response.json(); } catch (_) { /* empty body */ }
    } catch (_) {
      throw new ApiError(late ? 'timeout' : 'offline', 0);
    } finally {
      clearTimeout(timer);
    }
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

  /** A small question in the middle of the page. Resolves with the value of the chosen button, or null. */
  function ask(title, text, buttons) {
    return new Promise((done) => {
      const dialog = $('ask');
      $('askTitle').textContent = title || '';
      $('askText').textContent = text || '';
      $('askText').hidden = !text;
      const box = $('askButtons');
      box.textContent = '';
      let answer = null;
      for (const b of buttons) {
        const node = el('button', b.kind || null, b.label);
        node.type = 'button';
        node.addEventListener('click', () => { answer = b.value; dialog.close(); });
        box.append(node);
      }
      dialog.onclose = () => done(answer);
      if (dialog.open) dialog.close();
      dialog.showModal();
      (box.querySelector('.go') || box.lastElementChild).focus();
    });
  }
  const confirmAsk = (title, text, yes, danger) => ask(title, text,
    [{ label: t('cancel'), value: null }, { label: yes, value: true, kind: 'go' + (danger ? ' danger' : '') }]);

  /** One floating menu at a time, placed next to `anchor` (or at a point) and kept inside the window. */
  function closeMenu() {
    const menu = $('menu');
    if (menu.hidden) return;
    menu.hidden = true;
    menu.textContent = '';
    for (const node of document.querySelectorAll('.msg.sel')) node.classList.remove('sel');
  }
  function showMenu(anchor, fill, point) {
    const menu = $('menu');
    closeMenu();
    fill(menu);
    if (!menu.children.length) return;
    menu.hidden = false;
    const r = point ? { left: point.x, right: point.x, top: point.y, bottom: point.y } : anchor.getBoundingClientRect();
    const w = menu.offsetWidth, h = menu.offsetHeight;
    let x = r.left, y = r.bottom + 4, ox = '0%', oy = '0%';
    if (x + w > window.innerWidth - 8) { x = Math.max(8, r.right - w); ox = '100%'; }
    if (y + h > window.innerHeight - 8) { y = r.top - h - 4; oy = '100%'; }
    y = Math.max(8, Math.min(y, window.innerHeight - h - 8));
    menu.style.left = x + 'px';
    menu.style.top = y + 'px';
    menu.style.setProperty('--ox', ox);
    menu.style.setProperty('--oy', oy);
  }
  function menuItem(menu, iconName, label, action, cls, small) {
    const b = el('button', cls || null);
    b.type = 'button';
    b.setAttribute('role', 'menuitem');
    if (iconName) b.append(icon(iconName));
    const text = el('span', null, label);
    if (small) text.append(el('small', null, small));
    b.append(text);
    b.addEventListener('click', (e) => { e.stopPropagation(); closeMenu(); action(); });
    menu.append(b);
    return b;
  }
  /** The clipboard API needs HTTPS; on the phone's plain address the old way still works. */
  async function copyText(text) {
    try {
      if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(text);
      else {
        const box = el('textarea');
        box.value = text;
        box.style.cssText = 'position:fixed;opacity:0;left:-999px';
        document.body.append(box);
        box.select();
        document.execCommand('copy');
        box.remove();
      }
      toast(t('copied'));
    } catch (_) { toast(t('failed')); }
  }

  const find = { q: '', hits: [], at: -1, remote: [] };

  // ─── State ───────────────────────────────────────────────────────────
  const state = {
    signedIn: false, canSend: false, canReact: false, canReply: false, canDelete: false, reply: null, rev: null, chats: [], tab: 'all', query: '', hits: [],
    current: null, messages: new Map(), nodes: new Map(), noOlder: false, detached: false, drafts: new Map(),
    pairId: null, noAvatar: new Set(), unfolded: new Set(), loaded: false, canMarkRead: false, statuses: [], statusesOk: true, view: 'chats', unreadFrom: null, unreadCount: 0, newBelow: 0, loadingOlder: false, lastUnread: -1,
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
    $('loginError').textContent = '';
    if ($('settings').open) $('settings').close();
    if ($('ask').open) $('ask').close();
    closeMenu();
    closeViewer();
    if (!$('story').hidden) closeStory();
    showView('chats');
    document.title = 'WAEX Web';
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
    // The letter shows first; the picture covers it once it has loaded, so nothing flickers or looks broken.
    node.textContent = letter;
    if (state.noAvatar.has(jid)) return;
    const img = el('img');
    img.alt = '';
    img.loading = 'lazy';
    img.addEventListener('load', () => img.classList.add('ok'));
    img.addEventListener('error', () => { state.noAvatar.add(jid); img.remove(); });
    img.src = '/api/avatar/' + enc(jid) + accParam('?', account || '');
    node.append(img);
  }
  function preview(node, chat) {
    const draft = sameChat(chat) ? '' : (state.drafts.get(draftKeyOf(chat)) || '').trim();
    if (draft) {
      node.append(el('span', 'draft', t('draft') + ': '), draft);
      return node;
    }
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
    const focused = document.activeElement && document.activeElement.closest('#chats .row');
    const focusKey = focused ? focused.dataset.key : null;
    box.textContent = '';
    if (!state.loaded) {
      for (let i = 0; i < 9; i++) {
        const row = el('div', 'skel');
        const lines = el('span');
        lines.append(el('b'), el('u'));
        row.append(el('i'), lines);
        box.append(row);
      }
      return;
    }
    const q = state.query.toLowerCase();
    const channels = state.view === 'channels';
    const list = state.chats.filter((c) => {
      if (!!c.channel !== channels) return false;
      if (q) return c.name.toLowerCase().includes(q) || c.jid.includes(q);
      if (channels) return true;
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
      row.dataset.key = draftKeyOf(chat);
      const pic = el('div', 'avatar');
      avatar(pic, chat.jid, chat.name, chat.account || '');
      const main = el('div', 'grow');
      const top = el('div', 'top');
      const name = el('div', 'name grow', chat.name);
      // In the merged inbox only chats of the other accounts are marked; the rest belong to the one in use.
      if (state.merged && chat.account && chat.account !== state.activeAccount) name.append(el('span', 'acct idle', chat.accountName || ''));
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
    if (!box.children.length) box.append(el('div', 'section muted', t(channels ? 'noChannels' : 'noChats')));
    box.scrollTop = keep;
    if (focusKey) for (const row of box.querySelectorAll('.row')) if (row.dataset.key === focusKey) { row.focus({ preventScroll: true }); break; }
    const unread = state.chats.filter((c) => c.unread > 0 && !c.archived && !c.channel).length;
    document.title = (unread && prefs.titleCount ? '(' + unread + ') ' : '') + 'WAEX Web';
    $('railCount').hidden = !unread;
    $('railCount').textContent = unread > 99 ? '99+' : String(unread);
    drawFavicon(unread);
    renderTabs();
  }
  /** The tab icon: a green speech bubble, with a red dot while something is unread. */
  let faviconState = null;
  function drawFavicon(unread) {
    const key = (unread ? 1 : 0) + prefs.accent;
    if (faviconState === key) return;
    faviconState = key;
    try {
      const c = document.createElement('canvas');
      c.width = c.height = 64;
      const g = c.getContext('2d');
      g.fillStyle = isHex(prefs.accent) ? prefs.accent : DEFAULTS.accent;
      g.beginPath();
      g.arc(32, 30, 26, 0, Math.PI * 2);
      g.fill();
      g.beginPath();
      g.moveTo(10, 44); g.lineTo(6, 60); g.lineTo(24, 54);
      g.fill();
      g.fillStyle = '#fff';
      g.fillRect(19, 22, 26, 5);
      g.fillRect(19, 33, 17, 5);
      if (unread) {
        g.fillStyle = '#fff';
        g.beginPath(); g.arc(49, 15, 15, 0, Math.PI * 2); g.fill();
        g.fillStyle = '#e5484d';
        g.beginPath(); g.arc(49, 15, 11, 0, Math.PI * 2); g.fill();
      }
      let link = document.querySelector('link[rel="icon"]');
      if (!link) { link = el('link'); link.rel = 'icon'; document.head.append(link); }
      link.href = c.toDataURL('image/png');
    } catch (_) { /* no canvas: keep the default icon */ }
  }
  /** A short two-tone sound, made here so nothing is downloaded. Browsers allow it after the first click. */
  let audioCtx = null;
  function beep() {
    try {
      audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
      const now = audioCtx.currentTime;
      [[880, 0], [1175, 0.11]].forEach(([freq, at]) => {
        const osc = audioCtx.createOscillator(), gain = audioCtx.createGain();
        osc.type = 'sine';
        osc.frequency.value = freq;
        gain.gain.setValueAtTime(0.0001, now + at);
        gain.gain.exponentialRampToValueAtTime(0.12, now + at + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + at + 0.22);
        osc.connect(gain).connect(audioCtx.destination);
        osc.start(now + at);
        osc.stop(now + at + 0.25);
      });
    } catch (_) { /* no sound */ }
  }
  async function loadChats() {
    state.chats = (await api('/api/chats')).chats;
    state.loaded = true;
    const total = state.chats.reduce((n, c) => n + (c.archived || c.channel ? 0 : c.unread || 0), 0);
    $('railChannelDot').hidden = !state.chats.some((c) => c.channel && c.unread > 0 && !c.muted);
    if (prefs.sound && state.lastUnread >= 0 && total > state.lastUnread) beep();
    state.lastUnread = total;
    renderChats();
    const chat = currentChat();
    if (chat) $('chatName').textContent = chat.name;
  }
  const sameChat = (c) => c.jid === state.current && ((c.account && c.account !== state.activeAccount) ? c.account : '') === state.account;
  const currentChat = () => state.chats.find(sameChat);
  const draftKeyOf = (c) => c.jid + '|' + (c.account && c.account !== state.activeAccount ? c.account : '');
  function saveDrafts() {
    try {
      const keep = Array.from(state.drafts.entries()).filter(([, text]) => text && text.trim()).slice(-40);
      sessionStorage.setItem('waex_drafts', JSON.stringify(keep));
    } catch (_) { /* private mode */ }
  }
  try { for (const [k, v] of JSON.parse(sessionStorage.getItem('waex_drafts') || '[]')) state.drafts.set(k, v); } catch (_) { /* none */ }

  let searchTimer = 0;
  function onSearch() {
    state.query = $('search').value.trim();
    state.hits = [];
    renderChats();
    clearTimeout(searchTimer);
    const q = state.query;
    if (q.length < 2 || state.view === 'channels') return;
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

  const mediaUrl = (m) => '/api/media/' + m.id + accParam('?');
  const isPicture = (m) => /^image\/(jpeg|png|webp|gif)$/.test((m.media && m.media.mime) || '');
  const isVideo = (m) => ((m.media && m.media.mime) || '').startsWith('video/');
  const clock = (sec) => { sec = Math.max(0, Math.round(sec || 0)); return Math.floor(sec / 60) + ':' + String(sec % 60).padStart(2, '0'); };

  /** Voice messages and audio files: play button, a line to drag, time and speed. One plays at a time. */
  let playing = null;
  function voiceNode(m, url) {
    const media = m.media;
    const known = media.duration > 36000 ? media.duration / 1000 : media.duration || 0;
    const box = el('div', 'voice');
    const audio = new Audio();
    audio.preload = 'none';
    const play = el('button', 'play');
    play.type = 'button';
    play.append(icon('play'));
    const track = el('div', 'track');
    const range = el('input');
    range.type = 'range'; range.min = 0; range.max = 1000; range.value = 0;
    range.setAttribute('aria-label', t(media.voice ? 'voice' : 'audio'));
    const time = el('span', 'time', clock(known));
    track.append(range, time);
    const speeds = [1, 1.5, 2];
    const speed = el('button', 'speed', '1×');
    speed.type = 'button';
    speed.title = t('speed');
    const total = () => (Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration : known);
    const show = () => {
      const d = total(), p = d ? Math.min(1, audio.currentTime / d) : 0;
      range.value = Math.round(p * 1000);
      range.style.setProperty('--p', (p * 100) + '%');
      time.textContent = clock(audio.paused && !audio.currentTime ? d : audio.currentTime);
    };
    const setIcon = () => { play.textContent = ''; play.append(icon(audio.paused ? 'play' : 'pause')); };
    play.addEventListener('click', (e) => {
      e.stopPropagation();
      if (!audio.src) audio.src = url;
      if (audio.paused) {
        if (playing && playing !== audio) playing.pause();
        playing = audio;
        audio.play().catch(() => toast(t('failed')));
      } else audio.pause();
    });
    speed.addEventListener('click', (e) => {
      e.stopPropagation();
      const next = speeds[(speeds.indexOf(audio.playbackRate) + 1) % speeds.length];
      audio.playbackRate = next;
      speed.textContent = String(next).replace('.', ',') + '×';
    });
    range.addEventListener('input', () => {
      if (!audio.src) audio.src = url;
      const d = total();
      if (d) audio.currentTime = (range.value / 1000) * d;
      show();
    });
    for (const name of ['timeupdate', 'loadedmetadata', 'ended', 'seeked']) audio.addEventListener(name, show);
    for (const name of ['play', 'pause', 'ended']) audio.addEventListener(name, setIcon);
    audio.addEventListener('ended', () => { audio.currentTime = 0; show(); });
    if (media.voice) {
      const mic = el('span', 'mic');
      mic.append(icon('mic'));
      box.append(m.fromMe ? mic : '', play, track, speed, m.fromMe ? '' : mic);
    } else box.append(play, track, speed);
    return box;
  }

  function mediaNode(m) {
    const media = m.media, mime = media.mime || '', url = mediaUrl(m);
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
      img.addEventListener('click', () => openViewer(m));
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
    if (mime.startsWith('audio/')) return voiceNode(m, url);
    const link = el('a', 'file');
    link.href = url;
    link.download = media.name || 'file';
    const info = el('span');
    info.append(el('b', null, media.name || t('document')), [size(media.size), (mime.split('/')[1] || '').toUpperCase().slice(0, 8)].filter(Boolean).join(' · '));
    const arrow = icon('download');
    arrow.classList.add('dl');
    link.append(icon('file'), info, arrow);
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
        // Very long messages start folded, like in WhatsApp.
        const long = m.text.length > 1400 && !state.unfolded.has(m.id) && !find.q;
        rich(body, long ? m.text.slice(0, 900).replace(/\s+\S*$/, '') + '… ' : m.text);
        if (long) {
          const moreButton = el('button', 'readmore', t('readMore'));
          moreButton.type = 'button';
          moreButton.addEventListener('click', (e) => { e.stopPropagation(); state.unfolded.add(m.id); redraw(m); });
          body.append(moreButton);
        }
        if (find.q) highlight(body, find.q);
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
      mark.append(icon('block'), t('deletedShort'));
      meta.append(mark);
    }
    if (m.starred) meta.append(icon('star'));
    meta.append(new Date(m.ts).toLocaleTimeString([], clockOpts()));
    if (m.failed) {
      node.classList.add('failed');
      meta.textContent = '';
      const again = el('button', null, t('retry'));
      again.type = 'button';
      again.addEventListener('click', (e) => { e.stopPropagation(); retryLocal(m); });
      meta.append(icon('alert'), t('notSent'), again);
    } else if (m.fromMe && m.type !== 15) {
      const tick = ticks(m.status);
      const span = el('span', tick.read ? 'read' : null);
      span.append(icon(tick.icon));
      meta.append(span);
    }
    if (m.local && !m.failed) node.classList.add('pending');
    node.append(meta);
    if (m.reactions) {
      const chip = el('button', 'react' + (m.myReaction ? ' mine' : ''), m.reactions);
      if (m.myReaction) chip.addEventListener('click', () => react(m, ''));
      node.append(chip);
      node.classList.add('reacted');
    }
    if (m.type !== 15) {
      const chev = el('button', 'chev');
      chev.type = 'button';
      chev.title = t('more');
      chev.setAttribute('aria-label', t('more'));
      chev.append(icon('down'));
      chev.addEventListener('click', (e) => { e.stopPropagation(); showMessageMenu(node, m); });
      node.append(chev);
      if (state.canReact && !m.local) {
        const quickReact = el('button', 'sidereact');
        quickReact.type = 'button';
        quickReact.title = t('react');
        quickReact.setAttribute('aria-label', t('react'));
        quickReact.append(icon('emoji'));
        quickReact.addEventListener('click', (e) => { e.stopPropagation(); showMessageMenu(node, m, null, true); });
        node.append(quickReact);
      }
      node.addEventListener('contextmenu', (e) => {
        // The browser's own menu stays for links and selected text.
        if (e.target.closest('a') || String(window.getSelection() || '')) return;
        e.preventDefault();
        showMessageMenu(node, m, { x: e.clientX, y: e.clientY });
      });
      node.addEventListener('click', (e) => {
        if (!window.matchMedia('(hover: none)').matches || e.target.closest('a, video, audio, button, img, input')) return;
        showMessageMenu(node, m, { x: e.clientX, y: e.clientY });
      });
      node.addEventListener('dblclick', (e) => {
        if (!state.canReact || m.local || e.target.closest('a, video, audio, button, input')) return;
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
  /** The menu of one message: quick reactions on top, then what can be done with it. */
  function showMessageMenu(node, m, point, reactionsOnly) {
    const anchor = node.querySelector(reactionsOnly ? '.sidereact' : '.chev') || node;
    showMenu(anchor, (menu) => {
      node.classList.add('sel');
      if (state.canReact && !m.local) {
        const row = el('div', 'reactrow');
        for (const emoji of quick()) {
          const b = el('button', m.myReaction === emoji ? 'on' : null, emoji);
          b.type = 'button';
          b.addEventListener('click', (e) => { e.stopPropagation(); closeMenu(); react(m, m.myReaction === emoji ? '' : emoji); });
          row.append(b);
        }
        menu.append(row);
        if (reactionsOnly) { row.style.border = '0'; row.style.margin = '0'; row.style.paddingBottom = '2px'; return; }
      }
      if (state.canReply && state.canSend && !m.local) menuItem(menu, 'reply', t('reply'), () => setReply(m));
      if (m.text) menuItem(menu, 'copy', t('copy'), () => copyText(m.text));
      if (m.media && m.media.available) menuItem(menu, 'download', t('download'), () => {
        const a = el('a');
        a.href = mediaUrl(m);
        a.download = m.media.name || '';
        document.body.append(a);
        a.click();
        a.remove();
      });
      if (m.local) menuItem(menu, 'trash', t('delete'), () => dropLocal(m.id), 'danger');
      else if (state.canDelete) menuItem(menu, 'trash', t('delete'), () => deleteMessage(m), 'danger');
      const when = new Date(m.ts);
      menu.append(el('div', 'when', when.toLocaleDateString([], { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' }) +
        ', ' + when.toLocaleTimeString([], clockOpts())));
    }, point);
  }
  async function deleteMessage(m) {
    const buttons = [{ label: t('cancel'), value: null }];
    if (m.fromMe && Date.now() - m.ts < 2 * 864e5) buttons.push({ label: t('deleteForAll'), value: 'all', kind: 'danger' });
    buttons.push({ label: t('deleteForMe'), value: 'me', kind: 'go danger' });
    const pick = await ask(t('deleteSure'), '', buttons);
    if (!pick) return;
    const jid = state.current;
    try {
      await api('/api/chats/' + enc(jid) + '/delete', { id: m.id, everyone: pick === 'all' });
      if (state.current !== jid) return;
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
  }
  /** Draws one message again in place (after "read more", a failed send, ...). */
  function redraw(m) {
    const old = state.nodes.get(m.id);
    if (!old) return;
    const node = messageNode(m, false, old.classList.contains('tail'));
    old.replaceWith(node);
    state.nodes.set(m.id, node);
  }
  /** Marks every place `q` appears in the text under `root`. */
  function highlight(root, q) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const texts = [];
    while (walker.nextNode()) texts.push(walker.currentNode);
    for (const node of texts) {
      const lower = node.data.toLowerCase();
      let from = 0, at = lower.indexOf(q);
      if (at < 0) continue;
      const parts = document.createDocumentFragment();
      while (at >= 0) {
        parts.append(node.data.slice(from, at), el('mark', null, node.data.slice(at, at + q.length)));
        from = at + q.length;
        at = lower.indexOf(q, from);
      }
      parts.append(node.data.slice(from));
      node.replaceWith(parts);
    }
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
    if (playing) { playing.pause(); playing = null; }
    if (state.loadingOlder) box.append(el('div', 'loading'));
    let day = '', prev = null;
    for (const m of sorted()) {
      if (dayKey(m.ts) !== day) { day = dayKey(m.ts); box.append(el('div', 'day', dayLabel(m.ts))); prev = null; }
      if (m.id === state.unreadFrom) {
        const mark = el('div', 'unreadmark');
        mark.append(el('span', null, t('unreadCount', { s: state.unreadCount })));
        box.append(mark);
        prev = null;
      }
      const node = messageNode(m, false, startsRun(prev, m));
      prev = m;
      state.nodes.set(m.id, node);
      box.append(node);
    }
    if (!$('info').hidden) drawInfo();
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
    // A message that was shown as "being sent" is replaced by the real one once WhatsApp has it.
    let disorder = false;
    for (const m of list) {
      if (!m.fromMe || state.messages.has(m.id)) continue;
      for (const local of Array.from(state.messages.values())) {
        if (local.local && !local.failed && local.text === m.text) {
          state.messages.delete(local.id);
          const node = state.nodes.get(local.id);
          if (node) node.remove();
          state.nodes.delete(local.id);
          break;
        }
      }
    }
    const all = sorted();
    let newest = all.length ? all[all.length - 1] : null;
    let added = 0;
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
        if (!m.fromMe && !m.local) added++;
      }
    }
    if (disorder) renderAll();
    else if (!$('info').hidden && added) drawInfo();
    if (stick) toBottom();
    else if (added) { state.newBelow += added; showLatestButton(); }
  }
  function showLatestButton() {
    const away = state.detached || !nearBottom();
    if (!away) state.newBelow = 0;
    $('toLatest').hidden = !away || !state.current;
    $('latestCount').hidden = !state.newBelow;
    $('latestCount').textContent = String(state.newBelow);
  }

  const messagesUrl = (jid, query) => '/api/chats/' + enc(jid) + '/messages?limit=60' + (query || '') + accParam('&');
  const draftKey = () => state.current + '|' + state.account;

  async function openChat(jid, hit, account) {
    if (state.current) { state.drafts.set(draftKey(), $('text').value); saveDrafts(); }
    const nextAccount = account && account !== state.activeAccount ? account : '';
    const sameAsOpen = state.current === jid && state.account === nextAccount;
    if (!sameAsOpen) { closeFind(true); $('info').hidden = true; state.unfolded.clear(); }
    closeMenu();
    $('emojiPanel').hidden = true;
    state.current = jid;
    state.account = nextAccount;
    state.unreadFrom = null;
    state.newBelow = 0;
    state.loadingOlder = false;
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
    $('latestCount').hidden = true;
    $('chatName').textContent = chat ? chat.name : jid;
    $('chat').classList.toggle('channel', !!(chat && chat.channel));
    $('channelNote').textContent = t('channelReadOnly');
    $('chatSub').textContent = chat && chat.channel ? t('channel') : chat && chat.group ? t('group') : jid.endsWith('@s.whatsapp.net') ? '+' + jid.split('@')[0] : '';
    avatar($('chatAvatar'), jid, chat ? chat.name : '?', state.account);
    $('messages').textContent = '';
    $('messages').append(el('div', 'loading'));
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
      state.canMarkRead = live && !!r.canMarkRead;
      for (const m of r.messages) state.messages.set(m.id, m);
      if (r.messages.length < 60) state.noOlder = true;
      // "Unread messages" line above the first message the phone has not shown yet.
      if (!hit && chat && chat.unread > 0) {
        const incoming = sorted().filter((m) => !m.fromMe);
        const first = incoming[incoming.length - chat.unread] || (chat.unread >= incoming.length ? incoming[0] : null);
        if (first) { state.unreadFrom = first.id; state.unreadCount = chat.unread; }
      }
      renderAll();
      const target = hit && state.nodes.get(hit.id);
      const mark = $('messages').querySelector('.unreadmark');
      if (target) { target.classList.add('hit'); target.scrollIntoView({ block: 'center' }); }
      else if (mark) { toBottom(); if (mark.offsetTop < $('messages').scrollTop) $('messages').scrollTop = Math.max(0, mark.offsetTop - 80); }
      else toBottom();
      showSendNote();
      showLatestButton();
      markRead();
      if (window.matchMedia('(min-width: 761px)').matches && $('findBar').hidden) $('text').focus();
    } catch (e) {
      if (state.current !== jid) return;
      const box = $('messages');
      box.textContent = '';
      const problem = el('div', 'problem');
      const again = el('button', 'link', t('retry'));
      again.type = 'button';
      again.addEventListener('click', () => openChat(jid, hit, account));
      problem.append(el('span', null, e.code === 'offline' || e.code === 'timeout' ? errorText(e) : t('loadProblem')), again);
      box.append(problem);
    }
  }
  async function loadOlder() {
    const jid = state.current, first = sorted()[0];
    if (!jid || !first || first.local || state.noOlder || state.loadingOlder) return;
    state.loadingOlder = true;
    const box = $('messages');
    box.prepend(el('div', 'loading'));
    try {
      const r = await api(messagesUrl(jid, '&beforeSort=' + first.sort + '&beforeId=' + first.id));
      if (state.current !== jid) return;
      if (r.messages.length < 60) state.noOlder = true;
      // Keep the message the reader is looking at in the same place.
      const anchor = state.nodes.get(first.id);
      const before = anchor ? anchor.getBoundingClientRect().top : 0;
      for (const m of r.messages) state.messages.set(m.id, m);
      state.loadingOlder = false;
      renderAll();
      const again = state.nodes.get(first.id);
      if (again) box.scrollTop += again.getBoundingClientRect().top - before;
    } catch (e) {
      // Tried again on the next scroll.
      const spinner = box.querySelector('.loading');
      if (spinner) spinner.remove();
    } finally {
      if (state.current === jid) state.loadingOlder = false;
    }
  }
  async function loadLatest() {
    const jid = state.current;
    if (!jid || state.detached) return;
    const r = await api(messagesUrl(jid));
    if (state.current !== jid || state.detached) return;
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
    note.hidden = state.canSend || $('chat').classList.contains('channel');
    if (note.hidden) return;
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
  let localSeq = 0, sendChain = Promise.resolve();
  function dropLocal(id) {
    state.messages.delete(id);
    const node = state.nodes.get(id);
    if (node) node.remove();
    state.nodes.delete(id);
  }
  function retryLocal(m) {
    dropLocal(m.id);
    queueSend(m.jid, m.text, m.replyTo, m.quote);
  }
  /** Shows the message at once as "being sent" and hands it to the phone; several go out in order. */
  function queueSend(jid, text, replyTo, quote) {
    const id = -(++localSeq);
    const local = { id, local: true, jid, sort: Number.MAX_SAFE_INTEGER - 100000 + localSeq, fromMe: true, ts: Date.now(), type: 0, text, status: 0, replyTo };
    if (quote) local.quote = quote;
    if (state.current === jid && !state.detached) { merge([local]); toBottom(); }
    sendChain = sendChain.then(async () => {
      try {
        const body = { text };
        if (replyTo) body.replyTo = replyTo;
        const r = await api('/api/chats/' + enc(jid) + '/send', body);
        if (state.current === jid) {
          if (state.detached) await openChat(jid, null, state.account); else await loadLatest();
          toBottom();
          if (!r.stored) setTimeout(() => { if (state.current === jid) loadLatest().catch(() => {}); }, 1500);
        }
      } catch (e) {
        if (e.code === 'send_unavailable') { state.canSend = false; showSendNote(); }
        else if (e.code === 'send_failed') toast(t('sendProblem', { s: e.data.step || '?' }));
        else toast(errorText(e));
        local.failed = true;
        if (state.current === jid && state.messages.has(id)) redraw(local);
        else if (state.current !== jid) toast(t('notSent') + ': ' + text.slice(0, 60));
      }
    });
  }
  async function send() {
    const jid = state.current, text = $('text').value.trim();
    if (!jid || !text) return;
    if (!state.canSend) { openOnPhone(); return; }
    // Sending from an account that is not in use: switch first, then send.
    if (state.sendAs && state.sendAs !== state.activeAccount) {
      if (!await confirmAsk(t('switchTitle'), t('switchConfirm', { s: accountName(state.sendAs) }), t('confirmSwitch'))) return;
      state.pending = { jid, text };
      $('text').value = '';
      autosize();
      await switchAccount(state.sendAs, true);
      return;
    }
    const reply = state.reply;
    $('text').value = '';
    autosize();
    setReply(null);
    state.drafts.delete(draftKey());
    saveDrafts();
    $('text').focus();
    queueSend(jid, text, reply ? reply.id : undefined, reply ? (reply.text || t(TYPE_WORD[reply.type] || 'document')) : undefined);
  }

  // ─── Live updates ────────────────────────────────────────────────────
  let polling = false;
  function setOnline(on) {
    $('offline').hidden = on && !state.switchingTo;
    if (!on) $('offline').textContent = t(state.switchingTo ? 'switching' : 'reconnecting');
  }
  async function poll() {
    if (polling) return;
    polling = true;
    let fails = 0;
    while (state.signedIn) {
      const began = Date.now();
      try {
        const u = await api('/api/updates' + (state.rev == null ? '' : '?rev=' + state.rev), undefined, 45000);
        const wasDown = fails > 1;
        fails = 0;
        setOnline(true);
        if (u.canSend !== state.canSend) { state.canSend = u.canSend; if (state.current) showSendNote(); }
        if (state.switchingTo) await checkSwitched();
        if (u.rev !== state.rev || wasDown) {
          state.rev = u.rev;
          await loadChats();
          await loadLatest();
          if (nearBottom()) markRead();
          loadStatuses();
          await sleep(600);
        } else if (Date.now() - began < 400) {
          // An answer that came at once without news: do not spin.
          await sleep(1500);
        }
      } catch (e) {
        if (e.status === 401) break;
        fails++;
        // One lost request is normal (the laptop slept, Wi-Fi blinked); say so only when it lasts.
        if (fails >= 2 || state.switchingTo) setOnline(false);
        await sleep(Math.min(15000, 1500 * fails));
      }
    }
    polling = false;
  }
  // Back from sleep or another tab: ask right away instead of waiting for the next round.
  document.addEventListener('visibilitychange', () => {
    if (document.hidden || !state.signedIn) return;
    loadChats().then(loadLatest).then(() => { if (nearBottom()) markRead(); }).catch(() => {});
  });

  // ─── Marking chats as read ───────────────────────────────────────────
  // Only for the account in use, only while this tab is in front, at most once every few seconds.
  let lastRead = { key: '', at: 0 };
  async function markRead() {
    const chat = currentChat();
    if (!chat || !prefs.markRead || !state.canMarkRead || state.account || document.hidden || !(chat.unread > 0)) return;
    const key = draftKey();
    if (lastRead.key === key && Date.now() - lastRead.at < 4000) return;
    lastRead = { key, at: Date.now() };
    try {
      await api('/api/chats/' + enc(chat.jid) + '/read', {});
      chat.unread = 0;
      renderChats();
    } catch (e) {
      if (e.code === 'read_unavailable') state.canMarkRead = false;
    }
  }

  // ─── Statuses ────────────────────────────────────────────────────────
  // Seen here only: the phone is not told, so the ring turns grey for this browser alone.
  let seenStatus = new Set();
  try { seenStatus = new Set(JSON.parse(localStorage.getItem('waex_seen_status') || '[]')); } catch (_) { /* none */ }
  const saveSeen = () => { try { localStorage.setItem('waex_seen_status', JSON.stringify(Array.from(seenStatus).slice(-600))); } catch (_) { /* ignore */ } };
  const isNew = (item) => item.unseen && !seenStatus.has(item.id);
  const personNew = (p) => !p.mine && p.items.some(isNew);
  async function loadStatuses() {
    try {
      const r = await api('/api/statuses');
      state.statuses = (r.people || []).filter((p) => p.items && p.items.length);
      state.statusesOk = r.available !== false;
    } catch (_) {
      return;
    }
    $('railStatusDot').dataset.checked = '1';
    $('railStatusDot').hidden = !state.statuses.some(personNew);
    if (state.view === 'status') renderStatuses();
  }
  function statusRing(person) {
    const ring = el('span', 'ring' + (personNew(person) ? '' : ' seen') + (person.items.length === 1 ? ' one' : ''));
    ring.style.setProperty('--n', Math.min(person.items.length, 30));
    const pic = el('span', 'avatar');
    const last = person.items[person.items.length - 1];
    if (last.thumb) {
      const img = el('img');
      img.alt = '';
      img.loading = 'lazy';
      img.addEventListener('load', () => img.classList.add('ok'));
      img.addEventListener('error', () => img.remove());
      img.src = '/api/status-thumb/' + last.id;
      avatar(pic, person.jid, person.name || t('myStatus'), '');
      pic.textContent = (Array.from((person.name || t('myStatus')).trim())[0] || '?').toUpperCase();
      pic.append(img);
    } else if (!last.media && last.text) {
      pic.classList.add('txt');
      pic.style.background = argb(last.bg, '#54656f');
      pic.style.color = argb(last.fg, '#ffffff');
      pic.textContent = last.text.slice(0, 40);
    } else {
      avatar(pic, person.jid, person.name || t('myStatus'), '');
    }
    ring.append(pic);
    return ring;
  }
  function renderStatuses() {
    const box = $('statusList');
    box.textContent = '';
    if (!state.statusesOk) { box.append(el('div', 'section muted', t('statusesUnavailable'))); return; }
    const mine = state.statuses.filter((p) => p.mine);
    const fresh = state.statuses.filter((p) => !p.mine && personNew(p));
    const seen = state.statuses.filter((p) => !p.mine && !personNew(p));
    const add = (title, list) => {
      if (!list.length) return;
      box.append(el('div', 'section', title));
      for (const person of list) {
        const row = el('div', 'row');
        row.setAttribute('role', 'listitem');
        row.tabIndex = 0;
        const main = el('div', 'grow');
        const top = el('div', 'top');
        top.append(el('div', 'name grow', person.mine ? t('myStatus') : person.name || person.jid.split('@')[0]));
        const bottom = el('div', 'bottom');
        const fresh = person.items.filter(isNew).length;
        bottom.append(el('div', 'last grow', statusTime(person.ts) + (fresh ? ' · ' + t('updates', { s: fresh }) : '')));
        main.append(top, bottom);
        row.append(statusRing(person), main);
        const open = () => openStory(state.statuses.indexOf(person));
        row.addEventListener('click', open);
        row.addEventListener('keydown', (e) => { if (e.key === 'Enter') open(); });
        box.append(row);
      }
    };
    add(t('myStatus'), mine);
    add(t('recentUpdates'), fresh);
    add(t('viewedUpdates'), seen);
    if (!box.children.length) box.append(el('div', 'section muted', t('noStatuses')));
  }
  const statusTime = (ts) => dayLabel(ts) + ', ' + new Date(ts).toLocaleTimeString([], clockOpts());
  /** WhatsApp stores status colours as signed 32-bit ARGB numbers. */
  function argb(value, fallback) {
    if (value == null || !Number.isFinite(value)) return fallback;
    const v = value >>> 0;
    return 'rgb(' + ((v >> 16) & 255) + ' ' + ((v >> 8) & 255) + ' ' + (v & 255) + ')';
  }
  function showView(view) {
    const was = state.view;
    state.view = view;
    $('app').dataset.view = view;
    $('side').hidden = view === 'status';
    $('side').classList.toggle('channels', view === 'channels');
    $('statusSide').hidden = view !== 'status';
    $('railChats').classList.toggle('on', view === 'chats');
    $('railChannels').classList.toggle('on', view === 'channels');
    $('railStatus').classList.toggle('on', view === 'status');
    $('sideTitle').textContent = t('channels');
    $('search').placeholder = t(view === 'channels' ? 'searchChannels' : 'search');
    if (was !== view && (view === 'chats' || view === 'channels')) { $('search').value = ''; state.query = ''; state.hits = []; renderChats(); }
    if (view === 'status') { renderStatuses(); loadStatuses(); }
  }

  // The viewer: one person at a time, their statuses in order, each with its own progress bar.
  const story = { person: -1, item: 0, timer: 0, started: 0, left: 0, paused: false, media: null };
  const STORY_MS = 6000;
  function openStory(index) {
    const person = state.statuses[index];
    if (!person) return;
    story.person = index;
    const first = person.items.findIndex(isNew);
    story.item = first >= 0 ? first : 0;
    $('story').hidden = false;
    drawStory();
  }
  function closeStory() {
    clearTimeout(story.timer);
    if (story.media) { story.media.pause(); story.media = null; }
    $('story').hidden = true;
    $('storyStage').textContent = '';
    if (state.view === 'status') renderStatuses();
    $('railStatusDot').hidden = !state.statuses.some(personNew);
  }
  function drawStory() {
    clearTimeout(story.timer);
    if (story.media) { story.media.pause(); story.media = null; }
    const person = state.statuses[story.person];
    const item = person && person.items[story.item];
    if (!item) { closeStory(); return; }
    seenStatus.add(item.id);
    saveSeen();
    const bars = $('storyBars');
    bars.textContent = '';
    person.items.forEach((_, i) => {
      const bar = el('i', i < story.item ? 'done' : null);
      bar.append(el('b'));
      bars.append(bar);
    });
    const pic = $('storyAvatar');
    avatar(pic, person.jid, person.mine ? t('myStatus') : person.name || '?', '');
    $('storyName').textContent = person.mine ? t('myStatus') : person.name || person.jid.split('@')[0];
    $('storyWhen').textContent = statusTime(item.ts);
    const stage = $('storyStage');
    stage.textContent = '';
    const media = item.media;
    const url = '/api/status-media/' + item.id;
    $('storySave').hidden = !(media && media.available);
    $('storySave').href = url;
    $('storyCaption').textContent = media ? (item.text || '') : '';
    let duration = STORY_MS;
    if (media && media.available && (media.mime || '').startsWith('video/')) {
      const video = el('video');
      video.src = url;
      video.autoplay = true;
      video.playsInline = true;
      video.addEventListener('ended', () => stepStory(1));
      video.addEventListener('loadedmetadata', () => { if (Number.isFinite(video.duration)) runBar(video.duration * 1000, true); });
      video.addEventListener('error', () => runBar(STORY_MS));
      story.media = video;
      stage.append(video);
      duration = 0;
    } else if (media && media.available && (media.mime || '').startsWith('audio/')) {
      const audio = el('audio');
      audio.src = url;
      audio.autoplay = true;
      audio.controls = true;
      audio.addEventListener('ended', () => stepStory(1));
      audio.addEventListener('loadedmetadata', () => { if (Number.isFinite(audio.duration)) runBar(audio.duration * 1000, true); });
      story.media = audio;
      stage.append(audio);
      duration = 0;
    } else if (media && media.available) {
      const img = el('img');
      img.alt = '';
      img.src = url;
      stage.append(img);
    } else if (media) {
      const gone = el('div', 'gone');
      if (item.thumb) { const img = el('img'); img.alt = ''; img.src = '/api/status-thumb/' + item.id; gone.append(img); }
      gone.append(el('span', null, t('statusGone')));
      stage.append(gone);
    } else {
      const text = el('div', 'storytext' + (item.font === 1 || item.font === 4 ? ' serif' : item.font === 3 ? ' mono' : ''));
      text.style.background = argb(item.bg, '#54656f');
      text.style.color = argb(item.fg, '#ffffff');
      // One inner block, so formatted words stay in the same line as the text around them.
      const words = el('span');
      rich(words, item.text || '');
      text.append(words);
      stage.append(text);
    }
    $('storyPrevPerson').disabled = story.person <= 0;
    $('storyNextPerson').disabled = story.person >= state.statuses.length - 1;
    story.paused = false;
    setPauseIcon();
    if (duration) runBar(duration);
  }
  /** Fills the current bar over [ms] and moves on; [media] bars follow the video or audio instead. */
  function runBar(ms, media) {
    const bar = $('storyBars').children[story.item];
    if (!bar) return;
    const fill = bar.firstElementChild;
    clearTimeout(story.timer);
    if (media && story.media) {
      const tick = () => {
        if (!story.media || $('story').hidden) return;
        fill.style.width = Math.min(100, (story.media.currentTime * 1000 / ms) * 100) + '%';
        story.timer = setTimeout(tick, 100);
      };
      tick();
      return;
    }
    story.left = ms;
    story.started = Date.now();
    fill.style.transition = 'none';
    fill.style.width = '0%';
    requestAnimationFrame(() => {
      fill.style.transition = 'width ' + ms + 'ms linear';
      fill.style.width = '100%';
    });
    story.timer = setTimeout(() => stepStory(1), ms);
  }
  function setPauseIcon() {
    const b = $('storyPause');
    b.textContent = '';
    b.append(icon(story.paused ? 'play' : 'pause'));
    b.title = t(story.paused ? 'play' : 'pause');
  }
  function pauseStory(on) {
    if (story.paused === on || $('story').hidden) return;
    story.paused = on;
    setPauseIcon();
    if (story.media) { if (on) story.media.pause(); else story.media.play().catch(() => {}); return; }
    const bar = $('storyBars').children[story.item];
    const fill = bar && bar.firstElementChild;
    if (!fill) return;
    if (on) {
      clearTimeout(story.timer);
      story.left = Math.max(0, story.left - (Date.now() - story.started));
      fill.style.width = getComputedStyle(fill).width;
      fill.style.transition = 'none';
    } else {
      story.started = Date.now();
      fill.style.transition = 'width ' + story.left + 'ms linear';
      fill.style.width = '100%';
      story.timer = setTimeout(() => stepStory(1), story.left);
    }
  }
  function stepStory(by) {
    const person = state.statuses[story.person];
    if (!person) return closeStory();
    const next = story.item + by;
    if (next >= 0 && next < person.items.length) { story.item = next; drawStory(); return; }
    stepPerson(by);
  }
  function stepPerson(by) {
    const index = story.person + by;
    if (index < 0 || index >= state.statuses.length) { if (by > 0) closeStory(); else { story.item = 0; drawStory(); } return; }
    story.person = index;
    const person = state.statuses[index];
    const first = by > 0 ? person.items.findIndex(isNew) : -1;
    story.item = first >= 0 ? first : 0;
    drawStory();
  }
  $('railStatus').addEventListener('click', () => showView(state.view === 'status' ? 'chats' : 'status'));
  $('railChannels').addEventListener('click', () => showView(state.view === 'channels' ? 'chats' : 'channels'));
  $('statusBack').addEventListener('click', () => showView('chats'));
  $('storyClose').addEventListener('click', closeStory);
  $('storyPrev').addEventListener('click', () => stepStory(-1));
  $('storyNext').addEventListener('click', () => stepStory(1));
  $('storyPrevPerson').addEventListener('click', () => stepPerson(-1));
  $('storyNextPerson').addEventListener('click', () => stepPerson(1));
  $('storyPause').addEventListener('click', () => pauseStory(!story.paused));
  // Holding a finger or the mouse on the picture pauses it, as on the phone.
  let holdTimer = 0, held = false;
  for (const zone of [$('storyPrev'), $('storyNext')]) {
    zone.addEventListener('pointerdown', () => { held = false; holdTimer = setTimeout(() => { held = true; pauseStory(true); }, 220); });
    zone.addEventListener('pointerup', (e) => { clearTimeout(holdTimer); if (held) { e.preventDefault(); pauseStory(false); } });
    zone.addEventListener('click', (e) => { if (held) { e.stopImmediatePropagation(); held = false; } }, true);
  }
  $('story').addEventListener('click', (e) => { if (e.target === $('story')) closeStory(); });

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
    button.textContent = (Array.from(((active && active.name) || '?').trim())[0] || '?').toUpperCase();
    button.title = t('accounts') + (active ? ': ' + active.name : '');
  }
  function showAccountMenu(e) {
    e.stopPropagation();
    if (!$('menu').hidden) { closeMenu(); return; }
    showMenu($('accountButton'), (menu) => {
      for (const a of state.accounts) {
        menuItem(menu, 'user', a.name, async () => {
          if (a.active) return;
          if (await confirmAsk(t('switchTitle'), t('switchConfirm', { s: a.name }), t('confirmSwitch'))) switchAccount(a.id, false);
        }, a.active ? 'on' : null, [a.phone, a.active ? '' : t('notInUse')].filter(Boolean).join(' · '));
      }
    });
  }
  /** The "Send as" picker above the composer, shown when the phone has several accounts. */
  function drawSendAs() {
    const box = $('sendAs');
    box.textContent = '';
    box.hidden = state.accounts.length < 2 || !state.current;
    if (box.hidden) return;
    const label = el('span', null, t('sendAs'));
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
    look.append(slider(t('corners'), 'radius', 0, 22, 0.5, 'px'));
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
    chats.append(toggle(t('markReadPref'), 'markRead', false, t('markReadHint')));
    chats.append(toggle(t('groupAvatars'), 'groupAvatars', true));
    chats.append(toggle(t('senderColors'), 'senderColors'));
    chats.append(toggle(t('bigEmoji'), 'bigEmoji', true));
    chats.append(group(t('clock'), seg([{ value: 'auto', label: t('clockAuto') }, { value: '24', label: t('clock24') },
      { value: '12', label: t('clock12') }], 'clock', true)));

    const list = section('list', t('chatList'));
    list.append(group(t('listDensity'), seg([{ value: 'comfortable', label: t('comfortable') }, { value: 'compact', label: t('compact') }], 'list')));
    list.append(toggle(t('listAvatars'), 'listAvatars'));
    list.append(slider(t('sideWidth'), 'sideWidth', 300, 560, 10, 'px'));
    list.append(toggle(t('titleCount'), 'titleCount', true));
    list.append(toggle(t('sound'), 'sound'));
    list.append(toggle(t('blur'), 'blur', false, t('blurHint')));

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
    const keys = el('div', 'field col');
    const mod = /Mac|iPhone|iPad/.test(navigator.platform || '') ? '⌘' : 'Ctrl';
    keys.append(el('span', null, t('shortcuts')));
    for (const [combo, what] of [[mod + ' K', 'scSearch'], [mod + ' Shift F', 'scFind'], ['Alt ↓ / Alt ↑', 'scNext'], ['Esc', 'scClose']]) {
      const line = el('div', 'item');
      line.append(el('span', 'grow', t(what)), el('code', null, combo));
      keys.append(line);
    }
    writing.append(keys);

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
    state.loaded = false;
    renderChats();
    await loadAccounts();
    try { await loadChats(); } catch (_) { /* the poll loop retries */ }
    loadStatuses();
    poll();
  }

  // ─── Picture and video viewer ────────────────────────────────────────
  const viewer = { list: [], at: 0 };
  const gallery = () => sorted().filter((m) => m.media && m.media.available && m.type !== 20 && (isPicture(m) || isVideo(m)));
  function openViewer(m) {
    viewer.list = gallery();
    viewer.at = viewer.list.findIndex((x) => x.id === m.id);
    if (viewer.at < 0) { viewer.list = [m]; viewer.at = 0; }
    $('viewer').hidden = false;
    drawViewer();
  }
  function drawViewer() {
    const m = viewer.list[viewer.at];
    if (!m) return;
    const stage = $('viewerStage'), url = mediaUrl(m);
    stage.textContent = '';
    if (isVideo(m)) {
      const video = el('video');
      video.controls = true;
      video.autoplay = true;
      video.playsInline = true;
      video.src = url;
      stage.append(video);
    } else {
      const img = el('img');
      img.alt = t('photo');
      img.src = url;
      img.addEventListener('click', (e) => { e.stopPropagation(); img.classList.toggle('zoom'); });
      stage.append(img);
    }
    const when = new Date(m.ts);
    $('viewerWho').textContent = m.fromMe ? t('you') : (m.sender || $('chatName').textContent);
    $('viewerWhen').textContent = dayLabel(m.ts) + ', ' + when.toLocaleTimeString([], clockOpts());
    $('viewerCaption').textContent = m.text || '';
    $('viewerSave').href = url;
    $('viewerSave').download = m.media.name || (isVideo(m) ? 'video.mp4' : 'photo.jpg');
    $('viewerPrev').disabled = viewer.at <= 0;
    $('viewerNext').disabled = viewer.at >= viewer.list.length - 1;
  }
  function stepViewer(by) {
    const next = viewer.at + by;
    if (next < 0 || next >= viewer.list.length) return;
    viewer.at = next;
    drawViewer();
  }
  function closeViewer() {
    if ($('viewer').hidden) return;
    $('viewer').hidden = true;
    $('viewerStage').textContent = '';
  }
  $('viewerClose').addEventListener('click', closeViewer);
  $('viewerPrev').addEventListener('click', () => stepViewer(-1));
  $('viewerNext').addEventListener('click', () => stepViewer(1));
  $('viewerStage').addEventListener('click', (e) => { if (e.target === $('viewerStage')) closeViewer(); });

  // ─── Search inside the open chat ─────────────────────────────────────
  function openFind() {
    if (!state.current) return;
    $('findBar').hidden = false;
    $('findText').focus();
    $('findText').select();
  }
  function closeFind(quiet) {
    const had = find.q;
    find.q = ''; find.hits = []; find.at = -1; find.remote = [];
    $('findBar').hidden = true;
    $('findText').value = '';
    $('findCount').textContent = '';
    if (had && !quiet && state.current) { const keep = $('messages').scrollTop; renderAll(); $('messages').scrollTop = keep; }
  }
  function showFindHit() {
    for (const old of document.querySelectorAll('mark.cur')) old.classList.remove('cur');
    const total = find.hits.length + find.remote.length;
    $('findCount').textContent = find.q ? (total ? (find.remote.length + find.at + 1) + ' / ' + total : t('noMatches')) : '';
    const node = state.nodes.get(find.hits[find.at]);
    if (!node) return;
    for (const mark of node.querySelectorAll('mark')) mark.classList.add('cur');
    node.scrollIntoView({ block: 'center' });
  }
  /** Marks the matches in the loaded messages; older matches come from the phone's search. */
  function collectFind(keepId) {
    find.hits = sorted().filter((m) => m.text && m.text.toLowerCase().includes(find.q)).map((m) => m.id);
    const at = keepId == null ? -1 : find.hits.indexOf(keepId);
    find.at = at >= 0 ? at : find.hits.length - 1;
    const loaded = new Set(state.messages.keys());
    find.remote = find.remote.filter((hit) => !loaded.has(hit.id));
  }
  let findTimer = 0;
  function runFind() {
    const q = $('findText').value.trim().toLowerCase();
    if (q === find.q) return;
    find.q = q;
    find.remote = [];
    const keep = $('messages').scrollTop;
    renderAll();
    $('messages').scrollTop = keep;
    collectFind();
    showFindHit();
    clearTimeout(findTimer);
    if (q.length < 2 || state.account) return;
    const jid = state.current;
    findTimer = setTimeout(async () => {
      try {
        const r = await api('/api/search?q=' + enc(q) + '&limit=200');
        if (find.q !== q || state.current !== jid) return;
        const loaded = new Set(state.messages.keys());
        find.remote = r.results.filter((hit) => hit.jid === jid && !loaded.has(hit.id)).sort((a, b) => a.sort - b.sort);
        showFindHit();
      } catch (_) { /* the loaded matches still work */ }
    }, 400);
  }
  async function stepFind(by) {
    if (!find.q) return;
    const next = find.at + by;
    if (next < 0 && find.remote.length) {
      // An older match that is not loaded: jump to that part of the chat.
      const hit = find.remote[find.remote.length - 1];
      await openChat(state.current, hit, state.account);
      collectFind(hit.id);
      showFindHit();
      return;
    }
    if (!find.hits.length) return;
    find.at = (next + find.hits.length) % find.hits.length;
    showFindHit();
  }
  $('findOpen').addEventListener('click', () => ($('findBar').hidden ? openFind() : closeFind()));
  $('findClose').addEventListener('click', () => closeFind());
  $('findText').addEventListener('input', runFind);
  $('findText').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); stepFind(e.shiftKey ? 1 : -1); }
  });
  $('findPrev').addEventListener('click', () => stepFind(-1));
  $('findNext').addEventListener('click', () => stepFind(1));

  // ─── Chat info panel ─────────────────────────────────────────────────
  function drawInfo() {
    const body = $('infoBody');
    const keep = body.scrollTop;
    body.textContent = '';
    const chat = currentChat();
    const jid = state.current;
    if (!jid) return;
    const who = el('div', 'who');
    const pic = el('div', 'avatar');
    avatar(pic, jid, chat ? chat.name : '?', state.account);
    who.append(pic, el('h2', null, chat ? chat.name : jid), el('div', 'muted', $('chatSub').textContent));
    body.append(who);

    const actions = el('div', 'list');
    const action = (iconName, label, run) => {
      const b = el('button');
      b.type = 'button';
      b.append(icon(iconName), el('span', null, label));
      b.addEventListener('click', run);
      actions.append(b);
    };
    action('search', t('findInChat'), () => { if (window.matchMedia('(max-width: 1180px)').matches) $('info').hidden = true; openFind(); });
    if (!state.account) action('phone', t('openPhone'), () => $('phoneOpen').click());
    body.append(actions);

    const all = sorted();
    const media = all.filter((m) => m.media && m.media.available && m.type !== 20 && (isPicture(m) || isVideo(m))).reverse();
    const mediaHead = el('h3', null, t('media'));
    if (media.length) mediaHead.append(el('span', null, String(media.length)));
    body.append(mediaHead);
    if (media.length) {
      const grid = el('div', 'grid');
      for (const m of media.slice(0, 30)) {
        const b = el('button');
        b.type = 'button';
        const img = el('img');
        img.alt = '';
        img.loading = 'lazy';
        img.src = m.thumb || isVideo(m) ? '/api/thumb/' + m.id + accParam('?') : mediaUrl(m);
        img.addEventListener('error', () => { img.remove(); });
        b.append(img);
        if (isVideo(m)) b.append(icon('video'));
        b.addEventListener('click', () => openViewer(m));
        grid.append(b);
      }
      body.append(grid);
    } else body.append(el('div', 'none', t('nothingYet')));

    const docs = all.filter((m) => m.media && m.media.available && m.type === 9).reverse();
    body.append(el('h3', null, t('docs')));
    if (docs.length) {
      const list = el('div', 'list');
      for (const m of docs.slice(0, 20)) {
        const a = el('a');
        a.href = mediaUrl(m);
        a.download = m.media.name || 'file';
        const text = el('span', null, m.media.name || t('document'));
        text.append(el('small', null, [size(m.media.size), new Date(m.ts).toLocaleDateString()].filter(Boolean).join(' · ')));
        a.append(icon('file'), text);
        list.append(a);
      }
      body.append(list);
    } else body.append(el('div', 'none', t('nothingYet')));

    const links = [];
    for (const m of all) for (const url of (m.text || '').match(/https?:\/\/[^\s<>"']+/g) || []) links.push({ url: url.replace(/[).,!?:;]+$/, ''), ts: m.ts });
    body.append(el('h3', null, t('links')));
    if (links.length) {
      const list = el('div', 'list');
      for (const link of links.reverse().slice(0, 20)) {
        const a = el('a');
        a.href = link.url;
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        const text = el('span', null, link.url.replace(/^https?:\/\/(www\.)?/, ''));
        text.append(el('small', null, new Date(link.ts).toLocaleDateString()));
        a.append(icon('link'), text);
        list.append(a);
      }
      body.append(list);
    } else body.append(el('div', 'none', t('nothingYet')));
    body.scrollTop = keep;
  }
  function toggleInfo() {
    if (!state.current) return;
    $('info').hidden = !$('info').hidden;
    if (!$('info').hidden) { $('infoBody').scrollTop = 0; drawInfo(); }
  }
  $('chatHead').addEventListener('click', toggleInfo);
  $('infoOpen').addEventListener('click', toggleInfo);
  $('infoClose').addEventListener('click', () => { $('info').hidden = true; });

  // ─── Emoji picker ────────────────────────────────────────────────────
  let recentEmoji = [];
  try { recentEmoji = JSON.parse(localStorage.getItem('waex_emoji') || '[]').filter((e) => typeof e === 'string').slice(0, 24); } catch (_) { /* none */ }
  function insertEmoji(emoji) {
    const box = $('text'), at = box.selectionStart == null ? box.value.length : box.selectionStart;
    const end = box.selectionEnd == null ? at : box.selectionEnd;
    box.value = box.value.slice(0, at) + emoji + box.value.slice(end);
    box.selectionStart = box.selectionEnd = at + emoji.length;
    autosize();
    recentEmoji = [emoji].concat(recentEmoji.filter((e) => e !== emoji)).slice(0, 24);
    try { localStorage.setItem('waex_emoji', JSON.stringify(recentEmoji)); } catch (_) { /* ignore */ }
  }
  function buildEmojiPanel() {
    const panel = $('emojiPanel');
    panel.textContent = '';
    const nav = el('nav'), grid = el('div', 'grid');
    const heads = [];
    const add = (id, tab, list) => {
      if (!list.length) return;
      const head = el('h4', null, t(id));
      grid.append(head);
      for (const emoji of list) {
        const b = el('button', null, emoji);
        b.type = 'button';
        b.addEventListener('click', (e) => { e.stopPropagation(); insertEmoji(emoji); });
        grid.append(b);
      }
      const jump = el('button', null, tab);
      jump.type = 'button';
      jump.title = t(id);
      jump.setAttribute('aria-label', t(id));
      jump.addEventListener('click', (e) => { e.stopPropagation(); grid.scrollTop = head.offsetTop - grid.offsetTop; });
      nav.append(jump);
      heads.push([head, jump]);
    };
    add('recent', '🕒', recentEmoji);
    for (const set of EMOJI_SETS) add(set.id, set.tab, set.list);
    const markTab = () => {
      let current = heads[0];
      for (const pair of heads) if (pair[0].offsetTop - grid.offsetTop <= grid.scrollTop + 8) current = pair;
      for (const pair of heads) pair[1].classList.toggle('on', pair === current);
    };
    grid.addEventListener('scroll', markTab, { passive: true });
    panel.append(nav, grid);
    requestAnimationFrame(markTab);
  }
  $('emojiButton').addEventListener('click', (e) => {
    e.stopPropagation();
    const panel = $('emojiPanel');
    if (panel.hidden) { buildEmojiPanel(); panel.hidden = false; } else panel.hidden = true;
  });

  // ─── Buttons, keys and the rest ──────────────────────────────────────
  $('askCode').addEventListener('click', askCode);
  $('newCode').addEventListener('click', askCode);
  $('loginStep2').addEventListener('submit', confirmCode);
  $('search').addEventListener('input', onSearch);
  $('search').addEventListener('keydown', (e) => {
    if (e.key !== 'ArrowDown' && e.key !== 'Enter') return;
    const first = $('chats').querySelector('.row');
    if (!first) return;
    e.preventDefault();
    if (e.key === 'Enter') first.click(); else first.focus();
  });
  $('chats').addEventListener('keydown', (e) => {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    const row = e.target.closest('.row');
    if (!row) return;
    e.preventDefault();
    let next = row;
    do { next = e.key === 'ArrowDown' ? next.nextElementSibling : next.previousElementSibling; } while (next && !next.classList.contains('row'));
    if (next) next.focus(); else if (e.key === 'ArrowUp') $('search').focus();
  });
  $('tabs').addEventListener('click', (e) => {
    const button = e.target.closest('button[data-tab]');
    if (!button) return;
    state.tab = button.dataset.tab;
    for (const b of $('tabs').children) b.classList.toggle('on', b === button);
    renderChats();
  });
  $('railChats').addEventListener('click', () => {
    showView('chats'); $('search').value = ''; onSearch(); $('chats').scrollTop = 0; $('search').focus(); });
  function closeChat() {
    if (state.current) { state.drafts.set(draftKey(), $('text').value); saveDrafts(); }
    $('app').classList.remove('open');
    state.current = null;
    state.account = '';
    $('chat').hidden = true;
    $('info').hidden = true;
    $('empty').hidden = false;
    closeFind(true);
    if (playing) { playing.pause(); playing = null; }
    renderChats();
  }
  $('back').addEventListener('click', closeChat);
  $('toLatest').addEventListener('click', () => {
    if (state.detached) openChat(state.current, null, state.account);
    else { state.newBelow = 0; $('messages').scrollTo({ top: $('messages').scrollHeight, behavior: 'smooth' }); }
  });
  let scrollTick = 0;
  $('messages').addEventListener('scroll', () => {
    closeMenu();
    if (scrollTick) return;
    scrollTick = requestAnimationFrame(() => {
      scrollTick = 0;
      showLatestButton();
      if ($('messages').scrollTop < 240 && state.messages.size && !state.noOlder) loadOlder();
    });
  }, { passive: true });
  $('send').addEventListener('click', send);
  $('text').addEventListener('input', () => {
    autosize();
    if (state.current) { state.drafts.set(draftKey(), $('text').value); saveDrafts(); }
  });
  $('text').addEventListener('keydown', (e) => {
    if (e.key !== 'Enter' || e.isComposing) return;
    if (prefs.enter ? !e.shiftKey : (e.ctrlKey || e.metaKey)) { e.preventDefault(); send(); }
  });
  $('replyCancel').addEventListener('click', () => setReply(null));
  document.addEventListener('pointerdown', (e) => {
    if (!e.target.closest('#menu')) closeMenu();
    if (!e.target.closest('#emojiPanel, #emojiButton')) $('emojiPanel').hidden = true;
  });
  window.addEventListener('resize', closeMenu);
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
    prefs.sideWidth = Math.round(clamp(e.clientX - $('side').getBoundingClientRect().left, 300, 560, 400));
    applyPrefs();
  });
  const endDrag = () => { if (grip.classList.contains('on')) { grip.classList.remove('on'); savePrefs(); } };
  grip.addEventListener('pointerup', endDrag);
  grip.addEventListener('pointercancel', endDrag);
  grip.addEventListener('dblclick', () => { prefs.sideWidth = DEFAULTS.sideWidth; savePrefs(); applyPrefs(); });
  $('closeSettings').addEventListener('click', () => $('settings').close());
  $('settings').addEventListener('click', (e) => { if (e.target === $('settings')) $('settings').close(); });

  /** Opens the chat above or below the open one in the list as it is shown. */
  function stepChat(by) {
    const rows = Array.from($('chats').querySelectorAll('.row'));
    if (!rows.length) return;
    const at = rows.findIndex((row) => row.classList.contains('on'));
    const next = rows[Math.min(rows.length - 1, Math.max(0, at + by))];
    if (next && next !== rows[at]) { next.click(); next.scrollIntoView({ block: 'nearest' }); }
  }
  document.addEventListener('keydown', (e) => {
    const typing = /^(INPUT|TEXTAREA|SELECT)$/.test((e.target.tagName || ''));
    const mod = e.ctrlKey || e.metaKey;
    if (!$('story').hidden) {
      if (e.key === 'ArrowLeft') stepStory(-1);
      else if (e.key === 'ArrowRight') stepStory(1);
      else if (e.key === ' ') { e.preventDefault(); pauseStory(!story.paused); }
      else if (e.key === 'Escape') closeStory();
      return;
    }
    if (!$('viewer').hidden) {
      if (e.key === 'ArrowLeft') stepViewer(-1);
      else if (e.key === 'ArrowRight') stepViewer(1);
      else if (e.key === 'Escape') closeViewer();
      return;
    }
    if ($('settings').open || $('ask').open || !state.signedIn) return;
    if (mod && !e.shiftKey && e.key.toLowerCase() === 'k') { e.preventDefault(); $('search').focus(); $('search').select(); return; }
    if (mod && e.shiftKey && e.key.toLowerCase() === 'f') { e.preventDefault(); openFind(); return; }
    if (e.altKey && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) { e.preventDefault(); stepChat(e.key === 'ArrowDown' ? 1 : -1); return; }
    if (e.key === '/' && !typing && !mod) { e.preventDefault(); $('search').focus(); return; }
    if (e.key !== 'Escape') return;
    // Escape closes one thing at a time, the topmost first.
    if (!$('menu').hidden) closeMenu();
    else if (!$('emojiPanel').hidden) $('emojiPanel').hidden = true;
    else if (!$('findBar').hidden) closeFind();
    else if (state.reply) setReply(null);
    else if (!$('info').hidden) $('info').hidden = true;
    else if ($('search').value) { $('search').value = ''; onSearch(); }
    else if (state.current && !typing) closeChat();
  });
  // A mistake in one handler must not leave the page dead; the list and the open chat reload.
  window.addEventListener('unhandledrejection', (e) => {
    if (e.reason instanceof ApiError) { e.preventDefault(); if (e.reason.status !== 401) toast(errorText(e.reason)); }
  });

  applyPrefs();
  applyWords();
  start();
})();
