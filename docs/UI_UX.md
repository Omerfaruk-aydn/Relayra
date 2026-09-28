# Relayra — Professional UI/UX Specification

## 1. Tasarım Yönü

Relayra:
- desktop-first,
- responsive,
- dark-mode ağırlıklı,
- hızlı,
- yoğun fakat temiz,
- profesyonel

olmalıdır.

Kalite referansları:
- Linear
- Slack
- Discord
- Raycast

Birebir kopya yapılmaz.

---

## 2. Kaçınılacaklar

- ucuz AI SaaS görünümü
- aşırı gradient
- mor neon
- glassmorphism
- aşırı yuvarlak card
- emoji ağırlıklı ikonografi
- gereksiz dashboard metrik kartları
- anlamsız animasyon
- içerikten fazla dekorasyon

---

## 3. Ana Layout

```text
┌──────┬────────────────┬──────────────────────────────┬──────────────┐
│ Nav  │ Context Panel  │ Main Conversation            │ Member Panel │
│      │ Channels / DMs │                              │              │
└──────┴────────────────┴──────────────────────────────┴──────────────┘
```

---

## 4. Ekranlar

### Public
- Login
- Register

### Home
- Friends
- Online
- Pending
- Blocked
- Add Friend
- DM list

### Community
- Channel chat
- Members
- Community overview

### Settings
- My Account
- Profile
- Appearance
- Notifications
- Privacy
- Security

### Community Settings
- Overview
- Channels
- Members
- Roles
- Invites
- Bans
- Audit Log

---

## 5. Message UI

Mesaj:
- avatar
- author
- timestamp
- edited state
- content
- attachments
- reply reference
- reactions

Hover:
- reply
- react
- edit if owner
- delete if owner/permission
- more

---

## 6. Composer

- multiline
- Enter send / Shift+Enter newline
- attachment
- reply preview
- disabled state
- slowmode/rate-limit feedback if implemented
- character limit feedback near threshold

---

## 7. Realtime UX

Connection state:
- connected
- reconnecting
- offline

Message state:
- sending
- sent
- failed
- retry

Reconnect UI rahatsız edici modal olmamalıdır.

---

## 8. Empty States

Örnek:
- arkadaş yok
- pending request yok
- DM yok
- channel boş
- notification yok

Boş beyaz ekran bırakılmaz.

---

## 9. Error States

- inline form error
- page-level retry
- toast only for transient action
- permission error
- network error
- expired session

Error message anlaşılır olmalıdır.

---

## 10. Permission UX

Frontend gizleme yalnızca UX içindir.

Backend security'den bağımsız değildir.

Yetkisiz buton:
- gizlenebilir,
- disabled + tooltip olabilir

ürün kararına göre standartlaştırılmalıdır.

---

## 11. Accessibility

- keyboard navigation
- visible focus
- semantic HTML
- ARIA where needed
- contrast
- reduced motion
- icon-only button labels
- color-only state yok

---

## 12. Performance

Uzun message/member listelerde virtualization değerlendir.

Görseller lazy-load.

Infinite scroll memory leak oluşturmamalıdır.

---

## 13. Responsive

Desktop: 4 panel mümkün.  
Tablet: member panel collapsible.  
Mobile: drawer navigation + single main panel.

---

## 14. Design Tokens

`frontend/src/styles/tokens.css` kilitli token kaynağıdır:

- font: Inter, system fallback
- radius: 6 / 8 / 10 / 12 / 16 px
- background: `#0a0f1e` app, `#0d1426` panel, `#111a30` card, `#16203a` hover
- border: `rgba(148, 163, 184, 0.14)`
- text: `#f1f5f9` primary, `#94a3b8` secondary, `#64748b` muted
- accent: `#2563eb` primary, `#3b82f6` hover, `#1d4ed8` active
- semantic: success `#22c55e`, warning `#f59e0b`, danger `#ef4444`, info `#38bdf8`
- online presence: `#22c55e`, idle: `#f59e0b`, offline: `#64748b`
- elevation: panel `0 8px 24px rgba(2, 6, 23, 0.45)`, modal `0 24px 64px rgba(2, 6, 23, 0.6)`
- motion: 120ms hover, 180ms panel, `ease-out`

Rastgele component bazlı değerler azaltılmalıdır.

---

## 15. Mockup Envanteri

47 ekran `docs/mockups/` altındadır. Grup ve ekran listesi:

