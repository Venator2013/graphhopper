package com.graphhopper.suggestions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import com.graphhopper.routing.AbstractRoutingAlgorithm;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.StopWatch;

public class SuggestionsAlgorthm extends AbstractRoutingAlgorithm {

    private final long distance;
    private final long tolerance;
    private final int limit;
    private final int timeLimit;
    private final EncodingManager encodingManager;

    private int visitedNodes = 0;

    public SuggestionsAlgorthm(Graph graph, Weighting weighting, TraversalMode traversalMode, long distance,
            long tolerance, int limit, int timeLimit, EncodingManager encodingManager) {
        super(graph, weighting, traversalMode);
        this.distance = distance;
        this.tolerance = tolerance;
        this.limit = limit;
        this.timeLimit = timeLimit;
        this.encodingManager = encodingManager;
    }

    @Override
    public Path calcPath(int from, int to) {
        // not needed for this algorithm
        return null;
    }

    @Override
    public List<Path> calcPaths(int from, int to) {

        Deque<EdgeEntry> queue = new ArrayDeque<>();
        queue.push(EdgeEntry.startEntry(from));

        PriorityQueue<EdgeEntry> resultQueue = new PriorityQueue<>(limit,
                (e1, e2) -> Double.compare(e1.weighting(), e2.weighting()));

        BooleanEncodedValue footAccess = encodingManager.getBooleanEncodedValue("foot_access");

        StopWatch stopWatch = new StopWatch().start();
        while (!queue.isEmpty() && stopWatch.getMillis() < timeLimit) {
            EdgeEntry current = queue.pop();
            visitedNodes++;

            // skip if we have already reached the limit
            if (current.distance() > distance + tolerance) {
                continue;
            }

            // skip if we cannot reach the target node with the current distance
            if (GHUtility.getDistance(current.nodeId, from, nodeAccess) + current.distance() > distance + tolerance) {
                continue;
            }

            if (Math.abs(current.distance() - distance) < tolerance && current.nodeId() == to) {
                // reconstruct path
                resultQueue.add(current);
                if (resultQueue.size() > limit) {
                    resultQueue.poll();
                }
                continue;
            }

            EdgeIterator iter = edgeExplorer.setBaseNode(current.nodeId());

            while (iter.next()) {
                // skip if the edge is already in the path or if the edge is the last edge
                if (current.lastEdge() == iter.getEdge() || current.path().contains(iter.getEdgeKey())) {
                    continue;
                }

                if (!iter.get(footAccess)) {
                    continue;
                }

                double currentWeight = weighting.calcEdgeWeight(iter, false) + current.weighting();
                if (Double.isInfinite(currentWeight)) {
                    // continue;
                }

                Set<Integer> newPath = new LinkedHashSet<>(current.path());
                newPath.add(iter.getEdgeKey());
                queue.push(
                        new EdgeEntry(iter.getAdjNode(), iter.getEdge(), current.distance() + (long) iter.getDistance(),
                                newPath, currentWeight));
            }
        }

        return resultQueue.stream()
                .map(e -> constructPah(from, to, e)).toList();
    }

    private Path constructPah(int from, int to, EdgeEntry current) {
        Path path = new Path(graph);
        path.setFromNode(from);
        path.setEndNode(to);
        path.setDistance(current.distance());
        path.setWeight(current.weighting());

        for (int edgeKeyId : current.path()) {
            path.addEdge(GHUtility.getEdgeFromEdgeKey(edgeKeyId));
        }
        return path;
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    private record EdgeEntry(int nodeId, int lastEdge, long distance, Set<Integer> path, double weighting) {

        static EdgeEntry startEntry(int nodeId) {
            return new EdgeEntry(nodeId, -1, 0L, new LinkedHashSet<>(), 0);
        }
    }

}
