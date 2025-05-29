package programmingtheiot.gda.connection.handlers;

import java.util.logging.Logger;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.observe.ObserveRelation;

import programmingtheiot.common.*;
import programmingtheiot.data.DataUtil;
import programmingtheiot.common.IDataMessageListener;
import programmingtheiot.common.ResourceNameEnum;
import programmingtheiot.data.ActuatorData;


public class GetActuatorCommandResourceHandler extends GenericCoapResourceHandler implements IActuatorDataListener {
    // static

    // logging infrastructure - should already be defined, although you'll need
    // to update the class name as shown below
    private static final Logger _Logger =
            Logger.getLogger(GetActuatorCommandResourceHandler.class.getName());

    // params

    private ActuatorData actuatorData = null;

    // constructors

    public GetActuatorCommandResourceHandler(String resourceName)
    {
        super(resourceName);

        // set the resource to be observable
        super.setObservable(true);
    }

    public boolean onActuatorDataUpdate(ActuatorData data)
    {
        if (data != null && this.actuatorData != null) {
            this.actuatorData.updateData(data);

            // notify all connected clients
            super.changed();

            _Logger.fine("Actuator data updated for URI: " + super.getURI() + ": Data value = " + this.actuatorData.getValue());

            return true;
        }

        return false;
    }

    @Override
    public void handleGET(CoapExchange context) {

        // validate 'context'
        if (context == null) {
            _Logger.warning("CoapExchange context is null. Cannot process request.");
            return;
        }

        _Logger.info(
                "GET request received for resource: " + super.getURI() + " with query: " + context.getRequestText());

        // accept the request
        context.accept();

        // Actuator created only for testing CoapClientConnectorTest in CDA to ensure that testGetActuatorCommandCon() and
        // testGetActuatorCommandNon() run correctly

        if (this.actuatorData == null) {
            this.actuatorData = new ActuatorData();
            this.actuatorData.setName("TestActuator");
            this.actuatorData.setValue(42.0f);
            this.actuatorData.setStateData("Test state");
            this.actuatorData.setCommand(1);
        }

        // Simulation of periodic updates to check the correct functioning of the test CoapClientConnectorTest in CDA.
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                actuatorData.setValue((float)(Math.random() * 100));
                onActuatorDataUpdate(actuatorData);
            }
        }, 2000, 5000);

        // Convert the locally stored ActuatorData to JSON using DataUtil
        String jsonData = DataUtil.getInstance().actuatorDataToJson(this.actuatorData);

        // send an appropriate response
        context.respond(ResponseCode.CONTENT, jsonData, MediaTypeRegistry.APPLICATION_JSON);
    }
}
