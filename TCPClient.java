import java.io.*;
import java.net.*;
import java.util.*;

public class TCPClient {

    private static final String SERVER_IP = "127.0.0.1";
    private static final int PORT = 9000;
    private static final double DROP_PROB = 0.01;
    private static final int TOTAL_PACKETS = 500000;
    private static final int RETRANSMISSION = 100;

    public static void main(String[] args) throws Exception {

        String host;
        if (args.length > 0 ) {
            host = args[0];
        } else {
            host = SERVER_IP;
        }

        Socket socket = new Socket(host, PORT);
        Scanner in = new Scanner(socket.getInputStream());
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        System.out.println("Connected to server.");

        //connect to server
        out.println("network");
        System.out.println("Message from server: " + in.nextLine());

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

            //retransmit dropped packets every RETRANSMISSION steps
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

            //print every 10,000 packets
            if (nextSeq % 10000 == 0) {
                System.out.printf("Progress: %,d / %,d  pending_retransmit=%d%n",
                        nextSeq, TOTAL_PACKETS, dropped.size());
            }
        }

        //retransmit the remaining dropped packets
        for (long idx : dropped) {
            totalAttempted++;
            if (rand.nextDouble() >= DROP_PROB) {
                out.println(idx + ":" + totalAttempted);
                in.nextLine();
            }
        }

        out.println("DONE");
        System.out.printf("Done. Attempted=%,d%n", totalAttempted);

        //close everything
        in.close();
        out.close();
        socket.close();
    }
}
