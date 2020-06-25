import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Klasa uruchamiająca aplikację serwera.
 *
 * @author Mateusz Groblewski
 * @version 0.01
 */
public class Server {
    /** Port serwera - ten sam musi być wpisany w kliencie. */
    public final static int port = 13243;
    /** Set przechowujący nazwy zalogowanych użytkowników. */
    protected Set<String> userNames = new HashSet<>();
    /** Set przechowujący ścieżki zalogowanych użytkowników. */
    protected Set<String> userPaths = new HashSet<>();
    /** Set przechowujący wątki (obiekty klasy UserThread) zalogowanych użytkowników. */
    protected static Set<UserThread> userThreads = new HashSet<>();

    /** Główna ramka interfejsu graficznego */
    private static JFrame mainframe;
    /** oraz obiekt klasy ServerUI, w której znajdują się informacje o
     * tym, jaki powinien być układ interfejsu. */
    protected static ServerUI ui;
    /** Model dla listy użytkowników */
    private static DefaultListModel users_listModel;
    /** Model dla listy plików */
    private static DefaultListModel fileListModel = new DefaultListModel();

    /** ListSelectionListener nasłuchujący, jaki użytkownik został wybrany z listy -
     * powoduje on zmianę listy plików na tę konkretnego użytkownika.
     */
    protected static ListSelectionListener userSelectionListener = new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent ev) {
            if (ev!=null && ev.getValueIsAdjusting()) {
                return;
            }
            if (ev==null) {
                fileListModel.clear();
                return;
            }
            for (UserThread i : userThreads) {
                if (i.getUserName().equals(ui.users_list.getSelectedValue())) {
                    fileListModel.clear();
                    for (File f : i.getDirList()) {
                        fileListModel.addElement(f.getName());
                    }
                    return;
                }
            }
            fileListModel.clear();
        }
    };

    /** Metoda go() uruchamia nowy socket i czeka na połącznie z klientem - jeśli zostanie ono zaakceptowane, tworzony
     * jest nowy obiekt klasy UserThread i rozpoczynany jest wątek z nim związany. Obiekt ten jest również dodawany
     * do setu userThreads.
     */
    public void go(){
        try (ServerSocket ssocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = ssocket.accept();
                UserThread user = new UserThread(socket, this);
                userThreads.add(user);
                user.start();
            }
        }
        catch (IOException e) {
            System.out.println("IO Error");
        }

    }

    /**
     * Metoda dodająca nazwę oraz ścieżkę lokalną użytkownika otrzymaną od klienta.
     * Odświeżane są również: label z dopisaną ilością użytkowników oraz model listy użytkowników.
     *
     * @param name - nazwa użytkownika. Dodawana do setu userNames.
     * @param path - ścieżka lokalna. Dodawana do setu userPaths.
     */
    void addUser(String name, String path) {
        userNames.add(name);
        userPaths.add(path);
        users_listModel.addElement(name);
        ui.users_label.setText("Użytkownicy ("+ userNames.size() +")");
    }

    /**
     * Metoda usuwająca użytkownika i jego dane z odpowiednich setów - wykorzystywana przy wylogowaniu klienta.
     * Odświeżane są również: label z dopisaną ilością użytkowników oraz model listy użytkowników.
     *
     * @param name - nazwa użytkownika. Czyszczona z setu userNames.
     * @param path - ścieżka lokalna. Czyszczona z setu userPaths.
     * @param user - obiekt klasy UserThread dla unikalnego użytkownika. Czyszczony z setu userThreads.
     */
    void removeUser(String name, String path, UserThread user) {
        if (userNames.remove(name)) {
            userPaths.remove(path);
            userThreads.remove(user);
            users_listModel.removeElement(name);
            ui.users_label.setText("Użytkownicy ("+ userNames.size() +")");
        }
    }

    /**
     * Metoda główna - konfiguruje i uruchamia główną ramkę interfejsu graficznego oraz związany z nim
     * obiekt klasy ServerUI.
     * @param args - żadne argumenty nie są przyjmowane.
     */
    public static void main(String[] args) {
        mainframe = new JFrame("Sharem");
        ui = new ServerUI();
        mainframe.setContentPane(ui.main_panel);

        mainframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainframe.pack();
        mainframe.setVisible(true);
        mainframe.setSize(600,400);

        users_listModel=new DefaultListModel();
        ui.users_list.setModel(users_listModel);
        ui.users_label.setText("Użytkownicy (0)");

        ui.users_list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ui.users_list.addListSelectionListener(userSelectionListener);
        ui.files_list.setModel(fileListModel);

        Server server = new Server();
        server.go();
    }

    /**
     * Metoda odświeżająca model listy plików dla zawartości danego użytkownika.
     *
     * @param username - nazwa użytkownika, którego listę plików chcemy pobrać.
     * @param user - wątek użytkownika (obiekt klasy UserThread), którego listę plików chcemy pobrać.
     */
    public void refreshFiles(String username, UserThread user) {
        if (username.equals(ui.users_list.getSelectedValue())) {
            fileListModel.clear();
            for (File f : user.getDirList()) {
                fileListModel.addElement(f.getName());
            }
        }
    }

}
