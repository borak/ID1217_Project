package elevator;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingConstants;

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
    private AtomicBoolean shouldStop = new AtomicBoolean();

    public ElevatorController(Elevators elevators) {
        this.allElevators = elevators.allElevators;
        shouldStop.getAndSet(false);
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

                ElevatorButton button = new ElevatorButton(currentFloor, dir);
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

        ElevatorObserver observer = elevator.getNextUpObserver();
        if (observer == null) {
            observer = elevator.getNextDownObserver();
        }
        while (observer != null) {
            int dir = 0;

            if (elevator.Getpos() <= observer.getButton().getFloor()) {
                dir = 1;
            } else if (elevator.Getpos() >= observer.getButton().getFloor()) {
                dir = -1;
            }
            stream.println("m " + elevator.getNumber() + " " + dir);

            observer.waitPosition();
            // elevator.removeObserver(observer);
            if (shouldStop.get()) {
                shouldStop.set(false);
                stopElevator(elevator);
            }

            // todo DOWN FUNKAR EJ 
            if (dir == 1) {
                observer = elevator.getNextUPObserver();
//                if (observer == null) {
//                    observer = elevator.getNextDownObserver();
//                }
            } else if (dir == -1) {
                observer = elevator.getNextDOWNObserver();
//                if (observer == null) {
//                    observer = elevator.getNextUpObserver();
//                }
                System.out.println("OBSERVER OHW = " + observer.getButton().getFloor());
            }

        }
        synchronized (activeElevators) {
            activeElevators.remove(elevator);
        }
    }

    private void simulateDoors(Elevator elevator) {
        System.out.println("simulatedoors for " + elevator.getNumber());
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
        System.out.println("done simulating ");
    }

    /**
     * Regler: hiss kan bara ändra riktning när den är tom. Hämtar bara upp folk
     * som ska åka i hissens riktning kösystem, prioriterar alltid max våning
     *
     */
    public void pressPanel(int elevatorIndex, int floor) {
        startTimer();
        try {
            Elevator elevator = allElevators[elevatorIndex - 1];
            int dir = (int) (floor - elevator.Getpos());
            if (dir >= 0) {
                dir = 1;
            } else {
                dir = -1;
            }
            ElevatorButton button = new ElevatorButton(floor, dir);
            InnerObserver observer = new InnerObserver(elevator, button);
            elevator.registerObserver(observer);

            handleButtonQueue(elevator);

        } finally {
            stopTimer();
        }
    }

    public void stopElevator(Elevator elevator) {
        System.out.println("stopElevator for " + elevator.getNumber());
        stream.println("m " + elevator.getNumber() + " 0");
        simulateDoors(elevator);

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
        ElevatorButton button;
        Semaphore semaphore = new Semaphore(1);
        Thread waitingThread = null;

        InnerObserver(Elevator elevator, ElevatorButton button) {
            this.elevator = elevator;
            this.button = button;
        }

        @Override
        public void signalPosition(int floor) {
            if (button.getFloor() == floor) {
                // stopElevator(elevator);
                shouldStop.set(true);
            }
            semaphore.release();
        }

        @Override
        public void waitPosition() {
//            new Thread(new Runnable() {
            int floor = button.getFloor();
//
//                @Override
//                public void run() {
            System.out.println("waiting on floor ..." + floor);
            waitingThread = Thread.currentThread();

            if (floor >= elevator.Getpos() - 0.001) {
                while (elevator.Getpos() + 0.001 <= floor) {
                    // skriv hisstatusen till strömmen
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException ex) {
                        System.out.println(this.getFloor() + " INTERRUPT!");
                        return;
                    }
                }
            } else {
                while (elevator.Getpos() - 0.001 >= floor) {
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException ex) {
                        System.out.println(this.getFloor() + " INTERRUPT!");
                        return;
                    }
                }
            }
            waitingThread = null;
            elevator.removeObserver(InnerObserver.this);
//                }
//            }).start();
        }

        public ElevatorButton getButton() {
            return button;
        }

        public int getFloor() {
            return button.getFloor();
        }

        public int getDir() {
            return button.getDir();
        }

        @Override
        public int compareTo(ElevatorObserver t) {
            if (this.button.getFloor() > t.getButton().getFloor()) {
                return 1;
            } else if (this.button.getFloor() < t.getButton().getFloor()) {
                return -1;
            }
            return 0;
        }

        @Override
        public void interruptWait() {
            if (waitingThread != null) {
                System.out.println("interrupted thread!");
                waitingThread.interrupt();
            }
        }
    }
}
