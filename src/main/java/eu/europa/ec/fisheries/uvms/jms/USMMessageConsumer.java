package eu.europa.ec.fisheries.uvms.jms;

import eu.europa.ec.fisheries.uvms.message.AbstractConsumer;
import eu.europa.ec.fisheries.uvms.message.JMSUtils;
import eu.europa.ec.fisheries.uvms.message.MessageConstants;

import javax.annotation.PostConstruct;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.jms.Destination;
import javax.naming.InitialContext;

/**
 * Created by georgige on 10/23/2015.
 */
@Stateless
@LocalBean
public class USMMessageConsumer extends AbstractConsumer {

    private Destination destination;

	@PostConstruct
    public void init() {
        InitialContext ctx;
        try {
            ctx = new InitialContext();
        } catch (Exception e) {
            LOG.error("Failed to get InitialContext",e);
            throw new RuntimeException(e);
        }
        destination = JMSUtils.lookupQueue(ctx, MessageConstants.QUEUE_USM4UVMS);
    }
	
	
    @Override
    public Destination getDestination() {
        return destination;
    }

    public String getDestinationName() {
        return MessageConstants.QUEUE_USM4UVMS;
    }
}