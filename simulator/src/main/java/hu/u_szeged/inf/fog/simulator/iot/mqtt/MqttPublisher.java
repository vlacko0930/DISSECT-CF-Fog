package hu.u_szeged.inf.fog.simulator.iot.mqtt;

import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

/**
 * Represents an MQTT publisher client.
 * Publishers send messages to the broker on specific topics.
 */
public class MqttPublisher extends MqttClient {

    /**
     * The repository from which messages are sent.
     */
    private final Repository repository;

    /**
     * Statistics for this publisher.
     */
    private long messagesSent;
    private long bytesSent;

    /**
     * Constructs a new MQTT publisher.
     *
     * @param clientId   the unique client ID
     * @param repository the repository for network operations
     */
    public MqttPublisher(String clientId, Repository repository) {
        super(clientId);
        this.repository = repository;
        this.messagesSent = 0;
        this.bytesSent = 0;
    }

    /**
     * Publishes a message to a topic.
     *
     * @param topic       the topic to publish to
     * @param payloadSize the size of the message payload in bytes
     * @param qosLevel    the Quality of Service level
     */
    public void publish(String topic, long payloadSize, QoSLevel qosLevel) {
        if (!connected) {
            SimLogger.logRun("Error: Publisher " + clientId + " is not connected");
            return;
        }

        MqttMessage message = new MqttMessage(topic, payloadSize, qosLevel, clientId);
        broker.publish(message, repository);
        
        messagesSent++;
        bytesSent += payloadSize;

        SimLogger.logRun("Publisher " + clientId + " sent message " + message.getMessageId() +
                " to topic '" + topic + "' with QoS " + qosLevel.getValue());
    }

    /**
     * Publishers typically don't receive messages, but this is here for completeness.
     */
    @Override
    public void onMessageReceived(MqttMessage message) {
        SimLogger.logRun("Warning: Publisher " + clientId + " received unexpected message: " + message);
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
     * Gets the number of messages sent.
     *
     * @return the messages sent count
     */
    public long getMessagesSent() {
        return messagesSent;
    }

    /**
     * Gets the number of bytes sent.
     *
     * @return the bytes sent count
     */
    public long getBytesSent() {
        return bytesSent;
    }
}
