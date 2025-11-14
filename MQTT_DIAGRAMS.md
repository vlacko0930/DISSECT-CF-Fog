# MQTT Implementáció Diagramok

Ez a dokumentum Mermaid diagramokat tartalmaz az MQTT protokoll DISSECT-CF-Fog szimulátorban való implementációjához.

## 1. UML Class Diagram - Osztálydiagram

Az MQTT implementáció osztályszerkezete és kapcsolatai:

```mermaid
classDiagram
    %% Enumok
    class QoSLevel {
        <<enumeration>>
        +AT_MOST_ONCE(0)
        +AT_LEAST_ONCE(1)
        +EXACTLY_ONCE(2)
        -int value
        +getValue() int
        +requiresAck() boolean
        +requiresHandshake() boolean
    }

    %% Üzenet osztály
    class MqttMessage {
        -String messageId
        -String topic
        -String payload
        -long payloadSize
        -QoSLevel qos
        -long publishTime
        -long deliveryTime
        -int retryCount
        -String publisherId
        +getMessageId() String
        +getTopic() String
        +getQos() QoSLevel
        +calculateLatency() long
        +isExpired(maxAge) boolean
        +incrementRetry() void
        +setDeliveryTime(time) void
    }

    %% Téma menedzser
    class MqttTopicManager {
        -ConcurrentHashMap~String, Set~String~~ subscriptions
        +subscribe(topic, subscriberId) void
        +unsubscribe(topic, subscriberId) void
        +getMatchingSubscribers(topic) Set~String~
        +getSubscriptionCount() int
        +getTopics() Set~String~
        -matchesTopic(pattern, topic) boolean
    }

    %% Kliens absztrakt osztály
    class MqttClient {
        <<abstract>>
        #String clientId
        #MqttBroker broker
        #Repository repository
        #boolean connected
        +connect(broker) void
        +disconnect() void
        +isConnected() boolean
        +getClientId() String
        +onMessageReceived(message)* void
    }

    %% Publisher
    class MqttPublisher {
        -long messagesSent
        -long bytesSent
        +publish(topic, payloadSize, qos) void
        +getMessagesSent() long
        +getBytesSent() long
        +onMessageReceived(message) void
    }

    %% Subscriber
    class MqttSubscriber {
        -List~String~ subscriptions
        -MessageHandler messageHandler
        -long messagesReceived
        +subscribe(topic, qos) void
        +unsubscribe(topic) void
        +getMessagesReceived() long
        +onMessageReceived(message) void
    }

    %% Broker
    class MqttBroker {
        -String brokerId
        -Repository repository
        -MqttTopicManager topicManager
        -Map~String, MqttClient~ clients
        -Map~String, PendingAck~ pendingAcks
        -Map~String, QoS2Handshake~ qos2Handshakes
        -MqttBrokerStatistics statistics
        +registerClient(client) void
        +unregisterClient(clientId) void
        +publish(message, fromRepo) void
        +deliverMessage(message, subscriberIds) void
        -handleQoS0(message, subscriberIds) void
        -handleQoS1(message, subscriberIds) void
        -handleQoS2(message, subscriberIds) void
        +start() void
        +stop() void
    }

    %% Broker statisztikák
    class MqttBrokerStatistics {
        -String brokerId
        -long messagesReceived
        -long messagesDelivered
        -long messagesDropped
        -long bytesReceived
        -long bytesDelivered
        -int currentClientCount
        -int maxClientCount
        +incrementMessagesReceived() void
        +incrementMessagesDelivered() void
        +getDeliveryRate() double
        +getDropRate() double
    }

    %% Smart Device
    class MqttSmartDevice {
        -MqttPublisher publisher
        -String publishTopic
        -QoSLevel defaultQoS
        -long sentMessages
        +tick(fires) void
        +getPublisher() MqttPublisher
    }

    %% Application
    class MqttApplication {
        -MqttSubscriber subscriber
        +onMessageReceived(message) void
        +getSubscriber() MqttSubscriber
    }

    %% Metrics Collector
    class MqttMetricsCollector {
        <<singleton>>
        -static MqttMetricsCollector instance
        -List~MqttBroker~ brokers
        -List~MqttPublisher~ publishers
        -List~MqttSubscriber~ subscribers
        -Map~String, TopicMetrics~ topicMetrics
        +getInstance() MqttMetricsCollector
        +registerBroker(broker) void
        +registerPublisher(publisher) void
        +registerSubscriber(subscriber) void
        +recordMessageDelivery(message, latency) void
        +generateReport() String
        +exportToCSV(filename) void
    }

    %% Szimulátor alap osztályok
    class Device {
        <<simulator>>
        +generatedData long
        +locallyProcessedData long
    }

    class Application {
        <<simulator>>
        +receivedData long
        +processedData long
    }

    class Timed {
        <<simulator>>
        +subscribe(freq, subscriber) void
        +tick(fires)* void
    }

    %% Kapcsolatok
    MqttMessage --> QoSLevel : uses
    MqttBroker --> MqttTopicManager : manages
    MqttBroker --> MqttBrokerStatistics : tracks
    MqttBroker --> MqttClient : manages *
    MqttClient <|-- MqttPublisher : extends
    MqttClient <|-- MqttSubscriber : extends
    MqttPublisher --> MqttBroker : publishes to
    MqttSubscriber --> MqttBroker : subscribes to
    MqttSmartDevice --> MqttPublisher : contains
    MqttSmartDevice --|> Device : extends
    MqttSmartDevice ..|> Timed : implements
    MqttApplication --> MqttSubscriber : contains
    MqttApplication --|> Application : extends
    MqttMetricsCollector --> MqttBroker : monitors *
    MqttMetricsCollector --> MqttPublisher : monitors *
    MqttMetricsCollector --> MqttSubscriber : monitors *
    MqttBroker --> MqttMessage : processes *
```

