package com.graphhopper.suggestions;

import com.graphhopper.routing.AbstractRoutingAlgorithm;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;

public class SuggestionsAlgorthm extends AbstractRoutingAlgorithm {

    private final long distance;
    private final long tolerance;

    public SuggestionsAlgorthm(Graph graph, Weighting weighting, TraversalMode traversalMode, long distance,
            long tolerance) {
        super(graph, weighting, traversalMode);
        this.distance = distance;
        this.tolerance = tolerance;
    }

    @Override
    public Path calcPath(int from, int to) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getVisitedNodes() {
        // TODO Auto-generated method stub
        return 0;
    }

}
