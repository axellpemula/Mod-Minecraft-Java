# Wheel of Adaptation

> Project Untuk mod minecraft java fabric versi 1.21.1+

Mod **Wheel of Adaptation** — portingan Java Fabric dari addon Bedrock [axellpemula/Wheel-of-Adaptation](https://github.com/axellpemula/Wheel-of-Adaptation) v1.4.0 (Mahoraga's Dharma Wheel, Jujutsu Kaisen).

> Detail analisis addon + roadmap porting ada di **[PORTING.md](PORTING.md)**.

---

## 📦 Status Porting — Fase 1 (Core)

| Yang sudah ada | Keterangan |
|---|---|
| `WheelOfAdaptationMod.java` | Titik masuk mod (ModInitializer) |
| `ModItems.java` | **Wheel** (helm), **Sword of Extermination**, **Seal of Judgment**, **Mode Switcher**, **Adaptation Settings**, armor **Mahoraga** (chest/legs/boots) |
| `WheelDamageHandler.java` | Sistem adaptasi dasar: kena damage → Analysing (3 hit) → Adapted → nullify + heal + toast |
| `AdaptationType/Manager/Data` | Tipe fenomena (fire, lava, explosion, fall, projectile, mob, effect, nature) + data per-player |
| `ModItemGroups.java` | Creative tab khusus "Wheel of Adaptation" |
| `WheelOfAdaptationClient.java` | Titik masuk client |
| Recipe crafting | Sesuai Bedrock: Wheel, Sword, Seal |
| Texture | Asli dari RP Bedrock (`textures/items/xvoid/*.png`) |

**Cara coba adaptasi:** pakai Wheel di kepala → kena lava/api/ledakan/mob 3x → muncul toast `Adapting: ... (1/3)` → hit ke-3 jadi `Adapted` → damage tipe itu tidak masuk lagi + heal.

Fase 2 (evolved, effect flip, mode, sword slash/air blast/dash, seal domain, RBD, menu, sound) dan Fase 3 (roda 3D di kepala, animasi, particle custom, HUD) menyusul — lihat PORTING.md.

Mod ID: `wheel_of_adaptation`  
Package: `com.wheelofadaptation`  
Minecraft: `1.21.1` | Loader: Fabric | Java: `21`

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
