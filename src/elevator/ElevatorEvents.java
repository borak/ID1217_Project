package elevator;

import static elevator.ElevatorIO.in;
import java.awt.event.*;
import java.io.PrintStream;
import java.util.StringTokenizer;

/**
 * Title: Green Elevator Description: Green Elevator, 2G1915 Copyright:
 * Copyright (c) 2001 Company: IMIT/KTH
 *
 * @author Vlad Vlassov
 * @version 1.0
 */
/**
 * Provides an action listener for all buttons and a window listener for the
 * elevator main frame.
 */
public class ElevatorEvents extends WindowAdapter implements ActionListener {

    private java.io.PrintStream out;
    private ElevatorController elevatorController;

    /**
     * Creates an instance ElevatorEvents listener
     */
    public ElevatorEvents(PrintStream out, ElevatorController elevatorController) {
        super();
        this.out = out;
        this.elevatorController = elevatorController;

    }

    /**
     * Invoked when a button is pressed on a floor or on an inside button panel.
     * Prints an action commands associated with the button to the output stream
     * (either standard output of a socket output stream). An action command of
     * a floor button is "b <i>f d</i>" (where "b" stands for "button", <i>f</i>
     * is the number of the floor where the button was pressed, <i>d</i> is a
     * direction (upwards or downwards) assigned with the button. An action
     * command of a inside button is "p <i>n f</i>" (where "p" stands for
     * "panel", <i>n</i> is the number of the elevator where the button was
     * pressed, <i>f</i> is a floor number assigned with the button.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        new Thread(() -> {
            out.println(e.getActionCommand());
            String[] tokens = new String[3];
            StringTokenizer tokenizer = new StringTokenizer(e.getActionCommand());
            if (tokenizer.countTokens() < 1) {
                return;
            }
            tokens[0] = tokenizer.nextToken();
            if (tokens[0].equalsIgnoreCase("p") || tokens[0].equalsIgnoreCase("panel")) {
                if (tokenizer.countTokens() < 2) {
                    return;
                }
                try {
                    elevatorController.pressPanel(Integer.parseInt(tokenizer.nextToken()), Integer.parseInt(tokenizer.nextToken()));
                } catch (Exception ex) {
                    System.err.println("Illegal command: " + e.getActionCommand());
                }
            } else if (tokens[0].equalsIgnoreCase("b") || tokens[0].equalsIgnoreCase("button")) {
                if (tokenizer.countTokens() < 2) {
                    return;
                }
                try {
                    elevatorController.pressButton(Integer.parseInt(tokenizer.nextToken()), Integer.parseInt(tokenizer.nextToken()));
                } catch (Exception ex) {
                    System.err.println("Illegal command: " + e.getActionCommand());
                }
            }
        }).start();
    }

    /**
     * Invoked when the window is closing. The application exits.
     */
    public void windowClosing(WindowEvent e) {
        System.err.println("Bye Bye");
        System.exit(0);
    }
}
