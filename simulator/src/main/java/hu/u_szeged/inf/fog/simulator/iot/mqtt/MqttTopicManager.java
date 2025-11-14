package hu.u_szeged.inf.fog.simulator.iot.mqtt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages topic subscriptions and routing for MQTT messages.
 * Supports hierarchical topics and wildcard patterns (+ for single level, # for multi-level).
 */
public class MqttTopicManager {

    /**
     * Map of exact topic matches to their subscriber sets.
     * Key: topic string, Value: set of subscriber client IDs
     */
    private final Map<String, Set<String>> exactSubscriptions;

    /**
     * Map of wildcard patterns to their subscriber sets.
     * Key: pattern string, Value: set of subscriber client IDs
     */
    private final Map<String, Set<String>> wildcardSubscriptions;

    /**
     * Statistics for each topic.
     */
    private final Map<String, TopicStatistics> topicStats;

    /**
     * Constructs a new MQTT topic manager.
     */
    public MqttTopicManager() {
        this.exactSubscriptions = new ConcurrentHashMap<>();
        this.wildcardSubscriptions = new ConcurrentHashMap<>();
        this.topicStats = new ConcurrentHashMap<>();
    }

    /**
     * Subscribes a client to a topic or pattern.
     *
     * @param clientId   the ID of the subscribing client
     * @param topicFilter the topic or pattern to subscribe to
     */
    public void subscribe(String clientId, String topicFilter) {
        if (topicFilter.contains("+") || topicFilter.contains("#")) {
            wildcardSubscriptions.computeIfAbsent(topicFilter, k -> ConcurrentHashMap.newKeySet()).add(clientId);
        } else {
            exactSubscriptions.computeIfAbsent(topicFilter, k -> ConcurrentHashMap.newKeySet()).add(clientId);
        }
    }

    /**
     * Unsubscribes a client from a topic or pattern.
     *
     * @param clientId   the ID of the client to unsubscribe
     * @param topicFilter the topic or pattern to unsubscribe from
     */
    public void unsubscribe(String clientId, String topicFilter) {
        if (topicFilter.contains("+") || topicFilter.contains("#")) {
            Set<String> subscribers = wildcardSubscriptions.get(topicFilter);
            if (subscribers != null) {
                subscribers.remove(clientId);
                if (subscribers.isEmpty()) {
                    wildcardSubscriptions.remove(topicFilter);
                }
            }
        } else {
            Set<String> subscribers = exactSubscriptions.get(topicFilter);
            if (subscribers != null) {
                subscribers.remove(clientId);
                if (subscribers.isEmpty()) {
                    exactSubscriptions.remove(topicFilter);
                }
            }
        }
    }

    /**
     * Unsubscribes a client from all topics.
     *
     * @param clientId the ID of the client to unsubscribe
     */
    public void unsubscribeAll(String clientId) {
        exactSubscriptions.values().forEach(set -> set.remove(clientId));
        wildcardSubscriptions.values().forEach(set -> set.remove(clientId));
        
        // Clean up empty subscription sets
        exactSubscriptions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        wildcardSubscriptions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Gets all clients subscribed to a specific topic.
     * This includes both exact matches and wildcard pattern matches.
     *
     * @param topic the topic to check
     * @return a set of client IDs subscribed to this topic
     */
    public Set<String> getSubscribers(String topic) {
        Set<String> subscribers = new HashSet<>();

        // Add exact matches
        Set<String> exactMatch = exactSubscriptions.get(topic);
        if (exactMatch != null) {
            subscribers.addAll(exactMatch);
        }

        // Add wildcard matches
        for (Map.Entry<String, Set<String>> entry : wildcardSubscriptions.entrySet()) {
            if (matchesPattern(topic, entry.getKey())) {
                subscribers.addAll(entry.getValue());
            }
        }

        return subscribers;
    }

    /**
     * Checks if a topic matches a wildcard pattern.
     * Supports + (single level wildcard) and # (multi-level wildcard).
     *
     * @param topic   the topic to check
     * @param pattern the pattern to match against
     * @return true if the topic matches the pattern, false otherwise
     */
    private boolean matchesPattern(String topic, String pattern) {
        // Handle multi-level wildcard (#)
        if (pattern.endsWith("/#")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return topic.equals(prefix) || topic.startsWith(prefix + "/");
        }

        // Handle single-level wildcard (+)
        String[] topicLevels = topic.split("/");
        String[] patternLevels = pattern.split("/");

        if (topicLevels.length != patternLevels.length) {
            return false;
        }

        for (int i = 0; i < patternLevels.length; i++) {
            if (!patternLevels[i].equals("+") && !patternLevels[i].equals(topicLevels[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Records a message publication for statistics.
     *
     * @param topic       the topic
     * @param messageSize the size of the message
     */
    public void recordPublication(String topic, long messageSize) {
        topicStats.computeIfAbsent(topic, k -> new TopicStatistics(topic)).recordPublication(messageSize);
    }

    /**
     * Records a message delivery for statistics.
     *
     * @param topic the topic
     */
    public void recordDelivery(String topic) {
        TopicStatistics stats = topicStats.get(topic);
        if (stats != null) {
            stats.recordDelivery();
        }
    }

    /**
     * Gets statistics for a specific topic.
     *
     * @param topic the topic
     * @return the topic statistics, or null if not found
     */
    public TopicStatistics getTopicStatistics(String topic) {
        return topicStats.get(topic);
    }

    /**
     * Gets all topics with statistics.
     *
     * @return a map of topic names to their statistics
     */
    public Map<String, TopicStatistics> getAllTopicStatistics() {
        return new HashMap<>(topicStats);
    }

    /**
     * Gets the total number of subscriptions.
     *
     * @return the total subscription count
     */
    public int getTotalSubscriptionCount() {
        int count = 0;
        for (Set<String> subscribers : exactSubscriptions.values()) {
            count += subscribers.size();
        }
        for (Set<String> subscribers : wildcardSubscriptions.values()) {
            count += subscribers.size();
        }
        return count;
    }

    /**
     * Gets all topics that have subscribers.
     *
     * @return a set of topic names
     */
    public Set<String> getActiveTopics() {
        return new HashSet<>(exactSubscriptions.keySet());
    }

    /**
     * Gets all wildcard patterns that have subscribers.
     *
     * @return a set of pattern strings
     */
    public Set<String> getActivePatterns() {
        return new HashSet<>(wildcardSubscriptions.keySet());
    }

    /**
     * Statistics for a specific topic.
     */
    public static class TopicStatistics {
        private final String topic;
        private long messageCount;
        private long totalBytes;
        private long deliveryCount;

        public TopicStatistics(String topic) {
            this.topic = topic;
            this.messageCount = 0;
            this.totalBytes = 0;
            this.deliveryCount = 0;
        }

        void recordPublication(long messageSize) {
            messageCount++;
            totalBytes += messageSize;
        }

        void recordDelivery() {
            deliveryCount++;
        }

        public String getTopic() {
            return topic;
        }

        public long getMessageCount() {
            return messageCount;
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public long getDeliveryCount() {
            return deliveryCount;
        }

        public double getAverageMessageSize() {
            return messageCount > 0 ? (double) totalBytes / messageCount : 0;
        }

        @Override
        public String toString() {
            return "TopicStatistics{" +
                    "topic='" + topic + '\'' +
                    ", messageCount=" + messageCount +
                    ", totalBytes=" + totalBytes +
                    ", deliveryCount=" + deliveryCount +
                    ", avgSize=" + String.format("%.2f", getAverageMessageSize()) +
                    '}';
        }
    }
}
