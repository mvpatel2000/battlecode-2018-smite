// import the API.
// See xxx for the javadocs.
import bc.*;

public class Player {
    public static void main(String[] args) {

        // Connect to the manager, starting the game
        GameController gc = new GameController();

        PlanetMap map = gc.startingMap(Planet.Earth);                       //TODO: How to figure out which planet you're on??
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

        while (true) {
            if(gc.round()%50==0)
                System.out.println("Current round: "+gc.round());
            // VecUnit is a class that you can think of as similar to ArrayList<Unit>, but immutable.
            VecUnit units = gc.myUnits();
            for (int i = 0; i < units.size(); i++) {
                Unit unit = units.get(i);

                // Most methods on gc take unit IDs, instead of the unit objects themselves.
                if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), unit.location().mapLocation().directionTo(enemy_location))) {
                    gc.moveRobot(unit.id(), unit.location().mapLocation().directionTo(enemy_location));
                }
            }
            // Submit the actions we've done, and wait for our next turn.
            gc.nextTurn();
        }
    }
}