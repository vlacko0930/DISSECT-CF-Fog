# MQTT Protocol Implementation for DISSECT-CF-Fog

📊 **Visual Documentation**: Detailed Mermaid diagrams (UML class, sequence, use case, component, state) are available in the [`MQTT_DIAGRAMS.md`](../../../../../../MQTT_DIAGRAMS.md) file at the repository root.

## Overview

This implementation models the MQTT (Message Queuing Telemetry Transport) protocol within the DISSECT-CF-Fog simulator. MQTT is a lightweight publish/subscribe messaging protocol commonly used for IoT device communication. This implementation enables realistic simulation of IoT scenarios with message brokers, Quality of Service (QoS) levels, and topic-based routing.

## Architecture

### Core Components

1. **MqttBroker**: Central message broker that:
   - Manages client connections (publishers and subscribers)
   - Routes messages based on topic subscriptions
   - Handles QoS levels and message acknowledgments
   - Tracks performance metrics

2. **MqttClient**: Abstract base class for MQTT clients
   - **MqttPublisher**: Publishes messages to topics
   - **MqttSubscriber**: Subscribes to topics and receives messages

3. **MqttMessage**: Represents an MQTT message with:
   - Topic
   - Payload size
   - QoS level
   - Timestamps for latency tracking
   - Acknowledgment status

4. **MqttTopicManager**: Handles topic subscriptions and routing
   - Supports hierarchical topics (e.g., `sensors/temperature/device1`)
   - Wildcard patterns:
     - `+` (single-level wildcard): matches one level (e.g., `sensors/+/device1`)
     - `#` (multi-level wildcard): matches multiple levels (e.g., `sensors/#`)

5. **QoSLevel**: Three levels of message delivery guarantee:
   - **QoS 0 (AT_MOST_ONCE)**: No acknowledgment, message may be lost
   - **QoS 1 (AT_LEAST_ONCE)**: Message acknowledged, may be delivered multiple times
   - **QoS 2 (EXACTLY_ONCE)**: Four-step handshake ensures exactly once delivery

### Device and Application Classes

1. **MqttSmartDevice**: Extends `SmartDevice` to use MQTT for communication
   - Publishes sensor data to MQTT topics
   - Connects to broker automatically
   - Supports all QoS levels

2. **MqttApplication**: Extends `Application` to receive MQTT messages
   - Subscribes to topics using wildcard patterns
   - Processes messages through the broker
   - Integrates with existing VM management

## Quality of Service (QoS) Levels

### QoS 0: At Most Once
- **Behavior**: Fire and forget, no acknowledgment
- **Network Overhead**: Lowest
- **Reliability**: Messages may be lost
- **Use Case**: Non-critical sensor data where occasional loss is acceptable

### QoS 1: At Least Once
- **Behavior**: Message acknowledged with PUBACK
- **Network Overhead**: Medium
- **Reliability**: Message guaranteed to arrive, but duplicates may occur
- **Use Case**: Important data where duplicates can be handled

### QoS 2: Exactly Once
- **Behavior**: Four-step handshake (PUBLISH, PUBREC, PUBREL, PUBCOMP)
- **Network Overhead**: Highest
- **Reliability**: Message arrives exactly once
- **Use Case**: Critical data where duplicates cannot be tolerated

## Metrics and Logging

### MqttMetricsCollector

Comprehensive metrics collection system that tracks:

#### Message Statistics
- Total messages sent/received
- Total bytes transmitted
- Message latencies (overall and per QoS level)
- Delivery success/failure rates per QoS level

#### Broker Statistics
- Messages received/delivered/dropped
- Concurrent client connections (current and peak)
- Active subscriptions (current and peak)
- Processing times

#### Topic Statistics
- Messages per topic
- Bytes per topic
- Delivery counts
- Average message sizes

#### Network Performance
- Bandwidth utilization
- End-to-end latencies
- Broker processing times

### Exporting Metrics

Metrics can be exported to:
- **CSV**: Structured data for analysis
- **Console Report**: Formatted summary
- **Custom Handlers**: Extensible for additional formats

## Usage Examples

### Example 1: Basic MQTT Communication

```java
// Create broker
Repository brokerRepo = new Repository(10_737_418_240L, "broker-repo", ...);
MqttBroker broker = new MqttBroker(brokerRepo, 100);
broker.start();

// Create publishing device
MqttSmartDevice device = new MqttSmartDevice(
    0, 10000, 100, 1000, // startTime, stopTime, fileSize, freq
    mobilityStrategy, deviceStrategy, localMachine, 50, false,
    broker, "sensors/temperature/device1", QoSLevel.AT_LEAST_ONCE
);

// Create subscribing application
MqttApplication app = new MqttApplication(
    "TempMonitor", 1000, 1000, 1000,
    true, applicationStrategy, instance,
    broker, QoSLevel.AT_LEAST_ONCE
);
app.addSubscription("sensors/temperature/#");

// Run simulation
Timed.simulateUntilLastEvent();

// Get metrics
String report = MqttMetricsCollector.getInstance().generateReport();
System.out.println(report);
```

### Example 2: Multiple Brokers

```java
// Create multiple brokers for different regions
MqttBroker broker1 = new MqttBroker(repo1, 100);
MqttBroker broker2 = new MqttBroker(repo2, 100);

// Devices connect to nearest broker
device1.connectToBroker(broker1);
device2.connectToBroker(broker2);

// Applications can subscribe to specific brokers
app1.connectToBroker(broker1);
app2.connectToBroker(broker2);
```

### Example 3: Wildcard Topic Patterns

