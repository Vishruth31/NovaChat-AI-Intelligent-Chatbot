package chatbot;

import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class LoginScreen extends JFrame {
    private final JTextField userField = new JTextField();
    private final JPasswordField passField = new JPasswordField();

    public LoginScreen() {
        setTitle("Chatbot - Login / Signup");
        setSize(380, 220);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10,10));

        JPanel fields = new JPanel(new GridLayout(4,1,5,5));
        fields.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        fields.add(new JLabel("Username:"));
        fields.add(userField);
        fields.add(new JLabel("Password:"));
        fields.add(passField);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton loginBtn = new JButton("Login");
        JButton signupBtn = new JButton("Signup");
        buttons.add(loginBtn);
        buttons.add(signupBtn);

        add(fields, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        loginBtn.addActionListener(e -> login());
        signupBtn.addActionListener(e -> signup());

        setVisible(true);
    }

    private void signup() {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword()).trim();
        if (u.isEmpty() || p.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter both fields"); return; }

        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, u);
            ps.setString(2, p);
            int rows = ps.executeUpdate();
            System.out.println("Rows inserted (signup): " + rows);
            JOptionPane.showMessageDialog(this, "Signup successful. You can login now.");
        } catch (SQLIntegrityConstraintViolationException ex) {
            JOptionPane.showMessageDialog(this, "Username already exists.");
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private void login() {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword()).trim();
        if (u.isEmpty() || p.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter both fields"); return; }

        String sql = "SELECT * FROM users WHERE username=? AND password=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, u);
            ps.setString(2, p);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                dispose();
                SwingUtilities.invokeLater(() -> new chatbot(u));
            } else {
                JOptionPane.showMessageDialog(this, "Invalid username/password");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginScreen::new);
    }
}
