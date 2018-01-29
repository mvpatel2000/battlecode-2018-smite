import bc.*;
import java.util.*;

public class Knight {
	static class FactoryDist implements Comparable<FactoryDist> {
		long dist;
		Unit factory;
		public FactoryDist(Unit factory, long distance) {
			dist = distance;
			this.factory = factory;
		}
		public int compareTo(FactoryDist d) {
			return (int)(dist - d.dist);
		}
	}

	public static void runKnight(Unit unit, MapLocation myloc)  {
		VecUnit enemies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), Globals.enemy);
		if(enemies_in_range.size()>0) {
			knightAttack(unit, enemies_in_range);
			for(int x=0; x<enemies_in_range.size(); x++) {
				Unit t = enemies_in_range.get(x);
				if(t.unitType() == UnitType.Knight) {
					// move away
					PathShits.fuzzyMove(unit, Helpers.opposite(myloc.directionTo(t.location().mapLocation())));
					return;
				}
			}
		}
	 																/* TODO: tune this number */
		int detour_size = 5;
		VecUnit close_enemies = Globals.gc.senseNearbyUnitsByTeam(myloc, detour_size, Globals.enemy);
		if(close_enemies.size() > 0) {
			// if enemy is close enough, detour and attack them
			int m_val = -1000;
			Unit to_follow = close_enemies.get(0);
			for(int x=0; x<close_enemies.size(); x++) {
				Unit t = close_enemies.get(x);
				int val = 0;
				if(t.unitType() == UnitType.Ranger)
					val += 5;
				else if(t.unitType() == UnitType.Knight || t.unitType() == UnitType.Mage)
					val += 4;
				else if(t.unitType() == UnitType.Healer)
					val += 3;
				if(val > m_val) {
					m_val = val;
					to_follow = t;
				}
			}
			MapLocation loc = to_follow.location().mapLocation();
			/*Queue<Direction> path =
				Helpers.astar(unit, loc, true);*/

			//if(path.size() < detour_size+4) {
			if(Globals.map.isPassableTerrainAt(myloc.add(myloc.directionTo(loc))) != 0) {
				PathShits.fuzzyMove(unit, myloc.directionTo(loc));
				Globals.paths.put(unit.id(), new LinkedList<Direction>()); // re-run A* later
				return;
			}
		}
		

		boolean toMove = false;

		if(Globals.factory_field[myloc.getX()][myloc.getY()] < 50*50+1) {
			Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
			int x = myloc.getX(), y = myloc.getY();
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
				int my_value = Globals.factory_field[ar[0]][ar[1]];
				if(my_value < value) {
					value = my_value;
					bestDir = Helpers.opposite(dirs[ar[2]]);
				}
			}
			if(value < 100000) {
				PathShits.fuzzyMove(unit, bestDir);
				return;
			}
		}

										// TODO: use Globals.enemy_locations
		VecUnit enemies_in_sight = Globals.gc.senseNearbyUnitsByTeam(myloc, 1000, Globals.enemy);
		if(enemies_in_sight.size()>0) {      //combat state
			Unit nearestUnit = PathShits.getNearestUnit(myloc, enemies_in_sight); //move in a better fashion
			MapLocation nearloc = nearestUnit.location().mapLocation();
			PathShits.fuzzyMove(unit, myloc.directionTo(nearloc));
			return;
		}
		if( (Globals.doesPathExist==false && Globals.myPlanet==Planet.Earth && Globals.rocket_homing==0) || Globals.enemy_locations.size()==0) {
				PathShits.moveOnRandomField(unit, myloc);
			}
			else
				PathShits.moveOnVectorField(unit, myloc);
		
	

