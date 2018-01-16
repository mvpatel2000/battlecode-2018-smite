// import the API.
// See xxx for the javadocs.
//////////////////////////////////////////////////////////
//TODO:
// - workers on mars
// - workers building rockets
//- merge code with other
// PERFORMANCE ISSUES:
/// - performs decent on smaller or more karbonite rich maps (handily beats combatpath, even with very old rangers)
/// - performs poorly on large, sparse maps (maps with little karbonite)
// -----> eg. we never build a factory after the inital couple because
// ------- our ranger factory production never lets us reach 100 karbonite
// -----> thus use karbonite/area ratio to change rate of factories (line 127) & perhaps? worker production
//****************************************************************
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
    public static int[][] random_distance_field;
    public static ArrayList<Direction>[][] random_movement_field;

    static class KarbDir implements Comparable {
        Direction dir;
        long karb;
        // Constructor
        public KarbDir(Direction dir, long karb) {
            this.dir = dir;
            this.karb = karb;
        }
        public String toString() {
            return this.dir + " " + this.karb;
        }
        public int compareTo(Object other) {
            if (!(other instanceof KarbDir))
                throw new ClassCastException("A Karbdir object expected.");
            long otherkarb = ((KarbDir) other).karb;
            return (int)(this.karb - otherkarb);
        }
    }

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
        int initial_workers = 0;

        Queue<int[]> enemy_location_queue = new LinkedList<int[]>();  //starting enemy location queue for generating vector field
        VecUnit initial_units = map.getInitial_units();
        for(int i=0; i<initial_units.size(); i++) {
            Unit unit = initial_units.get(i);
            if(ally!=unit.team()) {
                MapLocation enemy_location = unit.location().mapLocation();
                int[] enemy_info = {enemy_location.getX(), enemy_location.getY(), 0, 0};
                enemy_location_queue.add(enemy_info);
            }
            if(ally==unit.team()) {
                if(unit.unitType()==UnitType.Worker) {
                    initial_workers+=1;
                }
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

        random_distance_field = new int[width][height]; //generate random movement field
        random_movement_field = new ArrayList[width][height];
        buildRandomField();


        int maxworkers = 1; //starting
        int maxfactory = 1;
        int maxrangers = 1;
        int current_workers=initial_workers;
        int num_factories = 0;
        int minworkers=initial_workers*4; //replicate each dude *4 before creating factories

        while (true) {
            if(gc.round()%50==0) {
                System.out.println("Current round: "+gc.round());
            }
            VecUnit units = gc.myUnits();

            for (int unit_counter = 0; unit_counter < units.size(); unit_counter++) {
                Unit unit = units.get(unit_counter);
                if(unit.unitType()==UnitType.Worker && !unit.location().isInGarrison() && !unit.location().isInSpace()) {
                    ArrayList<KarbDir> mykarbs = karboniteSort(unit, unit.location());
                    if(current_workers>=minworkers) {
                        //execute build order
                        boolean didbuild = buildFactory(unit, mykarbs, units, 20l);
                        if(didbuild==true){
                            continue;
                        } else {
                            if(num_factories<=(gc.round()/10)) {
                                //blueprint factory or (replicate or moveharvest)
                                int val = blueprintFactory(unit, mykarbs, units, 20l);
                                if(val>=2) { //if blueprintFactory degenerates to replicateOrMoveHarvest()
                                    current_workers+=(val-2);
                                } else { //did not degenerate
                                    num_factories+=val;
                                }
                            } else {
                                workerharvest(unit, mykarbs);
                                workermove(unit, mykarbs);
                            }
                        }
                    } else {
                        //replicate or move harvest
                        current_workers += replicateOrMoveHarvest(unit, mykarbs);
                    }
                }

                else if(unit.unitType()==UnitType.Ranger && !unit.location().isInGarrison() && !unit.location().isInSpace()) {

                    // TODO: EXTENSIVE TESTING ON MOVEMENT

                    MapLocation myloc = unit.location().mapLocation();
                    VecUnit enemies_in_sight = gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), enemy);
                    if(enemies_in_sight.size()>0) { //combat state
                        VecUnit enemies_in_range = gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), enemy);
                        if(enemies_in_range.size()==0) {    //move towards enemy
                            Unit nearestUnit = enemies_in_sight.get(0);
                            MapLocation nearloc = nearestUnit.location().mapLocation();
                            int mindist = (int)nearloc.distanceSquaredTo(myloc);
                            for(int i=1; i<enemies_in_sight.size(); i++) {
                                Unit testUnit = enemies_in_sight.get(i);
                                MapLocation testloc = testUnit.location().mapLocation();
                                int testdist = (int)testloc.distanceSquaredTo(myloc);
                                if(testdist<mindist) {
                                    nearestUnit = testUnit;
                                    nearloc = testloc;
                                    mindist = testdist;
                                }
                            }
                            fuzzyMove(unit, myloc.directionTo(nearloc));
                        }
                        enemies_in_range = gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), enemy);

                        if(enemies_in_range.size()>0) {
                            if(gc.isAttackReady(unit.id()) && gc.canAttack(unit.id(), enemies_in_range.get(0).id())) //attacks nearest enemy
                                gc.attack(unit.id(), enemies_in_range.get(0).id());
                            //attack
                            //1. anything that can hit u and u can kill
                            //2. anything that can hit u (by default rangers will be in here for obvious reasons)
                            //3. anything you can kill
                            //4. Other units
                            //5. Buildings
                            //In each tier: mages > rangers > healers > knights > workers
                            //Tiebreaker again: closest

                            if(gc.isMoveReady(unit.id())) {
                                Unit nearestUnit = enemies_in_range.get(0);
                                MapLocation nearloc = nearestUnit.location().mapLocation();
                                int mindist = (int)nearloc.distanceSquaredTo(myloc);
                                for(int i=1; i<enemies_in_range.size(); i++) {
                                    Unit testUnit = enemies_in_range.get(i);
                                    MapLocation testloc = testUnit.location().mapLocation();
                                    int testdist = (int)testloc.distanceSquaredTo(myloc);
                                    if(testdist<mindist) {
                                        nearestUnit = testUnit;
                                        nearloc = testloc;
                                        mindist = testdist;
                                    }
                                }
                                fuzzyMove(unit, myloc.directionTo(nearloc));
                            }
                        }
                    }
                    else {
                        moveOnVectorField(unit, myloc);
                    }
                }

                else if(unit.unitType()==UnitType.Factory) {
                    if(gc.canProduceRobot(unit.id(), UnitType.Ranger)) {  //Autochecks if something else is being produced or not
                        gc.produceRobot(unit.id(),UnitType.Ranger);
                    }
                    Direction unload_dir = Direction.East;
                    fuzzyUnload(unit, unload_dir);
                }
            }

            gc.nextTurn(); // Submit the actions we've done, and wait for our next turn.
        }
    }

    //Only called when no factories are within range
    //Blueprint a factory ONLY if there are 2+ workers within range (long rad). In this case, return 1
    //Else, replicate (or moveHarvest, if replication not possible).
    // Return 2(if moveharvest) or 3(if replication succesful)
    public static int blueprintFactory(Unit unit, ArrayList<KarbDir> mykarbs, VecUnit units, long rad) {
        MapLocation myLoc = unit.location().mapLocation();
        ArrayList<Unit> closeWorkers = nearbyWorkers(unit, myLoc, rad);
        if(closeWorkers.size()>2) { //includes the original worker, we want three workers per factory
            Direction blueprintDirection = optimalDirection(unit, myLoc, closeWorkers);
            if(blueprintDirection!=null) {
                gc.blueprint(unit.id(), UnitType.Factory, blueprintDirection);
                return 1;
            } else {
                //cannot build blueprint
                workerharvest(unit, mykarbs);
                workermove(unit, mykarbs);
                return 0;
            }
        } else {
            //not enough close workers
            return (2+replicateOrMoveHarvest(unit, mykarbs)); //2+ lets parent method determine whether we replicated or not
        }
    }

    //Helper Method for blueprintFactory: Determine nearbyWorkers (includes myUnit)
    public static ArrayList<Unit> nearbyWorkers(Unit myUnit, MapLocation myLoc, long rad) {
        VecUnit myWorkers = gc.senseNearbyUnitsByType(myLoc, rad, UnitType.Worker);
        ArrayList<Unit> siceWorkers = new ArrayList<Unit>();
        for(int i=0; i<myWorkers.size(); i++) {
            Unit k = myWorkers.get(i);
            if(k.team()==ally) {
                siceWorkers.add(k);
            }
        }
        return siceWorkers;
    }
    //Helper Method for blueprintFactory: Determine location of blueprint
    //Determine location closest to all the workers within range
    public static Direction optimalDirection(Unit myUnit, MapLocation myLoc, ArrayList<Unit> closeWorkers) {
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        long shortestdist = 10000000000000L;
        Direction bestdir=null;
        for (Direction dir: dirs) {
            if(gc.canBlueprint(myUnit.id(), UnitType.Factory, dir)) {
                MapLocation newLoc = myLoc.add(dir);
                long mydist = 0L;
                for (int j = 0; j < closeWorkers.size(); j++) {
                    Unit otherworker = closeWorkers.get(j);
                    if(otherworker.unitType()==UnitType.Worker && !otherworker.location().isInGarrison() && !otherworker.location().isInSpace()) {
                        mydist+= newLoc.distanceSquaredTo(otherworker.location().mapLocation());
                    }
                }
                if(mydist<shortestdist) {
                    shortestdist=mydist;
                    bestdir=dir;
                }
            }
        }
        return bestdir;
    }

    //Replicate if you can, perform harvsestmove if not
    //Returns a number (1 or 0) to indicate number of workers gained
    public static int replicateOrMoveHarvest(Unit unit, ArrayList<KarbDir> mykarbs) {
        for (KarbDir k : mykarbs) {
            if(gc.canReplicate(unit.id(), k.dir)) {
                gc.replicate(unit.id(), k.dir);
                return 1;
            }
        }
        workerharvest(unit, mykarbs);
        workermove(unit, mykarbs);
        return 0;
    }
    //If a factory is within range, and it is not at maximum health, then build it
    //If you cannot build it because it needs repairing, repair it
    //If you cannot do either, harvest+move towards the factory, since you are out of range
    //If either of these three above scenarious occur, return true
    //If there are no factories within range, then return false
    public static boolean buildFactory(Unit unit, ArrayList<KarbDir> mykarbs, VecUnit units, long rad) {
        VecUnit nearbyFactories = gc.senseNearbyUnitsByType(unit.location().mapLocation(), rad, UnitType.Factory);

        for(int i=0; i<nearbyFactories.size(); i++) {
            Unit k = nearbyFactories.get(i);
            if(k.team()!=ally) {
                continue;
            }
            if(k.health()!=k.maxHealth()) {
                if(gc.canBuild(unit.id(), k.id())) {
                    gc.build(unit.id(), k.id());
                    return true;
                } else if(gc.canRepair(unit.id(), k.id())){
                    gc.repair(unit.id(), k.id());
                    return true;
                } else {
                    workerharvest(unit, mykarbs);
                    Direction toFactory = unit.location().mapLocation().directionTo(nearbyFactories.get(0).location().mapLocation());
                    fuzzyMove(unit, toFactory);
                    return true;
                }
            }
        }
        return false;
    }
    //harvest in the optimal direction
    public static void workerharvest(Unit unit, ArrayList<KarbDir> mykarbs) {
        MapLocation myLoc = unit.location().mapLocation();
        for (KarbDir k : mykarbs) {
            MapLocation newLoc = myLoc.add(k.dir);
            if(gc.karboniteAt(newLoc)>0L) {
                if(gc.canHarvest(unit.id(), k.dir)){
                    gc.harvest(unit.id(), k.dir);
                    return;
                }
            }
        }

    }
    public static void workermove(Unit unit, ArrayList<KarbDir> mykarbs) {
        MapLocation myLoc = unit.location().mapLocation();
        for (KarbDir k : mykarbs) {
            MapLocation newLoc = myLoc.add(k.dir);
            if(gc.karboniteAt(newLoc)>0L) {
                if(gc.canMove(unit.id(), k.dir)) {
                    if(gc.isMoveReady(unit.id())) {
                        gc.moveRobot(unit.id(), k.dir);
                        return;
                    }
                }
            }
        }
        Direction tonearkarb = nearestKarboniteDir(unit, myLoc, 7);
        if(tonearkarb!=null) {
            fuzzyMove(unit, tonearkarb);
        } else {
            if(gc.round()<(width+height)/2) {
                fuzzyMove(unit, myLoc.directionTo(new MapLocation(myPlanet, (int)width/2, (int)height/2)));
            } else {
                moveOnRandomField(unit, myLoc);
            }
        }
        return;
    }

    //helper method for workermove
    //returns direction of nearest karbonite, in case there is no karbonite immediately around worker
    //Computationally inefficient, O(n^2), n=visionradius
    public static Direction nearestKarboniteDir(Unit unit, MapLocation myLoc, int visionrad) {
        int visrad = visionrad;
        long totalkarb = 0L;
        int x = myLoc.getX();
        int y = myLoc.getY();
        for (int i=Math.max(x-visrad, 0); i<Math.min(x+visrad+1,(int)map.getWidth()+1); i++) {
            for (int j=Math.max(0,y-visrad); j<Math.min(y+visrad+1,(int)map.getHeight()+1); j++) {
                MapLocation m = new MapLocation(myPlanet, i, j);
                if((x-i)*(x-i) + (y-j*(y-j))<unit.visionRange()) {
                    if(gc.canSenseLocation(m)) {
                        if(gc.karboniteAt(m)>0L) {
                            return myLoc.directionTo(m);
                        }
                    }
                }
            }
        }
        return null;
    }

    //sort directions by karbonite content
    public static ArrayList<KarbDir> karboniteSort(Unit unit, Location theloc) {
        MapLocation myLoc = theloc.mapLocation();
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.Center,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        ArrayList<KarbDir> karboniteDirections = new ArrayList<KarbDir>();
        long mykarb = 0L;
        for (int i=0; i<dirs.length; i++) {
            MapLocation newloc = myLoc.add(dirs[i]);
            if(gc.canMove(unit.id(), dirs[i]) || dirs[i]==Direction.Center) {
                long thiskarb = gc.karboniteAt(newloc);
                karboniteDirections.add(new KarbDir(dirs[i], thiskarb));
            }
        }
        Collections.sort(karboniteDirections, Collections.reverseOrder()); //sort high to low
        return karboniteDirections;
    }
    //***************************************************
    //***************FUZZY METHODS***********************
    //***************************************************
    public static void fuzzyUnload(Unit unit, Direction dir) {
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        int[] shifts = {0, -1, 1, -2, 2, -3, 3, 4};
        int dirindex = 0;
        for(int i=0; i<8; i++) {
            if(dir==dirs[i]) {
                dirindex = i;
                break;
            }
        }
        for(int i=0; i<shifts.length; i++) {
            if(gc.canUnload(unit.id(), dirs[ (dirindex+shifts[i]+8)%8 ])) {
                gc.unload(unit.id(), dirs[ (dirindex+shifts[i]+8)%8 ]);
            }
        }
    }

    //Attempts to move unit in direction as best as possible
    //Scans 45 degree offsets, then 90
    public static void fuzzyMove(Unit unit, Direction dir) {
        if(!gc.isMoveReady(unit.id()))
            return;
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        int[] shifts = {0, -1, 1, -2, 2};
        int dirindex = 0;
        for(int i=0; i<8; i++) {
            if(dir==dirs[i]) {
                dirindex = i;
                break;
            }
        }
        for(int i=0; i<5; i++) {
            if(gc.canMove(unit.id(), dirs[ (dirindex+shifts[i]+8)%8 ])) {
                gc.moveRobot(unit.id(), dirs[ (dirindex+shifts[i]+8)%8 ]);
                return;
            }
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
    //Returns vector field direction
    public static Direction getVectorFieldDirection(Unit unit, MapLocation mapLocation) {
        int x = mapLocation.getX();
        int y = mapLocation.getY();
        if(gc.isMoveReady(unit.id())) { //checks if can move
            for(int movedir=0; movedir<movement_field[x][y].size(); movedir++) { //loops over all possible move Directions
                if(gc.canMove(unit.id(), movement_field[x][y].get(movedir))) { //verifies can move in selected direction
                    return movement_field[x][y].get(movedir);
                }
            }
        }
        return Direction.Center;
    }


    //Finds random field direction
    public static Direction getRandomFieldDirection(Unit unit, MapLocation mapLocation) {
        if(!gc.isMoveReady(unit.id())) //checks if can move
            return Direction.Center;
        UnitType myUnitType = unit.unitType();
        int x = mapLocation.getX();
        int y = mapLocation.getY();
        for(int movedir=0; movedir<random_movement_field[x][y].size(); movedir++) { //loops over all possible move directions
            Direction dir = random_movement_field[x][y].get(movedir);
            if(dir == Direction.Center) { //refactors vector field if reaches enemy start location
                buildRandomField();
                return getRandomFieldDirection(unit, mapLocation);
            }
            else if(movedir==random_movement_field[x][y].size()-1) { //fuzzy moves last possible direction
                return dir;
            }
            else if(gc.canMove(unit.id(), dir)) { //verifies can move in selected direction
                return dir;
            }
        }
        return Direction.Center;
    }
    //Moves unit on vector field
    //Should be used if no enemies in sight
    //If no optimal move is available (all blocked), unit will attempt fuzzymove in last dir
    public static void moveOnRandomField(Unit unit, MapLocation mapLocation) {
        if(!gc.isMoveReady(unit.id())) //checks if can move
            return;
        UnitType myUnitType = unit.unitType();
        int x = mapLocation.getX();
        int y = mapLocation.getY();
        for(int movedir=0; movedir<random_movement_field[x][y].size(); movedir++) { //loops over all possible move directions
            Direction dir = random_movement_field[x][y].get(movedir);
            if(dir == Direction.Center) { //refactors vector field if reaches enemy start location
                buildRandomField();
                moveOnRandomField(unit, mapLocation);
                return;
            }
            else if(movedir==random_movement_field[x][y].size()-1) { //fuzzy moves last possible direction
                fuzzyMove(unit, dir);
                return;
            }
            else if(gc.canMove(unit.id(), dir)) { //verifies can move in selected direction
                gc.moveRobot(unit.id(), dir);
                return;
            }
        }
    }


    public static void buildRandomField() {
        MapLocation clustertarget = new MapLocation(myPlanet, (int)(Math.random()*width), (int)(Math.random()*height)); //random movement target for cluster
        for(int i=0; i<100; i++) {
            if(gc.canSenseLocation(clustertarget)==true) //target already within sight range
                break;
            clustertarget = new MapLocation(myPlanet, (int)(Math.random()*width), (int)(Math.random()*height));
        }
        int[] randtarget = {clustertarget.getX(), clustertarget.getY(), 0, 0};

        Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};

        Queue<int[]> queue = new LinkedList<int[]>();
        queue.add(randtarget);

        for(int w=0; w<width; w++) {
            for(int h=0; h<height; h++) {
                random_distance_field[w][h] = (50*50+1);
                random_movement_field[w][h] = new ArrayList<Direction>();
            }
        }

        while(queue.peek()!=null) {
            int[] lcc = queue.poll();
            int x = lcc[0];
            int y = lcc[1];
            int dir = lcc[2];
            int depth = lcc[3];

            if(x<0 || y<0 || x>=width || y>=height ||  //border checks
                    map.isPassableTerrainAt(new MapLocation(myPlanet, x, y))==0 || //is not passable
                    random_distance_field[x][y]<depth) { //is an inferior move
                continue;
            }
            else if(random_distance_field[x][y]==depth) { //add equivalently optimal Direction
                random_movement_field[x][y].add(dirs[dir]);
            }
            else if(random_distance_field[x][y]>depth) { //replace old Directions with more optimal ones
                random_distance_field[x][y] = depth;
                random_movement_field[x][y] = new ArrayList<Direction>();
                random_movement_field[x][y].add(dirs[dir]);
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
