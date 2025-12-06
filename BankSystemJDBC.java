import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Random;
import java.util.Scanner;

public class BankSystemJDBC {

    // Update these if your MySQL credentials are different
    static final String URL = "jdbc:mysql://localhost:3306/bankdb?useSSL=false&allowPublicKeyRetrieval=true";
    static final String USER = "root";
    static final String PASS = "Preethi@123";

    static Scanner sc = new Scanner(System.in);
    static Random rnd = new Random();

    public static void main(String[] args) {
        System.out.println("=== Bank System (JDBC) ===");
        while (true) {
            System.out.println("\n1. Register");
            System.out.println("2. Login");
            System.out.println("3. Admin - View All Users");
            System.out.println("4. Exit");
            System.out.print("Choice: ");
            int ch = Integer.parseInt(sc.nextLine().trim());
            switch (ch) {
                case 1 -> register();
                case 2 -> login();
                case 3 -> viewUsers();
                case 4 -> {
                    System.out.println("Goodbye!");
                    return;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // -------------------- Utilities --------------------
    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes());
            // bytes -> hex
            Formatter formatter = new Formatter();
            for (byte b : bytes) formatter.format("%02x", b);
            String res = formatter.toString();
            formatter.close();
            return res;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static String generateAccountNumber() {
        // Simple 12-digit account number starting with 10
        long num = 100000000000L + (Math.abs(rnd.nextLong()) % 900000000000L);
        return Long.toString(num);
    }

    static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    // -------------------- Register --------------------
    static void register() {
        System.out.print("Choose username: ");
        String username = sc.nextLine().trim();

        System.out.print("Choose password: ");
        String password = sc.nextLine();

        String hash = sha256Hex(password);
        String acct = generateAccountNumber();

        String sql = "INSERT INTO users(account_number, username, password_hash, balance) VALUES (?, ?, ?, 0)";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, acct);
            ps.setString(2, username);
            ps.setString(3, hash);
            ps.executeUpdate();
            System.out.println("Registered! Your account number: " + acct);

        } catch (SQLException e) {
            if (e.getMessage().contains("Duplicate")) {
                System.out.println("Username already exists - choose another.");
            } else {
                e.printStackTrace();
            }
        }
    }

    // -------------------- Login --------------------
    static void login() {
        System.out.print("Username: ");
        String username = sc.nextLine().trim();
        System.out.print("Password: ");
        String password = sc.nextLine();

        String hash = sha256Hex(password);

        String sql = "SELECT account_number FROM users WHERE username=? AND password_hash=?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String acct = rs.getString("account_number");
                    System.out.println("Login successful. Account: " + acct);
                    userMenu(acct);
                } else {
                    System.out.println("Invalid credentials.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // -------------------- User menu --------------------
    static void userMenu(String acct) {
        while (true) {
            System.out.println("\n--- User Menu ---");
            System.out.println("1. Deposit");
            System.out.println("2. Withdraw");
            System.out.println("3. Balance");
            System.out.println("4. Transfer");
            System.out.println("5. View Transactions");
            System.out.println("6. Logout");
            System.out.print("Choice: ");
            int ch = Integer.parseInt(sc.nextLine().trim());
            switch (ch) {
                case 1 -> deposit(acct);
                case 2 -> withdraw(acct);
                case 3 -> System.out.println("Balance: " + getBalance(acct));
                case 4 -> transfer(acct);
                case 5 -> viewTransactions(acct);
                case 6 -> {
                    System.out.println("Logged out.");
                    return;
                }
                default -> System.out.println("Invalid.");
            }
        }
    }

    // -------------------- Balance --------------------
    static double getBalance(String acct) {
        String sql = "SELECT balance FROM users WHERE account_number=?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, acct);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    // -------------------- Deposit --------------------
    static void deposit(String acct) {
        System.out.print("Amount to deposit: ");
        double amt = Double.parseDouble(sc.nextLine().trim());
        if (amt <= 0) { System.out.println("Invalid amount."); return; }

        String sql = "UPDATE users SET balance = balance + ? WHERE account_number=?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, amt);
            ps.setString(2, acct);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                addTransaction(acct, "Deposit", amt);
                System.out.println("Deposited. New balance: " + getBalance(acct));
            } else {
                System.out.println("Deposit failed.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // -------------------- Withdraw --------------------
    static void withdraw(String acct) {
        System.out.print("Amount to withdraw: ");
        double amt = Double.parseDouble(sc.nextLine().trim());
        if (amt <= 0) { System.out.println("Invalid."); return; }

        double bal = getBalance(acct);
        if (bal < amt) { System.out.println("Insufficient funds."); return; }

        String sql = "UPDATE users SET balance = balance - ? WHERE account_number=?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, amt);
            ps.setString(2, acct);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                addTransaction(acct, "Withdraw", amt);
                System.out.println("Withdrawn. New balance: " + getBalance(acct));
            } else {
                System.out.println("Withdraw failed.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // -------------------- Transfer --------------------
    static void transfer(String senderAcct) {
        System.out.print("Receiver account number: ");
        String recv = sc.nextLine().trim();
        System.out.print("Amount to transfer: ");
        double amt = Double.parseDouble(sc.nextLine().trim());
        if (amt <= 0) { System.out.println("Invalid."); return; }

        // Transactional update (two updates) -> use DB transaction
        String withdrawSql = "UPDATE users SET balance = balance - ? WHERE account_number=? AND balance >= ?";
        String depositSql = "UPDATE users SET balance = balance + ? WHERE account_number=?";
        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps1 = con.prepareStatement(withdrawSql);
                 PreparedStatement ps2 = con.prepareStatement(depositSql)) {

                ps1.setDouble(1, amt);
                ps1.setString(2, senderAcct);
                ps1.setDouble(3, amt);
                int w = ps1.executeUpdate();

                if (w == 0) {
                    con.rollback();
                    System.out.println("Transfer failed: insufficient funds or sender not found.");
                    con.setAutoCommit(true);
                    return;
                }

                ps2.setDouble(1, amt);
                ps2.setString(2, recv);
                int d = ps2.executeUpdate();

                if (d == 0) { // receiver not found
                    con.rollback();
                    System.out.println("Transfer failed: receiver not found.");
                    con.setAutoCommit(true);
                    return;
                }

                // commit and add transactions
                con.commit();
                addTransaction(senderAcct, "Transfer Sent", amt);
                addTransaction(recv, "Transfer Received", amt);
                System.out.println("Transfer successful. New balance: " + getBalance(senderAcct));
                con.setAutoCommit(true);
            } catch (SQLException ex) {
                con.rollback();
                con.setAutoCommit(true);
                ex.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // -------------------- Transactions --------------------
    static void addTransaction(String acct, String type, double amt) {
        String sql = "INSERT INTO transactions(account_number, type, amount) VALUES (?, ?, ?)";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, acct);
            ps.setString(2, type);
            ps.setDouble(3, amt);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static void viewTransactions(String acct) {
        String sql = "SELECT id, type, amount, created_at FROM transactions WHERE account_number=? ORDER BY created_at DESC LIMIT 50";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, acct);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\n--- Transactions for " + acct + " ---");
                while (rs.next()) {
                    System.out.printf("[%d] %s : %.2f  (%s)%n",
                            rs.getInt("id"),
                            rs.getString("type"),
                            rs.getDouble("amount"),
                            rs.getTimestamp("created_at").toString());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // -------------------- Admin --------------------
    static void viewUsers() {
        String sql = "SELECT account_number, username, balance FROM users ORDER BY id";
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\n--- All Users ---");
            while (rs.next()) {
                System.out.printf("Acct: %s | User: %s | Balance: %.2f%n",
                        rs.getString("account_number"),
                        rs.getString("username"),
                        rs.getDouble("balance"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
