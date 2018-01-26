import bc.*;
import java.util.*;

public class Mage {
  public static void runMage(Unit unit, MapLocation myloc) {
        VecUnit enemies_in_sight = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), Globals.enemy);
        if(enemies_in_sight.size()>0) {      //combat state
            Unit nearestUnit = PathShits.getNearestUnit(myloc, enemies_in_sight); //move in a better fashion
            MapLocation nearloc = nearestUnit.location().mapLocation();
            PathShits.fuzzyMove(unit, myloc.directionTo(nearloc)); //move in a better way

            VecUnit enemies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), Globals.enemy);
            if(enemies_in_range.size()>0) {
                mageAttack(unit, myloc, enemies_in_range);
            }
        }
        else { //non-combat state
            if( (Globals.doesPathExist==false && Globals.myPlanet==Planet.Earth && Globals.rocket_homing==0) || Globals.enemy_locations.size()==0) {
                PathShits.moveOnRandomField(unit, myloc);
            }
            else
                PathShits.moveOnVectorField(unit, myloc);
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
}