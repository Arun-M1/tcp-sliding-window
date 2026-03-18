import java.io.*;
import java.net.*;
import java.util.*;

public class TCPClient {

    private static final String SERVER_IP = "127.0.0.1";
    private static final int PORT = 9000;
    private static final double DROP_PROB = 0.01;
    private static final int TOTAL_PACKETS = 100000;
    private static final int RETRANSMISSION = 100;
    private static final int WINDOW_SIZE = 16;

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
        long sendBase = 0;

        while (nextSeq < TOTAL_PACKETS || sendBase < TOTAL_PACKETS) {

            //packets are sent until all of them are sent or until the window is full
            while (nextSeq < TOTAL_PACKETS && (nextSeq - sendBase) < WINDOW_SIZE) {

                totalAttempted++;

                if (rand.nextDouble() < DROP_PROB) {
                    dropped.add(nextSeq);
                } else {
                    long wrappedSeq = nextSeq % 65536;
                    out.println(wrappedSeq + ":" + totalAttempted);
                }

                nextSeq++;
                stepCounter++;
            }

            String ackLine = in.nextLine();
            long cumulativeAck = Long.parseLong(ackLine.trim());
            sendBase = Math.max(sendBase, cumulativeAck);

            //print every 10,000 packets
            long lastPrinted = 0;
            if (sendBase % 10000 == 0 && sendBase > 0  && sendBase != lastPrinted) {
                System.out.printf("Progress: %,d / %,d  pending_retransmit=%d  sendBase=%d%n",
                    nextSeq, TOTAL_PACKETS, dropped.size(), sendBase);
                lastPrinted = sendBase;
            }

            //if window is full, but can not restransmit because we are waiting for ACk(deadlock)
            boolean stuckWindow = (nextSeq - sendBase) >= WINDOW_SIZE && !dropped.isEmpty();

            //retransmit dropped packets every RETRANSMISSION steps
            if ((stepCounter >= RETRANSMISSION || stuckWindow) && !dropped.isEmpty()) {
                List<Long> stillDropped = new ArrayList<>();
                for (long idx : dropped) {
                    totalAttempted++;
                    if (rand.nextDouble() < DROP_PROB) {
                        stillDropped.add(idx);
                    } else {
                        long wrappedIdx = idx % 65536;
                        out.println(wrappedIdx + ":" + totalAttempted);
                        in.nextLine();
                    }
                }
                dropped = stillDropped;
                stepCounter = 0;
            }
        }

        //retransmit the remaining dropped packets
        for (long idx : dropped) {
            totalAttempted++;
            if (rand.nextDouble() >= DROP_PROB) {
                out.println(idx % 65536+ ":" + totalAttempted);
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
