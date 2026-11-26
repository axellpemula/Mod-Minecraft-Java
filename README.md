# Wheel of Adaptation

> Project Untuk mod minecraft java fabric versi 1.21.1+

Mod **Wheel of Adaptation** — portingan Java Fabric dari addon Bedrock [axellpemula/Wheel-of-Adaptation](https://github.com/axellpemula/Wheel-of-Adaptation) v1.4.0 (Mahoraga's Dharma Wheel, Jujutsu Kaisen).

> Detail analisis addon + roadmap porting ada di **[PORTING.md](PORTING.md)**.

---

## 📦 Status Porting — Fase 4 (Polish + release)

| Yang sudah ada | Keterangan |
|---|---|
| `WheelOfAdaptationMod.java` | Titik masuk mod (ModInitializer) |
| `ModItems.java` | **Wheel** (slot khusus), **Sword**, **Seal**, **Mode Switcher**, **Barehand**, **Settings**, armor **Mahoraga** (material custom Bedrock) |
| `WheelDamageHandler.java` | Adaptasi: Analysing → Adapted → Evolved + **Return by Death** + **reflect** Evolved |
| `WheelTickHandler.java` | **Roda 3D emas berputar di kepala** (model elements + `ItemDisplay` NBT transform), ring scan particle, effect flip, auto-save |
| `models/item/wheel_of_adaptation.json` | **Model 3D roda** (rim + 4 jari-jari + hub, swatch emas dari texture Bedrock) + display transform GUI/hand/head |
| `CameraShakeMixin` + `WheelShakeState` | **Camera shake** saat Adapted/Evolved/RBD/Seal; `/woa shake on|off` |
| `ShakePayload/ShakeConfigPayload` | Networking S2C camera shake |
| `WheelUseHandler.java` | Mode Switcher, Sword slash/air blast/dash, Barehand teleport kick/guard, Seal domain + **quake** |
| `WheelAttackHandler.java` | Attack adaptation (pierce per-mode) + **Purify** zombie villager |
| `ModParticles.java` + `AdaptWheelParticle` | **Particle custom** `adapt_wheel` (texture RP Bedrock) |
| `WheelScreenHandler/Screen` | **Slot khusus Wheel** + UI custom (mode, adapted list, progress) |
| `WheelHud.java` | HUD emblem + progress bar scan + watermark credit |
| `WheelCommands.java` | `/woa menu/list/mode/reset/performance/armor/shake` |
| `ModSounds.java` | 28 sound custom dari RP Bedrock |
| `AdaptationManager.java` | Persistensi JSON per-player (load join, save leave/tick) |

**Cara coba:**
1. Craft **Wheel** → tekan **R** (atau klik kanan Adaptation Settings / `/woa menu`) → taruh Wheel di slot khusus → roda 3D muncul berputar di atas kepala
2. Kena lava/api/mob 3x → lihat ring scan berputar + roda makin cepat + HUD progress → jadi `Adapted` → damage tipe itu tidak masuk lagi
3. Klik kanan **Mode Switcher** → ganti Attack/Balance/Defense
4. Klik kanan **Sword** → slash (potong block) · sneak+klik kanan → air blast · klik kanan di udara → dash
5. Pukul musuh yang sama 4x dengan **Sword/Barehand** → attack adapted → bonus damage
6. Klik kanan **Barehand** → teleport kick ke musuh terdekat · sneak+klik kanan → guard
7. Klik kanan **Seal** (butuh 3 adapted) → domain bind musuh
8. Kalau mau mati padahal sudah adapted ≥3 → **Return by Death**: teleport ke posisi 30 detik lalu, heal 50%, 1 adapted terpakai (cooldown 60s)
9. **Sword** hit Zombie Villager → purify (cure jadi Villager)

Perbedaan dari Bedrock (sengaja, karena Java bebas): Wheel pakai **slot khusus** + roda 3D `ItemDisplay` di kepala, UI **custom Java** (bukan form Bedrock), **particle custom** `adapt_wheel` dari RP Bedrock, **Return by Death** mekanik baru Java, **camera shake** via mixin, armor Mahoraga punya **material custom** (chest 6/legs 5/boots 2 persis Bedrock). Lihat PORTING.md.

Mod ID: `wheel_of_adaptation`  
Package: `com.wheelofadaptation`  
Minecraft: `1.21.1` | Loader: Fabric | Java: `21`

---

## 🚀 Cara Dapatkan Jar (2 cara)

### Cara A — Build lokal (butuh JDK 21)
```bash
./gradlew build            # Mac/Linux
.\gradlew.bat build        # Windows
```
Jar hasil: `build/libs/wheel-of-adaptation-1.0.0.jar`

### Cara B — GitHub Actions (tanpa install apa-apa)
1. Buka tab **Actions** di repo GitHub ini
2. Pilih workflow run terbaru (commit terakhir) → panel kanan **Artifacts** → download **Artifacts**
3. Ekstrak → ambil `wheel-of-adaptation-1.0.0.jar` (abaikan `-sources`)

---

## 🧪 Test Multiplayer / Dedicated Server (checklist)

1. **Dedicated server**: taruh jar + Fabric Loader + Fabric API di folder `mods/` server 1.21.1 → jalankan → pastikan log ada `Wheel of Adaptation loaded!`
2. **Sinkronisasi data**: 2 client join → satu player equip Wheel (R) → cek roda muncul di pelayan lain; keluar-masuk server → adaptasi masih ada (cek `<world>/wheel_of_adaptation/playerdata/<uuid>.json`)
3. **Menu + slot**: buka Wheel Menu di multiplayer → taruh/cabut Wheel → nullify jalan/tidak jalan
4. **Camera shake**: `/woa shake off` → adaptasi tidak bikin layar goyang; `on` → goyang lagi
5. **Return by Death**: adapted ≥3 → bunuh diri → harus rewind + cooldown aktif

---

## 🚀 Panduan Step-by-Step (dari nol sampai main)

### Langkah 1 — Install Java 21

Minecraft 1.21.1 **wajib** Java 21. Java versi lain tidak akan bisa build.

- **Windows:** download JDK 21 dari [Adoptium](https://adoptium.net/) atau [Oracle](https://www.oracle.com/java/technologies/downloads/), install, lalu cek:
  ```
  java -version
  ```
  Harus muncul `21.x.x`.
- **Mac/Linux:** install JDK 21 lewat package manager kamu, atau download dari link di atas.

> ⚠️ Kalau `java -version` masih 17 atau 8, build akan gagal. Set `JAVA_HOME` ke folder JDK 21.

### Langkah 2 — Clone & Buka Project

```bash
git clone https://github.com/axellpemula/Mod-Minecraft-Java.git
cd Mod-Minecraft-Java
```

Buka folder ini di **IntelliJ IDEA** (recommended) atau VS Code + Extension Pack for Java.

### Langkah 3 — Build Mod Pertama Kamu

**Windows:**
```powershell
.\gradlew.bat build
```

**Mac/Linux:**
```bash
./gradlew build
```

Pertama kali build akan download Minecraft + Fabric (butuh internet, bisa 5–10 menit).  
Kalau sukses, file `.jar` ada di:
```
build/libs/wheel-of-adaptation-1.0.0.jar
```

### Langkah 4 — Test Langsung Tanpa Install ke Minecraft

Cara tercepat buat testing:

```bash
./gradlew runClient
```

Ini akan membuka Minecraft 1.21.1 dengan mod kamu sudah aktif. Buat world baru → buka Creative Inventory → cari tab **Wheel of Adaptation** → kamu akan lihat Adaptation Wheel & Pedestal.

> Untuk run server: `./gradlew runServer`

### Langkah 5 — Install ke Minecraft Asli

1. Install **Fabric Loader** untuk Minecraft 1.21.1 dari [fabricmc.net](https://fabricmc.net/use/)
2. Install **Fabric API** versi 1.21.1 (taruh di folder `mods`)
3. Copy file `.jar` dari `build/libs/` ke folder `mods` Minecraft kamu:
   - Windows: `%appdata%\.minecraft\mods`
   - Mac: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
4. Buka Minecraft lewat profile Fabric → main!

---

## 🛠️ Cara Tambah Item / Block Baru

### Tambah Item Baru

1. Buka `src/main/java/com/wheelofadaptation/item/ModItems.java`, tambah:
   ```java
   public static final Item ITEM_BARU = register(
       new Item(new Item.Properties().stacksTo(64)),
       "item_baru"
   );
   ```
2. Buat model: `src/main/resources/assets/wheel_of_adaptation/models/item/item_baru.json`
   ```json
   {
     "parent": "minecraft:item/generated",
     "textures": { "layer0": "minecraft:item/diamond" }
   }
   ```
3. Tambah nama di `lang/en_us.json`:
   ```json
   "item.wheel_of_adaptation.item_baru": "Item Baru"
   ```
4. Tambahkan ke creative tab di `ModItemGroups.java`:
   ```java
   output.accept(ModItems.ITEM_BARU);
   ```
5. Build ulang: `./gradlew build`

### Tambah Block Baru

1. Buka `ModBlocks.java`, tambah field baru (copy pola `ADAPTATION_PEDESTAL`).
2. Buat 3 file JSON:
   - `assets/wheel_of_adaptation/blockstates/block_baru.json`
   - `assets/wheel_of_adaptation/models/block/block_baru.json`
   - `assets/wheel_of_adaptation/models/item/block_baru.json`
3. Tambah nama di `lang/en_us.json` dengan key `block.wheel_of_adaptation.block_baru`.
4. Tambahkan ke `ModItemGroups.java`, lalu build.

### Ganti Texture Placeholder

Sekarang model masih pakai texture vanilla (`compass`, `stone`) biar langsung jalan.  
Untuk texture custom:

1. Taruh file PNG 16×16 di:
   - Item: `assets/wheel_of_adaptation/textures/item/nama_item.png`
   - Block: `assets/wheel_of_adaptation/textures/block/nama_block.png`
2. Ubah model JSON jadi:
   ```json
   { "layer0": "wheel_of_adaptation:item/nama_item" }
   ```

---

## 📁 Struktur Project

```
.
├── build.gradle                  # Konfigurasi build (Loom, dependencies)
├── gradle.properties             # Versi Minecraft, Loader, Fabric API
├── settings.gradle               # Nama project
├── gradlew / gradlew.bat         # Gradle wrapper (tidak perlu install Gradle manual)
├── src/
│   ├── main/java/com/wheelofadaptation/
│   │   ├── WheelOfAdaptationMod.java   # ModInitializer
│   │   ├── item/ModItems.java          # Semua item
│   │   ├── block/ModBlocks.java        # Semua block
│   │   └── itemgroup/ModItemGroups.java # Creative tab
│   ├── client/java/com/wheelofadaptation/client/
│   │   └── WheelOfAdaptationClient.java # ClientModInitializer
│   └── main/resources/
│       ├── fabric.mod.json             # Metadata mod
│       ├── assets/wheel_of_adaptation/ # Model, lang, texture, icon
│       └── data/wheel_of_adaptation/   # Recipe crafting
└── build/libs/                         # Hasil build (.jar)
```

---

## 💡 Ide Fitur Wheel of Adaptation (langkah selanjutnya)

Template ini sengaja kosong untuk fitur — kita isi bareng step-by-step. Beberapa ide:

1. **Klik kanan Adaptation Wheel** → putar roda → dapat efek acak (speed, jump, night vision, dll)
2. **Adaptation Pedestal** → taruh wheel di atasnya → buka GUI roda
3. **Sistem adaptasi** → makin sering putar, makin kuat efeknya
4. **GUI roda** seperti wheel of fortune dengan 8–12 pilihan
5. **Cooldown & cost** → butuh item tertentu untuk putar

Mau mulai dari yang mana? Tinggal bilang aja!

---

## ❓ Troubleshooting

| Masalah | Solusi |
|---|---|
| `UnsupportedClassVersionError` / Java error | Pakai JDK 21, cek `java -version` |
| Build gagal karena network | Butuh internet untuk download pertama kali |
| Mod tidak muncul di Minecraft | Pastikan Fabric Loader + Fabric API terinstall, jar ada di folder `mods` |
| Item tidak muncul di creative tab | Cek `ModItemGroups.java` sudah `output.accept(...)` |
| Texture hilang / ungu-hitam | Cek path texture di model JSON & nama file PNG |

---

## 📚 Referensi

- [Fabric Docs](https://docs.fabricmc.net/develop/)
- [Fabric Example Mod (1.21.1)](https://github.com/FabricMC/fabric-example-mod/tree/1.21.1)
- [Fabric API](https://fabricmc.net/use/)
- [Minecraft Wiki — Modding](https://minecraft.wiki/w/Modding)

---

*Dibuat dengan ❤️ untuk belajar modding Minecraft Java — Fabric 1.21.1*
