package elevator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

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
public class ElevatorController implements Runnable {

    private Elevator elevator;
    //public static final int UP = 1, DOWN = -1, STOPED = 0;
    private int currentFloor, panel, el, velocity;
    private ArrayList<Integer> semList;
    private ArrayList floors;
    private Elevator[] allElevators;
    private Socket socket;
    private PrintWriter stream;

    public ElevatorController(Elevators elevators) {
        this.allElevators = elevators.allElevators;
    }

    //TODO: fixa sockets i ElevatorIO ?
    public void createSocket(String hostName, int port) {
        try {
            System.out.println("Client trying to connect to: " + hostName + " " + port);
            socket = new Socket(hostName, port);
            stream = new PrintWriter(socket.getOutputStream(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            Thread.sleep(500);
            createSocket("localhost", 4711);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void pressButton(int currentFloor) {
        System.out.println("button pressed on floor " + currentFloor);
        stream.println("m 2 1");   // funkar ej
        stream.flush();

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
            //out.print("\nm " + el.getNumber() + " " + currentFloor);
            //System.stream.println("m " + el.getNumber() + " " + currentFloor + " ");

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
