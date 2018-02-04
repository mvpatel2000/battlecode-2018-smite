import bc.*;
import java.util.*;

public class Worker {
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

    public static void runWorker(Unit unit, MapLocation loc, ArrayList<Unit> units) {
        ArrayList<KarbDir> myKarbs = onlyKarbs(unit, unit.location());
		Direction toKarb = Direction.Center;
		int dist_from_karb = 100000;
		for(KarbonitePath k : Globals.karbonite_path) {
			dist_from_karb = Math.min(dist_from_karb, k.distance_field[loc.getX()][loc.getY()]);
		}

        int distance_to_enemy = Globals.distance_field[loc.getX()][loc.getY()];
		boolean goTowardsKarb = true;
		if(dist_from_karb >= 15 && Globals.current_round > 20) { //TODO: tune this
			Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
			int x = loc.getX(), y = loc.getY();
			int[][] lc = {
				{x+1,y,  5},
				{x+1,y+1,6},
				{x,y+1,  7},
				{x-1,y+1,8},
				{x-1,y,  1},
				{x-1,y-1,2},
				{x,y-1,  3},
				{x+1,y-1,4},
			};

			Direction bestDir = Direction.Center;
			int value = 100000;
			for(int[] ar : lc) {
				if(ar[0] < 0 || ar[0] >= Globals.width ||
						ar[1] < 0 || ar[1] >= Globals.height) continue;
				int my_value = Globals.home_field[ar[0]][ar[1]];
				if(my_value < value) {
					value = my_value;
					bestDir = Helpers.opposite(dirs[ar[2]]);
				}
			}
			if(value > Math.max(distance_to_enemy/3, 5)) {
				toKarb = bestDir;
				goTowardsKarb = false;
			}
		}
		if(goTowardsKarb)
			toKarb = generateKarbDirection(myKarbs, loc, unit, Globals.rand_permutation);

        boolean shouldReplicate = replicatingrequirements(unit, loc);
        if(Globals.enemy_locations.size()==0) { //add Globals.enemy locations
            PathFinding.updateEnemies();
        }
        if(shouldReplicate && distance_to_enemy>10 && Globals.myPlanet==Planet.Earth) {
            //System.out.println("Because shouldReplicate is true!");
            Globals.nikhil_num_workers += replicateOrMoveHarvest(unit, toKarb, myKarbs);
        } else {
            if(Globals.nikhil_num_workers>=Globals.minworkers && Globals.myPlanet==Planet.Earth) {
                //execute build order
                if(buildRocket(unit, toKarb, myKarbs, units, 8L)==true) {
                    return;
                }
                else if(buildFactory(unit, toKarb, myKarbs, units, 20L)==true){
                    return;
                }
                else {
                    if(Globals.current_round>175 || Globals.doesPathExist==false && Globals.current_round>125) { //rocket cap
                        //blueprint rocket or (replicate or moveharvest)
                        int val = blueprintRocket(unit, toKarb, myKarbs, units, 8L);
                        if(val>=2) { //if blueprintRocket degenerates to replicateOrMoveHarvest()
                            Globals.nikhil_num_workers+=(val-2);
                        } else { //did not degenerate
                            Globals.num_rockets+=val;
                        }
                    }
                    else if( (Globals.doesPathExist && Globals.num_factories<6) || (Globals.doesPathExist && Globals.width>35 && ((int)Globals.gc.karbonite()>200+(50-Globals.width)) && Globals.num_factories<9) || (!Globals.doesPathExist && Globals.num_factories<3)) { //factory cap
                        //blueprint factory or (replicate or moveharvest)
                        int val = blueprintFactory(unit, toKarb, myKarbs, units, 20L);
                        if(val>=2) { //if blueprintFactory degenerates to replicateOrMoveHarvest()
                            Globals.nikhil_num_workers+=(val-2);
                        } else { //did not degenerate
                            //System.out.println("Building a factory");
                            Globals.num_factories+=val;
                        }
                    }
                    else {
                        if(shouldReplicate) {
                            //System.out.println("Because of the factory limit!");
                            Globals.nikhil_num_workers += replicateOrMoveHarvest(unit, toKarb, myKarbs);
                        } else {
                            workerharvest(unit, toKarb, myKarbs);
                            workermove(unit, toKarb, myKarbs);
                        }
                    }
                }
            } else if(Globals.myPlanet==Planet.Mars) {
                if(shouldReplicate || (int)Globals.gc.karbonite()>300 || Globals.current_round>750) {
                    Globals.nikhil_num_workers += replicateOrMoveHarvest(unit, toKarb, myKarbs);
                }
                else {
                    workerharvest(unit, toKarb, myKarbs);
                    workermove(unit, toKarb, myKarbs);
                }
            } else {
                //replicate or move harvest
                //System.out.println("Because I'm a relic!");
                Globals.nikhil_num_workers += replicateOrMoveHarvest(unit, toKarb, myKarbs);
            }
        }
        return;
    }

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


