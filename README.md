# ShubhCore0.26.2

Paper plugin project for SHUBH HYPIXEL. Target API: Paper 1.21.4 / Java 21. Build with `mvn clean package` or use the GitHub Actions workflow (`Build ShubhCore`).

## Features
- Animated sidebar scoreboard and TAB header/footer
- Rank prefixes above player names and rank-weighted TAB sorting (OWNER, DEV, ADMIN, MOD, VIP, PLAYER)
- Spawn and rank commands
- Custom wallet/bank currency named **Shubhcoins** (short label `SHC`)
- Persistent wallet, bank balances, bank loans, and player-to-player loan debts in `plugins/ShubhCore0.26.2/economy.yml`
- Separate `/shcb` bank command

## Economy commands
- `/shc balance` — wallet and bank balances
- `/shc pay <player> <amount>` — pay from wallet
- `/shc loan bank <amount>` — borrow from the bank into wallet
- `/shc loan lend <player> <amount>` — lend wallet coins to an online player; lender must have enough coins
- `/shc loan repay bank <amount>` — repay bank debt from wallet
- `/shc loan repay <lender> <amount>` — repay a player loan from wallet
- `/shcb` or `/shcb balance` — bank and wallet balances
- `/shcb deposit <amount>` — deposit wallet coins into bank
- `/shcb withdraw <amount>` — withdraw bank coins into wallet
- `/shcb loan <amount>` — take a bank loan
- `/shcb loan repay <amount>` — repay bank loan
- `/shc eco <give|take|set> <player> <amount>` — admin wallet management

The bank balance itself has no configured cap. The default bank-loan cap is `100000` Shubhcoins and can be changed in `config.yml` at `economy.bank-loan-max`. All amounts are positive whole numbers. Loans are tracked without interest; player-to-player lending is initiated by the lender and requires the borrower to repay manually.

## Build and install
1. Install JDK 21 and Maven, or push the project to GitHub and run Actions → Build ShubhCore.
2. Download the build artifact containing `ShubhCore0.26.2.jar`.
3. Upload the JAR to the Paper server's `plugins` folder and restart.
4. For Bedrock players, run a compatible Geyser/Floodgate setup alongside Paper. Verify formatting on your exact server version before production use.

This project ZIP contains source code, not a precompiled/tested JAR. Build it and test on a backup server before using it live.
