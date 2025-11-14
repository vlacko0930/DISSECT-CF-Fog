package hu.u_szeged.inf.fog.simulator.demo.mqtt;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;
import hu.u_szeged.inf.fog.simulator.iot.mobility.GeoLocation;
import hu.u_szeged.inf.fog.simulator.iot.mobility.StaticMobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.mqtt.*;
import hu.u_szeged.inf.fog.simulator.iot.strategy.PliantDeviceStrategy;

import java.util.*;

/**
 * Large-Scale MQTT Simulation Example
 * 
 * Demonstrates:
 * - Multiple MQTT brokers (Cloud, Edge1, Edge2)
 * - Many devices (30 total: 10 per broker)
 * - Multiple subscribers per broker
 * - Resource utilization metrics
 * - Performance comparison between brokers
 */
public class MqttLargeScaleExample {

    private static final int DEVICES_PER_BROKER = 10;
    private static final int SUBSCRIBERS_PER_BROKER = 3;
    private static final int NUM_BROKERS = 3;
    
    public static void main(String[] args) throws Exception {
        
        System.out.println("========== MQTT Large-Scale Simulation ==========\n");
        System.out.println("Configuration:");
        System.out.println("  - Brokers: " + NUM_BROKERS);
        System.out.println("  - Devices per broker: " + DEVICES_PER_BROKER);
        System.out.println("  - Subscribers per broker: " + SUBSCRIBERS_PER_BROKER);
        System.out.println("  - Total devices: " + (DEVICES_PER_BROKER * NUM_BROKERS));
        System.out.println("  - Total subscribers: " + (SUBSCRIBERS_PER_BROKER * NUM_BROKERS));
        System.out.println();
        
        // Power state transitions
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);
        Map<String, PowerState> cpuTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
        Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
        Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);
        
        // Create brokers
        List<MqttBroker> brokers = new ArrayList<>();
        String[] brokerNames = {"cloud-broker", "edge-broker-1", "edge-broker-2"};
        
        for (int b = 0; b < NUM_BROKERS; b++) {
            Map<String, Integer> brokerLatencyMap = new HashMap<>();
            brokerLatencyMap.put(brokerNames[b] + "-repo", 5);
            
            Repository brokerRepo = new Repository(10_737_418_240L, brokerNames[b] + "-repo", 
                    125_000, 125_000, 125_000, brokerLatencyMap, stTransitions, nwTransitions);
            brokerRepo.setState(NetworkNode.State.RUNNING);
            
            MqttBroker broker = new MqttBroker(brokerRepo, 50); // 50ms processing frequency
            MqttMetricsCollector.getInstance().registerBroker(broker);
            broker.start();
            
            brokers.add(broker);
            System.out.println("Created Broker: " + brokerNames[b]);
        }
        
        System.out.println();
        
        // Create devices and subscribers for each broker
        List<MqttSmartDevice> allDevices = new ArrayList<>();
        List<MqttSubscriber> allSubscribers = new ArrayList<>();
        
        for (int b = 0; b < NUM_BROKERS; b++) {
            MqttBroker broker = brokers.get(b);
            String brokerName = brokerNames[b];
            
            System.out.println("Setting up devices and subscribers for " + brokerName + "...");
            
            // Create devices for this broker
            for (int d = 0; d < DEVICES_PER_BROKER; d++) {
                String deviceId = "device-" + b + "-" + d;
                String topic = brokerName + "/sensors/" + (d % 3 == 0 ? "temperature" : 
                                                           d % 3 == 1 ? "humidity" : "pressure");
                
                Map<String, Integer> deviceLatencyMap = new HashMap<>();
                deviceLatencyMap.put(deviceId + "-repo", 10);
                
                Repository deviceRepo = new Repository(4_294_967_296L, deviceId + "-repo", 
                        3_250, 3_250, 3_250, deviceLatencyMap, stTransitions, nwTransitions);
                deviceRepo.setState(NetworkNode.State.RUNNING);
                
                PhysicalMachine devicePm = new PhysicalMachine(1, 0.001, 1_073_741_824L,
                        deviceRepo, 1, 1, cpuTransitions);
                
                // Vary QoS levels across devices
                QoSLevel qos = d % 3 == 0 ? QoSLevel.AT_MOST_ONCE :
                              d % 3 == 1 ? QoSLevel.AT_LEAST_ONCE : QoSLevel.EXACTLY_ONCE;
                
                MqttSmartDevice device = new MqttSmartDevice(
                        0, 15000, 500, 1500 + (d * 100), // Varying frequencies
                        new StaticMobilityStrategy(GeoLocation.generateRandomGeoLocation()),
                        new PliantDeviceStrategy(), devicePm, 50, false,
                        broker, topic, qos
                );
                
                allDevices.add(device);
                MqttMetricsCollector.getInstance().registerPublisher(device.getPublisher());
            }
            
            // Create subscribers for this broker
            for (int s = 0; s < SUBSCRIBERS_PER_BROKER; s++) {
                String subscriberId = "subscriber-" + b + "-" + s;
                String subscriptionPattern = s == 0 ? brokerName + "/sensors/temperature" :
                                           s == 1 ? brokerName + "/sensors/#" : brokerName + "/#";
                
                Map<String, Integer> subLatencyMap = new HashMap<>();
                subLatencyMap.put(subscriberId + "-repo", 10);
                
                Repository subRepo = new Repository(4_294_967_296L, subscriberId + "-repo", 
                        3_250, 3_250, 3_250, subLatencyMap, stTransitions, nwTransitions);
                subRepo.setState(NetworkNode.State.RUNNING);
                
                MqttSubscriber subscriber = new MqttSubscriber(subscriberId, subRepo);
                subscriber.connect(broker);
                subscriber.subscribe(subscriptionPattern, QoSLevel.AT_LEAST_ONCE);
                
                allSubscribers.add(subscriber);
                MqttMetricsCollector.getInstance().registerSubscriber(subscriber);
            }
            
            System.out.println("  - Created " + DEVICES_PER_BROKER + " devices");
            System.out.println("  - Created " + SUBSCRIBERS_PER_BROKER + " subscribers");
        }
        
        System.out.println("\n========================================");
        System.out.println("Starting simulation...\n");
        
        MqttMetricsCollector.getInstance().startSimulation();
        
        long startTime = System.nanoTime();
        
        // Run simulation until devices stop (15000 ms) + buffer for message delivery
        Timed.simulateUntilLastEvent();
        
        long stopTime = System.nanoTime();
        
        MqttMetricsCollector.getInstance().endSimulation();
        
        System.out.println("\nSimulation completed!");
        System.out.println("Execution time: " + ((stopTime - startTime) / 1_000_000) + " ms\n");
        
        // Generate comprehensive metrics report
        String report = MqttMetricsCollector.getInstance().generateReport();
        System.out.println(report);
        
        // Per-broker statistics
        System.out.println("\n=== Per-Broker Statistics ===");
        for (int b = 0; b < NUM_BROKERS; b++) {
            MqttBroker broker = brokers.get(b);
            MqttBrokerStatistics stats = broker.getStatistics();
            
            System.out.println("\n" + brokerNames[b] + ":");
            System.out.println("  Messages received: " + stats.getMessagesReceived());
            System.out.println("  Messages delivered: " + stats.getMessagesDelivered());
            System.out.println("  Messages dropped: " + stats.getMessagesDropped());
            System.out.println("  Bytes received: " + (stats.getBytesReceived() / 1024) + " KB");
            System.out.println("  Bytes delivered: " + (stats.getBytesDelivered() / 1024) + " KB");
            System.out.println("  Current clients: " + stats.getCurrentClientCount());
            System.out.println("  Max clients: " + stats.getMaxClientCount());
            System.out.println("  Current subscriptions: " + stats.getCurrentSubscriptionCount());
            System.out.println("  Max subscriptions: " + stats.getMaxSubscriptionCount());
            System.out.println("  Delivery rate: " + String.format("%.2f%%", stats.getDeliveryRate() * 100));
            System.out.println("  Drop rate: " + String.format("%.2f%%", stats.getDropRate() * 100));
            System.out.println("  Average processing time: " + String.format("%.2f ms", stats.getAverageProcessingTime()));
        }
        
        // Device statistics by broker
        System.out.println("\n=== Device Statistics by Broker ===");
        for (int b = 0; b < NUM_BROKERS; b++) {
            long totalMessagesSent = 0;
            int deviceCount = 0;
            
            for (int d = 0; d < DEVICES_PER_BROKER; d++) {
                int deviceIdx = b * DEVICES_PER_BROKER + d;
                totalMessagesSent += allDevices.get(deviceIdx).getPublisher().getMessagesSent();
                deviceCount++;
            }
            
            System.out.println("\n" + brokerNames[b] + ":");
            System.out.println("  Total devices: " + deviceCount);
            System.out.println("  Total messages sent: " + totalMessagesSent);
            System.out.println("  Average messages per device: " + (totalMessagesSent / deviceCount));
        }
        
        // Subscriber statistics by broker
        System.out.println("\n=== Subscriber Statistics by Broker ===");
        for (int b = 0; b < NUM_BROKERS; b++) {
            System.out.println("\n" + brokerNames[b] + ":");
            
            for (int s = 0; s < SUBSCRIBERS_PER_BROKER; s++) {
                int subIdx = b * SUBSCRIBERS_PER_BROKER + s;
                MqttSubscriber subscriber = allSubscribers.get(subIdx);
                String pattern = s == 0 ? brokerNames[b] + "/sensors/temperature" :
                               s == 1 ? brokerNames[b] + "/sensors/#" : brokerNames[b] + "/#";
                
                System.out.println("  " + subscriber.getClientId() + " (" + pattern + "): " + 
                                 subscriber.getMessagesReceived() + " messages");
            }
        }
        
        // QoS distribution
        System.out.println("\n=== QoS Level Distribution ===");
        int qos0Count = 0, qos1Count = 0, qos2Count = 0;
        for (int i = 0; i < allDevices.size(); i++) {
            // QoS varies based on device index
            int qosLevel = i % 3;
            if (qosLevel == 0) qos0Count++;
            else if (qosLevel == 1) qos1Count++;
            else if (qosLevel == 2) qos2Count++;
        }
        
        System.out.println("QoS 0 devices: " + qos0Count);
        System.out.println("QoS 1 devices: " + qos1Count);
        System.out.println("QoS 2 devices: " + qos2Count);
        
        // Overall statistics
        long totalSent = allDevices.stream()
                .mapToLong(d -> d.getPublisher().getMessagesSent())
                .sum();
        long totalReceived = allSubscribers.stream()
                .mapToLong(MqttSubscriber::getMessagesReceived)
                .sum();
        
        System.out.println("\n=== Overall Statistics ===");
        System.out.println("Total messages sent by all devices: " + totalSent);
        System.out.println("Total messages received by all subscribers: " + totalReceived);
        System.out.println("Average messages per device: " + (totalSent / allDevices.size()));
        System.out.println("Average messages per subscriber: " + (totalReceived / allSubscribers.size()));
        
        System.out.println("\n========== Large-Scale Example Complete ==========");
    }
}
