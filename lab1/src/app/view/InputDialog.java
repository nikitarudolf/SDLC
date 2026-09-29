package app.view;

import app.controller.LoanController;
import app.model.LoanModel;
import app.model.ModelListener;
import app.model.RawInput;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridLayout;

public class InputDialog extends JDialog implements ModelListener {

    private final LoanModel model;
    private final LoanController controller;

    private final JTextField amountField = new JTextField(14);
    private final JTextField rateField = new JTextField(14);
    private final JTextField yearsField = new JTextField(14);

    public InputDialog(Frame owner, LoanModel model, LoanController controller, RawInput lastInput) {
        super(owner, "Ввод данных по кредиту", true);
        this.model = model;
        this.controller = controller;

        buildUi();
        restore(lastInput);

        model.addListener(this);

        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        JPanel fields = new JPanel(new GridLayout(3, 2, 8, 8));
        fields.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        fields.add(new JLabel("Сумма кредита, BYN:"));
        fields.add(amountField);
        fields.add(new JLabel("Процентная ставка, % годовых:"));
        fields.add(rateField);
        fields.add(new JLabel("Срок кредита, лет:"));
        fields.add(yearsField);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> submit());

        JButton cancelButton = new JButton("Отмена");
        cancelButton.addActionListener(e -> dispose());

        JPanel buttons = new JPanel();
        buttons.add(okButton);
        buttons.add(cancelButton);

        setLayout(new BorderLayout());
        add(fields, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okButton);
    }

    private void restore(RawInput lastInput) {
        amountField.setText(lastInput.getAmount());
        rateField.setText(lastInput.getRate());
        yearsField.setText(lastInput.getYears());
    }

    private void submit() {
        controller.onDataEntered(amountField.getText(), rateField.getText(), yearsField.getText());
    }

    @Override
    public void modelChanged(LoanModel model) {
        if (model.hasError()) {
            JOptionPane.showMessageDialog(this, model.getErrorMessage(),
                    "Некорректные данные", JOptionPane.ERROR_MESSAGE);
        } else {
            dispose();
        }
    }

    @Override
    public void dispose() {
        model.removeListener(this);
        super.dispose();
    }
}
