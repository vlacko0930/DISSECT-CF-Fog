# MQTT Protokoll Implementáció - Összefoglaló

Ez a dokumentum összefoglalja az MQTT (Message Queuing Telemetry Transport) protokoll implementációját a DISSECT-CF-Fog szimulátorban.

📊 **Vizuális dokumentáció**: Részletes Mermaid diagramok (UML, szekvencia, use case) elérhetők a [`MQTT_DIAGRAMS.md`](MQTT_DIAGRAMS.md) fájlban.

## Tartalomjegyzék

### SimpleMqttQoSExample.java ✅
Helye: `simulator/src/main/java/hu/u_szeged/inf/fog/simulator/demo/mqtt/SimpleMqttQoSExample.java`

**Bemutató:**
- 1 MQTT broker
- 3 eszköz (egy-egy a három QoS szinthez: 0, 1, 2)
- 3 feliratkozó (egy-egy QoS szinthez)
- QoS szintek teljesítmény összehasonlítása
- Metrikák exportálása CSV-be

**Futtatás:** `java hu.u_szeged.inf.fog.simulator.demo.mqtt.SimpleMqttQoSExample`

### MqttHierarchicalTopicsExample.java ✅
Helye: `simulator/src/main/java/hu/u_szeged/inf/fog/simulator/demo/mqtt/MqttHierarchicalTopicsExample.java`

**Bemutató:**
- Hierarchikus témák:
  - `home/livingroom/temperature`
  - `home/bedroom/temperature`
  - `sensors/outdoor/humidity`
  - `industry/machine1/pressure`
- Wildcard feliratkozások:
  - `home/+/temperature` (egyszintű wildcard)
  - `sensors/#` (többszintű wildcard)
  - `industry/#` (összes industry téma)
  - `#` (univerzális feliratkozás)
- 4 eszköz, 4 feliratkozó különböző mintákkal
- Téma alapú üzenet routing ellenőrzés

**Futtatás:** `java hu.u_szeged.inf.fog.simulator.demo.mqtt.MqttHierarchicalTopicsExample`

### MqttLargeScaleExample.java ✅
Helye: `simulator/src/main/java/hu/u_szeged/inf/fog/simulator/demo/mqtt/MqttLargeScaleExample.java`

**Bemutató:**
- 3 broker (cloud-broker, edge-broker-1, edge-broker-2)
- 10 eszköz brokerenként (összesen 30)
- 3 feliratkozó brokerenként (összesen 9)
- Vegyes QoS szintek az eszközök között
- Különböző üzenet frekvenciák eszközönként
- Broker-enkénti teljesítmény összehasonlítás
- Skálázhatóság tesztelés

**Futtatás:** `java hu.u_szeged.inf.fog.simulator.demo.mqtt.MqttLargeScaleExample`SSECT-CF-Fog szimulátorban, amely lehetővé teszi az IoT eszközök közötti hatékony kommunikáció modellezését központi broker használatával.

## Implementált Komponensek (Implemented Components)

### 1. Alapvető MQTT Osztályok (Core MQTT Classes)

#### QoSLevel.java
- **Cél**: Az MQTT QoS szintek enumerációja
- **QoS 0**: "Legfeljebb egyszer" - nincs visszaigazolás
- **QoS 1**: "Legalább egyszer" - visszaigazolással, duplikáció lehetséges
- **QoS 2**: "Pontosan egyszer" - négylépéses handshake, legnagyobb megbízhatóság

#### MqttMessage.java
- **Cél**: MQTT üzenet reprezentáció
- **Funkciók**:
  - Egyedi üzenet azonosító
  - Topic (téma) kezelés
  - Payload (adattartalom) méret
  - QoS szint
  - Időbélyegek (publish, broker receive, subscriber receive)
  - Késleltetés (latency) számítás
  - Acknowledgment státusz kezelés (QoS 1 és 2-höz)

#### MqttTopicManager.java
- **Cél**: Topic előfizetések és routing kezelése
- **Funkciók**:
  - Hierarchikus topic struktúra támogatása (pl. `sensors/temperature/device1`)
  - Wildcard minták:
    - `+` (egyszintű): egy szintet helyettesít (pl. `sensors/+/device1`)
    - `#` (többszintű): több szintet helyettesít (pl. `sensors/#`)
  - Topic statisztikák (üzenet szám, byte-ok, átlagos üzenet méret)

#### MqttClient.java
- **Cél**: Absztrakt alap osztály MQTT kliensekhez
- **Funkciók**:
  - Broker-hez kapcsolódás
  - Kapcsolat bontás
  - Üzenet fogadás kezelése (abstract metódus)

