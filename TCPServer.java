// Arun Murugan
// Ajay Paramasivan

import java.io.*;
import java.net.*;
import java.util.*;

public class TCPServer {

    private static final int PORT = 9000;
    private static final int MAX_BUFFER_SIZE = 5000;
    private static final int TIME_INTERVAL = 1000; // packets interval for goodput
    
    // Server State
    private static long expectedSeq = 0;
    private static long totalReceived = 0;
    private static long totalAttempted = 0;
    private static Set<Long> outOfOrderBuffer = new HashSet<>(); // soft size of MAX_BUFFER_SIZE
    
    //Tracks sequence numbers that have been skipped
    private static Set<Long> missingPackets = new HashSet<>();

    //goodput tracking
    private static double goodputSum = 0;
    private static int goodputCount = 0;

    private static long startTime;
    private static PrintWriter csvLogger;

    public static void main(String[] args) {
        try (ServerSocket server = new ServerSocket(PORT)) {
            //print server ip address
            System.out.println("Server IP: " + InetAddress.getLocalHost().getHostAddress());
            System.out.println("Server started on port " + PORT + ". Waiting for connection...");
            
            try (Socket socket = server.accept();
                 Scanner in = new Scanner(socket.getInputStream());
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                
                System.out.println("Client connected from: " + socket.getInetAddress().getHostAddress());
                initConnection(in, out);
                initLogger();
                
                processClientMessages(in, out);

                double finalGoodput = (double) totalReceived / totalAttempted;
                double avgGoodput = goodputCount > 0 ? goodputSum / goodputCount : finalGoodput;

                System.out.println("\n===== FINAL SUMMARY =====");
                System.out.printf("Total Attempted by client : %,d%n", totalAttempted);
                System.out.printf("Total Unique Received     : %,d%n", totalReceived);
                System.out.printf("Total Missing Packets     : %,d%n", missingPackets.size());
                System.out.printf("Final Goodput             : %.6f%n", finalGoodput);
                System.out.printf("Average Goodput           : %.6f%n", avgGoodput);
                System.out.println("=========================");
                
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (csvLogger != null) {
                csvLogger.close();
            }
        }
    }

    /* start handshake with client
     * @param in  Scanner connected to client output
     * @param out PrintWriter connected to client input
     */

    private static void initConnection(Scanner in, PrintWriter out) {
        String msg = in.nextLine();
        System.out.println("Received: " + msg);
        if ("network".equals(msg)) {
            // Broadcast receive window size to client
            out.println("SUCCESS:" + MAX_BUFFER_SIZE);
            System.out.println("Sent SUCCESS with rwnd=" + MAX_BUFFER_SIZE);
        }
    }

    private static void initLogger() throws IOException {
        startTime = System.currentTimeMillis();
        csvLogger = new PrintWriter(new BufferedWriter(new FileWriter("server_log.csv")));
        csvLogger.println("TimeMs,Type,SeqNum,Rwnd");
    }

    /* read lines from client until done
     * @param in  Scanner connected to client output
     * @param out PrintWriter connected to client input (for sending ACKs)
     */
    private static void processClientMessages(Scanner in, PrintWriter out) {
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if (line.equals("DONE")) {
                break;
            }

            String[] parts = line.split(":");
            long wrappedSeq = Long.parseLong(parts[0]);
            totalAttempted = Long.parseLong(parts[1]);

            handleReceivedPacket(wrappedSeq, out);
        }
    }

    /* Processes a single received packet, buffers if out-of-order, and sends ACK.
     * @param wrappedSeq Wrapped (mod 65536) sequence number from the client
     * @param out        PrintWriter for sending ACK back to client
     */
    private static void handleReceivedPacket(long wrappedSeq, PrintWriter out) {
        long absoluteSeq = getAbsoluteSeq(wrappedSeq, expectedSeq);

        // Process new unique packets
        if (absoluteSeq >= expectedSeq && !outOfOrderBuffer.contains(absoluteSeq)) {
            totalReceived++;
            
            // Flush buffered packets if they are now consecutive
            if (absoluteSeq == expectedSeq) {
                expectedSeq++;
                missingPackets.remove(absoluteSeq); // Clear if it was previously marked missing

                //remove consecutive packets that were out of order
                while (outOfOrderBuffer.contains(expectedSeq)) {
                    outOfOrderBuffer.remove(expectedSeq);
                    missingPackets.remove(expectedSeq); // Gap is now closed
                    expectedSeq++;
                }
            } else {
                outOfOrderBuffer.add(absoluteSeq);

                // Mark every sequence number in the gap as missing
                for (long missing = expectedSeq; missing < absoluteSeq; missing++) {
                    if (!outOfOrderBuffer.contains(missing)) {
                        missingPackets.add(missing);
                    }
                }
            }

            logEvent("RECV", absoluteSeq);
            calculateAndReportGoodput();
        }

        // Calculate Receiver Window 
        int rwnd = Math.max(0, MAX_BUFFER_SIZE - outOfOrderBuffer.size());
        
        // Send ACK and window size back to client format: <wrapped_expected>:<rwnd>
        long wrappedExpected = expectedSeq % 65536;
        out.println(wrappedExpected + ":" + rwnd);
    }

    /* Converts a wrapped TCP sequence number back to its absolute sequence number.
     * @param wrappedSeq The wrapped sequence number received
     * @param baseSeq    The current absolute expected sequence number
     * @return The reconstructed absolute sequence number
     */
    private static long getAbsoluteSeq(long wrappedSeq, long baseSeq) {
        long baseWrapped = baseSeq % 65536;
        long diff = wrappedSeq - baseWrapped;
        if (diff < -32768) {
            diff += 65536;
        } else if (diff > 32768) {
            diff -= 65536;
        }
        return baseSeq + diff;
    }

    //calculates and prints goodput for packets received at every TIME_INTERVAL
    private static void calculateAndReportGoodput() {
        if (totalReceived % TIME_INTERVAL == 0) {
            double gp = (double) totalReceived / totalAttempted;
            goodputSum += gp;
            goodputCount++;
            System.out.printf("Received=%,7d  Attempted=%,7d  Goodput=%.4f%n",
                    totalReceived, totalAttempted, gp);
        }
    }
    
    private static void logEvent(String type, long seqNum) {
        long timeMs = System.currentTimeMillis() - startTime;
        int rwnd = Math.max(0, MAX_BUFFER_SIZE - outOfOrderBuffer.size());
        csvLogger.printf("%d,%s,%d,%d%n", timeMs, type, seqNum, rwnd);
    }
}