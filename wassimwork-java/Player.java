//https://s3.amazonaws.com/battlecode-2018/logs/matchnumber_0.bc18log

import bc.*;
import java.util.*;

public class Player {
    public static void main(String[] args) {
        Globals.enemy = Team.Red;   //this is evil team
        if(Globals.ally==Team.Red)
            Globals.enemy = Team.Blue;

        if(Globals.myPlanet==Planet.Earth) { //generate landing priorities for rockets
            generateLandingPriorities();
        }

        VecUnit initial_units = Globals.map.getInitial_units();
        for(int i=0; i<initial_units.size(); i++) { //initial units
            Unit unit = initial_units.get(i);
            if(Globals.ally!=unit.team()) {
                MapLocation enemy_location = unit.location().mapLocation();
                int[] enemy_info = {enemy_location.getX(), enemy_location.getY(), 0, 0};
                Globals.enemy_locations.add(enemy_info);
            }
            else {
                Globals.nikhil_num_workers+=1;
                MapLocation ally_location = unit.location().mapLocation();
                int[] ally_info = {ally_location.getX(), ally_location.getY()};
                Globals.ally_locations.add(ally_info);
            }
        }

        buildFieldBFS();       //pathing
        buildRandomField();

        for(int i=0; i<initial_units.size(); i++) { //verify pathing connectivity
            Unit unit = initial_units.get(i);
            if(Globals.ally==unit.team()) {
                Globals.nikhil_num_workers+=1;
                MapLocation ally_location = unit.location().mapLocation();
                if(Globals.distance_field[ally_location.getX()][ally_location.getY()]<50*50+1) {
                    Globals.doesPathExist = true;
                    break;
                }
            }
        }

        if(Globals.doesPathExist==false) { //research
            //50 75 100 200 300 325 //425 525 725 825 900 975
            UnitType[] rarray = {UnitType.Rocket, UnitType.Healer, UnitType.Worker, UnitType.Rocket, UnitType.Rocket, UnitType.Ranger,
                                    UnitType.Healer, UnitType.Ranger, UnitType.Ranger, UnitType.Healer, UnitType.Worker, UnitType.Worker}; //research queue
            for(int i=0; i<rarray.length; i++)
                Globals.gc.queueResearch(rarray[i]);
        }
        else {
            //25 50 150 200 225 325 //425 525 725 825 900 975
            UnitType[] rarray = {UnitType.Healer, UnitType.Ranger, UnitType.Healer, UnitType.Rocket, UnitType.Worker, UnitType.Rocket, 
                                    UnitType.Rocket, UnitType.Ranger, UnitType.Ranger, UnitType.Healer, UnitType.Worker, UnitType.Worker}; //research queue
            for(int i=0; i<rarray.length; i++)
                Globals.gc.queueResearch(rarray[i]);
        }

        Globals.minworkers=workerReplicateRatio();
        Globals.rand_permutation = randomPermutation(9);

        Globals.map_memo = new int[51][51];
        for(int x=0; x<Globals.width; x++) for(int y=0; y<Globals.height; y++) {
            if(Globals.map.isPassableTerrainAt(new MapLocation(Globals.myPlanet, x, y))==0) Globals.map_memo[x][y] = -1;
            else Globals.map_memo[x][y] = (int)Globals.map.initialKarboniteAt(new MapLocation(Globals.myPlanet, x, y));
        }

        while (true) {
            try {
                Globals.current_round = (int)Globals.gc.round();
                Globals.factories_active = 0; //tracks amount of factories producing units
                if(Globals.current_round%15==0) { //print round number and update random field
                    System.out.println("Current round: "+Globals.current_round+" Current time: "+Globals.gc.getTimeLeftMs());
                    System.runFinalization();
                    System.gc();
                    buildRandomField();
                }
                if(Globals.myPlanet==Planet.Earth)
                    updateLandingPriorities();
                buildSnipeTargets(); //build snipe targets

                //TODO: Tune this variable
                VecUnit unsorted_units = Globals.gc.myUnits();
                ArrayList<Unit> units = sortUnits(unsorted_units);
                if(Globals.current_round == 1 || Globals.current_round % 1 == 0) {
                    if((Globals.myPlanet == Planet.Earth && Globals.current_round < 750) ||
                            (Globals.myPlanet == Planet.Mars)) { // TODO: check if rocket has left
                        Globals.karbonite_path = karbonitePath(new int[] {0, 20});
                    }
                }

                // TODO: check for next asteroids within ~50 rounds
                if(Globals.myPlanet == Planet.Mars) {
                    if(Globals.asteroid_pattern.hasAsteroid(Globals.current_round)) {
                        AsteroidStrike a = Globals.asteroid_pattern.asteroid(Globals.current_round);
                        MapLocation loc = a.getLocation();
                        if(Globals.map_memo[loc.getX()][loc.getY()] != -1)
                            Globals.map_memo[loc.getX()][loc.getY()] += a.getKarbonite();
                    }
                }
                Globals.workers = new HashMap<>();
                Globals.num_rangers = 0;
                Globals.num_healers = 0;
                Globals.num_knights = 0;
                Globals.num_workers = 0;
                for(int i=0; i<units.size(); i++) { //Updates num_units. Anything not written here is treated differently and should not be added!!!
                    UnitType unit_type = units.get(i).unitType();
                    if(unit_type==UnitType.Ranger)
                        Globals.num_rangers++;
                    else if(unit_type==UnitType.Healer)
                        Globals.num_healers++;
                    else if(unit_type==UnitType.Knight)
                        Globals.num_knights++;
                    else if(unit_type==UnitType.Worker) {
                        Globals.num_workers++;
                        Globals.workers.put(units.get(i).id(), units.get(i).id());
                    }
                }
                Globals.total_rangers+=Globals.num_rangers;
                Globals.total_healers+=Globals.num_healers;
                Globals.total_knights+=Globals.num_knights;
                Globals.total_workers+=Globals.num_workers;

                //primary loop
                for (int unit_counter = 0; unit_counter < units.size(); unit_counter++) {
                    try {
                        Unit unit = units.get(unit_counter);
                        if(unit.location().isInGarrison() || unit.location().isInSpace())
                            continue;
                        MapLocation myloc = unit.location().mapLocation();

                        // WORKER CODE //
                        //TODO: u can do actions before replication but not after
                        //TODO: replication needs to be more aggressive
                        if(unit.unitType()==UnitType.Worker) {
                            try {
                                runWorker(unit, myloc, units);
                            } catch(Exception e) {
                                System.out.println("Error: "+e); 
                            }
                        }

                        // RANGER CODE //
                        //TODO: make rangerAttack not a sort
                        else if(unit.unitType()==UnitType.Ranger && unit.rangerIsSniping()==0) {
                            try {
                                runRanger(unit, myloc);
                            } catch(Exception e) {
                                System.out.println("Error: "+e); 
                            }
                        }

                        // KNIGHT CODE //
                        //TODO: update movement method priority
                        //TODO: Move towards better Globals.enemy
                        //TODO: Figure javelin
                        else if(unit.unitType()==UnitType.Knight) {
                            try {
                                runKnight(unit, myloc);
                            } catch(Exception e) {
                                System.out.println("Error: "+e); 
                            }
                        }

                        // MAGE CODE //
                        //TODO: Update Mage attack
                        //TODO: Update movement method priority
                        //TODO: move in a better way
                        //TODO: Figure out blink
                        else if(unit.unitType()==UnitType.Mage) {
                            try {
                                runMage(unit, myloc);
                            } catch(Exception e) {
                                System.out.println("Error: "+e); 
                            }
                        }

                        // HEALER CODE //
                        //TODO: Verify overcharge
                        //TODO: Update overcharge priority to overcharge unit closest to Globals.enemy via distance field
                        else if(unit.unitType()==UnitType.Healer) {
                            try {
                                runHealer(unit, myloc);
                            } catch(Exception e) {
                                System.out.println("Error: "+e); 
                            }
                        }

                        // FACTORY CODE //
                        //TODO: Anti-samosa unloading
                        else if(unit.unitType()==UnitType.Factory && unit.structureIsBuilt()!=0) {
                            try {
                                runFactory(unit, myloc);
                            } catch(Exception e) {
                                System.out.println("Error: "+e); 
                            }
                        }

                        // ROCKET CODE //
                        //TODO: make units go away from rocket b4 launch
                        //TODO: optmize launch timing to improve speed
                        //TODO: launch at same time
                        else if(unit.unitType()==UnitType.Rocket && unit.structureIsBuilt()!=0) {
                            try {
                                runRocket(unit, myloc);
                            } catch(Exception e) {
                                System.out.println("Error: "+e); 
                            }
                        }
                    } catch(Exception e) {
                        System.out.println("Error: "+e); 
                    }
                }

                //RunWorker on replicated units
                VecUnit unsorted_afterunits = Globals.gc.myUnits();
                ArrayList<Unit> afterunits = sortUnits(unsorted_afterunits);
                int additional_workers = 0;
                ArrayList<Unit> myaddworkers = new ArrayList<Unit>();
                for(int i=0; i<afterunits.size(); i++) { //Updates num_units. Anything not written here is treated differently and should not be added!!!
                    Unit myUnit = afterunits.get(i);
                    UnitType unit_type = myUnit.unitType();
                    if(unit_type==UnitType.Worker) {
                        if(!Globals.workers.containsKey(myUnit.id())) {
                            Globals.num_workers++;
                            additional_workers++;
                            myaddworkers.add(myUnit);
                            Globals.workers.put(myUnit.id(), myUnit.id());
                        }
                    }
                }
                Globals.total_workers+=additional_workers;
                for(int i=0; i<myaddworkers.size(); i++) {
                    try {
                        Unit myUnit = myaddworkers.get(i);
                        if(!myUnit.location().isInGarrison() && !myUnit.location().isInSpace()) {
                            runWorker(myUnit, myUnit.location().mapLocation(), afterunits);
                        }
                    } catch(Exception e) {
                        System.out.println("Error: "+e); 
                    }
                }
            } catch(Exception e) {
                System.out.println("Error: "+e); 
            }
            Globals.gc.nextTurn(); // Submit the actions we've done, and wait for our next turn.
        }
    }