## 2. Use Case Diagram - Használati esetek

Az MQTT rendszer főbb használati esetei:

```mermaid
graph TB
    %% Aktorok
    IoTDevice[("IoT Eszköz<br/>(MqttSmartDevice)")]
    MonitorApp[("Monitoring<br/>Alkalmazás<br/>(MqttApplication)")]
    Simulator[("Szimulátor<br/>Futtatókörnyezet")]
    
    %% MQTT Broker központi elem
    Broker{{"MQTT Broker"}}
    
    %% Főbb use case-ek - Eszköz oldalon
    subgraph DeviceUseCases["Eszköz Funkciók"]
        UC1[Csatlakozás brokerhez]
        UC2[Adatok publikálása<br/>témába]
        UC3[QoS szint választás<br/>0/1/2]
        UC4[Automatikus<br/>újraküldés QoS 1/2]
        UC5[Lecsatlakozás]
    end
    
    %% Főbb use case-ek - Alkalmazás oldalon
    subgraph AppUseCases["Alkalmazás Funkciók"]
        UC6[Csatlakozás brokerhez]
        UC7[Feliratkozás témákra]
        UC8[Wildcard előfizetés<br/>+, #]
        UC9[Üzenetek fogadása]
        UC10[Leiratkozás témákról]
        UC11[Lecsatlakozás]
    end
    
    %% Főbb use case-ek - Broker funkciók
    subgraph BrokerUseCases["Broker Funkciók"]
        UC12[Üzenetek<br/>fogadása]
        UC13[Téma alapú<br/>routing]
        UC14[QoS 0<br/>Fire-and-forget]
        UC15[QoS 1<br/>PUBACK]
        UC16[QoS 2<br/>4-way handshake]
        UC17[Statisztikák<br/>gyűjtése]
    end
    
    %% Főbb use case-ek - Monitoring
    subgraph MonitorUseCases["Monitoring Funkciók"]
        UC18[Metrikák<br/>gyűjtése]
        UC19[Teljesítmény<br/>mérés]
        UC20[Jelentés<br/>generálás]
        UC21[CSV export]
    end
    
    %% Eszköz kapcsolatok
    IoTDevice --> UC1
    IoTDevice --> UC2
    IoTDevice --> UC3
    IoTDevice --> UC5
    UC2 --> UC4
    
    UC1 --> Broker
    UC2 --> Broker
    UC3 --> Broker
    UC5 --> Broker
    
    %% Alkalmazás kapcsolatok
    MonitorApp --> UC6
    MonitorApp --> UC7
    MonitorApp --> UC9
    MonitorApp --> UC10
    MonitorApp --> UC11
    UC7 --> UC8
    
    UC6 --> Broker
    UC7 --> Broker
    UC9 --> Broker
    UC10 --> Broker
    UC11 --> Broker
    
    %% Broker belső funkciók
    Broker --> UC12
    UC12 --> UC13
    UC13 --> UC14
    UC13 --> UC15
    UC13 --> UC16
    Broker --> UC17
    
    %% Szimulátor funkciók
    Simulator --> UC18
    Simulator --> UC19
    Simulator --> UC20
    Simulator --> UC21
    
    UC18 -.->|figyel| Broker
    UC18 -.->|figyel| IoTDevice
    UC18 -.->|figyel| MonitorApp
    
    style Broker fill:#ff9999,stroke:#333,stroke-width:3px
    style IoTDevice fill:#99ccff,stroke:#333,stroke-width:2px
    style MonitorApp fill:#99ff99,stroke:#333,stroke-width:2px
    style Simulator fill:#ffcc99,stroke:#333,stroke-width:2px
```

