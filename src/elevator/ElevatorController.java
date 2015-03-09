package elevator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
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
    private int currentFloor, panel, el, velocity;
    private ArrayList<Integer> semList;
    private ArrayList floors;
    private Elevator[] allElevators;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public ElevatorController(Elevators elevators) {
        this.allElevators = elevators.allElevators;
        
        createSocket("localhost", 4711);
        
    }

    ElevatorController() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public void createSocket(String hostName, int port) {
        try {
            socket = new Socket(hostName, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void pressButton(int currentFloor) {
        this.currentFloor = currentFloor;
        double distance = 0, tempDistance = 0;
        Elevator el = elevator;
        try {
            startWaitTimer();

            for (int i = 0; i < allElevators.length; i++) {
                Elevator tempEl = allElevators[i];
                distance = (tempEl.Getpos() - currentFloor);
                if (tempDistance < distance) {
                    distance = tempDistance;
                    el = tempEl;
                }
            }
            out.print("m " + el.getNumber() + " " + currentFloor + " ");

        } finally {
            stopWaitTimer();
        }
    }

    public void pressPanel(int elevatorIndex, int floor) {
        startTimer();
        try {
            Elevator elevator = allElevators[elevatorIndex];
            double pos = elevator.Getpos();
            //if((int)pos != pos) throw Exception("moving"); 
            if (pos == floor) {
                return;
            } else if (pos > floor) {
                elevator.Setdir(Elevators.DOWN);
            } else /* if(pos < floor) */ {
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

    private void startWaitTimer() {

    }

    private void stopWaitTimer() {

    }

}
