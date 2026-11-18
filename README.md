# mTLS Kiosk

Pełnoekranowa przeglądarka (WebView) na Androida dla usług za Cloudflare mTLS.

- brak paska adresu, ukryte paski systemowe (wysuwane gestem)
- adres startowy ustawiany w aplikacji
- certyfikat klienta pobierany natywnie z magazynu Androida (KeyChain) — ten sam,
  który instalujesz w *Ustawienia → Bezpieczeństwo → Szyfrowanie i dane logowania → Zainstaluj certyfikat → Certyfikat użytkownika VPN i aplikacji*
- wybór certyfikatu zapamiętany (bez pytania przy każdym starcie); WebView-owy cache wyboru czyszczony przy każdym uruchomieniu
- przy zamknięciu (i dodatkowo przy starcie) kasowane: cache HTTP, historia, dane formularzy;
  opcjonalnie cookies + localStorage (przełącznik w ustawieniach — wyłączony domyślnie, żeby np. Home Assistant nie wylogowywał)
- obsługa uploadu plików (input type=file, wiele plików)

## Obsługa

- **Wstecz** cofa w historii; na pierwszej stronie otwiera menu: Odśwież / Strona startowa / Ustawienia / Zamknij.
- Pierwsze uruchomienie → ekran ustawień.
- „Zamknij” z menu = pełne czyszczenie. Zamknięcie swipe'em z ostatnich aplikacji nie zawsze wywołuje czyszczenie, dlatego jest ono powtarzane przy każdym starcie.

## Warianty (flavors)

Każdy wariant to osobna aplikacja na telefonie (własny `applicationId`, nazwa, ikona,
cookies, ustawienia i zgoda na certyfikat):

| flavor        | nazwa        | adres domyślny                  |
|---------------|--------------|---------------------------------|
| `generic`     | mTLS Kiosk   | (pyta przy starcie)             |
| `agh`         | AdGuard Home | https://agh.example.com         |
| `vaultwarden` | Vaultwarden  | https://vw.example.com      |
| `immich`      | Immich       | https://immich.example.com      |

Adres domyślny można zmienić w ustawieniach aplikacji (Wstecz → Ustawienia).
Nowa usługa = nowy blok `create("...")` w `app/build.gradle.kts`.

### Ikona wariantu
Android Studio → prawy klik na `app` → **New → Image Asset** →
Launcher Icons (Adaptive and Legacy), name `ic_launcher`, Asset type: Image →
**Res Directory: `src/<flavor>/res`** (np. `src/agh/res`) → Next → Finish.
Bez tego wariant ma ikonę z kłódką w swoim kolorze.

### Budowanie wariantów
```
gradlew.bat assembleDebug          # wszystkie warianty
gradlew.bat assembleAghDebug       # tylko AdGuard Home
adb install -r app\build\outputs\apk\agh\debug\app-agh-debug.apk
```
W Android Studio wariant wybierasz w panelu *Build Variants*.

## Budowanie

### GitHub Actions (bez instalowania niczego)
1. Wrzuć katalog do (prywatnego) repo na GitHubie.
2. Actions → *Build APK* → po zakończeniu pobierz artefakt `mtls-kiosk-apk`.

Bez sekretów APK jest podpisany kluczem debug, który na runnerze jest losowy — każda nowa wersja wymaga odinstalowania poprzedniej.
Żeby aktualizacje instalowały się „na wierzch”, wygeneruj własny klucz:

```bash
keytool -genkeypair -v -keystore kiosk.jks -alias kiosk -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 kiosk.jks   # wynik -> sekret KEYSTORE_BASE64
```
Sekrety repo: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (=kiosk), `KEY_PASSWORD`.

### Android Studio
Open → katalog projektu → *Build → Build APK(s)*.
