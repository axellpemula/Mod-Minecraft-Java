# Hardcore Tools — Fabric 1.21.1 Edition

Port mod **Hardcore Tools v1.5.0** untuk **Minecraft Java Edition `1.21.1` (Fabric)** dengan **Split-Pane Crimson Dashboard GUI**.

## Persyaratan
- **Minecraft Java Edition**: `1.21.1`
- **Java**: `JDK / JRE 21+`
- **Fabric Loader**: `0.15.11+` (direkomendasikan `0.16.2+`)
- **Fabric API**: `0.116.18+1.21.1` (atau versi Fabric API `1.21.1` apa pun)

## Cara Build `.jar`
Jalankan perintah berikut dari dalam folder `Hardcore Tools Fabric`:

- **Windows (PowerShell / CMD)**:
  ```bat
  .\gradlew.bat build
  ```
- **Linux / macOS**:
  ```bash
  ./gradlew build
  ```

File `.jar` siap pakai akan dihasilkan di:
`Hardcore Tools Fabric/build/libs/hardcore-tools-fabric-1.5.0+1.21.1.jar`
*(Salin file `.jar` tersebut — bukan yang berakhiran `-sources.jar` — ke dalam folder `.minecraft/mods/` bersama `fabric-api`).*

## Cara Membuka Menu di Dalam Game
- Tekan tombol **`H`** di keyboard *(bisa diubah kapan saja di `Options → Controls → Key Binds → Hardcore Tools`)*, **atau**
- Ketik command **`/hc`**, **`/hc config`**, atau **`/hc:config`** *(langsung jalan di dunia **Hardcore** maupun **Survival** tanpa perlu menyalakan Cheat / Open to LAN!)*.

## Fitur Lengkap (12 Tab Crimson Dashboard)
1. **Protection**: *Immortal Mode* (kebal damage, anti-lapar, penyelamat Void) + *Auto-Totem & Low-HP Panic Saver* langsung dari slot tas mana pun + *Offhand Auto-Refill*.
2. **Vein & Tree**: *Vein Miner* (26-arah BFS s/d 128 blok) + *Tree Capitator* (menebang pohon alami tanpa merusak bangunan kayu pemain) + *Auto-Collect* & *Instant Leaf Decay*.
3. **Sneak Grow**: Jongkok (*Sneak*) untuk mempercepat pertumbuhan tanaman, pohon, tebu, kaktus, Nether Wart, hingga tanaman dari mod lain (*Instant* / *Stage-by-Stage*).
4. **Ore Plus+**: Generasi vein tambang ekstra di *Overworld* & *Nether* + pengganda hasil tambang `x2`–`x4` (*Silk Touch* aman tanpa duplikasi blok ore).
5. **Enchants (Native Lv. 25)**: Preview 3D item di tangan utama secara *live*, *Auto Max (Vanilla)* vs *Auto Max (Over-Enchant Lv. 25)*, toggle *God Stack (Bypass Conflicts)*, slider level `1–25` (`I`–`XXV`), serta hapus satu/semua enchant.
6. **Enchanted Books**: Generator buku *Enchanted Book* semua jenis (termasuk enchant dari mod lain!) dari level Vanilla hingga `XXV` (`Lv. 25`).
7. **Repair Workshop**: Perbaiki item di tangan, armor terpakai, hotbar, atau seluruh isi tas ke durabilitas `100%` dalam 1 klik.
8. **Potion Effects**: Studio 16 efek buff pilihan dengan durasi *Infinite (`∞`)* atau detik kustom, tier `I`–`X`, serta penghapus efek negatif.
9. **XP Manager**: Tambah/kurangi level (`+1`, `+10`, `+50`, `+100`, `-10`), atur level spesifik, atau reset XP.
10. **Teleport & Locator**: *Locator* otomatis & teleport aman ke **24 Bioma**, **19 Struktur** (*Trial Chambers*, *Ancient City*, *Bastion*, *Fortress*, *End City*, *Stronghold*, dll. — dilengkapi pengaman anti-atap Bedrock Nether!), pemain online, maupun koordinat lintas dimensi.
11. **Waypoints**: Simpan koordinat dengan label warna kustom (hingga 64 waypoint per pemain) dan *warp* instan lintas dimensi.
12. **Command Console**: Toggle cepat *GameRules* (`keepInventory`, `mobGriefing`, siklus siang/cuaca, `PvP`), pengatur waktu/cuaca, *Heal & Feed*, *Clear Ground Lag*, hingga eksekutor command langsung di dunia Hardcore.