## 3. Sequence Diagram - QoS szintek szekvenciái

### 3.1 QoS 0 - At Most Once (Fire and Forget)

```mermaid
sequenceDiagram
    participant Device as IoT Eszköz<br/>(Publisher)
    participant Broker as MQTT Broker
    participant App as Monitoring App<br/>(Subscriber)
    
    Note over Device,App: QoS 0: Fire-and-forget, nincs visszaigazolás
    
    Device->>Broker: 1. PUBLISH (topic: "sensors/temp", QoS 0, payload)
    Note over Broker: Téma alapú routing<br/>Keresés: "sensors/temp"
    Broker->>App: 2. Deliver message (QoS 0)
    Note over App: Üzenet feldolgozás
    
    Note over Device,App: ❌ Nincs ACK, nincs garancia<br/>✅ Gyors, alacsony terhelés
```

### 3.2 QoS 1 - At Least Once (Acknowledged Delivery)

```mermaid
sequenceDiagram
    participant Device as IoT Eszköz<br/>(Publisher)
    participant Broker as MQTT Broker
    participant App as Monitoring App<br/>(Subscriber)
    
    Note over Device,App: QoS 1: Legalább egyszer, visszaigazolással
    
    Device->>Broker: 1. PUBLISH (topic, QoS 1, msgId: 123)
    Note over Broker: Téma routing<br/>Tárol pendingAcks-ban
    
    Broker->>App: 2. Deliver message (QoS 1, msgId: 123)
    Note over App: Üzenet feldolgozás
    App->>Broker: 3. ACK (msgId: 123)
    
    Broker->>Device: 4. PUBACK (msgId: 123)
    Note over Device: Töröl helyi várólistából
    
    Note over Device,App: ✅ Garantált kézbesítés<br/>⚠️ Duplikáció lehetséges
```

### 3.3 QoS 2 - Exactly Once (Assured Delivery)

