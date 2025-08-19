import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.Timer;  // if you want swing timer only
import java.awt.image.BufferedImage;

/**
 * ChipiChipiGUI_Full.java
 *
 * Standalone Swing app implementing the ChipiChipi project features in GUI:
 * - Register / Login
 * - Friend requests (send/receive), manage them
 * - View Friends
 * - Create posts / View posts
 * - Private messaging with per-friend chat tabs and persisted message files
 *
 * Data persisted to:
 * - users.txt  (username;password;age;gender;country;friendsCSV;requestsCSV)
 * - posts.txt  (username;timestamp;post)
 * - messages saved to files: username_friend_msg.txt (append)
 *
 * Style: rounded message bubbles, modern colors, spacing.
 */
public class ChipiChipiGUI_Full extends JFrame {
    // persistence files
    private static final String USER_FILE = "users.txt";
    private static final String POST_FILE = "posts.txt";
    private static final SimpleDateFormat TS_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    // In-memory models
    private Map<String, User> users = new HashMap<>();
    private User currentUser = null;

    // UI components
    private CardLayout cardLayout = new CardLayout();
    private JPanel cards = new JPanel(cardLayout);

    // Login/Register fields
    private JTextField loginUserField;
    private JPasswordField loginPassField;
    private JTextField regUserField, regPassField, regAgeField, regGenderField, regCountryField;

    // Main UI components
    private DefaultListModel<String> friendsListModel = new DefaultListModel<>();
    private JList<String> friendsList;
    private JPanel chatPanel; // where bubbles go
    private JScrollPane chatScroll;
    private JTextField messageInput;
    private JLabel userTitleLabel;
    private String activeChat = "#Global"; // default
    private Map<String, java.util.List<Message>> chatMessages = new HashMap<>(); // key: friend or #Global

    // Posts view quick cache
    private java.util.List<String> postsCache = new ArrayList<>();

    // Colors & style
    private static final Color BG = new Color(0xF5F7FA);
    private static final Color SIDEBAR = new Color(0x2F3136);
    private static final Color ACCENT = new Color(0x5865F2);
    private static final Color BUBBLE_ME = new Color(0x4C9AFF);
    private static final Color BUBBLE_OTHER = new Color(0xE4E6EB);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChipiChipiGUI_Full app = new ChipiChipiGUI_Full();
            app.setVisible(true);
        });
    }

    public ChipiChipiGUI_Full() {
        setTitle("ChipiChipi — GUI");
        setSize(1100, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        loadUsers();
        loadPosts();

        cards.add(buildAuthPanel(), "AUTH");
        cards.add(buildMainPanel(), "MAIN");

        add(cards);
        cardLayout.show(cards, "AUTH");

        // Precreate global chat
        chatMessages.put("#Global", new ArrayList<>());
    }

    // ------------------ Data models ------------------
    private static class User {
        String username, password, gender, country;
        int age;
        Set<String> friends = new LinkedHashSet<>();
        Set<String> friendRequests = new LinkedHashSet<>();

        User(String u, String p, int age, String g, String c) {
            username = u; password = p; this.age = age; gender = g; country = c;
        }

        String serialize() {
            String f = String.join(",", friends);
            String r = String.join(",", friendRequests);
            return username + ";" + password + ";" + age + ";" + gender + ";" + country + ";" + f + ";" + r;
        }

        static User fromLine(String line) {
            String[] parts = line.split(";", -1);
            if (parts.length < 5) return null;
            String u = parts[0], p = parts[1];
            int a = 18;
            try { a = Integer.parseInt(parts[2]); } catch (Exception ignored) {}
            String g = parts.length>3 ? parts[3] : "N/A";
            String c = parts.length>4 ? parts[4] : "N/A";
            User user = new User(u,p,a,g,c);
            if (parts.length>5 && !parts[5].isEmpty()) {
                user.friends.addAll(Arrays.asList(parts[5].split(",")));
            }
            if (parts.length>6 && !parts[6].isEmpty()) {
                user.friendRequests.addAll(Arrays.asList(parts[6].split(",")));
            }
            return user;
        }
    }

    private static class Message {
        String sender;
        String text;
        String ts;
        Message(String s, String t){ sender = s; text = t; ts = TS_FORMAT.format(new Date()); }
    }

    // ------------------ Persistence ------------------
    private void loadUsers() {
        users.clear();
        File f = new File(USER_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                User u = User.fromLine(line);
                if (u != null) users.put(u.username, u);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void saveUsers() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(USER_FILE, false))) {
            for (User u : users.values()) pw.println(u.serialize());
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadPosts() {
        postsCache.clear();
        File f = new File(POST_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) postsCache.add(line);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private synchronized void savePost(String username, String post) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(POST_FILE, true))) {
            String timestamp = TS_FORMAT.format(new Date());
            pw.println(username + ";" + timestamp + ";" + post);
            postsCache.add("[" + username + "] -> " + post + " (" + timestamp + ")");
        } catch (IOException e) { e.printStackTrace(); }
    }

    private java.util.List<String> loadMessagesFromFile(String me, String friend) {
        String fn1 = me + "_" + friend + "_msg.txt";
        String fn2 = friend + "_" + me + "_msg.txt";
        java.util.List<String> all = new ArrayList<>();
        for (String fn : Arrays.asList(fn1, fn2)) {
            File f = new File(fn);
            if (f.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = br.readLine()) != null) all.add(line);
                } catch (IOException ignored) {}
            }
        }
        return all;
    }

    private synchronized void appendMessageToFile(String me, String friend, String msgLine) {
        String fn = me + "_" + friend + "_msg.txt";
        try (PrintWriter pw = new PrintWriter(new FileWriter(fn, true))) {
            pw.println(msgLine);
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ------------------ UI Construction ------------------
    private JPanel buildAuthPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(24, 24, 24, 24));

        JLabel title = new JLabel("ChipiChipi", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 32));
        p.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 20, 0));
        center.setOpaque(false);


        // Login side
        JPanel loginPanel = new JPanel(new BorderLayout(8, 8));
        loginPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xDDDDDD)),
                new EmptyBorder(16, 16, 16, 16)));
        loginPanel.setBackground(Color.WHITE);

