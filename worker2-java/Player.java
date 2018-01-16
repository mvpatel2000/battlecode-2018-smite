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


        int maxworkers = 1; //starting
        int maxfactory = 1;
        int maxrangers = 1;
        boolean buildingFactory = false;
        int current_workers=initial_workers;
        int centerid=0;
        int replicatorid = 0;
        int num_factories = 0;
        while (true) {
            if(gc.round()%50==0) {
                System.out.println("Current round: "+gc.round());
            }
            VecUnit units = gc.myUnits();

            if(current_workers==4) {
                centerid = centralworker(units);
            } else if (current_workers<3) {
                replicatorid = optimalreplicator(units);
            }
            for (int unit_counter = 0; unit_counter < units.size(); unit_counter++) {
                Unit unit = units.get(unit_counter);
                if(unit.unitType()==UnitType.Worker && !unit.location().isInGarrison() && !unit.location().isInSpace()) {
                    ArrayList<KarbDir> mykarbs = karboniteSort(unit, unit.location());
                    if(current_workers>3) {
                        if(unit.id()==centerid && num_factories<=(gc.round()/30) + width) {
                            //blueprint factory
                            Direction buildDirection = leastKarboniteDirection(unit, unit.location());
                            if(gc.canBlueprint(unit.id(), UnitType.Factory, buildDirection)) {
                                gc.blueprint(unit.id(), UnitType.Factory, buildDirection);
                                buildingFactory=true;
                                num_factories+=1;
                            }
                        } else buildFactory(unit, mykarbs, units);
                    } else {
                        for (KarbDir k : mykarbs) {
                            if(gc.canReplicate(unit.id(), k.dir)) {
                                gc.replicate(unit.id(), k.dir);
                                current_workers+=1;
                                break;
                            }
                        }
                    }
                    // ****************************************
                    // WORKER LOGIC
                    // ****************************************
                    // 0. Mine a little Karbonite
                    // 1. Replicate a few workers originally
                    // 2. Mine Karbonite
                    // 2. Multiple workers to build each Factory
                    // 3. Find balance between factory, replication, and mining
                    // ****************************************
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

    public static long passability(Unit myUnit, long width, long height) {
        MapLocation myLoc = myUnit.location().mapLocation();
        MapLocation enemyLoc = new MapLocation(width - myLoc.x, height-myLoc.y);
        long distSquared = myLoc.distanceSquaredTo(enemyLoc);
        ArrayList<Direction> myList = Helpers.astar(myUnit, enemyLoc);
        long passability_factor = distSquared/(myList.size()*myList.size());//1 = completely clear, closer to 0=not clear at all.
        return passability_factor;
    }
    //find the closest factory
    public static Direction backToFactory(Unit myUnit, VecUnit units) {
        int closestfactoryid = 0;
        long smalldist=100000000000L;
        ArrayList<Unit> myFactories = new ArrayList<Unit>();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if(unit.unitType()==UnitType.Factory && !unit.location().isInGarrison() && !unit.location().isInSpace()) {
                myFactories.add(unit);
            }
        }
        if(myFactories.size()>0) {
            int r = Helpers.randInt(0,myFactories.size()-1);
            return myUnit.location().mapLocation().directionTo(myFactories.get(r).location().mapLocation());
        } else {
            return Helpers.opposite(getVectorFieldDirection(myUnit, myUnit.location().mapLocation()));
        }
    }
    //For Workers
    //Return the central of three workers, the center one will build the factory
    public static int centralworker(VecUnit units) {
        long smalldistsum=100000000000L;
        int centralworkerid = 0;
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            long mydistsum = 0L;
            int myworkerid = unit.id();
            if(unit.unitType()==UnitType.Worker && !unit.location().isInGarrison() && !unit.location().isInSpace()) {
                for (int j = i; j < units.size(); j++) {
                    Unit secondunit = units.get(j);
                    if(secondunit.unitType()==UnitType.Worker && !secondunit.location().isInGarrison() && !secondunit.location().isInSpace()) {
                        long tempdist = unit.location().mapLocation().distanceSquaredTo(secondunit.location().mapLocation());
                        if(tempdist<49L) { //really far away workers arent counted
                            mydistsum+=tempdist;
                        }
                    }
                }
            }
            if(mydistsum<smalldistsum && mydistsum>0L) {
                smalldistsum=mydistsum;
                centralworkerid=myworkerid;
            }
        }
        return centralworkerid;
    }

    public static boolean buildFactory(Unit unit, ArrayList<KarbDir> mykarbs, VecUnit units) {
        VecUnit nearbyFactories = gc.senseNearbyUnitsByType(unit.location().mapLocation(), unit.visionRange(), UnitType.Factory);

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
        //no blueprints exist
        workerharvest(unit, mykarbs);
        workermove(unit, mykarbs, units);
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
    public static void workermove(Unit unit, ArrayList<KarbDir> mykarbs, VecUnit units) {
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
        Direction tonearkarb = nearestKarboniteDir(unit, myLoc, 4);
        if(tonearkarb!=null) {
            fuzzyMove(unit, tonearkarb);
        } else {
            Direction d = Direction.Center;
            while(d == Direction.Center) d = Helpers.randDir();
            fuzzyMove(unit, d);
        }
        return;
    }

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
    //find the optimal replicator
    public static int optimalreplicator(VecUnit units) {
        long largestkarbonite=0L;
        int replicatorid = 0;
        int cooldown=0;
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            long mykarb = 0L;
            if(unit.unitType()==UnitType.Worker && !unit.location().isInGarrison() && !unit.location().isInSpace()) {
                mykarb = sumKarb(unit);
                //factor in abilitycooldown when determining best replicator
                //System.out.println("Amount of Karbonite: " + mykarb);
                if((mykarb-unit.abilityCooldown()*3)>=(largestkarbonite-cooldown*3)) {
                    largestkarbonite=mykarb;
                    replicatorid=unit.id();
                    cooldown = unit.id();
                }
            }
        }
        return replicatorid;
    }
    //sum karbonite over viewing radius
    public static long sumKarb(Unit unit) {
        long totalkarb = 0L;
        MapLocation myLoc = unit.location().mapLocation();
        int x = myLoc.getX();
        int y = myLoc.getY();
        for (int i=Math.max(x-7, 0); i<Math.min(x+8,(int)map.getWidth()+1); i++) {
            for (int j=Math.max(0,y-7); j<Math.min(y+8,(int)map.getHeight()+1); j++) {
                MapLocation m = new MapLocation(myPlanet, i, j);
                if((x-i)*(x-i) + (y-j*(y-j))<unit.visionRange()) {
                    if(gc.canSenseLocation(m)) {
                        totalkarb+=gc.karboniteAt(m);
                    }
                }
            }
        }
        return totalkarb;
    }
    public static boolean makeAttack(Unit unit, VecUnit myUnits[], boolean firstFactory) {
        if(firstFactory==true) {
            Direction buildDirection = leastKarboniteDirection(unit, unit.location());
            if(gc.canBlueprint(unit.id(), UnitType.Factory, buildDirection)) {
                gc.blueprint(unit.id(), UnitType.Factory, buildDirection);
                return true;
            }
        } else {
            Direction rdir = greedyKarboniteMove(unit, unit.location());
            if(sumKarb(unit)>100L) {
                if(gc.canReplicate(unit.id(), rdir)) {
                    gc.replicate(unit.id(), rdir);
                    return true;
                }
            }
        }
        return false; //took action, cannot mine
    }
    //

    public static Direction leastKarboniteDirection(Unit unit, Location theloc) {
        MapLocation myloc = theloc.mapLocation();
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        long mykarb = 0L;
        Direction bestdir=Helpers.opposite(getVectorFieldDirection(unit, myloc));
        for (int i=0; i<dirs.length; i++) {
            MapLocation newloc = myloc.add(dirs[i]);
            if(gc.canMove(unit.id(), dirs[i])) {
                long thiskarb = gc.karboniteAt(newloc);
                if (thiskarb<mykarb) {
                    mykarb = thiskarb;
                    bestdir = dirs[i];
                }
            }
        }
        return bestdir;
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
    //Move in the direction of the most Karbonite
    public static Direction greedyKarboniteMove(Unit unit, Location thisloc) {
        MapLocation myloc = thisloc.mapLocation();
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        long mykarb = 0L;
        Direction bestdir=getVectorFieldDirection(unit, myloc);
        for (int i=0; i<dirs.length; i++) {
            MapLocation newloc = myloc.add(dirs[i]);
            if(gc.canMove(unit.id(), dirs[i])) {
                long thiskarb = gc.karboniteAt(newloc);
                if (thiskarb>mykarb) {
                    mykarb = thiskarb;
                    bestdir = dirs[i];
                }
            }
        }
        return bestdir;
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
