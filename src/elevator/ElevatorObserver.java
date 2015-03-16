package elevator;

/**
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
