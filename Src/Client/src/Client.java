import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.*;
import javax.swing.*;
import java.util.*;

/**
 * Klasa uruchamiająca aplikację klienta.
 */
public class Client {
    /** Adres hosta. */
    private String hostname;
    /** Port serwera - ten sam musi być wpisany w serwerze. */
    public final static int port = 13243;
    /** Nazwa użytkownika podawana przez użytkownika. */
    protected String username;
    /** Ścieżka lokalna podawana przez użytkownika. */
    protected String userpath;

    /** Ramka interfejsu głównego */
    private static JFrame mainframe;
    /** Ramka okna logowania */
    private static JFrame loginframe;
    /** Ramka okna udostępniania */
    private static JFrame shareframe;

    /** Obiekt interfejsu głównego */
    protected static ClientUI ui;
    /** Obiekt okna logowania */
    protected static LoginUI loginui;
    /** Obiekt okna udostępniania */
    protected static SharingUI shareui;

    /**
     * ActionListener nasłuchujący, czy przycisk logowania został wciśnięty.
     * Weryfikuje, czy dane zostały poprawnie podane i w przypadku akceptacji zamyka
     * okno logowania i otwiera okno główne programu.
     */
    protected static ActionListener loginBtnOnClick = new ActionListener(){
        @Override
        public void actionPerformed(ActionEvent e) {
            String thostname = loginui.addressField.getText();
            String tusername = loginui.nameField.getText();
            String tuserpath = loginui.pathField.getText();
            if (!thostname.isBlank() && !tusername.isBlank() && !tuserpath.isBlank()) {
                mainframe = new JFrame("Sharem ("+tusername+")");
                ui = new ClientUI();
                mainframe.setContentPane(ui.mainPanel);
                mainframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                mainframe.pack();
                mainframe.setSize(600,400);
                ui.shareBtn.addActionListener(shareBtnOnClick);
                loginframe.setEnabled(false);

                Client client = new Client(thostname,tusername,tuserpath);
                client.go();
            }
        }
    };

    /**
     * WindowListener nasłuchujący, czy okno udostępniania zostało zamknięte.
     * Jeśli tak, odblokowuje z powrotem możliwość działania na oknie głównym.
     */
    protected static WindowListener shareUIListen = new WindowListener() {
        @Override
        public void windowClosing(WindowEvent e) {
            mainframe.setEnabled(true);
        }

        @Override public void windowOpened(WindowEvent e) {}
        @Override public void windowClosed(WindowEvent e) {}
        @Override public void windowActivated(WindowEvent e) {}
        @Override public void windowDeactivated(WindowEvent e) {}
        @Override public void windowDeiconified(WindowEvent e) {}
        @Override public void windowIconified(WindowEvent e) {}
    };

    /**
     * Lista służąca do zawierania informacji o wybranych przez użytkownika elementów
     * z listy plików.
     */
    private static List<String> selected;
    /** Lista z kolejką plików do udostępnienia */
    protected static List<String> filesQueue = new ArrayList<>();
    /** Lista z kolejką użytkowników, do jakich należy udostępnić pliki. */
    protected static List<String> filesQueueUsrnames = new ArrayList<>();

    /**
     * ActionListener nasłuchujący, czy przycisk "Udostępnij" został wciśnięty.
     * Weryfikuje, czy zostały wybrane pliki w liście plików - jeśli tak, blokowana jest możliwość
     * interakcji z oknem głównym i otwierane jest okno udostępniania.
     */
    protected static ActionListener shareBtnOnClick = new ActionListener(){
        @Override
        public void actionPerformed(ActionEvent e) {
            selected = ui.filesList.getSelectedValuesList();
            if (selected.size()>0) {
                mainframe.setEnabled(false);
                shareframe.setVisible(true);
            }
        }
    };

    /**
     * ActionListener nasłuchujący, czy przycisk w oknie udostępniania został wciśnięty.
     * Jeśli tak, dodaje odpowiednie pliki dla odpowiednich użytkowników do kolejki, zamyka
     * okno udostępniania i pokazuje z powrotem okno główne aplikacji.
     */
    protected static ActionListener applyBtnOnClick = new ActionListener(){
        @Override
        public void actionPerformed(ActionEvent e) {
            List<String> usrselected = shareui.usersList.getSelectedValuesList();
            if (usrselected.size()>0) {
                shareframe.setVisible(false);
                for(String i : usrselected) {
                    for(String j : selected) {
                        filesQueue.add(j);
                        filesQueueUsrnames.add(i);
                    }
                }
                mainframe.setEnabled(true);
            }
        }
    };


    /**
     * Konstruktor klasy Client. Przypisuje odpowiednie dane do odpowiednich pól.
     * @param hostname - adres hosta
     * @param username - nazwa użytkownika
     * @param userpath - ścieżka lokalna
     */
    public Client(String hostname, String username, String userpath) {
        this.hostname=hostname;
        this.username=username;
        this.userpath=userpath;
    }

    /**
     * Metoda go() uruchamia socket oraz wątek w obiekcie klasy SyncThread.
     */
    public void go() {
        try {
            Socket socket = new Socket(hostname, port);
            mainframe.setVisible(true);
            loginframe.setVisible(false);
            new SyncThread(socket, this).start();
        }
        catch (Exception e) {}
    }

    /**
     * Metoda główna konfigurująca i uruchamiająca okno logowania oraz część elementów okna głównego
     * i okna udostępniania.
     * @param args - przyjmuje 2 argumenty: nazwę użytkownika oraz ścieżkę lokalną, które są następnie
     *             wpisywane w odpowiednie pola okna logowania.
     */
    public static void main(String[] args) {
        loginframe = new JFrame("Zaloguj się do Sharem");
        loginui = new LoginUI();
        loginframe.setContentPane(loginui.mainPanel);
        loginframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginframe.pack();
        loginframe.setVisible(true);
        loginframe.setSize(500,200);

        if (args.length>=1) loginui.nameField.setText(args[0]);
        if (args.length>=2) loginui.pathField.setText(args[1]);

        shareframe = new JFrame("Udostępnianie");
        shareui = new SharingUI();
        shareframe.setContentPane(shareui.mainPanel);
        shareframe.pack();
        shareframe.setSize(500,400);
        shareframe.setVisible(false);

        loginui.loginBtn.addActionListener(loginBtnOnClick);
        shareframe.addWindowListener(shareUIListen);
        shareui.applyButton.addActionListener(applyBtnOnClick);
    }

}