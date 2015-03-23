package elevator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.swing.JComponent;

/**
 * Title: Green Elevator Description: Green Elevator, 2G1915 Copyright:
 * Copyright (c) 2001 Company: IMIT/KTH
 *
 * @author Vlad Vlassov
 * @version 1.0
 */
/**
 * Represents a state of components of one elevator, i.e. the motor, the
 * elevator cabin, the door and the scale, and provides methods for inspecting
 * and altering the state.
 * <p>
 * An object with this class holds the following state components:
 * <ul>
 * <li>The current state of the elevator motor which can be in one of three
 * possible states: (i) stopped, (ii) moving the cabin upwards, (iii) moving the
 * cabin downwards.
 * <li>The current y-position of the elevator cabin.
 * <li>The current state of the door which can be in one of six different states
 * reflecting different "degree of openness" of the door from "completely open"
 * to "completely closed".
 * <li>The current direction of the movement of the door (opening, closing,
 * still).
 * <li>The current position of the elevator scale (the level indicator)
 * </ul>
 *
 * @author Vlad Vlassov, IMIT/KTH, Stockholm, Sweden
 * @version 1.0
 * @see elevator.rmi.Elevator
 */
public class Elevator {

    /**
     * Object used to synchronize updates to the state of the motor of this
     * elevator
     */
    protected Object motorLock = new Object();
    /**
     * Object used to synchronize updates to the state of the door of this
     * elevator
     */
    protected Object doorLock = new Object();
    // private fields
    private int boxdir = 0;
    private int doordir = 0;
    private double boxpos = 0;
    private int scalepos = 0;
    private int doorstat = DoorStatus.CLOSED;
    private int topFloor = 0;
    private int number = 0;

    private ElevatorObserver currentObserver;
    private AtomicBoolean stop = new AtomicBoolean();
    private int queueTopFloor = -1;
    private int queueBotFloor = -1;

    private JComponent window;
    private JComponent scale;

    private final ArrayList<ElevatorObserver> observers = new ArrayList();

    /**
     * Constructs an instance of <code>Elevator</code> that represents the
     * elevator with the given number. Sets the position of the elevator cabin
     * at the bottom floor, sets the value of the elevator scale to the bottom
     * floor number.
     *
     * @param number the integer number of the elevator represented by this
     * <code>Elevator</code>
     */
    public Elevator(int number) {
        scalepos = 0;
        boxpos = 0.0;
        this.topFloor = Elevators.topFloor;
        this.number = number;
    }

