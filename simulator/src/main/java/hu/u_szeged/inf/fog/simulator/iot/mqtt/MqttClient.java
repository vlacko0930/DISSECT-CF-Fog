package hu.u_szeged.inf.fog.simulator.iot.mqtt;

/**
 * Abstract base class for MQTT clients.
 * Can be extended to create publishers and subscribers.
 */
public abstract class MqttClient {

    /**
     * Unique identifier for this client.
     */
    protected final String clientId;

    /**
     * The broker this client is connected to.
     */
    protected MqttBroker broker;

    /**
     * Indicates whether the client is currently connected.
     */
    protected boolean connected;

    /**
     * Constructs a new MQTT client.
     *
     * @param clientId the unique client ID
     */
    public MqttClient(String clientId) {
        this.clientId = clientId;
        this.connected = false;
    }

    /**
     * Connects this client to a broker.
     *
     * @param broker the broker to connect to
     */
    public void connect(MqttBroker broker) {
        if (this.connected) {
            throw new IllegalStateException("Client " + clientId + " is already connected");
        }
        this.broker = broker;
        this.connected = true;
        broker.registerClient(this);
    }

    /**
     * Disconnects this client from its broker.
     */
    public void disconnect() {
        if (!this.connected) {
            return;
        }
        if (broker != null) {
            broker.unregisterClient(this);
        }
        this.connected = false;
        this.broker = null;
    }

    /**
     * Gets the client ID.
     *
     * @return the client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Checks if the client is connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Gets the broker this client is connected to.
     *
     * @return the broker, or null if not connected
     */
    public MqttBroker getBroker() {
        return broker;
    }

    /**
     * Called when a message is received by this client.
     * Subclasses should implement this method to handle incoming messages.
     *
     * @param message the received message
     */
    public abstract void onMessageReceived(MqttMessage message);
}