```mermaid
sequenceDiagram
    participant Device as IoT Eszköz<br/>(Publisher)
    participant Broker as MQTT Broker
    participant App as Monitoring App<br/>(Subscriber)
    
    Note over Device,App: QoS 2: Pontosan egyszer, 4-way handshake
    
    Device->>Broker: 1. PUBLISH (topic, QoS 2, msgId: 456)
    Note over Broker: Téma routing<br/>QoS2 handshake indítás
    
    Broker->>Device: 2. PUBREC (msgId: 456)
    Note over Device: Megkapta PUBREC<br/>Várakozás PUBREL-re
    
    Device->>Broker: 3. PUBREL (msgId: 456)
    Note over Broker: Kézbesítés előfizetőknek
    
    Broker->>App: 4. Deliver message (QoS 2, msgId: 456)
    Note over App: Üzenet feldolgozás<br/>(pontosan egyszer)
    
    Broker->>Device: 5. PUBCOMP (msgId: 456)
    Note over Device: Tranzakció befejezve<br/>Töröl minden állapotot
    
    Note over Device,App: ✅ Garantált, pontosan egyszer<br/>⚠️ Legnagyobb terhelés és latencia
```

## 4. Sequence Diagram - Hierarchikus témák wildcard előfizetéssel

```mermaid
sequenceDiagram
    participant Dev1 as Device 1<br/>home/livingroom/temp
    participant Dev2 as Device 2<br/>home/bedroom/temp
    participant Dev3 as Device 3<br/>sensors/outdoor/humidity
    participant Broker as MQTT Broker<br/>(TopicManager)
    participant Sub1 as Subscriber 1<br/>home/+/temp
    participant Sub2 as Subscriber 2<br/>sensors/#
    participant Sub3 as Subscriber 3<br/>#
    
    Note over Dev1,Sub3: Feliratkozások létrehozása
    
    Sub1->>Broker: SUBSCRIBE "home/+/temperature" (QoS 1)
    Note over Broker: Wildcard pattern tárolás<br/>+ = egyszintű helyettesítő
    
    Sub2->>Broker: SUBSCRIBE "sensors/#" (QoS 1)
    Note over Broker: # = többszintű helyettesítő
    
    Sub3->>Broker: SUBSCRIBE "#" (QoS 0)
    Note over Broker: Univerzális előfizetés
    
    Note over Dev1,Sub3: Üzenetek publikálása és routing
    
    Dev1->>Broker: PUBLISH "home/livingroom/temperature"
    Note over Broker: Wildcard matching:<br/>✓ home/+/temperature<br/>✓ #
    Broker->>Sub1: Deliver (home/livingroom/temperature)
    Broker->>Sub3: Deliver (home/livingroom/temperature)
    
    Dev2->>Broker: PUBLISH "home/bedroom/temperature"
    Note over Broker: Wildcard matching:<br/>✓ home/+/temperature<br/>✓ #
    Broker->>Sub1: Deliver (home/bedroom/temperature)
    Broker->>Sub3: Deliver (home/bedroom/temperature)
    
    Dev3->>Broker: PUBLISH "sensors/outdoor/humidity"
    Note over Broker: Wildcard matching:<br/>✓ sensors/#<br/>✓ #
    Broker->>Sub2: Deliver (sensors/outdoor/humidity)
    Broker->>Sub3: Deliver (sensors/outdoor/humidity)
    
    Note over Dev1,Sub3: Sub1: 2 üzenet (csak home temp)<br/>Sub2: 1 üzenet (csak sensors)<br/>Sub3: 3 üzenet (minden)
```

## 5. Component Diagram - Rendszerkomponensek

