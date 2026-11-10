package com.king.esb.core.spi;

import com.king.esb.common.EsbErrorCode;
import com.king.esb.common.EsbException;
import com.king.esb.core.model.EsbExchange;
import com.king.esb.core.model.EsbMessage;
import com.king.esb.core.route.EndpointDefinition;
import com.king.esb.core.route.EndpointType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Resolves the {@link OutboundClient} for a given {@link EndpointType}. */
public class OutboundClientRegistry {

    private final Map<EndpointType, OutboundClient> clients = new EnumMap<>(EndpointType.class);

    public OutboundClientRegistry(List<OutboundClient> outboundClients) {
        for (OutboundClient client : outboundClients) {
            clients.put(client.supports(), client);
        }
    }

    public EsbMessage invoke(EndpointDefinition endpoint, EsbMessage request, EsbExchange exchange) {
        OutboundClient client = clients.get(endpoint.type());
        if (client == null) {
            throw new EsbException(EsbErrorCode.INTERNAL_ERROR,
                    "No outbound client registered for type " + endpoint.type());
        }
        return client.invoke(endpoint, request, exchange);
    }

    public boolean supports(EndpointType type) {
        return clients.containsKey(type);
    }
}
