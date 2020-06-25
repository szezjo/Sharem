import java.net.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

/**
 * Klasa SyncThread odpowiedzialna za komunikację klienta z serwerem.
 */
public class SyncThread extends Thread{
    private Socket socket;
    private Client client;
    private BufferedReader br;
    private PrintWriter pw;

    private InputStream in;
    private OutputStream out;

    /**
     * Hashset, w którym przechowywane są nazwy wcześniej zsynchronizowanych plików.
     * Wykorzystywane do usuwania plików, które zostały usunięte ręcznie po jednej z dwóch stron.
     */
    protected Set<String> syncedBefore = new HashSet<>();

    /**
     * Model listy użytkowników oraz model listy plików wykorzystywany do wypisywania zawartości folderu na liście.
     */
    private static DefaultListModel users_listModel;
    private static DefaultListModel files_listModel;

    /**
     * Konstruktor klasy SyncThread. Wpisuje dane do odpowiednich pól klasy.
     *
     * @param socket - socket klienta
     * @param client - klient, pod który ten wątek podlega.
     */
    public SyncThread(Socket socket, Client client) {
        this.socket=socket;
        this.client=client;
    }

    /**
     * Metoda uruchamiająca wątek, a wraz z nim całą komunikację pomiędzy klientem a serwerem.
     * Uruchamiana za pomocą funkcji start() użytej w aplikacji klienta.
     */
    public void run() {
        try {
            users_listModel=new DefaultListModel();
            client.shareui.usersList.setModel(users_listModel);
            files_listModel=new DefaultListModel();
            client.ui.filesList.setModel(files_listModel);
            in = socket.getInputStream();
            br = new BufferedReader(new InputStreamReader(in));
            out = socket.getOutputStream();
            pw = new PrintWriter(out, true);

            pw.println(client.username);
            sleep(100);
            pw.println(client.userpath);

            String dirpath = "./" + client.userpath;
            File dir = new File(dirpath);
            File[] dirListOld=null;
            File[] dirList;
            String check="";
            String fname="";
            String ret="";

            Set<String> namesListOld= new HashSet<>();
            Set<String> namesList = new HashSet<>();
            String tmpname;

            if(!dir.exists()) dir.mkdirs();
            while(true) {
                dirList=dir.listFiles();
                while(true) {
                    check = br.readLine();
                    if(check.equals("CHECKFILE")) {
                        client.ui.statusPanel.setText("Sprawdzanie...");
                        pw.println("AWAIT");
                        fname = br.readLine();
                        ret = "NOTEXIST";
                        for (File i : dirList) {
                            if (i.getName().equals(fname)) {
                                ret = "EXISTS";
                                break;
                            }
                        }
                        pw.println(ret);
                    }
                    else if (check.equals("GETFILE")) {
                        client.ui.statusPanel.setText("Pobieranie...");
                        getFile(fname);
                    }
                    else if (check.equals("DIRDONE")) break;
                }
                if(!Arrays.equals(dirList, dirListOld)) {
                    client.ui.statusPanel.setText("Odświeżanie...");
                    //files_listModel.clear();
                    for(File f : dirList) {
                        if (!files_listModel.contains(f.getName())) files_listModel.addElement(f.getName());
                    }
                }
                dirListOld=dirList;
                if(files_listModel.size()>0) {
                    files_listModel.addElement("");
                    files_listModel.removeElement("");
                }


                pw.println("USERSBEGIN");
                tmpname=br.readLine();
                namesList.clear();
                while(!tmpname.equals("USERSEND")) {
                    pw.println("AWAIT");
                    tmpname=br.readLine();
                    if (!tmpname.equals(client.username)) namesList.add(tmpname);
                    pw.println("AWAIT");
                    tmpname=br.readLine();
                }
                if (!namesList.equals(namesListOld)) {
                    users_listModel.clear();
                    for (String i : namesList) {
                        users_listModel.addElement(i);
                    }
                    namesListOld.clear();
                    namesListOld.addAll(namesList);
                }



                for(File f : dirList) {
                    client.ui.statusPanel.setText("Sprawdzanie...");
                    if (!checkFile(f.getName())) {
                        if(syncedBefore.contains(f.getName())) {
                            f.delete();
                            files_listModel.removeElement(f.getName());
                        }
                        else {
                            client.ui.statusPanel.setText("Wysyłanie...");
                            sendFile(f);
                        }
                    }
                }
                if(!Arrays.equals(dirList, dirListOld)) {
                    client.ui.statusPanel.setText("Odświeżanie...");
                    //files_listModel.clear();
                    for(File f : dirList) {
                        if(!files_listModel.contains(f.getName())) files_listModel.addElement(f.getName());
                    }
                }
                dirListOld=dirList;
                if(files_listModel.size()>0) {
                    files_listModel.addElement("");
                    files_listModel.removeElement("");
                }
                pw.println("DIRDONE");

                br.readLine();
                pw.println("USERSBEGIN");
                tmpname=br.readLine();
                namesList.clear();
                while(!tmpname.equals("USERSEND")) {
                    pw.println("AWAIT");
                    tmpname=br.readLine();
                    if (!tmpname.equals(client.username)) namesList.add(tmpname);
                    pw.println("AWAIT");
                    tmpname=br.readLine();
                }

                if (!namesList.equals(namesListOld)) {
                    users_listModel.clear();
                    for (String i : namesList) {
                        if (!i.equals(getName())) users_listModel.addElement(i);
                    }
                    namesListOld.clear();
                    namesListOld.addAll(namesList);
                }

                String t;
                if(!client.filesQueue.isEmpty()) {
                    client.ui.statusPanel.setText("Synchronizacja udostępnionych plików...");
                    pw.println("SHAREBEGIN");
                    t=br.readLine();
                    for (int i=0; i<client.filesQueue.size(); i++) {
                        pw.println("NEXTFILE");
                        t=br.readLine();
                        pw.println(client.filesQueue.get(i));
                        t=br.readLine();
                        pw.println(client.filesQueueUsrnames.get(i));
                        t=br.readLine();
                    }
                    pw.println("SHAREEND");
                    client.filesQueue.clear();
                    client.filesQueueUsrnames.clear();
                }
                else pw.println("SHAREEND");
                client.ui.statusPanel.setText("Oczekiwanie...");
            }
        }
        catch (Exception e) {
            System.exit(1);
        }
    }

