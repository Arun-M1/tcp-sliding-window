import java.io.*;
import java.net.*;
import java.util.*;

public class TCPServer {

    static final int PORT         = 9999;
    static final int TIME_INTERVAL = 1000;

    public static void main(String[] args) throws Exception {

        ServerSocket server = new ServerSocket(PORT);
        System.out.println("[SERVER] Waiting for connection...");

        Socket socket = server.accept();
        Scanner in = new Scanner(socket.getInputStream());
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        System.out.println("[SERVER] Client connected.");

        // --- Handshake ---
        System.out.println("[SERVER] Received: " + in.nextLine());
        out.println("SUCCESS");

        // --- Receive loop ---
        Set<Long> received = new HashSet<>();
        long totalReceived = 0;
        long totalAttempted = 0;
        long nextExpected = 0;
        long nextReportAt = TIME_INTERVAL;

        double goodputSum   = 0;
        int goodputCount = 0;

        while (in.hasNextLine()) {
            String line = in.nextLine();
            if (line.equals("DONE")) break;

            String[] parts = line.split(":");
            long seq = Long.parseLong(parts[0]);
            totalAttempted = Long.parseLong(parts[1]);

            if (!received.contains(seq)) {
                received.add(seq);
                totalReceived++;
            }

            while (received.contains(nextExpected)) {
                nextExpected++;
            }

            out.println(nextExpected);

            if (totalReceived >= nextReportAt) {
                double gp = (double) totalReceived / totalAttempted;
                goodputSum += gp;
                goodputCount++;
                System.out.printf("[SERVER] Received=%,7d  Attempted=%,7d  Goodput=%.4f%n",
                        totalReceived, totalAttempted, gp);
                nextReportAt += TIME_INTERVAL;
            }
        }

        // --- Final summary ---
        double finalGoodput = (double) totalReceived / totalAttempted;
        double avgGoodput = goodputSum / goodputCount;

        System.out.println("\n===== FINAL SUMMARY =====");
        System.out.printf("Total Attempted by client : %,d%n", totalAttempted);
        System.out.printf("Total Unique Received     : %,d%n", totalReceived);
        System.out.printf("Final Goodput             : %.6f%n", finalGoodput);
        System.out.printf("Average Goodput           : %.6f%n", avgGoodput);
        System.out.println("=========================");

        // Close everything
        in.close();
        out.close();
        socket.close();
        server.close();
    }
}