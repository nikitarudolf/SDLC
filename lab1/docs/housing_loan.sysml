// SysML v2 (текстовая нотация) - структура приложения "Кредит на жилье", вариант 19.

package HousingLoanCredit {

    part def LoanApplication {
        part model : LoanModel;
        part view : LoanView;
        part controller : LoanController;
    }

    part def LoanModel {
        attribute amount : ScalarValues::Real;      // сумма кредита, BYN
        attribute annualRate : ScalarValues::Real;  // процентная ставка, % годовых
        attribute years : ScalarValues::Integer;    // срок кредита, лет

        attribute monthlyPayment : ScalarValues::Real;
        attribute totalPayment : ScalarValues::Real;
        attribute overpayRatio : ScalarValues::Real;
    }

    part def LoanView {
        part mainFrame : MainFrame;
        part inputDialog : InputDialog;
    }

    part def MainFrame;
    part def InputDialog;
    part def LoanController;

    // Аннуитетный платёж: P = S * i * (1+i)^n / ((1+i)^n - 1)
    calc def MonthlyPayment {
        in amount : ScalarValues::Real;
        in monthlyRate : ScalarValues::Real;
        in months : ScalarValues::Integer;
        return payment : ScalarValues::Real;
    }

    requirement def ValidInputRequired {
        doc /* Сумма и срок должны быть положительными, ставка неотрицательной;
               иначе приложение показывает сообщение об ошибке. */
    }

    requirement def LastInputRestored {
        doc /* Последние введённые данные запоминаются в главной форме
               и восстанавливаются при повторном открытии окна ввода. */
    }
}
