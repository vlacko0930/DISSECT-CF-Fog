package hu.u_szeged.inf.fog.simulator.iot.mqtt;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an MQTT broker in the simulator.
 * Manages client connections, topic subscriptions, and message routing.
 * Handles QoS levels and coordinates message delivery between publishers and subscribers.
 */
public class MqttBroker extends Timed {

    /**
     * Counter for generating broker IDs.
     */
    private static int brokerIdCounter = 0;

    /**
     * Unique identifier for this broker.
     */
    private final String brokerId;

    /**
     * The repository (network node) where the broker is hosted.
     */
    private final Repository repository;

    /**
     * Topic manager for handling subscriptions and routing.
     */
    private final MqttTopicManager topicManager;

    /**
     * Map of connected clients.
     * Key: client ID, Value: MqttClient instance
     */
    private final Map<String, MqttClient> connectedClients;

    /**
     * Queue of messages pending delivery.
     */
    private final Queue<MqttMessage> messageQueue;

    /**
     * Messages awaiting acknowledgment (QoS 1 and 2).
     * Key: message ID, Value: message
     */
    private final Map<Long, MqttMessage> awaitingAck;

    /**
     * Messages awaiting PUBREC acknowledgment (QoS 2).
     * Key: message ID, Value: message
     */
    private final Map<Long, MqttMessage> awaitingPubRec;

    /**
     * Messages awaiting PUBREL (QoS 2).
     * Key: message ID, Value: message
     */
    private final Map<Long, MqttMessage> awaitingPubRel;

    /**
     * Messages awaiting PUBCOMP (QoS 2).
     * Key: message ID, Value: message
     */
    private final Map<Long, MqttMessage> awaitingPubComp;

    /**
     * Statistics for this broker.
     */
    private final MqttBrokerStatistics statistics;

    /**
     * The frequency at which the broker processes messages (in ms).
     */
    private long processingFrequency;

    /**
     * Indicates if the broker is running.
     */
    private boolean running;

    /**
     * Idle timeout in milliseconds. If > 0, broker will stop after this period of inactivity.
     * If 0, broker runs indefinitely.
     */
    private long idleTimeout;

    /**
     * Timestamp of the last activity (message processed).
     */
    private long lastActivityTime;

    /**
     * Constructs a new MQTT broker.
     *
     * @param repository          the repository where the broker is hosted
     * @param processingFrequency the frequency for processing messages (in ms)
     */
    public MqttBroker(Repository repository, long processingFrequency) {
        this(repository, processingFrequency, 0);
    }

    /**
     * Constructs a new MQTT broker with idle timeout.
     *
     * @param repository          the repository where the broker is hosted
     * @param processingFrequency the frequency for processing messages (in ms)
     * @param idleTimeout        idle timeout in ms (0 = no timeout, runs indefinitely)
     */
    public MqttBroker(Repository repository, long processingFrequency, long idleTimeout) {
        this.brokerId = "broker-" + (++brokerIdCounter);
        this.repository = repository;
        this.topicManager = new MqttTopicManager();
        this.connectedClients = new ConcurrentHashMap<>();
        this.messageQueue = new LinkedList<>();
        this.awaitingAck = new ConcurrentHashMap<>();
        this.awaitingPubRec = new ConcurrentHashMap<>();
        this.awaitingPubRel = new ConcurrentHashMap<>();
        this.awaitingPubComp = new ConcurrentHashMap<>();
        this.statistics = new MqttBrokerStatistics(brokerId);
        this.processingFrequency = processingFrequency;
        this.idleTimeout = idleTimeout;
        this.lastActivityTime = 0;
        this.running = false;
    }

    /**
     * Starts the broker.
     */
    public void start() {
        if (!running) {
            running = true;
            lastActivityTime = Timed.getFireCount();
            subscribe(processingFrequency);
            SimLogger.logRun("MQTT Broker " + brokerId + " started at: " + Timed.getFireCount()
                    + (idleTimeout > 0 ? " (idle timeout: " + idleTimeout + " ms)" : ""));
        }
    }

