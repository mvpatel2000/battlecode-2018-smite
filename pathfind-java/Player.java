// import the API.
// See xxx for the javadocs.
import bc.*;
import java.util.*;

public class Player {

    //Stuff from game/api
    public static GameController gc;
    public static Team ally;
    public static Team enemy;
    public static Planet myPlanet;
    public static PlanetMap map;
    public static int width;
    public static int height;

    //Stuff we create
    public static int[][] distance_field;
    public static ArrayList<Direction>[][] movement_field;

    public static void main(String[] args) {

        // Connect to the manager, starting the game
        gc = new GameController();

        ally = gc.team();   //this is our team
        enemy = Team.Red;   //this is evil team
        if(ally==Team.Red)
            enemy = Team.Blue;

        myPlanet = Planet.Earth;     //TODO: How to figure out which planet you're on??

        map = gc.startingMap(myPlanet);                   
        width = (int)map.getWidth();
        height = (int)map.getHeight(); 

        Queue<int[]> enemy_location_queue = new LinkedList<int[]>();  //starting enemy location queue for generating vector field
        VecUnit initial_units = map.getInitial_units();
        for(int i=0; i<initial_units.size(); i++) {
            Unit unit = initial_units.get(i);
            if(ally!=unit.team()) {      
                MapLocation enemy_location = unit.location().mapLocation();
                int[] enemy_info = {enemy_location.getX(), enemy_location.getY(), 0, 0};
                enemy_location_queue.add(enemy_info);
            }
        }

        //distance_field[x][y]: tells you how far away you are from the destination on your current path
        //movement_field[x][y]: gives you ArrayList of Directions that are equally optimal for reaching destination
        distance_field = new int[width][height];
        movement_field = new ArrayList[width][height];
        for(int w=0; w<width; w++) {
            for(int h=0; h<height; h++) {
                distance_field[w][h] = (50*50+1);
                movement_field[w][h] = new ArrayList<Direction>();                
            }
        }
        buildFieldBFS(enemy_location_queue);


        int maxworkers = 1-1; //starting
        int maxfactory = 1;
        int maxrangers = 1;

        while (true) {
            if(gc.round()%50==0)
                System.out.println("Current round: "+gc.round());
            
            VecUnit units = gc.myUnits();
            for (int unit_counter = 0; unit_counter < units.size(); unit_counter++) {
                Unit unit = units.get(unit_counter);

                if(unit.unitType()==UnitType.Worker && !unit.location().isInGarrison() && !unit.location().isInSpace()) {
                    MapLocation myloc = unit.location().mapLocation();
                    VecUnit nearbyFactories = gc.senseNearbyUnitsByType(myloc, (long)1.0, UnitType.Factory);
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
                    else
                        moveOnVectorField(unit, myloc);
                }       

                else if(unit.unitType()==UnitType.Ranger && !unit.location().isInGarrison() && !unit.location().isInSpace()) {                    
                    MapLocation myloc = unit.location().mapLocation();
                    VecUnit enemies = gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), enemy);      

                    moveOnVectorField(unit, myloc);

                    if(enemies.size()>0 && gc.isAttackReady(unit.id()) && gc.canAttack(unit.id(), enemies.get(0).id())) //attacks nearest enemy
                        gc.attack(unit.id(), enemies.get(0).id());
                }

                else if(unit.unitType()==UnitType.Factory) {
                    int rangers = 0;
                    for(int i=0; i<units.size(); i++)
                        if(units.get(i).unitType()==UnitType.Ranger)
                            rangers++;
                    if(rangers<maxrangers && gc.canProduceRobot(unit.id(),UnitType.Ranger)) {  //TODO: check to see queue is empty
                        gc.produceRobot(unit.id(),UnitType.Ranger);
                    }
                    if(gc.canUnload(unit.id(),Direction.East)) { //unload to east
                        gc.unload(unit.id(),Direction.East);
                    }
                    else if(gc.canUnload(unit.id(),Direction.South)) { //unload to east
                        gc.unload(unit.id(),Direction.South);
                    }
                }       
            }
            
            gc.nextTurn(); // Submit the actions we've done, and wait for our next turn.
        }
    }

    //Moves unit on vector field
    //Should be used if no enemies in sight
    //If no optimal move is available (all blocked) or there exists no path, unit will not move
    public static void moveOnVectorField(Unit unit, MapLocation mapLocation) {
        int x = mapLocation.getX();
        int y = mapLocation.getY();
        if(gc.isMoveReady(unit.id())) { //checks if can move
            for(int movedir=0; movedir<movement_field[x][y].size(); movedir++) { //loops over all possible move Directions
                if(gc.canMove(unit.id(), movement_field[x][y].get(movedir))) { //verifies can move in selected direction
                    gc.moveRobot(unit.id(), movement_field[x][y].get(movedir));
                    return;
                }
            }                        
        }  
    }

    //Takes a queue of starting enemy locations and builds vector fields
    //distance_field tells you how far from current path destination
    //movement_field gives ArrayList of equally optimal Directions to move in
    public static void buildFieldBFS(Queue<int[]> queue) {
        Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, 
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};

        while(queue.peek()!=null) {
            int[] lcc = queue.poll();
            int x = lcc[0];
            int y = lcc[1];
            int dir = lcc[2];
            int depth = lcc[3];

            if(x<0 || y<0 || x>=width || y>=height ||  //border checks
                    map.isPassableTerrainAt(new MapLocation(myPlanet, x, y))==0 || //is not passable
                    distance_field[x][y]<depth) { //is an inferior move
                continue;
            }
            else if(distance_field[x][y]==depth) { //add equivalently optimal Direction
                movement_field[x][y].add(dirs[dir]);
            }
            else if(distance_field[x][y]>depth) { //replace old Directions with more optimal ones
                distance_field[x][y] = depth;
                movement_field[x][y] = new ArrayList<Direction>();
                movement_field[x][y].add(dirs[dir]);
                int[] lc2 = {x+1,y,  5,depth+1}; 
                queue.add(lc2);
                int[] lc3 = {x+1,y+1,6,depth+1}; 
                queue.add(lc3);
                int[] lc4 = {x,y+1,  7,depth+1}; 
                queue.add(lc4);
                int[] lc5 = {x-1,y+1,8,depth+1}; 
                queue.add(lc5);
                int[] lc6 = {x-1,y,  1,depth+1}; 
                queue.add(lc6);
                int[] lc7 = {x-1,y-1,2,depth+1}; 
                queue.add(lc7);
                int[] lc8 = {x,y-1,  3,depth+1}; 
                queue.add(lc8);
                int[] lc9 = {x+1,y-1,4,depth+1}; 
                queue.add(lc9);
            }            
        }
    }
}