    /**
     * Metoda służąca do sprawdzania, czy po drugiej stronie istnieje żądany plik.
     * @param name - nazwa pliku, którego szukamy.
     * @return - zwraca true, jeśli plik istnieje.
     */
    public boolean checkFile(String name) {
        pw.println("CHECKFILE");
        try {
            sleep(100);
            br.readLine();
        }
        catch (Exception e) {
            System.exit(1);
        }
        pw.println(name);
        String res="NOTEXIST";
        try {
            sleep(100);
            res=br.readLine();
        }
        catch (Exception e) {
            System.exit(1);
        }
        return res.equals("EXISTS");
    }

    /**
     * Metoda służąca do wysłania pliku do serwera.
     * @param file - obiekt klasy Plik, który chcemy wysłać.
     */
    public void sendFile(File file) {
        BufferedInputStream bis = null;
        try {
            byte[] bytearray = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            bis = new BufferedInputStream(fis);
            bis.read(bytearray,0,bytearray.length);
            pw.println("GETFILE");
            sleep(100);
            br.readLine();
            pw.println(bytearray.length);
            br.readLine();
            out.write(bytearray,0,bytearray.length);
            out.flush();
            syncedBefore.add(file.getName());
        }
        catch (Exception e) {
            System.exit(1);
        }

        try {
            if (bis!=null) bis.close();
        }
        catch (IOException e) {
            System.exit(1);
        }
    }

    /**
     * Metoda służąca do odbierania pliku z serwera. Do wykorzystania po użyciu checkFile().
     * @param filename - nazwa pliku, który chcemy odebrać.
     */
    public void getFile(String filename) {
        int bytesRead;
        int current = 0;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        try {
            pw.println("AWAIT");
            String size = br.readLine();
            Integer sizeInt = Integer.parseInt(size);
            byte[] bytearray = new byte[sizeInt];
            fos = new FileOutputStream(client.userpath + "/" + filename);
            bos = new BufferedOutputStream(fos);
            pw.println("AWAIT");

            bytesRead = in.read(bytearray,0,bytearray.length);
            current=bytesRead;
            do {
                bytesRead=in.read(bytearray,current,(bytearray.length-current));
                if(bytesRead>=0) current+=bytesRead;
            } while (bytesRead>0);
            bos.write(bytearray,0,current);
            bos.flush();
            syncedBefore.add(filename);
        }
        catch (Exception e) {
            System.exit(1);
        }
        finally {
            try {
                if (fos!=null) fos.close();
                if (bos!=null) bos.close();
            }
            catch (IOException e) {
                System.exit(1);
            }
        }
    }
}
