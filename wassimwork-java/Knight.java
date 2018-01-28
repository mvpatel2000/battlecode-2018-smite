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

			/*for(int x=0; x<enemies_in_range.size(); x++) {
				Unit u = enemies_in_range.get(x);
				if(u.team() != Globals.ally && (u.unitType() == UnitType.Factory || u.unitType() == UnitType.Rocket))
					return; // if we're near a factory or rocket, stay there
			}*/
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
			Queue<Direction> path =
				Helpers.astar(unit, loc, true);
			if(path.size() < detour_size+4) {
				PathShits.fuzzyMove(unit, myloc.directionTo(loc));
				Globals.paths.put(unit.id(), new LinkedList<Direction>()); // re-run A* later
				return;
			}
		}
		

		boolean toMove = false;
		ArrayList<FactoryDist> factories = new ArrayList<>(); // or Rockets
		if(!Globals.paths.containsKey(unit.id()) || Globals.paths.get(unit.id()).size() == 0) {
			VecUnit units = Globals.gc.units();
			for(int x=0; x<units.size(); x++) {
				if(units.get(x).team() == Globals.ally) continue;
				if(units.get(x).unitType() == UnitType.Factory || units.get(x).unitType() == UnitType.Rocket) {
					factories.add(new FactoryDist(units.get(x),
							units.get(x).location().mapLocation().distanceSquaredTo(unit.location().mapLocation())));
					
				}
			}
			Collections.sort(factories);
			for(int x=0; x<factories.size(); x++) {
				Queue<Direction> path =
					Helpers.astar(unit, factories.get(x).factory.location().mapLocation(), true);//false);//true);
				if(path.size() > 0) {
					Globals.paths.put(unit.id(), path);
					//System.out.println("R: "+Globals.current_round+" "+path);
					toMove = true;
					break;
				}
			}
		} else toMove = true;
		
		if(!toMove) {
											// TODO: use Globals.enemy_locations
			VecUnit enemies_in_sight = Globals.gc.senseNearbyUnitsByTeam(myloc, 1000, Globals.enemy);
			if(enemies_in_sight.size()>0) {      //combat state
				Unit nearestUnit = PathShits.getNearestUnit(myloc, enemies_in_sight); //move in a better fashion
				MapLocation nearloc = nearestUnit.location().mapLocation();
				PathShits.fuzzyMove(unit, myloc.directionTo(nearloc));
				return;
			}
		}
		if(!toMove) {
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
		}

		if(toMove) {
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
		}
	}

	//knight attack prioritization
	//1. anything that u can kill
	//2. attack factories then rockets
	//Tiebreaker weakest
	//Tiebreaker again: mages > healers > knights > workers > rangers
	public static void knightAttack(Unit unit, VecUnit enemies_in_range) {
		if(!Globals.gc.isAttackReady(unit.id()))
			return;
		int[][] heuristics = new int[(int)enemies_in_range.size()][2];
		boolean hasFactory = false;
		boolean hasRocket = false;
		for(int x=0; x<enemies_in_range.size(); x++) {
			if(enemies_in_range.get(x).unitType() == UnitType.Rocket)
				hasRocket = true;
			if(enemies_in_range.get(x).unitType() == UnitType.Factory)
				hasFactory = true;
		}
		for(int i=0; i<enemies_in_range.size(); i++) {
			int hval = 0;
			Unit myenemy = enemies_in_range.get(i);
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
}
