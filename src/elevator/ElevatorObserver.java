package elevator;

/**
 *
 * @author Gabriel
 */
interface ElevatorObserver {

    void  signalPosition(int floor);

    FloorButton getButton();

    void waitPosition();

}
