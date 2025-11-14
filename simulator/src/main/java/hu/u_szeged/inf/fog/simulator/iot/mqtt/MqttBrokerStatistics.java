package hu.u_szeged.inf.fog.simulator.iot.mqtt;

/**
 * Statistics for an MQTT broker.
 * Tracks messages, bytes, clients, subscriptions, and performance metrics.
 */
public class MqttBrokerStatistics {

    private final String brokerId;
    private long messagesReceived;
    private long messagesDelivered;
    private long messagesDropped;
    private long bytesReceived;
    private long bytesDelivered;
    private int currentClientCount;
    private int maxClientCount;
    private int currentSubscriptionCount;
    private int maxSubscriptionCount;
    private long totalProcessingTime;
    private int processingCycles;

    /**
     * Constructs a new statistics object for a broker.
     *
     * @param brokerId the broker ID
     */
    public MqttBrokerStatistics(String brokerId) {
        this.brokerId = brokerId;
        this.messagesReceived = 0;
        this.messagesDelivered = 0;
        this.messagesDropped = 0;
        this.bytesReceived = 0;
        this.bytesDelivered = 0;
        this.currentClientCount = 0;
        this.maxClientCount = 0;
        this.currentSubscriptionCount = 0;
        this.maxSubscriptionCount = 0;
        this.totalProcessingTime = 0;
        this.processingCycles = 0;
    }

    public void incrementMessagesReceived() {
        messagesReceived++;
    }

    public void incrementMessagesDelivered() {
        messagesDelivered++;
    }

    public void incrementMessagesDropped() {
        messagesDropped++;
    }

    public void addBytesReceived(long bytes) {
        bytesReceived += bytes;
    }

    public void addBytesDelivered(long bytes) {
        bytesDelivered += bytes;
    }

    public void incrementClientCount() {
        currentClientCount++;
        if (currentClientCount > maxClientCount) {
            maxClientCount = currentClientCount;
        }
    }

    public void decrementClientCount() {
        currentClientCount--;
    }

    public void incrementSubscriptionCount() {
        currentSubscriptionCount++;
        if (currentSubscriptionCount > maxSubscriptionCount) {
            maxSubscriptionCount = currentSubscriptionCount;
        }
    }

    public void decrementSubscriptionCount() {
        currentSubscriptionCount--;
    }

    public void recordProcessingTime(long time) {
        totalProcessingTime += time;
        processingCycles++;
    }

    // Getters
    public String getBrokerId() {
        return brokerId;
    }

    public long getMessagesReceived() {
        return messagesReceived;
    }

    public long getMessagesDelivered() {
        return messagesDelivered;
    }

    public long getMessagesDropped() {
        return messagesDropped;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public long getBytesDelivered() {
        return bytesDelivered;
    }

    public int getCurrentClientCount() {
        return currentClientCount;
    }

    public int getMaxClientCount() {
        return maxClientCount;
    }

    public int getCurrentSubscriptionCount() {
        return currentSubscriptionCount;
    }

    public int getMaxSubscriptionCount() {
        return maxSubscriptionCount;
    }

    public double getAverageProcessingTime() {
        return processingCycles > 0 ? (double) totalProcessingTime / processingCycles : 0;
    }

    public double getDeliveryRate() {
        return messagesReceived > 0 ? (double) messagesDelivered / messagesReceived : 0;
    }

    public double getDropRate() {
        return messagesReceived > 0 ? (double) messagesDropped / messagesReceived : 0;
    }

    @Override
    public String toString() {
        return "MqttBrokerStatistics{" +
                "broker='" + brokerId + '\'' +
                ", received=" + messagesReceived +
                ", delivered=" + messagesDelivered +
                ", dropped=" + messagesDropped +
                ", bytesRx=" + bytesReceived +
                ", bytesTx=" + bytesDelivered +
                ", clients=" + currentClientCount + "/" + maxClientCount +
                ", subscriptions=" + currentSubscriptionCount + "/" + maxSubscriptionCount +
                ", deliveryRate=" + String.format("%.2f%%", getDeliveryRate() * 100) +
                ", dropRate=" + String.format("%.2f%%", getDropRate() * 100) +
                '}';
    }
}
