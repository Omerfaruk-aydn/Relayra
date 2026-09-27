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

Tanımlanmalı:
- spacing scale
- typography scale
- radius scale
- neutral/background/surface/border/text colors
- semantic colors
- elevation
- motion duration/easing

Rastgele component bazlı değerler azaltılmalıdır.
