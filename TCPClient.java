import java.net.*;
import java.io.*;
import java.util.*;


public class TCPClient {

    public static void main(String [] args) {
        try {
            String serverIP = args[0];
            int port = Integer.parseInt(serverIP);

            Socket socket = new Socket(serverIP, port);
            System.out.println("Connected to the server");

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            out.println("network");
            System.out.println("Sent: network");

            String response = in.readLine();
            System.out.println("Server response: " + response);
            
            in.close();
            out.close();
            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
