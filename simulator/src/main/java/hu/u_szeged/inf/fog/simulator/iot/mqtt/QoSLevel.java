package hu.u_szeged.inf.fog.simulator.iot.mqtt;

/**
 * Enumeration representing the MQTT Quality of Service (QoS) levels.
 * QoS levels define the guarantee of delivery for messages in MQTT protocol.
 */
public enum QoSLevel {
    /**
     * QoS 0 - At most once delivery.
     * The message is delivered according to the best efforts of the underlying TCP/IP network.
     * Message loss can occur. No acknowledgment is required.
     */
    AT_MOST_ONCE(0),

    /**
     * QoS 1 - At least once delivery.
     * The message is guaranteed to arrive at least once, but duplicates may occur.
     * Acknowledgment (PUBACK) is required from the receiver.
     */
    AT_LEAST_ONCE(1),

    /**
     * QoS 2 - Exactly once delivery.
     * The message is guaranteed to arrive exactly once.
     * This is the safest but slowest QoS level with a four-step handshake
     * (PUBLISH, PUBREC, PUBREL, PUBCOMP).
     */
    EXACTLY_ONCE(2);

    private final int value;

    QoSLevel(int value) {
        this.value = value;
    }

    /**
     * Gets the numeric value of the QoS level.
     *
     * @return the numeric value (0, 1, or 2)
     */
    public int getValue() {
        return value;
    }

    /**
     * Gets a QoS level from its numeric value.
     *
     * @param value the numeric value (0, 1, or 2)
     * @return the corresponding QoS level
     * @throws IllegalArgumentException if the value is not valid
     */
    public static QoSLevel fromValue(int value) {
        for (QoSLevel qos : values()) {
            if (qos.value == value) {
                return qos;
            }
        }
        throw new IllegalArgumentException("Invalid QoS value: " + value);
    }
}
