import java.util.*;
import bc.*;

/* moving and path related shits */

public class Pathfinding {

    public static void updateFactoryField() {
		VecUnit factories = Globals.gc.senseNearbyUnitsByType(new MapLocation(Globals.myPlanet, Globals.width/2, Globals.height/2), Globals.width*Globals.height/2, UnitType.Factory);
        Globals.enemy_factories = new ArrayList<int[]>();
        for(int i = 0; i<factories.size(); i++) {
            Unit factory = factories.get(i);
            if(factory.team() == Globals.enemy) {
                MapLocation enem_loc = factory.location().mapLocation();
                int[] building_info = {enem_loc.getX(), enem_loc.getY(), 0, 0};
                Globals.enemy_factories.add(building_info);
            }
        }
        buildFactoryField();
    }

    public static void updateFieldWithBuildings() {
        VecUnit total_enemies = Globals.gc.senseNearbyUnitsByTeam(new MapLocation(Globals.myPlanet, Globals.width/2, Globals.height/2), Globals.width*Globals.height/2, Globals.enemy); //all enemies
        int initsize = Globals.enemy_locations.size();
        for(int i = 0; i<total_enemies.size(); i++) {
            Unit enemy_unit = total_enemies.get(i);
            boolean isDuplicate = false;
            if(enemy_unit.unitType()==UnitType.Factory || enemy_unit.unitType()==UnitType.Rocket) { //if factory
                MapLocation enem_loc = enemy_unit.location().mapLocation();
                for(int targs=0; targs<Globals.enemy_locations.size(); targs++) { //check if already marked
                    int[] enem_loc_info = Globals.enemy_locations.get(targs);
                    if(enem_loc.getX()==enem_loc_info[0] && enem_loc.getY()==enem_loc_info[1]) {
                        isDuplicate = true;
                        break;
                    }
                }
                if(isDuplicate)
                    continue;
                int[] building_info = {enem_loc.getX(), enem_loc.getY(), 0, 0};
                Globals.enemy_locations.add(building_info);
            }
        }
        if(initsize==Globals.enemy_locations.size())
            buildFieldBFS(); //check if size changed
    }

    public static int getNearestNonWorkerEnemy(MapLocation myloc, VecUnit other_units) {
        Unit nearestUnit = null;
        MapLocation nearloc = null;
        int mindist = 50*50+1;
        for(int i=0; i<other_units.size(); i++) {
            Unit testUnit = other_units.get(i);
            if(testUnit.unitType()!=UnitType.Worker && testUnit.unitType()!=UnitType.Factory && testUnit.unitType()!=UnitType.Rocket) {
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
            return mindist;
        return 10000000;
    }

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

    public static void updateEnemies() {
        VecUnit total_enemies = Globals.gc.senseNearbyUnitsByTeam(new MapLocation(Globals.myPlanet, Globals.width/2, Globals.height/2), Globals.width*Globals.height/2, Globals.enemy);
        for(int eloc = 0; eloc<total_enemies.size(); eloc++) {
            MapLocation enemloc = total_enemies.get(eloc).location().mapLocation();
            int[] enemy_info = {enemloc.getX(), enemloc.getY(), 0, 0};
            Globals.enemy_locations.add(enemy_info);
        }

        if(Globals.enemy_locations.size()>0) {
            buildFieldBFS();
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

    public static void buildFactoryField() {
        Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};

        Queue<int[]> queue = new LinkedList<int[]>();
        for(int i=0; i<Globals.enemy_factories.size(); i++)
            queue.add(Globals.enemy_factories.get(i));
        for(int w=0; w<Globals.width; w++) {
            for(int h=0; h<Globals.height; h++) {
                Globals.factory_field[w][h] = (50*50+1);
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
                    Globals.factory_field[x][y]<=depth) { //is an inferior move
                continue;
            }
            else if(Globals.factory_field[x][y]>depth) { //replace old Directions with more optimal ones
                Globals.factory_field[x][y] = depth;
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

    public static void fuzzyMoveRanger(Unit unit, MapLocation myloc, Direction dir) {
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
                VecUnit enemies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc.add(dirs[ (dirindex+shifts[i]+8)%8 ]), unit.attackRange(), Globals.enemy);
                if(enemies_in_range.size()>0) {
                    boolean invalid = false;
                    for(int ctr=0; ctr<enemies_in_range.size(); ctr++) {
                        if(enemies_in_range.get(ctr).unitType()==UnitType.Ranger) {
                            invalid = true;
                            break;
                        }
                    }
                    if(invalid)
                        continue;
                }
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

	private static void connectedComponentsDFS(int x, int y, int counter) {
		if(x < 0 || x >= Globals.width || y < 0 || y >= Globals.height) return;
		if(Globals.connected_components[x][y] != 0) return;
		MapLocation loc = new MapLocation(Globals.myPlanet, x, y);
		if(Globals.map.isPassableTerrainAt(loc) == 0) { // not passable
			Globals.connected_components[x][y] = -1;
			return;
		}
		Globals.connected_components[x][y] = counter;
		int karb = (int)Globals.map.initialKarboniteAt(loc);
		if(karb > 0)
			Globals.karb_vals.get(counter).add(karb);
		connectedComponentsDFS(x-1, y-1, counter);
		connectedComponentsDFS(x-1, y, counter);
		connectedComponentsDFS(x-1, y+1, counter);
		connectedComponentsDFS(x, y-1, counter);
		connectedComponentsDFS(x, y+1, counter);
		connectedComponentsDFS(x+1, y-1, counter);
		connectedComponentsDFS(x+1, y, counter);
		connectedComponentsDFS(x+1, y+1, counter);
	}
	public static void createConnectedComponents() {
		int counter = 1;
		for(int x=0; x<Globals.width; x++) {
			for(int y=0; y<Globals.height; y++) {
				if(Globals.connected_components[x][y] != 0) continue;
				MapLocation loc = new MapLocation(Globals.myPlanet, x, y);
				if(Globals.map.isPassableTerrainAt(loc) == 0) { // not passable
					Globals.connected_components[x][y] = -1;
					continue;
				}
				Globals.karb_vals.put(counter, new ArrayList<Integer>());
				connectedComponentsDFS(x, y, counter);
				counter++;
			}
		}
	}

	public static void buildHomeField() {
        Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};

        Queue<int[]> queue = new LinkedList<int[]>();
        for(int i=0; i<Globals.map.getInitial_units().size(); i++) {
            Unit unit = Globals.map.getInitial_units().get(i);
            if(Globals.ally == unit.team()) {
				MapLocation loc = unit.location().mapLocation();
				int[] t = {loc.getX(), loc.getY(), 0, 0};
				queue.add(t);
			}
		}

        for(int w=0; w<Globals.width; w++) {
            for(int h=0; h<Globals.height; h++) {
                Globals.home_field[w][h] = (50*50+1);
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
                    Globals.home_field[x][y]<=depth) { //is an inferior move
                continue;
            }
            else if(Globals.home_field[x][y]>depth) { //replace old Directions with more optimal ones
                Globals.home_field[x][y] = depth;
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
