package dev.empengines.chunkupkeep.economy;

import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;

import java.util.UUID;

/** Thin wrapper around Numismatics bank accounts. Amounts are in spurs. */
public final class Bank {
    public static BankAccount account(UUID player) {
        return Numismatics.BANK.getOrCreateAccount(player, BankAccount.Type.PLAYER);
    }

    public static int balance(UUID player) {
        return account(player).getBalance();
    }

    /** Takes the amount if the balance covers it. */
    public static boolean charge(UUID player, int spurs) {
        if (spurs <= 0) return true;
        BankAccount acc = account(player);
        if (acc.getBalance() < spurs) return false;
        acc.setBalance(acc.getBalance() - spurs);
        acc.markDirty();
        Numismatics.BANK.markBankDirty();
        return true;
    }

    public static void pay(UUID player, int spurs) {
        if (spurs <= 0) return;
        BankAccount acc = account(player);
        acc.deposit(spurs);
        acc.markDirty();
        Numismatics.BANK.markBankDirty();
    }

    /** "1 crown 2 cogs 8 spurs" style, using Numismatics' coin values. */
    public static String format(int spurs) {
        int[] values = {4096, 512, 64, 16, 8, 1};
        String[] names = {"sun", "crown", "cog", "sprocket", "bevel", "spur"};
        if (spurs == 0) return "0 spurs";
        StringBuilder sb = new StringBuilder();
        int left = spurs;
        for (int i = 0; i < values.length; i++) {
            int n = left / values[i];
            if (n == 0) continue;
            left -= n * values[i];
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(n).append(' ').append(names[i]).append(n == 1 ? "" : "s");
        }
        return sb.toString();
    }

    private Bank() {}
}
