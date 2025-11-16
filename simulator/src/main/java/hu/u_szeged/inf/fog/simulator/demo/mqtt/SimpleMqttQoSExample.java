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
 * Simplified MQTT simulation example demonstrating QoS levels.
 * This example shows:
 * - 1 MQTT broker
 * - 3 devices publishing with different QoS levels (0, 1, 2)
 * - Direct subscriber clients receiving messages
 */
public class SimpleMqttQoSExample {

    public static void main(String[] args) throws Exception {
        
        System.out.println("========== MQTT QoS Levels Demonstration ==========\n");
        
        // Power state transitions for physical machines
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);
        Map<String, PowerState> cpuTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
        Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
        Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);
        
        // Create broker repository
        long brokerStorage = 10_737_418_240L; // 10 GB
        long brokerBandwidth = 125_000; // 1 Gbps
        Map<String, Integer> LatencyMap = new HashMap<>();
        LatencyMap.put("broker-repo", 5);
        
        Repository brokerRepo = new Repository(brokerStorage, "broker-repo", brokerBandwidth, brokerBandwidth,
                brokerBandwidth, LatencyMap, stTransitions, nwTransitions);
        brokerRepo.setState(NetworkNode.State.RUNNING);
        
        // Create MQTT broker with 100ms processing frequency and 5 second idle timeout
        MqttBroker broker = new MqttBroker(brokerRepo, 100, 5000);
        MqttMetricsCollector.getInstance().registerBroker(broker);
        broker.start();
        
        System.out.println("Created MQTT Broker: " + broker.getBrokerId());
        System.out.println("Broker will stop after 5 seconds of inactivity");
        
        // Create devices with different QoS levels
        QoSLevel[] qosLevels = {QoSLevel.AT_MOST_ONCE, QoSLevel.AT_LEAST_ONCE, QoSLevel.EXACTLY_ONCE};
        String[] qosNames = {"QoS 0 (At most once)", "QoS 1 (At least once)", "QoS 2 (Exactly once)"};
        
        List<MqttSmartDevice> devices = new ArrayList<>();
        List<MqttSubscriber> subscribers = new ArrayList<>();
        
        for (int i = 0; i < qosLevels.length; i++) {
            // Create device repository
            LatencyMap.put("device-repo-" + i, 5);
            Repository deviceRepo = new Repository(4_294_967_296L, "device-repo-" + i, 3_250, 3_250, 3_250,
                    LatencyMap, stTransitions, nwTransitions);
            deviceRepo.setState(NetworkNode.State.RUNNING);
            
            PhysicalMachine devicePm = new PhysicalMachine(1, 0.001, 1_073_741_824L,
                    deviceRepo, 1, 1, cpuTransitions);
            
            // Create MQTT device
            String topic = "sensors/test/device" + i;
            MqttSmartDevice device = new MqttSmartDevice(
                    0, 10000, 500, 2000, // start, stop, size, freq
                    new StaticMobilityStrategy(GeoLocation.generateRandomGeoLocation()),
                    new PliantDeviceStrategy(), devicePm, 50, false,
                    broker, topic, qosLevels[i]
            );
            
            devices.add(device);
            MqttMetricsCollector.getInstance().registerPublisher(device.getPublisher());
            
            // Create subscriber repository
            LatencyMap.put("subscriber-repo-" + i, 10);
            
            Repository subRepo = new Repository(4_294_967_296L, "subscriber-repo-" + i, 3_250, 3_250, 3_250,
                    LatencyMap, stTransitions, nwTransitions);
            subRepo.setState(NetworkNode.State.RUNNING);
            
            // Create subscriber
            MqttSubscriber subscriber = new MqttSubscriber("subscriber-" + i, subRepo);
            subscriber.connect(broker);
            subscriber.subscribe(topic, qosLevels[i]);
            
            subscribers.add(subscriber);
            MqttMetricsCollector.getInstance().registerSubscriber(subscriber);
            
            System.out.println("Created Device " + i + " with " + qosNames[i] + " publishing to: " + topic);
        }
        
        System.out.println("\nStarting simulation...\n");
        
        // Start metrics collection
        MqttMetricsCollector.getInstance().startSimulation();
        
        // Devices will start automatically through their lifecycle
        
        // Run simulation
        long startTime = System.nanoTime();
        
        Timed.simulateUntilLastEvent();
        

        long stopTime = System.nanoTime();
        
        // End metrics collection
        MqttMetricsCollector.getInstance().endSimulation();
        
        System.out.println("\nSimulation completed!");
        System.out.println("Execution time: " + ((stopTime - startTime) / 1_000_000) + " ms\n");
        
        // Generate and display metrics report
        String report = MqttMetricsCollector.getInstance().generateReport();
        System.out.println(report);
        
        // Display detailed QoS comparison
        System.out.println("\n=== QoS Level Comparison ===");
        for (int i = 0; i < qosLevels.length; i++) {
            System.out.println("\n" + qosNames[i] + ":");
            System.out.println("  Messages sent: " + devices.get(i).getPublisher().getMessagesSent());
            System.out.println("  Messages received: " + subscribers.get(i).getMessagesReceived());
            System.out.println("  Success rate: " + String.format("%.2f%%", 
                    MqttMetricsCollector.getInstance().getSuccessRate(qosLevels[i]) * 100));
            System.out.println("  Average latency: " + String.format("%.2f ms", 
                    MqttMetricsCollector.getInstance().getAverageLatencyByQoS(qosLevels[i])));
        }
        
        System.out.println("\n========== Simulation Complete ==========");
    }
}
