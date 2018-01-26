import bc.*;
import java.util.*;

public class Ranger {
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

        public static void runRanger(Unit unit, MapLocation myloc) {
        VecUnit enemies_in_sight = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), Globals.enemy);
        if(enemies_in_sight.size()>0) {      //combat state
            if(Globals.enemy_locations.size()==0) { //add Globals.enemy locations
                PathShits.updateEnemies();
            }

            PathShits.checkVectorField(unit, myloc);
            VecUnit enemies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), Globals.enemy);
            if(enemies_in_range.size()==0) {    //move towards Globals.enemy since nothing in attack range
                Unit nearestUnit = PathShits.getNearestUnit(myloc, enemies_in_sight);
                MapLocation nearloc = nearestUnit.location().mapLocation();
                PathShits.fuzzyMove(unit, myloc.directionTo(nearloc));
            }
            enemies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), Globals.enemy);

            if(enemies_in_range.size()>0) {
                rangerAttack(unit, myloc, enemies_in_range); //attack based on heuristic
                if(Globals.gc.isMoveReady(unit.id())) {  //move away from nearest unit to survive
                    if(true)  { //retreat //ADD CHARGE MECHANIC HERE
                        Direction toMoveDir = PathShits.getNearestNonWorkerOppositeDirection(myloc, enemies_in_range);
                        PathShits.fuzzyMove(unit, toMoveDir);
                    }
                    else { //charge
                        PathShits.moveOnVectorField(unit, myloc);
                    }
                }
            }
        }
        else { //non-combat state
            if( (Globals.doesPathExist==false && Globals.myPlanet==Planet.Earth && Globals.rocket_homing==0) || Globals.enemy_locations.size()==0) {
                PathShits.moveOnRandomField(unit, myloc);
            }
            else {
                PathShits.moveOnVectorField(unit, myloc);
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
}