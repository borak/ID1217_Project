package elevator;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
    private ArrayList activeElevators = new ArrayList();
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

    // TODO: ta hänsyn till flera knapptryckningar
    public void pressButton(int currentFloor, int dir) {
        System.out.println("button pressed on floor " + currentFloor);
        //stream.println("m 2 1");
        //stream.println("s 2 2");
        //stream.flush();

        Elevator elevator = null;
        try {
            startWaitTimer();

            //hämta närmaste hiss
            if (allElevators.length == 1) {
                elevator = allElevators[0];
            } else {
                double movingDistance = Double.MAX_VALUE;
                double emptyDistance = Double.MIN_VALUE;
                // elevator = allElevators[0];
                Elevator emptyElevator = null;
                Elevator movingElevator = null;

                for (int i = 0; i < allElevators.length - 1; i++) {
                    Elevator tempElevator = allElevators[i];

                    //kolla avståndet 
                    double tempDistance = (currentFloor - tempElevator.Getpos());
                    if (tempDistance < 0) {
                        tempDistance *= -1;
                    }

                    //ska åka åt samma håll som hissen åker
                    if (tempElevator.Getdir() == dir) {
                        if (tempDistance < movingDistance || movingElevator == null) {
                            movingElevator = tempElevator;
                            movingDistance = tempDistance;
                        } //ledig hiss 
                    } else if (tempElevator.Getdir() == 0) {
                        if (tempDistance < emptyDistance || emptyElevator == null) {
                            emptyElevator = tempElevator;
                            emptyDistance = tempDistance;
                        } //ledig hiss
                    }
                }

                if (movingDistance < emptyDistance) {
                    elevator = movingElevator;
                } else {
                    elevator = emptyElevator;
                }

                FloorButton button = new FloorButton(currentFloor, dir);
                InnerObserver observer = new InnerObserver(elevator, button);
                elevator.registerObserver(observer);
                handleButtonQueue(elevator);

            }
        } finally {
            stopWaitTimer();
        }

    }

    public void handleButtonQueue(Elevator elevator) {
        synchronized (activeElevators) {
            if (activeElevators.contains(elevator)) {
                return;
            }
            activeElevators.add(elevator);
        }

        ElevatorObserver observer = elevator.getNextObserver();
        while (observer != null) {
            System.out.println("m " + elevator.getNumber() + " " + observer.getButton().getDir());
            stream.println("m " + elevator.getNumber() + " " + observer.getButton().getDir());

            elevator.registerObserver(observer);
            observer.waitPosition();

            stream.println("m " + elevator.getNumber() + " 0");
            stream.println("d " + elevator.getNumber() + " 1");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            stream.println("d " + elevator.getNumber() + " -1");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }

            observer = elevator.getNextObserver();
        }
        synchronized (activeElevators) {
            activeElevators.remove(elevator);
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

    class InnerObserver implements ElevatorObserver {

        Lock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        Elevator elevator;
        FloorButton button;

        InnerObserver(Elevator elevator, FloorButton button) {
            this.elevator = elevator;
            this.button = button;
        }

        @Override
        public void signalPosition(int floor) {
            synchronized (condition) {
                condition.signal();
            }
        }

        @Override
        public void waitPosition() {
            int floor = button.getFloor();
            double pos;

            if (button.getDir() > 0) {
                System.out.println("button dir  = " + button.getDir() + " elevator pos  = " + elevator.Getpos() + " floor = " + floor);
                synchronized (elevator.motorLock) {
                    pos = elevator.Getpos();
                }
                while (pos <= floor) {
                    synchronized (condition) {
                        try {
                            System.out.println("condition waiting...");
                            condition.wait();
                            System.out.println("conrition signaled!");
                        } catch (InterruptedException ex) {
                            Logger.getLogger(ElevatorController.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    synchronized (elevator.motorLock) {
                        pos = elevator.Getpos();
                    }
                    // TODO: skriv till ström s
                }
            } else {
                while (elevator.Getpos() >= floor) {
                    synchronized (condition) {
                        try {
                            condition.wait();
                        } catch (InterruptedException ex) {
                            Logger.getLogger(ElevatorController.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
            elevator.removeObserver(this);

        }

        public FloorButton getButton() {
            return button;
        }

        public int getFloor() {
            return button.getFloor();
        }

        public int getDir() {
            return button.getDir();
        }

    }
}
