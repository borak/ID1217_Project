package elevator;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    private final ArrayList activeElevators = new ArrayList();
    private final Elevator[] allElevators;
    private Socket socket;
    private PrintWriter stream;
    private final AtomicBoolean shouldStop = new AtomicBoolean();
    private final Lock lock;
    private final Condition[] condition;

    /**
     * Initializes synchronization tools for the elevators.
     *
     * @param elevators The elevators to be controlled and synchronized.
     */
    public ElevatorController(Elevator[] elevators) {
        if (elevators == null || elevators.length == 0) {
            throw new IllegalArgumentException("Illegal set of elevators to "
                    + "be controlled.");
        }
        this.lock = new ReentrantLock();
        this.allElevators = elevators;
        shouldStop.getAndSet(false);
        condition = new Condition[elevators.length];
        for (int i = 0; i < elevators.length; i++) {
            condition[i] = lock.newCondition();
        }
    }

    /**
     * Initializes the socket to be used for elevator communications.
     *
     * @param hostName The name of the host, for example "localhost".
     * @param port The port to use, for example "4711".
     */
    public void createSocket(String hostName, int port) {
        try {
            System.out.println("Client trying to connect to: " + hostName + " " + port);
            socket = new Socket(hostName, port);
            stream = new PrintWriter(socket.getOutputStream(), true);
        } catch (Exception e) {
            System.err.println("Error occurred when creating socket: "
                    + e.getMessage());
        }
    }

    /**
     * Tries to create and connect to the localhost socket. This method is used
     * for development only. Use createSocket method instead.
     */
    @Override
    public void run() {
        try {
            Thread.sleep(500);
            createSocket("localhost", 4711);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Called to schedule the button event on an appropriate elevator.
     *
     * @param currentFloor The floor which the button was pressed on.
     * @param dir The direction the person which to travel. 1 for up and -1 for
     * down.
     */
    public void pressButton(int currentFloor, int dir) {
        System.out.println("Button pressed on floor " + currentFloor);

        if (currentFloor > Elevators.MaxTopFloor || currentFloor < 0
                || dir < -1 || dir > 1) {
            throw new IllegalArgumentException("Invalid arguments "
                    + "for button event.");
        }
        Elevator elevator = null;
        ElevatorButton button = new ElevatorButton(currentFloor, dir, false);
        try {
            startTimer();

            if (allElevators.length == 1) {
                if (allElevators[0].isStop()) {
                    return;
                }
                elevator = allElevators[0];
            } else {
                double movingDistance = Double.MAX_VALUE;
                double emptyDistance = Double.MAX_VALUE;
                double floorDistance = Double.MAX_VALUE;
                Elevator emptyElevator = null;
                Elevator movingElevator = null;
                Elevator floorElevator = null;

                for (int i = 0; i < allElevators.length - 1; i++) {
                    Elevator tempElevator = allElevators[i];
                    if (tempElevator.isStop()) {
                        continue;
                    } else if (tempElevator.containsButton(button)) {
                        return;
                    }

                    double tempDistance = Math.abs(currentFloor - tempElevator.Getpos());
                    if (tempElevator.getCurrentObserver() != null
                            && tempElevator.getCurrentObserver().getButton().getDir() == dir) {
                        if (tempDistance < movingDistance) {
                            movingElevator = tempElevator;
                            movingDistance = tempDistance;
                        }
                    } else if (tempElevator.getCurrentObserver() == null) {
                        if (tempDistance < 0.5) {
                            emptyElevator = tempElevator;
                            break;
                        }
                        if (tempDistance < emptyDistance) {
                            emptyElevator = tempElevator;
                            emptyDistance = tempDistance;
                        }
                    } else if (tempElevator.getCurrentObserver().getButton().getDir() == 1) {
                        int tempFloorDistance = (tempElevator.getQueueTopFloor() - tempElevator.getCurrentFloor())
                                + (tempElevator.getQueueTopFloor() - currentFloor);
                        if (tempFloorDistance < floorDistance) {
                            floorDistance = tempFloorDistance;
                            floorElevator = tempElevator;
                        }
                    } else {
                        int tempFloorDistance = (tempElevator.getCurrentFloor() - tempElevator.getQueueBotFloor())
                                + (currentFloor - tempElevator.getQueueBotFloor());
                        if (tempFloorDistance < floorDistance || floorElevator == null) {
                            floorDistance = tempFloorDistance;
                            floorElevator = tempElevator;
                        }
                    }
                }
                boolean needToChangeCurrentDir = true;
                if ((movingElevator != null && dir == -1 && currentFloor < movingElevator.Getpos())
                        || (movingElevator != null && dir == 1 && currentFloor > movingElevator.Getpos())) {
                    needToChangeCurrentDir = false;
                }
                if (movingElevator != null && movingElevator.getCurrentObserver().getButton().getDir() == dir
                        && (!needToChangeCurrentDir)) {
                    elevator = movingElevator;
                } else if (emptyElevator != null) {
                    elevator = emptyElevator;
                } else if (movingElevator != null && movingDistance < floorDistance) {
                    elevator = movingElevator;
                } else if (floorElevator != null) {
                    elevator = floorElevator;
                } else if (movingElevator != null) {
                    elevator = movingElevator;
                } else {
                    Random random = new Random();
                    int index = random.nextInt(allElevators.length - 1);
                    elevator = allElevators[index];
                }

                InnerObserver observer = new InnerObserver(elevator, button);
                elevator.registerObserver(observer);

                handleButtonQueue(elevator);
            }
        } finally {
            stopTimer();
        }
    }

    /**
     * For as long there is observers queued in the elevator, this method will
     * handle any movement command (such as doors, engine) for the elevator.
     * Note: This method runs on a separate thread if it's not already handling
     * the elevator.
     *
     * @param elevator The elevator to handle the queue for.
     */
    public void handleButtonQueue(Elevator elevator) {
        synchronized (activeElevators) {
            if (activeElevators.contains(elevator)) {
                return;
            }
            activeElevators.add(elevator);
        }
        new Thread(() -> {
            try {
                ElevatorObserver observer = elevator.getNextUpObserver();
                if (observer == null) {
                    observer = elevator.getNextDownObserver();
                }
                while (observer != null) {
                    if (elevator.isStop()) {
                        return;
                    }
                    int dir = 0;

                    if (elevator.Getpos() - 0.001 < observer.getButton().getFloor()) {
                        dir = 1;
                    } else if (elevator.Getpos() + 0.001 > observer.getButton().getFloor()) {
                        dir = -1;
                    }
                    boolean isOnAFloor = elevator.Getpos() % 1 < 0.04 || elevator.Getpos() % 1 > 0.97;
                    if (dir != 0 && (!isOnAFloor || elevator.getCurrentFloor() != observer.getButton().getFloor())) {
                        stream.println("m " + elevator.getNumber() + " " + dir);
                        observer.waitPosition();
                    } else if (isOnAFloor) {
                        shouldStop.set(true);
                    }

                    if (shouldStop.get()) {
                        shouldStop.set(false);
                        elevator.removeObserver(observer);
                        stopElevator(elevator);
                    } else if (!observer.getButton().isPanelButton()) {
                        elevator.removeObserver(observer);
                        pressButton(observer.getButton().getFloor(), observer.getButton().getDir());
                    }

                    if (dir == 1) {
                        observer = elevator.getNextUpObserver();
                        if (observer == null) {
                            observer = elevator.getNextDownObserver();
                        }
                    } else {
                        observer = elevator.getNextDownObserver();

                        if (observer == null) {
                            observer = elevator.getNextUpObserver();
                        }
                    }
                }
            } finally {
                synchronized (activeElevators) {
                    activeElevators.remove(elevator);
                }
            }
        }).start();
    }

    private void simulateDoors(Elevator elevator) {
        stream.println("d " + elevator.getNumber() + " 1");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            System.err.println("Interrupted while opening the doors for "
                    + "elevator " + elevator.getNumber() + ". Error: "
                    + ex.getMessage());
        }
        stream.println("d " + elevator.getNumber() + " -1");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            System.err.println("Interrupted while closing the doors for "
                    + "elevator " + elevator.getNumber() + ". Error: "
                    + ex.getMessage());
        }
    }

    /**
     * Called to schedule the panel event to the elevator with the specified
     * index.
     *
     * @param elevatorIndex The index of the elevator in the array. Its the same
     * as the elevators number.
     * @param floor The floor the person which to goto.
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

            if (floor == Elevators.SPECIAL_FOR_STOP) {
                immediateStopElevator(elevator);
                return;
            } else if (elevator.isStop()) {
                elevator.setStop(false);
            }

            ElevatorButton button = new ElevatorButton(floor, dir, true);
            InnerObserver observer = new InnerObserver(elevator, button);
            elevator.registerObserver(observer);
            handleButtonQueue(elevator);
        } finally {
            stopTimer();
        }
    }

    private void immediateStopElevator(Elevator elevator) {
        elevator.setStop(true);
        stream.println("m " + elevator.getNumber() + " 0");
    }

    private void stopElevator(Elevator elevator) {
        stream.println("m " + elevator.getNumber() + " 0");
        simulateDoors(elevator);
    }

    public void startTimer() {

    }

    public void stopTimer() {

    }

    /**
     * This class represents a button event for a certain elevator. It is used
     * as an observer for the elevator and a synchronization tool for the
     * handling of the button queue's movement commands.
     */
    class InnerObserver implements ElevatorObserver {

        Elevator elevator;
        ElevatorButton button;
        Semaphore semaphore = new Semaphore(1);
        Thread waitingThread = null;
        double rangeAccuracy = 0.01;

        InnerObserver(Elevator elevator, ElevatorButton button) {
            this.elevator = elevator;
            this.button = button;
        }

        @Override
        public void signalPosition(int floor) {
            stream.println("s " + elevator.getNumber() + " " + floor);
            lock.lock();
            try {
                condition[elevator.getNumber()].signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void waitPosition() {
            int floor = button.getFloor();
            System.out.println(elevator.getNumber() + " waiting on floor "
                    + floor + ".");
            waitingThread = Thread.currentThread();

            if (floor >= elevator.Getpos() - rangeAccuracy) {
                while (elevator.Getpos() + rangeAccuracy <= floor && elevator.getCurrentObserver() == this) {
                    try {
                        lock.lock();
                        try {
                            condition[elevator.getNumber()].await();
                        } finally {
                            lock.unlock();
                        }
                    } catch (InterruptedException ex) {
                        return;
                    }
                }
            } else {
                while (elevator.Getpos() - rangeAccuracy >= floor && elevator.getCurrentObserver() == this) {
                    try {
                        lock.lock();
                        try {
                            condition[elevator.getNumber()].await();
                        } finally {
                            lock.unlock();
                        }
                    } catch (InterruptedException ex) {
                        return;
                    }
                }
            }
            shouldStop.set(true);
            waitingThread = null;
            elevator.removeObserver(InnerObserver.this);
        }

        @Override
        public ElevatorButton getButton() {
            return button;
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
                waitingThread.interrupt();
            }
        }

        @Override
        public void signalStop() {
            if (waitingThread != null) {
                waitingThread.interrupt();
            }
        }
    }
}
