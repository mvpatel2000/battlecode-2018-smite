//https://s3.amazonaws.com/battlecode-2018/logs/matchnumber_0.bc18log

import bc.*;
import java.util.*;

public class Player {
    public static void main(String[] args) {
        Globals.enemy = Team.Red;   //this is evil team
        if(Globals.ally==Team.Red)
            Globals.enemy = Team.Blue;

        if(Globals.myPlanet==Planet.Earth) { //generate landing priorities for rockets
            Rocket.generateLandingPriorities();
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


        if(Globals.myPlanet==Planet.Earth && Globals.doesPathExist==false) { //research
            //50 75 175 275 300 375 //475 550 575 675 775 975
            UnitType[] rarray = {UnitType.Rocket, UnitType.Healer, UnitType.Healer, UnitType.Healer, UnitType.Mage, UnitType.Mage,
                                    UnitType.Mage, UnitType.Mage, UnitType.Ranger, UnitType.Rocket, UnitType.Ranger, UnitType.Ranger}; //research queue
            for(int i=0; i<rarray.length; i++)
                Globals.gc.queueResearch(rarray[i]);
        }
        else if(Globals.myPlanet==Planet.Earth) {
            //25 125 225 250 325 425 //500 550 575 675 775 975
            UnitType[] rarray = {UnitType.Healer, UnitType.Healer, UnitType.Healer, UnitType.Mage, UnitType.Mage, UnitType.Mage,
                                    UnitType.Mage, UnitType.Rocket, UnitType.Ranger, UnitType.Rocket, UnitType.Ranger, UnitType.Ranger}; //research queue
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
            //try {
                Globals.current_round = (int)Globals.gc.round();
                Globals.factories_active = 0; //tracks amount of factories producing units
                if(Globals.current_round%15==0) { //print round number and update random field
                    System.out.println("Current round: "+Globals.current_round+" Current time: "+Globals.gc.getTimeLeftMs());
                    System.runFinalization();
                    System.gc();
                    PathShits.buildRandomField();
                }
                if(Globals.myPlanet==Planet.Earth)
                    Rocket.updateLandingPriorities();
                Ranger.buildSnipeTargets(); //build snipe targets

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
                    //try {
                        Unit unit = units.get(unit_counter);
                        if(unit.location().isInGarrison() || unit.location().isInSpace())
                            continue;
                        MapLocation myloc = unit.location().mapLocation();

                        // WORKER CODE //
                        //TODO: u can do actions before replication but not after
                        //TODO: replication needs to be more aggressive
                        if(unit.unitType()==UnitType.Worker) {
                            //try {
                                Worker.runWorker(unit, myloc, units);
                            //} catch(Exception e) {
                            //    System.out.println("Worker Error: "+e);
                            //}
                        }

                        // RANGER CODE //
                        //TODO: make rangerAttack not a sort
                        else if(unit.unitType()==UnitType.Ranger && unit.rangerIsSniping()==0) {
                            //try {
                                Ranger.runRanger(unit, myloc);
                            //} catch(Exception e) {
                            //    System.out.println("Ranger Error: "+e);
                            //}
                        }

                        // KNIGHT CODE //
                        //TODO: update movement method priority
                        //TODO: Move towards better Globals.enemy
                        //TODO: Figure javelin
                        else if(unit.unitType()==UnitType.Knight) {
                            //try {
                                Knight.runKnight(unit, myloc);
                            //} catch(Exception e) {
                            //    System.out.println("Knight Error: "+e);
                            //}
                        }

                        // MAGE CODE //
                        //TODO: Update Mage attack
                        //TODO: Update movement method priority
                        //TODO: move in a better way
                        //TODO: Figure out blink
                        else if(unit.unitType()==UnitType.Mage) {
                            //try {
                                Mage.runMage(unit, myloc);
                            //} catch(Exception e) {
                            //    System.out.println("Mage Error: "+e);
                            //}
                        }

                        // HEALER CODE //
                        else if(unit.unitType()==UnitType.Healer) {
                            //try {
                                Healer.runHealer(unit, myloc);
                            //} catch(Exception e) {
                            //    System.out.println("Healer Error: "+e);
                            //}
                        }

                        // FACTORY CODE //
                        //TODO: Anti-samosa unloading
                        else if(unit.unitType()==UnitType.Factory && unit.structureIsBuilt()!=0) {
                            //try {
                                Factory.runFactory(unit, myloc);
                            //} catch(Exception e) {
                            //    System.out.println("Factory Error: "+e);
                            //}
                        }

                        // ROCKET CODE //
                        //TODO: make units go away from rocket b4 launch
                        //TODO: optmize launch timing to improve speed
                        //TODO: launch at same time
                        else if(unit.unitType()==UnitType.Rocket && unit.structureIsBuilt()!=0) {
                            //try {
                                Rocket.runRocket(unit, myloc);
                            //} catch(Exception e) {
                            //    System.out.println("Rocket Error: "+e);
                            //}
                        }
                    //} catch(Exception e) {
                    //    System.out.println("Unit Loop Error: "+e);
                    //}
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
                    //try {
                        Unit myUnit = myaddworkers.get(i);
                        if(!myUnit.location().isInGarrison() && !myUnit.location().isInSpace()) {
                            Worker.runWorker(myUnit, myUnit.location().mapLocation(), afterunits);
                        }
                    //} catch(Exception e) {
                    //    System.out.println("Replicated Worker Error: "+e);
                    //}
                }
            //} catch(Exception e) {
            //    System.out.println("Turn Error: "+e);
            //}
            Globals.gc.nextTurn(); // Submit the actions we've done, and wait for our next turn.
        }
    }

    public static ArrayList<Unit> sortUnits(VecUnit units) {
        ArrayList<Unit> ret = new ArrayList<Unit>();
        UnitType[] types = {UnitType.Rocket, UnitType.Ranger, UnitType.Knight, UnitType.Mage};
        for(int i=0; i<types.length; i++) {
            UnitType ut = types[i];
            for(int x=0; x<units.size(); x++) {
                Unit cur = units.get(x);
                if(cur.unitType()==ut)
                    ret.add(cur);
            }
        }

        ArrayList<Unit> healers = new ArrayList<Unit>();
        for(int x=0; x<units.size(); x++) {
            Unit cur = units.get(x);
            if(cur.unitType()==UnitType.Healer && !cur.location().isInSpace() && !cur.location().isInGarrison())
                healers.add(cur);
        }
        if(healers.size()>0) {
            int[][] hsort = new int[healers.size()][2];
            for(int i=0; i<healers.size(); i++) {
                Unit mHealer = healers.get(i);
                MapLocation hloc = mHealer.location().mapLocation();
                hsort[i][0] = Globals.distance_field[hloc.getX()][hloc.getY()];
                hsort[i][1] = i;
            }
            for(int x=0; x<hsort.length-1; x++) {
                for(int y=x+1; y<hsort.length; y++) {
                    if(hsort[x][0] > hsort[y][0]) {
                        int tempdist = hsort[x][0];
                        int tempind = hsort[x][1];
                        hsort[x][0] = hsort[y][0];
                        hsort[x][1] = hsort[y][1];
                        hsort[y][0] = tempdist;
                        hsort[y][1] = tempind;
                    }
                }
            }
            for(int i=0; i<hsort.length; i++) {
                ret.add(healers.get(hsort[i][1]));
            }
        }

        UnitType[] typestwo = {UnitType.Factory, UnitType.Worker};
        for(int i=0; i<typestwo.length; i++) {
            UnitType ut = typestwo[i];
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

}
