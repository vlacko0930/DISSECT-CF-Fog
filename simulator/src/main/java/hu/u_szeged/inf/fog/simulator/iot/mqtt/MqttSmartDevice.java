package hu.u_szeged.inf.fog.simulator.iot.mqtt;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.u_szeged.inf.fog.simulator.iot.Device;
import hu.u_szeged.inf.fog.simulator.iot.SmartDevice;
import hu.u_szeged.inf.fog.simulator.iot.mobility.MobilityStrategy;
import hu.u_szeged.inf.fog.simulator.iot.strategy.DeviceStrategy;
import hu.u_szeged.inf.fog.simulator.util.SimLogger;

/**
 * An MQTT-enabled smart device that uses the MQTT protocol for communication.
 * Instead of directly transferring data to applications, it publishes messages to topics
 * that are routed through an MQTT broker.
 */
public class MqttSmartDevice extends SmartDevice {

    /**
     * The MQTT publisher for this device.
     */
    private MqttPublisher publisher;

    /**
     * The MQTT broker this device connects to.
     */
    private MqttBroker mqttBroker;

    /**
     * The topic this device publishes to.
     */
    private String publishTopic;

    /**
     * The QoS level for published messages.
     */
    private QoSLevel qosLevel;

    /**
     * Constructs a new MQTT-enabled smart device.
     *
     * @param startTime        the time when the device starts generating data
     * @param stopTime         the time when the device stops generating data
     * @param fileSize         the size of data produced by each sensor measurement
     * @param freq             the frequency of sensor measurements
     * @param mobilityStrategy the mobility strategy
     * @param deviceStrategy   the device strategy
     * @param localMachine     the physical machine representing the device
     * @param latency          the network latency
     * @param isPathLogged     whether to log the device path
     * @param mqttBroker       the MQTT broker to connect to
     * @param publishTopic     the topic to publish to
     * @param qosLevel         the QoS level for messages
     */
    public MqttSmartDevice(long startTime, long stopTime, long fileSize, long freq,
                           MobilityStrategy mobilityStrategy, DeviceStrategy deviceStrategy,
                           PhysicalMachine localMachine, int latency, boolean isPathLogged,
                           MqttBroker mqttBroker, String publishTopic, QoSLevel qosLevel) {
        super(startTime, stopTime, fileSize, freq, mobilityStrategy, deviceStrategy, localMachine, latency, isPathLogged);
        this.mqttBroker = mqttBroker;
        this.publishTopic = publishTopic;
        this.qosLevel = qosLevel;
        
        // Create MQTT publisher
        this.publisher = new MqttPublisher("device-" + Device.allDevices.size(), this.localMachine.localDisk);
    }

    /**
     * Connects the device to the MQTT broker.
     */
    public void connectToBroker() {
        if (!publisher.isConnected()) {
            publisher.connect(mqttBroker);
            SimLogger.logRun("MQTT Device " + publisher.getClientId() + " connected to broker at: " + Timed.getFireCount());
        }
    }

    /**
     * Disconnects the device from the MQTT broker.
     */
    public void disconnectFromBroker() {
        if (publisher.isConnected()) {
            publisher.disconnect();
            SimLogger.logRun("MQTT Device " + publisher.getClientId() + " disconnected from broker at: " + Timed.getFireCount());
        }
    }

    /**
     * The tick method for MQTT device operation.
     * Handles data generation, movement, and MQTT message publishing.
     */
    @Override
    public void tick(long fires) {
        try {
            // Generate data
            if (Timed.getFireCount() >= this.startTime && Timed.getFireCount() <= this.stopTime) {
                this.generatedData += this.fileSize;
                Device.totalGeneratedSize += this.fileSize;
                this.messageCount++;

                // Connect to broker if not connected
                if (!publisher.isConnected() && mqttBroker.isRunning()) {
                    connectToBroker();
                }

                // Publish message via MQTT
                if (publisher.isConnected()) {
                    publisher.publish(publishTopic, fileSize, qosLevel);
                    
                    SimLogger.logRun("MQTT Device " + publisher.getClientId() + " published " + fileSize +
                            " bytes to topic '" + publishTopic + "' at: " + Timed.getFireCount());
                }
            }

            // Handle mobility
            if (this.mobilityStrategy != null) {
                this.mobilityStrategy.move(this);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Stop condition
        if (Timed.getFireCount() > stopTime) {
            disconnectFromBroker();
            this.stopMeter();
        }
    }

    /**
     * Gets the MQTT publisher.
     *
     * @return the publisher
     */
    public MqttPublisher getPublisher() {
        return publisher;
    }

    /**
     * Gets the publish topic.
     *
     * @return the topic
     */
    public String getPublishTopic() {
        return publishTopic;
    }

    /**
     * Sets the publish topic.
     *
     * @param publishTopic the new topic
     */
    public void setPublishTopic(String publishTopic) {
        this.publishTopic = publishTopic;
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
     * Sets the QoS level.
     *
     * @param qosLevel the new QoS level
     */
    public void setQosLevel(QoSLevel qosLevel) {
        this.qosLevel = qosLevel;
    }
}