    //DETERMINE RATIO
    public static int workerReplicateRatio() {
        if(Globals.myPlanet==Planet.Mars)
            return 1;
        int dist = 0;
        for(int i=0; i<Globals.ally_locations.size(); i++) {
            int[] ally_loc = Globals.ally_locations.get(i);
            dist+=Globals.distance_field[ally_loc[0]][ally_loc[1]];
        }
        dist = dist / Globals.ally_locations.size();
        int karbfactor = karboniteNearWorker();

        int ret = 0;
        if(Globals.doesPathExist==false)
            ret = 6;
        else if(dist<15)
            ret = 8;
        else if(dist<60 && karbfactor>200)
            ret = 14;
        else if(dist<60)
            ret = 10;
        else if(karbfactor>400)
            ret = 20;
        else if(karbfactor>200)
            ret = 16;
        else
            ret = 12;
        System.out.println("Karb Factor: "+karbfactor+" Path length: "+dist+" Worker Ratio: "+ret);
        return ret;
    }

    //calcualtes karbonite near Globals.workers for ratio determination
    public static int karboniteNearWorker() {
        int total_karb_level = 0;
        for(int i=0; i<Globals.ally_locations.size(); i++) {
            int karbonite_level = 0;
            int[] ally_loc = Globals.ally_locations.get(i);
            int idealw = ally_loc[0];
            int idealh = ally_loc[1];
            int[] shifts = {-3, -2, -1, 0, 1, 2, 3}; //update available squares
            for(int xsi=0; xsi<shifts.length; xsi++) {
                for(int ysi=0; ysi<shifts.length; ysi++) {
                    int shifted_x = idealw+shifts[xsi];
                    int shifted_y = idealh+shifts[ysi];
                    if(shifted_x>=0 && shifted_x<Globals.width && shifted_y>=0 && shifted_y<Globals.height) {
                        karbonite_level+=(int)Globals.gc.karboniteAt(new MapLocation(Globals.myPlanet, shifted_x, shifted_y));
                    }
                }
            }
            total_karb_level+=karbonite_level;
        }
        total_karb_level = total_karb_level/Globals.ally_locations.size();
        return total_karb_level;
    }

    public static ArrayList<Unit> sortUnits(VecUnit units) {
        ArrayList<Unit> ret = new ArrayList<Unit>();
        UnitType[] types = {UnitType.Rocket, UnitType.Ranger, UnitType.Knight, UnitType.Mage, UnitType.Healer, UnitType.Factory, UnitType.Worker};
        for(int i=0; i<types.length; i++) {
            UnitType ut = types[i];
            for(int x=0; x<units.size(); x++) {
                Unit cur = units.get(x);
                if(cur.unitType()==ut)
                    ret.add(cur);
            }
        }
        return ret;
    }

    //
    //
    //
    public static void runWorker(Unit unit, MapLocation loc, ArrayList<Unit> units) {
        ArrayList<KarbDir> myKarbs = karboniteSort(unit, unit.location());
        Direction toKarb = generateKarbDirection(myKarbs, loc, unit, Globals.rand_permutation);
        if(Globals.enemy_locations.size()==0) { //add Globals.enemy locations
            updateEnemies();
        }
        if(Globals.nikhil_num_workers>=Globals.minworkers && Globals.myPlanet==Planet.Earth) {
            //execute build order
            if(buildRocket(unit, toKarb, units, 8L)==true) {
                return;
            }
            else if(buildFactory(unit, toKarb, units, 20L)==true){
                return;
            }
            else {
                if(Globals.current_round>175 || Globals.doesPathExist==false && Globals.current_round>125) { //rocket cap
                    //blueprint rocket or (replicate or moveharvest)
                    int val = blueprintRocket(unit, toKarb, units, 8L, myKarbs);
                    if(val>=2) { //if blueprintRocket degenerates to replicateOrMoveHarvest()
                        Globals.nikhil_num_workers+=(val-2);
                    } else { //did not degenerate
                        Globals.num_rockets+=val;
                    }
                }
                else if( (Globals.doesPathExist && Globals.num_factories<4) || (Globals.doesPathExist && Globals.width>35 && ((int)Globals.gc.karbonite()>200+(50-Globals.width)) && Globals.num_factories<7) || (!Globals.doesPathExist && Globals.num_factories<2)) { //factory cap
                    //blueprint factory or (replicate or moveharvest)
                    int val = blueprintFactory(unit, toKarb, units, 20l, myKarbs);
                    if(val>=2) { //if blueprintFactory degenerates to replicateOrMoveHarvest()
                        Globals.nikhil_num_workers+=(val-2);
                    } else { //did not degenerate
                        Globals.num_factories+=val;
                    }
                }
                else {
                    if(replicatingrequirements(unit, loc)) {
                        Globals.nikhil_num_workers += replicateOrMoveHarvest(unit, toKarb, myKarbs);
                    } else {
                        workerharvest(unit, toKarb);
                        workermove(unit, toKarb, myKarbs);
                    }
                }
            }
        }
        else if(Globals.myPlanet==Planet.Mars) {
            if(replicatingrequirements(unit, loc) || (int)Globals.gc.karbonite()>300 || Globals.current_round>750) {
                Globals.nikhil_num_workers += replicateOrMoveHarvest(unit, toKarb, myKarbs);
            } 
            else {
                workerharvest(unit, toKarb);
                workermove(unit, toKarb, myKarbs);
            }
        } 
        else {
            //replicate or move harvest
            Globals.nikhil_num_workers += replicateOrMoveHarvest(unit, toKarb, myKarbs);
        }
        return;
    }

    public static boolean replicatingrequirements(Unit myunit, MapLocation myLoc) {
        int numworkers = nearbyWorkersFactory(myunit, myLoc, 2L).size();
        long totalkarb=0L;
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        for(Direction dir: dirs) {
            MapLocation newLoc = myLoc.add(dir);
            if(Globals.gc.canSenseLocation(newLoc)) {
                totalkarb+=Globals.gc.karboniteAt(newLoc);
            }
        }
        //System.out.println(totalkarb);
        if(totalkarb/((long)numworkers)>40L) {
            return true;
        }
        return false;
    }

