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
 * MQTT Hierarchical Topics Example
 * 
 * Demonstrates hierarchical topic structures with wildcard subscriptions:
 * - home/livingroom/temperature
 * - home/bedroom/temperature  
 * - sensors/outdoor/humidity
 * - industry/machine1/pressure
 * 
 * Wildcard subscriptions:
 * - home/+/temperature (single-level wildcard)
 * - sensors/# (multi-level wildcard)
 */
public class MqttHierarchicalTopicsExample {

    public static void main(String[] args) throws Exception {
        
        System.out.println("========== MQTT Hierarchical Topics Demonstration ==========\n");
        
        // Power state transitions
        EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions =
                PowerTransitionGenerator.generateTransitions(20, 200, 300, 10, 20);
        Map<String, PowerState> cpuTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
        Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
        Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);
        
        // Create broker
        Map<String, Integer> latencyMap = new HashMap<>();
        latencyMap.put("broker-repo", 5);
        
        Repository brokerRepo = new Repository(10_737_418_240L, "broker-repo", 125_000, 125_000,
                125_000, latencyMap, stTransitions, nwTransitions);
        brokerRepo.setState(NetworkNode.State.RUNNING);
        
        // Create broker with 100ms processing frequency and 5 second idle timeout
        MqttBroker broker = new MqttBroker(brokerRepo, 100, 5000);
        MqttMetricsCollector.getInstance().registerBroker(broker);
        broker.start();
        
        System.out.println("Created MQTT Broker: " + broker.getBrokerId());
        System.out.println("Broker will stop after 5 seconds of inactivity\n");
        
        // Define hierarchical topics
        String[] topics = {
            "home/livingroom/temperature",
            "home/bedroom/temperature",
            "sensors/outdoor/humidity",
            "industry/machine1/pressure"
        };
        
        String[] topicDescriptions = {
            "Home Living Room Temperature Sensor",
            "Home Bedroom Temperature Sensor",
            "Outdoor Humidity Sensor",
            "Industrial Machine Pressure Sensor"
        };
        
        // Create devices for each topic
        List<MqttSmartDevice> devices = new ArrayList<>();
        
        for (int i = 0; i < topics.length; i++) {
            latencyMap.put("device-repo-" + i, 10);
            
            Repository deviceRepo = new Repository(4_294_967_296L, "device-repo-" + i, 3_250, 3_250, 3_250,
                    latencyMap, stTransitions, nwTransitions);
            deviceRepo.setState(NetworkNode.State.RUNNING);
            
            PhysicalMachine devicePm = new PhysicalMachine(1, 0.001, 1_073_741_824L,
                    deviceRepo, 1, 1, cpuTransitions);
            
            MqttSmartDevice device = new MqttSmartDevice(
                    0, 10000, 500, 2000,
                    new StaticMobilityStrategy(GeoLocation.generateRandomGeoLocation()),
                    new PliantDeviceStrategy(), devicePm, 50, false,
                    broker, topics[i], QoSLevel.AT_LEAST_ONCE
            );
            
            devices.add(device);
            MqttMetricsCollector.getInstance().registerPublisher(device.getPublisher());
            
            System.out.println("Created Device: " + topicDescriptions[i] + " -> " + topics[i]);
        }
        
        // Create subscribers with wildcard patterns
        List<MqttSubscriber> subscribers = new ArrayList<>();
        
        // Subscriber 1: Subscribes to all home temperature sensors (single-level wildcard)
        {
            latencyMap.put("subscriber-repo-0", 10);
            
            Repository subRepo = new Repository(4_294_967_296L, "subscriber-repo-0", 3_250, 3_250, 3_250,
                    latencyMap, stTransitions, nwTransitions);
            subRepo.setState(NetworkNode.State.RUNNING);
            
            MqttSubscriber subscriber = new MqttSubscriber("home-temp-monitor", subRepo);
            subscriber.connect(broker);
            subscriber.subscribe("home/+/temperature", QoSLevel.AT_LEAST_ONCE);
            
            subscribers.add(subscriber);
            MqttMetricsCollector.getInstance().registerSubscriber(subscriber);
            
            System.out.println("\nCreated Subscriber: home-temp-monitor");
            System.out.println("  Subscribed to: home/+/temperature");
            System.out.println("  Will receive:");
            System.out.println("    - home/livingroom/temperature");
            System.out.println("    - home/bedroom/temperature");
        }
        
        // Subscriber 2: Subscribes to all sensors topics (multi-level wildcard)
        {
            latencyMap.put("subscriber-repo-1", 10);
            
            Repository subRepo = new Repository(4_294_967_296L, "subscriber-repo-1", 3_250, 3_250, 3_250,
                    latencyMap, stTransitions, nwTransitions);
            subRepo.setState(NetworkNode.State.RUNNING);
            
            MqttSubscriber subscriber = new MqttSubscriber("all-sensors-monitor", subRepo);
            subscriber.connect(broker);
            subscriber.subscribe("sensors/#", QoSLevel.AT_LEAST_ONCE);
            
            subscribers.add(subscriber);
            MqttMetricsCollector.getInstance().registerSubscriber(subscriber);
            
            System.out.println("\nCreated Subscriber: all-sensors-monitor");
            System.out.println("  Subscribed to: sensors/#");
            System.out.println("  Will receive:");
            System.out.println("    - sensors/outdoor/humidity");
        }
        
