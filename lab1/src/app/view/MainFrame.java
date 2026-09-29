package app.view;

import app.controller.LoanController;
import app.model.LoanInput;
import app.model.LoanModel;
import app.model.LoanResult;
import app.model.ModelListener;
import app.model.RawInput;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

public class MainFrame extends JFrame implements ModelListener {

    private static final String EMPTY_VALUE = "---";

    private final LoanModel model;
    private final LoanController controller;

    private RawInput lastInput = RawInput.empty();

    private final JLabel amountLabel = new JLabel();
    private final JLabel rateLabel = new JLabel();
    private final JLabel yearsLabel = new JLabel();

    private final JLabel monthlyLabel = new JLabel();
    private final JLabel totalLabel = new JLabel();
    private final JLabel overpayLabel = new JLabel();
    private final JLabel ratioLabel = new JLabel();

    public MainFrame(LoanModel model, LoanController controller) {
        super("Кредит на жилье - вариант 19");
        this.model = model;
        this.controller = controller;

        buildUi();

        model.addListener(this);
        refresh();

        pack();
        setMinimumSize(new Dimension(540, getHeight()));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    private void buildUi() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        content.add(section("Исходные данные",
                new String[]{"Сумма кредита:", "Процентная ставка:", "Срок кредита:"},
                new JLabel[]{amountLabel, rateLabel, yearsLabel}));
        content.add(Box.createVerticalStrut(10));
        content.add(section("Результат расчёта",
                new String[]{"Ежемесячный платёж:", "Общая сумма выплат:",
                        "Переплата банку:", "Во сколько раз переплата:"},
                new JLabel[]{monthlyLabel, totalLabel, overpayLabel, ratioLabel}));

        JButton inputButton = new JButton("Ввести данные");
        inputButton.addActionListener(e -> showInputDialog());

        JPanel buttons = new JPanel();
        buttons.add(inputButton);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(inputButton);
    }

    private JPanel section(String title, String[] captions, JLabel[] values) {
        JPanel panel = new JPanel(new GridLayout(captions.length, 2, 8, 6));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        for (int i = 0; i < captions.length; i++) {
            panel.add(new JLabel(captions[i]));
            values[i].setFont(values[i].getFont().deriveFont(Font.BOLD));
            panel.add(values[i]);
        }
        return panel;
    }

    private void showInputDialog() {
        InputDialog dialog = new InputDialog(this, model, controller, lastInput);
        dialog.setVisible(true);
    }

    @Override
    public void modelChanged(LoanModel model) {
        this.lastInput = model.getLastInput();
        refresh();
    }

    private void refresh() {
        LoanInput input = model.getInput();
        LoanResult result = model.getResult();

        if (input == null || result == null) {
            amountLabel.setText(EMPTY_VALUE);
            rateLabel.setText(EMPTY_VALUE);
            yearsLabel.setText(EMPTY_VALUE);
            monthlyLabel.setText(EMPTY_VALUE);
            totalLabel.setText(EMPTY_VALUE);
            overpayLabel.setText(EMPTY_VALUE);
            ratioLabel.setText(EMPTY_VALUE);
            return;
        }

        amountLabel.setText(money(input.getAmount()) + " BYN");
        rateLabel.setText(number(input.getAnnualRate()) + " % годовых");
        yearsLabel.setText(input.getYears() + " лет (" + input.getMonths() + " мес.)");

        monthlyLabel.setText(money(result.getMonthlyPayment()) + " BYN");
        totalLabel.setText(money(result.getTotalPayment()) + " BYN");
        overpayLabel.setText(money(result.getOverpayment()) + " BYN");
        ratioLabel.setText(String.format("в %.2f раза", result.getOverpayRatio()));
    }

    private static String money(double value) {
        return String.format("%,.2f", value);
    }

    private static String number(double value) {
        return String.format("%.2f", value);
    }
}