    public static Direction generateKarbDirection(ArrayList<KarbDir> myKarbs, MapLocation loc, Unit unit, ArrayList<Integer> rand_permutation) {
        int x = loc.getX();
        int y = loc.getY();
        int value = -100000000;
        int amount = -1;
        Direction toKarb = Direction.Center;
        int distance = -1;
        for(KarbonitePath k : Globals.karbonite_path) {
            if(k.movement_field[x][y] == null) continue;
            int my_value = k.amount_field[x][y]-k.distance_field[x][y]*6;
            if(my_value > value) {
                value = my_value;
                amount = k.amount_field[x][y];
                distance = k.distance_field[x][y];
                Collections.shuffle(rand_permutation);
                for(int z : rand_permutation) {
                    if(z >= k.movement_field[x][y].size()) continue;
                    Direction d = k.movement_field[x][y].get(z);
                    if(Globals.gc.canMove(unit.id(), d)) {
                        toKarb = d;
                        break;
                    }
                }
            }
        }
        boolean fallback = false;
        Direction toNearest = null;
        if(toKarb == Direction.Center && Globals.gc.karboniteAt(loc) == 0)
            fallback = true;
        else if(toKarb == null || !Globals.gc.canMove(unit.id(), toKarb))
            fallback = true;
        if(value < -10000000 || fallback) {
            if(myKarbs.get(0).karb > 0L)
                toKarb = myKarbs.get(0).dir;
            else if(distance > 5) {
                toKarb = fuzzyMoveDir(unit, toKarb);
            } else {
                if(toNearest == null)
                    toNearest = nearestKarboniteDir(unit, loc, 7);
                if(toNearest != null) toKarb = toNearest;
                else if(Globals.current_round < (Globals.width+Globals.height)/2) {
                    toKarb = fuzzyMoveDir(unit, loc.directionTo(new MapLocation(Globals.myPlanet,
                                Globals.width/2, Globals.height/2)));
                } else {
                    toKarb = moveOnRandomFieldDir(unit, loc);
                }
            }
        }
        return toKarb;
    }
    public static ArrayList<Integer> randomPermutation(int l) {
        ArrayList<Integer> a = new ArrayList<>();
        for(int x=0; x<l; x++) {
            a.add(x);
        }
        Collections.shuffle(a);
        return a;
    }

    //
    //
    //

    public static void runFactory(Unit unit, MapLocation myloc) {
        Globals.factories_active++;
        if( (Globals.current_round<451 || Globals.current_round>450 && Globals.factories_active<3) && //only 2 factories after round 600
            (Globals.current_round<700 || Globals.current_round<600 && Globals.doesPathExist==false)) {  //no production in final rounds
            produceUnit(unit, myloc);
        }
        Direction unload_dir = Direction.East;
        if(Globals.enemy_locations.size()>0) {
            int[] enemy_direction = Globals.enemy_locations.get(0);
            unload_dir = myloc.directionTo(new MapLocation(Globals.myPlanet, enemy_direction[0], enemy_direction[1]));
        }
        fuzzyUnload(unit, unload_dir);
    }

    public static void produceUnit(Unit unit, MapLocation myloc) {
        if(!(Globals.gc.canProduceRobot(unit.id(), UnitType.Ranger) && Globals.gc.canProduceRobot(unit.id(), UnitType.Healer) &&
            Globals.gc.canProduceRobot(unit.id(), UnitType.Knight) && Globals.gc.canProduceRobot(unit.id(), UnitType.Mage)))
            return;

        int distance_to_enemy = Globals.distance_field[myloc.getX()][myloc.getY()];

        if(Globals.current_round<100 && distance_to_enemy<10 && Globals.total_knights<2)
            Globals.gc.produceRobot(unit.id(),UnitType.Knight);
        else if(Globals.num_workers<2 && Globals.gc.canProduceRobot(unit.id(), UnitType.Worker))
            Globals.gc.produceRobot(unit.id(),UnitType.Worker);
        else if(Globals.current_round>550 && Globals.num_workers<4 && Globals.gc.canProduceRobot(unit.id(), UnitType.Worker))
            Globals.gc.produceRobot(unit.id(),UnitType.Worker);
        else if(Globals.num_rangers<7)
            Globals.gc.produceRobot(unit.id(), UnitType.Ranger);
        else if(Globals.num_rangers>30 && (Globals.num_rangers)/(1.0*Globals.num_healers)>3.0/2.0)
            Globals.gc.produceRobot(unit.id(), UnitType.Healer);
        else if((Globals.num_rangers-4)/(1.0*Globals.num_healers)>2.0/1.0)
            Globals.gc.produceRobot(unit.id(), UnitType.Healer);
        else if(Globals.num_rangers<60 || (int)Globals.gc.karbonite()>500)
            Globals.gc.produceRobot(unit.id(), UnitType.Ranger);
    }

    //
    //
    //

