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
    //public static final int UP = 1, DOWN = -1, STOPED = 0;
    private int floor, panel, el, velocity;
    private ArrayList<Integer> semList;
    private ArrayList floors;
    private Elevator[] allElevators;

    public ElevatorController(Elevator[] allElevators) {
        this.allElevators = allElevators;        
    }

    public void pressButton(int floor) {
        this.floor = floor;
    }

    public void pressPanel(int elevatorIndex, int floor) {
        startTimer();
        try {
            Elevator elevator = allElevators[elevatorIndex];
            double pos = elevator.Getpos();
            //if((int)pos != pos) throw Exception("moving"); 
            if(pos == floor) {
                return;
            } else if(pos > floor) {
                elevator.Setdir(Elevators.DOWN);
            } else /* if(pos < floor) */{
                elevator.Setdir(Elevators.UP);
            } 
        } finally {
            stopTimer();
        }
    }

    public void setVelocity(double velocity) {
        
    }

    public void startTimer() {

    }
    
    public void stopTimer() {

    }
}
