import java.util.*;
import bc.*;

public class KarbonitePath
{
	public int[][] distance_field;
	public int[][] amount_field;
	public ArrayList<Direction>[][] movement_field;
	public KarbonitePath(int[][] amount_field, int[][] distance_field, ArrayList<Direction>[][] movement_field) 
	{
		this.distance_field = distance_field;
		this.movement_field = movement_field;
		this.amount_field = amount_field;
	}
}