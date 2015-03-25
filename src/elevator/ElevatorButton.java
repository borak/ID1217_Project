package elevator;

/**
 * Represents a button or panel button event.
 * 
 * @author Gabriel
 */
public class ElevatorButton {
    
    private int floor;
    private int dir;
    private final boolean isPanelButton;

    public ElevatorButton(int floor, int dir, boolean isPanelButton) {
        this.floor = floor;
        this.dir = dir;
        this.isPanelButton = isPanelButton;
    }

    public boolean isPanelButton() {
        return isPanelButton;
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
