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
    public static ArrayList<int[]> enemy_locations;
    public static int[][] distance_field;
    public static ArrayList<Direction>[][] movement_field;
    public static int[][] random_distance_field;
    public static ArrayList<Direction>[][] random_movement_field;
    public static ArrayList<int[]> enemy_buildings;
    public static boolean canSnipe;
    public static boolean doesPathExist;

    public static void main(String[] args) {

        // Connect to the manager, starting the game
        gc = new GameController();

        ally = gc.team();   //this is our team
        enemy = Team.Red;   //this is evil team
        if(ally==Team.Red)
            enemy = Team.Blue;

        myPlanet = gc.planet(); //this planet

        map = gc.startingMap(myPlanet); //map characteristics             
        width = (int)map.getWidth();
        height = (int)map.getHeight(); 

        enemy_locations = new ArrayList<int[]>(); //starting enemy location queue for generating vector field         
        VecUnit initial_units = map.getInitial_units();
        for(int i=0; i<initial_units.size(); i++) {
            Unit unit = initial_units.get(i);
            if(ally!=unit.team()) {      
                MapLocation enemy_location = unit.location().mapLocation();
                int[] enemy_info = {enemy_location.getX(), enemy_location.getY(), 0, 0};
                enemy_locations.add(enemy_info);
            }
        }

        distance_field = new int[width][height]; //generate movement field
        movement_field = new ArrayList[width][height];         
        buildFieldBFS(); 

        random_distance_field = new int[width][height]; //generate random movement field
        random_movement_field = new ArrayList[width][height];         
        buildRandomField();

        doesPathExist = false; //determine if a path exists
        for(int i=0; i<initial_units.size(); i++) {
            Unit unit = initial_units.get(i);
            if(ally==unit.team()) {      
                MapLocation ally_location = unit.location().mapLocation();
                if(distance_field[ally_location.getX()][ally_location.getY()]<50*50+1) {
                    doesPathExist = true;
                    break;
                }
            }
        }     

        UnitType[] rarray = {UnitType.Worker, UnitType.Ranger, UnitType.Ranger, UnitType.Ranger, UnitType.Rocket, UnitType.Rocket,
                                 UnitType.Rocket, UnitType.Worker, UnitType.Worker, UnitType.Worker}; //research queue
        for(int i=0; i<rarray.length; i++)
            gc.queueResearch(rarray[i]); 
        canSnipe = false;

        int maxworkers = 10-1; //unit limits
        int maxfactory = 4;
        int maxrangers = 1000;

        while (true) {            
            if(gc.round()%50==0) { //print round number and update random field
                System.out.println("Current round: "+gc.round());                
                buildRandomField();
            }
            if(canSnipe==false && gc.round()>350) {//activate snipe
                canSnipe = true;
                enemy_buildings = new ArrayList<int[]>();
            }
            if(canSnipe) //build snipe targets
                buildSnipeTargets();
            
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

                else if(unit.unitType()==UnitType.Ranger && !unit.location().isInGarrison() && !unit.location().isInSpace() && unit.rangerIsSniping()==0) {
                    MapLocation myloc = unit.location().mapLocation();
                    VecUnit enemies_in_sight = gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), enemy);      
                    if(enemies_in_sight.size()>0) {      //combat state
                        if(enemy_locations.size()==0) { //add enemy locations
                            updateEnemies();
                        }
                        VecUnit enemies_in_range = gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), enemy);  
                        if(enemies_in_range.size()==0) {    //move towards enemy since nothing in attack range   
                            Unit nearestUnit = getNearestUnit(myloc, enemies_in_sight);
                            MapLocation nearloc = nearestUnit.location().mapLocation();
                            fuzzyMove(unit, myloc.directionTo(nearloc));
                        }
                        enemies_in_range = gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), enemy);

                        if(enemies_in_range.size()>0) {
                            rangerAttack(unit, enemies_in_range); //attack based on heuristic
                            if(gc.isMoveReady(unit.id())) {  //move away from nearest unit to survive
                                Unit nearestUnit = getNearestUnit(myloc, enemies_in_range);
                                MapLocation nearloc = nearestUnit.location().mapLocation();
                                fuzzyMove(unit, nearloc.directionTo(myloc));
                            }
                        }
                    }
                    else { //non-combat state
                        if(doesPathExist==false || enemy_locations.size()==0) {
                            moveOnRandomField(unit, myloc);
                        }
                        else {
                            moveOnVectorField(unit, myloc);
                        }

                        if(canSnipe && enemy_buildings.size()>0 && gc.isBeginSnipeReady(unit.id())) { //sniping
                            int[] target = enemy_buildings.get(0);
                            MapLocation snipetarget = new MapLocation(myPlanet, target[1], target[2]);
                            if(gc.canBeginSnipe(unit.id(), snipetarget)) {
                                gc.beginSnipe(unit.id(), snipetarget);
                            }
                            target[0]--;
                            if(target[0]==0)
                                enemy_buildings.remove(0);
                            else
                                enemy_buildings.set(0,target);
                        }                        
                    }                                     
                }

                else if(unit.unitType()==UnitType.Knight && !unit.location().isInGarrison() && !unit.location().isInSpace()) {
                    MapLocation myloc = unit.location().mapLocation();
                    VecUnit enemies_in_sight = gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), enemy);      
                    if(enemies_in_sight.size()>0) {      //combat state
                        Unit nearestUnit = getNearestUnit(myloc, enemies_in_sight);
                        MapLocation nearloc = nearestUnit.location().mapLocation();
                        fuzzyMove(unit, myloc.directionTo(nearloc));

                        VecUnit enemies_in_range = gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), enemy);     
                        if(enemies_in_range.size()>0) {
                            nearestUnit = enemies_in_range.get(0);
                            if(gc.isAttackReady(unit.id()) && gc.canAttack(unit.id(), nearestUnit.id()))
                                gc.attack(unit.id(), nearestUnit.id());
                        }
                    }
                    else { //non-combat state
                        if(enemy_locations.size()>0)
                            moveOnVectorField(unit, myloc);
                    }                                     
                }

                else if(unit.unitType()==UnitType.Mage && !unit.location().isInGarrison() && !unit.location().isInSpace()) {
                    MapLocation myloc = unit.location().mapLocation();
                    VecUnit enemies_in_sight = gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), enemy);      
                    if(enemies_in_sight.size()>0) {      //combat state
                        Unit nearestUnit = getNearestUnit(myloc, enemies_in_sight);
                        MapLocation nearloc = nearestUnit.location().mapLocation();
                        fuzzyMove(unit, myloc.directionTo(nearloc));
                        if(gc.isAttackReady(unit.id()) && gc.canAttack(unit.id(),nearestUnit.id()))
                            gc.attack(unit.id(), nearestUnit.id());
                    }
                    else { //non-combat state
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

                else if(unit.unitType()==UnitType.Rocket && !unit.location().isInSpace()) {
                    Direction dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.West
                                        Direction.Southwest, Direction.South, Direction.Southeast};
                    if(myPlanet==Planet.Earth) {
                        //load everything ASAP

                        //figure out where / how to launch
                    }
                    else if(myPlanet==Planet.Mars) { //unload everything ASAP
                        int dirctr = 0;
                        VecUnitID garrison = unit.structureGarrison();
                        for(int i=0; i<garrison.size(); i++) {
                            while(dirctr<8) {
                                if(gc.canUnload(unit.id(), dirs[dirctr])) {
                                    gc.unload(unit.id(), dirs[dirctr]);
                                    dirctr++;
                                    break;
                                }
                                dirctr++;
                            }
                            if(dirctr>=8)
                                break;
                        }
                    }
                }
            }
            
            gc.nextTurn(); // Submit the actions we've done, and wait for our next turn.
        }
    }

    //adds all spotted enemies to enemy_locations
    public static void updateEnemies() {
        VecUnit total_enemies = gc.senseNearbyUnitsByTeam(new MapLocation(myPlanet, width/2, height/2), width*height/2, enemy);
        for(int eloc = 0; eloc<total_enemies.size(); eloc++) {
            MapLocation enemloc = total_enemies.get(eloc).location().mapLocation();
            int[] enemy_info = {enemloc.getX(), enemloc.getY(), 0, 0};
            enemy_locations.add(enemy_info);
        }
        if(enemy_locations.size()>0)
            buildFieldBFS();
    }

    //updates snipe list to contain all buildings
    public static void buildSnipeTargets() {
        VecUnit total_enemies = gc.senseNearbyUnitsByTeam(new MapLocation(myPlanet, width/2, height/2), width*height/2, enemy); //all enemies
        for(int i = 0; i<total_enemies.size(); i++) {
            Unit enemy_unit = total_enemies.get(i);
            boolean isDuplicate = false;
            if(enemy_unit.unitType()==UnitType.Factory) { //if factory
                for(int targs=0; targs<enemy_buildings.size(); targs++) { //check if already marked
                    if(enemy_buildings.get(targs)[3]==enemy_unit.id()) {
                        isDuplicate = true;
                        break;
                    }
                }
                if(isDuplicate)
                    continue;
                MapLocation enem_loc = enemy_unit.location().mapLocation();
                int[] building_info = {8, enem_loc.getX(), enem_loc.getY(), enemy_unit.id()};
                enemy_buildings.add(building_info);
            }
            else if(enemy_unit.unitType()==UnitType.Rocket) { //if rocket
                for(int targs=0; targs<enemy_buildings.size(); targs++) { //check if already marked
                    if(enemy_buildings.get(targs)[3]==enemy_unit.id()) {
                        isDuplicate = true;
                        break;
                    }
                }
                if(isDuplicate)
                    continue;
                MapLocation enem_loc = enemy_unit.location().mapLocation();
                int[] building_info = {5, enem_loc.getX(), enem_loc.getY(), enemy_unit.id()};
                enemy_buildings.add(building_info);
            }
        }
    }

    //1. anything that u can kill
    //2. attack factories then rockets
    //3. anything that can hit u
    //Tiebreaker weakest
    //Tiebreaker again: rangers > mages > healers > knights > workers > factory > rocket
    public static void rangerAttack(Unit unit, VecUnit enemies_in_range) {
        if(!gc.isAttackReady(unit.id()))
            return;
        MapLocation myloc = unit.location().mapLocation();
        int[][] heuristics = new int[(int)enemies_in_range.size()][2];
        for(int i=0; i<enemies_in_range.size(); i++) {
            int hval = 0;
            Unit enemy = enemies_in_range.get(i);
            UnitType enemyType = enemy.unitType();
            int distance = (int)myloc.distanceSquaredTo(enemy.location().mapLocation()); //max value of 70
            if(unit.damage()>(int)enemy.health()) //can kill
                hval+=10000;
            if(enemyType==UnitType.Rocket)
                hval+=8000;
            if(enemyType==UnitType.Factory)
                hval+=7000;
            try {
                if(distance<(int)enemy.attackRange()) //can be hit
                    hval+=1000;
            } catch(Exception e) {} //if unit has no attack range
            hval += (10-((int)enemy.health())/(unit.damage()))*100; //weakest unit        
            UnitType[] priorities = {UnitType.Worker, UnitType.Knight, UnitType.Healer, UnitType.Mage, UnitType.Ranger}; //unit priorities
            for(int utctr=0; utctr<priorities.length; utctr++) {
                if(enemyType == priorities[utctr]) {
                    hval+=10*utctr;
                    break;
                }
            }
            heuristics[i][0] = hval;
            heuristics[i][1] = i;
        }
        java.util.Arrays.sort(heuristics, new java.util.Comparator<int[]>() { //sort by heuristic
            public int compare(int[] a, int[] b) {
                return b[0] - a[0];
            }
        });
        for(int i=0; i<heuristics.length; i++) {            
            if(gc.canAttack(unit.id(), enemies_in_range.get(heuristics[i][1]).id())) {
                gc.attack(unit.id(), enemies_in_range.get(heuristics[i][1]).id());
                return;
            }
        }        
    }

    //Takes MapLocation and a VecUnit
    //Finds unit from VecUnit closest to MapLocation
    //Typically used to find enemy unit from array closest to your unit
    public static Unit getNearestUnit(MapLocation myloc, VecUnit other_units) {
        Unit nearestUnit = other_units.get(0);
        MapLocation nearloc = nearestUnit.location().mapLocation();
        int mindist = (int)nearloc.distanceSquaredTo(myloc);
        for(int i=1; i<other_units.size(); i++) {
            Unit testUnit = other_units.get(i);
            MapLocation testloc = testUnit.location().mapLocation();
            int testdist = (int)testloc.distanceSquaredTo(myloc);
            if(testdist<mindist) {
                nearestUnit = testUnit;
                nearloc = testloc;
                mindist = testdist;
            }
        }
        return nearestUnit;
    }

    //Attempts to move unit in direction as best as possible
    //Scans 45 degree offsets, then 90
    public static void fuzzyMove(Unit unit, Direction dir) {
        if(!gc.isMoveReady(unit.id()) || dir==Direction.Center)
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

    //Takes a random llocation and builds vector fields
    //random_distance_field tells you how far from current path destination
    //random_movement_field gives ArrayList of equally optimal Directions to move in
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

    //Moves unit on vector field
    //Should be used if no enemies in sight
    //If no optimal move is available (all blocked), unit will attempt fuzzymove in last dir
    public static void moveOnVectorField(Unit unit, MapLocation mapLocation) {
        if(!gc.isMoveReady(unit.id())) //checks if can move
            return;
        UnitType myUnitType = unit.unitType();
        int x = mapLocation.getX();
        int y = mapLocation.getY();
        for(int movedir=0; movedir<movement_field[x][y].size(); movedir++) { //loops over all possible move directions
            Direction dir = movement_field[x][y].get(movedir);
            if(dir == Direction.Center) { //refactors vector field if reaches enemy start location
                for(int eloc=0; eloc<enemy_locations.size(); eloc++) {
                    int[] elocinfo = enemy_locations.get(eloc);
                    if(x==elocinfo[0] && y==elocinfo[1]) {
                        enemy_locations.remove(eloc);
                        break;
                    }
                }
                if(enemy_locations.size()==0) { //add more targets
                    updateEnemies();
                }
                buildFieldBFS();
                moveOnVectorField(unit, mapLocation);
                return;                            
            }
            else if(movedir==movement_field[x][y].size()-1) { //fuzzy moves last possible direction
                fuzzyMove(unit, dir);
                return;
            }
            else if(gc.canMove(unit.id(), dir)) { //verifies can move in selected direction
                gc.moveRobot(unit.id(), dir);
                return;
            }
        }                        
    }

    //Takes an arraylist of starting enemy locations and builds vector fields
    //distance_field tells you how far from current path destination
    //movement_field gives ArrayList of equally optimal Directions to move in
    public static void buildFieldBFS() {
        Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, 
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};

        Queue<int[]> queue = new LinkedList<int[]>();
        for(int i=0; i<enemy_locations.size(); i++)
            queue.add(enemy_locations.get(i));
        for(int w=0; w<width; w++) {
            for(int h=0; h<height; h++) {
                distance_field[w][h] = (50*50+1);
                movement_field[w][h] = new ArrayList<Direction>();                
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