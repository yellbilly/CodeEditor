# Code Editor per Android

Editor di codice semplice ispirato a Notepad++ (Kotlin, nessuna dipendenza oltre ad AndroidX e Material).

## Funzioni

- **Schede multiple** (tocco lungo su una scheda = chiudi), `●` indica le modifiche non salvate
- **Apri / Salva / Salva con nome** tramite il selettore file di Android (anche da Drive, memoria interna, SD)
- Apertura di file di testo da altre app (gestore file → "Apri con" → Code Editor)
- **Evidenziazione sintassi**: Kotlin, Java, Python, JS/TS, C/C++, HTML, XML, CSS, JSON, YAML, Shell, SQL (riconosciuta dall'estensione, cambiabile da menu)
- **Numeri di riga** e riga corrente evidenziata
- **Trova / Sostituisci** con maiuscole/minuscole, **espressioni regolari** (`$1` nei gruppi), evidenziazione di tutte le occorrenze, "Sostituisci tutto"
- **Annulla / Ripeti** illimitati per scheda (le battute consecutive vengono raggruppate)
- Auto-indentazione, Tab = 4 spazi, indenta/deindenta più righe
- Duplica riga, elimina riga, commenta/decommenta, MAIUSCOLO/minuscolo, vai alla riga
- Barra di simboli rapidi sopra la tastiera (`{ } ( ) [ ] ; : " ' < > / \ …`)
- A capo automatico, zoom del carattere, scelta del font (Monospace o Courier), tema chiaro/scuro
- Schermata Informazioni con autore e contatto
- Rilevamento codifica (UTF-8 / UTF-8 BOM / ANSI) e fine riga (LF / CRLF), mantenuti al salvataggio
- **Ripristino automatico della sessione**: schede e testo non salvato restano anche se l'app viene chiusa

## Scorciatoie (tastiera fisica)

`Ctrl+S` salva · `Ctrl+O` apri · `Ctrl+N` nuovo · `Ctrl+W` chiudi scheda · `Ctrl+F`/`Ctrl+H` trova/sostituisci ·
`Ctrl+G` vai alla riga · `Ctrl+D` duplica riga · `Ctrl+L` elimina riga · `Ctrl+/` commenta ·
`Ctrl+Z`/`Ctrl+Y` annulla/ripeti · `Tab`/`Shift+Tab` indenta/deindenta

## Come ottenere l'APK

1. Installa **Android Studio** (qualsiasi versione recente, JDK 17+ incluso).
2. `File → Open` e scegli la cartella `CodeEditor`. Alla prima apertura scarica Gradle e l'SDK necessari.
3. Per provarla: collega il telefono (debug USB attivo) e premi **Run ▶**.
4. Per un APK da installare: `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
   Il file è in `app/build/outputs/apk/debug/app-debug.apk` (copialo sul telefono e aprilo; serve consentire
   l'installazione da origini sconosciute).

Da terminale, con SDK installato e `ANDROID_HOME` impostata: `gradle assembleDebug`
(oppure `./gradlew assembleDebug` dopo aver generato il wrapper con `gradle wrapper`).

## Struttura

```
app/src/main/java/it/fabio/codeeditor/
  MainActivity.kt        schede, menu, file, trova/sostituisci, barra simboli
  CodeEditText.kt        view dell'editor (numeri di riga, undo, indent, ricerca)
  SyntaxHighlighter.kt   regole di colorazione + temi chiaro/scuro
  UndoManager.kt         cronologia annulla/ripeti
  FileIo.kt              lettura/scrittura con codifica e fine riga
  SessionStore.kt        salvataggio automatico delle schede
  Doc.kt                 modello di un documento aperto
```

## Pubblicazione

Vedi [PUBBLICAZIONE.md](PUBBLICAZIONE.md) (GitHub da terminale, firma dell'APK). Licenza: [MIT](LICENSE). Privacy: [PRIVACY.md](PRIVACY.md). Font Courier Prime: [SIL OFL](FONT-LICENSE-CourierPrime.txt).