#### MqttPublisher.java
- **Cél**: MQTT üzenet publikáló kliens
- **Funkciók**:
  - Üzenetek küldése topic-okra
  - QoS szint megadása
  - Statisztikák (elküldött üzenetek, byte-ok)

#### MqttSubscriber.java
- **Cél**: MQTT üzenet fogadó kliens
- **Funkciók**:
  - Topic-okra feliratkozás (wildcard támogatással)
  - Üzenet callback kezelés
  - Leiratkozás topic-okról
  - Statisztikák (fogadott üzenetek, byte-ok)

#### MqttBroker.java
- **Cél**: Központi MQTT broker implementáció
- **Funkciók**:
  - Kliens kapcsolatok kezelése
  - Topic előfizetések kezelése
  - Üzenet routing topic-ok alapján
  - QoS szintek kezelése:
    - QoS 0: Egyszerű továbbítás
    - QoS 1: PUBACK visszaigazolás
    - QoS 2: Négylépéses handshake (PUBLISH, PUBREC, PUBREL, PUBCOMP)
  - Hálózati átvitel szimuláció a NetworkNode API-val
  - Részletes statisztikák

#### MqttBrokerStatistics.java
- **Cél**: Broker statisztikák gyűjtése
- **Metrikák**:
  - Fogadott/továbbított/eldobott üzenetek
  - Byte-ok (fogadott/továbbított)
  - Aktuális/maximális kliens szám
  - Aktuális/maximális előfizetés szám
  - Kézbesítési ráta
  - Eldobási ráta

### 2. Integráció Meglévő Osztályokkal (Integration with Existing Classes)

#### MqttSmartDevice.java
- **Cél**: MQTT-képes IoT eszköz (SmartDevice kiterjesztése)
- **Funkciók**:
  - MQTT protokoll használata adat küldéshez
  - Automatikus broker kapcsolódás
  - Topic-alapú publikáció
  - QoS szint beállítás
  - Teljes kompatibilitás a meglévő mobility és device strategy-kkel

#### MqttApplication.java
- **Cél**: MQTT-képes alkalmazás (Application kiterjesztése)
- **Funkciók**:
  - Topic-okra feliratkozás (wildcard támogatással)
  - Üzenet fogadás broker-en keresztül
  - Automatikus adatfeldolgozás integráció
  - Meglévő VM management integráció

### 3. Metrikák és Értékelés (Metrics and Evaluation)

#### MqttMetricsCollector.java
- **Cél**: Átfogó metrika gyűjtés és értékelés
- **Funkciók**:
  - Singleton minta a globális metrika gyűjtéshez
  - **Üzenet statisztikák**:
    - Összes küldött/fogadott üzenet
    - Összes továbbított byte
    - Átlagos/per-QoS késleltetés
    - Kézbesítési sikeresség/kudarc QoS szintenként
  - **Broker statisztikák**:
    - Üzenet feldolgozás metrikák
    - Kliens kapcsolat metrikák
    - Előfizetés metrikák
  - **Topic statisztikák**:
    - Topic-onkénti üzenetek
    - Topic-onkénti byte-ok
    - Átlagos üzenet méret topic-onként
  - **Export funkciók**:
    - CSV export
    - Formázott konzol riport
    - Teljesítmény összehasonlítás

### 4. Példa Szcenáriók (Example Scenarios)

#### SimpleMqttQoSExample.java
- **Cél**: QoS szintek demonstrációja
- **Konfiguráció**:
  - 1 MQTT broker
  - 3 eszköz (mindegyik más QoS szinttel: 0, 1, 2)
  - 3 feliratkozó (mindegyik megfelelő QoS szinttel)
  - Direkt összehasonlítás a QoS szintek teljesítményéről

#### BasicMqttExample.java
- **Cél**: Tipikus IoT szcenárió
- **Konfiguráció**:
  - 1 MQTT broker
  - 5 hőmérséklet szenzor (publishers)
  - 1 monitoring alkalmazás (subscriber wildcard-dal)
  - Teljes metrika gyűjtés
  - CSV export

## Kulcs Funkciók (Key Features)

### 1. Hierarchikus Topic Rendszer
```
sensors/
  ├── temperature/
  │   ├── device1
  │   ├── device2
  │   └── device3
  └── humidity/
      ├── device1
      └── device2
```

Wildcard példák:
- `sensors/temperature/#` - minden hőmérséklet szenzor
- `sensors/+/device1` - device1 minden típusú szenzoránál

### 2. QoS Szintek Implementációja

**QoS 0 (At most once)**:
- Legalacsonyabb késleltetés
- Nincs garantált kézbesítés
- Minimális hálózati forgalom

**QoS 1 (At least once)**:
- PUBACK visszaigazolás
- Garantált kézbesítés
- Duplikációk lehetségesek

