//// ChipiChipiClient.java
//
//import java.io.*;
//import java.net.*;
//import java.util.*;
//
//public class ChipiChipiClient {
//    public static void main(String[] args) throws IOException {
//        Socket socket = new Socket("localhost", 12345);
//        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
//        Scanner sc = new Scanner(System.in);
//
//        Thread readerThread = new Thread(() -> {
//            try {
//                String line;
//                while ((line = in.readLine()) != null) {
//                    System.out.println(line);
//                }
//            } catch (IOException e) {
//                System.out.println("[CLIENT] Server disconnected.");
//            }
//        });
//        readerThread.setDaemon(true);
//        readerThread.start();
//
//        while (true) {
//            if (sc.hasNextLine()) {
//                String input = sc.nextLine();
//                out.println(input);
//            }
//        }
//    }
//}
//
