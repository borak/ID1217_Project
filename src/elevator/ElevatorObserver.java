package elevator;

/**
 *
 * @author Gabriel
 */
interface ElevatorObserver {

    void  signalPosition(int floor);

    ElevatorButton getButton();

    void waitPosition();

}