**QoS 2 (Exactly once)**:
- Négylépéses handshake
- Garantált egyszer kézbesítés
- Legnagyobb hálózati forgalom

### 3. Hálózati Szimuláció
- Valósághű hálózati átvitel (NetworkNode API)
- Késleltetés szimuláció
- Sávszélesség limit kezelés
- Energia fogyasztás mérés

### 4. Teljesítmény Metrikák
- End-to-end késleltetés
- Broker feldolgozási idő
- Hálózati sávszélesség használat
- QoS szintenkénti sikerességi ráta
- Topic-onkénti statisztikák

## Értékelési Lehetőségek (Evaluation Capabilities)

### 1. Konfiguráció Variációk
- **Üzenet méret**: 100 byte - 100 KB
- **Eszköz szám**: 5 - 500+ eszköz
- **QoS szintek**: Egyedi vagy kevert
- **Topic struktúra**: Lapos vagy mély hierarchia
- **Broker szám**: Egy vagy több broker
- **Hálózati feltételek**: Változó késleltetés és sávszélesség

### 2. Összehasonlító Elemzés
- MQTT vs. közvetlen kommunikáció
- QoS szintek közötti trade-off-ok
- Egyedi vs. több broker architektúra
- Különböző topic struktúrák hatékonysága

### 3. Erőforrás Kihasználtság
- Hálózati sávszélesség
- Broker CPU/memória
- Eszköz energia fogyasztás
- Költség elemzés

## Használati Példa (Usage Example)

```java
// Broker létrehozása
MqttBroker broker = new MqttBroker(brokerRepo, 100);
broker.start();

// Eszköz létrehozása (publisher)
MqttSmartDevice device = new MqttSmartDevice(
    0, 10000, 500, 2000,
    mobilityStrategy, deviceStrategy, localMachine, 50, false,
    broker, "sensors/temperature/device1", QoSLevel.AT_LEAST_ONCE
);

// Feliratkozó létrehozása
MqttSubscriber subscriber = new MqttSubscriber("sub1", subRepo);
subscriber.connect(broker);
subscriber.subscribe("sensors/temperature/#", QoSLevel.AT_LEAST_ONCE);

// Szimuláció futtatása
Timed.simulateUntilLastEvent();

// Eredmények
String report = MqttMetricsCollector.getInstance().generateReport();
System.out.println(report);
```

## Kiterjeszthetőség (Extensibility)

Az implementáció könnyen bővíthető:
- Egyedi QoS kezelők
- Üzenet transzformációk
- Fejlett routing logika
- Biztonság (autentikáció, titkosítás)
- Perzisztencia (üzenet tárolás)

## Fájlok Összefoglalása (Files Summary)

### Core MQTT (/simulator/src/main/java/hu/u_szeged/inf/fog/simulator/iot/mqtt/)
1. **QoSLevel.java** - QoS szintek enum
2. **MqttMessage.java** - Üzenet osztály
3. **MqttTopicManager.java** - Topic routing és wildcard kezelés
4. **MqttClient.java** - Kliens alap osztály
5. **MqttPublisher.java** - Publisher kliens
6. **MqttSubscriber.java** - Subscriber kliens
7. **MqttBroker.java** - Broker implementáció
8. **MqttBrokerStatistics.java** - Broker statisztikák
9. **MqttSmartDevice.java** - MQTT eszköz
10. **MqttApplication.java** - MQTT alkalmazás
11. **MqttMetricsCollector.java** - Metrika gyűjtő
12. **README.md** - Részletes dokumentáció

### Examples (/simulator/src/main/java/hu/u_szeged/inf/fog/simulator/demo/mqtt/)
1. **SimpleMqttQoSExample.java** - QoS szintek demo
2. **BasicMqttExample.java** - Alapvető IoT szcenárió

## Összegzés (Summary)

Az implementáció teljes körű MQTT protokoll támogatást biztosít a DISSECT-CF-Fog szimulátorban:

✅ **Teljes MQTT funkcionalitás**: Broker, Publisher, Subscriber
✅ **Három QoS szint** teljes implementációval
✅ **Topic routing** hierarchikus struktúrával és wildcard-okkal
✅ **Integráció** meglévő Device és Application osztályokkal
✅ **Átfogó metrikák** teljesítmény értékeléshez
✅ **Példa szcenáriók** különböző konfigurációkkal
✅ **Dokumentáció** és használati útmutatók
✅ **Kiterjeszthető** architektúra

Az implementáció lehetővé teszi valósághű IoT szcenáriók szimulációját változó konfiguráció paraméterekkel, és részletes teljesítmény metrikákat biztosít az összehasonlító elemzéshez.
