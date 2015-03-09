package elevator;

import java.util.ArrayList;

/**
 * The controller must accept actions events from the elevators (button
 * pressings, position changes) and react on the events by sending control
 * commands to elevators' motors, doors and scales (level indicators). In order
 * to be able to control elevators in "real-time"', the controller should be
 * implemented as a multithreaded application. The velocity of elevators can be
 * controlled "by-hand" with a special control slider in the Elevators GUI, so
 * the timing requirements (deadlines) can be really hard.
 *
 * @author Gabriel
 */
public class ElevatorController {

    private Elevator elevator;
    public static final int UP = 1, DOWN = -1, STOPED = 0;
    private int floor, panel, el, velocity;
    private ArrayList<Integer> semList;
    private ArrayList floors;
    private Elevator[] elevators;

    public ElevatorController(Elevators elevators) {
        this.elevators = elevators.allElevators;
    }

    private boolean calculateWhereToGo(int floor, Elevator elevator) {
        startTimer();

        int eDir = elevator.Getdir();
        double ePos = elevator.Getpos();

//        if (semList.get(floor)) {
//            
//        }
        
        if ((eDir == UP || eDir == STOPED) && (ePos < floor)) {
            elevator.Setdir(UP);
        } else if ((eDir == DOWN || eDir == STOPED) && (ePos > floor)) {
            elevator.Setdir(DOWN);
        }
        return false;
    }

    public void pressButton(int floor) {
        this.floor = floor;
    }

    public void pressPanel(int panel, Elevator elevator) {
        this.panel = panel;
        this.elevator = elevator;
        calculateWhereToGo(panel, elevator);
    }

    public void setVelocity(int velocity) {
        this.velocity = velocity;
    }

    public void startTimer() {

    }

}
