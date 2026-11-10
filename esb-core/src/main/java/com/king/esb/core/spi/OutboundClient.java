package com.king.esb.core.spi;

import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.EndpointDefinition;
import com.king.esb.core.route.EndpointType;

/**
 * Outbound transport SPI. Implementations live in {@code esb-transport}; the core engine only
 * depends on this interface so protocol support can be added without touching the engine.
 */
public interface OutboundClient {

    /** The endpoint type this client handles. */
    EndpointType supports();

    /** Invokes the endpoint with the given request message and returns the response message. */
    EsbMessage invoke(EndpointDefinition endpoint, EsbMessage request, EsbExchange exchange) throws EsbException;
}
