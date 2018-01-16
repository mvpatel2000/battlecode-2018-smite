import bc.*;
import java.util.*;


public class Helpers {
	static class Tuple4<A,B,C,D> {
		public final A a;
		public final B b;
		public final C c;
		public final D d;
		public Tuple4(A a, B b, C c, D d) {
			this.a = a; this.b = b;
			this.c = c; this.d = d;
		}
	}
	
	// random integer between i and j, inclusive
	public static int randInt(int i, int j) { 
		return (int)(Math.random()*(j-i+1)+i);
	}

	// random direction, including Center
	public static Direction randDir() {
		switch(randInt(0, 8)) {
			case 0:
				return Direction.North;
			case 1:
				return Direction.Northeast;
			case 2:
				return Direction.East;
			case 3:
				return Direction.Southeast;
			case 4:
				return Direction.South;
			case 5:
				return Direction.Southwest;
			case 6:
				return Direction.West;
			case 7:
				return Direction.Northwest;
			default:
				return Direction.Center;
		}
	}

	public static Direction opposite(Direction d) {
		switch(d) {
			case North:
				return Direction.South;
			case Northeast:
				return Direction.Southwest;
			case East:
				return Direction.West;
			case Southeast:
				return Direction.Northwest;
			case South:
				return Direction.North;
			case Southwest:
				return Direction.Northeast;
			case West:
				return Direction.East;
			case Northwest:
				return Direction.Southeast;
			default:
				return Direction.Center;
		}
	}
	public static ArrayList<Direction> astar(Unit me, MapLocation location) {
		PlanetMap planet = Player.gc.startingMap(Player.gc.planet());
		VecUnit units = Player.gc.senseNearbyUnits(me.location().mapLocation(), me.visionRange());
		HashSet<MapLocation> unitLocations = new HashSet<>();
		for(int x=0; x<units.size(); x++) {
			unitLocations.add(units.get(x).location().mapLocation());
		}

		PriorityQueue<Tuple4<Long, Long, MapLocation, Direction>> pq
			= new PriorityQueue<>(new Comparator<Tuple4<Long,Long,MapLocation,Direction>>() {
				public int compare(Tuple4<Long,Long,MapLocation,Direction> a, Tuple4<Long,Long,MapLocation,Direction> b) {
					if(a.a < b.a) return -1;
					else if(a.a > b.a) return 1;
					else if(a.b < b.b) return -1;
					else if(a.b > b.b) return 1;
					else return 0;
				}
			});
		pq.add(new Tuple4<Long,Long,MapLocation,Direction>(0L, 0L, me.location().mapLocation(), null));
		Map<MapLocation, Direction> vis = new TreeMap<>(new Comparator<MapLocation>() {
			public int compare(MapLocation m1, MapLocation m2) {
				if(m1.getX() < m2.getX()) return -1;
				else if(m1.getX() > m2.getX()) return 1;
				else if(m1.getY() < m2.getY()) return -1;
				else if(m1.getY() > m2.getY()) return 1;
				else return m1.getPlanet().compareTo(m2.getPlanet());
			}
		});
		while(!pq.isEmpty()) {
			Tuple4<Long, Long, MapLocation, Direction> t = pq.remove();
			if(vis.containsKey(t.c)) continue;
			vis.put(t.c, t.d);
			if(t.c == location) break;
			for(Direction dir : Direction.values()) {
				if(dir == Direction.Center) continue;
				MapLocation adj = t.c.add(dir);
				if(vis.containsKey(adj)) continue;
				if(unitLocations.contains(adj) || !planet.onMap(adj) || planet.isPassableTerrainAt(adj) == 0)
					continue;
				Long h = new Long(Math.abs(location.getX() - adj.getX())
						+ Math.abs(location.getY() - adj.getY()));
				Long g = t.b + 1;
				pq.add(new Tuple4<Long, Long, MapLocation, Direction>(g+h, g, adj, dir));
			}
		}
		ArrayList<Direction> path = new ArrayList<>();
		for(;;) {
			Direction d = vis.get(location);
			if(d == null) break;
			path.add(d);
			location = location.add(opposite(d));
		}
		Collections.reverse(path); // empty if location is inaccessible
		return path;
	}
}


