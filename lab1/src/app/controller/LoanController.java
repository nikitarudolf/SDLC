package app.controller;

import app.model.LoanModel;
import app.model.RawInput;

public class LoanController {

    private final LoanModel model;

    public LoanController(LoanModel model) {
        this.model = model;
    }

    public void onDataEntered(String amount, String rate, String years) {
        model.calculate(new RawInput(amount, rate, years));
    }
}