    public void registerObserver(ElevatorObserver observer) {
        
        //System.out.println("ISPANEL="+isPanelButton);
        synchronized (observers) {
            for (ElevatorObserver observer1 : observers) {
                if (observer1.getButton().getFloor() == observer.getButton().getFloor()
                        && (observer1.getButton().getDir() == observer.getButton().getDir()
                        || observers.isEmpty())) {
                    return;
                }
            }
            //System.out.println("ADDING ISPANEL="+isPanelButton);
            observers.add(observer);

            Collections.sort(observers, new Comparator<ElevatorObserver>() {
                public int compare(ElevatorObserver firstObserver, ElevatorObserver nextObserver) {
                    return firstObserver.compareTo(nextObserver);
                }
            });
            
            updateQueueBotTopStatus();
            
            System.out.print(getNumber()+"::-> ");
            for (ElevatorObserver observer1 : observers) {
                System.out.print(observer1.getButton().getFloor() + ", ");
            }
            System.out.println("");
            
            if (currentObserver == null) {
                currentObserver = observer;
                return;
            }
            int currentDir = currentObserver.getButton().getDir();
            int currentFloor = currentObserver.getButton().getFloor();
            
            int elevatorDir = 0;
            if (currentFloor > Getpos()) {
                elevatorDir = 1;
            } else if (currentFloor < Getpos()){
                elevatorDir = -1;
            }
            
            if ((currentDir == observer.getButton().getDir() /*|| observer.getButton().isPanelButton()*/)
                    && ((currentDir == -1) && (currentFloor < observer.getButton().getFloor())
                    || ((currentDir == 1) && (currentFloor > observer.getButton().getFloor())))
                || (observer.getButton().isPanelButton() 
                    && (!currentObserver.getButton().isPanelButton() 
                        //ny panel - e.getpos > old panel -e 
                        || ((currentDir == -1) && observer.getButton().getFloor() - Getpos() > currentObserver.getButton().getFloor() - Getpos())
                        || ((currentDir == 1) && observer.getButton().getFloor() - Getpos() < currentObserver.getButton().getFloor() - Getpos())
                    //&& (((elevatorDir == -1) && (currentFloor < observer.getButton().getFloor())
                    //|| ((elevatorDir == 1) && (currentFloor > observer.getButton().getFloor()))))
                        )
                    )
                ) {
                System.out.println(getNumber()+"ISPANEL="+observer.getButton().isPanelButton() + " eDir="+elevatorDir);
                System.out.println(getNumber()+" " + (observer.getButton().getFloor() - Getpos()) + " >(-1) or <(1) " + (currentObserver.getButton().getFloor() - Getpos()));
                ElevatorObserver tempObserver = currentObserver;
                currentObserver = observer;
                System.out.println(getNumber()+"tempOb = " + tempObserver.getButton().getFloor());
                System.out.println(getNumber()+"currentOb = " + currentObserver.getButton().getFloor());
                tempObserver.interruptWait();
                //reschedule buttons
            }
            if (observer.getButton().isPanelButton() && currentObserver != observer) {
                currentObserver.interruptWait();
            }
            //System.out.println("ISPANEL="+isPanelButton);
        }
    }
    
    private void updateQueueBotTopStatus() {
        try {
            queueTopFloor = observers.get(observers.size()-1).getButton().getFloor();
        } catch (Exception e) {
            queueTopFloor = getCurrentFloor();
        }
        try {
            queueBotFloor = observers.get(0).getButton().getFloor();
        } catch (Exception e) {
            queueBotFloor = getCurrentFloor();
        }
    }

    public ElevatorObserver getCurrentObserver() {
        return currentObserver;
    }

    public boolean containsButton(ElevatorButton button) {
        synchronized (observers) {
            for (ElevatorObserver tempObserver : observers) {
                if(tempObserver.getButton().getFloor() != button.getFloor()
                    || tempObserver.getButton().getDir() != button.getDir()
                    || tempObserver.getButton().isPanelButton() != button.isPanelButton()){
                    continue;
                } 
                return true;
            }
            return false;
        }
    }
    
    public ElevatorObserver getNextUpObserver() {
        ElevatorObserver tempObserver = null;

        synchronized (observers) {
            if (currentObserver != null && observers.contains(currentObserver)) {
                return currentObserver;
            }

            for (ElevatorObserver observer : observers) {
                int tempFloor = observer.getButton().getFloor();
                //System.out.println("TEMP FLOOR = " + tempFloor);
                //if(currentObserver != null)System.out.println("CURRENT FLOOR = " + currentObserver.getButton().getFloor());
                //4, 5, 6
                //current != null && 4 < 5, 
                //current=4, 
                //5 ,6 
                //true, true(5), 4 < 5
                
                //System.out.println(currentObserver+" "+tempObserver);
            
                //tom och nuvarande knapp är närmare
                if (currentObserver != null && currentObserver.getButton().getFloor() < tempFloor) {
                    System.out.println(getNumber()+" breaks------------------------------------------");
                    break;
                } //föregående observer har bra värden
                //tempfloor ligger över (längre bort) en annan upp
                else if (tempObserver != null && tempFloor > tempObserver.getButton().getFloor()) {
                    System.out.println(getNumber()+" breaks------------------------------------------");
                    break;
                }
                System.out.println("TEMP FLOOR = " + tempFloor);
                if(tempFloor > Getpos()) tempObserver = observer;
            }
        }

        if (tempObserver != null) {
            currentObserver = tempObserver; //exp: 5
            System.out.println(getNumber()+"UP: tempObserver = " + tempObserver.getButton().getFloor());
        } 
        
        return tempObserver;
    }