    /**
     * Stops the broker.
     */
    public void stop() {
        if (running) {
            running = false;
            unsubscribe();
            SimLogger.logRun("MQTT Broker " + brokerId + " stopped at: " + Timed.getFireCount());
        }
    }

    /**
     * Registers a client with this broker.
     *
     * @param client the client to register
     */
    public void registerClient(MqttClient client) {
        connectedClients.put(client.getClientId(), client);
        statistics.incrementClientCount();
        SimLogger.logRun("Client " + client.getClientId() + " connected to broker " + brokerId +
                " at: " + Timed.getFireCount());
    }

    /**
     * Unregisters a client from this broker.
     *
     * @param client the client to unregister
     */
    public void unregisterClient(MqttClient client) {
        connectedClients.remove(client.getClientId());
        topicManager.unsubscribeAll(client.getClientId());
        statistics.decrementClientCount();
        SimLogger.logRun("Client " + client.getClientId() + " disconnected from broker " + brokerId +
                " at: " + Timed.getFireCount());
    }

    /**
     * Subscribes a client to a topic.
     *
     * @param clientId   the client ID
     * @param topicFilter the topic or pattern to subscribe to
     * @param qosLevel   the requested QoS level
     */
    public void subscribe(String clientId, String topicFilter, QoSLevel qosLevel) {
        if (!connectedClients.containsKey(clientId)) {
            SimLogger.logRun("Error: Client " + clientId + " not connected to broker " + brokerId);
            return;
        }
        topicManager.subscribe(clientId, topicFilter);
        statistics.incrementSubscriptionCount();
        SimLogger.logRun("Client " + clientId + " subscribed to '" + topicFilter + "' with QoS " +
                qosLevel.getValue() + " at: " + Timed.getFireCount());
    }

    /**
     * Unsubscribes a client from a topic.
     *
     * @param clientId   the client ID
     * @param topicFilter the topic or pattern to unsubscribe from
     */
    public void unsubscribe(String clientId, String topicFilter) {
        topicManager.unsubscribe(clientId, topicFilter);
        statistics.decrementSubscriptionCount();
        SimLogger.logRun("Client " + clientId + " unsubscribed from '" + topicFilter +
                "' at: " + Timed.getFireCount());
    }

