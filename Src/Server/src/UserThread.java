import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

/**
 * Klasa UserThread odpowiedzialna za komunikację serwera z konkretnym klientem.
 */
public class UserThread extends Thread {
    private Socket socket;
    private Server server;
    private BufferedReader br;
    private PrintWriter pw;

    private OutputStream out;
    private InputStream in;

    /**
     * String, w którym przechowywana jest ścieżka do katalogu użytkownika po stronie serwera.
     */
    private String dirpath;

    /**
     * Hashset, w którym przechowywane są nazwy wcześniej zsynchronizowanych plików.
     * Wykorzystywane do usuwania plików, które zostały usunięte ręcznie po jednej z dwóch stron.
     */
    protected Set<String> syncedBefore = new HashSet<>();

    /**
     * Model listy plików wykorzystywany do wypisywania zawartości folderu na liście.
     */
    private static DefaultListModel filesListModel;

    /**
     * Dane użytkownika odebrane od klienta - nazwa użytkownika oraz ścieżka lokalna.
     */
    private String name;
    private String path;

    /**
     * Tablica plików wykorzystywana do iteracji pomiędzy plikami w katalogu oraz badania różnic między
     * wcześniejszym skanem plików a kolejnym.
     */
    private File[] dirList;

    /**
     * Konstruktor klasy UserThread. Wpisuje dane do odpowiednich pól klasy.
     *
     * @param socket - socket serwera
     * @param server - serwer, pod który ten wątek podlega.
     */
    public UserThread(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    /**
     * Metoda uruchamiająca wątek, a wraz z nim całą komunikację pomiędzy serwerem a klientem.
     * Uruchamiana za pomocą funkcji start() użytej w aplikacji serwera.
     */
    public void run() {
        try {
            filesListModel=new DefaultListModel();
            in = socket.getInputStream();
            br = new BufferedReader(new InputStreamReader(in));
            out = socket.getOutputStream();
            pw = new PrintWriter(out, true);

            name = br.readLine();
            path = br.readLine();
            server.addUser(name,path);

            dirpath = "./Files/"+name;
            File dir = new File(dirpath);
            File[] dirListOld=null;
            String check="";
            String fname="";
            String ret="";

            String tmp;
            String fileToShare;
            String userToShare;

            if(!dir.exists()) dir.mkdirs();
            while(true) {
                dirList=dir.listFiles();
                for(File f : dirList) {
                    if (!checkFile(f.getName())) {
                        if(syncedBefore.contains(f.getName())) {
                            f.delete();
                            filesListModel.removeElement(f.getName());
                        }
                        else sendFile(f);
                    }
                }
                if(!Arrays.equals(dirList, dirListOld)) {
                    server.refreshFiles(name,this);
                }
                dirListOld=dirList;
                pw.println("DIRDONE");

                check = br.readLine();
                if (check.equals("USERSBEGIN")) {
                    for (String i : server.userNames) {
                        pw.println("NEXTUSER");
                        br.readLine();
                        pw.println(i);
                        br.readLine();
                    }
                    pw.println("USERSEND");
                }

                while(true) {
                    check = br.readLine();
                    if (check.equals("CHECKFILE")) {
                        pw.println("AWAIT");
                        fname = br.readLine();
                        ret = "NOTEXIST";
                        for (File i : dirList) {
                            if (i.getName().equals(fname)) {
                                ret="EXISTS";
                                break;
                            }
                        }
                        pw.println(ret);
                    }
                    else if (check.equals("GETFILE")) {
                        getFile(fname);
                    }
                    else if (check.equals("DIRDONE")) break;
                }
                if(!Arrays.equals(dirList, dirListOld)) {
                    server.refreshFiles(name,this);
                }
                if(filesListModel.size()>0) {
                    filesListModel.addElement("");
                    filesListModel.removeElement("");
                }
                dirListOld=dirList;

                pw.println("AWAIT");
                check = br.readLine();
                if (check.equals("USERSBEGIN")) {
                    for (String i : server.userNames) {
                        pw.println("NEXTUSER");
                        br.readLine();
                        pw.println(i);
                        br.readLine();
                    }
                    pw.println("USERSEND");
                }

                check=br.readLine();

                if (check.equals("SHAREBEGIN")) {
                    pw.println("AWAIT");
                    tmp = br.readLine();
                    while(!tmp.equals("SHAREEND")) {
                        pw.println("AWAIT");
                        fileToShare = br.readLine();
                        pw.println("AWAIT");
                        userToShare = br.readLine();
                        copyFile(fileToShare, userToShare);
                        pw.println("AWAIT");
                        tmp = br.readLine();
                    }
                }
            }
        }
        catch (IOException e) {
            server.removeUser(name, path, this);
            try {
                socket.close();
            }
            catch (IOException ex) {}
        }
        catch (NullPointerException e) {
            server.removeUser(name, path, this);
            try {
                socket.close();
            }
            catch (IOException ex) {}
        }
    }

    /**
     * Metoda służąca do kopiowania plików pomiędzy katalogami użytkowników po stronie serwera.
     * @param filename - nazwa pliku do kopiowania.
     * @param userToShare - nazwa użytkownika, do którego plik ma zostać skopiowany.
     */
    void copyFile(String filename, String userToShare) {
        File src = new File(dirpath+"/"+filename);
        File dst = new File("./Files/"+userToShare+"/"+filename);
        if (dst.exists()) return;
        else try {
            dst.createNewFile();
        }
        catch (IOException e) {}

        try (
            FileInputStream fis = new FileInputStream(src);
            FileOutputStream fos = new FileOutputStream(dst)
        ) {
            byte[] buffer = new byte[(int) src.length()];
            int current;
            while((current = fis.read(buffer)) >0) {
                fos.write(buffer,0,current);
            }
        }
        catch (Exception e) {}

    }

    /**
     * Metoda służąca do sprawdzania, czy po drugiej stronie istnieje żądany plik.
     * @param name - nazwa pliku, którego szukamy.
     * @return - zwraca true, jeśli plik istnieje.
     */
    boolean checkFile(String name) {
        pw.println("CHECKFILE");
        try {
            sleep(100);
            br.readLine();
        }
        catch (Exception e) {
            server.removeUser(name, path, this);
            try {
                socket.close();
            }
            catch (IOException ex) {}
        }
        pw.println(name);
        String res="NOTEXIST";
        try {
            sleep(100);
            res=br.readLine();
        }
        catch (Exception e) {
            server.removeUser(name, path, this);
            try {
                socket.close();
            }
            catch (IOException ex) {}
        }
        return res.equals("EXISTS");
    }

    /**
     * Metoda służąca do wysłania pliku do danego klienta.
     * @param file - obiekt klasy Plik, który chcemy wysłać.
     */
    void sendFile(File file) {
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
            server.removeUser(name, path, this);
            try {
                socket.close();
            }
            catch (IOException ex) {}
        }

        try {
            if (bis!=null) bis.close();
        }
        catch (IOException e) {
            server.removeUser(name, path, this);
            try {
                socket.close();
            }
            catch (IOException ex) {}
        }
    }

    /**
     * Metoda służąca do odbierania pliku z klienta. Do wykorzystania po użyciu checkFile().
     * @param filename - nazwa pliku, który chcemy odebrać.
     */
    void getFile(String filename) {
        int bytesRead;
        int current = 0;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        try {
            pw.println("AWAIT");
            String size = br.readLine();
            Integer sizeInt = Integer.parseInt(size);
            byte[] bytearray = new byte[sizeInt];
            fos = new FileOutputStream(dirpath + "/" + filename);
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
            server.removeUser(name, path, this);
            try {
                socket.close();
            }
            catch (IOException ex) {}
        }
        finally {
            try {
                if (fos!=null) fos.close();
                if (bos!=null) bos.close();
            }
            catch (IOException e) {
                server.removeUser(name, path, this);
                try {
                    socket.close();
                }
                catch (IOException ex) {}
            }
        }
    }

    /**
     * @return - nazwa użytkownika.
     */
    public String getUserName() {
        return name;
    }

    /**
     * @return - zbiór plików.
     */
    public File[] getDirList() {
        return dirList;
    }
}