    public ElevatorObserver getNextDownObserver() {
        ElevatorObserver tempObserver = null;
        
        synchronized (observers) {
            if (observers.contains(currentObserver)) {
                //System.out.println("returning observer");
                return currentObserver;
            }
            // 2,5 
            for (ElevatorObserver observer : observers) {
                int tempFloor = observer.getButton().getFloor();
                /*if (currentObserver != null && tempObserver != null 
                        && currentObserver.getButton().getFloor() < observer.getButton().getFloor()) {//current ... > tempObserver.getButton().getFloor()) {
                    System.out.println(getNumber()+"DOWN: currentObserver="+ currentObserver.getButton().getFloor());
                    break;
                }
                tempObserver = observer;*/
                if (currentObserver != null && currentObserver.getButton().getFloor() < tempFloor) {
                    System.out.println(getNumber()+" breaks------------------------------------------");
                    break;
                } //föregående observer har bra värden
                //tempfloor ligger under (längre bort) en annan vi har tittat på
                else if (tempObserver != null && tempFloor < tempObserver.getButton().getFloor()) {
                    System.out.println(getNumber()+" breaks------------------------------------------");
                    break;
                }
                System.out.println("TEMP FLOOR = " + tempFloor);
                if(tempFloor < Getpos()) tempObserver = observer;
            }
        }
        
        if (tempObserver != null) {
            currentObserver = tempObserver;
            System.out.println(getNumber()+"DOWN: tempObserver = " + tempObserver.getButton().getFloor());
            
        }

        return tempObserver;
    }

    /**
     * Sets position of the elevator cabin to the specified double value.
     *
     * @param f float position to be set.
     */
    public void Setpos(double f) {
        if (f < 0) {
            System.err.println("In Setpos: Position out of range = " + f);
            boxpos = 0;
            return;
        }
        if (f > topFloor) {
            System.err.println("In Setpos: Position out of range = " + f);
            boxpos = topFloor;
            return;
        }

        //System.out.println("f = " + f + " f%1= " + f % 1); 
        if (f % 1 < 0.04 || f % 1 > 0.97) {
            System.out.println(getNumber()+" signaling floor = " + (int) Math.round(f));
            ElevatorObserver observer = null;
            synchronized (observers) {
                for (ElevatorObserver observerTemp : observers) {
                    observer = observerTemp;
                    if(observer != null) {
                        break;
                    }
                    //observers.get(0).signalPosition((int) Math.round(f));
                }
            }
            if(observer != null) observer.signalPosition((int) Math.round(f));
        }

        boxpos = f; //still here ?
    }
    
    public int getQueueTopFloor() {
        synchronized (observers) {
            return queueTopFloor;
        }
    }
    
    public int getQueueBotFloor() {
        synchronized (observers) {
            return queueBotFloor;
        }
    }
    
    public int getCurrentFloor() {
        return (int) Math.round(boxpos);
    }

