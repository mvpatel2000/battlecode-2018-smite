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

        PathShits.buildFieldBFS();       //pathing
        PathShits.buildRandomField();

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

        Globals.paths = new HashMap<>();
        Globals.minworkers = Worker.workerReplicateRatio();
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
                    PathShits.buildRandomField();
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
                        Globals.karbonite_path = PathShits.karbonitePath(new int[] {0, 20});
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
                                Worker.runWorker(unit, myloc, units);
                            } catch(Exception e) {
                                System.out.println("Error: "+e);
                            }
                        }

                        // RANGER CODE //
                        //TODO: make rangerAttack not a sort
                        else if(unit.unitType()==UnitType.Ranger && unit.rangerIsSniping()==0) {
                            try {
                                Ranger.runRanger(unit, myloc);
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
                                Knight.runKnight(unit, myloc);
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
                                Mage.runMage(unit, myloc);
                            } catch(Exception e) {
                                System.out.println("Error: "+e);
                            }
                        }

                        // HEALER CODE //
                        //TODO: Verify overcharge
                        //TODO: Update overcharge priority to overcharge unit closest to Globals.enemy via distance field
                        else if(unit.unitType()==UnitType.Healer) {
                            try {
                                Healer.runHealer(unit, myloc);
                            } catch(Exception e) {
                                System.out.println("Error: "+e);
                            }
                        }

                        // FACTORY CODE //
                        //TODO: Anti-samosa unloading
                        else if(unit.unitType()==UnitType.Factory && unit.structureIsBuilt()!=0) {
                            try {
                                Factory.runFactory(unit, myloc);
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
                                Rocket.runRocket(unit, myloc);
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
                            Worker.runWorker(myUnit, myUnit.location().mapLocation(), afterunits);
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


    public static ArrayList<Integer> randomPermutation(int l) {
        ArrayList<Integer> a = new ArrayList<>();
        for(int x=0; x<l; x++) {
            a.add(x);
        }
        Collections.shuffle(a);
        return a;
    }
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

    public static long countKarbonite() {
        long totalkarb = 0L;
        for (int i=0; i<Globals.width; i++) {
            for(int j=0; j<Globals.width; j++) {
                totalkarb += Globals.map.initialKarboniteAt(new MapLocation(Globals.myPlanet, i,j));
            }
        }
        return totalkarb;
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

}
