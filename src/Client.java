import java.net.*;

public class Client {


    public static void main (String[] args) {

        DatagramSocket ds;
        DatagramPacket dp;

        int port;

        byte[] data = new byte[1024];

        boolean clientOn = true;

        //region Initialisation Client
        try {
            ds = new DatagramSocket();
            port = ds.getLocalPort();
            dp = new DatagramPacket(data, data.length);
        } catch (SocketException err) {
            System.out.println("Erreur création serveur !");
            err.printStackTrace();
            return;
        }
        //endregion

        //Boucle principale
        /*
        while (clientOn) {


            //On envoie une requête au serveur :

        }*/
    }

    public int receivefile(String fileLocal, String fileDistant, String ipAdresse) {

        DatagramPacket dp;
        DatagramSocket ds;
        int port;
        byte[] data = new byte[1024];
        InetAddress ip;

        //Creation socket
        try {
            ip = InetAddress.getByName(ipAdresse);
            ds = new DatagramSocket();
            port = ds.getLocalPort();
            dp = new DatagramPacket(data, data.length);
        } catch (SocketException err) {
            System.out.println("Erreur création socket Client !");
            err.printStackTrace();
            return -1;
        } catch (UnknownHostException err) {
            System.out.println("Adresse IP non valide !");
            err.printStackTrace();
            return -2;
        }
        //endregion





        return 0;

    }

    /*
    public byte[] createPacketRQ(String rq, String filename) {

        int opcode;


    }
    */
}