```mermaid
graph TB
    subgraph SimulatorCore["DISSECT-CF-Fog Szimulátor Mag"]
        Device[Device<br/>Alap IoT eszköz]
        Application[Application<br/>Alap alkalmazás]
        Timed[Timed<br/>Időzített események]
        Repository[Repository<br/>Adattárolás]
        NetworkNode[NetworkNode<br/>Hálózat szimuláció]
    end
    
    subgraph MqttCore["MQTT Protokoll Implementáció"]
        QoSLevel[QoSLevel<br/>Enum: 0, 1, 2]
        MqttMessage[MqttMessage<br/>Üzenet objektum]
        MqttTopicManager[MqttTopicManager<br/>Téma routing<br/>Wildcard support]
        MqttClient[MqttClient<br/>Abstract base]
        MqttPublisher[MqttPublisher<br/>Publikáló]
        MqttSubscriber[MqttSubscriber<br/>Feliratkozó]
        MqttBroker[MqttBroker<br/>Központi broker<br/>QoS 0/1/2 kezelés]
        MqttBrokerStats[MqttBrokerStatistics<br/>Broker metrikák]
    end
    
    subgraph MqttDevices["MQTT Eszközök"]
        MqttSmartDevice[MqttSmartDevice<br/>MQTT-képes IoT eszköz<br/>Publisher integráció]
        MqttApplication[MqttApplication<br/>MQTT-képes app<br/>Subscriber integráció]
    end
    
    subgraph MqttMonitoring["Monitoring és Metrikák"]
        MqttMetrics[MqttMetricsCollector<br/>Singleton<br/>Teljes rendszer monitoring]
    end
    
    subgraph Examples["Példa Szkriptek"]
        Ex1[SimpleMqttQoSExample<br/>QoS összehasonlítás]
        Ex2[MqttHierarchicalTopicsExample<br/>Témák és wildcard-ok]
        Ex3[MqttLargeScaleExample<br/>Nagy léptékű szimuláció<br/>Több broker]
    end
    
    %% Core connections
    Device --> MqttSmartDevice
    Application --> MqttApplication
    Timed -.->|implements| MqttSmartDevice
    Repository --> MqttBroker
    Repository --> MqttClient
    NetworkNode -.->|uses| MqttBroker
    
    %% MQTT Core connections
    MqttClient --> MqttPublisher
    MqttClient --> MqttSubscriber
    MqttBroker --> MqttTopicManager
    MqttBroker --> MqttBrokerStats
    MqttBroker --> MqttMessage
    MqttMessage --> QoSLevel
    MqttPublisher --> MqttBroker
    MqttSubscriber --> MqttBroker
    
    %% Device connections
    MqttSmartDevice --> MqttPublisher
    MqttApplication --> MqttSubscriber
    
    %% Monitoring connections
    MqttMetrics --> MqttBroker
    MqttMetrics --> MqttPublisher
    MqttMetrics --> MqttSubscriber
    
    %% Example connections
    Ex1 --> MqttBroker
    Ex1 --> MqttSmartDevice
    Ex1 --> MqttSubscriber
    Ex1 --> MqttMetrics
    
    Ex2 --> MqttBroker
    Ex2 --> MqttSmartDevice
    Ex2 --> MqttSubscriber
    Ex2 --> MqttTopicManager
    
    Ex3 --> MqttBroker
    Ex3 --> MqttSmartDevice
    Ex3 --> MqttSubscriber
    Ex3 --> MqttMetrics
    
    style SimulatorCore fill:#e1f5ff,stroke:#01579b,stroke-width:2px
    style MqttCore fill:#fff3e0,stroke:#e65100,stroke-width:2px
    style MqttDevices fill:#f3e5f5,stroke:#4a148c,stroke-width:2px
    style MqttMonitoring fill:#e8f5e9,stroke:#1b5e20,stroke-width:2px
    style Examples fill:#fff9c4,stroke:#f57f17,stroke-width:2px
```

## 6. State Diagram - MQTT Üzenet Életciklus (QoS 2)

