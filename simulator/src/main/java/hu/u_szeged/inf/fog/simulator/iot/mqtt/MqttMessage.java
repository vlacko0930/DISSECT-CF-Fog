package hu.u_szeged.inf.fog.simulator.iot.mqtt;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;

/**
 * Represents an MQTT message in the simulator.
 * Contains the topic, payload, QoS level, and metadata for tracking.
 */
public class MqttMessage {

    /**
     * Global counter for generating unique message IDs.
     */
    private static long messageIdCounter = 0;

    /**
     * Unique identifier for this message.
     */
    private final long messageId;

    /**
     * The topic to which this message belongs.
     */
    private final String topic;

    /**
     * The size of the message payload in bytes.
     */
    private final long payloadSize;

    /**
     * The Quality of Service level for this message.
     */
    private final QoSLevel qosLevel;

    /**
     * The client ID that published this message.
     */
    private final String publisherId;

    /**
     * Timestamp when the message was created (published).
     */
    private final long publishTime;

    /**
     * Indicates if this message has been acknowledged (for QoS 1 and 2).
     */
    private boolean acknowledged;

    /**
     * Indicates if this message has been received (for QoS 2).
     */
    private boolean received;

    /**
     * Indicates if this message has been released (for QoS 2).
     */
    private boolean released;

    /**
     * Indicates if this message has been completed (for QoS 2).
     */
    private boolean completed;

    /**
     * Number of delivery attempts for this message.
     */
    private int deliveryAttempts;

    /**
     * Timestamp when the message was delivered to broker.
     */
    private long brokerReceiveTime;

    /**
     * Timestamp when the message was delivered to subscriber.
     */
    private long subscriberReceiveTime;

    /**
     * Constructs a new MQTT message.
     *
     * @param topic        the topic for this message
     * @param payloadSize  the size of the payload in bytes
     * @param qosLevel     the QoS level
     * @param publisherId  the ID of the client publishing this message
     */
    public MqttMessage(String topic, long payloadSize, QoSLevel qosLevel, String publisherId) {
        this.messageId = ++messageIdCounter;
        this.topic = topic;
        this.payloadSize = payloadSize;
        this.qosLevel = qosLevel;
        this.publisherId = publisherId;
        this.publishTime = Timed.getFireCount();
        this.acknowledged = false;
        this.received = false;
        this.released = false;
        this.completed = false;
        this.deliveryAttempts = 0;
        this.brokerReceiveTime = -1;
        this.subscriberReceiveTime = -1;
    }

    /**
     * Gets the unique message ID.
     *
     * @return the message ID
     */
    public long getMessageId() {
        return messageId;
    }

    /**
     * Gets the topic.
     *
     * @return the topic string
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Gets the payload size.
     *
     * @return the payload size in bytes
     */
    public long getPayloadSize() {
        return payloadSize;
    }

    /**
     * Gets the QoS level.
     *
     * @return the QoS level
     */
    public QoSLevel getQosLevel() {
        return qosLevel;
    }

    /**
     * Gets the publisher ID.
     *
     * @return the publisher ID
     */
    public String getPublisherId() {
        return publisherId;
    }

    /**
     * Gets the publish timestamp.
     *
     * @return the publish time
     */
    public long getPublishTime() {
        return publishTime;
    }

    /**
     * Checks if the message has been acknowledged.
     *
     * @return true if acknowledged, false otherwise
     */
    public boolean isAcknowledged() {
        return acknowledged;
    }

    /**
     * Sets the acknowledgment status.
     *
     * @param acknowledged the acknowledgment status
     */
    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    /**
     * Checks if the message has been received (QoS 2).
     *
     * @return true if received, false otherwise
     */
    public boolean isReceived() {
        return received;
    }

    /**
     * Sets the received status (QoS 2).
     *
     * @param received the received status
     */
    public void setReceived(boolean received) {
        this.received = received;
    }

    /**
     * Checks if the message has been released (QoS 2).
     *
     * @return true if released, false otherwise
     */
    public boolean isReleased() {
        return released;
    }

    /**
     * Sets the released status (QoS 2).
     *
     * @param released the released status
     */
    public void setReleased(boolean released) {
        this.released = released;
    }

    /**
     * Checks if the message has been completed (QoS 2).
     *
     * @return true if completed, false otherwise
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Sets the completed status (QoS 2).
     *
     * @param completed the completed status
     */
    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    /**
     * Gets the number of delivery attempts.
     *
     * @return the delivery attempts count
     */
    public int getDeliveryAttempts() {
        return deliveryAttempts;
    }

    /**
     * Increments the delivery attempts counter.
     */
    public void incrementDeliveryAttempts() {
        this.deliveryAttempts++;
    }

    /**
     * Gets the broker receive timestamp.
     *
     * @return the broker receive time, or -1 if not yet received
     */
    public long getBrokerReceiveTime() {
        return brokerReceiveTime;
    }

    /**
     * Sets the broker receive timestamp.
     *
     * @param brokerReceiveTime the broker receive time
     */
    public void setBrokerReceiveTime(long brokerReceiveTime) {
        this.brokerReceiveTime = brokerReceiveTime;
    }

    /**
     * Gets the subscriber receive timestamp.
     *
     * @return the subscriber receive time, or -1 if not yet received
     */
    public long getSubscriberReceiveTime() {
        return subscriberReceiveTime;
    }

    /**
     * Sets the subscriber receive timestamp.
     *
     * @param subscriberReceiveTime the subscriber receive time
     */
    public void setSubscriberReceiveTime(long subscriberReceiveTime) {
        this.subscriberReceiveTime = subscriberReceiveTime;
    }

    /**
     * Calculates the end-to-end latency from publish to subscriber delivery.
     *
     * @return the end-to-end latency in milliseconds, or -1 if not yet delivered
     */
    public long getEndToEndLatency() {
        if (subscriberReceiveTime == -1) {
            return -1;
        }
        return subscriberReceiveTime - publishTime;
    }

    /**
     * Calculates the broker processing time.
     *
     * @return the broker processing time, or -1 if not applicable
     */
    public long getBrokerProcessingTime() {
        if (brokerReceiveTime == -1 || subscriberReceiveTime == -1) {
            return -1;
        }
        return subscriberReceiveTime - brokerReceiveTime;
    }

    @Override
    public String toString() {
        return "MqttMessage{" +
                "id=" + messageId +
                ", topic='" + topic + '\'' +
                ", payloadSize=" + payloadSize +
                ", qos=" + qosLevel +
                ", publisher='" + publisherId + '\'' +
                ", publishTime=" + publishTime +
                ", attempts=" + deliveryAttempts +
                '}';
    }
}