    /**
     * Publishes a message to the broker.
     *
     * @param message the message to publish
     * @param sourceRepository the repository of the publishing client
     */
    public void publish(MqttMessage message, Repository sourceRepository) {
        if (!running) {
            SimLogger.logRun("Error: Broker " + brokerId + " is not running");
            return;
        }

        statistics.incrementMessagesReceived();
        statistics.addBytesReceived(message.getPayloadSize());
        topicManager.recordPublication(message.getTopic(), message.getPayloadSize());

        // Simulate network transfer from publisher to broker
        try {
            StorageObject msgObject = new StorageObject("mqtt-msg-" + message.getMessageId(),
                    message.getPayloadSize(), false);
            sourceRepository.registerObject(msgObject);

            NetworkNode.initTransfer(message.getPayloadSize(), ResourceConsumption.unlimitedProcessing,
                    sourceRepository, repository, new ConsumptionEventAdapter() {
                        @Override
                        public void conComplete() {
                            sourceRepository.deregisterObject(msgObject);
                            message.setBrokerReceiveTime(Timed.getFireCount());
                            messageQueue.offer(message);
                            SimLogger.logRun("Broker " + brokerId + " received message " + message.getMessageId() +
                                    " on topic '" + message.getTopic() + "' at: " + Timed.getFireCount());
                        }
                    });
        } catch (NetworkException e) {
            SimLogger.logRun("Error publishing message to broker " + brokerId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Processes messages in the queue and delivers them to subscribers.
     * This method is called periodically based on the processing frequency.
     */
    @Override
    public void tick(long fires) {
        if (!running) {
            return;
        }

        long currentTime = Timed.getFireCount();
        boolean hadActivity = false;

        // Process pending acknowledgments
        processAcknowledgments();

        // Process new messages
        while (!messageQueue.isEmpty()) {
            MqttMessage message = messageQueue.poll();
            deliverMessage(message);
            hadActivity = true;
        }

        // Update last activity time if there was any activity
        if (hadActivity || !awaitingAck.isEmpty() || !awaitingPubRec.isEmpty() 
                || !awaitingPubRel.isEmpty() || !awaitingPubComp.isEmpty()) {
            lastActivityTime = currentTime;
        }

        // Check for idle timeout
        if (idleTimeout > 0 && lastActivityTime > 0) {
            long idleTime = currentTime - lastActivityTime;
            if (idleTime >= idleTimeout) {
                SimLogger.logRun("Broker " + brokerId + " stopping due to idle timeout (" 
                        + idleTimeout + " ms). Idle time: " + idleTime + " ms");
                stop();
            }
        }
    }

    /**
     * Delivers a message to all subscribed clients.
     *
     * @param message the message to deliver
     */
    private void deliverMessage(MqttMessage message) {
        Set<String> subscribers = topicManager.getSubscribers(message.getTopic());

        if (subscribers.isEmpty()) {
            SimLogger.logRun("No subscribers for topic '" + message.getTopic() + "'");
            statistics.incrementMessagesDropped();
            return;
        }

        for (String subscriberId : subscribers) {
            MqttClient subscriber = connectedClients.get(subscriberId);
            if (subscriber == null) {
                continue;
            }

            message.incrementDeliveryAttempts();

            // Handle different QoS levels
            switch (message.getQosLevel()) {
                case AT_MOST_ONCE:
                    deliverQoS0(message, subscriber);
                    break;
                case AT_LEAST_ONCE:
                    deliverQoS1(message, subscriber);
                    break;
                case EXACTLY_ONCE:
                    deliverQoS2(message, subscriber);
                    break;
            }
        }
    }

    /**
     * Delivers a message with QoS 0 (at most once).
     *
     * @param message    the message to deliver
     * @param subscriber the subscriber to deliver to
     */
    private void deliverQoS0(MqttMessage message, MqttClient subscriber) {
        try {
            StorageObject msgObject = new StorageObject("mqtt-msg-" + message.getMessageId() +
                    "-to-" + subscriber.getClientId(), message.getPayloadSize(), false);
            repository.registerObject(msgObject);

            // Find subscriber's repository
            Repository subscriberRepo = findClientRepository(subscriber);
            if (subscriberRepo == null) {
                SimLogger.logRun("Error: Could not find repository for subscriber " + subscriber.getClientId());
                return;
            }

            NetworkNode.initTransfer(message.getPayloadSize(), ResourceConsumption.unlimitedProcessing,
                    repository, subscriberRepo, new ConsumptionEventAdapter() {
                        @Override
                        public void conComplete() {
                            repository.deregisterObject(msgObject);
                            message.setSubscriberReceiveTime(Timed.getFireCount());
                            subscriber.onMessageReceived(message);
                            statistics.incrementMessagesDelivered();
                            statistics.addBytesDelivered(message.getPayloadSize());
                            topicManager.recordDelivery(message.getTopic());
                            SimLogger.logRun("QoS0: Message " + message.getMessageId() + " delivered to " +
                                    subscriber.getClientId() + " at: " + Timed.getFireCount() +
                                    " (latency: " + message.getEndToEndLatency() + "ms)");
                        }
                    });
        } catch (NetworkException e) {
            SimLogger.logRun("Error delivering QoS0 message: " + e.getMessage());
            statistics.incrementMessagesDropped();
        }
    }

    /**
     * Delivers a message with QoS 1 (at least once).
     *
     * @param message    the message to deliver
     * @param subscriber the subscriber to deliver to
     */
    private void deliverQoS1(MqttMessage message, MqttClient subscriber) {
        try {
            StorageObject msgObject = new StorageObject("mqtt-msg-" + message.getMessageId() +
                    "-to-" + subscriber.getClientId(), message.getPayloadSize(), false);
            repository.registerObject(msgObject);

            Repository subscriberRepo = findClientRepository(subscriber);
            if (subscriberRepo == null) {
                SimLogger.logRun("Error: Could not find repository for subscriber " + subscriber.getClientId());
                return;
            }

            NetworkNode.initTransfer(message.getPayloadSize(), ResourceConsumption.unlimitedProcessing,
                    repository, subscriberRepo, new ConsumptionEventAdapter() {
                        @Override
                        public void conComplete() {
                            repository.deregisterObject(msgObject);
                            message.setSubscriberReceiveTime(Timed.getFireCount());
                            subscriber.onMessageReceived(message);
                            
                            // Wait for PUBACK
                            awaitingAck.put(message.getMessageId(), message);
                            SimLogger.logRun("QoS1: Message " + message.getMessageId() + " delivered to " +
                                    subscriber.getClientId() + ", awaiting PUBACK at: " + Timed.getFireCount());
                        }
                    });
        } catch (NetworkException e) {
            SimLogger.logRun("Error delivering QoS1 message: " + e.getMessage());
            statistics.incrementMessagesDropped();
        }
    }

    /**
     * Delivers a message with QoS 2 (exactly once).
     *
     * @param message    the message to deliver
     * @param subscriber the subscriber to deliver to
     */
    private void deliverQoS2(MqttMessage message, MqttClient subscriber) {
        try {
            StorageObject msgObject = new StorageObject("mqtt-msg-" + message.getMessageId() +
                    "-to-" + subscriber.getClientId(), message.getPayloadSize(), false);
            repository.registerObject(msgObject);

            Repository subscriberRepo = findClientRepository(subscriber);
            if (subscriberRepo == null) {
                SimLogger.logRun("Error: Could not find repository for subscriber " + subscriber.getClientId());
                return;
            }

            NetworkNode.initTransfer(message.getPayloadSize(), ResourceConsumption.unlimitedProcessing,
                    repository, subscriberRepo, new ConsumptionEventAdapter() {
                        @Override
                        public void conComplete() {
                            repository.deregisterObject(msgObject);
                            message.setSubscriberReceiveTime(Timed.getFireCount());
                            subscriber.onMessageReceived(message);
                            
                            // Wait for PUBREC
                            awaitingPubRec.put(message.getMessageId(), message);
                            SimLogger.logRun("QoS2: Message " + message.getMessageId() + " delivered to " +
                                    subscriber.getClientId() + ", awaiting PUBREC at: " + Timed.getFireCount());
                        }
                    });
        } catch (NetworkException e) {
            SimLogger.logRun("Error delivering QoS2 message: " + e.getMessage());
            statistics.incrementMessagesDropped();
        }
    }

    /**
     * Processes acknowledgments for QoS 1 and QoS 2 messages.
     */
    private void processAcknowledgments() {
        // Simulate acknowledgments (in real implementation, these would come from clients)
        // For QoS 1: PUBACK
        for (Iterator<Map.Entry<Long, MqttMessage>> it = awaitingAck.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, MqttMessage> entry = it.next();
            MqttMessage msg = entry.getValue();
            
            // Simulate acknowledgment after a small delay
            if (Timed.getFireCount() - msg.getSubscriberReceiveTime() >= processingFrequency) {
                msg.setAcknowledged(true);
                statistics.incrementMessagesDelivered();
                statistics.addBytesDelivered(msg.getPayloadSize());
                topicManager.recordDelivery(msg.getTopic());
                SimLogger.logRun("QoS1: PUBACK received for message " + msg.getMessageId() +
                        " at: " + Timed.getFireCount() + " (latency: " + msg.getEndToEndLatency() + "ms)");
                it.remove();
            }
        }

        // For QoS 2: Four-step handshake
        // Step 1: PUBREC
        for (Iterator<Map.Entry<Long, MqttMessage>> it = awaitingPubRec.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, MqttMessage> entry = it.next();
            MqttMessage msg = entry.getValue();
            
            if (Timed.getFireCount() - msg.getSubscriberReceiveTime() >= processingFrequency) {
                msg.setReceived(true);
                awaitingPubRel.put(msg.getMessageId(), msg);
                SimLogger.logRun("QoS2: PUBREC received for message " + msg.getMessageId() +
                        ", sending PUBREL at: " + Timed.getFireCount());
                it.remove();
            }
        }

        // Step 2: PUBREL processed, wait for PUBCOMP
        for (Iterator<Map.Entry<Long, MqttMessage>> it = awaitingPubRel.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, MqttMessage> entry = it.next();
            MqttMessage msg = entry.getValue();
            
            if (Timed.getFireCount() - msg.getSubscriberReceiveTime() >= processingFrequency * 2) {
                msg.setReleased(true);
                awaitingPubComp.put(msg.getMessageId(), msg);
                SimLogger.logRun("QoS2: PUBREL confirmed for message " + msg.getMessageId() +
                        ", awaiting PUBCOMP at: " + Timed.getFireCount());
                it.remove();
            }
        }

        // Step 3: PUBCOMP
        for (Iterator<Map.Entry<Long, MqttMessage>> it = awaitingPubComp.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, MqttMessage> entry = it.next();
            MqttMessage msg = entry.getValue();
            
            if (Timed.getFireCount() - msg.getSubscriberReceiveTime() >= processingFrequency * 3) {
                msg.setCompleted(true);
                statistics.incrementMessagesDelivered();
                statistics.addBytesDelivered(msg.getPayloadSize());
                topicManager.recordDelivery(msg.getTopic());
                SimLogger.logRun("QoS2: PUBCOMP received for message " + msg.getMessageId() +
                        " at: " + Timed.getFireCount() + " (latency: " + msg.getEndToEndLatency() + "ms)");
                it.remove();
            }
        }
    }

    /**
     * Finds the repository associated with a client.
     * This is a simplified implementation - in a real scenario, this would be
     * determined by the client's network location.
     *
     * @param client the client
     * @return the repository, or null if not found
     */
    private Repository findClientRepository(MqttClient client) {
        // In the actual implementation, clients would have their own repositories
        // For now, we return the broker's repository (assuming same network)
        return repository;
    }

    /**
     * Gets the broker ID.
     *
     * @return the broker ID
     */
    public String getBrokerId() {
        return brokerId;
    }

    /**
     * Gets the repository where this broker is hosted.
     *
     * @return the repository
     */
    public Repository getRepository() {
        return repository;
    }

    /**
     * Gets the topic manager.
     *
     * @return the topic manager
     */
    public MqttTopicManager getTopicManager() {
        return topicManager;
    }

    /**
     * Gets the broker statistics.
     *
     * @return the statistics
     */
    public MqttBrokerStatistics getStatistics() {
        return statistics;
    }

    /**
     * Gets the number of currently connected clients.
     *
     * @return the client count
     */
    public int getConnectedClientCount() {
        return connectedClients.size();
    }

    /**
     * Checks if the broker is running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the idle timeout value.
     *
     * @return idle timeout in milliseconds (0 = no timeout)
     */
    public long getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * Sets the idle timeout value.
     *
     * @param idleTimeout idle timeout in milliseconds (0 = no timeout)
     */
    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
        SimLogger.logRun("Broker " + brokerId + " idle timeout set to: " + idleTimeout + " ms");
    }

    /**
     * Gets the time of last activity.
     *
     * @return last activity timestamp
     */
    public long getLastActivityTime() {
        return lastActivityTime;
    }
}
