import bc.*;
import java.util.*;

public class Factory {
	 public static void produceUnit(Unit unit, MapLocation myloc) {
        if(!(Globals.gc.canProduceRobot(unit.id(), UnitType.Ranger) && Globals.gc.canProduceRobot(unit.id(), UnitType.Healer) &&
            Globals.gc.canProduceRobot(unit.id(), UnitType.Knight) && Globals.gc.canProduceRobot(unit.id(), UnitType.Mage)))
            return;

		int distance_to_enemy = Globals.distance_field[myloc.getX()][myloc.getY()];
		
		int num_nonworkers = 0;
		VecUnit units = Globals.gc.units();
		for(int x=0; x<units.size(); x++) {
			Unit t = units.get(x);
			if(t.team() == Globals.ally) continue;
			if(t.unitType() != UnitType.Worker)
				num_nonworkers++;
		}
		VecUnit nearenemies = Globals.gc.senseNearbyUnitsByTeam(myloc, 7, Globals.enemy);
		int num_rangers_near = 0;
		for(int x=0; x<nearenemies.size(); x++) {
			if(nearenemies.get(x).unitType() == UnitType.Ranger)
				num_rangers_near++;
		}

        if(distance_to_enemy<=6 && num_rangers_near<3 && num_nonworkers<=12)
            Globals.gc.produceRobot(unit.id(),UnitType.Knight);
        else if(Globals.num_workers<2 && Globals.gc.canProduceRobot(unit.id(), UnitType.Worker))
            Globals.gc.produceRobot(unit.id(),UnitType.Worker);
        else if(Globals.current_round>550 && Globals.num_workers<4 && Globals.gc.canProduceRobot(unit.id(), UnitType.Worker))
            Globals.gc.produceRobot(unit.id(),UnitType.Worker);
        else if(Globals.num_rangers<7)
            Globals.gc.produceRobot(unit.id(), UnitType.Ranger);
        else if(Globals.num_rangers>30 && (Globals.num_rangers)/(1.0*Globals.num_healers)>3.0/2.0)
            Globals.gc.produceRobot(unit.id(), UnitType.Healer);
        else if((Globals.num_rangers-4)/(1.0*Globals.num_healers)>2.0/1.0)
            Globals.gc.produceRobot(unit.id(), UnitType.Healer);
        else if(Globals.num_rangers<60 || (int)Globals.gc.karbonite()>500)
            Globals.gc.produceRobot(unit.id(), UnitType.Ranger);
    }

	 //Attempts to unload unit in direction as best as possible from factory
    public static void fuzzyUnload(Unit unit, Direction dir) {
        Direction[] dirs = {Direction.East, Direction.Northeast, Direction.North, Direction.Northwest,
                                Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
        int[] shifts = {0, -1, 1, -2, 2, -3, 3, 4};
        int dirindex = 0;
        for(int i=0; i<8; i++) {
            if(dir==dirs[i]) {
                dirindex = i;
                break;
            }
        }
        for(int i=0; i<shifts.length; i++) {
            if(Globals.gc.canUnload(unit.id(), dirs[ (dirindex+shifts[i]+8)%8 ])) {
                Globals.gc.unload(unit.id(), dirs[ (dirindex+shifts[i]+8)%8 ]);
            }
        }
    }

	 public static void runFactory(Unit unit, MapLocation myloc) {
        Globals.factories_active++;
        if( (Globals.current_round<451 || Globals.current_round>450 && Globals.factories_active<3) && //only 2 factories after round 600
            (Globals.current_round<700 || Globals.current_round<600 && Globals.doesPathExist==false)) {  //no production in final rounds
            produceUnit(unit, myloc);
        }
        Direction unload_dir = Direction.East;
        if(Globals.enemy_locations.size()>0) {
            int[] enemy_direction = Globals.enemy_locations.get(0);
            unload_dir = myloc.directionTo(new MapLocation(Globals.myPlanet, enemy_direction[0], enemy_direction[1]));
        }
        fuzzyUnload(unit, unload_dir);
    }
}
