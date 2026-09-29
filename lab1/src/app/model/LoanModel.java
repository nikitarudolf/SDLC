package app.model;

import java.util.ArrayList;
import java.util.List;

public class LoanModel {

    private final List<ModelListener> listeners = new ArrayList<>();

    private RawInput lastInput = RawInput.empty();

    private LoanInput input;

    private LoanResult result;

    private String errorMessage;

    public void addListener(ModelListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(ModelListener listener) {
        listeners.remove(listener);
    }

    private void fireModelChanged() {
        for (ModelListener listener : new ArrayList<>(listeners)) {
            listener.modelChanged(this);
        }
    }

    public void calculate(RawInput raw) {
        this.lastInput = raw;

        try {
            LoanInput parsed = LoanInput.parse(raw);
            this.input = parsed;
            this.result = computeAnnuity(parsed);
            this.errorMessage = null;
        } catch (ValidationException e) {
            this.result = null;
            this.errorMessage = e.getMessage();
        }

        fireModelChanged();
    }

    private LoanResult computeAnnuity(LoanInput in) {
        int months = in.getMonths();
        double monthlyRate = in.getAnnualRate() / 100.0 / 12.0;

        double monthlyPayment;
        if (monthlyRate == 0.0) {
            monthlyPayment = in.getAmount() / months;
        } else {
            double factor = Math.pow(1.0 + monthlyRate, months);
            monthlyPayment = in.getAmount() * monthlyRate * factor / (factor - 1.0);
        }

        double totalPayment = monthlyPayment * months;
        double overpayment = totalPayment - in.getAmount();
        double overpayRatio = totalPayment / in.getAmount();

        return new LoanResult(monthlyPayment, totalPayment, overpayment, overpayRatio);
    }

    public RawInput getLastInput() {
        return lastInput;
    }

    public LoanInput getInput() {
        return input;
    }

    public LoanResult getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasError() {
        return errorMessage != null;
    }

    public boolean hasResult() {
        return result != null;
    }
}
