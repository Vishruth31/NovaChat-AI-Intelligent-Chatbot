package chatbot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.net.http.*;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import org.json.JSONObject;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;


public class chatbot extends JFrame {

    private final String username;
    private final JPanel chatPanel = new JPanel();
    private final JScrollPane scrollPane;
    private final JTextField inputField = new JTextField();
    private String geminiKey;

    private final java.util.List<JPanel> chatBlocks = new ArrayList<>();
    private final java.util.List<Integer> chatIds = new ArrayList<>();

    public chatbot(String username) {
        this.username = username;
        setTitle("Gemini Chatbot - " + username);
        setSize(600, 600);
        setMinimumSize(new Dimension(500, 500));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // MAIN PANEL
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(18, 18, 18));
        add(mainPanel, BorderLayout.CENTER);

        // CHAT PANEL
        chatPanel.setBackground(new Color(18,18,18));
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        scrollPane = new JScrollPane(chatPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // INPUT PANEL
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBackground(new Color(30,30,30));
        inputField.setForeground(Color.WHITE);
        inputField.setBorder(new EmptyBorder(10,10,10,10));

        JButton sendButton = new JButton("Send");
        sendButton.setBackground(new Color(0,120,255));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);

        JButton deleteButton = new JButton("Delete Selected");
        deleteButton.setBackground(new Color(255,50,50));
        deleteButton.setForeground(Color.WHITE);
        deleteButton.setFocusPainted(false);

        JPanel bottomPanel = new JPanel(new BorderLayout(10,10));
        bottomPanel.setBorder(new EmptyBorder(10,10,10,10));
        bottomPanel.setBackground(new Color(18,18,18));
        bottomPanel.add(inputField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT,5,0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(sendButton);
        buttonPanel.add(deleteButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // SHOW WINDOW
        setLocationRelativeTo(null);
        setVisible(true);

        // GEMINI KEY
        loadGeminiKey();

        

        // LOAD HISTORY
        loadChatHistory();

        // ACTIONS
        ActionListener sendAction = e -> sendMessage();
        sendButton.addActionListener(sendAction);
        inputField.addActionListener(sendAction);

        deleteButton.addActionListener(e -> deleteSelectedChats());
    }

    // SEND MESSAGE
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.setText("");

        int chatId = saveChatAndGetId(text, "");

        // Show typing placeholder
        JLabel typing = createMessageLabel("AI", "AI is typing...", java.time.LocalDateTime.now().toString(), false);
        JPanel typingWrapper = new JPanel(new BorderLayout());
        typingWrapper.setOpaque(false);
        typingWrapper.add(typing, BorderLayout.EAST);
        chatPanel.add(typingWrapper);
        revalidateAndScroll();

        // Show user message with checkbox
        addChatBlock(text, "", chatId, true);

        new Thread(() -> {
            try {
                String reply = getGeminiReply(text);
                SwingUtilities.invokeLater(() -> {
                    chatPanel.remove(typingWrapper);
                    addChatBlock(text, reply, chatId, false);
                    updateBotResponse(chatId, reply);
                    revalidateAndScroll();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    chatPanel.remove(typingWrapper);
                    addChatBlock(text, "Error: " + ex.getMessage(), chatId, false);
                    revalidateAndScroll();
                });
                ex.printStackTrace();
            }
        }).start();
    }

    // ADD CHAT BLOCK
    private void addChatBlock(String userMsg, String botMsg, int chatId, boolean isUserNew) {
        JPanel chatBlock = new JPanel(new BorderLayout());
        chatBlock.setOpaque(false);
        chatBlock.setBorder(new EmptyBorder(5,5,5,5));

        JCheckBox selectBox = new JCheckBox();
        selectBox.setOpaque(false);
        chatBlock.add(selectBox, BorderLayout.WEST);

        JPanel messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setOpaque(false);

        String ts = java.time.LocalDateTime.now().toString();

        messagesPanel.add(createMessageLabel("You", userMsg, ts, true));
        if(!botMsg.isEmpty())
            messagesPanel.add(createMessageLabel("AI", botMsg, ts, false));

        chatBlock.add(messagesPanel, BorderLayout.CENTER);

        chatPanel.add(chatBlock);
        chatBlocks.add(chatBlock);
        chatIds.add(chatId);

        revalidateAndScroll();
    }

    // CREATE MESSAGE LABEL
    private JLabel createMessageLabel(String sender, String text, String time, boolean isUser) {
        Color bg = isUser ? new Color(64,64,64) : new Color(0,120,255);
        JLabel label = new JLabel("<html><div style='width:400px;'>" +
                "<b>" + sender + ":</b> " + escapeHtml(text) +
                (time != null && !time.isEmpty() ? "<br><small style='color:#ccc;'>" + time + "</small>" : "") +
                "</div></html>");
        label.setOpaque(true);
        label.setBackground(bg);
        label.setForeground(Color.WHITE);
        label.setBorder(new EmptyBorder(10,10,10,10));
        label.setAlignmentX(isUser ? Component.LEFT_ALIGNMENT : Component.RIGHT_ALIGNMENT);
        return label;
    }

