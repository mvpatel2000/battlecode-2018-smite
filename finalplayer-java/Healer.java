import bc.*;
import java.util.*;

public class Healer 
{
	 public static void runHealer(Unit unit, MapLocation myloc) {
        VecUnit enemies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, Globals.maxVisionRange, Globals.enemy);
        if(true && enemies_in_range.size()>0) {      //combat state //ADD CHARGE MECHANIC
            if(Globals.enemy_locations.size()==0) { //add Globals.enemy locations
                PathShits.updateEnemies();
            }
            Direction toMoveDir = PathShits.getNearestNonWorkerOppositeDirection(myloc, enemies_in_range);
            PathShits.fuzzyMove(unit, toMoveDir);
        }
        else { //non-combat state
            if( (Globals.doesPathExist==false && Globals.myPlanet==Planet.Earth && Globals.rocket_homing==0) || Globals.enemy_locations.size()==0) {
                PathShits.moveOnRandomField(unit, myloc);
            }
            else {
                PathShits.moveOnVectorField(unit, myloc);
            }
        }
        healerHeal(unit, myloc);
        healerOvercharge(unit, myloc);
    }

    public static void healerOvercharge(Unit unit, MapLocation myloc) {
        if(!Globals.gc.isOverchargeReady(unit.id()))
            return;
        VecUnit allies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.abilityRange(), Globals.ally);
        if(allies_in_range.size()<=0)
            return;
        Unit ally_to_heal = null;
        int ally_score = 0;
        for(int i=0; i<allies_in_range.size(); i++) {
            Unit test_to_heal = allies_in_range.get(0);
            if(test_to_heal.unitType()!=UnitType.Ranger)
                continue;
            int test_score = 0;
            int attack_heat = (int)test_to_heal.attackHeat();
            int movement_heat = (int)test_to_heal.movementHeat();
            if(attack_heat>=20)
                test_score+=2000;
            else if(attack_heat>=10)
                test_score+=1000;
            if(movement_heat>=20)
                test_score+=200;
            else if(movement_heat>=10)
                test_score+=100;
            if(test_score>ally_score) {
                ally_to_heal = test_to_heal;
                ally_score = test_score;
            }
        }
        if(ally_to_heal!=null && Globals.gc.canOvercharge(unit.id(), ally_to_heal.id())) {
            Globals.gc.overcharge(unit.id(), ally_to_heal.id());
            Ranger.runRanger(ally_to_heal, ally_to_heal.location().mapLocation());
        }
    }

    //heal lowest hp unit in range
    public static void healerHeal(Unit unit, MapLocation myloc) {
        if(!Globals.gc.isHealReady(unit.id()))
            return;
        VecUnit allies_in_range = Globals.gc.senseNearbyUnitsByTeam(myloc, unit.attackRange(), Globals.ally);
        if(allies_in_range.size()==0)
            return;
        Unit ally_to_heal = allies_in_range.get(0);
        int ally_damage = (int)(ally_to_heal.maxHealth()-ally_to_heal.health());
        for(int i=1; i<allies_in_range.size(); i++) {
            Unit test_ally = allies_in_range.get(i);
            int test_damage = (int)(test_ally.maxHealth()-test_ally.health());
            if(test_damage>ally_damage) {
                ally_to_heal = test_ally;
                ally_damage = test_damage;
            }
        }
        if(ally_damage>0 && Globals.gc.canHeal(unit.id(), ally_to_heal.id())) {
            Globals.gc.heal(unit.id(), ally_to_heal.id());
        }
    }
}