/*		if(!toMove) {
			for(int x=0; x<factories.size(); x++) {
				Queue<Direction> path =
					Helpers.astar(unit, factories.get(x).factory.location().mapLocation(), false);
				if(path.size() > 0) {
					Globals.paths.put(unit.id(), path);
					//System.out.println("R2: "+Globals.current_round+" "+path);
					toMove = true;
					break;
				}
			}
		}

		if(!toMove && Globals.enemy_locations.size() > 0) {
			MapLocation m = new MapLocation(Globals.myPlanet, Globals.enemy_locations.get(0)[0], Globals.enemy_locations.get(0)[1]);
			Queue<Direction> path =
				Helpers.astar(unit, m, false);
			if(path.size() > 0) {
				Globals.paths.put(unit.id(), path);
				toMove = true;
			}
		}*/

		/*if(toMove) {
			Direction d = Globals.paths.get(unit.id()).poll();
			if(Globals.gc.isMoveReady(unit.id()) && Globals.gc.canMove(unit.id(), d)) {
				Globals.gc.moveRobot(unit.id(), d);
			} else {
				// trying to move in impassable terrain
				// clear the current path
				// TODO: BOUND NUMBER OF A* calls
				// (THIS PART FORCES A* RE-RUN)
				Globals.paths.put(unit.id(), new LinkedList<Direction>());
			}
		} else { //non-combat state
			if( (Globals.doesPathExist==false && Globals.myPlanet==Planet.Earth && Globals.rocket_homing==0) || Globals.enemy_locations.size()==0) {
				PathShits.moveOnRandomField(unit, myloc);
			}
			else
				PathShits.moveOnVectorField(unit, myloc);
		}*/
	}

	//knight attack prioritization
	//1. anything that u can kill
	//2. attack factories then rockets
	//Tiebreaker weakest
	//Tiebreaker again: mages > healers > knights > workers > rangers
	public static void knightAttack(Unit unit, VecUnit enemies_in_range) {
		if(!Globals.gc.isAttackReady(unit.id()))
			return;
		boolean hasFactory = false;
		boolean hasRocket = false;
		for(int x=0; x<enemies_in_range.size(); x++) {
			if(enemies_in_range.get(x).unitType() == UnitType.Rocket)
				hasRocket = true;
			if(enemies_in_range.get(x).unitType() == UnitType.Factory)
				hasFactory = true;
		}
		UnitType[] priorities = {UnitType.Ranger, UnitType.Worker, UnitType.Knight, UnitType.Mage, UnitType.Healer}; //unit priorities
		Unit best_eligible = null;
		int best_val = -10000;
		for(int i=0; i<enemies_in_range.size(); i++) {
			int hval = 0;
			Unit myenemy = enemies_in_range.get(i);
			if(!Globals.gc.canAttack(unit.id(), myenemy.id()))
				continue;
			UnitType enemyType = myenemy.unitType();
			if(UnitType.Knight==myenemy.unitType() && unit.damage()>(int)myenemy.health()-(int)myenemy.knightDefense()) //is knight and can kill
				hval+=10000;
			else if(unit.damage()>(int)myenemy.health()) //can kill
				hval+=10000;
			if(enemyType==UnitType.Rocket)
				hval+=8000;
			if(enemyType==UnitType.Factory)
				hval+=7000;
			if(enemyType==UnitType.Worker && hasRocket) // workers that are repairing a rocket
				hval+=8500;
			else if(enemyType==UnitType.Worker && hasFactory) // workers that are repairing a factory
				hval+=7500;
			if(UnitType.Knight==myenemy.unitType()) {
				hval += (10-((int)myenemy.health())/(unit.damage()-(int)myenemy.knightDefense()))*1000; //is knight and weakest unit
			}
			else if(myenemy.unitType() == UnitType.Mage) {
				hval += (10-((int)myenemy.health())/(unit.damage()))*500;
			} else if(myenemy.unitType() == UnitType.Ranger) {
				hval += (10-((int)myenemy.health())/(unit.damage()))*400; //weakest unit
			} else {
				hval += (10-((int)myenemy.health())/(unit.damage()))*200; //weakest unit
			}
			for(int utctr=0; utctr<priorities.length; utctr++) {
				if(enemyType == priorities[utctr]) {
					hval+=10*utctr; //later units have higher priorities because weight based on index
					break;
				}
			}
			if(hval > best_val) {
				best_val = hval;
				best_eligible = myenemy;
			}
		}
		if(best_eligible == null) return;
		Globals.gc.attack(unit.id(), best_eligible.id());
	}
}
