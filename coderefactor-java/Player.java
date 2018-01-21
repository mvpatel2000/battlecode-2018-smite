//https://s3.amazonaws.com/battlecode-2018/logs/matchnumber_0.bc18log

import bc.*;
import java.util.*;

public class Player {

    //Stuff from game/api
    public static GameController gc;
    public static Team ally;
    public static Team enemy;
    public static Planet myPlanet;
    public static PlanetMap map;
    public static PlanetMap mars_map;
    public static int width;
    public static int height;
    public static int mars_width;
    public static int mars_height;
    public static int current_round;

    //Stuff we create
	public static ArrayList<int[]> karbonite_locations;
    public static ArrayList<int[]> enemy_locations;
    public static int[][] distance_field;
    public static ArrayList<Direction>[][] movement_field;
    public static int[][] random_distance_field;
    public static ArrayList<Direction>[][] random_movement_field;
    public static ArrayList<int[]> enemy_buildings;
    public static boolean canSnipe;
    public static boolean doesPathExist;
    public static int[][] mars_landing;
    public static int landing_spaces;
    public static int rocket_homing = 0;
	public static ArrayList<KarbonitePath> karbonite_path;
	public static Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};


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
        int initial_workers = 0; //TODO: Make global?

        if(myPlanet==Planet.Earth) { //generate landing priorities for rockets
            generateLandingPriorities();
        }

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
                initial_workers+=1;
                MapLocation ally_location = unit.location().mapLocation();
                if(distance_field[ally_location.getX()][ally_location.getY()]<50*50+1) {
                    doesPathExist = true;
                    break;
                }
            }
        }

        if(doesPathExist==false) {
            UnitType[] rarray = {UnitType.Worker, UnitType.Rocket, UnitType.Rocket, UnitType.Rocket, UnitType.Ranger, 
                                    UnitType.Ranger, UnitType.Ranger, UnitType.Worker, UnitType.Worker, UnitType.Worker}; //research queue
            for(int i=0; i<rarray.length; i++)
                gc.queueResearch(rarray[i]);
        }
        else {
            UnitType[] rarray = {UnitType.Worker, UnitType.Ranger, UnitType.Ranger, UnitType.Ranger, UnitType.Rocket, UnitType.Rocket,
                                    UnitType.Rocket, UnitType.Worker, UnitType.Worker, UnitType.Worker}; //research queue
            for(int i=0; i<rarray.length; i++)
                gc.queueResearch(rarray[i]);
        }        
        
        canSnipe = false;

        current_round = 0;
        rocket_homing = 0; //are rockets built

        int current_workers=initial_workers; //TODO: make global?
        int num_factories = 0;
        int num_rockets = 0;
        int minworkers=initial_workers*24; //replicate each dude *4 before creating factories

        //TODO: optimize how we go thorugh units (toposort?)
        //TODO: if enemy dead, build rockets??        
        while (true) {
            current_round = (int)gc.round();
            int factories_active = 0; //tracks amount of factories producing units
            if(current_round%50==0) { //System.out.print();rint round number and update random field
                System.out.println("Current round: "+current_round+" Current time: "+gc.getTimeLeftMs());
                buildRandomField();
            }
            if(canSnipe==false && current_round>350) {//activate snipe
                canSnipe = true;
                enemy_buildings = new ArrayList<int[]>();
            }
            if(canSnipe) //build snipe targets
                buildSnipeTargets();
			if(current_round == 1 || (current_round % 20 == 0 && current_round < 750)) {
				karbonite_path = karbonitePath(new int[] {0, 20, 50});
			}

            VecUnit units = gc.myUnits();
            for (int unit_counter = 0; unit_counter < units.size(); unit_counter++) {
                Unit unit = units.get(unit_counter);

                //TODO:
                // - update factory function based on karbonite levels
                // - worker replication late game for pure harvesting / navigation
                if(unit.unitType()==UnitType.Worker && !unit.location().isInGarrison() && !unit.location().isInSpace()) {
					int x = unit.location().mapLocation().getX();
					int y = unit.location().mapLocation().getY();
					int value = -100000000;
					int amount;
					Direction toKarb = Direction.Center;
					int distance;
					System.out.println(karbonite_path);
					for(KarbonitePath k : karbonite_path) {
						int my_value = k.amount_field[x][y]-k.distance_field[x][y]*4;
						if(my_value > value) {
							value = my_value;
							amount = k.amount_field[x][y];
							distance = k.distance_field[x][y];
							toKarb = k.movement_field[x][y];
						}
					}
					if(toKarb == null || value < -10000000) {
						toKarb = dirs[1+(int)(Math.random()*8)];
					}
                    if(current_workers>=minworkers && myPlanet==Planet.Earth) {
                        //execute build order
                        if(buildRocket(unit, toKarb, units, 20l)==true) {
                            continue;
                        }
                        else if(buildFactory(unit, toKarb, units, 20l)==true){
                            continue;
                        }
                        else {
                            if(current_round>450 || doesPathExist==false && current_round>125) { //rocket cap
                                //blueprint rocket or (replicate or moveharvest)
                                int val = blueprintRocket(unit, toKarb, units, 20l);
                                if(val>=2) { //if blueprintRocket degenerates to replicateOrMoveHarvest()
                                    current_workers+=(val-2);
                                } else { //did not degenerate
                                    num_rockets+=val;
                                }
                            }
                            else if(num_factories<4 || (width>25 && (gc.karbonite()>200+(50-width))) || (doesPathExist==false && num_factories<1)) { //factory cap
                                //blueprint factory or (replicate or moveharvest)
                                int val = blueprintFactory(unit, toKarb, units, 20l);
                                if(val>=2) { //if blueprintFactory degenerates to replicateOrMoveHarvest()
                                    current_workers+=(val-2);
                                } else { //did not degenerate
                                    num_factories+=val;
                                }
                            }
                            else {
                                workerharvest(unit, toKarb);
                                workermove(unit, toKarb);
                            }
                        }
                    } else {
                        //replicate or move harvest
                        current_workers += replicateOrMoveHarvest(unit, toKarb);
                    }
                }

                //TODO: Giev rolling fire with snipetarget
                else if(unit.unitType()==UnitType.Ranger && !unit.location().isInGarrison() && !unit.location().isInSpace() && unit.rangerIsSniping()==0) {
                    MapLocation myloc = unit.location().mapLocation();
                    VecUnit enemies_in_sight = gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), enemy);
                    if(enemies_in_sight.size()>0) {      //combat state
                        if(enemy_locations.size()==0) { //add enemy locations
                            updateEnemies();
                        }
                        checkVectorField(unit, myloc);
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
                                Direction toMoveDir = getNearestNonWorkerDirection(myloc, enemies_in_range);
                                fuzzyMove(unit, toMoveDir);
                            }
                        }
                    }
                    else { //non-combat state
                        if( (doesPathExist==false && rocket_homing==0) || enemy_locations.size()==0) {
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

                //TODO: Heal weakest unit
                else if(unit.unitType()==UnitType.Healer && !unit.location().isInGarrison() && !unit.location().isInSpace()) {
                    MapLocation myloc = unit.location().mapLocation();
                    VecUnit enemies_in_range = gc.senseNearbyUnitsByTeam(myloc, 70L, enemy);
                    if(enemies_in_range.size()>0) {      //combat state
                        Direction toMoveDir = getNearestNonWorkerDirection(myloc, enemies_in_range);
                        fuzzyMove(unit, toMoveDir);
                    }
                    else { //non-combat state
                        moveOnVectorField(unit, myloc);
                    }
                    healUnit(unit, myloc);
                }

                //TODO: Heuristic to shut off production
                //TODO: Build workers late for rockets
                else if(unit.unitType()==UnitType.Factory) {
                    factories_active++;
                    if(gc.canProduceRobot(unit.id(), UnitType.Ranger) && //Autochecks if queue empty
                        (current_round<601 || current_round>600 && factories_active<3) && //only 2 factories after round 600
                        (current_round<700 || current_round<600 && doesPathExist==false)) {  //no production in final rounds
                        gc.produceRobot(unit.id(),UnitType.Ranger);
                    }
                    Direction unload_dir = Direction.East;
                    if(enemy_locations.size()>0) {
                        int[] enemy_direction = enemy_locations.get(0);
                        unload_dir = unit.location().mapLocation().directionTo(new MapLocation(myPlanet, enemy_direction[0], enemy_direction[1]));
                    }
                    fuzzyUnload(unit, unload_dir);
                }

                //TODO: imrpove land priority to accuont for nearby karbonite in decimal place
                else if(unit.unitType()==UnitType.Rocket && !unit.location().isInSpace() && unit.structureIsBuilt()!=0) {
                    Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.West,
                                        Direction.Southwest, Direction.South, Direction.Southeast};
                    MapLocation myloc = unit.location().mapLocation();
                    if(myPlanet==Planet.Earth) { //on earth load/lift
                        addRocketLocation(unit, myloc);
                        VecUnit allies_to_load = gc.senseNearbyUnitsByTeam(myloc, 2, ally);
                        VecUnitID garrison = unit.structureGarrison();
                        int maxcapacity = (int)unit.structureMaxCapacity();
                        int num_in_garrison = (int)garrison.size();
                        int allyctr = 0;
                        while(maxcapacity>num_in_garrison && allyctr<allies_to_load.size()) { //load all units while space
                            Unit ally_to_load = allies_to_load.get(allyctr);
                            if(gc.canLoad(unit.id(), ally_to_load.id())) {
                                gc.load(unit.id(), ally_to_load.id());
                                num_in_garrison++;
                            }
                            allyctr++;
                        }
                        if(shouldLaunchRocket(unit, myloc, num_in_garrison, maxcapacity)) { //launch
                            launchRocket(unit);
                            removeRocketLocation(unit, myloc);
                            System.out.println("Rocket launched");
                        }
                    }
                    else if(myPlanet==Planet.Mars) { //unload everything ASAP on Mars
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

    //***********************************************************************************//
    //********************************** HEALER METHODS *********************************//
    //***********************************************************************************//

    //heal lowest hp unit in range
    public static void healUnit(Unit unit, MapLocation myloc) {
        if(!gc.isHealReady(unit.id()))
            return;
        VecUnit allies_in_range = gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), ally);
        if(allies_in_range.size()==0)
            return;
        Unit ally_to_heal = allies_in_range.get(0);
        int ally_health = (int)ally_to_heal.health();
        for(int i=1; i<allies_in_range.size(); i++) {
            Unit test_ally = allies_in_range.get(i);
            int test_health = (int)test_ally.health();
            if(test_health<ally_health) {
                ally_to_heal = test_ally;
                ally_health = test_health;
            }
        }
        if(gc.canHeal(unit.id(), ally_to_heal.id()))
            gc.heal(unit.id(), ally_to_heal.id());
    }

    //***********************************************************************************//
    //********************************** WORKER METHODS *********************************//
    //***********************************************************************************//

    //count number of karbonites on map initially
    public static long countKarbonite() {
        long totalkarb = 0L;
        for (int i=0; i<width; i++) {
            for(int j=0; j<width; j++) {
                totalkarb += map.initialKarboniteAt(new MapLocation(myPlanet, i,j));
            }
        }
        return totalkarb;
    }
    //Only called when no factories are within range
    //Blueprint a factory ONLY if there are 2+ workers within range (long rad). In this case, return 1
    //Else, replicate (or moveHarvest, if replication not possible).
    // Return 2(if moveharvest) or 3(if replication succesful)
    public static int blueprintRocket(Unit unit, Direction toKarb, VecUnit units, long rad) {
        MapLocation myLoc = unit.location().mapLocation();
        ArrayList<Unit> closeWorkers = nearbyWorkersRocket(unit, myLoc, rad);
        if(closeWorkers.size()>2) { //includes the original worker, we want three workers per factory
            Direction blueprintDirection = optimalDirectionRocket(unit, myLoc, closeWorkers);
            if(blueprintDirection!=null) {
                gc.blueprint(unit.id(), UnitType.Rocket, blueprintDirection);
                return 1;
            } else {
                //cannot build blueprint
                workerharvest(unit, toKarb);
                workermove(unit, toKarb);
                return 0;
            }
        } else {
            //not enough close workers
            return (2+replicateOrMoveHarvest(unit, toKarb)); //2+ lets parent method determine whether we replicated or not
        }
    }

    //Helper Method for blueprintFactory: Determine nearbyWorkers (includes myUnit)
    public static ArrayList<Unit> nearbyWorkersRocket(Unit myUnit, MapLocation myLoc, long rad) {
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
    public static Direction optimalDirectionRocket(Unit myUnit, MapLocation myLoc, ArrayList<Unit> closeWorkers) {
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        long shortestdist = 10000000000000L;
        Direction bestdir=null;
        for (Direction dir: dirs) {
            if(gc.canBlueprint(myUnit.id(), UnitType.Rocket, dir)) {
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

    //If a factory is within range, and it is not at maximum health, then build it
    //If you cannot build it because it needs repairing, repair it
    //If you cannot do either, harvest+move towards the factory, since you are out of range
    //If either of these three above scenarious occur, return true
    //If there are no factories within range, then return false
    public static boolean buildRocket(Unit unit, Direction toKarb, VecUnit units, long rad) {
        VecUnit nearbyRockets = gc.senseNearbyUnitsByType(unit.location().mapLocation(), rad, UnitType.Rocket);

        for(int i=0; i<nearbyRockets.size(); i++) {
            Unit k = nearbyRockets.get(i);
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
                    workerharvest(unit, toKarb);
                    Direction toRocket = unit.location().mapLocation().directionTo(nearbyRockets.get(0).location().mapLocation());
                    fuzzyMove(unit, toRocket);
                    return true;
                }
            }
        }
        return false;
    }

    //Only called when no factories are within range
    //Blueprint a factory ONLY if there are 2+ workers within range (long rad). In this case, return 1
    //Else, replicate (or moveHarvest, if replication not possible).
    // Return 2(if moveharvest) or 3(if replication succesful)
    public static int blueprintFactory(Unit unit, Direction toKarb, VecUnit units, long rad) {
        MapLocation myLoc = unit.location().mapLocation();
        ArrayList<Unit> closeWorkers = nearbyWorkersFactory(unit, myLoc, rad);
        if(closeWorkers.size()>2) { //includes the original worker, we want three workers per factory
            Direction blueprintDirection = optimalDirectionFactory(unit, myLoc, closeWorkers);
            if(blueprintDirection!=null) {
                gc.blueprint(unit.id(), UnitType.Factory, blueprintDirection);
                return 1;
            } else {
                //cannot build blueprint
                workerharvest(unit, toKarb);
                workermove(unit, toKarb);
                return 0;
            }
        } else {
            //not enough close workers
            return (2+replicateOrMoveHarvest(unit, toKarb)); //2+ lets parent method determine whether we replicated or not
        }
    }

    //Helper Method for blueprintFactory: Determine nearbyWorkers (includes myUnit)
    public static ArrayList<Unit> nearbyWorkersFactory(Unit myUnit, MapLocation myLoc, long rad) {
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
    public static Direction optimalDirectionFactory(Unit myUnit, MapLocation myLoc, ArrayList<Unit> closeWorkers) {
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
    public static int replicateOrMoveHarvest(Unit unit, Direction toKarb) {
        if(gc.canReplicate(unit.id(), toKarb)) {
            gc.replicate(unit.id(), toKarb);
            return 1;
        }
        workerharvest(unit, toKarb);
        workermove(unit, toKarb);
        return 0;
    }

    //If a factory is within range, and it is not at maximum health, then build it
    //If you cannot build it because it needs repairing, repair it
    //If you cannot do either, harvest+move towards the factory, since you are out of range
    //If either of these three above scenarious occur, return true
    //If there are no factories within range, then return false
    public static boolean buildFactory(Unit unit, Direction toKarb, VecUnit units, long rad) {
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
                    workerharvest(unit, toKarb);
                    Direction toFactory = unit.location().mapLocation().directionTo(nearbyFactories.get(0).location().mapLocation());
                    fuzzyMove(unit, toFactory);
                    return true;
                }
            }
        }
        return false;
    }

    //harvest in the optimal direction
    public static void workerharvest(Unit unit, Direction toKarb) {
        MapLocation myLoc = unit.location().mapLocation();
		MapLocation newLoc = myLoc.add(toKarb);
		if(gc.karboniteAt(newLoc)>0L) {
			if(gc.canHarvest(unit.id(), toKarb)){
				gc.harvest(unit.id(), toKarb);
				return;
			}
		}
    }

    public static void workermove(Unit unit, Direction toKarb) {
        MapLocation myLoc = unit.location().mapLocation();
		MapLocation newLoc = myLoc.add(toKarb);
		if(gc.canMove(unit.id(), toKarb)) {
			if(gc.isMoveReady(unit.id())) {
				gc.moveRobot(unit.id(), toKarb);
			}
		} else {
			fuzzyMove(unit, toKarb);
		}
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

    //***********************************************************************************//
    //********************************** ROCKET METHODS *********************************//
    //***********************************************************************************//

    //check if rocket launch conditions are met
    //max garrison, about to die, or turn 749
    public static boolean shouldLaunchRocket(Unit unit, MapLocation myloc, int num_in_garrison, int maxcapacity) {
        if(num_in_garrison==maxcapacity)
            return true;        
        if(current_round>=749)
            return true;
        int hp = (int)unit.health();
        VecUnit enemies_in_range = gc.senseNearbyUnitsByTeam(myloc, 70L, enemy);
        for(int i=0; i<enemies_in_range.size(); i++) {
            Unit enem = enemies_in_range.get(i);
            int dist = (int)enem.location().mapLocation().distanceSquaredTo(myloc);
            if((int)enem.attackHeat()-10<10 && enem.attackRange()>dist) { //can do damage
                hp -= enem.damage();
                if(hp<=0)
                    return true;
            }
        }
        return false;
    }

    //removes rocket location to enemy_locations
    //this does to stop homing towards rockets
    public static void removeRocketLocation(Unit unit, MapLocation myloc) {
        int x = myloc.getX();
        int y = myloc.getY();
        for(int i=0; i<enemy_locations.size(); i++) { //search through list
            int[] enem_loc = enemy_locations.get(i);
            if(x==enem_loc[0] && y==enem_loc[1]) {
                enemy_locations.remove(i);
                rocket_homing--;
                return;
            }
        }
    }

    //adds rocket location to enemy_locations if not added
    //this does homing towards rockets
    public static void addRocketLocation(Unit unit, MapLocation myloc) {
        int x = myloc.getX();
        int y = myloc.getY();
        boolean addLocation = true;
        for(int i=0; i<enemy_locations.size(); i++) { //add rocket to enemy list if not there already
            int[] enem_loc = enemy_locations.get(i);
            if(x==enem_loc[0] && y==enem_loc[1]) {
                addLocation = false;
                break;
            }
        }
        if(addLocation) {
            int[] rocket_loc = {x, y, 0, 0};
            enemy_locations.add(rocket_loc);
            rocket_homing++;
            buildFieldBFS();
        }
    }

    //launches rocket based on precomputed space
    public static void launchRocket(Unit unit) {
        if(landing_spaces<0) //no available spaces left
            return;
        for(int w=0; w<mars_width; w++) {
            for(int h=0; h<mars_height; h++) {
                if(mars_landing[w][h]==landing_spaces) { //one of the max space squares
                    if(gc.canLaunchRocket(unit.id(), new MapLocation(Planet.Mars, w, h))) {
                        gc.launchRocket(unit.id(), new MapLocation(Planet.Mars, w, h)); //launch rocket
                        int[] shifts = {-2, -1, 0, 1, 2}; //update available squares
                        for(int xsi=0; xsi<shifts.length; xsi++) {
                            for(int ysi=0; ysi<shifts.length; ysi++) {
                                int shifted_x = w+shifts[xsi];
                                int shifted_y = h+shifts[ysi];
                                if(shifted_x>=0 && shifted_x<mars_width && shifted_y>=0 && shifted_y<mars_height) {
                                    if(xsi>0 && xsi<4 && ysi>0 && ysi<4)
                                        mars_landing[shifted_x][shifted_y]=-1;
                                    else
                                        mars_landing[shifted_x][shifted_y]--;
                                }
                            }
                        }
                    }
                    return;
                }
            }
        }
        landing_spaces--;
        launchRocket(unit);
    }

    //generates count of open adjacent spaces for locations on mars
    //used to land rockets
    public static void generateLandingPriorities() {
        mars_map = gc.startingMap(Planet.Mars);
        mars_width = (int)mars_map.getWidth();
        mars_height = (int)mars_map.getHeight();
        mars_landing = new int[mars_width][mars_height];
        for(int w=0; w<mars_width; w++) //default initialization
            for(int h=0; h<mars_height; h++)
                mars_landing[w][h] = 8;
        for(int w=0; w<mars_width; w++) { //correct for borders horizontally
            mars_landing[w][0]--;
            mars_landing[w][mars_height-1]--;
        }
        for(int h=0; h<mars_height; h++) { //correct for borders vertically
            mars_landing[0][h]--;
            mars_landing[mars_width-1][h]--;
        }
        int[] shifts = {-1, 0, 1};
        for(int w=0; w<mars_width; w++) {
            for(int h=0; h<mars_height; h++) {
                if(mars_map.isPassableTerrainAt(new MapLocation(Planet.Mars, w, h))==0) { //not passable
                    for(int xsi=0; xsi<3; xsi++) {
                        for(int ysi=0; ysi<3; ysi++) {
                            int shifted_x = w+shifts[xsi];
                            int shifted_y = h+shifts[ysi];
                            if(shifted_x>=0 && shifted_x<mars_width && shifted_y>=0 && shifted_y<mars_height)
                                mars_landing[shifted_x][shifted_y]--;
                        }
                    }
                    mars_landing[w][h] = -1;
                }
            }
        }
        landing_spaces = -1;
        for(int w=0; w<mars_width; w++)
            for(int h=0; h<mars_height; h++)
                if(mars_landing[w][h]>landing_spaces)
                    landing_spaces = mars_landing[w][h];
    }

    //***********************************************************************************//
    //********************************** RANGER METHODS *********************************//
    //***********************************************************************************//

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
                    hval+=10*utctr; //later units have higher priorities because weight based on index
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

    //***********************************************************************************//
    //******************************* GENERAL UNIT METHODS ******************************//
    //***********************************************************************************//

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

    //Takes MapLocation and a VecUnit
    //Finds unit from VecUnit closest to MapLocation and returns direction
    //Returns towards unit if only workers 
    public static Direction getNearestNonWorkerDirection(MapLocation myloc, VecUnit other_units) {
        Unit nearestUnit = null;
        MapLocation nearloc = null;
        int mindist = 50*50+1;
        for(int i=0; i<other_units.size(); i++) {
            Unit testUnit = other_units.get(i);
            if(testUnit.unitType()!=UnitType.Worker) {
                MapLocation testloc = testUnit.location().mapLocation();
                int testdist = (int)testloc.distanceSquaredTo(myloc);
                if(testdist<mindist) {
                    nearestUnit = testUnit;
                    nearloc = testloc;
                    mindist = testdist;
                }
            }
        }
        if(nearestUnit!=null)
            return nearloc.directionTo(myloc);
        nearestUnit = null;
        nearloc = null;
        mindist = 50*50+1;
        for(int i=0; i<other_units.size(); i++) {
            Unit testUnit = other_units.get(i);
            MapLocation testloc = testUnit.location().mapLocation();
            int testdist = (int)testloc.distanceSquaredTo(myloc);
            if(testdist<mindist) {
                nearestUnit = testUnit;
                nearloc = testloc;
                mindist = testdist;
            }
        }
        return myloc.directionTo(nearloc);
    }    

    //***********************************************************************************//
    //***************************** GENERAL BUILDING METHODS ****************************//
    //***********************************************************************************//

    //Attempts to unload unit in direction as best as possible from factory
    public static void fuzzyUnload(Unit unit, Direction dir) {
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

    //***********************************************************************************//
    //*********************************** PATHFINDING ***********************************//
    //***********************************************************************************//

    //Attempts to move unit in direction as best as possible
    //Scans 45 degree offsets, then 90
    public static void fuzzyMove(Unit unit, Direction dir) {
        if(!gc.isMoveReady(unit.id()) || dir==Direction.Center)
            return;
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
            if(dir == Direction.Center) { //reruns vector field if reaches enemy start location
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

    //Checks if current location is a destination in vector field
    //Used when in combat mode to verify not blocked into enemy initial location
    public static void checkVectorField(Unit unit, MapLocation mapLocation) {
        UnitType myUnitType = unit.unitType();
        int x = mapLocation.getX();
        int y = mapLocation.getY();
        if(movement_field[x][y].size()==1 && movement_field[x][y].get(0)==Direction.Center) {
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

    //***********************************************************************************//
    //********************************** OTHER CLASSES **********************************//
    //***********************************************************************************//

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
	static class KarbonitePath {
		public int[][] distance_field;
		public int[][] amount_field;
		public Direction[][] movement_field;
		public KarbonitePath(int[][] amount_field, int[][] distance_field, Direction[][] movement_field) {
			this.distance_field = distance_field;
			this.movement_field = movement_field;
			this.amount_field = amount_field;
		}
	}

	public static ArrayList<KarbonitePath> karbonitePath(int[] buckets) {
		ArrayList<KarbonitePath> R = new ArrayList<>();
		Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
		int[][] k = new int[51][51];
		for(int x=0; x<width; x++)
			for(int y=0; y<height; y++) {
				MapLocation m = new MapLocation(myPlanet, x, y);
				if(gc.canSenseLocation(m))
					k[x][y] = (int)gc.karboniteAt(m);
			}
		for(int bucket : buckets) {


			Queue<int[]> queue = new LinkedList<int[]>();
			int[][] distance_field = new int[51][51];
			Direction[][] movement_field = new Direction[51][51];
			int[][] amount_field = new int[51][51];
			for(int x=0; x<width; x++)
				for(int y=0; y<height; y++) {
					distance_field[x][y] = 50*50+1;
					if(k[x][y] > bucket) {
						int[] j = {x, y, 0, 0, k[x][y]};
						queue.add(j);
					}
				}

			while(queue.peek()!=null) {
				int[] lcc = queue.poll();
				int x = lcc[0];
				int y = lcc[1];
				int dir = lcc[2];
				int depth = lcc[3];
				int amount = lcc[4];

				if(x<0 || y<0 || x>=width || y>=height ||  //border checks
						map.isPassableTerrainAt(new MapLocation(myPlanet, x, y))==0 || //is not passable
						distance_field[x][y]<=depth) { //is an inferior move
					continue;
				}
				else if(distance_field[x][y]>depth) { //replace old Directions with more optimal ones
					distance_field[x][y] = depth;
					movement_field[x][y] = dirs[dir];
					amount_field[x][y] = amount;
					int[] lc2 = {x+1,y,  5,depth+1,amount};
					queue.add(lc2);
					int[] lc3 = {x+1,y+1,6,depth+1,amount};
					queue.add(lc3);
					int[] lc4 = {x,y+1,  7,depth+1,amount};
					queue.add(lc4);
					int[] lc5 = {x-1,y+1,8,depth+1,amount};
					queue.add(lc5);
					int[] lc6 = {x-1,y,  1,depth+1,amount};
					queue.add(lc6);
					int[] lc7 = {x-1,y-1,2,depth+1,amount};
					queue.add(lc7);
					int[] lc8 = {x,y-1,  3,depth+1,amount};
					queue.add(lc8);
					int[] lc9 = {x+1,y-1,4,depth+1,amount};
				}
			}
			R.add(new KarbonitePath(amount_field, distance_field, movement_field));
		}
		return R;
	}
}