    public static void runMage(Unit unit, MapLocation myloc) {
        VecUnit enemies_in_sight = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), Globals.enemy);
        if(enemies_in_sight.size()>0) {      //combat state
            Unit nearestUnit = getNearestUnit(myloc, enemies_in_sight); //move in a better fashion
            MapLocation nearloc = nearestUnit.location().mapLocation();
            fuzzyMove(unit, myloc.directionTo(nearloc)); //move in a better way

            VecUnit enemies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), Globals.enemy);
            if(enemies_in_range.size()>0) {
                mageAttack(unit, myloc, enemies_in_range);
            }
        }
        else { //non-combat state
            if( (Globals.doesPathExist==false && Globals.myPlanet==Planet.Earth && Globals.rocket_homing==0) || Globals.enemy_locations.size()==0) {
                moveOnRandomField(unit, myloc);
            }
            else
                moveOnVectorField(unit, myloc);
        }
    }

    //1. anything that u can kill
    //2. attack factories then rockets
    //3. anything that can hit u
    //Tiebreaker weakest
    //Tiebreaker again: rangers > mages > healers > knights > Globals.workers
    public static void mageAttack(Unit unit, MapLocation myloc, VecUnit enemies_in_range) {
        if(!Globals.gc.isAttackReady(unit.id()))
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
            if(Globals.gc.canAttack(unit.id(), enemies_in_range.get(heuristics[i][1]).id())) {
                Globals.gc.attack(unit.id(), enemies_in_range.get(heuristics[i][1]).id());
                return;
            }
        }
    }

    //
    //
    //

    public static void runKnight(Unit unit, MapLocation myloc)  {
        VecUnit enemies_in_sight = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), Globals.enemy);
        if(enemies_in_sight.size()>0) {      //combat state
            Unit nearestUnit = getNearestUnit(myloc, enemies_in_sight); //move in a better fashion
            MapLocation nearloc = nearestUnit.location().mapLocation();
            fuzzyMove(unit, myloc.directionTo(nearloc)); //move in a better way

            VecUnit enemies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), Globals.enemy);
            if(enemies_in_range.size()>0) {
                knightAttack(unit, enemies_in_range);
            }
        }
        else { //non-combat state
            if( (Globals.doesPathExist==false && Globals.myPlanet==Planet.Earth && Globals.rocket_homing==0) || Globals.enemy_locations.size()==0) {
                moveOnRandomField(unit, myloc);
            }
            else
                moveOnVectorField(unit, myloc);
        }
    }

    //knight attack prioritization
    //1. anything that u can kill
    //2. attack factories then rockets
    //Tiebreaker weakest
    //Tiebreaker again: mages > healers > knights > Globals.workers > rangers
    public static void knightAttack(Unit unit, VecUnit enemies_in_range) {
        if(!Globals.gc.isAttackReady(unit.id()))
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
            if(Globals.gc.canAttack(unit.id(), enemies_in_range.get(heuristics[i][1]).id())) {
                Globals.gc.attack(unit.id(), enemies_in_range.get(heuristics[i][1]).id());
                return;
            }
        }
    }

    //
    //
    //

    public static void runHealer(Unit unit, MapLocation myloc) {
        VecUnit enemies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, Globals.maxVisionRange, Globals.enemy);
        if(true && enemies_in_range.size()>0) {      //combat state //ADD CHARGE MECHANIC
            if(Globals.enemy_locations.size()==0) { //add Globals.enemy locations
                updateEnemies();
            }
            Direction toMoveDir = getNearestNonWorkerOppositeDirection(myloc, enemies_in_range);
            fuzzyMove(unit, toMoveDir);
        }
        else { //non-combat state
            if( (Globals.doesPathExist==false && Globals.myPlanet==Planet.Earth && Globals.rocket_homing==0) || Globals.enemy_locations.size()==0) {
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
        if(!Globals.gc.isOverchargeReady(unit.id()))
            return;
        VecUnit allies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.abilityRange(), Globals.ally);
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
        if(ally_to_heal!=null && Globals.gc.canOvercharge(unit.id(), ally_to_heal.id())) {
            Globals.gc.overcharge(unit.id(), ally_to_heal.id());
            runRanger(ally_to_heal, ally_to_heal.location().mapLocation());
        }
    }

    //heal lowest hp unit in range
    public static void healerHeal(Unit unit, MapLocation myloc) {
        if(!Globals.gc.isHealReady(unit.id()))
            return;
        VecUnit allies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), Globals.ally);
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
        if(ally_damage>0 && Globals.gc.canHeal(unit.id(), ally_to_heal.id())) {
            Globals.gc.heal(unit.id(), ally_to_heal.id());
        }
    }

    //
    //
    //

    //count number of karbonites on Globals.map initially
    public static long countKarbonite() {
        long totalkarb = 0L;
        for (int i=0; i<Globals.width; i++) {
            for(int j=0; j<Globals.width; j++) {
                totalkarb += Globals.map.initialKarboniteAt(new MapLocation(Globals.myPlanet, i,j));
            }
        }
        return totalkarb;
    }

    //Only called when no factories are within range
    //Blueprint a factory ONLY if there are 2+ Globals.workers within range (long rad). In this case, return 1
    //Else, replicate (or moveHarvest, if replication not possible).
    // Return 2(if moveharvest) or 3(if replication succesful)
    public static int blueprintRocket(Unit unit, Direction toKarb, ArrayList<Unit> units, long rad, ArrayList<KarbDir> myKarbs) {
        MapLocation myLoc = unit.location().mapLocation();
        ArrayList<Unit> closeWorkers = nearbyWorkersRocket(unit, myLoc, rad);
        if(closeWorkers.size()>0) { //includes the original worker, we want three Globals.workers per factory
            Direction blueprintDirection = optimalDirectionRocket(unit, myLoc, closeWorkers);
            if(blueprintDirection!=null) {
                Globals.gc.blueprint(unit.id(), UnitType.Rocket, blueprintDirection);
                return 1;
            } else {
                //cannot build blueprint
                workerharvest(unit, toKarb);
                workermove(unit, toKarb, myKarbs);
                return 0;
            }
        } else {
            //not enough close Globals.workers
            return (2+replicateOrMoveHarvest(unit, toKarb, myKarbs)); //2+ lets parent method determine whether we replicated or not
        }
    }

    //Helper Method for blueprintFactory: Determine nearbyWorkers (includes myUnit)
    public static ArrayList<Unit> nearbyWorkersRocket(Unit myUnit, MapLocation myLoc, long rad) {
        VecUnit myWorkers = Globals.gc.senseNearbyUnitsByType(myLoc, rad, UnitType.Worker);
        ArrayList<Unit> siceWorkers = new ArrayList<Unit>();
        for(int i=0; i<myWorkers.size(); i++) {
            Unit k = myWorkers.get(i);
            if(k.team()==Globals.ally) {
                siceWorkers.add(k);
            }
        }
        return siceWorkers;
    }

    //Helper Method for blueprintFactory: Determine location of blueprint
    //Determine location closest to all the Globals.workers within range
    public static Direction optimalDirectionRocket(Unit myUnit, MapLocation myLoc, ArrayList<Unit> closeWorkers) {
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        long shortestdist = 10000000000000L;
        Direction bestdir=null;
        for (Direction dir: dirs) {
            if(Globals.gc.canBlueprint(myUnit.id(), UnitType.Rocket, dir)) {
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
    public static boolean buildRocket(Unit unit, Direction toKarb, ArrayList<Unit> units, long rad) {
        VecUnit nearbyRockets = Globals.gc.senseNearbyUnitsByType(unit.location().mapLocation(), rad, UnitType.Rocket);

        for(int i=0; i<nearbyRockets.size(); i++) {
            Unit k = nearbyRockets.get(i);
            if(k.team()!=Globals.ally) {
                continue;
            }
            if(k.health()!=k.maxHealth()) {
                if(Globals.gc.canBuild(unit.id(), k.id())) {
                    Globals.gc.build(unit.id(), k.id());
                    return true;
                } else if(Globals.gc.canRepair(unit.id(), k.id())){
                    Globals.gc.repair(unit.id(), k.id());
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
    //Blueprint a factory ONLY if there are 2+ Globals.workers within range (long rad). In this case, return 1
    //Else, replicate (or moveHarvest, if replication not possible).
    // Return 2(if moveharvest) or 3(if replication succesful)
    public static int blueprintFactory(Unit unit, Direction toKarb, ArrayList<Unit> units, long rad, ArrayList<KarbDir> myKarbs) {
        MapLocation myLoc = unit.location().mapLocation();
        ArrayList<Unit> closeWorkers = nearbyWorkersFactory(unit, myLoc, rad);
        if(closeWorkers.size()>2) { //includes the original worker, we want three Globals.workers per factory
            Direction blueprintDirection = optimalDirectionFactory(unit, myLoc, closeWorkers);
            if(blueprintDirection!=null) {
                Globals.gc.blueprint(unit.id(), UnitType.Factory, blueprintDirection);
                return 1;
            } else {
                //cannot build blueprint
                workerharvest(unit, toKarb);
                workermove(unit, toKarb, myKarbs);
                return 0;
            }
        } else {
            //not enough close Globals.workers
            return (2+replicateOrMoveHarvest(unit, toKarb, myKarbs)); //2+ lets parent method determine whether we replicated or not
        }
    }

    //Helper Method for blueprintFactory: Determine nearbyWorkers (includes myUnit)
    public static ArrayList<Unit> nearbyWorkersFactory(Unit myUnit, MapLocation myLoc, long rad) {
        VecUnit myWorkers = Globals.gc.senseNearbyUnitsByType(myLoc, rad, UnitType.Worker);
        ArrayList<Unit> siceWorkers = new ArrayList<Unit>();
        for(int i=0; i<myWorkers.size(); i++) {
            Unit k = myWorkers.get(i);
            if(k.team()==Globals.ally) {
                siceWorkers.add(k);
            }
        }
        return siceWorkers;
    }

    //Helper Method for blueprintFactory: Determine location of blueprint
    //Determine location closest to all the Globals.workers within range
    public static Direction optimalDirectionFactory(Unit myUnit, MapLocation myLoc, ArrayList<Unit> closeWorkers) {
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        long shortestdist = 10000000000000L;
        Direction bestdir=null;
        for (Direction dir: dirs) {
            if(Globals.gc.canBlueprint(myUnit.id(), UnitType.Factory, dir)) {
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
    //Returns a number (1 or 0) to indicate number of Globals.workers gained
    public static int replicateOrMoveHarvest(Unit unit, Direction toKarb, ArrayList<KarbDir> myKarbs) {
        if(Globals.gc.canReplicate(unit.id(), toKarb)) {
            Globals.gc.replicate(unit.id(), toKarb);
            return 1;
        } else {
            for (KarbDir k : myKarbs) {
                if(Globals.gc.canReplicate(unit.id(), k.dir)) {
                    Globals.gc.replicate(unit.id(), k.dir);
                    return 1;
                }
            }
        }
        workerharvest(unit, toKarb);
        workermove(unit, toKarb, myKarbs);
        return 0;
    }

    //If a factory is within range, and it is not at maximum health, then build it
    //If you cannot build it because it needs repairing, repair it
    //If you cannot do either, harvest+move towards the factory, since you are out of range
    //If either of these three above scenarious occur, return true
    //If there are no factories within range, then return false
    public static boolean buildFactory(Unit unit, Direction toKarb, ArrayList<Unit> units, long rad) {
        VecUnit nearbyFactories = Globals.gc.senseNearbyUnitsByType(unit.location().mapLocation(), rad, UnitType.Factory);

        for(int i=0; i<nearbyFactories.size(); i++) {
            Unit k = nearbyFactories.get(i);
            if(k.team()!=Globals.ally) {
                continue;
            }
            if(nearbyWorkersFactory(k, k.location().mapLocation(), unit.location().mapLocation().distanceSquaredTo(k.location().mapLocation())-1L).size()>3) {
                continue;
            }
            if(k.health()!=k.maxHealth()) {
                if(Globals.gc.canBuild(unit.id(), k.id())) {
                    Globals.gc.build(unit.id(), k.id());
                    return true;
                } else if(Globals.gc.canRepair(unit.id(), k.id())){
                    Globals.gc.repair(unit.id(), k.id());
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
        ArrayList<KarbDir> whatkarbs = onlyKarbs(unit, unit.location());
        MapLocation myLoc = unit.location().mapLocation();
        for (KarbDir k : whatkarbs) {
            MapLocation newLoc = myLoc.add(k.dir);
            if(Globals.gc.karboniteAt(newLoc)>0L) {
                if(Globals.gc.canHarvest(unit.id(), k.dir)){
                    Globals.gc.harvest(unit.id(), k.dir);
                    return;
                }
            }
        }
    }

    public static void workermove(Unit unit, Direction toKarb, ArrayList<KarbDir> myKarbs) {
        MapLocation myLoc = unit.location().mapLocation();
        for (KarbDir k : myKarbs) {
            MapLocation newLoc = myLoc.add(k.dir);
            if(Globals.gc.karboniteAt(newLoc)>0L) {
                if(Globals.gc.canMove(unit.id(), k.dir)) {
                    if(Globals.gc.isMoveReady(unit.id())) {
                        Globals.gc.moveRobot(unit.id(), k.dir);
                        return;
                    }
                }
            }
        }
        //NO KARBONITE NEXT TO WORKER
        MapLocation newLoc = myLoc.add(toKarb);
        if(Globals.gc.canMove(unit.id(), toKarb)) {
            if(Globals.gc.isMoveReady(unit.id())) {
                Globals.gc.moveRobot(unit.id(), toKarb);
            }
        } else {
            fuzzyMove(unit, toKarb);
        }
        return;
    }

    public static MapLocation nearestKarbonite(Unit unit, MapLocation myLoc, int visionrad) {
        int visrad = visionrad;
        long totalkarb = 0L;
        int x = myLoc.getX();
        int y = myLoc.getY();
        for (int i=Math.max(x-visrad, 0); i<Math.min(x+visrad+1,(int)Globals.map.getWidth()+1); i++) {
            for (int j=Math.max(0,y-visrad); j<Math.min(y+visrad+1,(int)Globals.map.getHeight()+1); j++) {
                if(Globals.map_memo[i][j] <= 0) continue;
                MapLocation m = new MapLocation(Globals.myPlanet, i, j);
                if((x-i)*(x-i) + (y-j)*(y-j)<unit.visionRange()) {
                    if(Globals.gc.canSenseLocation(m)) {
                        if(Globals.gc.karboniteAt(m)>0L) {
                            return m;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static long totalVisibleKarb(Unit unit, MapLocation myLoc, int visionrad) {
        int visrad = visionrad;
        long totalkarb = 0L;
        int x = myLoc.getX();
        int y = myLoc.getY();
        for (int i=Math.max(x-visrad, 0); i<Math.min(x+visrad+1,(int)Globals.map.getWidth()+1); i++) {
            for (int j=Math.max(0,y-visrad); j<Math.min(y+visrad+1,(int)Globals.map.getHeight()+1); j++) {
                MapLocation m = new MapLocation(Globals.myPlanet, i, j);
                if((x-i)*(x-i) + (y-j*(y-j))<unit.visionRange()) {
                    if(Globals.gc.canSenseLocation(m)) {
                        totalkarb+=Globals.gc.karboniteAt(m);
                    }
                }
            }
        }
        return totalkarb;
    }

    //helper method for workermove
    //returns direction of nearest karbonite, in case there is no karbonite immediately around worker
    //Computationally inefficient, O(n^2), n=visionradius
    public static Direction nearestKarboniteDir(Unit unit, MapLocation myLoc, int visionrad) {
        int visrad = visionrad;
        long totalkarb = 0L;
        int x = myLoc.getX();
        int y = myLoc.getY();
        for (int i=Math.max(x-visrad, 0); i<Math.min(x+visrad+1,(int)Globals.map.getWidth()+1); i++) {
            for (int j=Math.max(0,y-visrad); j<Math.min(y+visrad+1,(int)Globals.map.getHeight()+1); j++) {
                MapLocation m = new MapLocation(Globals.myPlanet, i, j);
                if((x-i)*(x-i) + (y-j*(y-j))<unit.visionRange()) {
                    if(Globals.gc.canSenseLocation(m)) {
                        if(Globals.gc.karboniteAt(m)>0L) {
                            return myLoc.directionTo(m);
                        }
                    }
                }
            }
        }
        return null;
    }

    //sort directions, regardless of movement ability, by karbonite content
    public static ArrayList<KarbDir> onlyKarbs(Unit unit, Location theloc) {
        MapLocation myLoc = theloc.mapLocation();
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.Center,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        ArrayList<KarbDir> karboniteDirections = new ArrayList<KarbDir>();
        long mykarb = 0L;
        for (int i=0; i<dirs.length; i++) {
            try {
                MapLocation newloc = myLoc.add(dirs[i]);
                long thiskarb = Globals.gc.karboniteAt(newloc);
                karboniteDirections.add(new KarbDir(dirs[i], thiskarb));
            } catch(Exception e) {}
        }
        Collections.sort(karboniteDirections, Collections.reverseOrder()); //sort high to low
        return karboniteDirections;
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
            if(Globals.gc.canMove(unit.id(), dirs[i]) || dirs[i]==Direction.Center) {
                long thiskarb = Globals.gc.karboniteAt(newloc);
                karboniteDirections.add(new KarbDir(dirs[i], thiskarb));
            }
        }
        Collections.sort(karboniteDirections, Collections.reverseOrder()); //sort high to low
        return karboniteDirections;
    }

    //
    //
    //

    public static void runRocket(Unit unit, MapLocation myloc) {
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.West,
                    Direction.Southwest, Direction.South, Direction.Southeast};
        if(Globals.myPlanet==Planet.Earth) { //on earth load/lift
            addRocketLocation(unit, myloc);
            VecUnit allies_to_load = Globals.gc.senseNearbyUnitsByTeam(myloc, 2, Globals.ally);
            VecUnitID garrison = unit.structureGarrison();
            int workers_in_garrison = 0;
            int healers_in_garrison = 0;
            for(int i=0; i<garrison.size(); i++) {
                UnitType test = Globals.gc.unit(garrison.get(i)).unitType();
                if(test==UnitType.Worker)
                    workers_in_garrison++;
                if(test==UnitType.Healer)
                    healers_in_garrison++;
            }
            int maxcapacity = (int)unit.structureMaxCapacity();
            int num_in_garrison = (int)garrison.size();
            int allyctr = 0;
            while(maxcapacity>num_in_garrison && allyctr<allies_to_load.size()) { //load all units while space
                Unit ally_to_load = allies_to_load.get(allyctr);
                if(Globals.gc.canLoad(unit.id(), ally_to_load.id()) && (ally_to_load.unitType()!=UnitType.Worker || workers_in_garrison<2) && (ally_to_load.unitType()!=UnitType.Healer || healers_in_garrison<3 || Globals.current_round>740)) {
                    Globals.gc.load(unit.id(), ally_to_load.id());
                    num_in_garrison++;
                    if(ally_to_load.unitType()==UnitType.Worker)
                        workers_in_garrison++;
                    if(ally_to_load.unitType()==UnitType.Healer)
                        healers_in_garrison++;
                }
                allyctr++;
            }
            if(shouldLaunchRocket(unit, myloc, num_in_garrison, maxcapacity)) { //launch
                launchRocket(unit);
                removeRocketLocation(unit, myloc);
                System.out.println("Rocket launched");
            }
        }
        else if(Globals.myPlanet==Planet.Mars) { //unload everything ASAP on Mars
            int dirctr = 0;
            VecUnitID garrison = unit.structureGarrison();
            for(int i=0; i<garrison.size(); i++) {
                while(dirctr<8) {
                    if(Globals.gc.canUnload(unit.id(), dirs[dirctr])) {
                        Globals.gc.unload(unit.id(), dirs[dirctr]);
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
        if(Globals.current_round>=745)
            return true;
        int hp = (int)unit.health();
        VecUnit enemies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, Globals.maxAttackRange, Globals.enemy);
        for(int i=0; i<enemies_in_range.size(); i++) {
            Unit enem = enemies_in_range.get(i);
            int dist = (int)enem.location().mapLocation().distanceSquaredTo(myloc);
            try {
                if((int)enem.attackHeat()-10<10 && enem.attackRange()>dist) { //can do damage
                    hp -= enem.damage();
                    if(hp<=0)
                        return true;
                }
            } catch(Exception e) {}
        }
        if(num_in_garrison==maxcapacity && Globals.orbit_pattern.duration(Globals.current_round)<Globals.orbit_pattern.duration(Globals.current_round+1)+1) {
            return true;
        }
        else if(num_in_garrison==maxcapacity) {
            removeRocketLocation(unit, myloc);
        }
        return false;
    }

    //removes rocket location to Globals.enemy_locations
    //this does to stop homing towards rockets
    public static void removeRocketLocation(Unit unit, MapLocation myloc) {
        int x = myloc.getX();
        int y = myloc.getY();
        for(int i=0; i<Globals.enemy_locations.size(); i++) { //search through list
            int[] enem_loc = Globals.enemy_locations.get(i);
            if(x==enem_loc[0] && y==enem_loc[1]) {
                Globals.enemy_locations.remove(i);
                Globals.rocket_homing--;
                return;
            }
        }
    }

    //adds rocket location to Globals.enemy_locations if not added
    //this does homing towards rockets
    public static void addRocketLocation(Unit unit, MapLocation myloc) {
        int x = myloc.getX();
        int y = myloc.getY();
        boolean addLocation = true;
        for(int i=0; i<Globals.enemy_locations.size(); i++) { //add rocket to Globals.enemy list if not there already
            int[] enem_loc = Globals.enemy_locations.get(i);
            if(x==enem_loc[0] && y==enem_loc[1]) {
                addLocation = false;
                break;
            }
        }
        if(addLocation) {
            int[] rocket_loc = {x, y, 0, 0};
            Globals.enemy_locations.add(rocket_loc);
            Globals.rocket_homing++;
            buildFieldBFS();
        }
    }

    //launches rocket based on precomputed space
    public static void launchRocket(Unit unit) {
        int idealw = 0;
        int idealh = 0;
        double score = -99.0;
        for(int w=0; w<Globals.mars_width; w++) {
            for(int h=0; h<Globals.mars_height; h++) {
                double locscore = Globals.mars_landing[w][h];
                if(locscore>score) {
                    idealw = w;
                    idealh = h;
                    score = locscore;
                }
            }
        }
        if(Globals.gc.canLaunchRocket(unit.id(), new MapLocation(Planet.Mars, idealw, idealh))) {
            Globals.gc.launchRocket(unit.id(), new MapLocation(Planet.Mars, idealw, idealh)); //launch rocket
            int[] shifts = {-3, -2, -1, 0, 1, 2, 3}; //update available squares
            for(int xsi=0; xsi<shifts.length; xsi++) {
                for(int ysi=0; ysi<shifts.length; ysi++) {
                    int shifted_x = idealw+shifts[xsi];
                    int shifted_y = idealh+shifts[ysi];
                    if(shifted_x>=0 && shifted_x<Globals.mars_width && shifted_y>=0 && shifted_y<Globals.mars_height) {
                        if(xsi>1 && xsi<5 && ysi>1 && ysi<5)
                            Globals.mars_landing[shifted_x][shifted_y]=-4; //1 space adjacency penalty
                        else if(xsi>0 && xsi<6 && ysi>0 && ysi<6)
                            Globals.mars_landing[shifted_x][shifted_y]=-2; //2 space adjacency penalty
                        else
                            Globals.mars_landing[shifted_x][shifted_y]--; //3 space adjacency penalty
                    }
                }
            }
        }
    }

    //update Mars with karbonite levels
    public static void updateLandingPriorities() {
        if(!Globals.asteroid_pattern.hasAsteroid(Globals.current_round))
            return;
        AsteroidStrike strike = Globals.asteroid_pattern.asteroid(Globals.current_round);
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
                if(shifted_x>=0 && shifted_x<Globals.mars_width && shifted_y>=0 && shifted_y<Globals.mars_height) {
                    if(xsi==1 && ysi==1)
                        Globals.mars_landing[shifted_x][shifted_y]-=karbshift; //this square
                    else
                        Globals.mars_landing[shifted_x][shifted_y]+=karbshift;
                }
            }
        }
    }

    //generates count of open adjacent spaces for locations on mars
    //used to land rockets
    public static void generateLandingPriorities() {
        for(int w=0; w<Globals.mars_width; w++) //default initialization
            for(int h=0; h<Globals.mars_height; h++)
                Globals.mars_landing[w][h] = 8.0;
        for(int w=0; w<Globals.mars_width; w++) { //correct for borders horizontally
            Globals.mars_landing[w][0]--;
            Globals.mars_landing[w][Globals.mars_height-1]--;
        }
        for(int h=0; h<Globals.mars_height; h++) { //correct for borders vertically
            Globals.mars_landing[0][h]--;
            Globals.mars_landing[Globals.mars_width-1][h]--;
        }
        int[] shifts = {-1, 0, 1};
        for(int w=0; w<Globals.mars_width; w++) {
            for(int h=0; h<Globals.mars_height; h++) {
                if(Globals.mars_map.isPassableTerrainAt(new MapLocation(Planet.Mars, w, h))==0) { //not passable
                    for(int xsi=0; xsi<3; xsi++) {
                        for(int ysi=0; ysi<3; ysi++) {
                            int shifted_x = w+shifts[xsi];
                            int shifted_y = h+shifts[ysi];
                            if(shifted_x>=0 && shifted_x<Globals.mars_width && shifted_y>=0 && shifted_y<Globals.mars_height)
                                Globals.mars_landing[shifted_x][shifted_y]--;
                        }
                    }
                    Globals.mars_landing[w][h] = -100.0;
                }
            }
        }

        // get mars Globals.map and update initial karbonite levels
        for(int w=0; w<Globals.mars_width; w++) {
            for(int h=0; h<Globals.mars_height; h++) {
                int karblocation = (int)Globals.mars_map.initialKarboniteAt(new MapLocation(Planet.Mars, w, h));
                double karbshift = karblocation/1000.0;
                for(int xsi=0; xsi<shifts.length; xsi++) { //penalize this square and boost all nearby squares
                    for(int ysi=0; ysi<shifts.length; ysi++) {
                        int shifted_x = w+shifts[xsi];
                        int shifted_y = h+shifts[ysi];
                        if(shifted_x>=0 && shifted_x<Globals.mars_width && shifted_y>=0 && shifted_y<Globals.mars_height) {
                            if(xsi==1 && ysi==1)
                                Globals.mars_landing[shifted_x][shifted_y]-=karbshift; //this square
                            else
                                Globals.mars_landing[shifted_x][shifted_y]+=karbshift;
                        }
                    }
                }
            }
        }
    }

    //
    //
    //

    public static void runRanger(Unit unit, MapLocation myloc) {
        VecUnit enemies_in_sight = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), Globals.enemy);
        if(enemies_in_sight.size()>0) {      //combat state
            if(Globals.enemy_locations.size()==0) { //add Globals.enemy locations
                updateEnemies();
            }
            checkVectorField(unit, myloc);
            VecUnit enemies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), Globals.enemy);
            if(enemies_in_range.size()==0) {    //move towards Globals.enemy since nothing in attack range
                Unit nearestUnit = getNearestUnit(myloc, enemies_in_sight);
                MapLocation nearloc = nearestUnit.location().mapLocation();
                fuzzyMove(unit, myloc.directionTo(nearloc));
            }
            enemies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), Globals.enemy);

            if(enemies_in_range.size()>0) {
                rangerAttack(unit, myloc, enemies_in_range); //attack based on heuristic
                if(Globals.gc.isMoveReady(unit.id())) {  //move away from nearest unit to survive
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
            if( (Globals.doesPathExist==false && Globals.myPlanet==Planet.Earth && Globals.rocket_homing==0) || Globals.enemy_locations.size()==0) {
                moveOnRandomField(unit, myloc);
            }
            else {
                moveOnVectorField(unit, myloc);
            }

            if(Globals.enemy_buildings.size()>0 && Globals.gc.isBeginSnipeReady(unit.id())) { //sniping
                int[] target = Globals.enemy_buildings.get(0);
                MapLocation snipetarget = new MapLocation(Globals.myPlanet, target[1], target[2]);
                if(Globals.gc.canBeginSnipe(unit.id(), snipetarget)) {
                    Globals.gc.beginSnipe(unit.id(), snipetarget);
                }
                target[0]--;
                if(target[0]==0)
                    Globals.enemy_buildings.remove(0);
                else
                    Globals.enemy_buildings.set(0,target);
            }
        }
    }

    //updates snipe list to contain all buildings
    public static void buildSnipeTargets() {
        if(Globals.current_round%10==0)
            Globals.enemy_buildings.clear();
        VecUnit total_enemies = Globals.gc.senseNearbyUnitsByTeam(new MapLocation(Globals.myPlanet, Globals.width/2, Globals.height/2), Globals.width*Globals.height/2, Globals.enemy); //all enemies
        for(int i = 0; i<total_enemies.size(); i++) {
            Unit enemy_unit = total_enemies.get(i);
            boolean isDuplicate = false;
            if(enemy_unit.unitType()==UnitType.Factory) { //if factory
                for(int targs=0; targs<Globals.enemy_buildings.size(); targs++) { //check if already marked
                    if(Globals.enemy_buildings.get(targs)[3]==enemy_unit.id()) {
                        isDuplicate = true;
                        break;
                    }
                }
                if(isDuplicate)
                    continue;
                MapLocation enem_loc = enemy_unit.location().mapLocation();
                int[] building_info = {10, enem_loc.getX(), enem_loc.getY(), enemy_unit.id()};
                Globals.enemy_buildings.add(building_info);
            }
            else if(enemy_unit.unitType()==UnitType.Rocket) { //if rocket
                for(int targs=0; targs<Globals.enemy_buildings.size(); targs++) { //check if already marked
                    if(Globals.enemy_buildings.get(targs)[3]==enemy_unit.id()) {
                        isDuplicate = true;
                        break;
                    }
                }
                if(isDuplicate)
                    continue;
                MapLocation enem_loc = enemy_unit.location().mapLocation();
                int[] building_info = {7, enem_loc.getX(), enem_loc.getY(), enemy_unit.id()};
                Globals.enemy_buildings.add(building_info);
            }
            else if(enemy_unit.unitType()==UnitType.Healer) { //if rocket
                for(int targs=0; targs<Globals.enemy_buildings.size(); targs++) { //check if already marked
                    if(Globals.enemy_buildings.get(targs)[3]==enemy_unit.id()) {
                        isDuplicate = true;
                        break;
                    }
                }
                if(isDuplicate)
                    continue;
                MapLocation enem_loc = enemy_unit.location().mapLocation();
                int[] building_info = {4, enem_loc.getX(), enem_loc.getY(), enemy_unit.id()};
                Globals.enemy_buildings.add(building_info);
            }
            else if(enemy_unit.unitType()==UnitType.Mage) { //if rocket
                for(int targs=0; targs<Globals.enemy_buildings.size(); targs++) { //check if already marked
                    if(Globals.enemy_buildings.get(targs)[3]==enemy_unit.id()) {
                        isDuplicate = true;
                        break;
                    }
                }
                if(isDuplicate)
                    continue;
                MapLocation enem_loc = enemy_unit.location().mapLocation();
                int[] building_info = {4, enem_loc.getX(), enem_loc.getY(), enemy_unit.id()};
                Globals.enemy_buildings.add(building_info);
            }
            else if(enemy_unit.unitType()==UnitType.Worker) { //if rocket
                for(int targs=0; targs<Globals.enemy_buildings.size(); targs++) { //check if already marked
                    if(Globals.enemy_buildings.get(targs)[3]==enemy_unit.id()) {
                        isDuplicate = true;
                        break;
                    }
                }
                if(isDuplicate)
                    continue;
                MapLocation enem_loc = enemy_unit.location().mapLocation();
                int[] building_info = {4, enem_loc.getX(), enem_loc.getY(), enemy_unit.id()};
                Globals.enemy_buildings.add(building_info);
            }
        }
    }

    //1. anything that u can kill
    //2. attack factories then rockets
    //3. anything that can hit u
    //Tiebreaker weakest
    //Tiebreaker again: rangers > mages > healers > knights > Globals.workers
    public static void rangerAttack(Unit unit, MapLocation myloc, VecUnit enemies_in_range) {
        if(!Globals.gc.isAttackReady(unit.id()))
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
            UnitType[] priorities = {UnitType.Worker, UnitType.Knight, UnitType.Ranger, UnitType.Mage, UnitType.Healer}; //unit priorities
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
            if(Globals.gc.canAttack(unit.id(), enemies_in_range.get(heuristics[i][1]).id())) {
                Globals.gc.attack(unit.id(), enemies_in_range.get(heuristics[i][1]).id());
                return;
            }
        }
    }

    //
    //
    //

    //Takes MapLocation and a VecUnit
    //Finds unit from VecUnit closest to MapLocation
    //Typically used to find Globals.enemy unit from array closest to your unit
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

    //
    //
    //

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
            if(Globals.gc.canUnload(unit.id(), dirs[ (dirindex+shifts[i]+8)%8 ])) {
                Globals.gc.unload(unit.id(), dirs[ (dirindex+shifts[i]+8)%8 ]);
            }
        }
    }

    //
    //
    //

    //Attempts to move unit in direction as best as possible
    //Scans 45 degree offsets, then 90
    public static void fuzzyMove(Unit unit, Direction dir) {
        if(!Globals.gc.isMoveReady(unit.id()) || dir==Direction.Center)
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
            if(Globals.gc.canMove(unit.id(), dirs[ (dirindex+shifts[i]+8)%8 ])) {
                Globals.gc.moveRobot(unit.id(), dirs[ (dirindex+shifts[i]+8)%8 ]);
                return;
            }
        }
    }

    public static Direction fuzzyMoveDir(Unit unit, Direction dir) {
        int[] shifts = {0, -1, 1, -2, 2};
        int dirindex = 0;
        for(int i=0; i<8; i++) {
            if(dir==Globals.dirs[i]) {
                dirindex = i;
                break;
            }
        }
        for(int i=0; i<5; i++) {
            if(Globals.gc.canMove(unit.id(), Globals.dirs[ (dirindex+shifts[i]+8)%8 ])) {
                return Globals.dirs[(dirindex+shifts[i]+8)%8];
            }
        }
        return Direction.Center;
    }

    //Moves unit on vector field
    //Should be used if no enemies in sight
    //If no optimal move is available (all blocked), unit will attempt fuzzymove in last dir
    public static void moveOnRandomField(Unit unit, MapLocation mapLocation) {
        if(!Globals.gc.isMoveReady(unit.id())) //checks if can move
            return;
        UnitType myUnitType = unit.unitType();
        int x = mapLocation.getX();
        int y = mapLocation.getY();
        for(int movedir=0; movedir<Globals.random_movement_field[x][y].size(); movedir++) { //loops over all possible move directions
            Direction dir = Globals.random_movement_field[x][y].get(movedir);
            if(dir == Direction.Center) { //reruns vector field if reaches Globals.enemy start location
                buildRandomField();
                moveOnRandomField(unit, mapLocation);
                return;
            }
            else if(movedir==Globals.random_movement_field[x][y].size()-1) { //fuzzy moves last possible direction
                fuzzyMove(unit, dir);
                return;
            }
            else if(Globals.gc.canMove(unit.id(), dir)) { //verifies can move in selected direction
                Globals.gc.moveRobot(unit.id(), dir);
                return;
            }
        }
    }
    public static Direction moveOnRandomFieldDir(Unit unit, MapLocation mapLocation) {
        UnitType myUnitType = unit.unitType();
        int x = mapLocation.getX();
        int y = mapLocation.getY();
        for(int movedir=0; movedir<Globals.random_movement_field[x][y].size(); movedir++) { //loops over all possible move directions
            Direction dir = Globals.random_movement_field[x][y].get(movedir);
            if(dir == Direction.Center) { //reruns vector field if reaches Globals.enemy start location
                buildRandomField();
                return moveOnRandomFieldDir(unit, mapLocation);
            }
            else if(movedir==Globals.random_movement_field[x][y].size()-1) { //fuzzy moves last possible direction
                return fuzzyMoveDir(unit, dir);
            }
            else if(Globals.gc.canMove(unit.id(), dir)) { //verifies can move in selected direction
                return dir;
            }
        }
        for(int w=0; w<50; w++) {
            Direction d = Globals.dirs[(int)Math.random()*9];
            if(Globals.gc.canMove(unit.id(), d)) return d;
        }
        return Direction.Center;
    }

    //Takes a random llocation and builds vector fields
    //Globals.random_distance_field tells you how far from current path destination
    //Globals.random_movement_field gives ArrayList of equally optimal Directions to move in
    public static void buildRandomField() {
        MapLocation clustertarget = new MapLocation(Globals.myPlanet, (int)(Math.random()*Globals.width), (int)(Math.random()*Globals.height)); //random movement target for cluster
        for(int i=0; i<100; i++) {
            if(Globals.gc.canSenseLocation(clustertarget)==true) //target already within sight range
                break;
            clustertarget = new MapLocation(Globals.myPlanet, (int)(Math.random()*Globals.width), (int)(Math.random()*Globals.height));
        }
        int[] randtarget = {clustertarget.getX(), clustertarget.getY(), 0, 0};

        Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};

        Queue<int[]> queue = new LinkedList<int[]>();
        queue.add(randtarget);

        for(int w=0; w<Globals.width; w++) {
            for(int h=0; h<Globals.height; h++) {
                Globals.random_distance_field[w][h] = (50*50+1);
                Globals.random_movement_field[w][h] = new ArrayList<Direction>();
            }
        }

        while(queue.peek()!=null) {
            int[] lcc = queue.poll();
            int x = lcc[0];
            int y = lcc[1];
            int dir = lcc[2];
            int depth = lcc[3];

            if(x<0 || y<0 || x>=Globals.width || y>=Globals.height ||  //border checks
                    Globals.map.isPassableTerrainAt(new MapLocation(Globals.myPlanet, x, y))==0 || //is not passable
                    Globals.random_distance_field[x][y]<depth) { //is an inferior move
                continue;
            }
            else if(Globals.random_distance_field[x][y]==depth) { //add equivalently optimal Direction
                Globals.random_movement_field[x][y].add(dirs[dir]);
            }
            else if(Globals.random_distance_field[x][y]>depth) { //replace old Directions with more optimal ones
                Globals.random_distance_field[x][y] = depth;
                Globals.random_movement_field[x][y] = new ArrayList<Direction>();
                Globals.random_movement_field[x][y].add(dirs[dir]);
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

    //adds all spotted enemies to Globals.enemy_locations
    public static void updateEnemies() {
        VecUnit total_enemies = Globals.gc.senseNearbyUnitsByTeam(new MapLocation(Globals.myPlanet, Globals.width/2, Globals.height/2), Globals.width*Globals.height/2, Globals.enemy);
        for(int eloc = 0; eloc<total_enemies.size(); eloc++) {
            MapLocation enemloc = total_enemies.get(eloc).location().mapLocation();
            int[] enemy_info = {enemloc.getX(), enemloc.getY(), 0, 0};
            Globals.enemy_locations.add(enemy_info);
        }
        if(Globals.enemy_locations.size()>0)
            buildFieldBFS();
    }

    //Checks if current location is a destination in vector field
    //Used when in combat mode to verify not blocked into Globals.enemy initial location
    public static void checkVectorField(Unit unit, MapLocation mapLocation) {
        UnitType myUnitType = unit.unitType();
        int x = mapLocation.getX();
        int y = mapLocation.getY();
        if(Globals.movement_field[x][y].size()==1 && Globals.movement_field[x][y].get(0)==Direction.Center) {
            for(int eloc=0; eloc<Globals.enemy_locations.size(); eloc++) {
                int[] elocinfo = Globals.enemy_locations.get(eloc);
                if(x==elocinfo[0] && y==elocinfo[1]) {
                    Globals.enemy_locations.remove(eloc);
                    break;
                }
            }
            if(Globals.enemy_locations.size()==0) { //add more targets
                updateEnemies();
            }
            buildFieldBFS();
        }
    }

    //Moves unit on vector field
    //Should be used if no enemies in sight
    //If no optimal move is available (all blocked), unit will attempt fuzzymove in last dir
    public static void moveOnVectorField(Unit unit, MapLocation mapLocation) {
        if(!Globals.gc.isMoveReady(unit.id())) //checks if can move
            return;
        UnitType myUnitType = unit.unitType();
        int x = mapLocation.getX();
        int y = mapLocation.getY();
        for(int movedir=0; movedir<Globals.movement_field[x][y].size(); movedir++) { //loops over all possible move directions
            Direction dir = Globals.movement_field[x][y].get(movedir);
            if(dir == Direction.Center) { //refactors vector field if reaches Globals.enemy start location
                for(int eloc=0; eloc<Globals.enemy_locations.size(); eloc++) {
                    int[] elocinfo = Globals.enemy_locations.get(eloc);
                    if(x==elocinfo[0] && y==elocinfo[1]) {
                        Globals.enemy_locations.remove(eloc);
                        break;
                    }
                }
                if(Globals.enemy_locations.size()==0) { //add more targets
                    updateEnemies();
                }
                buildFieldBFS();
                moveOnVectorField(unit, mapLocation);
                return;
            }
            else if(movedir==Globals.movement_field[x][y].size()-1) { //fuzzy moves last possible direction
                fuzzyMove(unit, dir);
                return;
            }
            else if(Globals.gc.canMove(unit.id(), dir)) { //verifies can move in selected direction
                Globals.gc.moveRobot(unit.id(), dir);
                return;
            }
        }
    }

    //Takes an arraylist of starting Globals.enemy locations and builds vector fields
    //Globals.distance_field tells you how far from current path destination
    //Globals.movement_field gives ArrayList of equally optimal Directions to move in
    public static void buildFieldBFS() {
        Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};

        Queue<int[]> queue = new LinkedList<int[]>();
        for(int i=0; i<Globals.enemy_locations.size(); i++)
            queue.add(Globals.enemy_locations.get(i));
        for(int w=0; w<Globals.width; w++) {
            for(int h=0; h<Globals.height; h++) {
                Globals.distance_field[w][h] = (50*50+1);
                Globals.movement_field[w][h] = new ArrayList<Direction>();
            }
        }

        while(queue.peek()!=null) {
            int[] lcc = queue.poll();
            int x = lcc[0];
            int y = lcc[1];
            int dir = lcc[2];
            int depth = lcc[3];

            if(x<0 || y<0 || x>=Globals.width || y>=Globals.height ||  //border checks
                    Globals.map.isPassableTerrainAt(new MapLocation(Globals.myPlanet, x, y))==0 || //is not passable
                    Globals.distance_field[x][y]<depth) { //is an inferior move
                continue;
            }
            else if(Globals.distance_field[x][y]==depth) { //add equivalently optimal Direction
                Globals.movement_field[x][y].add(dirs[dir]);
            }
            else if(Globals.distance_field[x][y]>depth) { //replace old Directions with more optimal ones
                Globals.distance_field[x][y] = depth;
                Globals.movement_field[x][y] = new ArrayList<Direction>();
                Globals.movement_field[x][y].add(dirs[dir]);
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

    public static ArrayList<KarbonitePath> karbonitePath(int[] buckets) {
        ArrayList<KarbonitePath> R = new ArrayList<>();
        Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        for(int x=0; x<Globals.width; x++)
            for(int y=0; y<Globals.height; y++) {
                if(Globals.map_memo[x][y] > 0) {
                    MapLocation m = new MapLocation(Globals.myPlanet, x, y);
                    if(Globals.gc.canSenseLocation(m)) {
                        Globals.map_memo[x][y] = (int)Globals.gc.karboniteAt(m);
                    }
                }
            }
        for(int bucket : buckets) {


            Queue<int[]> queue = new LinkedList<int[]>();
            int[][] distance_field = new int[51][51];
            ArrayList<Direction>[][] movement_field = new ArrayList[51][51];
            int[][] amount_field = new int[51][51];
            for(int x=0; x<Globals.width; x++)
                for(int y=0; y<Globals.height; y++) {
                    distance_field[x][y] = 50*50+1;
                    if(Globals.map_memo[x][y] > bucket) {
                        int[] j = {x, y, 0, 0, Globals.map_memo[x][y]};
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

                if(x<0 || y<0 || x>=Globals.width || y>=Globals.height ||  //border checks
                        Globals.map_memo[x][y] == -1 || //is not passable
                        distance_field[x][y] < depth) { //is an inferior move
                    continue;
                }
                else if(distance_field[x][y]==depth) { //add equivalently optimal Direction
                    movement_field[x][y].add(dirs[dir]);
                }
                else if(distance_field[x][y]>depth) { //replace old Directions with more optimal ones
                    distance_field[x][y] = depth;
                    movement_field[x][y] = new ArrayList<Direction>();
                    movement_field[x][y].add(dirs[dir]);
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
                    queue.add(lc9);
                }
            }









            R.add(new KarbonitePath(amount_field, distance_field, movement_field));


        }
        return R;
    }
}
