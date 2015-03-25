package elevator;

/**
 * An ElevatorObserver is a communication tool used for synchronization of e.g.
 * button queue handling as the class Elevator Controller.
 *
 * @author Gabriel
 */
interface ElevatorObserver extends Comparable<ElevatorObserver> {

    void signalPosition(int floor);

    ElevatorButton getButton();

    void waitPosition();

    void interruptWait();

    void signalStop();

}