// Use BoxLayout vertical with rigid areas to control spacing & size
        JPanel loginFields = new JPanel();
        loginFields.setOpaque(false);
        loginFields.setLayout(new BoxLayout(loginFields, BoxLayout.Y_AXIS));

        loginFields.add(new JLabel("Username:"));
        loginUserField = new JTextField();
        loginUserField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));  // fix height ~28px
        loginFields.add(loginUserField);
        loginFields.add(Box.createRigidArea(new Dimension(0, 8)));

        loginFields.add(new JLabel("Password:"));
        loginPassField = new JPasswordField();
        loginPassField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28)); // fix height ~28px
        loginFields.add(loginPassField);

        loginPanel.add(new JLabel("Login", SwingConstants.CENTER), BorderLayout.NORTH);
        loginPanel.add(loginFields, BorderLayout.CENTER);

        JButton btnLogin = new JButton("Login");
        btnLogin.setBackground(ACCENT);
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setFocusPainted(false);
        btnLogin.addActionListener(e -> attemptLogin());

        loginPanel.add(btnLogin, BorderLayout.SOUTH);

        // Register side
        JPanel regPanel = new JPanel(new BorderLayout(8, 8));
        regPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xDDDDDD)),
                new EmptyBorder(16, 16, 16, 16)));
        regPanel.setBackground(Color.WHITE);

        JPanel regFields = new JPanel(new GridLayout(10, 1, 6, 6));
        regFields.setOpaque(false);
        regFields.add(new JLabel("Username:"));
        regUserField = new JTextField();
        regFields.add(regUserField);
        regFields.add(new JLabel("Password:"));
        regPassField = new JTextField();
        regFields.add(regPassField);
        regFields.add(new JLabel("Age:"));
        regAgeField = new JTextField();
        regFields.add(regAgeField);
        regFields.add(new JLabel("Gender:"));
        regGenderField = new JTextField();
        regFields.add(regGenderField);
        regFields.add(new JLabel("Country:"));
        regCountryField = new JTextField();
        regFields.add(regCountryField);

        JButton btnReg = new JButton("Register");
        btnReg.setBackground(new Color(0x22AA66));
        btnReg.setForeground(Color.WHITE);
        btnReg.setFocusPainted(false);
        btnReg.addActionListener(e -> attemptRegister());

        regPanel.add(new JLabel("Register", SwingConstants.CENTER), BorderLayout.NORTH);
        regPanel.add(regFields, BorderLayout.CENTER);
        regPanel.add(btnReg, BorderLayout.SOUTH);

        center.add(loginPanel);
        center.add(regPanel);
        p.add(center, BorderLayout.CENTER);

        JTextArea sysPreview = new JTextArea();
        sysPreview.setEditable(false);
        sysPreview.setText("Local demo mode. Users are persisted in users.txt. Posts saved in posts.txt.");
        sysPreview.setBackground(BG);
        sysPreview.setBorder(new EmptyBorder(8, 8, 8, 8));
        p.add(sysPreview, BorderLayout.SOUTH);

        return p;
    }



    private JPanel buildMainPanel() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG);

        // Left sidebar
        JPanel left = new JPanel(new BorderLayout(8,8));
        left.setBackground(SIDEBAR);
        left.setPreferredSize(new Dimension(260, getHeight()));
        left.setBorder(new EmptyBorder(12,12,12,12));

        userTitleLabel = new JLabel("User: Guest");
        userTitleLabel.setForeground(Color.WHITE);
        userTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        left.add(userTitleLabel, BorderLayout.NORTH);

        friendsListModel = new DefaultListModel<>();
        friendsList = new JList<>(friendsListModel);
        friendsList.setCellRenderer(new FriendCellRenderer());
        friendsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        friendsList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    String sel = friendsList.getSelectedValue();
                    if (sel != null) {
                        activeChat = sel;
                        refreshChatPanel();
                    }
                }
                if (e.getClickCount() == 2) {
                    String sel = friendsList.getSelectedValue();
                    if (sel != null && !sel.equals(currentUser)) {
                        // open chat same as single-click in this design
                        activeChat = sel;
                        refreshChatPanel();
                    }
                }
            }
        });

        JScrollPane friendScroll = new JScrollPane(friendsList);
        friendScroll.setBorder(null);
        left.add(friendScroll, BorderLayout.CENTER);

        JPanel leftButtons = new JPanel(new GridLayout(6,1,8,8));
        leftButtons.setOpaque(false);
        JButton addFriendBtn = new JButton("Add Friend");
        addFriendBtn.addActionListener(e -> showAddFriendDialog());
        JButton manageBtn = new JButton("Manage Requests");
        manageBtn.addActionListener(e -> showManageRequestsDialog());
        JButton viewFriendsBtn = new JButton("Refresh Friends");
        viewFriendsBtn.addActionListener(e -> loadFriendsToList());
        JButton createPostBtn = new JButton("Create Post");
        createPostBtn.addActionListener(e -> showCreatePostDialog());
        JButton viewPostsBtn = new JButton("View Posts");
        viewPostsBtn.addActionListener(e -> showPostsDialog());
        JButton logoutBtn = new JButton("Logout");
        logoutBtn.addActionListener(e -> doLogout());
        for (JButton b : Arrays.asList(addFriendBtn, manageBtn, viewFriendsBtn, createPostBtn, viewPostsBtn, logoutBtn)) {
            b.setBackground(Color.WHITE);
            leftButtons.add(b);
        }
        left.add(leftButtons, BorderLayout.SOUTH);

        main.add(left, BorderLayout.WEST);

        // Center: chat area
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(new EmptyBorder(12,12,12,12));
        center.setBackground(BG);

        // Top bar (active chat)
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG);
        JLabel chatLabel = new JLabel("Chat: " + activeChat);
        chatLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        topBar.add(chatLabel, BorderLayout.WEST);
        center.add(topBar, BorderLayout.NORTH);

        // Chat panel (bubbles)
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setOpaque(false);
        chatScroll = new JScrollPane(chatPanel);
        chatScroll.setBorder(null);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        center.add(chatScroll, BorderLayout.CENTER);

        // Input area
        JPanel inputBar = new JPanel(new BorderLayout(8,8));
        inputBar.setBorder(new EmptyBorder(10,0,0,0));
        inputBar.setBackground(BG);
        messageInput = new JTextField();
        JButton sendBtn = new JButton("Send");
        sendBtn.setBackground(ACCENT); sendBtn.setForeground(Color.WHITE);

        sendBtn.addActionListener(e -> doSendMessage());
        messageInput.addActionListener(e -> doSendMessage());

        inputBar.add(messageInput, BorderLayout.CENTER);
        inputBar.add(sendBtn, BorderLayout.EAST);

        center.add(inputBar, BorderLayout.SOUTH);

        main.add(center, BorderLayout.CENTER);

        // Right: small profile + notifications
        JPanel right = new JPanel(new BorderLayout(8,8));
        right.setBorder(new EmptyBorder(12,12,12,12));
        right.setPreferredSize(new Dimension(300, getHeight()));
        right.setBackground(Color.WHITE);

        JLabel aboutLbl = new JLabel("Profile");
        aboutLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
        right.add(aboutLbl, BorderLayout.NORTH);

        JTextArea profileArea = new JTextArea();
        profileArea.setEditable(false);
        profileArea.setText("No user logged in");
        profileArea.setBackground(Color.WHITE);
        right.add(profileArea, BorderLayout.CENTER);

        // Keep profileArea updated when login
        // Also a small notifications list
        main.add(right, BorderLayout.EAST);

        // toggle update chat label whenever activeChat changes
        Timer t = new Timer(250, e -> {
            chatLabel.setText("Chat: " + activeChat);
            if (currentUser != null) profileArea.setText(userProfileText(currentUser.toString()));
        });
        t.start();

        return main;
    }

    // ------------------ UI Actions ------------------
    private void attemptRegister() {
        String u = regUserField.getText().trim();
        String p = regPassField.getText().trim();
        String ageS = regAgeField.getText().trim();
        String g = regGenderField.getText().trim();
        String c = regCountryField.getText().trim();
        if (u.isEmpty() || p.isEmpty() || ageS.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill username, password, age.");
            return;
        }
        if (users.containsKey(u)) {
            JOptionPane.showMessageDialog(this, "Username already exists.");
            return;
        }
        int age = 18;
        try { age = Integer.parseInt(ageS); } catch (Exception ignored) {}
        User nu = new User(u, p, age, g.isEmpty()?"N/A":g, c.isEmpty()?"N/A":c);
        users.put(u, nu);
        saveUsers();
        JOptionPane.showMessageDialog(this, "Registration successful. You can login now.");
        clearRegisterFields();
    }

    private void attemptLogin() {
        String u = loginUserField.getText().trim();
        String p = new String(loginPassField.getPassword()).trim();
        if (u.isEmpty() || p.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username & password.");
            return;
        }
        User found = users.get(u);
        if (found == null || !found.password.equals(p)) {
            JOptionPane.showMessageDialog(this, "Invalid username or password.");
            return;
        }
        currentUser = found;
        // build friend list
        loadFriendsToList();
        userTitleLabel.setText("User: " + currentUser.username);
        cardLayout.show(cards, "MAIN");
        setTitle("ChipiChipi — " + currentUser.username);
        // load messages for each friend (lazy load when opening chat) but ensure global posts loaded
        loadPosts();
    }

    private void clearRegisterFields() {
        regUserField.setText("");
        regPassField.setText("");
        regAgeField.setText("");
        regGenderField.setText("");
        regCountryField.setText("");
    }

    private void loadFriendsToList() {
        friendsListModel.clear();
        // first entry: Global timeline
        friendsListModel.addElement("#Global");
        if (currentUser == null) return;
        // user's friends
        for (String f : currentUser.friends) friendsListModel.addElement(f);
        // show friend requests as special item (if any)
        if (!currentUser.friendRequests.isEmpty()) {
            friendsListModel.addElement("⟡ Friend Requests (" + currentUser.friendRequests.size() + ")");
        }
    }

    private void showAddFriendDialog() {
        if (currentUser == null) { JOptionPane.showMessageDialog(this, "Login first"); return; }
        String target = JOptionPane.showInputDialog(this, "Enter username to send friend request to:");
        if (target == null || target.trim().isEmpty()) return;
        target = target.trim();
        if (!users.containsKey(target)) {
            JOptionPane.showMessageDialog(this, "User not found.");
            return;
        }
        if (currentUser.friends.contains(target)) {
            JOptionPane.showMessageDialog(this, "Already friends.");
            return;
        }
        User targetUser = users.get(target);
        if (targetUser.friendRequests.contains(currentUser.username)) {
            JOptionPane.showMessageDialog(this, "Friend request already sent.");
            return;
        }
        targetUser.friendRequests.add(currentUser.username);
        saveUsers();
        JOptionPane.showMessageDialog(this, "Friend request sent to " + target);
    }

    private void showManageRequestsDialog() {
        if (currentUser == null) { JOptionPane.showMessageDialog(this, "Login first"); return; }
        if (currentUser.friendRequests.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No friend requests.");
            return;
        }
        // create a dialog with list and accept/reject buttons
        JDialog d = new JDialog(this, "Manage Friend Requests", true);
        d.setSize(400, 300);
        d.setLocationRelativeTo(this);

        DefaultListModel<String> reqModel = new DefaultListModel<>();
        for (String r : currentUser.friendRequests) reqModel.addElement(r);
        JList<String> reqList = new JList<>(reqModel);
        reqList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton accept = new JButton("Accept");
        JButton reject = new JButton("Reject");
        accept.addActionListener(e -> {
            String sel = reqList.getSelectedValue();
            if (sel == null) return;
            currentUser.friends.add(sel);
            users.get(sel).friends.add(currentUser.username);
            currentUser.friendRequests.remove(sel);
            saveUsers();
            reqModel.removeElement(sel);
            loadFriendsToList();
        });
        reject.addActionListener(e -> {
            String sel = reqList.getSelectedValue();
            if (sel == null) return;
            currentUser.friendRequests.remove(sel);
            saveUsers();
            reqModel.removeElement(sel);
            loadFriendsToList();
        });

        JPanel btns = new JPanel(new GridLayout(1,2,6,6));
        btns.add(accept); btns.add(reject);

        d.getContentPane().add(new JScrollPane(reqList), BorderLayout.CENTER);
        d.getContentPane().add(btns, BorderLayout.SOUTH);
        d.setVisible(true);
    }

    private void showCreatePostDialog() {
        if (currentUser == null) { JOptionPane.showMessageDialog(this, "Login first"); return; }
        JTextArea area = new JTextArea(6,30);
        int res = JOptionPane.showConfirmDialog(this, new JScrollPane(area), "Create Post", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String post = area.getText().trim();
            if (post.isEmpty()) return;
            savePost(currentUser.username, post);
            JOptionPane.showMessageDialog(this, "Posted.");
        }
    }

    private void showPostsDialog() {
        loadPosts();
        if (postsCache.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No posts available.");
            return;
        }
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        for (String p : postsCache) ta.append(p + "\n");
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(700, 400));
        JOptionPane.showMessageDialog(this, sp, "All Posts", JOptionPane.PLAIN_MESSAGE);
    }

    private void doLogout() {
        currentUser = null;
        chatMessages.clear();
        activeChat = "#Global";
        setTitle("ChipiChipi — GUI");
        cardLayout.show(cards, "AUTH");
    }

    private void doSendMessage() {
        if (currentUser == null) { JOptionPane.showMessageDialog(this, "Login first"); return; }
        String text = messageInput.getText().trim();
        if (text.isEmpty()) return;
        String ts = TS_FORMAT.format(new Date());

        if (activeChat.equals("#Global")) {
            // global post-like message (we'll store as a post-less global chat)
            Message m = new Message(currentUser.username, text);
            chatMessages.get("#Global").add(m);
            appendBubble(currentUser.username, text, true);
            // also optionally save to posts as "post" or skip; to keep parity we'll not save as post.
        } else {
            // private message to friend
            String friend = activeChat;
            if (!users.containsKey(friend)) {
                JOptionPane.showMessageDialog(this, "Friend not found.");
                return;
            }
            // add to in-memory chat
            chatMessages.computeIfAbsent(friend, k -> new ArrayList<>());
            Message me = new Message(currentUser.username, text);
            chatMessages.get(friend).add(me);
            appendBubble(currentUser.username, text, true);
            // persist to file as "sender: text (timestamp)"
            String line = currentUser.username + ": " + text + " (" + ts + ")";
            appendMessageToFile(currentUser.username, friend, line);
            // also append to friend's file view so they can read when they login (mock)
        }
        messageInput.setText("");
        SwingUtilities.invokeLater(() -> chatScroll.getVerticalScrollBar().setValue(chatScroll.getVerticalScrollBar().getMaximum()));
    }

    // Refresh chatPanel content according to activeChat
    private void refreshChatPanel() {
        chatPanel.removeAll();
        chatPanel.revalidate();
        chatPanel.repaint();

        if (activeChat.equals("#Global")) {
            // show global chat messages
            java.util.List<Message> msgs = chatMessages.getOrDefault("#Global", new ArrayList<>());
            for (Message m : msgs) appendBubble(m.sender, m.text, m.sender.equals(currentUser.username));
        } else if (activeChat.startsWith("⟡")) {
            // friend requests placeholder; ignore
            JLabel lbl = new JLabel("Click 'Manage Requests' to respond to requests.");
            chatPanel.add(Box.createVerticalStrut(12));
            chatPanel.add(lbl);
        } else {
            // private chat: load from files and memory
            java.util.List<Message> msgs = chatMessages.computeIfAbsent(activeChat, k -> new ArrayList<>());
            // load persisted messages
            java.util.List<String> persisted = loadMessagesFromFile(currentUser.username, activeChat);
            for (String line : persisted) {
                // format: Sender: message (timestamp)
                int idx = line.indexOf(":");
                if (idx > 0) {
                    String s = line.substring(0, idx).trim();
                    String rest = line.substring(idx + 1).trim();
                    boolean mine = s.equals(currentUser.username);
                    appendBubble(s, rest, mine);
                } else {
                    appendBubble("?", line, false);
                }
            }
            // then append in-memory messages (new ones)
            for (Message m : msgs) appendBubble(m.sender, m.text, m.sender.equals(currentUser.username));
        }

        chatPanel.add(Box.createVerticalStrut(12));
        chatPanel.revalidate();
        SwingUtilities.invokeLater(() -> chatScroll.getVerticalScrollBar().setValue(chatScroll.getVerticalScrollBar().getMaximum()));
    }

    // append bubble to panel (thread-safe)
    private void appendBubble(String sender, String text, boolean isMe) {
        JPanel bubbleWrap = new JPanel(new BorderLayout());
        bubbleWrap.setOpaque(false);
        JPanel bubble = new BubblePanel(text, isMe ? BUBBLE_ME : BUBBLE_OTHER, isMe ? Color.WHITE : Color.BLACK);

        // show sender name above message for others
        if (!isMe) {
            JLabel name = new JLabel(sender);
            name.setFont(new Font("Segoe UI", Font.BOLD, 12));
            bubbleWrap.add(name, BorderLayout.NORTH);
        }

        if (isMe) {
            bubbleWrap.add(bubble, BorderLayout.EAST);
        } else {
            bubbleWrap.add(bubble, BorderLayout.WEST);
        }
        bubbleWrap.setBorder(new EmptyBorder(6,6,6,6));
        chatPanel.add(bubbleWrap);
        chatPanel.add(Box.createVerticalStrut(6));
        chatPanel.revalidate();
        chatPanel.repaint();
    }

    private String userProfileText(String username) {
        if (username == null) return "No user.";
        User u = users.get(username);
        if (u == null) return "No user.";
        StringBuilder sb = new StringBuilder();
        sb.append("Username: ").append(u.username).append("\n");
        sb.append("Age: ").append(u.age).append("\n");
        sb.append("Gender: ").append(u.gender).append("\n");
        sb.append("Country: ").append(u.country).append("\n");
        sb.append("Friends: ").append(u.friends.size()).append("\n");
        sb.append("Requests: ").append(u.friendRequests.size()).append("\n");
        return sb.toString();
    }

    // ------------------ UI small components ------------------
    private static class FriendCellRenderer extends JLabel implements ListCellRenderer<String> {
        public FriendCellRenderer() { setOpaque(true); setBorder(new EmptyBorder(6,8,6,8)); }
        @Override public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            setText(value);
            if (value.startsWith("⟡")) {
                setIcon(null);
            } else if (value.equals("#Global")) {
                setIcon(null);
            } else {
                // small dot avatar
                setIcon(createColorDot(14, new Color(0xFFB74D)));
            }
            if (isSelected) {
                setBackground(new Color(0x525252));
                setForeground(Color.WHITE);
            } else {
                setBackground(SIDEBAR);
                setForeground(Color.WHITE);
            }
            return this;
        }

        private static Icon createColorDot(int size, Color c) {
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(c);
            g.fillOval(0,0,size-1,size-1);
            g.dispose();
            return new ImageIcon(img);
        }
    }

    // Rounded bubble panel
    private static class BubblePanel extends JPanel {
        private String text;
        private Color bg;
        private Color fg;
        private static final int ARC = 18;
        BubblePanel(String text, Color bg, Color fg) {
            this.text = text;
            this.bg = bg;
            this.fg = fg;
            setOpaque(false);
            setLayout(new BorderLayout());
            JTextArea ta = new JTextArea(text);
            ta.setEditable(false);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            ta.setOpaque(false);
            ta.setForeground(fg);
            ta.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            ta.setBorder(new EmptyBorder(10,10,10,10));
            add(ta, BorderLayout.CENTER);
            setMaximumSize(new Dimension(620, Integer.MAX_VALUE));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            int w = getWidth();
            int h = getHeight();
            g2.fillRoundRect(0,0,w,h, ARC, ARC);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ------------------ utilities ------------------
    private void loadUsersToMemoryIfNeeded() {
        if (users.isEmpty()) loadUsers();
    }
}