    private void revalidateAndScroll() {
        chatPanel.revalidate();
        chatPanel.repaint();
        SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar()
                .setValue(scrollPane.getVerticalScrollBar().getMaximum()));
    }

    // DATABASE METHODS
    private int saveChatAndGetId(String userMsg, String botMsg) {
        try(Connection conn = DBConnection.getConnection()) {
            String q = "INSERT INTO chat_history (username, user_message, bot_response) VALUES (?, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(q, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, userMsg);
            ps.setString(3, botMsg);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if(rs.next()) return rs.getInt(1);
        } catch(Exception e) { e.printStackTrace(); }
        return -1;
    }

    private void updateBotResponse(int chatId, String botMsg) {
        if(chatId == -1) return;
        try(Connection conn = DBConnection.getConnection()) {
            String q = "UPDATE chat_history SET bot_response=? WHERE id=?";
            PreparedStatement ps = conn.prepareStatement(q);
            ps.setString(1, botMsg);
            ps.setInt(2, chatId);
            ps.executeUpdate();
        } catch(Exception e) { e.printStackTrace(); }
    }

    private void loadChatHistory() {
        try(Connection conn = DBConnection.getConnection()) {
            String q = "SELECT id, user_message, bot_response, timestamp FROM chat_history WHERE username=? ORDER BY id ASC";
            PreparedStatement ps = conn.prepareStatement(q);
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            while(rs.next()) {
                int id = rs.getInt("id");
                String userMsg = rs.getString("user_message");
                String botMsg = rs.getString("bot_response");
                addChatBlock(userMsg, botMsg, id, false);
            }
        } catch(Exception e) { e.printStackTrace(); }
    }

    // DELETE SELECTED
    private void deleteSelectedChats() {
        for(int i = chatBlocks.size()-1; i>=0; i--) {
            JPanel block = chatBlocks.get(i);
            JCheckBox cb = (JCheckBox)block.getComponent(0);
            if(cb.isSelected()) {
                int id = chatIds.get(i);
                try(Connection conn = DBConnection.getConnection()) {
                    String q = "DELETE FROM chat_history WHERE id=?";
                    PreparedStatement ps = conn.prepareStatement(q);
                    ps.setInt(1,id);
                    ps.executeUpdate();
                } catch(Exception e) { e.printStackTrace(); }
                chatPanel.remove(block);
                chatBlocks.remove(i);
                chatIds.remove(i);
            }
        }
        revalidateAndScroll();
    }

    // GEMINI API CALL
    private String getGeminiReply(String userMessage) throws Exception {
        if (geminiKey == null || geminiKey.isBlank()) throw new IllegalStateException("Gemini key missing");

        HttpClient client = HttpClient.newHttpClient();
        String json = """
        {
          "contents": [
            {
              "parts": [
                { "text": "%s" }
              ]
            }
          ]
        }
        """.formatted(userMessage.replace("\"","\\\"").replace("\n","\\n"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .header("Content-Type","application/json")
                .header("x-goog-api-key", geminiKey)
                .POST(BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        if(response.statusCode()!=200) throw new RuntimeException("HTTP "+response.statusCode()+" - "+response.body());

        JSONObject jsonResp = new JSONObject(response.body());
        return jsonResp.getJSONArray("candidates")
                       .getJSONObject(0)
                       .getJSONObject("content")
                       .getJSONArray("parts")
                       .getJSONObject(0)
                       .getString("text");
    }

    // HTML ESCAPE
    private static String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
 // File name for storing the Gemini API key
    private static final String CONFIG_FILE = "config.txt";

    // Load Gemini API key from config file or ask user once
    private void loadGeminiKey() {
        File file = new File(CONFIG_FILE);
        if(file.exists()) {
            try(BufferedReader br = new BufferedReader(new FileReader(file))) {
                geminiKey = br.readLine().trim();
            } catch(Exception e) {
                geminiKey = "";
            }
        }

        if(geminiKey == null || geminiKey.isBlank()) {
            geminiKey = JOptionPane.showInputDialog(this, "Enter your Gemini API key:");
            if(geminiKey == null) geminiKey = "";
            saveGeminiKey(); // save to file for future runs
        }
    }

    // Save Gemini API key to config file
    private void saveGeminiKey() {
        try(BufferedWriter bw = new BufferedWriter(new FileWriter(CONFIG_FILE))) {
            bw.write(geminiKey);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}
