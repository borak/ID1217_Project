package elevator;

/**
 *
 * @author Gabriel
 */
public class FloorButton {
    
    int floor;
    int dir;

    public FloorButton(int floor, int dir) {
        this.floor = floor;
        this.dir = dir;
    }
    
    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    public int getDir() {
        return dir;
    }

    public void setDir(int dir) {
        this.dir = dir;
    }
    
}
