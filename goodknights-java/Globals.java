import bc.*;
import java.util.*;

public class Globals {
    //Stuff from game/api
    public static GameController gc = new GameController();;
    public static Team ally = gc.team();
    public static Team enemy;
    public static Planet myPlanet = gc.planet();
    public static PlanetMap map = gc.startingMap(myPlanet);;
    public static PlanetMap mars_map = gc.startingMap(Planet.Mars);
    public static int width = (int)map.getWidth();;
    public static int height = (int)map.getHeight();;
    public static int mars_width = (int)mars_map.getWidth();
    public static int mars_height = (int)mars_map.getHeight();
    public static int initial_workers = 0;
    public static int current_round = 0;
    public static AsteroidPattern asteroid_pattern = gc.asteroidPattern();
    public static OrbitPattern orbit_pattern = gc.orbitPattern();
    public static Direction[] dirs = {Direction.Center, Direction.East, Direction.Northeast, Direction.North, Direction.Northwest, Direction.West, Direction.Southwest, Direction.South, Direction.Southeast};
    public static int[][] map_memo; // 1 if possible karbonite, -1 if not passable
    public static ArrayList<KarbonitePath> karbonite_path;



    //Stuff we create
    public static ArrayList<int[]> enemy_locations = new ArrayList<int[]>(); //starting enemy location queue for generating vector field
	public static int[][] home_field = new int[width][height];
    public static ArrayList<int[]> ally_locations = new ArrayList<int[]>();
    public static int[][] distance_field = new int[width][height];
    public static ArrayList<Direction>[][] movement_field = new ArrayList[width][height];
    public static ArrayList<int[]> enemy_factories = new ArrayList<int[]>(); //nearest enemy factory
    public static int[][] factory_field = new int[width][height];
    public static HashMap<Integer, Integer> workers = new HashMap<>();
    public static int[][] random_distance_field = new int[width][height]; //generate random movement field
    public static ArrayList<Direction>[][] random_movement_field = new ArrayList[width][height];
    public static ArrayList<int[]> enemy_buildings = new ArrayList<int[]>();
    public static ArrayList<Integer> rand_permutation = randomPermutation(9);
    public static boolean doesPathExist = false; //determine if a path exists
    public static double[][] mars_landing = new double[mars_width][mars_height];
    public static HashMap<Integer, Queue<Direction>> paths; //used in knightcode
    public static int rocket_homing = 0; //are rockets built / how many
    public static int minworkers = 0;
    public static int factories_active = 0;
    public static int nikhil_num_workers = 0;
	public static int[][] connected_components = new int[width][height];

    public static int num_factories = 0;
    public static int num_rockets = 0;
    public static int num_workers = 0;
    public static int num_rangers = 0;
    public static int num_knights = 0;
    public static int num_mages = 0;
    public static int num_healers = 0;
    public static int total_workers = 0;
    public static int total_rangers = 0;
    public static int total_knights = 0;
    public static int total_healers = 0;
    public static int total_mages = 0;

    //Constants
    public static final long maxAttackRange = 50L;
    public static final long maxVisionRange = 100L;

    /* dummy duplicate function in here so that Globals can compile independently
     * of the other stuff... u can blame jz that we need such a hack -_-
     * You will also note that I made the variable names appropriate.
     */

    private static ArrayList<Integer> randomPermutation(int size) {
        ArrayList<Integer> result = new ArrayList<>();
        for (int i = 0; i < size; i++)
            result.add(i);

        Collections.shuffle(result);
        return result;
    }
}
