import java.util.*;
import bc.*;

public class KarbDir implements Comparable {
        Direction dir;
        long karb;
        // Constructor
        public KarbDir(Direction dir, long karb) {
            this.dir = dir;
            this.karb = karb;
        }
        public String toString() {
            return this.dir + " " + this.karb;
        }
        public int compareTo(Object other) {
            if (!(other instanceof KarbDir))
                throw new ClassCastException("A Karbdir object expected.");
            long otherkarb = ((KarbDir) other).karb;
            return (int)(this.karb - otherkarb);
        }
}
