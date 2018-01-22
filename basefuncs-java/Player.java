//https://s3.amazonaws.com/battlecode-2018/logs/matchnumber_0.bc18log

import bc.*;
import java.util.*;

public class Player {

    //Stuff from game/api
    public static GameController gc = new GameController();;
    public static Team ally = gc.team();
    public static Team enemy;
    public static Planet myPlanet = gc.planet();
    public static PlanetMap map = gc.startingMap(myPlanet);;
    public static PlanetMap mars_map = gc.startingMap(Planet.Mars);
    public static int width = (int)map.getWidth();;
    public static int height = (int)map.getHeight();;
    public static int mars_width = (int)mars_map.getWidth();
    public static int mars_height = (int)mars_map.getHeight();
    public static int initial_workers = 0;
    public static int current_round = 0;
    public static AsteroidPattern asteroid_pattern = gc.asteroidPattern();

    //Stuff we create
    public static ArrayList<int[]> enemy_locations = new ArrayList<int[]>(); //starting enemy location queue for generating vector field
    public static int[][] distance_field = new int[width][height];
    public static ArrayList<Direction>[][] movement_field = new ArrayList[width][height];
    public static int[][] random_distance_field = new int[width][height]; //generate random movement field
    public static ArrayList<Direction>[][] random_movement_field = new ArrayList[width][height];
    public static ArrayList<int[]> enemy_buildings = new ArrayList<int[]>();
    public static boolean doesPathExist = false; //determine if a path exists
    public static double[][] mars_landing = new double[mars_width][mars_height];
    public static int rocket_homing = 0; //are rockets built / how many
    public static int minworkers = 0;
    public static int factories_active = 0;
    public static int nikhil_num_workers = 0;

    public static int num_factories = 0;
    public static int num_rockets = 0;
    public static int num_workers = 0;
    public static int num_rangers = 0;
    public static int num_knights = 0;
    public static int num_healers = 0;
    public static int total_workers = 0;
    public static int total_rangers = 0;
    public static int total_knights = 0;
    public static int total_healers = 0;    

    //Constants
    public static final long maxAttackRange = 50L;
    public static final long maxVisionRange = 100L;

