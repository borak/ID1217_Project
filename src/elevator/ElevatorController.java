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
    private Lock lock = new ReentrantLock();
    private Condition[] condition;

    public ElevatorController(Elevators elevators) {
        this.allElevators = elevators.allElevators;
        shouldStop.getAndSet(false);
        condition = new Condition[elevators.allElevators.length];
        for (int i=0; i< elevators.allElevators.length; i++) {
            condition[i] = lock.newCondition();
        }
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

    /**
     * TODO: testcase
     * tryck 2 ggr på samma våning så åker båda hissarna med.
     * exp: 1 åker om det inte va 2 olika håll.
     * lös 1: begränsa kön i förväg genom att inte lägga in den i kön.
     * gör det genom att titta om någon annan elevator har den i kön?
     */
    public void pressButton(int currentFloor, int dir) {
        System.out.println("Button pressed on floor " + currentFloor);

        Elevator elevator = null;
        ElevatorButton button = new ElevatorButton(currentFloor, dir, false);
        try {
            startWaitTimer();

            if (allElevators.length == 1) {
                if(allElevators[0].isStop()) {
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
                    if(tempElevator.isStop()) {
                        continue;
                    } else if(tempElevator.containsButton(button)) {
                        return;
                    }

                    double tempDistance = Math.abs(currentFloor - tempElevator.Getpos());
                    if (tempElevator.getCurrentObserver() != null && tempElevator.getCurrentObserver().getButton().getDir() == dir) {//tempElevator.Getdir() == dir) {
                        //närmare
                        if (tempDistance < movingDistance /*|| movingElevator == null*/) {
                            movingElevator = tempElevator;
                            movingDistance = tempDistance;
                        }
                    } //stilla  
                    else if (tempElevator.getCurrentObserver() == null /*|| tempElevator.getCurrentObserver().getButton().getDir() == 0*/) {//tempElevator.Getdir() == 0) {
                        //samma våning - öppna
                        if (tempDistance < 0.5) {
                            emptyElevator = tempElevator;
                            break;
                        }
                        //närmare
                        if (tempDistance < emptyDistance /* || emptyElevator == null */) {
                            emptyElevator = tempElevator;
                            emptyDistance = tempDistance;
                        }
                    } // olika riktningar
                    else if (tempElevator.getCurrentObserver().getButton().getDir() == 1) {//tempElevator.Getdir() == 1) { //påväg upp
                        //nuvarande pos till top
                        int tempFloorDistance = (tempElevator.getQueueTopFloor() - tempElevator.getCurrentFloor())
                                // top ner till önskad
                                + (tempElevator.getQueueTopFloor() - currentFloor);
                        if (tempFloorDistance < floorDistance) {
                            floorDistance = tempFloorDistance;
                            floorElevator = tempElevator;
                        }
                    } else { 
                        //nuvarande pos ner till bot
                        int tempFloorDistance = (tempElevator.getCurrentFloor() - tempElevator.getQueueBotFloor())
                                //bot upp till önskad
                                + (currentFloor - tempElevator.getQueueBotFloor());
                        if (tempFloorDistance < floorDistance || floorElevator == null) {
                            floorDistance = tempFloorDistance;
                            floorElevator = tempElevator;
                        }
                    }
                }
                boolean needToChangeCurrentDir = true;
                if((movingElevator != null && dir == -1 && currentFloor < movingElevator.Getpos())
                    || (movingElevator != null && dir == 1 && currentFloor > movingElevator.Getpos())) {
                    needToChangeCurrentDir = false;
                }
                if (movingElevator != null && movingElevator.getCurrentObserver().getButton().getDir() == dir 
                        && (!needToChangeCurrentDir /* || movingElevator.getCurrentObserver().getButton().getFloor() == Elevators.MaxTopFloor
                        || movingElevator.getCurrentObserver().getButton().getFloor() == 0*/)) { 
                    System.out.println(movingElevator.getNumber()+" moving1 elev taken");
                    elevator = movingElevator;
                } else if (emptyElevator != null) {
                    System.out.println(emptyElevator.getNumber()+" empty elev taken");
                    elevator = emptyElevator;
                } else if (movingElevator != null && movingDistance < floorDistance /*&& !needToChangeCurrentDir*/) {
                    System.out.println(movingElevator.getNumber()+" moving2 elev taken");
                    elevator = movingElevator;
                } else if (floorElevator != null) {
                    System.out.println(floorElevator.getNumber()+" floor elev taken");
                    elevator = floorElevator;
                } else if (movingElevator != null) {
                    System.out.println(movingElevator.getNumber()+" moving3 elev taken");
                    elevator = movingElevator;
                } else {
                    Random random = new Random();
                    int index = random.nextInt(allElevators.length-1);
                    elevator = allElevators[index];
                    System.out.println(elevator.getNumber()+" random elev taken");
                }
                
                InnerObserver observer = new InnerObserver(elevator, button);
                elevator.registerObserver(observer);

                handleButtonQueue(elevator);
            }
        } finally {
            stopWaitTimer();
        }

    }

    public void handleButtonQueue(Elevator elevator) {
        System.out.println("buttonQ=1");
        synchronized (activeElevators) {
            if (activeElevators.contains(elevator)) {
                return;
            }
            activeElevators.add(elevator);
        }
        System.out.println("buttonQ=2");
        try {
            ElevatorObserver observer = elevator.getNextUpObserver();
            if (observer == null) {
                observer = elevator.getNextDownObserver();
            }
            System.out.println("elevator queue handler = " + elevator.getNumber() + " started.");
            while (observer != null) {
                System.out.println("buttonQ=3 , "+ elevator.isStop());
                //System.out.println("e:"+elevator.getNumber()+" 3");
                //System.out.println("e:"+elevator.getNumber()+" going "+observer.getButton().getDir());
                if (elevator.isStop()) { // stoppas av pressPanel
                    System.out.println("e:"+elevator.getNumber()+" isStop=true. returning");
                    //stopElevator(elevator);
                    //immediateStopElevator(elevator);
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
                    System.out.println("e:"+elevator.getNumber()+" has woken up.");
                } else if (isOnAFloor) { //bug fix for interrupts between floors
                    shouldStop.set(true); //interrupted safety
                }
                
                if (shouldStop.get()) {
                    shouldStop.set(false);
                    elevator.removeObserver(observer);
                    stopElevator(elevator);
                } else //{
                    //reschedule button, new thread? perhaps it can be slow
                    if(!observer.getButton().isPanelButton()) {
                        elevator.removeObserver(observer);
                        pressButton(observer.getButton().getFloor(), observer.getButton().getDir());
                    //}
                }
                

                if (dir == 1) {
                    observer = elevator.getNextUpObserver();
                    if (observer == null) {
                        observer = elevator.getNextDownObserver();
                    }
                } else/*if (dir == -1)*/ {
                    //if current is going down, it should not change to going up
                    //if (observer == elevator.getCurrentObserver())
                    observer = elevator.getNextDownObserver();
                    
                    if (observer == null) {
                        observer = elevator.getNextUpObserver();
                    }
                    // System.out.println("OBSERVER OHW = " + observer.getButton().getFloor());
                }
                if (observer != null) {
                    System.out.println(elevator.getNumber()+" getnext=" + observer.getButton().getFloor());
                } else {
                    System.out.println(elevator.getNumber()+" getnext=null");
                }

            }
        } finally {
            synchronized (activeElevators) {
                activeElevators.remove(elevator);
            }

            System.out.println("elevator queue handler = " + elevator.getNumber() + " ended.");
        }
    }

    private void simulateDoors(Elevator elevator) {
        //System.out.println("simulatedoors for " + elevator.getNumber());
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
        //System.out.println("done simulating ");
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

            if (floor == Elevators.SPECIAL_FOR_STOP) {
                //System.out.println("STOPPING elevator " + elevator.getNumber());
                //elevator.setStop(true);
                immediateStopElevator(elevator);
                return;
            } else if (elevator.isStop()) {
                System.out.println("elevator " + elevator.getNumber() +" SETSTOP=FALSEEEEE");
                elevator.setStop(false); //doesnt sync well atm
                //handleButtonQueue(elevator);
                //stream.println("m " + elevator.getNumber() + " " + elevator.getCurrentObserver().getButton().getDir());
            }
            
            ElevatorButton button = new ElevatorButton(floor, dir, true);
            InnerObserver observer = new InnerObserver(elevator, button);
            elevator.registerObserver(observer);

            //System.out.println("e:"+elevator.getNumber()+" 1");
            handleButtonQueue(elevator);

        } finally {
            stopTimer();
        }
    }

    public void immediateStopElevator(Elevator elevator) {
        System.out.println("STOPPING elevator " + elevator.getNumber());
        elevator.setStop(true);
        stream.println("m " + elevator.getNumber() + " 0");
        //ta bort alla buttons tryckningar å anroppa pressButton
    }
    
    public void stopElevator(Elevator elevator) {
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

        //static Lock lock = new ReentrantLock();
        //Condition condition = lock.newCondition();
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
            stream.println("s " + elevator.getNumber() + " " + floor);
            if (button.getFloor() == floor) {
                //shouldStop.set(true);
            }
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

            System.out.println(elevator.getNumber() + " waiting on floor ... " + floor);
            waitingThread = Thread.currentThread();

            if (floor >= elevator.Getpos() - 0.001) {
                while (elevator.Getpos() + 0.001 <= floor && elevator.getCurrentObserver() == this) {
                    try {
                        lock.lock();
                        try {
                            condition[elevator.getNumber()].await();
                        } finally {
                            lock.unlock();
                        }
                    } catch (InterruptedException ex) {
                        System.out.println(this.getFloor() + " INTERRUPT!");
                        return;
                    }
                }
            } else {
                while (elevator.Getpos() - 0.001 >= floor && elevator.getCurrentObserver() == this) {
                    try {
                        lock.lock();
                        try {
                            condition[elevator.getNumber()].await();
                        } finally {
                            lock.unlock();
                        }
                    } catch (InterruptedException ex) {
                        System.out.println(this.getFloor() + " INTERRUPT!");
                        return;
                    }
                }
            }
            shouldStop.set(true);
            waitingThread = null;
            elevator.removeObserver(InnerObserver.this);
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
                waitingThread.interrupt();
            }
        }

        @Override
        public void signalStop() {
            if (waitingThread != null) {
                waitingThread.interrupt();
            }
            /*try {
                Thread.currentThread().wait();
            } catch (InterruptedException ex) {
                Logger.getLogger(ElevatorController.class.getName()).log(Level.SEVERE, null, ex);
            }*/
        }
    }
}
