package com.graphhopper.resources;

import static com.graphhopper.resources.RouteResource.removeLegacyParameters;

import java.util.List;
import java.util.OptionalLong;
import java.util.ArrayList;
import java.util.HashMap;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hibernate.validator.constraints.Range;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.http.ProfileResolver;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Subnetwork;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultSnapFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.suggestions.SuggestionsAlgorthm;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;

@Path("suggestions")
public class SuggestionsResource {

    private static final Logger logger = LoggerFactory.getLogger(SuggestionsResource.class);

    private final GraphHopperConfig config;
    private final GraphHopper graphHopper;
    private final ProfileResolver profileResolver;
    private final String osmDate;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(1E8));

    @Inject
    public SuggestionsResource(GraphHopperConfig config, GraphHopper graphHopper,
            ProfileResolver profileResolver) {
        this.config = config;
        this.graphHopper = graphHopper;
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
            @QueryParam("distance") @DefaultValue("5000") @Range(min = 100, max = 50000) long distanceInMeter,
            @QueryParam("type") @DefaultValue("json") ResponseType respType,
            @QueryParam("tolerance") @DefaultValue("0") long toleranceInMeter,
            @QueryParam("full_geometry") @DefaultValue("false") boolean fullGeometry,
            @QueryParam("limit") @DefaultValue("20") @Range(min = 1, max = 200) int limit) {
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

        // run through all edges and find all cycles with the given distance limit and
        // tolerance with a breadth first search
        SuggestionsAlgorthm algo = new SuggestionsAlgorthm(queryGraph, weighting, TraversalMode.EDGE_BASED,
                distanceInMeter, toleranceInMeter, limit);

        List<com.graphhopper.routing.Path> results = algo.calcPaths(snap.getClosestNode(), snap.getClosestNode());

        // convert the paths to jsonfeatures
        List<JsonFeature> features = new ArrayList<>();
        for (com.graphhopper.routing.Path path : results) {
            JsonFeature feature = new JsonFeature();
            HashMap<String, Object> properties = new HashMap<>();
            properties.put("bucket", features.size());
            properties.put("distance", path.getDistance());
            feature.setProperties(properties);

            PointList pointList = path.calcPoints();
            feature.setGeometry(pointList.toLineString(true));
            features.add(feature);
        }

        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("type", "FeatureCollection");
        json.putPOJO("features", features);

        return Response.ok(json).header("X-GH-Took", "" + sw.getSeconds() * 1000).build();
    }

}
