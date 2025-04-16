/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.connection;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;

/**
 * Shell representation of class for student implementation.
 * 
 */
public class MqttClientConnector implements IPubSubClient, MqttCallbackExtended
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(MqttClientConnector.class.getName());
	
	//private variables

	private boolean useAsyncClient = false;

	private MqttClient           mqttClient = null;
	private MqttConnectOptions   connOpts = null;
	private MemoryPersistence    persistence = null;
	private IDataMessageListener dataMsgListener = null;

	private String  clientID = null;
	private String  brokerAddr = null;
	private String  host = ConfigConst.DEFAULT_HOST;
	private String  protocol = ConfigConst.DEFAULT_MQTT_PROTOCOL;
	private int     port = ConfigConst.DEFAULT_MQTT_PORT;
	private int     brokerKeepAlive = ConfigConst.DEFAULT_KEEP_ALIVE;
	
	
	// constructors
	
	/**
	 * Default.
	 * 
	 */
	public MqttClientConnector()
	{
		super();

		ConfigUtil configUtil = ConfigUtil.getInstance();

		this.host =
				configUtil.getProperty(
						ConfigConst.MQTT_GATEWAY_SERVICE,
						ConfigConst.HOST_KEY,
						ConfigConst.DEFAULT_HOST);

		this.port =
				configUtil.getInteger(
						ConfigConst.MQTT_GATEWAY_SERVICE,
						ConfigConst.PORT_KEY,
						ConfigConst.DEFAULT_MQTT_PORT);

		this.brokerKeepAlive =
				configUtil.getInteger(
						ConfigConst.MQTT_GATEWAY_SERVICE,
						ConfigConst.KEEP_ALIVE_KEY,
						ConfigConst.DEFAULT_KEEP_ALIVE);

		// This next config file boolean property is optional; it can be
		// set within the [Mqtt.GatewayService] and [Cloud.GatewayService]
		// sections of PiotConfig.props. You can use it to create a logical
		// flow within this class to determine whether to use MqttClient
		// or MqttAsyncClient, or simply choose one of the two classes based
		// on your usage needs. Generally speaking, MqttAsyncClient will
		// be necessary when running the GDA as an application, as it will
		// need to handle incoming and outgoing messages using MQTT
		// simultaneously. For GDA-only testing using the test cases
		// specified in this lab module and others, it's generally best -
		// and likely required - to use MqttClient.
		//
		// IMPORTANT: If you're using an older version of ConfigConst.java,
		// you'll need to add the following line of code to ConfigConst.java:
		// public static final String USE_ASYNC_CLIENT_KEY = "useAsyncClient";
		this.useAsyncClient =
				configUtil.getBoolean(
						ConfigConst.MQTT_GATEWAY_SERVICE,
						ConfigConst.USE_ASYNC_CLIENT_KEY);

		// NOTE: paho Java client requires a client ID - for now, you
		// can use the generated client ID; for later exercises, you
		// should define your own and load it from the config file
		this.clientID = MqttClient.generateClientId();

		// these are specific to the MQTT connection which will be used during connect
		this.persistence = new MemoryPersistence();
		this.connOpts = new MqttConnectOptions();

		this.connOpts.setKeepAliveInterval(this.brokerKeepAlive);

		// NOTE: If using a random clientID for each new connection,
		// clean session should be 'true'; see MQTT spec for details
		this.connOpts.setCleanSession(false);

		// NOTE: Auto-reconnect can be a useful connection recovery feature
		this.connOpts.setAutomaticReconnect(true);

		// NOTE: URL does not have a protocol handler for "tcp",
		// so we need to construct the URL manually
		this.brokerAddr = this.protocol + "://" + this.host + ":" + this.port;

	}
	
	
	// public methods
	
	@Override
	public boolean connectClient()
	{
		try {
			if (this.mqttClient == null) {
				this.mqttClient = new MqttClient(
						this.brokerAddr,
						this.clientID,
						this.persistence);

				this.mqttClient.setCallback(this);
			}

			if (! this.mqttClient.isConnected()) {
				_Logger.info("MQTT client is connecting to broker: " + this.brokerAddr);
				this.mqttClient.connect(this.connOpts);
				return true;
			} else {
				_Logger.warning("MQTT client already connected to broker: " + this.brokerAddr);
			}
		} catch (MqttException e) {
			_Logger.log(Level.SEVERE, "BE CAREFUL, failed to connect MQTT client to broker.", e);
		}

		return false;
	}

	@Override
	public boolean disconnectClient()
	{
		try {
			if (this.mqttClient != null) {
				if (this.mqttClient.isConnected()) {
					_Logger.info("Disconnecting MQTT client from broker: " + this.brokerAddr);
					this.mqttClient.disconnect();
					return true;
				} else {
					_Logger.warning("MQTT client is not connected to broker: " + this.brokerAddr);
				}
			}
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Failed to disconnect MQTT client from broker: " + this.brokerAddr, e);
		}

		return false;
	}

	//Verifies whether the MQTT client is currently connected
	public boolean isConnected()
	{
		return false;
	}
	
	@Override
	public boolean publishMessage(ResourceNameEnum topicName, String msg, int qos)
	{
		// Validate that the topic is not null
		if (topicName == null) {
			_Logger.warning("Error!: Resource is null. Unable to publish message on broker: " + this.brokerAddr);
			return false;
		}

		// Check that the message is not null or empty
		if (msg == null || msg.trim().isEmpty()) {
			_Logger.warning("Error!: Message is null or empty. Publishing aborted on broker: " + this.brokerAddr);
			return false;
		}

		// Validate QoS value
		if (qos < 0 || qos > 2) {
			_Logger.warning("Warning!: Invalid QoS value (" + qos + ") detected. Using default QoS: " + ConfigConst.DEFAULT_QOS);
			qos = ConfigConst.DEFAULT_QOS;
		}

		try {
			//In many MQTT modules, it is necessary to convert the message into a byte array for proper transmission.
			byte[] payload = msg.getBytes();
			MqttMessage mqttMsg = new MqttMessage(payload);
			mqttMsg.setQos(qos);

			// Publish the message on the given topic
			this.mqttClient.publish(topicName.getResourceName(), mqttMsg);
			return true;
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Exception while publishing message on topic: " + topicName + ". Details: ", e);
		}

		return false;
	}

	@Override
	public boolean subscribeToTopic(ResourceNameEnum topicName, int qos)
	{
		// Validate that the topic is not null
		if (topicName == null) {
			_Logger.warning("Error!: Resource is null. Unable to subscribe to topic");
			return false;
		}

		// Validate QoS value
		if (qos < 0 || qos > 2) {
			_Logger.warning("Warning!: Invalid QoS value (" + qos + ") detected. Using default QoS: " + ConfigConst.DEFAULT_QOS);
			qos = ConfigConst.DEFAULT_QOS;
		}

		try {
			this.mqttClient.subscribe(topicName.getResourceName(), qos);
			_Logger.info("Congrats!Client successfully subscribed to topic: " + topicName.getResourceName());
			return true;
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Error! Failed to subscribe to topic: " + topicName, e);
		}

		return false;
	}

	@Override
	public boolean unsubscribeFromTopic(ResourceNameEnum topicName)
	{
		// Validate that the topic is not null
		if (topicName == null) {
			_Logger.warning("Error!: Resource is null. Unable to unsubscribe from topic");
			return false;
		}

		try {
			this.mqttClient.unsubscribe(topicName.getResourceName());
			_Logger.info("Congrats! Successfully unsubscribed from topic: " + topicName.getResourceName());
			return true;
		} catch (Exception e) {
			_Logger.log(Level.SEVERE, "Error! Failed to unsubscribe from topic: " + topicName, e);
		}

		return false;
	}

	@Override
	public boolean setConnectionListener(IConnectionListener listener)
	{
		return false;
	}
	
	@Override
	public boolean setDataMessageListener(IDataMessageListener listener)
	{
		if (listener != null) {
			this.dataMsgListener = listener;
			return true;
		}

		return false;
	}
	
	// callbacks
	@Override
	public void connectComplete(boolean reconnect, String serverURI)
	{
		_Logger.info("MQTT connection result of automatic reconnect is = " + reconnect + "). Server: " + serverURI);
	}

	@Override
	public void connectionLost(Throwable t)
	{
		_Logger.log(Level.WARNING, "Lost connection to MQTT broker: " + this.brokerAddr, t);
	}
	
	@Override
	public void deliveryComplete(IMqttDeliveryToken token)
	{
		_Logger.info("Delivered MQTT message with the following ID: " + token.getMessageId());
	}
	
	@Override
	public void messageArrived(String topic, MqttMessage msg) throws Exception
	{
		_Logger.info("MQTT message arrived with the following topic: '" + topic + "'");
	}

	
	// private methods
	
	/**
	 * Called by the constructor to set the MQTT client parameters to be used for the connection.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 * the MQTT client configuration parameters.
	 */
	private void initClientParameters(String configSectionName)
	{
		// TODO: implement this
	}
	
	/**
	 * Called by {@link #initClientParameters(String)} to load credentials.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 * the MQTT client configuration parameters.
	 */
	private void initCredentialConnectionParameters(String configSectionName)
	{
		// TODO: implement this
	}
	
	/**
	 * Called by {@link #initClientParameters(String)} to enable encryption.
	 * 
	 * @param configSectionName The name of the configuration section to use for
	 * the MQTT client configuration parameters.
	 */
	private void initSecureConnectionParameters(String configSectionName)
	{
		// TODO: implement this
	}
}
