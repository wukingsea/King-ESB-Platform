package com.king.esb.core.routing;

import com.king.esb.core.route.EndpointDefinition;
import com.king.esb.core.route.EndpointType;
import com.king.esb.core.route.LoadBalanceStrategy;
import com.king.esb.core.route.TargetDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadBalancerTest {

    private final LoadBalancer loadBalancer = new LoadBalancer();

    private EndpointDefinition endpoint(String id) {
        return new EndpointDefinition(id, "http://localhost/" + id, EndpointType.HTTP, "GET", null, 0, 0, 0, null);
    }

    @Test
    void roundRobinRotatesThroughEndpoints() {
        TargetDefinition target = new TargetDefinition("t", List.of(endpoint("a"), endpoint("b"), endpoint("c")),
                LoadBalanceStrategy.ROUND_ROBIN, null, false);

        assertEquals("a", loadBalancer.candidates(target).get(0).id());
        assertEquals("b", loadBalancer.candidates(target).get(0).id());
        assertEquals("c", loadBalancer.candidates(target).get(0).id());
        assertEquals("a", loadBalancer.candidates(target).get(0).id());
    }

    @Test
    void failoverAdvancesOnFailureAndResetsOnSuccess() {
        TargetDefinition target = new TargetDefinition("f", List.of(endpoint("a"), endpoint("b")),
                LoadBalanceStrategy.FAILOVER, null, false);

        assertEquals("a", loadBalancer.candidates(target).get(0).id());
        loadBalancer.reportFailure(target);
        assertEquals("b", loadBalancer.candidates(target).get(0).id());
        loadBalancer.reportFailure(target);
        assertEquals("a", loadBalancer.candidates(target).get(0).id());
        loadBalancer.reportSuccess(target);
        assertEquals("a", loadBalancer.candidates(target).get(0).id());
    }

    @Test
    void randomReturnsEveryCandidateOnce() {
        TargetDefinition target = new TargetDefinition("r", List.of(endpoint("a"), endpoint("b"), endpoint("c")),
                LoadBalanceStrategy.RANDOM, null, false);
        List<EndpointDefinition> candidates = loadBalancer.candidates(target);
        assertEquals(3, candidates.size());
        assertTrue(candidates.stream().map(EndpointDefinition::id).toList().containsAll(List.of("a", "b", "c")));
    }

    @Test
    void emptyEndpointPoolHasNoCandidates() {
        TargetDefinition target = new TargetDefinition("e", List.of(), LoadBalanceStrategy.ROUND_ROBIN, null, false);
        assertTrue(loadBalancer.candidates(target).isEmpty());
    }
}