### Auth (5)
- sign-in split hero (cam kart + form)
- split login (dağ manzaralı)
- register (form + topluluk paneli)
- reset password (merkezi kart)
- onboarding profil adımı (step 2/3 + canlı önizleme)

### App Shell (1)
- 3 kolon: ikon nav + context panel + ana içerik + sağ inspector

### Channel Chat (4)
- dolu kanal (mesajlar, embed kartlar, reaction, reply)
- boş kanal (illüstrasyon + CTA)
- skeleton loading
- reconnect banner + offline composer

### Direct Message (2)
- DM konuşma (balonlar, dosya kartı, ses kaydı, sağ profil paneli)
- DM liste varyantı

### Friends (4)
- friends listesi (tablo + aktivite + öneriler)
- friend requests (incoming/outgoing + trust paneli)
- add friends (arama + öneri kartları)
- blocked users (tablo + bilgi paneli)

### Community (6)
- create community wizard (4 adım + canlı önizleme)
- create community basit form
- community settings (profil + görünürlük + invite code)
- community members (tablo + sağ profil kartı)
- community invites (link tablosu + kurallar)
- join community (invite code + önizleme)
- discover communities (öne çıkan kartlar + trend tablo)

### Channel Yönetimi (2)
- channel settings (detay + tip + organizasyon + izin + entegrasyon)
- create channel modalı

### Roles & Permissions (3)
- roles listesi + permission toggle matrisi
- roles varyantı (kategori gruplu)
- assign role modalı

### Moderation (3)
- banned members tablosu
- ban member modalı
- audit log tablosu + filtre paneli

### Notification (2)
- notification merkezi (grup + filtre + tercih paneli)
- notification settings (kanal bazlı + özet)

### Search (1)
- global search (mesaj + dosya + filtre paneli)

### Settings (5)
- account settings (profil + plan + bildirim + bağlantılar)
- security settings (şifre + 2FA + session + skor)
- privacy settings (görünürlük + DM + presence)
- appearance (tema + kontrast + font + density + canlı önizleme)
- user profile (banner + aktivite + rozetler)

### State Sayfaları (3)
- access restricted (kilit illüstrasyon)
- community load error (retry)
- boş/error varyantları yukarıdaki ekranlara gömülüdür

### Modallar (6)
- create channel, invite people, create invite, assign role, ban member, delete community (isim onaylı)

### Kapsam Dışı (mockup'ta var, MVP'de yok)
- OAuth butonları (Google/Apple/Microsoft) ve "Continue with Google": `API.md` non-goals OAuth yasağına takılır, implemente edilmez.
- Threads, Events, Analytics, Emoji, Webhooks, Integrations (Figma/GitHub/Jira), Forum/Voice/Announcement kanal tipleri, 2FA, telefon/SMS, billing/plan ekranları, keşfet/discover akışı: mockup vizyonu olarak kalır, MVP kapsamına alınmaz.
- Sesli/video arama butonları: yalnızca görsel placeholder, arama fonksiyonu yoktur.

---

## 16. Mockup'tan Türetilmiş UI Kuralları

- Auth sayfaları split-layout: sol form (max 420px), sağ marka paneli; mobilde marka paneli gizlenir.
- App shell her zaman 3 kolon + sağ inspector; inspector kapatılabilir.
- Mesaj satırı: 40px avatar, ad + zaman, içerik, hover aksiyonları (reply/react/edit/delete/more).
- Embed kart: başlık + açıklama + aksiyon butonu + kapatma; dosya kartı: ikon + ad + boyut + indir.
- Reaction hapı: emoji + sayı; aktifse mavi kenarlık.
- Toggle switch tüm settings ekranlarında aynı bileşendir.
- Tablo satırı: avatar + ad + meta; sağda aksiyon butonu; hover'da satır highlight.
- Modal: başlık + açıklama + X; danger modal kırmızı aksiyon; delete community isim onayı ister.
- Empty state: illüstrasyon + başlık + açıklama + birincil CTA; asla boş beyaz ekran yok.
- Error state: ikon + başlık + açıklama + retry; reconnect banner modal değildir.
- Skeleton: avatar + satır blokları, shimmer animasyonu, `prefers-reduced-motion` kapalıyken static.
- Form validasyonu inline; karakter sayacı eşik yaklaştığında görünür.
- Permission-gated butonlar gizlenir veya disabled + tooltip olur; backend kontrolü esastır.
- Avatar yoksa baş harf fallback; presence noktası sağ altta.
- Zaman formatı göreli (şimdi, 5d, 12m); detay title tooltip'tedir.

