/**
 * 
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 by Andrew D. King
 */ 

package programmingtheiot.part03.integration.connection;

import static org.junit.Assert.*;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import programmingtheiot.common.ConfigConst;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.common.ConfigUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.data.*;
import programmingtheiot.gda.connection.*;

/**
 * This test case class contains very basic integration tests for
 * MqttClientControlPacketTest. It should not be considered complete,
 * but serve as a starting point for the student implementing
 * additional functionality within their Programming the IoT
 * environment.
 *
 */
public class MqttClientControlPacketTest
{
	// static
	
	private static final Logger _Logger =
		Logger.getLogger(MqttClientControlPacketTest.class.getName());
	
	
	// member var's

	private MqttClientConnector mqttClient = null;
	private int keepAliveSeconds;

	// test setup methods

	//Initialize MQTT client configuration before every test
	@Before
	public void setUp() throws Exception
	{
		this.mqttClient = new MqttClientConnector();
		mqttClient = new MqttClientConnector();
		keepAliveSeconds = ConfigUtil.getInstance().getInteger(
				ConfigConst.MQTT_GATEWAY_SERVICE,
				ConfigConst.KEEP_ALIVE_KEY,
				ConfigConst.DEFAULT_KEEP_ALIVE
		);
	}

	@After
	public void tearDown() throws Exception
	{
		if (mqttClient.isConnected()) {
			mqttClient.disconnectClient();
		}
	}

	@Test
	public void testConnectAndDisconnect()
	{
		// CONNECT → CONNACK
		assertTrue("Client should establish initial connection", mqttClient.connectClient());
		// calling again should warn and return false
		assertFalse("Second connect call should return false", mqttClient.connectClient());

		// wait for keep‑alive cycle to trigger PINGREQ/PINGRESP
		try {
			Thread.sleep((keepAliveSeconds * 1000L) + 5_000L);
		} catch (InterruptedException ignored) {}

		// DISCONNECT
		assertTrue("Disconnect should succeed", mqttClient.disconnectClient());
		// and now should return false
		assertFalse("Repeated disconnect should fail", mqttClient.disconnectClient());

		_Logger.info("testConnectAndDisconnect() completed.");
	}

	@Test
	public void testServerPing()
	{
		assertTrue("Client must connect", mqttClient.connectClient());
		assertTrue("Client must be marked connected", mqttClient.isConnected());

		// sleep long enough to force a ping
		try {
			Thread.sleep((keepAliveSeconds * 1000L) + 5_000L);
		} catch (InterruptedException ignored) {}

		// after ping cycle, client should still be connected
		assertTrue("Client remains connected after ping", mqttClient.isConnected());
		assertTrue("Disconnect must be successful", mqttClient.disconnectClient());

		_Logger.info("testServerPing() completed.");
	}

	@Test
	public void testPubSub()
	{
		int[] qosLevels = { 0, 1, 2 };

		assertTrue("Connection must be successful", mqttClient.connectClient());

		for (int qos : qosLevels) {
			// SUBSCRIBE → SUBACK
			assertTrue(
					"Subscribe must succeed at QoS " + qos,
					mqttClient.subscribeToTopic(
							ResourceNameEnum.CDA_MGMT_STATUS_MSG_RESOURCE,
							qos
					)
			);

			// sleep broker for a moment before publishing
			try {
				Thread.sleep(1_000L);
			} catch (InterruptedException ignored) {}

			// PUBLISH (+ PUBACK if QoS 1, or full QoS 2 handshake)
			assertTrue(
					"Publishing must work at QoS " + qos,
					mqttClient.publishMessage(
							ResourceNameEnum.CDA_MGMT_STATUS_MSG_RESOURCE,
							"Sample payload: QoS " + qos,
							qos
					)
			);

			// UNSUBSCRIBE → UNSUBACK
			assertTrue(
					"Unsubscribing must succeed for QoS " + qos,
					mqttClient.unsubscribeFromTopic(
							ResourceNameEnum.CDA_MGMT_STATUS_MSG_RESOURCE
					)
			);
		}

		assertTrue("disconnectClient must succeed", mqttClient.disconnectClient());
		_Logger.info("testPubSub() completed.");
	}
	// IMPORTANT: be sure to use QoS 1 and 2 to see ALL control packets

}
