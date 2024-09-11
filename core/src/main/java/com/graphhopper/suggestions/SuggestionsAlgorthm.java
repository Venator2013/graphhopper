package com.graphhopper.suggestions;

import java.util.List;

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
        // not needed for this algorithm
        return null;
    }

    @Override
    public List<Path> calcPaths(int from, int to) {

        // search for paths with the given distance and tolerance

        return List.of();
    }

    @Override
    public int getVisitedNodes() {
        return 0;
    }

}
