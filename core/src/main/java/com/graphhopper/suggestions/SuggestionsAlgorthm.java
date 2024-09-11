package com.graphhopper.suggestions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.routing.AbstractRoutingAlgorithm;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;

public class SuggestionsAlgorthm extends AbstractRoutingAlgorithm {

    private final long distance;
    private final long tolerance;

    private int visitedNodes = 0;

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
        GHBitSet explored = new GHBitSetImpl();

        List<Path> resultPaths = new ArrayList<>();

        Deque<EdgeEntry> stack = new ArrayDeque<>();

        stack.add(new EdgeEntry(from, 0L, new ArrayList<>()));

        while (!stack.isEmpty()) {
            EdgeEntry current = stack.pop();
            visitedNodes++;
            if (explored.contains(current.nodeId())) {
                continue;
            }

            if (current.distance() > distance - tolerance && current.distance() < distance + tolerance) {
                // reconstruct path
                Path path = new Path(graph);
                path.setFromNode(from);
                path.setEndNode(to);
                path.setDistance(current.distance());
                for (int edgeId : current.path()) {
                    path.addEdge(edgeId);
                }
                resultPaths.add(path);
            }

            explored.add(current.nodeId());

            EdgeIterator iter = edgeExplorer.setBaseNode(current.nodeId());
            while (iter.next()) {
                int connectedId = iter.getAdjNode();
                List<Integer> newPath = new ArrayList<>(current.path());
                newPath.add(iter.getEdge());
                stack.push(new EdgeEntry(connectedId, current.distance() + (long) iter.getDistance(), newPath));
            }
        }

        return resultPaths;
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    private record EdgeEntry(int nodeId, long distance, List<Integer> path) {
    }

}
