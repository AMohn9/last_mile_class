package org.example;

import com.graphhopper.jsprit.analysis.toolbox.Plotter;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.state.StateId;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.algorithm.state.VehicleDependentTraveledDistance;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint;
import com.graphhopper.jsprit.core.problem.constraint.MaxDistanceConstraint;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Solutions;

import java.io.File;
import java.util.*;

public class App
{
    // Constants
    final static int NUM_DELIVERIES = 100;
    final static int NUM_SUPER_SHOPPERS = 10;

    final static int WALMART_VEHICLE_CAPACITY = 100;
    final static int CUSTOMER_VEHICLE_CAPACITY = 15;

    final static int WALMART_VEHICLE_MAX_STOPS = 20;
    final static int CUSTOMER_VEHICLE_MAX_STOPS = 3;

    final static int WALMART_VEHICLE_FIXED_COST = 1000;
    final static int CUSTOMER_VEHICLE_FIXED_COST = 10;

    final static int WALMART_VEHICLE_VARIABLE_DISTANCE_COST = 1;
    final static int CUSTOMER_VEHICLE_VARIABLE_DISTANCE_COST = 2;

    final static double WALMART_VEHICLE_MAX_DISTANCE = 35*8;
    final static double CUSTOMER_MAX_DISTANCE_RATIO = 0.1;

    final static int STORE_X = 10;
    final static int STORE_Y = 10;

    // jsprit constants
    final static int VOLUME_INDEX = 0;
    final static int MAX_STOP_INDEX = 1;

    final static Random r = new Random(1234);

    final static Map<Vehicle, Double> maxDistanceMap = new HashMap<>();

    public static void main(String[] args) {
        createOutputDir();

        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();

        // Add our Walmart truck
        vrpBuilder.addVehicle(getWalmartVehicle());

        // Add random deliveries
        for (int i=0; i<NUM_DELIVERIES; i++) {
            vrpBuilder.addJob(
                    getNewService(
                            String.valueOf(i),
                            0,
                            STORE_X * 2,
                            1,
                            5,
                            List.of()
                    )
            );
        }

        // Add random store pickup customers
        List<Service> customerHomes = new ArrayList<>();
        for (int i=0; i<NUM_SUPER_SHOPPERS; i++) {
            Service service = getNewService(
                    String.valueOf(NUM_DELIVERIES + i),
                    0,
                    STORE_X * 2,
                    10,
                    11,
                    List.of("customer " + i)
            );
            vrpBuilder.addJob(service);
            customerHomes.add(service);
        }

        // Solve
        VehicleRoutingProblem problem = vrpBuilder.build();
        StateManager stateManager = new StateManager(problem);
        ConstraintManager constraintManager = new ConstraintManager(problem, stateManager);

        for (int i=0; i<NUM_SUPER_SHOPPERS; i++) {
            vrpBuilder.addVehicle(getCustomerVehicle(problem, i, customerHomes.get(i).getLocation()));
        }

        // This is incredibly dumb, but we have to build the problem to get the distance to the customer
        // to build the vehicle, but then we add the vehicle to the builder, so we have to re-build the problem
        problem = vrpBuilder.build();
        stateManager = new StateManager(problem);
        constraintManager = new ConstraintManager(problem, stateManager);

        addMaxDistanceConstraint(problem, stateManager, constraintManager, maxDistanceMap);

        VehicleRoutingProblemSolution solution = solve(problem, stateManager, constraintManager);

        solution.getCost();

        // Print
        SolutionPrinter.print(problem, solution, SolutionPrinter.Print.VERBOSE);

        // Plot and save to file
        Plotter plotter = new Plotter(problem, solution);
        plotter.plot("output/solution.png", "solution");

        // Animate
//        new GraphStreamViewer(problem, solution).labelWith(Label.ID).setRenderDelay(10).display();
    }

    public static void createOutputDir() {
        // create output folder
        File dir = new File("output");
        // if the directory does not exist, create it
        if (!dir.exists()){
            System.out.println("creating directory ./output");
            boolean result = dir.mkdir();
            if(result) System.out.println("./output created");
        }
    }

