package com.graphhopper.suggestions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Queue;
import java.util.LinkedList;
import java.util.logging.Logger;

import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.routing.AbstractRoutingAlgorithm;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;

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

        List<Path> resultPaths = new ArrayList<>();
        Deque<EdgeEntry> queue = new ArrayDeque<>();
        queue.push(EdgeEntry.startEntry(from));

        while (!queue.isEmpty()) {
            EdgeEntry current = queue.pop();
            visitedNodes++;

            // skip if we have already reached the limit
            if (current.distance() > distance + tolerance) {
                continue;
            }

            if (Math.abs(current.distance() - distance) < tolerance && current.nodeId() == to) {
                // reconstruct path
                Path path = constructPah(from, to, current);
                resultPaths.add(path);
                continue;
            }

            EdgeIterator iter = edgeExplorer.setBaseNode(current.nodeId());

            while (iter.next()) {
                // skip if the edge is already in the path
                if (current.lastEdge() == iter.getEdge()) {
                    continue;
                }

                if (current.path().contains(iter.getEdgeKey())) {
                    continue;
                }

                int connectedId = iter.getAdjNode();

                List<Integer> newPath = new ArrayList<>(current.path());
                newPath.add(iter.getEdgeKey());
                queue.push(new EdgeEntry(connectedId, iter.getEdge(), current.distance() + (long) iter.getDistance(),
                        newPath));
            }
        }

        return resultPaths;
    }

    private Path constructPah(int from, int to, EdgeEntry current) {
        Path path = new Path(graph);
        path.setFromNode(from);
        path.setEndNode(to);
        path.setDistance(current.distance());
        for (int edgeKeyId : current.path()) {
            path.addEdge(GHUtility.getEdgeFromEdgeKey(edgeKeyId));
        }
        return path;
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    private record EdgeEntry(int nodeId, int lastEdge, long distance, List<Integer> path) {

        static EdgeEntry startEntry(int nodeId) {
            return new EdgeEntry(nodeId, -1, 0L, new ArrayList<>());
        }
    }

}
