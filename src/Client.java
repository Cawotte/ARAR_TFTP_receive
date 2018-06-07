import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.sql.SQLOutput;
import java.util.Scanner;

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

    public static void main2 (String[] args) {

        byte[] RRQPacket = createPacketRRQ("Fichier.txt");
        readRRQ(RRQPacket);

        createPacketACK(6);
        createPacketACK(0);
        createPacketACK(3);
        createPacketACK(201);
    }

    public static void main (String[] args) {

        Scanner sc = new Scanner(System.in);
        String str;

        int cr_rv;
        int port;

        InetAddress ipServer;

        try {
            ipServer = InetAddress.getByName("127.0.0.1");
        } catch ( IOException err) {
            System.out.println("Erreur hote inconnus !");
            err.printStackTrace();
            return;
        }

        int portServ = 69;

        byte[] data = new byte[1024];

        boolean clientOn = true;

        //region Initialisation Client
        /*
        try {
            ds = new DatagramSocket();
            port = ds.getLocalPort();
        } catch (SocketException err) {
            System.out.println("Erreur création Client !");
            err.printStackTrace();
            return;
        } */
        //endregion

        //Information
        System.out.println("Serveur cible : " + ipServer.toString() + " : " + portServ);
        //Boucle principale

        while (clientOn) {

            //On lit l'entrée client
            System.out.println("Entrez 'quit' pour arrêter le client ou le nom du fichier à obtenir ");
            str = sc.nextLine();

            if ( str.equals("quit") ) {
                clientOn = false;
            }
            else {
                cr_rv = receivefile(str, str, ipServer, portServ);
                System.out.println("cr_rv = " + cr_rv);
            }

        }

        //ds.close();
        sc.close();

        System.out.println("Arrêt du client..");
    }

    public static int receivefile(String fileLocal, String fileDistant, InetAddress ipServ, int portServ) {

        DatagramPacket dp;
        DatagramSocket ds;
        int ackNum = 1;
        byte[] data;
        byte[] datafile;
        InetAddress ip;
        FileOutputStream fos;

        boolean fichierRecu = false;
        //Creation socket
        try {
            //ip = InetAddress.getByName(ipServ);
            ds = new DatagramSocket();
        } catch (SocketException err) {
            System.out.println("Erreur création socket Client !");
            err.printStackTrace();
            return -1;
        } /*catch (UnknownHostException err) {
            System.out.println("Adresse IP non valide !");
            err.printStackTrace();
            return -2;
        }*/
        //endregion

        //On envoie la requête initiale

        //Creation packet RRQ
        data = createPacketRRQ(fileDistant);
        dp = new DatagramPacket(data, data.length, ipServ, portServ);

        //Envoie
        try {
            ds.send(dp);
        }
        catch (IOException err) {
            System.out.println("Erreur envoie RRQ !");
            err.printStackTrace();
            ds.close();
            return -3;
        }

        //Ouverture du fichier
        try {
            fos = new FileOutputStream(fileLocal);
        }
        catch (FileNotFoundException err) {
            System.out.println("ERREUR fichier non trouvé !");
            err.printStackTrace();
            ds.close();
            return -6;
        }

        //Reception
        while ( !fichierRecu ) {

            try {
                System.out.println("attente reponse");
                dp = new DatagramPacket(new byte[516], 516);
                ds.receive(dp);
                System.out.println("Réponse reçue !");

                data = dp.getData();
                portServ = dp.getPort();
                System.out.println("nouveau port serv : " + portServ);
                int opcode = getOpcode(data);

                System.out.println("data length = " + data.length);
                //Traitement réception.
                if ( opcode == DATA ) {

                    System.out.println("DATA packet reçu !");

                    System.out.println("Numéro de bloc : " + getPacketNumBlock(data));
                    //On récupère le bout de data qui nous intéresse.
                    datafile = new byte[data.length - 4];
                    for (int i = 4; i < data.length; i++)
                        datafile[i-4] = data[i];


                    System.out.println("datafile length = " + datafile.length);

                    //On l'écrit dans le fichier
                    fos.write(datafile, 0, datafile.length);

                    //Si c'était le dernier bout on ferme le fichier.
                    if ( datafile.length <= 512 ) {
                        fichierRecu = true;
                        fos.close();
                    }

                    //On envoie un ACK de confirmation de reception.
                    data = createPacketACK(ackNum++);
                    dp = new DatagramPacket(data, data.length, ipServ, portServ);
                    ds.send(dp);

                }
                else if ( opcode == ERROR ) {
                    System.out.println("ERREUR RECUE ! ");
                    ds.close();
                    return -5;
                }
                else {
                    System.out.println("ERREUR packet inconnue !");
                    System.out.println("opcode = " + opcode);
                }

            }
            catch (IOException err) {
                System.out.println("Erreur réception réponse !");
                err.printStackTrace();
                ds.close();
                return -4;
            }

        }

        System.out.println("Fichier reçu !");
        ds.close();

        return 0;

    }

    //region Fuction création de Packets

    public static int getOpcode(byte[] data) {
        return data[1];
    }

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
        //readRRQ(packetRRQ);

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

        //System.out.println("packet numblock = " + getPacketNumBlock(packetACK));
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