        // Subscriber 3: Subscribes to industry topics
        {
            latencyMap.put("subscriber-repo-2", 10);
            
            Repository subRepo = new Repository(4_294_967_296L, "subscriber-repo-2", 3_250, 3_250, 3_250,
                    latencyMap, stTransitions, nwTransitions);
            subRepo.setState(NetworkNode.State.RUNNING);
            
            MqttSubscriber subscriber = new MqttSubscriber("industry-monitor", subRepo);
            subscriber.connect(broker);
            subscriber.subscribe("industry/#", QoSLevel.AT_LEAST_ONCE);
            
            subscribers.add(subscriber);
            MqttMetricsCollector.getInstance().registerSubscriber(subscriber);
            
            System.out.println("\nCreated Subscriber: industry-monitor");
            System.out.println("  Subscribed to: industry/#");
            System.out.println("  Will receive:");
            System.out.println("    - industry/machine1/pressure");
        }
        
        // Subscriber 4: Universal logger - subscribes to all topics
        {
            latencyMap.put("subscriber-repo-3", 10);
            
            Repository subRepo = new Repository(4_294_967_296L, "subscriber-repo-3", 3_250, 3_250, 3_250,
                    latencyMap, stTransitions, nwTransitions);
            subRepo.setState(NetworkNode.State.RUNNING);
            
            MqttSubscriber subscriber = new MqttSubscriber("universal-logger", subRepo);
            subscriber.connect(broker);
            subscriber.subscribe("#", QoSLevel.AT_MOST_ONCE);
            
            subscribers.add(subscriber);
            MqttMetricsCollector.getInstance().registerSubscriber(subscriber);
            
            System.out.println("\nCreated Subscriber: universal-logger");
            System.out.println("  Subscribed to: #");
            System.out.println("  Will receive: ALL messages");
        }
        
        System.out.println("\n========================================");
        System.out.println("Starting simulation...\n");
        
        MqttMetricsCollector.getInstance().startSimulation();
        
        long startTime = System.nanoTime();
        
        // Run simulation until last event (broker will auto-stop after 5s idle)
        Timed.simulateUntilLastEvent();
        
        long stopTime = System.nanoTime();
        
        MqttMetricsCollector.getInstance().endSimulation();
        
        System.out.println("\nSimulation completed!");
        System.out.println("Execution time: " + ((stopTime - startTime) / 1_000_000) + " ms\n");
        
        // Generate metrics report
        String report = MqttMetricsCollector.getInstance().generateReport();
        System.out.println(report);
        
        // Display topic routing results
        System.out.println("\n=== Topic Routing Verification ===");
        System.out.println("\nPublished messages per topic:");
        for (int i = 0; i < topics.length; i++) {
            System.out.println("  " + topics[i] + ": " + devices.get(i).getPublisher().getMessagesSent() + " messages");
        }
        
        System.out.println("\nReceived messages per subscriber:");
        String[] subNames = {"home-temp-monitor (home/+/temperature)", 
                             "all-sensors-monitor (sensors/#)", 
                             "industry-monitor (industry/#)",
                             "universal-logger (#)"};
        for (int i = 0; i < subscribers.size(); i++) {
            System.out.println("  " + subNames[i] + ": " + subscribers.get(i).getMessagesReceived() + " messages");
        }
        
        // Expected routing analysis
        System.out.println("\n=== Expected vs Actual Routing ===");
        
        long homeTempMsgs = devices.get(0).getPublisher().getMessagesSent() + 
                          devices.get(1).getPublisher().getMessagesSent();
        System.out.println("\nhome-temp-monitor should receive: " + homeTempMsgs + " messages");
        System.out.println("  (home/livingroom/temperature + home/bedroom/temperature)");
        System.out.println("  Actual: " + subscribers.get(0).getMessagesReceived() + " messages");
        
        long sensorsMsgs = devices.get(2).getPublisher().getMessagesSent();
        System.out.println("\nall-sensors-monitor should receive: " + sensorsMsgs + " messages");
        System.out.println("  (sensors/outdoor/humidity)");
        System.out.println("  Actual: " + subscribers.get(1).getMessagesReceived() + " messages");
        
        long industryMsgs = devices.get(3).getPublisher().getMessagesSent();
        System.out.println("\nindustry-monitor should receive: " + industryMsgs + " messages");
        System.out.println("  (industry/machine1/pressure)");
        System.out.println("  Actual: " + subscribers.get(2).getMessagesReceived() + " messages");
        
        long totalMsgs = homeTempMsgs + sensorsMsgs + industryMsgs;
        System.out.println("\nuniversal-logger should receive: " + totalMsgs + " messages");
        System.out.println("  (ALL topics)");
        System.out.println("  Actual: " + subscribers.get(3).getMessagesReceived() + " messages");
        
        System.out.println("\n========== Example Complete ==========");
    }
}
