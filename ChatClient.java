import java.io.*;
import java.nio.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    static Socket s;
    static InputStream serverIn;
    static OutputStream serverOut;
    static String server;
    static int port;
    static BufferedReader bufferedIn;


    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
	frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocus();
            }
	    });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
	this.server = server;
	this.port = port;


    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
	//System.out.println(message);
	serverOut.write(message.getBytes());
	//String response = bufferedIn.readLine();
	//System.out.println(response);
	
    }

    
    // Método principal do objecto
    public void run() throws IOException {
        // PREENCHER AQUI
	
	try{
	    s = new Socket(server,port); // vriacao da socket para conectar com o servidor
	    this.serverIn = s.getInputStream();
	    this.serverOut = s.getOutputStream();
	    this.bufferedIn = new BufferedReader(new InputStreamReader(serverIn));
	    String teste = "/nick OLA\n";
	    newMessage(teste);
	    teste = "/join SALA\n";
	    newMessage(teste);
	    teste = "OLA\n";
	    newMessage(teste);
	    startMessageReader();
	    
	   
	}catch (IOException e) {
	    System.err.println(e);
	}
    }

    public void startMessageReader() throws IOException {
	Thread t = new Thread() {
		@Override
		public void run() {
		     readServerMessage();
		}
	    };
	t.start();
    }

     private void readServerMessage() {
        try {
            String response;
	    System.out.println("1");
            response = bufferedIn.readLine();
	    System.out.println(response);
	    printMessage(response);
	}
	catch (Exception ex) {
            ex.printStackTrace();
            try {
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
	}
     }




    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
