// import the API.
// See xxx for the javadocs.
import bc.*;

public class Player {
    public static void main(String[] args) {

        // Connect to the manager, starting the game
        GameController gc = new GameController();

        PlanetMap map = gc.startingMap(Planet.Earth);                       //TODO: How to figure out which planet you're on??
        long width = map.getWidth();
        long height = map.getHeight(); 
        System.out.println(map.getWidth()+" "+map.getHeight());

        MapLocation enemy_location = new MapLocation(Planet.Earth,0,0);     //Location of enemy--where units should move!
        VecUnit initial_units = map.getInitial_units();
        for(int i=0; i<initial_units.size(); i++) {
            Unit unit = initial_units.get(i);
            if(gc.team()!=unit.team()) {
                enemy_location = unit.location().mapLocation();
                break;
            }
        }

        int maxworkers = 5;
        int maxfactory = 3;

        while (true) {
            if(gc.round()%50==0)
                System.out.println("Current round: "+gc.round());
            
            VecUnit units = gc.myUnits();  // VecUnit is a class that you can think of as similar to ArrayList<Unit>, but immutable.
            for (int i = 0; i < units.size(); i++) {
                Unit unit = units.get(i);

                if(unit.unitType()==UnitType.Worker) {
                    VecUnit nearbyFactories = gc.senseNearbyUnitsByType(unit.location().mapLocation(), (long)1.0, UnitType.Factory);
                    if(maxworkers>0 && gc.canReplicate(unit.id(),Direction.East)) { //replicate
                        gc.replicate(unit.id(),Direction.East);
                        maxworkers--;
                    }
                    else if(nearbyFactories.size()>0 && gc.canBuild(unit.id(), nearbyFactories.get(0).id())) //build factory
                        gc.build(unit.id(),nearbyFactories.get(0).id());
                    else if(maxfactory>0 && gc.canBlueprint(unit.id(), UnitType.Factory, Direction.South)) { //blueprint factory 
                        gc.blueprint(unit.id(), UnitType.Factory, Direction.South);
                        maxfactory--;
                    }
                    else if(gc.canHarvest(unit.id(),Direction.North)) { //harvest
                        gc.harvest(unit.id(),Direction.North);
                    }
                    else if(gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), Direction.North) ) {
                        gc.moveRobot(unit.id(), Direction.North);
                    }
                }
                else if(unit.unitType()==UnitType.Factory) {
                    if(gc.canProduceRobot(unit.id(),UnitType.Ranger)) {
                        gc.produceRobot(unit.id(),UnitType.Ranger);
                    }
                    if(1!=1 && gc.canUnload(unit.id(),Direction.East)) {
                        gc.unload(unit.id(),Direction.East);
                    }
                }
                else if(unit.unitType()==UnitType.Ranger) {
                    if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), unit.location().mapLocation().directionTo(enemy_location))) {
                        gc.moveRobot(unit.id(), unit.location().mapLocation().directionTo(enemy_location));
                    }    
                }

                // Most methods on gc take unit IDs, instead of the unit objects themselves.
                // if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), unit.location().mapLocation().directionTo(enemy_location))) {
                //     gc.moveRobot(unit.id(), unit.location().mapLocation().directionTo(enemy_location));
                // }                
            }
            
            gc.nextTurn(); // Submit the actions we've done, and wait for our next turn.
        }
    }
}