    public static VehicleRoutingProblemSolution solve(VehicleRoutingProblem problem, StateManager stateManager, ConstraintManager constraintManager) {
        VehicleRoutingAlgorithm algorithm = Jsprit.Builder.newInstance(problem)
                .setStateAndConstraintManager(stateManager, constraintManager)
                .buildAlgorithm();

        // Search for a solution
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();

        // Get the best
        return Solutions.bestOf(solutions);
    }

    public static VehicleImpl getWalmartVehicle() {
        VehicleTypeImpl vehicleType = VehicleTypeImpl.Builder.newInstance("walmartVehicle")
                .addCapacityDimension(VOLUME_INDEX, WALMART_VEHICLE_CAPACITY)
                .addCapacityDimension(MAX_STOP_INDEX, WALMART_VEHICLE_MAX_STOPS)
                .setFixedCost(WALMART_VEHICLE_FIXED_COST)
                .setCostPerDistance(WALMART_VEHICLE_VARIABLE_DISTANCE_COST)
                .build();

        VehicleImpl walmartVehicle = VehicleImpl.Builder.newInstance("walmartVehicle")
                .setStartLocation(Location.newInstance(STORE_X, STORE_Y))
                .setType(vehicleType)
                .build();

        maxDistanceMap.put(walmartVehicle, WALMART_VEHICLE_MAX_DISTANCE);
        return walmartVehicle;
    }

    public static VehicleImpl getCustomerVehicle(VehicleRoutingProblem problem, int vehicleNum, Location customerLocation) {
        VehicleTypeImpl vehicleType = VehicleTypeImpl.Builder.newInstance("customerVehicle " + vehicleNum)
                .addCapacityDimension(VOLUME_INDEX, CUSTOMER_VEHICLE_CAPACITY)
                .addCapacityDimension(MAX_STOP_INDEX, CUSTOMER_VEHICLE_MAX_STOPS)
                .setFixedCost(CUSTOMER_VEHICLE_FIXED_COST)
                .setCostPerDistance(CUSTOMER_VEHICLE_VARIABLE_DISTANCE_COST)
                .build();

        VehicleImpl customerVehicle = VehicleImpl.Builder.newInstance("customerVehicle " + vehicleNum)
                .setStartLocation(Location.newInstance(STORE_X, STORE_Y))
                .setType(vehicleType)
                .addSkill("customer " + vehicleNum)
                .build();

        Location storeLocation = problem.getAllLocations().stream()
                .filter(l -> l.getCoordinate().getX() == 10 && l.getCoordinate().getY() == 10)
                .findFirst().orElseThrow();
        double transitDistance = problem.getTransportCosts().getDistance(storeLocation, customerLocation, 1, customerVehicle);
        maxDistanceMap.put(customerVehicle, 2 * transitDistance * (1 + CUSTOMER_MAX_DISTANCE_RATIO));

        return customerVehicle;
    }

    public static Service getNewService(String id, int rangeMin, int rangeMax, int weightMin, int weightMax, Collection<String> skills) {
        double randomX = rangeMin + (rangeMax - rangeMin) * r.nextDouble();
        double randomY = rangeMin + (rangeMax - rangeMin) * r.nextDouble();

        int randomWeight = weightMin + r.nextInt(weightMax - weightMin);

        return Service.Builder.newInstance(String.valueOf(id))
                .addSizeDimension(VOLUME_INDEX, randomWeight)
                .addSizeDimension(MAX_STOP_INDEX, 1)
                .setLocation(Location.newInstance(randomX, randomY))
                .addAllRequiredSkills(skills)
                .build();
    }

    private static void addMaxDistanceConstraint(VehicleRoutingProblem problem, StateManager stateManager, ConstraintManager constraintManager, Map<Vehicle, Double> vehicleDistanceMap) {

        StateId maxDistance = stateManager.createStateId("max-distance");

        stateManager.addStateUpdater(
                new VehicleDependentTraveledDistance(
                        problem.getTransportCosts(),
                        stateManager,
                        maxDistance,
                        vehicleDistanceMap.keySet()
                )
        );

        HardActivityConstraint distanceConstraint = new MaxDistanceConstraint(
                stateManager,
                maxDistance,
                problem.getTransportCosts(),
                vehicleDistanceMap
        );

        constraintManager.addConstraint(distanceConstraint, ConstraintManager.Priority.CRITICAL);
    }
}