```java
// Subscribe to all temperature sensors
subscriber.subscribe("sensors/temperature/#", QoSLevel.AT_LEAST_ONCE);

// Subscribe to any sensor in building1
subscriber.subscribe("sensors/+/building1", QoSLevel.AT_MOST_ONCE);

// Devices publish to specific topics
device1.publish("sensors/temperature/floor1", 100, QoSLevel.AT_LEAST_ONCE);
device2.publish("sensors/humidity/building1", 100, QoSLevel.AT_MOST_ONCE);
```

## Example Scenarios

### SimpleMqttQoSExample.java
Location: `hu.u_szeged.inf.fog.simulator.demo.mqtt.SimpleMqttQoSExample`

Demonstrates all three QoS levels with:
- 1 broker
- 3 devices (one per QoS level: 0, 1, 2)
- 3 subscribers (one per QoS level)
- Direct comparison of QoS performance
- Metrics export to CSV

**Run with:** `java hu.u_szeged.inf.fog.simulator.demo.mqtt.SimpleMqttQoSExample`

### MqttHierarchicalTopicsExample.java
Location: `hu.u_szeged.inf.fog.simulator.demo.mqtt.MqttHierarchicalTopicsExample`

Demonstrates hierarchical topic structures and wildcards:
- Topics: `home/livingroom/temperature`, `home/bedroom/temperature`, `sensors/outdoor/humidity`, `industry/machine1/pressure`
- Wildcard subscriptions: `home/+/temperature` (single-level), `sensors/#` (multi-level), `#` (all topics)
- 4 devices, 4 subscribers with different subscription patterns
- Topic routing verification

**Run with:** `java hu.u_szeged.inf.fog.simulator.demo.mqtt.MqttHierarchicalTopicsExample`

### MqttLargeScaleExample.java
Location: `hu.u_szeged.inf.fog.simulator.demo.mqtt.MqttLargeScaleExample`

Large-scale MQTT simulation:
- 3 brokers (cloud-broker, edge-broker-1, edge-broker-2)
- 10 devices per broker (30 total)
- 3 subscribers per broker (9 total)
- Mixed QoS levels across devices
- Per-broker performance comparison

**Run with:** `java hu.u_szeged.inf.fog.simulator.demo.mqtt.MqttLargeScaleExample`

## Configuration Parameters

### Broker Configuration
```java
new MqttBroker(
    repository,          // Network node where broker is hosted
    processingFrequency  // How often broker processes messages (ms)
);
```

### Device Configuration
```java
new MqttSmartDevice(
    startTime,          // When device starts generating data
    stopTime,           // When device stops
    fileSize,           // Size of each message (bytes)
    freq,               // Frequency of messages (ms)
    mobilityStrategy,   // Movement pattern
    deviceStrategy,     // Application selection strategy
    localMachine,       // Physical machine representing device
    latency,            // Base network latency
    isPathLogged,       // Whether to log device path
    mqttBroker,         // Broker to connect to
    publishTopic,       // Topic to publish to
    qosLevel            // QoS level for messages
);
```

## Performance Considerations

### QoS Trade-offs
- **QoS 0**: Lowest latency, lowest reliability, minimal bandwidth
- **QoS 1**: Medium latency, high reliability, medium bandwidth
- **QoS 2**: Highest latency, highest reliability, highest bandwidth

### Broker Scaling
- Processing frequency affects throughput and latency
- More frequent processing = lower latency, higher CPU usage
- Less frequent processing = higher latency, lower CPU usage

### Topic Design
- Hierarchical structure enables efficient routing
- Wildcards allow flexible subscriptions
- Specific topics reduce routing overhead

## Evaluation Framework

The implementation includes comprehensive evaluation capabilities:

1. **Performance Metrics**
   - Message delivery rates
   - Latency distributions
   - Throughput measurements

2. **Resource Utilization**
   - Network bandwidth usage
   - Broker CPU/memory consumption
   - Device energy consumption

3. **Cost Analysis**
   - Cloud infrastructure costs
   - Network transfer costs
   - Per-device operational costs

4. **Comparative Analysis**
   - MQTT vs. direct communication
   - Different QoS level comparisons
   - Single vs. multiple broker scenarios

## Testing Scenarios

Comprehensive testing should include:

1. **Message Size Variations**
   - Small (100 bytes)
   - Medium (1 KB)
   - Large (10 KB, 100 KB)

2. **Device Count Scaling**
   - 5, 10, 50, 100, 500 devices

3. **QoS Level Combinations**
   - All QoS 0
   - Mixed QoS levels
   - All QoS 2

4. **Topic Structures**
   - Flat topics (no hierarchy)
   - Deep hierarchy (5+ levels)
   - Wildcard subscriptions

5. **Network Conditions**
   - Low latency (< 10ms)
   - High latency (> 100ms)
   - Variable latency

6. **Architecture Variations**
   - Single broker
   - Multiple brokers
   - Distributed brokers

## Extensibility

The implementation is designed to be extensible:

1. **Custom QoS Handlers**: Implement specialized acknowledgment logic
2. **Message Transformations**: Add message processing pipelines
3. **Advanced Routing**: Implement content-based routing
4. **Security**: Add authentication and encryption
5. **Persistence**: Implement message persistence for QoS > 0

## Future Enhancements

Potential improvements:

1. **Retained Messages**: Last known good value delivery
2. **Last Will and Testament**: Automatic notification on disconnect
3. **Session Persistence**: Resume subscriptions after reconnect
4. **Message Batching**: Optimize network usage
5. **Cluster Support**: Multi-broker clusters with synchronization

## References

- MQTT Version 3.1.1 Specification: http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/mqtt-v3.1.1.html
- MQTT Version 5.0 Specification: https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html

## Authors and Contributors

This MQTT implementation extends the DISSECT-CF-Fog simulator to support IoT communication patterns commonly found in fog and edge computing scenarios.
