package com.novabank.contracts;

/** Kafka topic names. See docs/UPGRADE-SPEC.md section 4.2 for the event catalog. */
public final class Topics {

    /** Transaction -> Account: DebitAccount, CreditAccount. Key: accountNumber. */
    public static final String ACCOUNT_COMMANDS = "novabank.account.commands.v1";

    /** Account -> Transaction, Notification: UserRegistered, AccountDebited, AccountCredited, AccountOperationRejected. */
    public static final String ACCOUNT_EVENTS = "novabank.account.events.v1";

    /** Transaction -> others: TransactionCompleted, TransactionFailed. Key: reference. */
    public static final String TRANSACTION_EVENTS = "novabank.transaction.events.v1";

    private Topics() {
    }
}