```mermaid
stateDiagram-v2
    [*] --> Created: Üzenet létrehozása
    
    Created --> Published: publish() hívás
    
    state "Publikálva" as Published {
        [*] --> WaitingPubRec
        WaitingPubRec --> PubRecReceived: PUBREC megérkezett
        PubRecReceived --> WaitingPubComp: PUBREL elküldve
    }
    
    Published --> Routing: Broker fogadta
    
    state "Broker Routing" as Routing {
        [*] --> TopicMatching
        TopicMatching --> SubscriberLookup: Témák illesztése
        SubscriberLookup --> QoSHandling: Előfizetők megtalálva
        
        state "QoS Kezelés" as QoSHandling {
            [*] --> CheckQoS
            CheckQoS --> QoS0Handler: QoS = 0
            CheckQoS --> QoS1Handler: QoS = 1
            CheckQoS --> QoS2Handler: QoS = 2
            
            QoS0Handler --> DirectDeliver
            QoS1Handler --> DeliverWithAck
            QoS2Handler --> DeliverWithHandshake
        }
    }
    
    Routing --> InTransit: Hálózati továbbítás
    
    state "Átvitel alatt" as InTransit {
        [*] --> NetworkTransfer
        NetworkTransfer --> Bandwidth: Sávszélesség számítás
        Bandwidth --> Latency: Késleltetés alkalmazása
    }
    
    InTransit --> Delivered: Előfizetőhöz ért
    
    state "Kézbesítve" as Delivered {
        [*] --> ReceivedBySubscriber
        ReceivedBySubscriber --> ProcessingMessage
        ProcessingMessage --> AckSent: ACK küldés (QoS 1/2)
    }
    
    Delivered --> Completed: Feldolgozva
    
    state "Befejezett" as Completed {
        [*] --> StatisticsUpdate
        StatisticsUpdate --> MetricsRecorded
        MetricsRecorded --> CleanupState
    }
    
    Completed --> [*]: Életciklus vége
    
    %% Hibakezelés
    Published --> Retry: Timeout (QoS 1/2)
    Retry --> Published: Újrapróbálkozás
    Retry --> Dropped: Max retry elérve
    Dropped --> [*]
    
    note right of Created
        messageId generálás
        QoS szint beállítás
        publishTime rögzítés
    end note
    
    note right of Routing
        TopicManager wildcard
        matching: +, #
    end note
    
    note right of Completed
        deliveryTime rögzítés
        latency számítás
        metrikák frissítése
    end note
```

## Diagramok Magyarázata

### UML Class Diagram
Az osztálydiagram mutatja:
- **12 fő osztály** hierarchiáját és kapcsolatait
- Az öröklési láncot (Device → MqttSmartDevice, Application → MqttApplication)
- A kompozíciós kapcsolatokat (MqttBroker tartalmaz MqttTopicManager-t)
- Az összes főbb metódust és attribútumot

### Use Case Diagram
A használati esetek diagramja szemlélteti:
- **3 fő aktor**: IoT eszköz, Monitoring alkalmazás, Szimulátor
- **21 use case** funkcionális csoportokba rendezve
- A wildcard előfizetések speciális funkcióit
- A QoS szintek közötti kapcsolatokat

### Sequence Diagrams
A szekvenciadiagramok részletezik:
- **QoS 0**: 2 lépés, nincs visszaigazolás
- **QoS 1**: 4 lépés, PUBACK protokoll
- **QoS 2**: 5 lépés, teljes 4-way handshake (PUBREC/PUBREL/PUBCOMP)
- **Hierarchikus témák**: Wildcard matching működése (`+`, `#`)

### Component Diagram
A komponensdiagram bemutatja:
- A **4 fő modul** szétválasztását (Core, Protocol, Devices, Monitoring)
- Az integrációt a DISSECT-CF-Fog alaprendszerével
- A **3 példa szkript** kapcsolódási pontjait

### State Diagram
Az állapotgép diagram követi:
- Egy MQTT üzenet teljes **életciklusát**
- A **QoS 2** specifikus állapotátmeneteit
- A **hibakezelést** (retry, drop)
- A **statisztikák** gyűjtésének időzítését

---

**Használat**: Ezek a diagramok beágyazhatók bármely Markdown-ot támogató rendszerbe (GitHub, GitLab, VS Code, stb.) és automatikusan renderelődnek.
