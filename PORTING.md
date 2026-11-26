# Porting Plan — Wheel of Adaptation (Bedrock → Java Fabric 1.21.1)

Sumber: [axellpemula/Wheel-of-Adaptation](https://github.com/axellpemula/Wheel-of-Adaptation) v1.4.0  
Target: mod Fabric 1.21.1 di repo ini (`wheel_of_adaptation`)

> Addon Bedrock ini adalah **Mahoraga's Dharma Wheel** dari Jujutsu Kaisen — lore-accurate adaptation.  
> Total sumber Bedrock: **603 file**, script `main.js` **9.361 baris** (sudah minified/obfuscated), **271 particle**, ~20 sound.

---

## 1. Apa yang ada di addon Bedrock?

### 1.1 Item (BP `items/` + RP `textures/items/xvoid/`)

| Bedrock ID | Nama di game | Fungsi |
|---|---|---|
| `xvoid:wheel_of_adaptation` | Wheel of Adaptation | Helm (head, protection 3). Item utama. Dipakai → roda 3D muncul di atas kepala |
| `xvoid:sword_of_extermination` | Sword of Extermination | Pedang (damage 8). Stab / Slash / Air Blast / Dash / Purify |
| `xvoid:seal_of_judgment` | Seal of Judgment | Item domain. Hold → buka domain, bind entity di dalam |
| `xvoid:mode_switcher` (+ attack/defense/barehand/sword variants) | Wheel Mode Switcher | Ganti mode Attack / Balance / Defense |
| `xvoid:barehand` | Barehand | Punch / Guard / Teleport Kick / Dash |
| `xvoid:mahoraga_chest` | Mahoraga Upper Body | Armor chest (protection 6) |
| `xvoid:mahoraga_legs` | Mahoraga Hakama | Armor legs (protection 5) |
| `xvoid:mahoraga_boots` | Mahoraga Feet | Armor boots (protection 2) |
| `xvoid:adaptation_config` | Adaptation Settings | Item menu settings (hidden) |
| `xvoid:wheel_of_adaptation_*` (spin/full/react/first/fast/step/step_back/seal/rewind) | — | Varian state roda (hidden, diganti script saat animasi) |
| `xvoid:sword_of_extermination_aura*` | — | Varian aura pedang (hidden) |

Recipe Bedrock:
- **Wheel:** golden apple, clock, gold ingot, totem (pola ACA / GTG / CGC)
- **Sword:** golden apple, diamond sword, gold ingot, blaze rod
- **Seal:** iron bars, lightning rod, clock, totem

### 1.2 Sistem inti (dari `main.js` + `en_US.lang`)

1. **Adaptation** — kena damage dari suatu fenomena → wheel spin → *Analysing* → *Adapted* → damage tipe itu di-nullify + heal.  
   Fenomena yang dikenali: `env:` (fire, lava, explosion, fall, drowning, freezing, lightning, wither, cactus, thorns, dll), `mob:` (blaze, ghast, shulker, breeze, dll), `proj:` (fireball, shulker bullet, wind charge, dll), `effect:` (blindness, darkness, hunger, dll), `kind:` (sword, axe, spear, mace, trident, tool, zombie, spider, dll).
2. **Evolved (II)** — terus kena serangan yang sudah adapted → berevolusi (efek lebih kuat: reflect, quake, absorb, dll).
3. **Effect flip** — poison/wither → regeneration, slowness → speed, blindness/darkness → night vision, dll.
4. **Attack adaptation** — pukul musuh berulang → adaptasi serangan (pierce armor, bonus damage).
5. **Mode** — Attack / Balance / Defense (hold use 1 detik untuk ganti).
6. **Sword** — Attack: Stab. Use: Slash (10 dmg, potong block). Sneak+Use: Air Blast. Lompat di udara: Dash. Double-tap sneak: Barehand. Purify zombie villager → cure.
7. **Barehand** — Punch, Guard (hold use), Teleport Kick, Dash.
8. **Seal of Judgment** — butuh wheel + full adaptations. Hold → domain terbuka, bind entity, barrier, exhaustion.
9. **Return by Death** — rewind waktu, butuh full adaptation, cooldown.
10. **Config** — starter/crafting, slash cuts blocks, keep on death, low FX, adaptation limit, performance (low/medium/ultra), camera shake, armor on/off. Command: `/woa:performance`, `/woa:armor`, `/woa:shake`.
11. **HUD** — emblem roda, progress bar, adapted list, watermark `Wheel of Adaptation v1.4.0 by xVoid_Axell`.
12. **Soulbound** — wheel tidak bisa drop, reset saat dilepas/mati (kecuali keep-on-death).

### 1.3 Visual (RP)

- Model 3D Bedrock (`.geo.json`): wheel, sword, armor, seal chain — attachable di kepala/tangan/badan
- Animasi: wheel idle/spin/seal/rewind, sword slash/stab, barehand guard/dash/kick/punch, RBD fall/rise
- 271 particle custom (`adapt_*`, `slash_*`, `seal_*`, `guard_*`, `dash_*`, dll)
- Sound custom (~20 `.ogg`): wheel spin/adapted/heal, sword slash/stab/purify, seal chain/open/close, dash, guard, dll
- UI: HUD custom + server form (menu settings + adaptation list) + glyph font

---

## 2. Bedrock → Java: pemetaan teknis

| Bedrock | Java Fabric 1.21.1 |
|---|---|
| `minecraft:wearable` head (Wheel) | **Slot khusus**: `Item` + Wheel Menu (`ItemDisplay` di atas kepala) — bukan helm vanilla |
| `minecraft:damage` sword | `SwordItem` + `ToolMaterial` |
| `minecraft:wearable` armor | `ArmorItem` + `ArmorMaterial` custom Mahoraga (defense sesuai Bedrock) |
| Script `main.js` (damage, tick, use) | Fabric events: `ServerLivingEntityEvents.ALLOW_DAMAGE`, `UseItemCallback`, `AttackEntityCallback`, tick via `ServerTickEvents` |
| Attachable 3D di kepala | `ItemDisplay` entity + spin per-tick (Fase 3), model penuh (Fase 4) |
| Animasi Bedrock | Animasi Java (keyframe / `LivingEntityRenderer`) — Fase 4 |
| 271 particle custom | Particle vanilla + `adapt_wheel` custom via `ParticleFactoryRegistry` (Fase 3) |
| Sound `.ogg` custom | Copy `.ogg` ke `assets/.../sounds/` + `sounds.json` (Fase 2) |
| HUD custom + form | HUD `HudRenderCallback` + `WheelScreen` menu + action bar (Fase 2/3) |
| Player data (adaptations) | `PlayerAdaptationData` + JSON per-player di `<world>/wheel_of_adaptation/playerdata/` |
| Return by Death | History posisi server tick + cooldown di `PlayerAdaptationData` (Fase 3) |
| Command `/woa:*` | Brigadier command Fabric `/woa` (Fase 2) |

---

## 3. Roadmap fase

### Fase 1 — Core (sekarang)
- [x] Item inti: Wheel (helm), Sword, Seal, Mode Switcher, Config, Mahoraga armor (chest/legs/boots)
- [x] Texture item dari RP Bedrock + recipe sesuai Bedrock
- [x] Sistem adaptasi dasar: kena damage → Analysing (3 hit) → Adapted → nullify + heal + toast + particle/sound vanilla
- [x] Creative tab + lang

### Fase 2 — Gameplay lengkap
- [x] Persistensi data player (JSON file per-player) + load/save join/leave/tick
- [x] Evolved (II), effect flip (poison/wither/slowness/dll -> positif)
- [x] Mode Attack/Balance/Defense + Mode Switcher use logic (klik kanan)
- [x] Sword: slash (potong block), air blast (sneak+use), dash (use di udara)
- [x] Barehand: teleport kick (klik kanan ke target terdekat) + guard (sneak+use: Resistance)
- [x] Seal of Judgment (domain sederhana + bind slowness)
- [x] Return by Death — lethal damage di-cancel, rewind ke posisi 30 detik lalu, heal 50%, konsumsi 1 adapted, cooldown 60s
- [x] Menu settings (Screen custom Java) + command `/woa:*`
- [x] Sound custom dari RP (28 .ogg + sounds.json)
- [x] Attack adaptation — pukul musuh 4x (Sword/Barehand + Wheel) → adapted per-entity → bonus damage pierce
- [x] Purify — Sword hit Zombie Villager → cure jadi Villager

### Fase 3 — Visual (kebebasan Java, bukan 1:1 Bedrock)
- [x] **Slot khusus Wheel** — bukan helm vanilla. Equip lewat Wheel Menu (keybind R / Adaptation Settings / `/woa menu`). Bisa pakai helm lain sambil pakai Wheel.
- [x] **UI custom Java** — Wheel Menu Screen (slot + mode + adapted list + analysing progress) + HUD emblem + progress bar scan
- [x] **Animasi scan** — ring particle berputar di atas kepala + beam saat Analysing (server tick)
- [x] **Roda berputar di atas kepala** — `ItemDisplay` Wheel + spin otomatis (pelan idle, cepat saat Analysing, miring saat scan). Model `.geo.json` penuh menyusul di Fase 4.
- [x] **Particle custom** — `adapt_wheel` (texture 256x256 dari RP Bedrock) via `ParticleFactoryRegistry`, dipakai saat Adapted/Evolved/RBD + scan ring
- [x] **Armor texture Mahoraga** — `ArmorMaterial` custom (chest 6 / legs 5 / boots 2, sesuai Bedrock) + layer 1/2 dari RP Bedrock
- [ ] Animasi keyframe penuh (seal/rewind/sword slash)

### Fase 4 — Polish
- [ ] Model `.geo.json` → model Java roda 3D penuh
- [ ] Balance vs Bedrock v1.4.0, camera shake
- [ ] Multiplayer test, dedicated server
- [ ] Release jar + docs

---

## 4. Catatan penting

- `main.js` Bedrock sudah **minified** — logika dibaca dari konstanta, string, dan `en_US.lang`, bukan dari kode yang rapi. Porting dilakukan berdasarkan perilaku, bukan transliterasi baris-per-baris.
- Texture item Bedrock (`textures/items/xvoid/*.png`) dipakai ulang apa adanya di Java.
- Model 3D Bedrock (`.geo.json`) **tidak bisa dipakai langsung** di Java — harus dibuat ulang sebagai model Java (Blockbench → JSON).
- Untuk Fase 1, material armor/pedang masih pakai material vanilla (Netherite/Diamond) supaya stabil. Material custom Mahoraga dibuat di Fase 2/3.