    public static boolean replicatingrequirements(Unit myunit, MapLocation myLoc) {
        int numworkers = nearbyWorkersFactory(myunit, myLoc, 4L).size();
        long totalkarb=0L;
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        for(Direction dir: dirs) {
            MapLocation newLoc = myLoc.add(dir);
            if(Globals.gc.canSenseLocation(newLoc)) {
                totalkarb+=Globals.gc.karboniteAt(newLoc);
            }
            MapLocation againLoc = newLoc.add(dir);
            if(Globals.map.onMap(againLoc)) {
                if(Globals.connected_components[myLoc.getX()][myLoc.getY()]==Globals.connected_components[againLoc.getX()][againLoc.getY()]) {
                    if(Globals.gc.canSenseLocation(againLoc)) {
                        totalkarb+=Globals.gc.karboniteAt(againLoc);
                    }
                }
            }
        }
        //System.out.println(totalkarb);
        if(numworkers==0) {
            numworkers=1;
        }
        if(totalkarb/((long)numworkers)-(Globals.nikhil_num_workers/3)>20L) {
            return true;
        }
        return false;
    }

    public static ArrayList<Unit> nearbyWorkersRocket(Unit myUnit, MapLocation myLoc, long rad) {
        VecUnit myWorkers = Globals.gc.senseNearbyUnitsByTeam(myLoc, rad, Globals.ally);
        ArrayList<Unit> siceWorkers = new ArrayList<Unit>();
        for(int i=0; i<myWorkers.size(); i++) {
            Unit k = myWorkers.get(i);
            if(k.unitType()==UnitType.Worker) {
                siceWorkers.add(k);
            }
        }
        return siceWorkers;
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

    public static Direction nearestKarboniteDir(Unit unit, MapLocation myLoc, int visionrad) {
        int visrad = visionrad;
        long totalkarb = 0L;
        int x = myLoc.getX();
        int y = myLoc.getY();
        for (int i=Math.max(x-visrad, 0); i<Math.min(x+visrad+1,(int)Globals.map.getWidth()+1); i++) {
            for (int j=Math.max(0,y-visrad); j<Math.min(y+visrad+1,(int)Globals.map.getHeight()+1); j++) {
                MapLocation m = new MapLocation(Globals.myPlanet, i, j);
                if(((x-i)*(x-i) + (y-j)*(y-j))<unit.visionRange()) {
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
            boolean bob = false;
            for(KarbDir k: myKarbs) {
                if(Globals.gc.canMove(unit.id(), k.dir)) {
                    if(k.karb > 0L) {
                        toKarb = k.dir;
                        bob=true;
                        break;
                    }
                }
            }
            if(bob==false) {
                if(distance > 5) {
                    toKarb = PathFinding.fuzzyMoveDir(unit, toKarb);
                } else {
                    if(toNearest == null)
                        toNearest = nearestKarboniteDir(unit, loc, 7);
                    if(toNearest != null) toKarb = toNearest;
                    else if(Globals.current_round < (Globals.width+Globals.height)/2) {
                        toKarb = PathFinding.fuzzyMoveDir(unit, loc.directionTo(new MapLocation(Globals.myPlanet,
                                    Globals.width/2, Globals.height/2)));
                    } else {
                        toKarb = PathFinding.moveOnRandomFieldDir(unit, loc);
                    }
                }
            }
        }
        return toKarb;
    }

     public static int replicateOrMoveHarvest(Unit unit, Direction toKarb, ArrayList<KarbDir> myKarbs) {
        workerharvest(unit, toKarb, myKarbs);
        workermove(unit, toKarb, myKarbs);
        MapLocation myLoc = unit.location().mapLocation();
        if(Globals.nikhil_num_workers<150) {
            if(Globals.current_round<(Globals.width+Globals.height)/2) {
                if(Globals.movement_field[myLoc.getX()][myLoc.getY()].size()>0) {
                    Direction optimalDir = Globals.movement_field[myLoc.getX()][myLoc.getY()].get(0);
                    if(Globals.gc.canReplicate(unit.id(), optimalDir)) {
                        //System.out.println("Replicating my ass off");
                        //System.out.println(myLoc);
                        Globals.gc.replicate(unit.id(), optimalDir);
                        return 1;
                    }
                }
            }
            if(Globals.gc.canReplicate(unit.id(), toKarb)) {
                //System.out.println("Replicating my ass off");
                //System.out.println(myLoc);
                Globals.gc.replicate(unit.id(), toKarb);
                return 1;
            } else {
                for (KarbDir k : myKarbs) {
                    if(Globals.gc.canReplicate(unit.id(), k.dir)) {
                        //System.out.println("Replicating my ass off");
                        //System.out.println(myLoc);
                        Globals.gc.replicate(unit.id(), k.dir);
                        return 1;
                    }
                }
            }
        }
        return 0;
    }

     public static Direction optimalDirectionFactory(Unit myUnit, MapLocation myLoc, ArrayList<Unit> closeWorkers) {
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        long shortestdist = 10000000000000L;
        Direction bestdir=null;
        for (Direction dir: dirs) {
            if(Globals.gc.canBlueprint(myUnit.id(), UnitType.Factory, dir)) {
                MapLocation newLoc = myLoc.add(dir);
                if(checkFactoryDirections(newLoc)>=3) {
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
        }
        return bestdir;
    }

    public static int checkFactoryDirections(MapLocation factoryLocation) {
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        int passterr=0;
        for (Direction dir: dirs) {
            MapLocation newLoc = factoryLocation.add(dir);
            if(Globals.map.onMap(newLoc)) {
                if(Globals.map.isPassableTerrainAt(newLoc)!=0) { //it is passible
                    passterr+=1;
                }
            }
        }
        return passterr;
    }

    public static ArrayList<Unit> nearbyWorkersFactory(Unit myUnit, MapLocation myLoc, long rad) {
        VecUnit myWorkers = Globals.gc.senseNearbyUnitsByTeam(myLoc, rad, Globals.ally);
        ArrayList<Unit> siceWorkers = new ArrayList<Unit>();
        for(int i=0; i<myWorkers.size(); i++) {
            Unit k = myWorkers.get(i);
            if(k.unitType()==UnitType.Worker) {
                siceWorkers.add(k);
            }
        }
        return siceWorkers;
    }

     public static int blueprintFactory(Unit unit, Direction toKarb, ArrayList<KarbDir> myKarbs, ArrayList<Unit> units, long rad) {
        MapLocation myLoc = unit.location().mapLocation();
        ArrayList<Unit> closeWorkers = nearbyWorkersFactory(unit, myLoc, rad);
        if(closeWorkers.size()>2) { //includes the original worker, we want three Globals.workers per factory
            Direction blueprintDirection = optimalDirectionFactory(unit, myLoc, closeWorkers);
            if(blueprintDirection!=null) {
                if(PathFinding.getNearestNonWorkerEnemy(myLoc, Globals.gc.senseNearbyUnitsByTeam(myLoc, unit.visionRange(), Globals.enemy))>50) {
                    Globals.gc.blueprint(unit.id(), UnitType.Factory, blueprintDirection);
                    return 1;
                } else {
                    //should not build blueprint, it will die
                    workerharvest(unit, toKarb, myKarbs);
                    workermove(unit, toKarb, myKarbs);
                    return 0;
                }
            } else {
                //cannot build blueprint
                workerharvest(unit, toKarb, myKarbs);
                workermove(unit, toKarb, myKarbs);
                return 0;
            }
        } else {
            //not enough close Globals.workers
            return (2+replicateOrMoveHarvest(unit, toKarb, myKarbs)); //2+ lets parent method determine whether we replicated or not
        }
    }

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

    public static int blueprintRocket(Unit unit, Direction toKarb, ArrayList<KarbDir> myKarbs, ArrayList<Unit> units, long rad) {
        MapLocation myLoc = unit.location().mapLocation();
        ArrayList<Unit> closeWorkers = nearbyWorkersRocket(unit, myLoc, rad);
        if(closeWorkers.size()>0) { //includes the original worker, we want three Globals.workers per factory
            Direction blueprintDirection = optimalDirectionRocket(unit, myLoc, closeWorkers);
            if(blueprintDirection!=null) {
                Globals.gc.blueprint(unit.id(), UnitType.Rocket, blueprintDirection);
                return 1;
            } else {
                //cannot build blueprint
                workerharvest(unit, toKarb, myKarbs);
                workermove(unit, toKarb, myKarbs);
                return 0;
            }
        } else {
            //not enough close Globals.workers
            return (2+replicateOrMoveHarvest(unit, toKarb, myKarbs)); //2+ lets parent method determine whether we replicated or not
        }
    }

    public static boolean buildRocket(Unit unit, Direction toKarb, ArrayList<KarbDir> myKarbs, ArrayList<Unit> units, long rad) {
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
                    workerharvest(unit, toKarb, myKarbs);
                    Direction toRocket = unit.location().mapLocation().directionTo(nearbyRockets.get(0).location().mapLocation());
                    PathFinding.fuzzyMove(unit, toRocket);
                    return true;
                }
            }
        }
        return false;
    }

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

    public static void workerharvest(Unit unit, Direction toKarb, ArrayList<KarbDir> myKarbs) {
        MapLocation myLoc = unit.location().mapLocation();
        for (KarbDir k : myKarbs) {
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
            if(toKarb!=Direction.Center) {
                PathFinding.fuzzyMove(unit, toKarb);
            } else {
                PathFinding.moveOnRandomField(unit, myLoc);
            }
        }
        return;
    }

    public static boolean buildFactory(Unit unit, Direction toKarb, ArrayList<KarbDir> myKarbs, ArrayList<Unit> units, long rad) {
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
                    workerharvest(unit, toKarb, myKarbs);
                    Direction toFactory = unit.location().mapLocation().directionTo(nearbyFactories.get(0).location().mapLocation());
                    PathFinding.fuzzyMove(unit, toFactory);
                    return true;
                }
            }
        }
        return false;
    }

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
            ret = 4;
        else if(dist<15)
            ret = 5;
        else if(dist<60 && karbfactor>200)
            ret = 12;
        else if(dist<60)
            ret = 10;
        else if(karbfactor>400)
            ret = 16;
        else if(karbfactor>200)
            ret = 12;
        else
            ret = 10;
        System.out.println("Karb Factor: "+karbfactor+" Path length: "+dist+" Worker Ratio: "+ret);
        return ret;
    }
}
