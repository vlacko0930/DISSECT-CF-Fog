package hu.u_szeged.inf.fog.simulator.iot.mqtt;

import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents an MQTT subscriber client.
 * Subscribers receive messages from the broker on subscribed topics.
 */
public class MqttSubscriber extends MqttClient {

    /**
     * The repository for network operations.
     */
    private final Repository repository;

    /**
     * List of subscribed topics/patterns.
     */
    private final List<String> subscriptions;

    /**
     * Callback function to handle received messages.
     */
    private Consumer<MqttMessage> messageHandler;

    /**
     * Statistics for this subscriber.
     */
    private long messagesReceived;
    private long bytesReceived;

    /**
     * Constructs a new MQTT subscriber.
     *
     * @param clientId   the unique client ID
     * @param repository the repository for network operations
     */
    public MqttSubscriber(String clientId, Repository repository) {
        super(clientId);
        this.repository = repository;
        this.subscriptions = new ArrayList<>();
        this.messageHandler = null;
        this.messagesReceived = 0;
        this.bytesReceived = 0;
    }

    /**
     * Subscribes to a topic or pattern.
     *
     * @param topicFilter the topic or pattern to subscribe to
     * @param qosLevel    the requested QoS level
     */
    public void subscribe(String topicFilter, QoSLevel qosLevel) {
        if (!connected) {
            SimLogger.logRun("Error: Subscriber " + clientId + " is not connected");
            return;
        }

        broker.subscribe(clientId, topicFilter, qosLevel);
        subscriptions.add(topicFilter);
        SimLogger.logRun("Subscriber " + clientId + " subscribed to '" + topicFilter + "' with QoS " + qosLevel.getValue());
    }

    /**
     * Unsubscribes from a topic or pattern.
     *
     * @param topicFilter the topic or pattern to unsubscribe from
     */
    public void unsubscribe(String topicFilter) {
        if (!connected) {
            return;
        }

        broker.unsubscribe(clientId, topicFilter);
        subscriptions.remove(topicFilter);
        SimLogger.logRun("Subscriber " + clientId + " unsubscribed from '" + topicFilter + "'");
    }

    /**
     * Sets the message handler callback.
     *
     * @param handler the callback function to handle received messages
     */
    public void setMessageHandler(Consumer<MqttMessage> handler) {
        this.messageHandler = handler;
    }

    /**
     * Called when a message is received.
     */
    @Override
    public void onMessageReceived(MqttMessage message) {
        messagesReceived++;
        bytesReceived += message.getPayloadSize();

        SimLogger.logRun("Subscriber " + clientId + " received message " + message.getMessageId() +
                " on topic '" + message.getTopic() + "' (QoS " + message.getQosLevel().getValue() +
                ", latency: " + message.getEndToEndLatency() + "ms)");

        if (messageHandler != null) {
            messageHandler.accept(message);
        }
    }

    /**
     * Gets the repository.
     *
     * @return the repository
     */
    public Repository getRepository() {
        return repository;
    }

    /**
     * Gets the list of subscriptions.
     *
     * @return the subscriptions
     */
    public List<String> getSubscriptions() {
        return new ArrayList<>(subscriptions);
    }

    /**
     * Gets the number of messages received.
     *
     * @return the messages received count
     */
    public long getMessagesReceived() {
        return messagesReceived;
    }

    /**
     * Gets the number of bytes received.
     *
     * @return the bytes received count
     */
    public long getBytesReceived() {
        return bytesReceived;
    }
}
