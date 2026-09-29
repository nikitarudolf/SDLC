package app;

import app.controller.LoanController;
import app.model.LoanModel;
import app.view.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {

            }

            LoanModel model = new LoanModel();
            LoanController controller = new LoanController(model);
            MainFrame view = new MainFrame(model, controller);

            view.setVisible(true);
        });
    }
}
