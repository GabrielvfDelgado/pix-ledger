package io.github.gabrielvfdelgado.pixledger.application.port.out;

/** Resposta definitiva do banco externo. */
public final class BankDecision {

    private static final BankDecision APPROVED = new BankDecision(true, null);

    private final boolean approved;
    private final String reason;

    private BankDecision(boolean approved, String reason) {
        this.approved = approved;
        this.reason = reason;
    }

    public static BankDecision approved() {
        return APPROVED;
    }

    public static BankDecision rejected(String reason) {
        return new BankDecision(false, reason);
    }

    public boolean isApproved() {
        return approved;
    }

    public String reason() {
        return reason;
    }
}
