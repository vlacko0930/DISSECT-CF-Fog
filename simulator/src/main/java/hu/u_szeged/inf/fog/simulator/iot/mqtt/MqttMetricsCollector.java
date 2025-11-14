package hu.u_szeged.inf.fog.simulator.iot.mqtt;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Comprehensive metrics collector for MQTT simulations.
 * Tracks performance indicators, resource utilization, and costs.
 */
public class MqttMetricsCollector {

    /**
     * Singleton instance.
     */
    private static MqttMetricsCollector instance;

    /**
     * Map of brokers being monitored.
     */
    private final Map<String, MqttBroker> brokers;

    /**
     * Map of publishers being monitored.
     */
    private final Map<String, MqttPublisher> publishers;

    /**
     * Map of subscribers being monitored.
     */
    private final Map<String, MqttSubscriber> subscribers;

    /**
     * List of all message latencies recorded.
     */
    private final List<Long> messageLatencies;

    /**
     * Map of latencies per QoS level.
     */
    private final Map<QoSLevel, List<Long>> latenciesByQoS;

    /**
     * Map of delivery success by QoS level.
     */
    private final Map<QoSLevel, Integer> deliverySuccessByQoS;

    /**
     * Map of delivery failures by QoS level.
     */
    private final Map<QoSLevel, Integer> deliveryFailuresByQoS;

    /**
     * Total network bandwidth used (bytes).
     */
    private long totalNetworkBytes;

    /**
     * Simulation start time.
     */
    private long simulationStartTime;

    /**
     * Simulation end time.
     */
    private long simulationEndTime;

    /**
     * Private constructor for singleton.
     */
    private MqttMetricsCollector() {
        this.brokers = new HashMap<>();
        this.publishers = new HashMap<>();
        this.subscribers = new HashMap<>();
        this.messageLatencies = new ArrayList<>();
        this.latenciesByQoS = new EnumMap<>(QoSLevel.class);
        this.deliverySuccessByQoS = new EnumMap<>(QoSLevel.class);
        this.deliveryFailuresByQoS = new EnumMap<>(QoSLevel.class);
        
        // Initialize QoS maps
        for (QoSLevel qos : QoSLevel.values()) {
            latenciesByQoS.put(qos, new ArrayList<>());
            deliverySuccessByQoS.put(qos, 0);
            deliveryFailuresByQoS.put(qos, 0);
        }
        
        this.totalNetworkBytes = 0;
        this.simulationStartTime = -1;
        this.simulationEndTime = -1;
    }

    /**
     * Gets the singleton instance.
     *
     * @return the instance
     */
    public static synchronized MqttMetricsCollector getInstance() {
        if (instance == null) {
            instance = new MqttMetricsCollector();
        }
        return instance;
    }

    /**
     * Resets all metrics.
     */
    public void reset() {
        brokers.clear();
        publishers.clear();
        subscribers.clear();
        messageLatencies.clear();
        
        for (QoSLevel qos : QoSLevel.values()) {
            latenciesByQoS.get(qos).clear();
            deliverySuccessByQoS.put(qos, 0);
            deliveryFailuresByQoS.put(qos, 0);
        }
        
        totalNetworkBytes = 0;
        simulationStartTime = -1;
        simulationEndTime = -1;
    }

    /**
     * Starts the simulation timer.
     */
    public void startSimulation() {
        simulationStartTime = Timed.getFireCount();
    }

    /**
     * Ends the simulation timer.
     */
    public void endSimulation() {
        simulationEndTime = Timed.getFireCount();
    }

    /**
     * Registers a broker for monitoring.
     *
     * @param broker the broker to monitor
     */
    public void registerBroker(MqttBroker broker) {
        brokers.put(broker.getBrokerId(), broker);
    }

    /**
     * Registers a publisher for monitoring.
     *
     * @param publisher the publisher to monitor
     */
    public void registerPublisher(MqttPublisher publisher) {
        publishers.put(publisher.getClientId(), publisher);
    }

    /**
     * Registers a subscriber for monitoring.
     *
     * @param subscriber the subscriber to monitor
     */
    public void registerSubscriber(MqttSubscriber subscriber) {
        subscribers.put(subscriber.getClientId(), subscriber);
    }

