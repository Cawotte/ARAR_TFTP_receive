import java.net.*;
import java.nio.ByteBuffer;

public class Client {

    //CONSTANTES

    //OPCODES
    static final byte RRQ = 1;
    static final byte WRQ = 2; //Non utilisé
    static final byte DATA = 3;
    static final byte ACK = 4;
    static final byte ERROR = 5;

    //CODES ERREUR (Voir norme RFC 1350 pour details)
    static final byte ERROR_1 = 1;
    static final byte ERROR_2 = 2;
    static final byte ERROR_3 = 3;
    static final byte ERROR_4 = 4;
    static final byte ERROR_5 = 5;
    static final byte ERROR_6 = 6;
    static final byte ERROR_7 = 7;

    public static void main (String[] args) {

        byte[] RRQPacket = createPacketRRQ("Fichier.txt");
        readRRQ(RRQPacket);

        createPacketACK(6);
        createPacketACK(0);
        createPacketACK(3);
        createPacketACK(201);
    }

    public static void main2 (String[] args) {

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




    //region Fuction création de Packets

    //Créer un packet de type WRQ
    public static byte[] createPacketRRQ(String filename) {

        /*
            2 premiers Octet : Opcode = 1
            Ensuite nom de fichier qui se termine par un byte null
            Ensuite string "octet" qui se termine par un byte null.
         */

        int opcode = 1;
        byte[] octetBytes = "octet".getBytes();
        byte[] filenameBytes = filename.getBytes();
        int packetLength = 2 + filenameBytes.length + 1 + octetBytes.length + 1;
        int currentInd = 0;

        byte[] packetRRQ = new byte[packetLength];

        //OPCode
        packetRRQ[0] = 0;
        packetRRQ[1] = RRQ;

        currentInd = 2;
        //Nom du fichier à recevoir
        for ( int i = 0; i < filenameBytes.length; i++) {
            packetRRQ[i+2] = filenameBytes[i];
            currentInd++;
        }
        //byte null de fin de chaine
        packetRRQ[currentInd++] = 0; //currentInd est incrémenté après utilisation.

        //Nom mode = "octet"
        for ( int i = 0; i < octetBytes.length; i++) {
            packetRRQ[currentInd] = octetBytes[i];
            currentInd++;
        }
        packetRRQ[currentInd] = 0; //byte null de fin de chaine

        //Affichage du RRQ Packet (Debug)
        readRRQ(packetRRQ);

        return packetRRQ;

    }

    //Lit un packet RRQ affiche son contenu dans la console ( Debug )
    public static void readRRQ(byte[] packetRRQ) {

        int i;
        System.out.println("Lecture du packet RRQ :");
        System.out.println("Opcode = " + packetRRQ[1]);

        //Nom fichier
        String filename = "";
        i = 2;
        while ( packetRRQ[i] != 0) {
            filename += (char)packetRRQ[i]; //Si bug lecture (mauvais caractères )inspecter ça.
            i++;
        }
        System.out.println("Nom du fichier : " + filename + ", longueur nom = " + (i-2));

        //Nom mode
        i++;
        String mode = "";
        while ( packetRRQ[i] != 0) {
            mode += (char)packetRRQ[i]; //Si bug lecture (mauvais caractères )inspecter ça.
            i++;
        }
        System.out.println("Nom du mode : " + mode);

        if ( ++i < packetRRQ.length ) {
            System.out.println("Datagram non terminé ! Il reste " + (packetRRQ.length - i) + " paquets à lire.");
        }

    }

    //Crée un packet ACK
    public static byte[] createPacketACK(int numBlock) {
        /*
        Packet ACK
            Opcode = 4 sur les deux premiers octets
            Numéro du bloc de données sur les 4 octets suivants.
         */

        byte[] packetACK = new byte[4];

        //Opcode
        packetACK[0] = 0;
        packetACK[1] = ACK;

        //Num bloc:
        //On convertie le int en un tableau de 4 octets.
        byte[] numBlockBytes = shortToBytes((short)numBlock);

        //On remplit le packet
        for (int i = 2; i < 4; i++) {
            packetACK[i] = numBlockBytes[i-2];
        }

        System.out.println("packet numblock = " + getPacketNumBlock(packetACK));
        return packetACK;

    }

    //Crée un packet DATA
    public static byte[] createPacketDATA(int numBlock, byte[] data) {
        /*
        Packet DATA
            Opcode = 3 sur les deux premiers octets
            Numéro du bloc de données sur les 2 octets suivants.
            Données sur tout les octets suivants, maximum 512 octets de données.
         */
        if ( data.length > 512 ) {
            System.out.println("ERREUR création Data Packet : Data fais plus de 512 octets !");
            return null;
        }

        byte[] packetDATA = new byte[4 + data.length];

        //Opcode
        packetDATA[0] = 0;
        packetDATA[1] = DATA;

        //Numblock
        byte[] numBlockBytes = intToSmallBytes(numBlock);
        packetDATA[2] = numBlockBytes[0];
        packetDATA[3] = numBlockBytes[1];

        //Data
        for ( int i = 0; i < data.length; i++)
            packetDATA[i+4] = data[i];

        return packetDATA;
    }

    //Crée un Packet Error
    public static byte[] createPacketError(byte ErrorCode) {
        /*
        Packet Error :
            OPcode sur 2 octets
            ErrorCode sur 2 octets
            String du ErrMsg se terminant par un byte null
         */

        //TODO : GESTION MESSAGES ERREURS

        byte[] errMsgBytes = "Erreur".getBytes();
        byte[] packetError = new byte[errMsgBytes.length + 4];

        //Opcode
        packetError[0] = 0;
        packetError[1] = ERROR;

        //ErrorCode
        packetError[2] = 0;
        packetError[3] = ErrorCode;

        for (int i = 0; i < errMsgBytes.length; i++)
            packetError[i+4] = errMsgBytes[i];
        packetError[errMsgBytes.length + 3] = 0; //byte null fin chaine

        return packetError;
    }

    //endregion

    //Renvoie le numéro de bloc du packets ACK ou DATA donné en argument.
    public static int getPacketNumBlock(byte[] packet) {

        //Gestion erreur par opcode
        if ( packet[1] != ACK && packet[1] != DATA ) {
            System.out.println("ERREUR getPacketNumBlock : Ce n'est pas un packet ACK ou DATA !");
            return -1;
        }

        return  packet[3] & 0xFF | (packet[2] & 0xFF) << 8;
    }

    //Convertie un integer en un tableau de 4 octets
    public static byte[] intToBytes(int value) {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(value);
        return bb.array();
    }

    //Convertie un short en un tableau de 2 octets
    public static byte[] shortToBytes(short value) {
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.putShort(value);
        return bb.array();
    }

    //Convertie un int en un tableau de 2 octets
    public static byte[] intToSmallBytes(int value) {
        return shortToBytes((short)value);
    }
}