    public void removeObserver(ElevatorObserver observer) {
        synchronized (observers) {
            observers.remove(observer);
            if (observer == currentObserver) {
                currentObserver = null;
            }
            updateQueueBotTopStatus();
        }
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    /**
     * Sets the state of the door to the specified state (DoorStatus.OPEN1, ...
     * DoorStatus.OPEN4, DoorStatus.CLOSED).
     *
     * @param s the integer state code to be set.
     */
    public void Setdoorstat(int s) {
        if (s < DoorStatus.CLOSED) {
            System.err.println("In Setdoorstat: Doorstatus out of range = " + s);
            doorstat = DoorStatus.CLOSED;
            return;
        }
        if (s > DoorStatus.OPEN4) {
            System.err.println("In Setdoorstat: Doorstatus out of range = " + s);
            doorstat = DoorStatus.OPEN4;
            return;
        }
        doorstat = s; //still here ?
    }

    /**
     * Sets the direction of the motor to the specified direction, e.g. moving
     * downwards (-1), moving upwards (1), none = stopped (0).
     *
     * @param d an integer code of the direction to be set.
     */
    public void Setdir(int d) {
        if (d < -1 || d > 1) {
            System.err.println("In Setdir: Direction out of range = " + d);
            boxdir = 0;
        } else {
            boxdir = d;
        }
    }

    /**
     * Sets the movement direction of the door to the specified direction, e.g.
     * closing (-1), opening (1), still (0)
     *
     * @param d the integer code of the direction to be set.
     */
    public void Setdoor(int d) {
        if (d < -1 || d > 1) {
            System.err.println("In Setdoor: Direction out of range = " + d);
            doordir = 0;
        } else {
            doordir = d;
        }
    }

    /**
     * Sets the scale position to the specified value (level number).
     *
     * @param s the integer position value to be set.
     */
    public void Setscalepos(int s) {
        if (s < 0 || s > topFloor) {
            System.err.println("In Setscale: Scalevalue out of range = " + s);
        } else {
            scalepos = s;
        }
    }

    /**
     * Sets a GUI component (window) with the
     * <code>javax.swing.JComponent</code> class used to display this <code>Elevator<code>.
     *
     * @param w <code>JComponent</code> to be set.
     */
    public void Setwin(JComponent w) {
        window = w;
    }

    /**
     * Sets a GUI component (e.g. gauge) with the
     * <code>javax.swing.JComponent</code> class used to display the scale value
     * of this <code>Elevator<code>.
     *
     * @param w <code>JComponent</code> to be set.
     */
    public void Setscale(JComponent s) {
        scale = s;
    }

    /**
     * Returns the current position of the elevator cabin.
     *
     * @return the current position of the cabin as a double value in "floor
     * units".
     */
    public double Getpos() {
        return boxpos;
    }

    /**
     * Returns the current state of the door (DoorStatus.OPEN1, ...
     * DoorStatus.OPEN4, DoorStatus.CLOSED).
     *
     * @return the integer code of the current state of the door.
     */
    public int Getdoorstat() {
        return doorstat;
    }

    /**
     * Returns the current direction of movement of the elevator cabin (motor),
     * e.g. 1 (upwards), -1 (downwards), 0(stopped).
     *
     * @return the integer code of the cabin movement direction.
     */
    public int Getdir() {
        return boxdir;
    }

    /**
     * Returns the current direction of movement of the door, e.g. 1 (opening),
     * -1 (closing), 0 (still).
     *
     * @return the the integer code of the door movement direction.
     */
    public int Getdoor() {
        return doordir;
    }

    /**
     * Returns the current position (level) of the elevator scale.
     *
     * @return the the integer value of the current position of the elevator
     * scale.
     */
    public int Getscalepos() {
        return scalepos;
    }

    /**
     * Returns the GUI component with the <code>javax.swing.JComponent</code>
     * class used to display this <code>Elevator<code>.
     *
     * @return <code>JComponent</code> used to display this <code>Elevator<code>.
     */
    JComponent Getwin() {
        return window;
    }

    /**
     * Returns the GUI component with the <code>javax.swing.JComponent</code>
     * class used to display the value of the scale of this <code>Elevator<code>.
     *
     * @return <code>JComponent</code> used to display this <code>Elevator<code>.
     */
    JComponent Getscale() {
        return scale;
    }

    public boolean isStop() {
        System.out.println("stop = " + stop);
        synchronized (stop) {
            return stop.get();
        }
    }

    public void setStop(boolean stop) {
        //currentObserver.signalStop();
        synchronized (this.stop) {
            this.stop.set(stop);
        }
        //signal
        //if(currentObserver != null) currentObserver.signalStop();
        ElevatorObserver observer = null;
        synchronized (observers) {
            for (ElevatorObserver observerTemp : observers) {
                observer = observerTemp;
                if(observer != null) {
                    break;
                }
                //observers.get(0).signalPosition((int) Math.round(f));
            }
        }
        if(observer != null) observer.signalStop();
    }
    
    


}
