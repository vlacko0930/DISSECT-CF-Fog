package hu.u_szeged.inf.fog.simulator.iot.mqtt;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.u_szeged.inf.fog.simulator.application.Application;
import hu.u_szeged.inf.fog.simulator.application.strategy.ApplicationStrategy;
import hu.u_szeged.inf.fog.simulator.provider.Instance;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * An MQTT-enabled application that subscribes to topics and processes messages
 * received from the broker instead of directly from devices.
 */
public class MqttApplication extends Application {

    /**
     * The MQTT subscriber for this application.
     */
    private MqttSubscriber subscriber;

    /**
     * The MQTT broker this application connects to.
     */
    private MqttBroker mqttBroker;

    /**
     * List of topics this application subscribes to.
     */
    private List<String> subscriptionTopics;

    /**
     * The QoS level for subscriptions.
     */
    private QoSLevel subscriptionQoS;

    /**
     * Counter for messages received via MQTT.
     */
    private long mqttMessagesReceived;

    /**
     * Constructs a new MQTT-enabled application.
     *
     * @param name                the name of the application
     * @param freq                the frequency of periodic task execution
     * @param tasksize            the maximum size of a task
     * @param instructions        the number of instructions per task
     * @param serviceable         indicates whether the application can receive data from devices
     * @param applicationStrategy the strategy for finding other applications
     * @param instance            the type of VMs used by this application
     * @param mqttBroker          the MQTT broker to connect to
     * @param subscriptionQoS     the QoS level for subscriptions
     */
    public MqttApplication(String name, long freq, long tasksize, double instructions,
                           boolean serviceable, ApplicationStrategy applicationStrategy,
                           Instance instance, MqttBroker mqttBroker, QoSLevel subscriptionQoS) {
        super(name, freq, tasksize, instructions, serviceable, applicationStrategy, instance);
        this.mqttBroker = mqttBroker;
        this.subscriptionTopics = new ArrayList<>();
        this.subscriptionQoS = subscriptionQoS;
        this.mqttMessagesReceived = 0;
    }

    /**
     * Connects the application to the MQTT broker and subscribes to topics.
     */
    public void connectToBroker() {
        if (this.computingAppliance == null) {
            SimLogger.logRun("Error: Application " + name + " has no computing appliance set");
            return;
        }

        // Create subscriber if not exists
        if (subscriber == null) {
            subscriber = new MqttSubscriber("app-" + name, this.computingAppliance.iaas.repositories.get(0));
            
            // Set message handler
            subscriber.setMessageHandler(message -> {
                onMqttMessageReceived(message);
            });
        }

        // Connect to broker
        if (!subscriber.isConnected()) {
            subscriber.connect(mqttBroker);
            SimLogger.logRun("MQTT Application " + name + " connected to broker at: " + Timed.getFireCount());

            // Subscribe to all registered topics
            for (String topic : subscriptionTopics) {
                subscriber.subscribe(topic, subscriptionQoS);
            }
        }
    }

    /**
     * Disconnects the application from the MQTT broker.
     */
    public void disconnectFromBroker() {
        if (subscriber != null && subscriber.isConnected()) {
            subscriber.disconnect();
            SimLogger.logRun("MQTT Application " + name + " disconnected from broker at: " + Timed.getFireCount());
        }
    }

    /**
     * Adds a topic subscription.
     *
     * @param topic the topic to subscribe to
     */
    public void addSubscription(String topic) {
        if (!subscriptionTopics.contains(topic)) {
            subscriptionTopics.add(topic);
            
            // If already connected, subscribe immediately
            if (subscriber != null && subscriber.isConnected()) {
                subscriber.subscribe(topic, subscriptionQoS);
            }
        }
    }

    /**
     * Removes a topic subscription.
     *
     * @param topic the topic to unsubscribe from
     */
    public void removeSubscription(String topic) {
        subscriptionTopics.remove(topic);
        
        // If connected, unsubscribe immediately
        if (subscriber != null && subscriber.isConnected()) {
            subscriber.unsubscribe(topic);
        }
    }

    /**
     * Called when an MQTT message is received.
     * This increases the received data counter so the application can process it.
     *
     * @param message the received MQTT message
     */
    private void onMqttMessageReceived(MqttMessage message) {
        mqttMessagesReceived++;
        this.receivedData += message.getPayloadSize();
        
        SimLogger.logRun("MQTT Application " + name + " received message " + message.getMessageId() +
                " on topic '" + message.getTopic() + "' (" + message.getPayloadSize() + " bytes, " +
                "QoS " + message.getQosLevel().getValue() + ", latency: " + message.getEndToEndLatency() + "ms)");
    }

    /**
     * Overrides the subscribe method to also connect to MQTT broker.
     */
    @Override
    public void subscribeApplication() {
        super.subscribeApplication();
        
        // Connect to MQTT broker if not connected
        if (mqttBroker.isRunning() && (subscriber == null || !subscriber.isConnected())) {
            connectToBroker();
        }
    }

    /**
     * Gets the MQTT subscriber.
     *
     * @return the subscriber
     */
    public MqttSubscriber getSubscriber() {
        return subscriber;
    }

    /**
     * Gets the list of subscription topics.
     *
     * @return the subscription topics
     */
    public List<String> getSubscriptionTopics() {
        return new ArrayList<>(subscriptionTopics);
    }

    /**
     * Gets the subscription QoS level.
     *
     * @return the QoS level
     */
    public QoSLevel getSubscriptionQoS() {
        return subscriptionQoS;
    }

    /**
     * Sets the subscription QoS level.
     *
     * @param subscriptionQoS the new QoS level
     */
    public void setSubscriptionQoS(QoSLevel subscriptionQoS) {
        this.subscriptionQoS = subscriptionQoS;
    }

    /**
     * Gets the number of MQTT messages received.
     *
     * @return the MQTT messages received count
     */
    public long getMqttMessagesReceived() {
        return mqttMessagesReceived;
    }
}
