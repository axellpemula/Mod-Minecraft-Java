# Void Sovereign — Fabric 1.21.1 Edition

Mod **Void Sovereign v1.7.5** (*oleh xVoid Axell*) untuk **Minecraft Java Edition `1.21.1` (Fabric)** dengan **Modern Frosted Dashboard GUI (5 Tema)**, **Live Weapon Damage Studio (All-Weapons Catalog + Bag Forge)**, **Quick Radial Wheel (`R`)**, **Arcane Altar Item Upgrader + Pity**, dan **Return by Death (`20s` True Chrono World Rewind)**.

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
`Hardcore Tools Fabric/build/libs/void-sovereign-fabric-1.7.5+1.21.1.jar`
*(Salin file `.jar` tersebut — bukan yang berakhiran `-sources.jar` — ke dalam folder `.minecraft/mods/` bersama `fabric-api`).*

## Cara Membuka Menu di Dalam Game
- Tekan tombol **`H`** untuk membuka **Dashboard Utama** *(bisa diubah kapan saja di `Options → Controls → Key Binds → Void Sovereign`)*,
- Tekan tombol **`R`** sekali (tanpa ditahan) untuk membuka **Quick Radial Wheel**,
- Tekan tombol **`G`** untuk toggle **Fullbright**, **atau**
- Ketik command **`/vs`**, **`/vs config`**, **`/hc`**, atau **`/hc:config`** *(langsung jalan di dunia **Hardcore** maupun **Survival** tanpa perlu menyalakan Cheat / Open to LAN!)*.
- Shortcut cepat kustomisasi damage senjata di tangan: **`/vs damage <angka> [speed]`** atau **`/vs damage reset`**.
