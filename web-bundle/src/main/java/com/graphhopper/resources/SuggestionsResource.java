package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.coll.GHBitSet;
import com.graphhopper.coll.GHTBitSet;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.http.ProfileResolver;
import com.graphhopper.isochrone.algorithm.ContourBuilder;
import com.graphhopper.isochrone.algorithm.ShortestPathTree;
import com.graphhopper.isochrone.algorithm.Triangulator;
import com.graphhopper.jackson.ResponsePathSerializer;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import org.hibernate.validator.constraints.Range;
import org.locationtech.jts.geom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.ToDoubleFunction;

import static com.graphhopper.resources.IsochroneResource.ResponseType.geojson;
import static com.graphhopper.resources.RouteResource.removeLegacyParameters;
import static com.graphhopper.routing.util.TraversalMode.EDGE_BASED;
import static com.graphhopper.routing.util.TraversalMode.NODE_BASED;

@Path("suggestions")
public class SuggestionsResource {

    private static final Logger logger = LoggerFactory.getLogger(SuggestionsResource.class);

    private final GraphHopperConfig config;
    private final GraphHopper graphHopper;
    private final ProfileResolver profileResolver;
    private final String osmDate;

    @Inject
    public SuggestionsResource(GraphHopperConfig config, GraphHopper graphHopper, Triangulator triangulator,
            ProfileResolver profileResolver) {
        this.config = config;
        this.graphHopper = graphHopper;
        this.triangulator = triangulator;
        this.profileResolver = profileResolver;
        this.osmDate = graphHopper.getProperties().get("datareader.data.date");
    }

    public enum ResponseType {
        json, geojson
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGet(
            @Context UriInfo uriInfo,
            @QueryParam("profile") String profileName,
            @QueryParam("point") @NotNull GHPointParam point,
            @QueryParam("time_limit") @DefaultValue("600") OptionalLong timeLimitInSeconds,
            @QueryParam("distance_limit") @DefaultValue("-1") OptionalLong distanceLimitInMeter,
            @QueryParam("type") @DefaultValue("json") ResponseType respType,
            @QueryParam("tolerance") @DefaultValue("0") double toleranceInMeter,
            @QueryParam("full_geometry") @DefaultValue("false") boolean fullGeometry) {
        StopWatch sw = new StopWatch().start();
        PMap hintsMap = new PMap();
        RouteResource.initHints(hintsMap, uriInfo.getQueryParameters());
        hintsMap.putObject(Parameters.CH.DISABLE, true);
        hintsMap.putObject(Parameters.Landmark.DISABLE, true);

        PMap profileResolverHints = new PMap(hintsMap);
        profileResolverHints.putObject("profile", profileName);
        profileName = profileResolver.resolveProfile(profileResolverHints);
        removeLegacyParameters(hintsMap);

        Profile profile = graphHopper.getProfile(profileName);
        if (profile == null)
            throw new IllegalArgumentException("The requested profile '" + profileName + "' does not exist");
        LocationIndex locationIndex = graphHopper.getLocationIndex();
        BaseGraph graph = graphHopper.getBaseGraph();
        Weighting weighting = graphHopper.createWeighting(profile, hintsMap);
        BooleanEncodedValue inSubnetworkEnc = graphHopper.getEncodingManager()
                .getBooleanEncodedValue(Subnetwork.key(profileName));
        Snap snap = locationIndex.findClosest(point.get().lat, point.get().lon,
                new DefaultSnapFilter(weighting, inSubnetworkEnc));
        if (!snap.isValid())
            throw new IllegalArgumentException("Point not found:" + point);
        QueryGraph queryGraph = QueryGraph.create(graph, snap);

        BreadthFirstSearch findAllCycles = new BreadthFirstSearch() {

            @Override
            protected GHBitSet createBitSet() {
                return new GHTBitSet();
            }

            @Override
            public boolean goFurther(int nodeId) {
                return true;
            }

            @Override
            public boolean checkAdjacent(EdgeIteratorState edge) {
                return true;
            }

        };

        findAllCycles.start(queryGraph.createEdgeExplorer(), snap.getClosestNode());

        return Response.ok().header("X-GH-Took", "" + sw.getSeconds() * 1000).build();
    }

}
