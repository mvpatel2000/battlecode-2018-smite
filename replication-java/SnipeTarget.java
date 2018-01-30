import java.util.*;
import bc.*;

public class SnipeTarget
{
	Unit me;
	int round_hits;
	MapLocation loc;
	public SnipeTarget(Unit me, MapLocation loc, int round_hits) 
	{
		this.me = me;
		this.round_hits = round_hits;
		this.loc = loc;
	}
}
