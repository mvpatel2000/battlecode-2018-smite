import bc.*;
import java.util.*;

public class Mage {
  public static void runMage(Unit unit, MapLocation myloc) {        
        VecUnit enemies_in_blink = Globals.gc.senseNearbyUnitsByTeam(myloc, 56L, Globals.enemy);
        if(enemies_in_blink.size()>0 && Globals.gc.isMoveReady(unit.id())) {
            Unit nearestUnit = PathShits.getNearestUnit(myloc, enemies_in_blink);
            MapLocation nearloc = nearestUnit.location().mapLocation();
            if(nearestUnit.unitType()!=UnitType.Knight) {
                Direction blinkdir = myloc.directionTo(nearloc);
                //System.out.println("1. "+myloc+" "+myloc.distanceSquaredTo(nearloc));
                mageBlink(unit, myloc, blinkdir);
                unit = Globals.gc.unit(unit.id());
                myloc = unit.location().mapLocation();
                //System.out.println("2. "+myloc+" "+myloc.distanceSquaredTo(nearloc));
            }
        }        
        VecUnit enemies_in_sight = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.visionRange(), Globals.enemy);
        if(enemies_in_sight.size()>0) {      //combat state
            Unit nearestUnit = PathShits.getNearestUnit(myloc, enemies_in_sight); //get nearest unit
            MapLocation nearloc = nearestUnit.location().mapLocation();
            int distance = (int)myloc.distanceSquaredTo(nearloc);

            Direction movedir = null;
            if(nearestUnit.unitType()==UnitType.Knight || distance<3L) //repel knight
                movedir = nearloc.directionTo(myloc);
            else 
                movedir = myloc.directionTo(nearloc);
            PathShits.fuzzyMove(unit, nearloc.directionTo(myloc));        

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

    //blinks as best as possible in optimal direction
    public static void mageBlink(Unit unit, MapLocation myloc, Direction movedir) {
        if(!Globals.gc.isBlinkReady(unit.id()))
            return;

        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        int[] shifts = {-1, 0, 1};
        int dirindex = 0;
        for(int i=0; i<8; i++) {
            if(movedir==dirs[i]) {
                dirindex = i;
                break;
            }
        }

        MapLocation bestmove = null;
        int bestscore = 0;
        for(int xctr=0; xctr<3; xctr++) {
            MapLocation shift_1 = myloc.add( dirs[(dirindex+xctr+8)%8] );
            for(int yctr=0; yctr<3; yctr++) {
                MapLocation shift_2 = shift_1.add( dirs[(dirindex+yctr+8)%8] );
                int x = shift_2.getX();
                int y = shift_2.getY();
                if(x>=0 && x<Globals.width && y>=0 && y<Globals.height && Globals.gc.isOccupiable(shift_2)!=0) {
                    int shiftscore = 50*50+1 - Globals.distance_field[x][y];
                    if(shiftscore>bestscore) {
                        bestscore = shiftscore;
                        bestmove = shift_2;
                    }
                }
            }
        }

        for(int yctr=0; yctr<3; yctr++) {
            MapLocation shift_2 = myloc.add( dirs[(dirindex+yctr+8)%8] );
            int x = shift_2.getX();
            int y = shift_2.getY();
            if(x>=0 && x<Globals.width && y>=0 && y<Globals.height && Globals.gc.isOccupiable(shift_2)!=0) {
                int shiftscore = 50*50+1 - Globals.distance_field[x][y];
                if(shiftscore>bestscore) {
                    bestscore = shiftscore;
                    bestmove = shift_2;
                }
            }
        }
        
        if(bestmove!=null && Globals.gc.canBlink(unit.id(), bestmove)) {
            //System.out.println("BLINKED! "+Globals.current_round+" "+myloc+" "+bestmove+" [Distance]: "+(50*50+1-bestscore)+" "+Globals.distance_field[myloc.getX()][myloc.getY()]);
            Globals.gc.blink(unit.id(), bestmove);
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