    public static void main(String[] args) {

        enemy = Team.Red;   //this is evil team
        if(ally==Team.Red)
            enemy = Team.Blue;
              
        if(myPlanet==Planet.Earth) { //generate landing priorities for rockets
            generateLandingPriorities();            
        }
        
        VecUnit initial_units = map.getInitial_units();
        for(int i=0; i<initial_units.size(); i++) { //initial units
            Unit unit = initial_units.get(i);
            if(ally!=unit.team()) {
                MapLocation enemy_location = unit.location().mapLocation();
                int[] enemy_info = {enemy_location.getX(), enemy_location.getY(), 0, 0};
                enemy_locations.add(enemy_info);
            }
        }
        
        buildFieldBFS();       //pathing
        buildRandomField();
       
        for(int i=0; i<initial_units.size(); i++) { //verify pathing connectivity
            Unit unit = initial_units.get(i);
            if(ally==unit.team()) {
                nikhil_num_workers+=1;
                MapLocation ally_location = unit.location().mapLocation();
                if(distance_field[ally_location.getX()][ally_location.getY()]<50*50+1) {
                    doesPathExist = true;
                    break;
                }
            }
        }

        if(doesPathExist==false) { //research
            UnitType[] rarray = {UnitType.Worker, UnitType.Rocket, UnitType.Rocket, UnitType.Rocket, UnitType.Ranger, 
                                    UnitType.Ranger, UnitType.Ranger, UnitType.Healer, UnitType.Healer, UnitType.Healer}; //research queue
            for(int i=0; i<rarray.length; i++)
                gc.queueResearch(rarray[i]);
        }
        else {
            UnitType[] rarray = {UnitType.Worker, UnitType.Healer, UnitType.Healer, UnitType.Healer, UnitType.Rocket, UnitType.Rocket,
                                    UnitType.Rocket, UnitType.Ranger, UnitType.Ranger, UnitType.Mage}; //research queue
            for(int i=0; i<rarray.length; i++)
                gc.queueResearch(rarray[i]);
        }                            

        minworkers=nikhil_num_workers*16; //write a method that does this better

        //TODO: Sort so rangers > healers > factories > workers
        //TODO: if enemy dead, build rockets??        
        while (true) {
            current_round = (int)gc.round();            
            factories_active = 0; //tracks amount of factories producing units
            if(current_round%20==0) { //print round number and update random field
                System.out.println("Current round: "+current_round+" Current time: "+gc.getTimeLeftMs());
                System.runFinalization();
                System.gc();
                buildRandomField();
            }
            if(myPlanet==Planet.Earth)
                updateLandingPriorities();
            buildSnipeTargets(); //build snipe targets

            VecUnit units = gc.myUnits();
            num_rangers = 0;
            num_healers = 0;
            num_knights = 0;
            num_workers = 0;
            for(int i=0; i<units.size(); i++) { //Updates num_units. Anything not written here is treated differently and should not be added!!!
                UnitType unit_type = units.get(i).unitType();
                if(unit_type==UnitType.Ranger)
                    num_rangers++;
                else if(unit_type==UnitType.Healer)
                    num_healers++;
                else if(unit_type==UnitType.Knight)
                    num_knights++;
                else if(unit_type==UnitType.Worker)
                    num_workers++;
            }
            total_rangers+=num_rangers;
            total_healers+=num_healers;
            total_knights+=num_knights;
            total_workers+=num_workers;
            for (int unit_counter = 0; unit_counter < units.size(); unit_counter++) {                
                Unit unit = units.get(unit_counter); 
                if(unit.location().isInGarrison() || unit.location().isInSpace())
                    continue; 
                MapLocation myloc = unit.location().mapLocation();              

                // WORKER CODE //
                //TODO:
                // - update factory function based on karbonite levels / size of map USE DISTANCE FIELD!
                // - tune worker ratio! account for more costly replication
                if(unit.unitType()==UnitType.Worker) {
                    ArrayList<KarbDir> mykarbs = karboniteSort(unit, unit.location());
                    if(nikhil_num_workers>=minworkers && myPlanet==Planet.Earth) {
                        //execute build order
                        if(buildRocket(unit, mykarbs, units, 20l)==true) {
                            continue;
                        }
                        else if(buildFactory(unit, mykarbs, units, 20l)==true){
                            continue;
                        }
                        else {
                            if(current_round>450 || doesPathExist==false && current_round>125) { //rocket cap
                                //blueprint rocket or (replicate or moveharvest)
                                int val = blueprintRocket(unit, mykarbs, units, 20l);
                                if(val>=2) { //if blueprintRocket degenerates to replicateOrMoveHarvest()
                                    nikhil_num_workers+=(val-2);
                                } else { //did not degenerate
                                    num_rockets+=val;
                                }
                            }
                            else if( (doesPathExist && num_factories<4) || (doesPathExist && width>25 && (gc.karbonite()>200+(50-width))) || (!doesPathExist && num_factories<1)) { //factory cap
                                //blueprint factory or (replicate or moveharvest)
                                int val = blueprintFactory(unit, mykarbs, units, 20l);
                                if(val>=2) { //if blueprintFactory degenerates to replicateOrMoveHarvest()
                                    nikhil_num_workers+=(val-2);
                                } else { //did not degenerate
                                    num_factories+=val;
                                }
                            }
                            else {
                                workerharvest(unit, mykarbs);
                                workermove(unit, mykarbs);
                            }
                        }
                    } else {
                        //replicate or move harvest
                        nikhil_num_workers += replicateOrMoveHarvest(unit, mykarbs);
                    }
                }

                // RANGER CODE //
                //TODO: Give rolling fire with snipetarget?
                //TODO: Charge mechanic
                //TODO: make rangerAttack not a sort
                else if(unit.unitType()==UnitType.Ranger && unit.rangerIsSniping()==0) {                    
                    runRanger(unit, myloc);
                }

                // KNIGHT CODE //
                //TODO: update movement method priority
                //TODO: Move towards better enemy
                //TODO: Figure javelin
                else if(unit.unitType()==UnitType.Knight) {
                    runKnight(unit, myloc);
                }

                // MAGE CODE //
                //TODO: Update Mage attack
                //TODO: Update movement method priority
                //TODO: move in a better way
                //TODO: Figure out blink
                else if(unit.unitType()==UnitType.Mage) {
                    runMage(unit, myloc);
                }

                // HEALER CODE //
                //TODO: Verify overcharge
                else if(unit.unitType()==UnitType.Healer) {                    
                    runHealer(unit, myloc);
                }

                // FACTORY CODE //
                //TODO: Heuristic to shut off production
                else if(unit.unitType()==UnitType.Factory && unit.structureIsBuilt()!=0) {
                    runFactory(unit, myloc);
                }

                // ROCKET CODE //
                //TODO: make units go away from rocket b4 launch
                else if(unit.unitType()==UnitType.Rocket && unit.structureIsBuilt()!=0) {
                    runRocket(unit, myloc);
                }
            }

            gc.nextTurn(); // Submit the actions we've done, and wait for our next turn.
        }
    }

