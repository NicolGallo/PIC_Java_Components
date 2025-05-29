/**
 * This class is part of the Programming the Internet of Things project.
 * 
 * It is provided as a simple shell to guide the student and assist with
 * implementation for the Programming the Internet of Things exercises,
 * and designed to be modified by the student as needed.
 */ 

package programmingtheiot.gda.app;

import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;
import java.util.Arrays;
import java.util.List;
import java.time.OffsetDateTime;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IActuatorDataListener;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;

import programmingtheiot.data.DataUtil;
import programmingtheiot.data.SensorData;
import programmingtheiot.data.ActuatorData;
import programmingtheiot.data.SystemPerformanceData;
import programmingtheiot.data.SystemStateData;
import programmingtheiot.data.BaseIotData;

import programmingtheiot.gda.connection.CoapServerGateway;
import programmingtheiot.gda.connection.IPersistenceClient;
import programmingtheiot.gda.connection.IPubSubClient;
import programmingtheiot.gda.connection.IRequestResponseClient;
import programmingtheiot.gda.connection.MqttClientConnector;

import programmingtheiot.gda.system.SystemPerformanceManager;

/**
 * Shell representation of class for student implementation.
 *
 */
public class DeviceDataManager implements IDataMessageListener
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(DeviceDataManager.class.getName());

	// Necessary startManager method to check each resource and take the appropiate action demanded
	private static final List<ResourceNameEnum> topics = Arrays.asList(ResourceNameEnum.GDA_MGMT_STATUS_MSG_RESOURCE,
			ResourceNameEnum.CDA_ACTUATOR_RESPONSE_RESOURCE, ResourceNameEnum.CDA_SENSOR_MSG_RESOURCE,
			ResourceNameEnum.CDA_SYSTEM_PERF_MSG_RESOURCE);

	// private var's
	
	private boolean enableMqttClient = true;
	private boolean enableCoapServer = false;
	private boolean enableCloudClient = false;
	private boolean enableSmtpClient = false;
	private boolean enablePersistenceClient = false;
	private boolean enableSystemPerf = false;
	
	private IActuatorDataListener actuatorDataListener = null;
	private MqttClientConnector mqttClient = null;
	private IPubSubClient cloudClient = null;
	private IPersistenceClient persistenceClient = null;
	private IRequestResponseClient smtpClient = null;
	private CoapServerGateway coapServer = null;
	private SystemPerformanceManager sysPerfMgr = null;
	private IActuatorDataListener dataMsgListener = null;

	private ActuatorData latestHumidifierActuatorData = null;
	private ActuatorData latestHumidifierActuatorResponse = null;
	private SensorData latestHumiditySensorData = null;
	private OffsetDateTime latestHumiditySensorTimeStamp = null;

	private boolean handleHumidityChangeOnDevice = false; // this is optional
	private int lastKnownHumidifierCommand = ConfigConst.OFF_COMMAND;

	private long humidityMaxTimePastThreshold = 300;// seconds
	private float nominalHumiditySetting = 40.0f;
	private float triggerHumidifierFloor = 30.0f;
	private float triggerHumidifierCeiling = 50.0f;


	// constructors
	
	public DeviceDataManager()
	{
		super();

		ConfigUtil configUtil = ConfigUtil.getInstance();

		this.enableMqttClient = configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE,
				ConfigConst.ENABLE_MQTT_CLIENT_KEY);

		this.enableCoapServer = configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE,
				ConfigConst.ENABLE_COAP_SERVER_KEY);

		this.enableCloudClient = configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE,
				ConfigConst.ENABLE_CLOUD_CLIENT_KEY);

		this.enablePersistenceClient = configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE,
				ConfigConst.ENABLE_PERSISTENCE_CLIENT_KEY);

		this.handleHumidityChangeOnDevice = configUtil.getBoolean(
				ConfigConst.GATEWAY_DEVICE,
				ConfigConst.HANDLE_HUMIDITY_CHANGE_ON_DEVICE_KEY);

		this.humidityMaxTimePastThreshold = configUtil.getInteger(
				ConfigConst.GATEWAY_DEVICE,
				ConfigConst.HUMIDITY_MAX_TIME_PAST_THRESHOLD_KEY);

		this.nominalHumiditySetting = configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE,
				ConfigConst.NOMINAL_HUMIDITY_SETTING_KEY);

		this.triggerHumidifierFloor = configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE,
				ConfigConst.TRIGGER_HUMIDIFIER_FLOOR_KEY);

		this.triggerHumidifierCeiling = configUtil.getFloat(
				ConfigConst.GATEWAY_DEVICE,
				ConfigConst.TRIGGER_HUMIDIFIER_CEILING_KEY);

		initManager();
		initConnections();
	}
	
	public DeviceDataManager(
		boolean enableMqttClient,
		boolean enableCoapClient,
		boolean enableCloudClient,
		boolean enableSmtpClient,
		boolean enablePersistenceClient)

	{

		super();

		initManager();
		initConnections();

	}
	
	
	// public methods
	
	@Override
	public boolean handleActuatorCommandResponse(ResourceNameEnum resourceName, ActuatorData data) {

		if (data != null) {
			_Logger.info("Handling actuator response: " + data.getName());

			// this next call is optional for now
			//this.handleIncomingDataAnalysis(resourceName, data);

			if (data.hasError()) {
				_Logger.warning("Error flag set for ActuatorData instance.");
			}

			return true;
		} else {
			return false;
		}

	}


	@Override
	public boolean handleActuatorCommandRequest(ResourceNameEnum resourceName, ActuatorData data)
	{
		return false;
	}



	@Override
	public boolean handleIncomingMessage(ResourceNameEnum resourceName, String msg) {

		if (msg != null) {
			_Logger.info("Handling incoming generic message: " + msg);

			return true;
		} else {
			return false;
		}

	}

	@Override
	public boolean handleSensorMessage(ResourceNameEnum resourceName, SensorData data) {
		if (data != null) {
			_Logger.fine("Handling sensor message: " +data.getName());

			if (data.hasError()) {
				_Logger.warning("ERROR FLAG set for SensorData instance.");
			}

			String jsonData = DataUtil.getInstance().sensorDataToJson(data);

			_Logger.fine("JSON [SensorData] -> " +jsonData);

			int qos = ConfigUtil.getInstance().getInteger(
					ConfigConst.MQTT_GATEWAY_SERVICE,
					ConfigConst.DEFAULT_QOS_KEY,
					ConfigConst.DEFAULT_QOS);

			if (this.enablePersistenceClient && this.persistenceClient != null) {

				this.persistenceClient.storeData(resourceName.getResourceName(),qos,data);

			}

			this.handleIncomingDataAnalysis(resourceName,data);

			this.handleUpstreamTransmission(resourceName,jsonData,qos);

			return true;

		} else {

			return false;
		}
	}


	private void handleIncomingDataAnalysis(ResourceNameEnum resource, SensorData data) {

		// check either resource or SensorData for type
		if (data.getTypeID() == ConfigConst.HUMIDITY_SENSOR_TYPE) {

			handleHumiditySensorAnalysis(resource,data);

		}
	}

	private void handleHumiditySensorAnalysis(ResourceNameEnum resource, SensorData data) {

		_Logger.fine("Analyzing humidity data from CDA: " + data.getLocationID() + ". Value: " + data.getValue());

		boolean isLow = data.getValue() < this.triggerHumidifierFloor;
		boolean isHigh = data.getValue() > this.triggerHumidifierCeiling;

		if (isLow || isHigh) {

			_Logger.fine("Humidity data from CDA exceeds nominal range.");

			if (this.latestHumiditySensorData == null) {

				// set properties then exit - nothing more to do until the next sample
				this.latestHumiditySensorData = data;
				this.latestHumiditySensorTimeStamp = getDateTimeFromData(data);

				_Logger.fine("Starting humidity nominal exception timer. Waiting for seconds: " +
						this.humidityMaxTimePastThreshold);

				return;

			} else {

				OffsetDateTime curHumiditySensorTimeStamp = getDateTimeFromData(data);

				long diffSeconds = ChronoUnit.SECONDS.between(
						this.latestHumiditySensorTimeStamp,
						curHumiditySensorTimeStamp);

				_Logger.fine("Checking Humidity value exception time delta: " + diffSeconds);

				if (diffSeconds >= this.humidityMaxTimePastThreshold) {
					ActuatorData ad = new ActuatorData();
					ad.setName(ConfigConst.HUMIDIFIER_ACTUATOR_NAME);
					ad.setLocationID(data.getLocationID());
					ad.setTypeID(ConfigConst.HUMIDIFIER_ACTUATOR_TYPE);
					ad.setValue(this.nominalHumiditySetting);

					if (isLow) {

						ad.setCommand(ConfigConst.ON_COMMAND);

					} else if (isHigh) {

						ad.setCommand(ConfigConst.OFF_COMMAND);

					}

					_Logger.info("Humidity exceptional value reached. Sending actuation event to CDA: " + ad);

					this.lastKnownHumidifierCommand = ad.getCommand();
					sendActuatorCommandtoCda(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE, ad);

					// set ActuatorData and reset SensorData (and timestamp)
					this.latestHumidifierActuatorData = ad;
					this.latestHumiditySensorData = null;
					this.latestHumiditySensorTimeStamp = null;
				}
			}

		} else if (this.lastKnownHumidifierCommand == ConfigConst.ON_COMMAND) {

			// check if we need to turn off the humidifier
			if (this.latestHumidifierActuatorData != null) {

				// check the value - if the humidifier is on, but not yet at nominal, keep it on
				if (this.latestHumidifierActuatorData.getValue() >= this.nominalHumiditySetting) {
					this.latestHumidifierActuatorData.setCommand(ConfigConst.OFF_COMMAND);

					_Logger.info(
							"Humidity nominal value reached. Sending OFF actuation event to CDA: " +
									this.latestHumidifierActuatorData);

					sendActuatorCommandtoCda(ResourceNameEnum.CDA_ACTUATOR_CMD_RESOURCE,
							this.latestHumidifierActuatorData);

					// reset ActuatorData and SensorData (and timestamp)
					this.lastKnownHumidifierCommand = this.latestHumidifierActuatorData.getCommand();
					this.latestHumidifierActuatorData = null;
					this.latestHumiditySensorData = null;
					this.latestHumiditySensorTimeStamp = null;

				} else {

					_Logger.fine("Humidifier is still on. Not yet at nominal levels (OK).");

				}

			} else {
				// shouldn't happen, unless some other logic
				// nullifies the class-scoped ActuatorData instance
				_Logger.warning("ERROR: ActuatorData for humidifier is null (shouldn't be). Can't send command.");
			}
		}
	}

	private void sendActuatorCommandtoCda(ResourceNameEnum resource, ActuatorData data) {

		// NOTE: This is how an ActuatorData command will get passed to the CDA
		// when the GDA is providing the CoAP server and hosting the appropriate
		// ActuatorData resource. It will typically be used when the OBSERVE
		// client (the CDA, assuming the GDA is the server and CDA is the client)
		// has sent an OBSERVE GET request to the ActuatorData resource.

		if (this.actuatorDataListener != null) {

			this.actuatorDataListener.onActuatorDataUpdate(data);

		}

		// NOTE: This is how an ActuatorData command will get passed to the CDA
		// when using MQTT to communicate between the GDA and CDA

		if (this.enableMqttClient && this.mqttClient != null) {

			String jsonData =DataUtil.getInstance().actuatorDataToJson(data);

			if (this.mqttClient.publishMessage(resource,jsonData,ConfigConst.DEFAULT_QOS)) {

				_Logger.info("Published ActuatorData command from GDA to CDA: " +data.getCommand());

			} else {

				_Logger.warning("Failed to publish ActuatorData command from GDA to CDA: " +data.getCommand());
			}
		}
	}

	private OffsetDateTime getDateTimeFromData(BaseIotData data) {

		OffsetDateTime odt = null;

		try {

			odt = OffsetDateTime.parse(data.getTimeStamp());

		} catch (Exception e) {

			_Logger.warning("Failed to extract ISO 8601 timestamp from IoT data. Using local current time.");

			// TODO: this won't be accurate, but should be reasonably close, as the CDA will
			// most likely have recently sent the data to the GDA

			odt =OffsetDateTime.now();

		}

		return odt;

	}



	@Override
	public boolean handleSystemPerformanceMessage(ResourceNameEnum resourceName, SystemPerformanceData data) {

		if (data != null) {
			_Logger.info("Handling system performance message: " + data.getName());

			if (data.hasError()) {
				_Logger.warning("Error flag set for SystemPerformanceData instance.");
			}

			return true;
		} else {
			return false;
		}

	}
	
	public void setActuatorDataListener(String name, IActuatorDataListener listener) {

		if (listener != null) {

			this.actuatorDataListener = listener;

		}
	}



	public void startManager() {

		_Logger.info("Starting DeviceDataManager...");

		if (this.sysPerfMgr != null) {

			this.sysPerfMgr.startManager();

		}

		if (this.mqttClient != null) {

			if (this.mqttClient.connectClient()) {

				_Logger.info("Successfully connected MQTT client to broker.");

				//int qos = ConfigUtil.getInstance().getInteger(
						//ConfigConst.MQTT_GATEWAY_SERVICE,
						//ConfigConst.DEFAULT_QOS_KEY,
						//ConfigConst.DEFAULT_QOS);

				// IMPORTANT NOTE: The 'subscribeToTopic()' method calls shown
				// below will be moved to MqttClientConnector.connectComplete()
				// in Lab Module 10. For now, they can remain here.
				//for (ResourceNameEnum topic : topics) {
					//boolean flag = this.mqttClient.subscribeToTopic(topic, qos);
					//if (!flag) {
						//_Logger.warning("Error! Failed to subscribe to this topic: " + topic);
					//}
				//}

			} else {
				_Logger.severe("Error! Failed to connect MQTT client to broker.");
			}
		}

		if (this.enableCoapServer && this.coapServer != null) {

			if (this.coapServer.startServer()) {

				_Logger.info("CoAP server started.");

			} else {

				_Logger.severe("Failed to start CoAP server. Check log file for details.");

			}
		}
	}


	public void stopManager() {

		_Logger.info("Stopping DeviceDataManager...");

		if (this.sysPerfMgr != null) {

			this.sysPerfMgr.stopManager();

		}

		if (this.mqttClient != null) {

				// IMPORTANT NOTE: The 'subscribeToTopic()' method calls shown
				// below will be moved to MqttClientConnector.connectComplete()
				// in Lab Module 10. For now, they can remain here.

				for (ResourceNameEnum topic : topics) {

					boolean flag = this.mqttClient.unsubscribeFromTopic(topic);
					if (!flag) {

						_Logger.warning("Error! Failed to unsubscribe from this topic: " + topic);

					}
				}

				if (this.mqttClient.disconnectClient()) {

					_Logger.info("Successfully disconnected MQTT client from broker.");

			} else {

				_Logger.severe("Error! Failed to disconnect MQTT client from broker.");

			}
		}

		if (this.enableCoapServer && this.coapServer != null) {

			if (this.coapServer.stopServer()) {

				_Logger.info("CoAP server stopped.");

			} else {

				_Logger.severe("Failed to stop CoAP server. Check log file for details.");

			}
		}
	}

	
	// private methods
	
	/**
	 * Initializes the enabled connections. This will NOT start them, but only create the
	 * instances that will be used in the {@link #startManager() and #stopManager()) methods.
	 * 
	 */
	private void initConnections() {

	}


	private void initManager() {

	ConfigUtil configUtil = ConfigUtil.getInstance();

		this.enableSystemPerf = configUtil.getBoolean(ConfigConst.GATEWAY_DEVICE,
														ConfigConst.ENABLE_SYSTEM_PERF_KEY);

		if (this.enableSystemPerf) {

			this.sysPerfMgr = new SystemPerformanceManager();
			this.sysPerfMgr.setDataMessageListener(this);
		}

		// NOTE: This is new - creating the MQTT client connector instance
		if (this.enableMqttClient) {
			this.mqttClient = new MqttClientConnector();

			// NOTE: The next line isn't technically needed until Lab Module 10
			this.mqttClient.setDataMessageListener(this);
		}

		if (this.enableCoapServer) {
			this.coapServer = new CoapServerGateway(this);

		}

		if (this.enableCloudClient) {
			// TODO: implement this in Lab Module 10
		}

		if (this.enablePersistenceClient) {
			// TODO: implement this as an optional exercise in Lab Module 5

		}
	}

	private void handleIncomingDataAnalysis(ResourceNameEnum resourceName, ActuatorData data) {

		_Logger.info("Analyzing incoming actuator data: " + data.getName());

		if (data.isResponseFlagEnabled()) {
			// TODO: implement this
		} else {
			if (this.actuatorDataListener != null) {
				this.actuatorDataListener.onActuatorDataUpdate(data);
			}
		}
	}

	private void handleIncomingDataAnalysis(ResourceNameEnum resourceName, SystemStateData data) {

		_Logger.info("handleIncomingDataAnalysis for SystemStateData has been called");

	}


	private void handleUpstreamTransmission(ResourceNameEnum resourceName, String jsonData, int qos) {

		// NOTE: This will be implemented in Part 04
		_Logger.info("TODO: Send JSON data to cloud service: " + resourceName);

	}

}
