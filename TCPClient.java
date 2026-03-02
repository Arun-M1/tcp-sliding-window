import java.io.*;
import java.net.*;
import java.util.*;

public class TCPClient {

    static final String SERVER_IP = "127.0.0.1";
    static final int PORT = 9999;
    static final double DROP_PROB = 0.01;
    static final int TOTAL_PACKETS = 100000;
    static final int RETRANSMISSION = 100;

    public static void main(String[] args) throws Exception {

        String host = args.length > 0 ? args[0] : SERVER_IP;

        Socket socket = new Socket(host, PORT);
        Scanner in = new Scanner(socket.getInputStream());
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        System.out.println("[CLIENT] Connected to server.");

        // --- Handshake ---
        out.println("network");
        System.out.println("[CLIENT] Server says: " + in.nextLine());

        // --- Send loop ---
        Random rand = new Random();
        long nextSeq = 0;
        long totalAttempted = 0;
        int stepCounter = 0;
        List<Long> dropped = new ArrayList<>();

        while (nextSeq < TOTAL_PACKETS) {

            totalAttempted++;

            if (rand.nextDouble() < DROP_PROB) {
                dropped.add(nextSeq);
            } else {
                out.println(nextSeq + ":" + totalAttempted);
                in.nextLine();  // wait for ACK
            }

            nextSeq++;
            stepCounter++;

            // Retransmit dropped packets every RETRANSMISSION steps
            if (stepCounter >= RETRANSMISSION && !dropped.isEmpty()) {
                List<Long> stillDropped = new ArrayList<>();
                for (long idx : dropped) {
                    totalAttempted++;
                    if (rand.nextDouble() < DROP_PROB) {
                        stillDropped.add(idx);
                    } else {
                        out.println(idx + ":" + totalAttempted);
                        in.nextLine();
                    }
                }
                dropped = stillDropped;
                stepCounter = 0;
            }

            if (nextSeq % 10000 == 0) {
                System.out.printf("[CLIENT] Progress: %,d / %,d  pending_retransmit=%d%n",
                        nextSeq, TOTAL_PACKETS, dropped.size());
            }
        }

        // Final retransmit pass
        for (long idx : dropped) {
            totalAttempted++;
            if (rand.nextDouble() >= DROP_PROB) {
                out.println(idx + ":" + totalAttempted);
                in.nextLine();
            }
        }

        out.println("DONE");
        System.out.printf("[CLIENT] Done. Attempted=%,d%n", totalAttempted);

        // Close everything
        in.close();
        out.close();
        socket.close();
    }
}