    /**
     * Records a message delivery.
     *
     * @param message the delivered message
     */
    public void recordDelivery(MqttMessage message) {
        long latency = message.getEndToEndLatency();
        if (latency >= 0) {
            messageLatencies.add(latency);
            latenciesByQoS.get(message.getQosLevel()).add(latency);
        }
        
        deliverySuccessByQoS.put(message.getQosLevel(),
                deliverySuccessByQoS.get(message.getQosLevel()) + 1);
        
        totalNetworkBytes += message.getPayloadSize();
    }

    /**
     * Records a message delivery failure.
     *
     * @param qosLevel the QoS level of the failed message
     */
    public void recordFailure(QoSLevel qosLevel) {
        deliveryFailuresByQoS.put(qosLevel, deliveryFailuresByQoS.get(qosLevel) + 1);
    }

    /**
     * Calculates average message latency.
     *
     * @return average latency in milliseconds
     */
    public double getAverageLatency() {
        if (messageLatencies.isEmpty()) {
            return 0;
        }
        return messageLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    /**
     * Calculates average latency for a specific QoS level.
     *
     * @param qosLevel the QoS level
     * @return average latency in milliseconds
     */
    public double getAverageLatencyByQoS(QoSLevel qosLevel) {
        List<Long> latencies = latenciesByQoS.get(qosLevel);
        if (latencies.isEmpty()) {
            return 0;
        }
        return latencies.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    /**
     * Calculates the delivery success rate for a QoS level.
     *
     * @param qosLevel the QoS level
     * @return success rate (0.0 to 1.0)
     */
    public double getSuccessRate(QoSLevel qosLevel) {
        int successes = deliverySuccessByQoS.get(qosLevel);
        int failures = deliveryFailuresByQoS.get(qosLevel);
        int total = successes + failures;
        
        return total > 0 ? (double) successes / total : 0;
    }

    /**
     * Gets total simulation duration.
     *
     * @return duration in milliseconds
     */
    public long getSimulationDuration() {
        if (simulationStartTime == -1 || simulationEndTime == -1) {
            return 0;
        }
        return simulationEndTime - simulationStartTime;
    }

    /**
     * Calculates total messages sent by all publishers.
     *
     * @return total messages sent
     */
    public long getTotalMessagesSent() {
        return publishers.values().stream()
                .mapToLong(MqttPublisher::getMessagesSent)
                .sum();
    }

    /**
     * Calculates total messages received by all subscribers.
     *
     * @return total messages received
     */
    public long getTotalMessagesReceived() {
        return subscribers.values().stream()
                .mapToLong(MqttSubscriber::getMessagesReceived)
                .sum();
    }

    /**
     * Calculates total bytes sent by all publishers.
     *
     * @return total bytes sent
     */
    public long getTotalBytesSent() {
        return publishers.values().stream()
                .mapToLong(MqttPublisher::getBytesSent)
                .sum();
    }

    /**
     * Calculates total bytes received by all subscribers.
     *
     * @return total bytes received
     */
    public long getTotalBytesReceived() {
        return subscribers.values().stream()
                .mapToLong(MqttSubscriber::getBytesReceived)
                .sum();
    }

    /**
     * Generates a comprehensive metrics report.
     *
     * @return formatted report string
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("\n========== MQTT Simulation Metrics Report ==========\n\n");
        
        // Simulation overview
        report.append("=== Simulation Overview ===\n");
        report.append(String.format("Duration: %d ms\n", getSimulationDuration()));
        report.append(String.format("Brokers: %d\n", brokers.size()));
        report.append(String.format("Publishers: %d\n", publishers.size()));
        report.append(String.format("Subscribers: %d\n", subscribers.size()));
        report.append("\n");
        
        // Message statistics
        report.append("=== Message Statistics ===\n");
        report.append(String.format("Total messages sent: %d\n", getTotalMessagesSent()));
        report.append(String.format("Total messages received: %d\n", getTotalMessagesReceived()));
        report.append(String.format("Total bytes sent: %d (%.2f MB)\n", getTotalBytesSent(), getTotalBytesSent() / 1024.0 / 1024.0));
        report.append(String.format("Total bytes received: %d (%.2f MB)\n", getTotalBytesReceived(), getTotalBytesReceived() / 1024.0 / 1024.0));
        report.append(String.format("Average message latency: %.2f ms\n", getAverageLatency()));
        report.append("\n");
        
        // QoS statistics
        report.append("=== QoS Level Statistics ===\n");
        for (QoSLevel qos : QoSLevel.values()) {
            int successes = deliverySuccessByQoS.get(qos);
            int failures = deliveryFailuresByQoS.get(qos);
            
            report.append(String.format("\nQoS %d (%s):\n", qos.getValue(), qos.name()));
            report.append(String.format("  Successful deliveries: %d\n", successes));
            report.append(String.format("  Failed deliveries: %d\n", failures));
            report.append(String.format("  Success rate: %.2f%%\n", getSuccessRate(qos) * 100));
            report.append(String.format("  Average latency: %.2f ms\n", getAverageLatencyByQoS(qos)));
        }
        report.append("\n");
        
        // Broker statistics
        report.append("=== Broker Statistics ===\n");
        for (MqttBroker broker : brokers.values()) {
            MqttBrokerStatistics stats = broker.getStatistics();
            report.append(String.format("\nBroker: %s\n", broker.getBrokerId()));
            report.append(String.format("  Messages received: %d\n", stats.getMessagesReceived()));
            report.append(String.format("  Messages delivered: %d\n", stats.getMessagesDelivered()));
            report.append(String.format("  Messages dropped: %d\n", stats.getMessagesDropped()));
            report.append(String.format("  Bytes received: %d\n", stats.getBytesReceived()));
            report.append(String.format("  Bytes delivered: %d\n", stats.getBytesDelivered()));
            report.append(String.format("  Max concurrent clients: %d\n", stats.getMaxClientCount()));
            report.append(String.format("  Max subscriptions: %d\n", stats.getMaxSubscriptionCount()));
            report.append(String.format("  Delivery rate: %.2f%%\n", stats.getDeliveryRate() * 100));
        }
        report.append("\n");
        
        // Topic statistics
        report.append("=== Topic Statistics ===\n");
        for (MqttBroker broker : brokers.values()) {
            Map<String, MqttTopicManager.TopicStatistics> topicStats = broker.getTopicManager().getAllTopicStatistics();
            if (!topicStats.isEmpty()) {
                report.append(String.format("\nBroker: %s\n", broker.getBrokerId()));
                for (MqttTopicManager.TopicStatistics stats : topicStats.values()) {
                    report.append(String.format("  Topic '%s':\n", stats.getTopic()));
                    report.append(String.format("    Messages: %d\n", stats.getMessageCount()));
                    report.append(String.format("    Total bytes: %d\n", stats.getTotalBytes()));
                    report.append(String.format("    Deliveries: %d\n", stats.getDeliveryCount()));
                    report.append(String.format("    Avg message size: %.2f bytes\n", stats.getAverageMessageSize()));
                }
            }
        }
        
        report.append("\n====================================================\n");
        
        return report.toString();
    }

    /**
     * Exports metrics to a CSV file.
     *
     * @param filename the output filename
     * @throws IOException if writing fails
     */
    public void exportToCSV(String filename) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // Header
            writer.write("Metric,Value\n");
            
            // Basic metrics
            writer.write(String.format("SimulationDuration,%d\n", getSimulationDuration()));
            writer.write(String.format("BrokerCount,%d\n", brokers.size()));
            writer.write(String.format("PublisherCount,%d\n", publishers.size()));
            writer.write(String.format("SubscriberCount,%d\n", subscribers.size()));
            writer.write(String.format("TotalMessagesSent,%d\n", getTotalMessagesSent()));
            writer.write(String.format("TotalMessagesReceived,%d\n", getTotalMessagesReceived()));
            writer.write(String.format("TotalBytesSent,%d\n", getTotalBytesSent()));
            writer.write(String.format("TotalBytesReceived,%d\n", getTotalBytesReceived()));
            writer.write(String.format("AverageLatency,%.2f\n", getAverageLatency()));
            
            // QoS metrics
            for (QoSLevel qos : QoSLevel.values()) {
                writer.write(String.format("QoS%d_Successes,%d\n", qos.getValue(), deliverySuccessByQoS.get(qos)));
                writer.write(String.format("QoS%d_Failures,%d\n", qos.getValue(), deliveryFailuresByQoS.get(qos)));
                writer.write(String.format("QoS%d_SuccessRate,%.4f\n", qos.getValue(), getSuccessRate(qos)));
                writer.write(String.format("QoS%d_AvgLatency,%.2f\n", qos.getValue(), getAverageLatencyByQoS(qos)));
            }
        }
    }
}
