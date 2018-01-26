import bc.*;
import java.util.*;

public class Rocket {
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
            PathShits.buildFieldBFS();
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
}
