// ChipiChipiServer.java

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChipiChipiServer {
    private static final int PORT = 12345;
    private static Map<String, User> users = new ConcurrentHashMap<>();
    private static Map<String, PrintWriter> loggedInUsers = new ConcurrentHashMap<>();
    private static final String USER_FILE = "users.txt";
    private static final String POST_FILE = "posts.txt";

    public static void main(String[] args) throws IOException {
        loadUsers();

        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("ChipiChipi Server started on port " + PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    private static void loadUsers() {
        File file = new File("users.txt");
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length >= 5) {
                    String username = parts[0];
                    String password = parts[1];
                    int age = Integer.parseInt(parts[2]);
                    String gender = parts[3];
                    String country = parts[4];

                    User user = new User(username, password, age, gender, country);

                    if (parts.length > 5 && !parts[5].isEmpty()) {
                        user.friends.addAll(Arrays.asList(parts[5].split(",")));
                    }
                    if (parts.length > 6 && !parts[6].isEmpty()) {
                        user.friendRequests.addAll(Arrays.asList(parts[6].split(",")));
                    }

                    users.put(username, user);
                }
            }
        } catch (IOException e) {
            System.out.println("Error loading users: " + e.getMessage());
        }
    }


    private static synchronized void saveUsers() {
        try (PrintWriter pw = new PrintWriter(new FileWriter("users.txt", false))) {
            for (User user : users.values()) {
                String friendStr = String.join(",", user.friends);
                String requestStr = String.join(",", user.friendRequests);
                pw.println(user.username + ";" + user.password + ";" + user.age + ";" + user.gender + ";" + user.country + ";" + friendStr + ";" + requestStr);
            }
        } catch (IOException e) {
            System.out.println("Error saving users: " + e.getMessage());
        }
    }


    private static synchronized void savePost(String username, String post) {
        try (PrintWriter pw = new PrintWriter(new FileWriter("posts.txt", true))) {
            String timestamp = new Date().toString();
            pw.println(username + ";" + timestamp + ";" + post);
        } catch (IOException e) {
            System.out.println("Error saving post: " + e.getMessage());
        }
    }


    private static List<String> getPostsByUser(String username) {
        List<String> posts = new ArrayList<>();
        File file = new File(POST_FILE);
        if (!file.exists()) return posts;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(username + ":")) {
                    posts.add(line.substring(username.length() + 1));
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading posts: " + e.getMessage());
        }
        return posts;
    }

    private static class User {
        String username, password, gender, country;
        int age;
        Set<String> friends = new HashSet<>();
        Set<String> friendRequests = new HashSet<>();

        User(String username, String password, int age, String gender, String country) {
            this.username = username;
            this.password = password;
            this.age = age;
            this.gender = gender;
            this.country = country;
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private User currentUser = null;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("Welcome to ChipiChipi!");
                mainMenu();

            } catch (IOException e) {
                System.out.println("Client error: " + e.getMessage());
            } finally {
                if (currentUser != null) {
                    loggedInUsers.remove(currentUser.username);
                }
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        private void mainMenu() throws IOException {
            while (true) {
                out.println("1. Register\n2. Login\n3. Exit\nChoose option (1-3):");
                String choice = in.readLine();
                if (choice == null) break;
                switch (choice) {
                    case "1" -> register();
                    case "2" -> login();
                    case "3" -> { out.println("Goodbye!"); return; }
                    default -> out.println("Invalid option.");
                }
            }
        }

        private void register() throws IOException {
            out.println("Enter username:");
            String username = in.readLine();
            if (users.containsKey(username)) {
                out.println("Username already exists.");
                return;
            }
            out.println("Enter password:");
            String password = in.readLine();
            out.println("Enter age:");
            int age = Integer.parseInt(in.readLine());
            out.println("Enter gender:");
            String gender = in.readLine();
            out.println("Enter country:");
            String country = in.readLine();

            User user = new User(username, password, age, gender, country);
            users.put(username, user);
            saveUsers();
            out.println("Registration successful!");
        }

        private void login() throws IOException {
            out.println("Enter username:");
            String username = in.readLine();
            out.println("Enter password:");
            String password = in.readLine();

            User user = users.get(username);
            if (user == null || !user.password.equals(password)) {
                out.println("Invalid username or password.");
                return;
            }

            currentUser = user;
            loggedInUsers.put(username, out);
            out.println("Login successful. Welcome, " + username + "!");
            userMenu();
        }

        private void userMenu() throws IOException {
            while (true) {
                out.println("\n1. Send Friend Request\n2. Manage Requests\n3. View Friends\n4. Post\n5. View Posts\n6. Send Message\n7. View Messages\n8. Logout\nChoose option:");
                String opt = in.readLine();
                if (opt == null) break;
                switch (opt) {
                    case "1" -> sendFriendRequest();
                    case "2" -> manageFriendRequests();
                    case "3" -> showFriends();
                    case "4" -> post();
                    case "5" -> viewPosts();
                    case "6" -> sendMessage();
                    case "7" -> viewMessages();
                    case "8" -> { loggedInUsers.remove(currentUser.username); currentUser = null; return; }
                    default -> out.println("Invalid.");
                }
            }
        }

        private void sendFriendRequest() throws IOException {
            out.println("Username to request:");
            String target = in.readLine();
            if (!users.containsKey(target)) {
                out.println("User not found."); return;
            }
            if (currentUser.friends.contains(target)) {
                out.println("Already friends."); return;
            }
            User friend = users.get(target);
            if (friend.friendRequests.contains(currentUser.username)) {
                out.println("Request already sent."); return;
            }
            friend.friendRequests.add(currentUser.username);
            saveUsers();
            out.println("Request sent.");
        }

        private void manageFriendRequests() throws IOException {
            Iterator<String> iter = currentUser.friendRequests.iterator();
            while (iter.hasNext()) {
                String requester = iter.next();
                out.println("Request from: " + requester + " (A)ccept / (R)eject?");
                String res = in.readLine();
                if (res.equalsIgnoreCase("A")) {
                    currentUser.friends.add(requester);
                    users.get(requester).friends.add(currentUser.username);
                    iter.remove();
                    out.println("Accepted.");
                } else if (res.equalsIgnoreCase("R")) {
                    iter.remove();
                    out.println("Rejected.");
                }
            }
            saveUsers();
        }

        private void showFriends() {
            if (currentUser.friends.isEmpty()) {
                out.println("No friends.");
                return;
            }
            out.println("Your friends:");
            currentUser.friends.forEach(f -> out.println("- " + f));
        }

        private void post() throws IOException {
            out.println("Enter post:");
            String post = in.readLine();
            savePost(currentUser.username, post);
            out.println("Posted.");
        }

        private void viewPosts() {
            File file = new File("posts.txt");
            if (!file.exists()) {
                out.println("No posts found.");
                return;
            }

            List<String> posts = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(";", 3);
                    if (parts.length == 3) {
                        String username = parts[0];
                        String timestamp = parts[1];
                        String content = parts[2];
                        posts.add("[" + username + "] "+"-> " + content + " (" + timestamp + ")");
                    }
                }
            } catch (IOException e) {
                out.println("Error reading posts.");
                return;
            }

            if (posts.isEmpty()) {
                out.println("No posts available.");
                return;
            }

            out.println("All Posts:");
            for (String post : posts) {
                out.println(post);
            }
        }


        private void sendMessage() throws IOException {
            out.println("Send message to:");
            String target = in.readLine();
            if (!currentUser.friends.contains(target)) {
                out.println("Not your friend."); return;
            }
            out.println("Enter message:");
            String msg = in.readLine();
            String file = currentUser.username + "_" + target + "_msg.txt";
            try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {
                pw.println(currentUser.username + ": " + msg);
            }
            out.println("Sent.");
        }

        private void viewMessages() throws IOException {
            out.println("With whom:");
            String target = in.readLine();
            String f1 = currentUser.username + "_" + target + "_msg.txt";
            String f2 = target + "_" + currentUser.username + "_msg.txt";
            List<String> msgs = new ArrayList<>();
            for (String file : List.of(f1, f2)) {
                File f = new File(file);
                if (f.exists()) {
                    try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                        String line;
                        while ((line = br.readLine()) != null) msgs.add(line);
                    }
                }
            }
            if (msgs.isEmpty()) {
                out.println("No messages."); return;
            }
            msgs.forEach(out::println);
        }
    }
}