    //***********************************************************************************//
    //********************************** FACTORY METHODS ********************************//
    //***********************************************************************************//

    public static void runFactory(Unit unit, MapLocation myloc) {
        factories_active++;
        if( (current_round<601 || current_round>600 && factories_active<3) && //only 2 factories after round 600
            (current_round<700 || current_round<600 && doesPathExist==false)) {  //no production in final rounds
            produceUnit(unit, myloc);
        }
        Direction unload_dir = Direction.East;
        if(enemy_locations.size()>0) {
            int[] enemy_direction = enemy_locations.get(0);
            unload_dir = myloc.directionTo(new MapLocation(myPlanet, enemy_direction[0], enemy_direction[1]));
        }
        fuzzyUnload(unit, unload_dir);
    }

    public static void produceUnit(Unit unit, MapLocation myloc) {
        if(!(gc.canProduceRobot(unit.id(), UnitType.Ranger) && gc.canProduceRobot(unit.id(), UnitType.Healer) && 
            gc.canProduceRobot(unit.id(), UnitType.Knight) && gc.canProduceRobot(unit.id(), UnitType.Mage)))
            return;
        if(total_knights<0)
            gc.produceRobot(unit.id(),UnitType.Knight);
        else if(num_workers<=0 && gc.canProduceRobot(unit.id(), UnitType.Worker))
            gc.produceRobot(unit.id(),UnitType.Worker);
        else if(num_rangers<7)
            gc.produceRobot(unit.id(), UnitType.Ranger);
        else if(num_rangers>30 && (num_rangers)/(1.0*num_healers)>3.0/2.0)
            gc.produceRobot(unit.id(), UnitType.Healer);
        else if((num_rangers-4)/(1.0*num_healers)>2.0/1.0)
            gc.produceRobot(unit.id(), UnitType.Healer);
        else
            gc.produceRobot(unit.id(), UnitType.Ranger);
    }

    //***********************************************************************************//
    //*********************************** MAGE METHODS **********************************//
    //***********************************************************************************//

