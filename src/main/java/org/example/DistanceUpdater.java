package org.example;

import com.graphhopper.jsprit.core.algorithm.state.StateId;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.algorithm.state.StateUpdater;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.ActivityVisitor;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;

public class DistanceUpdater implements StateUpdater, ActivityVisitor {

    private StateManager stateManager;
    private StateId distanceId;
    private double distance;
    private VehicleRoute route;
    private TourActivity prevAct;
    private VehicleRoutingTransportCosts transportCosts;

    public DistanceUpdater(
            StateId distanceId,
            StateManager stateManager,
            VehicleRoutingTransportCosts transportCosts
    ) {
        this.stateManager = stateManager;
        this.distanceId = distanceId;
        this.transportCosts = transportCosts;
    }

    @Override
    public void begin(VehicleRoute route) {
        this.distance = 0.;
        this.route = route;
        this.prevAct = route.getStart();
    }

    @Override
    public void visit(TourActivity activity) {

        this.distance += getDistance(prevAct, activity);
        prevAct = activity;

    }

    @Override
    public void finish() {
        this.distance += getDistance(this.prevAct, this.route.getEnd());
        stateManager.putRouteState(route, route.getVehicle(), distanceId, distance);
    }

    private double getDistance(TourActivity from, TourActivity to) {
        return this.transportCosts.getDistance(
                from.getLocation(),
                to.getLocation(),
                from.getEndTime(),
                this.route.getVehicle()
        );
    }
}
