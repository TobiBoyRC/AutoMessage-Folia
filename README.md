# AutoMessage — Folia-ready modernizace

## Jak sestavit a ověřit (proveď u sebe — sandbox tady nemá přístup na repo.papermc.io)

```bash
cd automessage-folia
# vygeneruje gradle wrapper, pokud ho nemáš
gradle wrapper --gradle-version 8.10
./gradlew build
```

Výstup: `build/libs/AutoMessage-2.0.0.jar`. Pokud build spadne, pošli mi celý
stacktrace — konkrétně první "cannot find symbol" nebo "cannot access" řádek.

### Rychlý test
1. Hoď jar do `plugins/` na **Folia** serveru (1.21.x).
2. Sleduj log při startu — hledej `folia-supported: true` respect a žádný
   `UnsupportedOperationException` kolem schedulingu.
3. `/automessage` jako hráč bez práv → musí odmítnout.
4. `/automessage` s `automessage.reload` → přenačte config, staré
   naplánované vysílání se zruší (`ScheduledTask.cancel()`), naplánuje nové.
5. Nech server běžet přes jeden cyklus `timer` → zpráva se objeví v chatu
   všem hráčům i v konzoli.

## Co se změnilo oproti originálu a proč

| Změna | Důvod |
|---|---|
| `BukkitRunnable.runTaskTimer` → `Bukkit.getGlobalRegionScheduler().runAtFixedRate` | Broadcast se týká všech hráčů napříč regiony, ne jednoho místa/entity — patří do global scheduleru. Legacy `BukkitScheduler` na Folia pro tenhle typ úlohy neběží. |
| `int bukkitidTask` → `ScheduledTask broadcastTask` | Folia scheduler nevrací číselné task ID, vrací handle s `.cancel()`. |
| `HashMap<Integer,String>` → `List<String>` | Původní mapa jen simulovala pořadí seznamu ručním čítačem. Zbytečná komplexita, navíc zdroj bugu níž. |
| Oprava `getRandom()` off-by-one | Originál mapoval `nextInt(size) == 0` na `1`, takže zpráva #1 měla dvojnásobnou šanci a poslední zpráva se nikdy nevybrala. Nová verze používá index přímo. |
| `ChatColor` + `Bukkit.broadcastMessage` → Adventure `Component` | Legacy string API je na moderním Paper/Folia deprecated. `&`-kódy v config.yml zůstávají beze změny (parsují se přes `LegacyComponentSerializer`), takže stávající configy fungují beze změny. |
| `/tellraw` přes `dispatchCommand` → `GsonComponentSerializer` přímo | Config formát (JSON string) je stejný, ale posílá se přes API, ne přes spouštění příkazu — rychlejší a nezávislé na command-dispatch threadingu. |
| `plugin.yml`: `api-version: '1.21'`, `folia-supported: true` | Bez `folia-supported: true` Folia plugin vůbec nenačte. |
| Gradle + `dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT` (compileOnly) | folia-api je nadmnožina Paper API, takže stejný jar běží i na čistém Paperu/Purpuru — není potřeba dvojí build. |

## Co NENÍ ověřeno skutečnou kompilací

Tenhle sandbox nemá síťový přístup na `repo.papermc.io` (potvrzeno
`x-deny-reason: host_not_allowed`), takže jsem nemohl stáhnout folia-api jar
a spustit `javac`/`gradle build` naostro. Všechny API signatury (scheduler
metody, Adventure serializery) jsem ověřil křížově přes aktuální javadoc
a oficiální PaperMC dokumentaci (ne z paměti), ale **první skutečný build
proveď ty** a pošli mi výsledek.