    public static void runMage(Unit unit, MapLocation myloc) {
        VecUnit enemies_in_sight = gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), enemy);
        if(enemies_in_sight.size()>0) {      //combat state
            Unit nearestUnit = getNearestUnit(myloc, enemies_in_sight); //move in a better fashion
            MapLocation nearloc = nearestUnit.location().mapLocation();
            fuzzyMove(unit, myloc.directionTo(nearloc)); //move in a better way

            VecUnit enemies_in_range = gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), enemy);
            if(enemies_in_range.size()>0) {
                mageAttack(unit, myloc, enemies_in_range);
            }
        }
        else { //non-combat state
            if( (doesPathExist==false && rocket_homing==0) || enemy_locations.size()==0)
                moveOnRandomField(unit, myloc);                        
            else
                moveOnVectorField(unit, myloc);                    
        }
    }

    //1. anything that u can kill
    //2. attack factories then rockets
    //3. anything that can hit u
    //Tiebreaker weakest
    //Tiebreaker again: rangers > mages > healers > knights > workers
    public static void mageAttack(Unit unit, MapLocation myloc, VecUnit enemies_in_range) {
        if(!gc.isAttackReady(unit.id()))
            return;
        int[][] heuristics = new int[(int)enemies_in_range.size()][2];
        for(int i=0; i<enemies_in_range.size(); i++) {
            int hval = 0;
            Unit enemy = enemies_in_range.get(i);
            UnitType enemyType = enemy.unitType();
            int distance = (int)myloc.distanceSquaredTo(enemy.location().mapLocation()); //max value of 70
            if(UnitType.Knight==enemy.unitType() && unit.damage()>(int)enemy.health()-(int)enemy.knightDefense()) //is knight and can kill
                hval+=10000;
            else if(unit.damage()>(int)enemy.health()) //can kill
                hval+=10000;
            if(enemyType==UnitType.Rocket)
                hval+=8000;
            if(enemyType==UnitType.Factory)
                hval+=7000;
            if(UnitType.Knight==enemy.unitType())
                hval += (10-((int)enemy.health())/(unit.damage()-(int)enemy.knightDefense()))*100; //is knight and weakest unit
            else
                hval += (10-((int)enemy.health())/(unit.damage()))*100; //weakest unit
            UnitType[] priorities = {UnitType.Worker, UnitType.Knight, UnitType.Mage, UnitType.Ranger, UnitType.Healer}; //unit priorities
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
    //********************************** KNIGHT METHODS *********************************//
    //***********************************************************************************//

    public static void runKnight(Unit unit, MapLocation myloc)  {
        VecUnit enemies_in_sight = gc.senseNearbyUnitsByTeam(myloc, maxVisionRange, enemy);
        if(enemies_in_sight.size()>0) {      //combat state
            Unit nearestUnit = getNearestUnit(myloc, enemies_in_sight); //move in a better fashion
            MapLocation nearloc = nearestUnit.location().mapLocation();
            fuzzyMove(unit, myloc.directionTo(nearloc)); //move in a better way

            VecUnit enemies_in_range = gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), enemy);
            if(enemies_in_range.size()>0) {
                knightAttack(unit, enemies_in_range);
            }
        }
        else { //non-combat state
            if( (doesPathExist==false && rocket_homing==0) || enemy_locations.size()==0)
                moveOnRandomField(unit, myloc);                        
            else
                moveOnVectorField(unit, myloc);                    
        }
    }

    //knight attack prioritization
    //1. anything that u can kill
    //2. attack factories then rockets
    //Tiebreaker weakest
    //Tiebreaker again: mages > healers > knights > workers > rangers
    public static void knightAttack(Unit unit, VecUnit enemies_in_range) {
        if(!gc.isAttackReady(unit.id()))
            return;
        int[][] heuristics = new int[(int)enemies_in_range.size()][2];
        for(int i=0; i<enemies_in_range.size(); i++) {
            int hval = 0;
            Unit enemy = enemies_in_range.get(i);
            UnitType enemyType = enemy.unitType();
            if(UnitType.Knight==enemy.unitType() && unit.damage()>(int)enemy.health()-(int)enemy.knightDefense()) //is knight and can kill
                hval+=10000;
            else if(unit.damage()>(int)enemy.health()) //can kill
                hval+=10000;
            if(enemyType==UnitType.Rocket)
                hval+=8000;
            if(enemyType==UnitType.Factory)
                hval+=7000;
            if(UnitType.Knight==enemy.unitType())
                hval += (10-((int)enemy.health())/(unit.damage()-(int)enemy.knightDefense()))*100; //is knight and weakest unit
            else
                hval += (10-((int)enemy.health())/(unit.damage()))*100; //weakest unit
            UnitType[] priorities = {UnitType.Ranger, UnitType.Worker, UnitType.Knight, UnitType.Mage, UnitType.Healer}; //unit priorities
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
    //********************************** HEALER METHODS *********************************//
    //***********************************************************************************//

    public static void runHealer(Unit unit, MapLocation myloc) {
        VecUnit enemies_in_range = gc.senseNearbyUnitsByTeam(myloc, maxVisionRange, enemy);
        if(true && enemies_in_range.size()>0) {      //combat state //ADD CHARGE MECHANIC
            Direction toMoveDir = getNearestNonWorkerOppositeDirection(myloc, enemies_in_range);
            fuzzyMove(unit, toMoveDir);
        }
        else { //non-combat state
            if( (doesPathExist==false && rocket_homing==0) || enemy_locations.size()==0) {
                moveOnRandomField(unit, myloc);
            }
            else {
                moveOnVectorField(unit, myloc);
            }
        }
        healerHeal(unit, myloc);
        healerOvercharge(unit, myloc);
    }

    public static void healerOvercharge(Unit unit, MapLocation myloc) {
        if(!gc.isOverchargeReady(unit.id()))
            return;
        VecUnit allies_in_range = gc.senseNearbyUnitsByTeam(myloc, unit.abilityRange(), ally);
        if(allies_in_range.size()<=0)
            return;
        Unit ally_to_heal = null;
        int ally_score = 0;
        for(int i=0; i<allies_in_range.size(); i++) {
            Unit test_to_heal = allies_in_range.get(0);
            if(test_to_heal.unitType()!=UnitType.Ranger)
                continue;
            int test_score = 0;
            int attack_heat = (int)test_to_heal.attackHeat();
            int movement_heat = (int)test_to_heal.movementHeat();
            if(attack_heat>=20)
                test_score+=2000;
            else if(attack_heat>=10)
                test_score+=1000;
            if(movement_heat>=20)
                test_score+=200;
            else if(movement_heat>=10)
                test_score+=100;
            if(test_score>ally_score) {
                ally_to_heal = test_to_heal;
                ally_score = test_score;
            }
        }
        if(ally_to_heal!=null && gc.canOvercharge(unit.id(), ally_to_heal.id())) {
            gc.overcharge(unit.id(), ally_to_heal.id());
            runRanger(ally_to_heal, ally_to_heal.location().mapLocation());
        }
    }

    //heal lowest hp unit in range
    public static void healerHeal(Unit unit, MapLocation myloc) {
        if(!gc.isHealReady(unit.id()))
            return;
        VecUnit allies_in_range = gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), ally);
        if(allies_in_range.size()==0)
            return;
        Unit ally_to_heal = allies_in_range.get(0);
        int ally_damage = (int)(ally_to_heal.maxHealth()-ally_to_heal.health());
        for(int i=1; i<allies_in_range.size(); i++) {
            Unit test_ally = allies_in_range.get(i);
            int test_damage = (int)(test_ally.maxHealth()-test_ally.health());
            if(test_damage>ally_damage) {
                ally_to_heal = test_ally;
                ally_damage = test_damage;
            }
        }
        if(ally_damage>0 && gc.canHeal(unit.id(), ally_to_heal.id())) {
            gc.heal(unit.id(), ally_to_heal.id());
        }
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
    public static int blueprintRocket(Unit unit, ArrayList<KarbDir> mykarbs, VecUnit units, long rad) {
        MapLocation myLoc = unit.location().mapLocation();
        ArrayList<Unit> closeWorkers = nearbyWorkersRocket(unit, myLoc, rad);
        if(closeWorkers.size()>2) { //includes the original worker, we want three workers per factory
            Direction blueprintDirection = optimalDirectionRocket(unit, myLoc, closeWorkers);
            if(blueprintDirection!=null) {
                gc.blueprint(unit.id(), UnitType.Rocket, blueprintDirection);
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
    public static boolean buildRocket(Unit unit, ArrayList<KarbDir> mykarbs, VecUnit units, long rad) {
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
                    workerharvest(unit, mykarbs);
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
    public static int blueprintFactory(Unit unit, ArrayList<KarbDir> mykarbs, VecUnit units, long rad) {
        MapLocation myLoc = unit.location().mapLocation();
        ArrayList<Unit> closeWorkers = nearbyWorkersFactory(unit, myLoc, rad);
        if(closeWorkers.size()>2) { //includes the original worker, we want three workers per factory
            Direction blueprintDirection = optimalDirectionFactory(unit, myLoc, closeWorkers);
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
            if(current_round<(width+height)/2) {
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

    //***********************************************************************************//
    //********************************** ROCKET METHODS *********************************//
    //***********************************************************************************//

    public static void runRocket(Unit unit, MapLocation myloc) {
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.West,
                    Direction.Southwest, Direction.South, Direction.Southeast};
        if(myPlanet==Planet.Earth) { //on earth load/lift
            addRocketLocation(unit, myloc);
            VecUnit allies_to_load = gc.senseNearbyUnitsByTeam(myloc, 2, ally);
            VecUnitID garrison = unit.structureGarrison();
            int workers_in_garrison = 0;
            for(int i=0; i<garrison.size(); i++)
                if(gc.unit(garrison.get(i)).unitType()==UnitType.Worker)
                    workers_in_garrison++;
            int maxcapacity = (int)unit.structureMaxCapacity();
            int num_in_garrison = (int)garrison.size();
            int allyctr = 0;
            while(maxcapacity>num_in_garrison && allyctr<allies_to_load.size()) { //load all units while space
                Unit ally_to_load = allies_to_load.get(allyctr);
                if(gc.canLoad(unit.id(), ally_to_load.id()) && (ally_to_load.unitType()!=UnitType.Worker || workers_in_garrison<=2)) {
                    gc.load(unit.id(), ally_to_load.id());
                    num_in_garrison++;
                    if(ally_to_load.unitType()==UnitType.Worker)
                        workers_in_garrison++;
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

    //check if rocket launch conditions are met
    //max garrison, about to die, or turn 749
    public static boolean shouldLaunchRocket(Unit unit, MapLocation myloc, int num_in_garrison, int maxcapacity) {
        if(num_in_garrison==maxcapacity)
            return true;        
        if(current_round>=749)
            return true;
        int hp = (int)unit.health();
        VecUnit enemies_in_range = gc.senseNearbyUnitsByTeam(myloc, maxAttackRange, enemy);
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
        int idealw = 0;
        int idealh = 0;
        double score = -99.0;
        for(int w=0; w<mars_width; w++) {
            for(int h=0; h<mars_height; h++) {
                double locscore = mars_landing[w][h];
                if(locscore>score) {
                    idealw = w;
                    idealh = h;
                    score = locscore;
                }
            }
        }
        if(gc.canLaunchRocket(unit.id(), new MapLocation(Planet.Mars, idealw, idealh))) {
            gc.launchRocket(unit.id(), new MapLocation(Planet.Mars, idealw, idealh)); //launch rocket
            int[] shifts = {-3, -2, -1, 0, 1, 2, 3}; //update available squares
            for(int xsi=0; xsi<shifts.length; xsi++) {
                for(int ysi=0; ysi<shifts.length; ysi++) {
                    int shifted_x = idealw+shifts[xsi];
                    int shifted_y = idealh+shifts[ysi];
                    if(shifted_x>=0 && shifted_x<mars_width && shifted_y>=0 && shifted_y<mars_height) {
                        if(xsi>1 && xsi<5 && ysi>1 && ysi<5)
                            mars_landing[shifted_x][shifted_y]=-4; //1 space adjacency penalty
                        else if(xsi>0 && xsi<6 && ysi>0 && ysi<6)
                            mars_landing[shifted_x][shifted_y]=-2; //2 space adjacency penalty
                        else
                            mars_landing[shifted_x][shifted_y]--; //3 space adjacency penalty
                    }
                }
            }
        }
    }

    //update Mars with karbonite levels
    public static void updateLandingPriorities() {
        if(!asteroid_pattern.hasAsteroid(current_round))
            return;
        AsteroidStrike strike = asteroid_pattern.asteroid(current_round);
        MapLocation strikeloc = strike.getLocation();
        int w = strikeloc.getX();
        int h = strikeloc.getY();
        int[] shifts = {-1, 0, 1};
        int karblocation = (int)strike.getKarbonite();
        double karbshift = karblocation/1000.0;   
        for(int xsi=0; xsi<shifts.length; xsi++) { //penalize this square and boost all nearby squares
            for(int ysi=0; ysi<shifts.length; ysi++) {
                int shifted_x = w+shifts[xsi];
                int shifted_y = h+shifts[ysi];
                if(shifted_x>=0 && shifted_x<mars_width && shifted_y>=0 && shifted_y<mars_height) {
                    if(xsi==1 && ysi==1)
                        mars_landing[shifted_x][shifted_y]-=karbshift; //this square
                    else
                        mars_landing[shifted_x][shifted_y]+=karbshift;
                }
            }
        }
    }

    //generates count of open adjacent spaces for locations on mars
    //used to land rockets
    public static void generateLandingPriorities() {        
        for(int w=0; w<mars_width; w++) //default initialization
            for(int h=0; h<mars_height; h++)
                mars_landing[w][h] = 8.0;
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
                    mars_landing[w][h] = -100.0;
                }
            }
        }

        // get mars map and update initial karbonite levels
        for(int w=0; w<mars_width; w++) {
            for(int h=0; h<mars_height; h++) {
                int karblocation = (int)mars_map.initialKarboniteAt(new MapLocation(Planet.Mars, w, h));
                double karbshift = karblocation/1000.0;   
                for(int xsi=0; xsi<shifts.length; xsi++) { //penalize this square and boost all nearby squares
                    for(int ysi=0; ysi<shifts.length; ysi++) {
                        int shifted_x = w+shifts[xsi];
                        int shifted_y = h+shifts[ysi];
                        if(shifted_x>=0 && shifted_x<mars_width && shifted_y>=0 && shifted_y<mars_height) {
                            if(xsi==1 && ysi==1)
                                mars_landing[shifted_x][shifted_y]-=karbshift; //this square
                            else
                                mars_landing[shifted_x][shifted_y]+=karbshift;
                        }
                    }
                }
            }
        }
    }

    //***********************************************************************************//
    //********************************** RANGER METHODS *********************************//
    //***********************************************************************************//

    public static void runRanger(Unit unit, MapLocation myloc) {
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
                rangerAttack(unit, myloc, enemies_in_range); //attack based on heuristic
                if(gc.isMoveReady(unit.id())) {  //move away from nearest unit to survive
                    if(true)  { //retreat //ADD CHARGE MECHANIC HERE
                        Direction toMoveDir = getNearestNonWorkerOppositeDirection(myloc, enemies_in_range);
                        fuzzyMove(unit, toMoveDir);
                    }
                    else { //charge
                        moveOnVectorField(unit, myloc);
                    }
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

            if(enemy_buildings.size()>0 && gc.isBeginSnipeReady(unit.id())) { //sniping
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
                int[] building_info = {10, enem_loc.getX(), enem_loc.getY(), enemy_unit.id()};
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
                int[] building_info = {7, enem_loc.getX(), enem_loc.getY(), enemy_unit.id()};
                enemy_buildings.add(building_info);
            }
        }
    }

    //1. anything that u can kill
    //2. attack factories then rockets
    //3. anything that can hit u
    //Tiebreaker weakest
    //Tiebreaker again: rangers > mages > healers > knights > workers
    public static void rangerAttack(Unit unit, MapLocation myloc, VecUnit enemies_in_range) {
        if(!gc.isAttackReady(unit.id()))
            return;
        int[][] heuristics = new int[(int)enemies_in_range.size()][2];
        for(int i=0; i<enemies_in_range.size(); i++) {
            int hval = 0;
            Unit enemy = enemies_in_range.get(i);
            UnitType enemyType = enemy.unitType();
            int distance = (int)myloc.distanceSquaredTo(enemy.location().mapLocation()); //max value of 70
            if(UnitType.Knight==enemy.unitType() && unit.damage()>(int)enemy.health()-(int)enemy.knightDefense()) //is knight and can kill
                hval+=10000;
            else if(unit.damage()>(int)enemy.health()) //can kill
                hval+=10000;
            if(enemyType==UnitType.Rocket)
                hval+=8000;
            if(enemyType==UnitType.Factory)
                hval+=7000;
            if(UnitType.Knight==enemy.unitType())
                hval += (10-((int)enemy.health())/(unit.damage()-(int)enemy.knightDefense()))*100; //is knight and weakest unit
            else
                hval += (10-((int)enemy.health())/(unit.damage()))*100; //weakest unit
            UnitType[] priorities = {UnitType.Worker, UnitType.Knight, UnitType.Mage, UnitType.Ranger, UnitType.Healer}; //unit priorities
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
    //Returns direction from unit to me
    public static Direction getNearestNonWorkerOppositeDirection(MapLocation myloc, VecUnit other_units) {
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

    //***********************************************************************************//
    //*********************************** PATHFINDING ***********************************//
    //***********************************************************************************//

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
}
