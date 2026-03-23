import java.io.*;
import java.net.*;
import java.util.*;

public class TCPClient {

    private static final String SERVER_IP = "127.0.0.1";
    private static final int PORT = 9000;
    private static final double DROP_PROB = 0.01;
    private static final int TOTAL_PACKETS = 10_000;
    private static final int RETRANSMIT_STEPS = 100;

    // Client State
    private static long sendBase = 0;
    private static long nextSeqNum = 0;
    private static long totalAttempted = 0;
    private static int stepsSinceRetransmit = 0;
    
    // Window Variables
    private static int cwnd = 16;         // congestion window 
    private static int rwnd = 65535;      // Receiver Window (properly initialized by server)
    
    private static List<Long> droppedPackets = new ArrayList<>();
    private static Random rand = new Random();
    
    private static long startTime;
    private static PrintWriter csvLogger;

    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : SERVER_IP;
        
        try (Socket socket = new Socket(host, PORT)) {
            // Use timeout to prevent deadlock
            socket.setSoTimeout(100); 
            
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("Connected to server.");
            initConnection(in, out);
            initLogger();

            runSlidingWindow(in, out);

            out.println("DONE");
            System.out.printf("Done. Total Attempted=%,d%n", totalAttempted);

        } catch (Exception e) {
            System.err.println("Client exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (csvLogger != null) {
                csvLogger.close();
            }
        }
    }

    private static void initConnection(BufferedReader in, PrintWriter out) throws IOException {
        out.println("network");
        String response = in.readLine();
        System.out.println("Message from server: " + response);
        
        // Parse the initial rwnd from the server's SUCCESS message
        if (response != null && response.startsWith("SUCCESS:")) {
            String[] parts = response.split(":");
            if (parts.length > 1) {
                rwnd = Integer.parseInt(parts[1]);
                System.out.println("Initial Server rwnd set to: " + rwnd);
            }
        }
    }

    private static void initLogger() throws IOException {
        startTime = System.currentTimeMillis();
        csvLogger = new PrintWriter(new BufferedWriter(new FileWriter("client_log.csv")));
        csvLogger.println("TimeMs,Type,SeqNum,Cwnd,Rwnd");
    }

    // Core loop representing the TCP Sliding Window logic.
    private static void runSlidingWindow(BufferedReader in, PrintWriter out) throws IOException {
        long lastPrinted = 0;

        while (sendBase < TOTAL_PACKETS) {
            int effectiveWindow = Math.min(cwnd, rwnd);

            // 1. Send packets up to the window size limit
            while (nextSeqNum < TOTAL_PACKETS && (nextSeqNum - sendBase) < effectiveWindow) {
                totalAttempted++;
                stepsSinceRetransmit++;

                if (rand.nextDouble() < DROP_PROB) {
                    droppedPackets.add(nextSeqNum);
                    logEvent("DROP", nextSeqNum);
                } else {
                    sendPacket(out, nextSeqNum, totalAttempted);
                    logEvent("SEND", nextSeqNum);
                }
                nextSeqNum++;
            }

            // 2. Read incoming ACKs from the server
            boolean forceRetransmit = false;
            try {
                // If window is full, we must block until an ACK arrives. 
                if (nextSeqNum - sendBase >= effectiveWindow || nextSeqNum == TOTAL_PACKETS) {
                    processAck(in.readLine()); 
                }
                while (in.ready()) {
                    processAck(in.readLine());
                }
            } catch (SocketTimeoutException e) {
                logEvent("TIMEOUT", nextSeqNum);
                forceRetransmit = true;
            }

            // 3. Trigger Retransmission based on timesteps
            if (stepsSinceRetransmit >= RETRANSMIT_STEPS && !droppedPackets.isEmpty()) {
                logEvent("SS_RETRANS", stepsSinceRetransmit);
                forceRetransmit = true;
            }

            if (forceRetransmit && !droppedPackets.isEmpty()) {
                retransmitDropped(out);
            }



            if (sendBase % 10000 == 0 && sendBase > 0 && sendBase != lastPrinted) {
                System.out.printf("Progress: %,d / %,d  pending_drops=%d  sendBase=%d  cwnd=%d  rwnd=%d%n",
                    nextSeqNum, TOTAL_PACKETS, droppedPackets.size(), sendBase, cwnd, rwnd);
                lastPrinted = sendBase;
            }
        }
    }


    // Sends a packet to the server (using wrapped sequence numbers).
    private static void sendPacket(PrintWriter out, long seqNum, long attemptedCount) {
        long wrappedSeq = seqNum % 65536;
        out.println(wrappedSeq + ":" + attemptedCount);
    }

    // Processes an incoming ACK, updating the window variables.
    private static void processAck(String ackLine) {
        if (ackLine == null || ackLine.isEmpty()) return;
        
        String[] parts = ackLine.split(":");
        long ackSeqWrapped = Long.parseLong(parts[0]);
        int advertisedRwnd = Integer.parseInt(parts[1]);

        long absoluteAck = getAbsoluteSeq(ackSeqWrapped, sendBase);

        if (absoluteAck > sendBase) {
            sendBase = absoluteAck;
            // Additive Increase. Almost like *2 for every new window, but increase by 1 to not interfere with retransmit steps
            cwnd = Math.min(cwnd + 1, 65535); 
        }
        
        rwnd = advertisedRwnd;
        
        logEvent("ACK_RECV", absoluteAck);
    }


    // Retransmits the dropped packets and applies congestion control.
    private static void retransmitDropped(PrintWriter out) {
        // Cut congestion window in half upon failure
        cwnd = Math.max(16, cwnd / 2); 
        
        Iterator<Long> iter = droppedPackets.iterator();
        while (iter.hasNext()) {
            long seq = iter.next();
            totalAttempted++;
            
            if (rand.nextDouble() < DROP_PROB) {
                // Packet dropped again. Keep it in the list.
                logEvent("DROP_RETRANS", seq);
            } else {
                sendPacket(out, seq, totalAttempted);
                logEvent("RETRANS", seq);
                iter.remove(); 
            }
        }
        stepsSinceRetransmit = 0;
    }

    // Converts a wrapped TCP sequence number back to its absolute sequence number.
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

    // Logs an event to the CSV file.
    private static void logEvent(String type, long seqNum) {
        long timeMs = System.currentTimeMillis() - startTime;
        csvLogger.printf("%d,%s,%d,%d,%d%n", timeMs, type, seqNum, cwnd, rwnd);
    }
}