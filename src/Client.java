import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Scanner;

@SuppressWarnings("Duplicates")

public class Client {

    //CONSTANTES

    //OPCODES
    static final byte RRQ = 1;
    static final byte WRQ = 2; //Non utilisé
    static final byte DATA = 3;
    static final byte ACK = 4;
    static final byte ERROR = 5;

    //CODES ERREUR (Voir norme RFC 1350 pour details)
    static final byte ERROR_FILE_NOT_FOUND = 1;
    static final byte ERROR_FORBIDDEN_ACCESS = 2;
    static final byte ERROR_DISK_FULL = 3;
    static final byte ERROR_ILLEGAL_TFTP_OP = 4;
    static final byte ERROR_TRANSFERT_ID_UNKNOWN = 5;
    static final byte ERROR_FILE_ALREADY_EXISTS = 6;
    static final byte ERROR_UNKNOWN_USER = 7;

    public static void main (String[] args) {

        Scanner sc = new Scanner(System.in);
        String str;

        int cr_rv;

        InetAddress ipServer;

        try {
            ipServer = InetAddress.getByName("192.168.43.10"); //Quentin
            //ipServer = InetAddress.getByName("127.0.0.1");
            //ipServer = InetAddress.getByName("192.168.43.4"); //Fabien
            //ipServer = InetAddress.getByName("192.168.43.11"); //Anthony
        } catch ( IOException err) {
            System.out.println("Erreur hote inconnus !");
            err.printStackTrace();
            return;
        }

        int portServ = 70;

        boolean clientOn = true;

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
                if ( cr_rv != 0 )
                    messageErreur(cr_rv);
            }

        }

        //ds.close();
        sc.close();

        System.out.println("Arrêt du client..");
    }

    public static int receivefile(String fileLocal, String fileDistant, InetAddress ipServ, int portServ) {

        DatagramPacket dp;
        DatagramPacket dpReception = new DatagramPacket(new byte[516], 516);
        DatagramSocket ds;
        FileOutputStream fos = null;

        int ackNum = 1;
        int totalOctetRecu = 0;
        int opcode;
        byte[] data;

        boolean fichierRecu = false;
        boolean paquetNonRecu = false;
        boolean reponseRRQrecu = false;
        int nbEssai = 0;

        //Creation socket
        try {
            ds = new DatagramSocket();
        } //region gestion erreur
        catch (SocketException err) {
            err.printStackTrace();
            return -1;
        } //endregion

        //On envoie la requête initiale

        //Creation packet RRQ
        data = createPacketRRQ(fileDistant);
        dp = new DatagramPacket(data, data.length, ipServ, portServ);

        //Envoie
        try {
            ds.setSoTimeout(5000); //Timeout plutot long pour le RRQ.
            ds.send(dp);
        } //region gestion erreur
        catch (IOException err) {
            err.printStackTrace();
            ds.close();
            return -2;
        } //endregion


        //Reception
        while ( !fichierRecu ) {

            //Boucle réception
            do {
                try {

                    System.out.println("Attente packet...");
                    dpReception = new DatagramPacket(new byte[516], 516);
                    ds.receive(dpReception);

                }
                catch (SocketTimeoutException err) {
                    //Si il y a eu un timeout à la réception
                    paquetNonRecu = true;

                    nbEssai++;
                    //Erreur timeout
                    if ( nbEssai > 3) {
                        ds.close();
                        return -8;
                    }

                    //On renvoie le paquet précédent
                    System.out.println("Timeout reception ! On renvoie le paquet...");
                    try {
                        ds.send(dp);
                    }
                    catch (IOException error) {
                        ds.close();
                        return -3;
                    }
                }
                //region gestion erreur
                catch (IOException err) {
                    ds.close();
                    return -3;
                } //endregion

            } while ( paquetNonRecu );


            nbEssai = 0;
            paquetNonRecu = false;

            data = dpReception.getData();

            //Si c'est la première réception on change le port serveur. (Serveur concurrent)
            if ( portServ == 69 )
                portServ = dpReception.getPort();

            //On récupère l'opcode du packet pour reconnaitre son type.
            opcode = getOpcode(data);

            //System.out.println("Taille datagramme = " + dp.getLength() + " octets.");
            //Traitement réception.
            if ( opcode == DATA ) {

                System.out.println("DATA Packet #" + getPacketNumBlock(data) + " reçu.");
                totalOctetRecu += dpReception.getLength(); //Pour vérifier notre total

                //Si c'est la réponse à notre tout premier paquet, RRQ, on ouvre le fichier
                if ( !reponseRRQrecu ) {

                    //Ouverture du fichier
                    try {
                        fos = new FileOutputStream("data/" + fileLocal);
                    } //region gestion erreur
                    catch (FileNotFoundException err) {
                        //System.out.println("ERREUR fichier non trouvé !");
                        err.printStackTrace();
                        ds.close();
                        return -4;
                    } //endregion

                    try { ds.setSoTimeout(500); } catch (SocketException err) {}

                    reponseRRQrecu = true;
                }

                //On l'écrit dans le fichier le morceau data
                try {
                    for (int i = 4; i < data.length; i++)
                        fos.write(dpReception.getData()[i]);
                } //region gestion erreur
                catch (IOException err) {
                    err.printStackTrace();
                    ds.close();
                    return -5;
                }
                //endregion

                //Si c'était le dernier bout on ferme le fichier et on sors de la boucle.
                if ( dpReception.getLength() < 516 ) {
                    fichierRecu = true;

                    try {
                        fos.close();
                    }
                    //region gestion erreur
                    catch (IOException err) {
                        err.printStackTrace();
                        ds.close();
                        return -6;
                    }
                    //endregion
                }

                //On envoie un ACK de confirmation de reception.
                data = createPacketACK(ackNum++);
                dp = new DatagramPacket(data, data.length, ipServ, portServ);

                try {
                    ds.send(dp);
                } //region gestion erreur
                catch (IOException err) {
                    err.printStackTrace();
                    ds.close();
                    return -2;
                } //endregion

            }
            else if ( opcode == ERROR ) {
                System.out.println("Packet erreur reçu ! ");
                ds.close();
                //return data[3];
                //return  ((data[0] & 0xff) << 8) | (data[1] & 0xff);
                return getErrorCode(dpReception.getData());
            }
            else {
                System.out.println("Packet inconnu !" + " Opcode = " + opcode);
                return -7;
            }

        }

        System.out.println("Fichier reçu ! " + totalOctetRecu + " octets téléchargés.");
        ds.close();

        return 0;

    }

    public static void messageErreur(int codeErreur) {

        System.out.print("ERREUR : ");
        switch (codeErreur) {
            case 0:
                return; //
            case -8:
                System.out.println("Timeout ! 3 paquets envoyés sans réponses !");
                break;
            case -7:
                System.out.println("Erreur reçu datagramme inconnu !");
                break;
            case -6:
                System.out.println("Erreur lors de la fermeture du fichier !");
                break;
            case -5:
                System.out.println("Erreur lors de l'écriture du fichier !");
                break;
            case -4:
                System.out.println("Fichier introuvable !");
                break;
            case -3:
                System.out.println("Erreur lors de la réception d'un datagramme !");
                break;
            case -2:
                System.out.println("Erreur lors de l'envoi du datagramme !");
                break;
            case -1:
                System.out.println("Erreur lors de la création du Socket Client !");
                break;
            case ERROR_FILE_NOT_FOUND:
                System.out.println("fichier introuvable coté serveur !");
                break;
            case ERROR_FORBIDDEN_ACCESS:
                System.out.println("Violation d'accès !");
                break;
            case ERROR_DISK_FULL:
                System.out.println("Disque pleine ou dépassement d'espace alloué !");
                break;
            case ERROR_ILLEGAL_TFTP_OP:
                System.out.println("Opération TFTP illégale !");
                break;
            case ERROR_TRANSFERT_ID_UNKNOWN:
                System.out.println("Transfert ID inconnu !");
                break;
            case ERROR_FILE_ALREADY_EXISTS:
                System.out.println("Le fichier existe déjà !");
                break;
            case ERROR_UNKNOWN_USER:
                System.out.println("Utilisateur inconnu !");
                break;

        }
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

    public static int getErrorCode(byte[] data) {
        //return data[3];
        return  data[3] & 0xFF | (data[2] & 0xFF) << 8;
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


