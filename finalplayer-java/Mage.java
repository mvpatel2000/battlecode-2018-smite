import bc.*;
import java.util.*;

public class Mage {
  public static void runMage(Unit unit, MapLocation myloc) {
        VecUnit enemies_in_sight = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), Globals.enemy);
        if(enemies_in_sight.size()>0) {      //combat state
            Unit nearestUnit = PathShits.getNearestUnit(myloc, enemies_in_sight); //get nearest unit
            MapLocation nearloc = nearestUnit.location().mapLocation();
            int distance = (int)myloc.distanceSquaredTo(nearloc);
            if(nearestUnit.unitType()==UnitType.Knight || distance<3L) //repel knight
                PathShits.fuzzyMove(unit, nearloc.directionTo(myloc));
            else
                PathShits.fuzzyMove(unit, myloc.directionTo(nearloc));

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
        VecUnit heuristic_enemies = Globals.gc.senseNearbyUnits(myloc, 42L);
        int[][] heuristics = mageAttackHeuristic(unit, myloc, heuristic_enemies);
        int target_score = 0;
        Unit target_enemy = null;
        for(int i=0; i<enemies_in_range.size(); i++) {
            Unit enemy = enemies_in_range.get(i);
            MapLocation enemloc = enemy.location().mapLocation();
            int enemy_score = heuristics[enemloc.getX()][enemloc.getY()];
            if(enemy_score>target_score) {
                target_score = enemy_score;
                target_enemy = enemy;
            }
        }
        if(target_enemy!=null && Globals.gc.canAttack(unit.id(), target_enemy.id())) {
            Globals.gc.attack(unit.id(), target_enemy.id());
        }
    }

    //mage unti heuristic
    public static int[][] mageAttackHeuristic(Unit unit, MapLocation myloc, VecUnit enemies_in_range) {
        int curwidth = 0;
        int curheight = 0;
        if(Globals.myPlanet==Planet.Earth) {
            curwidth = Globals.width;
            curheight = Globals.height;
        }
        else {
            curwidth = Globals.mars_width;
            curheight = Globals.mars_height;
        }
        int[][] heuristics = new int[curwidth][curheight];
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
            UnitType[] priorities = {UnitType.Worker, UnitType.Knight, UnitType.Mage, UnitType.Ranger, UnitType.Healer}; //unit priorities
            for(int utctr=0; utctr<priorities.length; utctr++) {
                if(enemyType == priorities[utctr]) {
                    hval+=10*utctr; //later units have higher priorities because weight based on index
                    break;
                }
            }
            if(enemy.team()==Globals.ally)
                hval = hval*-1;
            MapLocation enemloc = enemy.location().mapLocation();
            int x = enemloc.getX();
            int y = enemloc.getY();
            int[] shifts = {-1, 0, 1};
            for(int xs = 0; xs<shifts.length; xs++) {
                for(int ys=0; ys<shifts.length; ys++) {
                    int xtemp = x+shifts[xs];
                    int ytemp = y+shifts[ys];
                    if(xtemp>=0 && xtemp<curwidth && ytemp>=0 && ytemp<curheight)
                        heuristics[xtemp][ytemp]+=hval;
                }
            }
        }
        return heuristics;
    }
}
