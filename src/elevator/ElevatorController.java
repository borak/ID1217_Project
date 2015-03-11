package elevator;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
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

    private ArrayList activeElevators = new ArrayList();
    private Elevator[] allElevators;
    private Socket socket;
    private PrintWriter stream;

    public ElevatorController(Elevators elevators) {
        this.allElevators = elevators.allElevators;
    }

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

    public void pressButton(int currentFloor, int dir) {
        System.out.println("Button pressed on floor " + currentFloor);

        Elevator elevator = null;
        try {
            startWaitTimer();

            if (allElevators.length == 1) {
                elevator = allElevators[0];
            } else {
                double movingDistance = Double.MAX_VALUE;
                double emptyDistance = Double.MIN_VALUE;
                Elevator emptyElevator = null;
                Elevator movingElevator = null;

                for (int i = 0; i < allElevators.length - 1; i++) {
                    Elevator tempElevator = allElevators[i];

                    double tempDistance = (currentFloor - tempElevator.Getpos());
                    if (tempDistance < 0) {
                        tempDistance *= -1;
                    }
                    if (tempElevator.Getdir() == dir) {
                        if (tempDistance < movingDistance || movingElevator == null) {
                            movingElevator = tempElevator;
                            movingDistance = tempDistance;
                        }
                    } else if (tempElevator.Getdir() == 0) {
                        if (tempDistance < emptyDistance || emptyElevator == null) {
                            emptyElevator = tempElevator;
                            emptyDistance = tempDistance;
                        }
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
            int dir = 0;

            if (elevator.Getpos() <= observer.getButton().getFloor()) {
                dir = 1;
            } else if (elevator.Getpos() >= observer.getButton().getFloor()) {
                dir = -1;
            }
            stream.println("m " + elevator.getNumber() + " " + dir);

            elevator.registerObserver(observer);
            observer.waitPosition();

            stream.println("m " + elevator.getNumber() + " 0");
            stream.println("d " + elevator.getNumber() + " 1");
            elevator.removeObserver(observer);

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
        Semaphore semaphore = new Semaphore(1);

        InnerObserver(Elevator elevator, FloorButton button) {
            this.elevator = elevator;
            this.button = button;
        }

        @Override
        public void signalPosition(int floor) {
            semaphore.release();
        }

        @Override
        public void waitPosition() {
            int floor = button.getFloor();

            if (floor >= elevator.Getpos() - 0.001) {
                while (elevator.Getpos() + 0.001 <= floor) {
                    semaphore.acquireUninterruptibly();
                }
            } else {
                while (elevator.Getpos() - 0.001 >= floor) {
                    semaphore.acquireUninterruptibly();
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
