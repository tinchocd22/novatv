# NOVA TV — Android Box

App IPTV para Android TV / Mi Box / Mecool.
Servidor Flask (Python) embebido + WebView optimizada para control remoto.

---

## ¿Cómo instalar?

### Opción A — Compilar APK con GitHub (RECOMENDADO, gratis)

1. **Creá una cuenta en GitHub** en https://github.com/signup (si no tenés)

2. **Creá un repositorio nuevo**:
   - Entrá a https://github.com/new
   - Nombre: `novatv`
   - Público o privado, cualquiera sirve
   - Clic en "Create repository"

3. **Subí todos estos archivos**:
   - Podés arrastrar la carpeta completa en la web de GitHub
   - O usar Git si sabés: `git init && git add . && git push`

4. **La compilación arranca sola** (GitHub Actions):
   - Entrá a tu repo → pestaña **"Actions"**
   - Vas a ver el workflow "Build NOVA TV APK" corriendo
   - Esperá ~15-20 minutos

5. **Descargá el APK**:
   - Al terminar el workflow → clic en el nombre de la ejecución
   - Bajá hasta "Artifacts" → descargá **NovaTV-debug.zip**
   - Descomprimilo → tenés `app-debug.apk`

6. **Instalá en el Android Box**:
   - Copiá el APK a un pendrive → insertalo en el box
   - O mandalo por WhatsApp/email y abrilo desde el box
   - O usá un file manager para instalarlo
   - En ajustes del box: activá "Fuentes desconocidas" si pide

---

### Opción B — Correr el servidor en PC y abrir en navegador del box

Si querés probar primero SIN compilar nada:

1. Instalá dependencias en tu PC:
   ```
   pip install flask requests
   ```

2. Corré el servidor:
   ```
   cd server
   python server.py
   ```

3. En el Android Box abrí el navegador y entrá a:
   ```
   http://TU-IP-LOCAL:5000
   ```
   (Ej: `http://192.168.1.100:5000`)

4. Para saber tu IP: en Windows → cmd → `ipconfig` → busca IPv4

---

## Uso de la app

1. Al abrir, si ya cargaste una lista antes, la carga automáticamente
2. Si es la primera vez, pegá la URL de tu lista M3U en el campo de arriba
3. Clic en "Cargar" o Enter
4. Navegá con las flechas del control remoto:
   - **↑↓** — moverse por la lista
   - **←→** — cambiar entre sidebar y grilla / entre pestañas
   - **OK / Enter** — seleccionar
   - **Atrás** — cerrar player

## Controles en reproducción

| Tecla | Acción |
|-------|--------|
| ← → | Retroceder / adelantar 10 segundos |
| ↑ ↓ | Subir / bajar volumen |
| OK / Space | Play / Pause |
| Atrás / Esc | Cerrar player |
| Menú | Pantalla completa |

---

## Estructura del proyecto

```
novatv-android/
├── server/                  ← Servidor Flask (para pruebas en PC)
│   ├── server.py
│   └── static/
│       └── index.html       ← UI web optimizada para TV
├── apk/                     ← Proyecto Android
│   ├── app/
│   │   └── src/main/
│   │       ├── java/com/novatv/
│   │       │   └── MainActivity.kt
│   │       ├── python/
│   │       │   └── server.py   ← Flask embebido en APK
│   │       ├── assets/
│   │       │   └── index.html  ← UI copiada al APK
│   │       └── AndroidManifest.xml
│   ├── build.gradle
│   └── gradlew
└── .github/
    └── workflows/
        └── build.yml        ← Compilación automática en GitHub
```

---

## Notas técnicas

- **Python embebido**: usa [Chaquopy](https://chaquo.com/chaquopy/) para correr Python 3.11 + Flask dentro del APK
- **Video**: usa el `<video>` nativo de Android WebView (ExoPlayer interno) — compatible con HLS, MP4, TS
- **Sin VLC**: el player nativo de Android es suficiente para IPTV
- **Persistencia**: la URL de la lista se guarda en el almacenamiento interno del box
