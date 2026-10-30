# Pubblicare Code Editor su GitHub (solo terminale)

Serve solo `git` (controlla con `git --version`) e un account su github.com.

## 1. Prima configurazione di git (una volta sola)

```bash
git config --global user.name "FabioSirigu"
git config --global user.email "fabiosirigu@gmail.com"
```

## 2. Crea il repository vuoto su GitHub

Dal browser: <https://github.com/new> → nome `CodeEditor` → *Public* → **non** spuntare README, .gitignore o licenza
(ci sono già) → *Create repository*.

## 3. Carica il codice

Dalla cartella del progetto:

```bash
git init
git add .
git status          # controlla: NON devono comparire .jks, keystore.properties, build/
git commit -m "Prima versione"
git branch -M main
git remote add origin https://github.com/TUO_UTENTE/CodeEditor.git
git push -u origin main
```

Al `git push` GitHub chiede l'accesso:
- **Windows / macOS**: si apre il browser, accedi e conferma.
- **Linux o se chiede "Password"**: GitHub non accetta la password dell'account, serve un *token*: github.com →
  Settings → Developer settings → Personal access tokens → Fine-grained tokens → repository `CodeEditor`,
  permesso *Contents: Read and write*. Incolla il token al posto della password.

## 3b. Alternativa con chiave SSH (niente token)

```bash
ssh-keygen -t ed25519 -C "fabiosirigu@gmail.com"     # invio a tutte le domande
cat ~/.ssh/id_ed25519.pub                             # su Windows: type %USERPROFILE%\.ssh\id_ed25519.pub
```
Copia l'output in github.com → Settings → SSH and GPG keys → New SSH key. Poi usa l'indirizzo SSH:
```bash
git remote set-url origin git@github.com:TUO_UTENTE/CodeEditor.git
git push -u origin main
```

## 4. Crea la chiave di firma per l'APK (una volta sola)

L'APK di release deve essere firmato per potersi installare. `keytool` è incluso nel JDK di Android Studio.

```bash
keytool -genkeypair -v -keystore codeeditor-release.jks -alias codeeditor -keyalg RSA -keysize 2048 -validity 10000
```

Crea il file `keystore.properties` nella cartella del progetto (è escluso da git, non va mai pubblicato):

```
storeFile=codeeditor-release.jks
storePassword=LA_TUA_PASSWORD
keyAlias=codeeditor
keyPassword=LA_TUA_PASSWORD
```

Fai un backup del `.jks` e della password: se li perdi non potrai più pubblicare aggiornamenti installabili sopra
la versione precedente.

## 5. Costruisci l'APK

```bash
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
```

Risultato: `app/build/outputs/apk/release/app-release.apk`.
(Servono JDK 17+ e l'Android SDK; se il comando non trova l'SDK crea `local.properties` con
`sdk.dir=/percorso/del/tuo/Android/Sdk`.)

## 6. Pubblica la Release con l'APK

Dal browser, senza installare nulla: pagina del repository → *Releases* → *Create a new release* → tag `v1.0` →
titolo → trascina `app-release.apk` → *Publish release*.

## 7. Nuove versioni

1. In `app/build.gradle.kts` aumenta `versionCode` (2, 3, …) e `versionName`.
2. ```bash
   git add .
   git commit -m "Descrizione della modifica"
   git push
   ```
3. Ripeti i punti 5 e 6 con un nuovo tag (`v1.1`, …).
