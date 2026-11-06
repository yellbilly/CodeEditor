# Code Editor – Manuale d'uso

## 1. Primi passi

- **Nuovo / Apri… / Salva / Salva con nome…** sono nel menu in alto a destra (⋮). Salva è anche un pulsante sulla barra.
- Ogni file aperto è una **scheda**. Tocca una scheda per passare a quel file; **tieni premuto** per chiuderla.
- Un pallino `●` davanti al nome indica modifiche non salvate. Chiudendo la scheda, l'app chiede se salvare.
- Puoi aprire file anche da Drive o dal gestore file: l'app sceglie il linguaggio dall'estensione.
- Se l'app viene chiusa, **schede e testo non salvato vengono ripristinati** alla riapertura.

## 2. Scrivere e modificare

- **Annulla / Ripeti**: ogni scheda ha la sua cronologia; la digitazione continua conta come un'unica modifica.
- **Barra dei simboli** sopra la tastiera: `{ } ( ) [ ] ; : " ' < > / \ …` e il tasto **TAB** (4 spazi).
- **Auto-indentazione**: andando a capo si mantiene il rientro della riga; dopo `{`, `(`, `[` (e `:` in Python) viene aggiunto un livello.
- **Tab / Shift+Tab** (tastiera fisica) o il tasto TAB: indenta e deindenta anche più righe selezionate.
- Menu → **Duplica riga**, **Elimina riga**, **Commenta/decommenta** (usa il simbolo del linguaggio), **MAIUSCOLO**, **minuscolo**, **Vai alla riga…**.

## 3. Trova e sostituisci

Menu → **Trova / Sostituisci** apre la barra in alto.

- Scrivi il testo da cercare: tutte le occorrenze vengono evidenziate. Le frecce ▲ ▼ vanno alla precedente/successiva.
- `Aa` distingue maiuscole e minuscole. `.*` attiva le **espressioni regolari**.
- **Sostituisci** cambia l'occorrenza selezionata e passa alla successiva; **Tutto** le cambia tutte in un'unica modifica annullabile.

### Espressioni regolari

Con `.*` attivo il campo "Trova" usa la sintassi regex di Java/Kotlin. `^` e `$` valgono per ogni riga.
Nel campo "Sostituisci con": `$1`, `$2`… sono i gruppi tra parentesi, `$0` è tutta la corrispondenza,
`\n` è un a capo, `\t` una tabulazione, `\\` un backslash, `\$` un dollaro.

- Commentare tutte le righe: trova `^`, sostituisci con `// `
- Togliere gli spazi a fine riga: trova `[ \t]+$`, sostituisci con niente
- Eliminare le righe vuote: trova `^\n`, sostituisci con niente
- Scambiare "Nome Cognome": trova `^(\w+) (\w+)$`, sostituisci con `$2 $1`
- Trasformare le virgole in righe: trova `,`, sostituisci con `\n`
- Racchiudere i numeri tra `<>`: trova `\d+`, sostituisci con `<$0>`

Consigli: se il campo di ricerca diventa rosso, l'espressione non è valida. Per far corrispondere il punto anche
agli a capo usa `(?s)` all'inizio; `\w` copre le lettere senza accenti, con `(?U)` anche quelle accentate.
Prima di un "Tutto" importante puoi sempre tornare indietro con **Annulla**.

## 4. Aspetto

- **Linguaggio**: riconosciuto dall'estensione (Kotlin, Java, Python, JS/TS, C/C++, HTML, XML, CSS, JSON, YAML, Shell, SQL). Per cambiarlo: menu → **Linguaggio**.
- **Numeri di riga**, **A capo automatico**: voci con spunta nel menu.
- **Tipo di carattere…**: Monospace (di sistema) o Courier Prime. **Aumenta / Riduci carattere** per la dimensione.
- **Tema chiaro/scuro**: menu → Tema. Le scelte vengono ricordate.

## 5. Barra di stato

In basso: riga e colonna del cursore, righe totali, caratteri, **codifica** (UTF-8, UTF-8 BOM o ANSI),
**fine riga** (LF o CRLF) e linguaggio. Codifica e fine riga del file originale vengono mantenute al salvataggio.

## 6. Scorciatoie (tastiera fisica)

`Ctrl+S` salva · `Ctrl+O` apri · `Ctrl+N` nuovo · `Ctrl+W` chiudi scheda · `Ctrl+F` / `Ctrl+H` trova e sostituisci ·
`Ctrl+G` vai alla riga · `Ctrl+D` duplica riga · `Ctrl+L` elimina riga · `Ctrl+/` commenta ·
`Ctrl+Z` annulla · `Ctrl+Y` ripeti · `Tab` / `Shift+Tab` indenta / deindenta.

## 7. Limiti

- File fino a 8 MB. Oltre circa 400.000 caratteri la colorazione della sintassi si disattiva per mantenere l'app fluida.
- L'app non usa Internet e non raccoglie dati: i file restano dove li salvi.
